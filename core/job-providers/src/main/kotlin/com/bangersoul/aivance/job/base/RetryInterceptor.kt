package com.bangersoul.aivance.job.base

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import kotlin.math.pow
import kotlin.random.Random

/**
 * Interceptor that retries failed requests with exponential backoff.
 *
 * The backoff sleep runs on an OkHttp dispatcher thread, so it is deliberately
 * bounded: [maxDelay] caps the worst case and [jitterFactor] spreads retries so
 * that many providers failing at once don't retry in lockstep. When the server
 * sends a `Retry-After` header on a 429/503 we honour it (still capped).
 *
 * Known limitation: this still blocks the calling thread. Fully removing that
 * means moving retry out of the interceptor into a suspending call site, which
 * would touch every provider; see the perf follow-up notes.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelay: Long = 1000L,
    private val backoffMultiplier: Double = 2.0,
    private val maxDelay: Long = 10_000L,
    private val jitterFactor: Double = 0.2
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var lastException: IOException? = null

        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                // `response` still holds the previous attempt's result here — it is
                // not closed until further down — so Retry-After is still readable.
                val delay = backoffDelayMs(attempt - 1, response)
                Timber.d("Retrying request ${request.url} (attempt $attempt/$maxRetries) after ${delay}ms")
                try {
                    Thread.sleep(delay)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Retry interrupted", e)
                }
            }

            try {
                response?.close()
                response = chain.proceed(request)
                if (response.isSuccessful) return response

                // Don't retry client errors (4xx) except maybe 408 or 429
                if (response.code in 400..499 && response.code != 408 && response.code != 429) {
                    return response
                }
            } catch (e: IOException) {
                lastException = e
                Timber.w(e, "Request failed: ${request.url} (attempt $attempt/$maxRetries)")
            }
        }

        return response ?: throw lastException ?: IOException("Request failed after $maxRetries retries")
    }

    /**
     * Delay before retry number [retryIndex] (0-based), preferring the server's
     * `Retry-After` hint when it is present and parseable as a delay in seconds.
     * An HTTP-date form of the header falls back to the computed backoff.
     */
    private fun backoffDelayMs(retryIndex: Int, previous: Response?): Long {
        val retryAfterSeconds = previous
            ?.takeIf { it.code == 429 || it.code == 503 }
            ?.header("Retry-After")
            ?.toLongOrNull()
        if (retryAfterSeconds != null && retryAfterSeconds >= 0) {
            return retryAfterSeconds.coerceAtMost(maxDelay)
        }

        val exponential = initialDelay * backoffMultiplier.pow(retryIndex.toDouble())
        val jitter = exponential * jitterFactor * Random.nextDouble()
        return (exponential + jitter).toLong().coerceIn(0L, maxDelay)
    }
}
