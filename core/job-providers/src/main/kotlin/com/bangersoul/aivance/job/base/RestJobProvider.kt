package com.bangersoul.aivance.job.base

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.sdk.api.JobProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Base class for REST-based job providers.
 * Implements caching, retries via [NetworkRetry], and basic circuit breaker logic.
 */
abstract class RestJobProvider(
    metadata: ProviderMetadata,
    capabilities: Set<ProviderCapability>,
    protected val jobCache: JobCache,
    protected val baseOkHttpClient: OkHttpClient,
    protected val baseRetrofit: Retrofit,
    private val errorThreshold: Int = 3
) : JobProvider(metadata, capabilities) {

    private val consecutiveErrors = AtomicInteger(0)

    /**
     * Base URL for the provider's API.
     */
    abstract val baseUrl: String

    /**
     * OkHttpClient used by this provider.
     *
     * Deliberately carries no retry interceptor: retrying here would sleep with
     * `Thread.sleep` on an OkHttp dispatcher thread, and with only 5 concurrent
     * requests permitted per host, a few failing providers could hold every
     * dispatcher thread in the pool. Retries are applied by [retrying] at the
     * suspending call sites instead, where the backoff releases the thread.
     */
    protected val okHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder().build()
    }

    /**
     * Runs [block] with transient-failure retries. Backoff suspends rather than
     * blocking, so a slow provider never occupies an OkHttp dispatcher thread.
     */
    protected suspend fun <T> retrying(
        maxRetries: Int = NetworkRetry.DEFAULT_MAX_RETRIES,
        block: suspend (attempt: Int) -> T
    ): T = NetworkRetry.execute(maxRetries = maxRetries) { attempt ->
        block(attempt).also {
            if (attempt > 0) {
                Timber.d("Provider ${metadata.id} succeeded on retry $attempt")
            }
        }
    }

    /**
     * Retrofit instance configured with the provider's base URL and [okHttpClient].
     */
    protected val retrofit: Retrofit by lazy {
        baseRetrofit.newBuilder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .build()
    }

    override suspend fun searchJobs(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): Result<List<JobListing>> {
        // If the provider is in a hard Error state, don't even try
        if (status == ProviderStatus.Error) {
            return Result.Failure(ProviderError(metadata.id, message = "Provider is in Error state"))
        }

        return try {
            val jobs = retrying { executeSearch(filter, sortOrder, page) }

            // Success: Reset error counter and restore status if needed
            consecutiveErrors.set(0)
            if (status == ProviderStatus.Degraded) {
                updateStatus(ProviderStatus.Active)
            }

            // Cache results for reliability
            jobCache.saveJobs(jobs)

            Result.Success(jobs)
        } catch (e: Exception) {
            handleFailure(e)

            // Reliability: Fallback to THIS provider's own cache if the network
            // fails. The shared cache is attributed per provider (sourceProvider
            // is stamped by the normalizer on every successful fetch), so a
            // provider can never masquerade another board's stale listings as
            // its own results — that was the "dummy results" bug where every
            // unconfigured provider echoed the whole DB on failure.
            val cachedJobs = jobCache.getJobs().filter { it.sourceProvider == metadata.id }
            if (cachedJobs.isNotEmpty()) {
                Timber.d("Network failed for ${metadata.id}, returning ${cachedJobs.size} cached jobs")
                Result.Success(cachedJobs)
            } else {
                Result.Failure(ProviderError(metadata.id, message = e.message ?: "Unknown error", cause = e))
            }
        }
    }

    override suspend fun getJobDetails(jobId: String): Result<JobListing> {
        // 1. Try Cache
        val cachedJob = jobCache.getJobs().find { it.id == jobId }
        if (cachedJob != null) return Result.Success(cachedJob)

        // 2. Try Network if implemented by subclass
        return try {
            val job = retrying { executeGetDetails(jobId) }
            if (job != null) {
                jobCache.saveJobs(listOf(job))
                Result.Success(job)
            } else {
                Result.Failure(ProviderError(metadata.id, message = "Job $jobId not found in network or cache"))
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch job details from network for ${metadata.id}")
            Result.Failure(ProviderError(metadata.id, message = e.message ?: "Failed to fetch job details", cause = e))
        }
    }

    /**
     * Hook for subclasses to implement fetching a single job by ID from the network.
     *
     * Implementations should throw [ProviderHttpException] (with the real status
     * code) on a non-2xx response so [retrying] can distinguish a transient 503
     * from a permanent 401.
     */
    protected open suspend fun executeGetDetails(jobId: String): JobListing? = null

    /**
     * Implementation-specific search logic using [okHttpClient].
     *
     * Implementations should throw [ProviderHttpException] (with the real status
     * code) on a non-2xx response so [retrying] can distinguish a transient 503
     * from a permanent 401.
     */
    protected abstract suspend fun executeSearch(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): List<JobListing>

    /**
     * Handles request failures and implements circuit breaker logic.
     */
    private fun handleFailure(e: Exception) {
        val errors = consecutiveErrors.incrementAndGet()
        Timber.w(e, "Provider ${metadata.id} request failed ($errors/$errorThreshold)")

        if (errors >= errorThreshold) {
            Timber.e("Circuit breaker tripped for ${metadata.id}. Setting status to Degraded.")
            updateStatus(ProviderStatus.Degraded)
        }
    }

    override suspend fun checkHealth(): ProviderStatus {
        return try {
            retrying { performHealthCheck() }

            // If health check succeeds, reset errors and mark as Active
            consecutiveErrors.set(0)
            if (status == ProviderStatus.Degraded || status == ProviderStatus.Error) {
                updateStatus(ProviderStatus.Active)
            }
            ProviderStatus.Active
        } catch (e: Exception) {
            handleFailure(e)
            // Honest failure: a throwing health check (bad key, unreachable host)
            // must surface as Degraded so validateProvider rejects bad credentials
            // (anything that isn't Ready/Active fails validation) instead of silently
            // passing via the previous status (typically Ready).
            //
            // We deliberately use Degraded, NOT Error: searchJobs hard-blocks on
            // Error with no automatic recovery path, so a single transient network
            // blip during validation would permanently disable the provider.
            // Degraded keeps the provider searchable and it self-recovers on the
            // next successful search (restored to Active).
            if (status != ProviderStatus.Error) {
                updateStatus(ProviderStatus.Degraded)
            }
            ProviderStatus.Degraded
        }
    }

    /**
     * Hook for subclasses to implement actual health check (e.g., pinging an endpoint).
     *
     * Implementations should throw [ProviderHttpException] on a non-2xx response.
     */
    protected open suspend fun performHealthCheck() {
        // Default: no-op, assumes healthy if no exceptions
    }

    override suspend fun onInitialize() {
        updateStatus(ProviderStatus.Initializing)
        // Subclasses can override to perform setup
        updateStatus(ProviderStatus.Ready)
    }

    override suspend fun onStart() {
        updateStatus(ProviderStatus.Active)
    }

    override suspend fun onStop() {
        updateStatus(ProviderStatus.Ready)
    }

    override suspend fun onDispose() {
        updateStatus(ProviderStatus.Disposed)
    }
}
