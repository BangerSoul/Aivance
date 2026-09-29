package com.bangersoul.aivance.job.arbeitnow

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.job.base.ProviderHttpException
import com.bangersoul.aivance.job.base.RestJobProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.mapper.JobMapper
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Free job board focused on Germany and the EU.
 * No API key required - works out of the box.
 */
class ArbeitnowProvider(
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://www.arbeitnow.com/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "arbeitnow",
        name = "Arbeitnow",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Free jobs in Germany & the EU (no API key).",
        icon = "https://www.arbeitnow.com/favicon.ico",
        author = "BangerSoul"
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: ArbeitnowApi by lazy { retrofit.create(ArbeitnowApi::class.java) }

    override suspend fun executeSearch(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): List<JobListing> {
        // NOTE: the API's `remote` query param is a no-op. Verified live against
        // https://www.arbeitnow.com/api/job-board-api (Aug 2026): identical
        // 175-job responses with and without `remote=true`, and only a handful
        // of listings carry `remote: true`. Sending it would imply API-level
        // remote filtering that never happens, so we omit it; the client-side
        // matcher accepts remote-signaling listings even when the board's
        // structured field says ON_SITE.
        val response = api.getJobs(
            page = page,
            search = filter.query.ifBlank { null },
            location = filter.location.ifBlank { null }
        )
        if (response.isSuccessful) {
            return response.body()?.data?.map { JobMapper.mapToJobListing(it, metadata.id) } ?: emptyList()
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "Arbeitnow API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }


}
