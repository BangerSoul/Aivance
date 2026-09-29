package com.bangersoul.aivance.job.base

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.math.pow

/**
 * Covers [NetworkRetry], which replaced the blocking `RetryInterceptor`.
 *
 * The important behavioural guarantees are: transient failures are retried,
 * permanent 4xx fail fast without burning retries, `Retry-After` is honoured,
 * and the backoff is bounded and jittered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkRetryTest {

    private fun httpError(status: Int, retryAfterSeconds: Long? = null) =
        ProviderHttpException(
            providerId = "test",
            statusCode = status,
            message = "HTTP $status",
            retryAfterSeconds = retryAfterSeconds
        )

    @Test
    fun `returns immediately when the first attempt succeeds`() = runTest {
        var calls = 0
        val result = NetworkRetry.execute(maxRetries = 3) {
            calls++
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries a 503 and succeeds`() = runTest {
        var calls = 0
        val result = NetworkRetry.execute(maxRetries = 2, initialDelayMs = 1) {
            calls++
            if (calls < 3) throw httpError(503)
            "recovered"
        }

        assertEquals("recovered", result)
        assertEquals(3, calls)
    }

    @Test
    fun `retries an IOException and succeeds`() = runTest {
        var calls = 0
        val result = NetworkRetry.execute(maxRetries = 2, initialDelayMs = 1) {
            calls++
            if (calls < 3) throw IOException("connection reset")
            "recovered"
        }

        assertEquals("recovered", result)
        assertEquals(3, calls)
    }

    @Test
    fun `retries 429 and 408`() = runTest {
        for (status in listOf(429, 408)) {
            var calls = 0
            val result = NetworkRetry.execute(maxRetries = 2, initialDelayMs = 1) {
                calls++
                if (calls < 2) throw httpError(status)
                "ok-$status"
            }
            assertEquals("ok-$status", result)
            assertEquals("status $status", 2, calls)
        }
    }

    @Test
    fun `permanent 4xx is not retried`() = runTest {
        for (status in listOf(400, 401, 403, 404)) {
            var calls = 0
            var thrown: ProviderHttpException? = null
            try {
                NetworkRetry.execute(maxRetries = 3, initialDelayMs = 1) {
                    calls++
                    throw httpError(status)
                }
            } catch (e: ProviderHttpException) {
                thrown = e
            }

            assertEquals(status, thrown?.statusCode)
            assertEquals("status $status must not be retried", 1, calls)
        }
    }

    @Test
    fun `rethrows the last error once retries are exhausted`() = runTest {
        var calls = 0
        var thrown: ProviderHttpException? = null
        try {
            NetworkRetry.execute(maxRetries = 2, initialDelayMs = 1) {
                calls++
                throw httpError(502)
            }
        } catch (e: ProviderHttpException) {
            thrown = e
        }

        assertEquals(502, thrown?.statusCode)
        assertEquals(3, calls) // initial attempt + 2 retries
    }

    @Test
    fun `retryability classification matches transient statuses`() {
        assertTrue(httpError(500).isRetryable)
        assertTrue(httpError(502).isRetryable)
        assertTrue(httpError(503).isRetryable)
        assertTrue(httpError(408).isRetryable)
        assertTrue(httpError(429).isRetryable)
        assertTrue(!httpError(400).isRetryable)
        assertTrue(!httpError(401).isRetryable)
        assertTrue(!httpError(404).isRetryable)
    }

    @Test
    fun `backoff grows exponentially and stays within the cap`() {
        val cap = 10_000L
        val initial = 1_000L

        // First retry sits at roughly the initial delay (plus up to 20% jitter).
        val first = NetworkRetry.backoffDelayMs(retryIndex = 0, initialDelayMs = initial, maxDelayMs = cap)
        assertTrue("first=$first", first in (initial)..(initial * 1.2).toLong())

        // Later retries grow, but never past the cap.
        val later = (1..12).map { NetworkRetry.backoffDelayMs(it, initial, cap) }
        assertTrue("later must be non-decreasing", later.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("must respect cap", later.all { it <= cap })
    }

    @Test
    fun `Retry-After takes precedence over computed backoff and is still capped`() {
        // 429 with Retry-After: 2 -> the helper prefers 2_000ms over 1_000ms base.
        val withHeader = NetworkRetry.backoffDelayMs(
            retryIndex = 0,
            initialDelayMs = 1_000L,
            maxDelayMs = 10_000L,
            retryAfterSeconds = 2
        )
        assertEquals(2_000L, withHeader)

        // A huge Retry-After must not exceed the cap.
        val capped = NetworkRetry.backoffDelayMs(
            retryIndex = 0,
            initialDelayMs = 1_000L,
            maxDelayMs = 10_000L,
            retryAfterSeconds = 86_400
        )
        assertEquals(10_000L, capped)
    }

    @Test
    fun `jitter keeps simultaneous failures from retrying in lockstep`() {
        val samples = List(50) {
            NetworkRetry.backoffDelayMs(retryIndex = 2, initialDelayMs = 1_000L, maxDelayMs = 10_000L)
        }
        // Without jitter every sample would be identical; spread proves it is applied.
        assertTrue("expected jittered spread", samples.distinct().size > 1)
        val base = (1_000L * 2.0.pow(2.0)).toLong()
        assertTrue("samples must stay in [base, base+20%]", samples.all { it in base..(base * 1.2).toLong() })
    }
}
