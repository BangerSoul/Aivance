package com.bangersoul.aivance.job.base

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.job.bayt.BaytProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.glassdoor.GlassdoorProvider
import com.bangersoul.aivance.job.naukri.NaukriProvider
import com.bangersoul.aivance.job.ziprecruiter.ZipRecruiterProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Bayt, Glassdoor, Naukri and ZipRecruiter used to answer a non-2xx response
 * with `return emptyList()`.
 *
 * That is indistinguishable from "this search genuinely matched nothing", so a
 * blocked or broken provider looked exactly like a healthy one and the failure
 * never reached the health counter, the per-provider status, or the Discovery
 * UI. Every other provider in this module already throws
 * [ProviderHttpException]; these four now do too.
 *
 * Each case asserts both halves of the contract, because fixing the failure
 * path only matters if the success path is left alone: a non-2xx has to become a
 * [Result.Failure] carrying the status, and a 200 with zero matches has to stay
 * a [Result.Success] with an empty list.
 */
class ProviderErrorSignalTest {

    private lateinit var jobCache: JobCache
    private lateinit var json: Json
    private val servers = mutableListOf<MockWebServer>()

    /** The four providers that used to swallow failures, keyed by their provider id. */
    private val providers: List<Pair<String, (JobCache, OkHttpClient, Retrofit, String) -> RestJobProvider>> = listOf(
        "bayt" to { cache, client, retrofit, url -> BaytProvider(cache, client, retrofit, url) },
        "glassdoor" to { cache, client, retrofit, url -> GlassdoorProvider(cache, client, retrofit, url) },
        "naukri" to { cache, client, retrofit, url -> NaukriProvider(cache, client, retrofit, url) },
        "ziprecruiter" to { cache, client, retrofit, url -> ZipRecruiterProvider(cache, client, retrofit, url) }
    )

    @Before
    fun setUp() {
        jobCache = mockk(relaxed = true)
        json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        coEvery { jobCache.getJobs() } returns emptyList()
        coEvery { jobCache.saveJobs(any()) } returns Unit
    }

    @After
    fun tearDown() {
        servers.forEach { it.shutdown() }
        servers.clear()
    }

    @Test
    fun `non-2xx is reported as a failure instead of an empty result`() = runTest {
        forEachProvider { bound ->
            val id = bound.id
            // 403 is what these boards actually answer with: a bot block or a
            // missing subscription, which no amount of retrying will fix.
            bound.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

            val result = bound.search()

            assertTrue("$id should have failed, got $result", result is Result.Failure)
            val cause = (result as Result.Failure).error.cause
            assertTrue("$id should carry a ProviderHttpException, got $cause", cause is ProviderHttpException)
            val httpError = cause as ProviderHttpException
            assertEquals("$id should surface the real status", 403, httpError.statusCode)
            assertEquals("$id should attribute the error to itself", id, httpError.providerId)
        }
    }

    @Test
    fun `retryable 5xx is retried before the failure is surfaced`() = runTest {
        forEachProvider { bound ->
            val id = bound.id
            // One response per attempt: the initial call plus
            // NetworkRetry.DEFAULT_MAX_RETRIES. A 503 is retryable, so all four
            // have to be consumed before the failure reaches the caller.
            repeat(4) {
                bound.enqueue(MockResponse().setResponseCode(503).setBody("Unavailable"))
            }

            val result = bound.search()

            assertTrue("$id should have failed, got $result", result is Result.Failure)
            assertEquals("$id should have been retried", 4, bound.requestCount)
            val cause = (result as Result.Failure).error.cause
            assertTrue("$id should carry a ProviderHttpException, got $cause", cause is ProviderHttpException)
            assertEquals("$id should surface the real status", 503, (cause as ProviderHttpException).statusCode)
        }
    }

    @Test
    fun `an empty 200 stays a success`() = runTest {
        val emptyBodies = mapOf(
            "bayt" to """{"results":[]}""",
            "glassdoor" to """{"jobs":[]}""",
            "naukri" to """{"jobDetails":[]}""",
            "ziprecruiter" to """{"jobs":[]}"""
        )

        forEachProvider { bound ->
            val id = bound.id
            bound.enqueue(MockResponse().setResponseCode(200).setBody(emptyBodies.getValue(id)))

            val result = bound.search()

            assertTrue("$id should have succeeded, got $result", result is Result.Success)
            assertTrue("$id should report zero jobs", (result as Result.Success).data.isEmpty())
        }
    }

    /** A provider wired to its own [MockWebServer], started and ready to search. */
    private inner class Bound(
        val id: String,
        private val provider: RestJobProvider,
        private val server: MockWebServer
    ) {
        fun enqueue(response: MockResponse) = server.enqueue(response)

        val requestCount: Int get() = server.requestCount

        suspend fun search() = provider.searchJobs(JobSearchFilter(), JobSortOrder.RELEVANCE, 1)
    }

    private suspend fun forEachProvider(block: suspend (Bound) -> Unit) {
        for ((id, factory) in providers) {
            val server = MockWebServer()
            servers.add(server)
            val url = server.url("/").toString()
            val client = OkHttpClient.Builder().build()
            val retrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            val provider = factory(jobCache, client, retrofit, url)
            provider.onInitialize()
            provider.onStart()
            block(Bound(id, provider, server))
        }
    }
}
