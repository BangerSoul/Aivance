package com.bangersoul.aivance.job.base

import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.math.pow
import kotlin.random.Random

/**
 * A non-2xx HTTP response from a job provider's API.
 *
 * Providers previously threw a bare `Exception("... failed: <code>")`, which
 * made it impossible to tell a retryable 503 from a permanent 401. Carrying the
 * status in a type lets [NetworkRetry] retry precisely and leaves permanent
 * failures to fail fast.
 */
class ProviderHttpException(
    val providerId: String,
    val statusCode: Int,
    message: String,
    /** Seconds from `Retry-After`, when the server sent a parseable one. */
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** True when another attempt could plausibly succeed. */
    val isRetryable: Boolean
        get() = statusCode in 500..599 || statusCode == 408 || statusCode == 429

    override fun toString(): String =
        "ProviderHttpException(provider=$providerId, status=$statusCode, message=$message)"
}

/**
 * Retries a suspending network call without ever blocking a thread.
 *
 * This replaces the previous `RetryInterceptor`, which slept with
 * [Thread.sleep] on an OkHttp dispatcher thread. OkHttp allows only 5
 * concurrent requests per host by default, so a few providers failing together
 * could hold every dispatcher thread in the pool for seconds. Retrying here
 * makes the backoff a real suspension: the thread is released and the
 * dispatcher keeps serving other work.
 *
 * Backoff is capped, jittered so that simultaneously-failing providers do not
 * retry in lockstep, and defers to the server's `Retry-After` on 429/503.
 */
object NetworkRetry {

    const val DEFAULT_MAX_RETRIES = 3
    const val DEFAULT_INITIAL_DELAY_MS = 1_000L
    const val DEFAULT_MAX_DELAY_MS = 10_000L
    const val DEFAULT_JITTER_FACTOR = 0.2

    /**
     * Runs [block], retrying transient transport failures ([IOException]) and
     * retryable HTTP statuses ([ProviderHttpException.isRetryable]).
     *
     * Rethrows the last failure once attempts are exhausted, so callers keep
     * their existing error handling. Non-retryable HTTP errors (4xx other than
     * 408/429) are rethrown immediately without consuming retries.
     */
    suspend fun <T> execute(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        jitterFactor: Double = DEFAULT_JITTER_FACTOR,
        onRetry: (attempt: Int, error: Throwable) -> Unit = { _, _ -> },
        block: suspend (attempt: Int) -> T
    ): T {
        var lastError: Throwable? = null

        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                delay(
                    backoffDelayMs(
                        retryIndex = attempt - 1,
                        initialDelayMs = initialDelayMs,
                        maxDelayMs = maxDelayMs,
                        jitterFactor = jitterFactor,
                        retryAfterSeconds = (lastError as? ProviderHttpException)?.retryAfterSeconds
                    )
                )
            }

            try {
                return block(attempt)
            } catch (e: ProviderHttpException) {
                if (!e.isRetryable) throw e
                lastError = e
                onRetry(attempt, e)
            } catch (e: IOException) {
                lastError = e
                onRetry(attempt, e)
            }
        }

        throw lastError ?: IOException("Request failed after $maxRetries retries")
    }

    /**
     * Delay before retry number [retryIndex] (0-based), preferring a server
     * supplied `Retry-After` when one was captured. The HTTP-date form of the
     * header is not parsed and falls back to the computed backoff.
     */
    fun backoffDelayMs(
        retryIndex: Int,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        jitterFactor: Double = DEFAULT_JITTER_FACTOR,
        retryAfterSeconds: Long? = null
    ): Long {
        if (retryAfterSeconds != null && retryAfterSeconds >= 0) {
            return (retryAfterSeconds * 1_000L).coerceAtMost(maxDelayMs)
        }

        val exponential = initialDelayMs * 2.0.pow(retryIndex.toDouble())
        val jitter = exponential * jitterFactor * Random.nextDouble()
        return (exponential + jitter).toLong().coerceIn(0L, maxDelayMs)
    }
}
