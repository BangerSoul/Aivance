package com.bangersoul.aivance.job.apify

import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
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

class ApifyJobProviderTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: ApifyApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        api = buildApi(mockWebServer)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `runActor then getActorRun then getDatasetItems parses real Apify schema`() = runTest {
        // 1. runActor -> RUNNING with dataset id
        mockWebServer.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"data":{"id":"run-1","status":"RUNNING","defaultDatasetId":"ds-1"}}"""
            )
        )
        // 2. Poll -> SUCCEEDED
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"id":"run-1","status":"SUCCEEDED","defaultDatasetId":"ds-1"}}"""
            )
        )
        // 3. Dataset items (Apify returns an array)
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"id":"a1","title":"Android Dev","company":"TechCorp","location":"Remote","url":"https://x.com/job/1"}]"""
            )
        )

        val run = api.runActor("test-actor", "test-key", buildJsonObject { })
        assertTrue(run.isSuccessful)
        val runData = run.body()?.data
        assertEquals("run-1", runData?.id)
        assertEquals("RUNNING", runData?.status)
        assertEquals("ds-1", runData?.defaultDatasetId)

        val status = api.getActorRun("run-1", "test-key")
        assertEquals("SUCCEEDED", status.body()?.data?.status)

        val items = api.getDatasetItems("ds-1", "test-key", limit = 100, offset = 0)
        assertEquals(1, items.body()?.size)
        assertEquals("Android Dev", items.body()?.first()?.title)

        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun `failed run status is surfaced through the API`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"data":{"id":"run-2","status":"READY","defaultDatasetId":null}}"""
            )
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"id":"run-2","status":"FAILED","defaultDatasetId":null}}"""
            )
        )

        api.runActor("test-actor", "test-key", buildJsonObject { })
        val status = api.getActorRun("run-2", "test-key")

        assertEquals("FAILED", status.body()?.data?.status)
    }

    @Test
    fun `executeSearch sends keywords location country maxItems to the actor`() = runTest {
        // runActor -> READY with dataset
        mockWebServer.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"data":{"id":"r1","status":"READY","defaultDatasetId":"ds1"}}"""
            )
        )
        // poll -> SUCCEEDED
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"id":"r1","status":"SUCCEEDED","defaultDatasetId":"ds1"}}"""
            )
        )
        // dataset -> empty
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""[]"""))

        val provider = TestApifyProvider(
            jobCache = mockk(relaxed = true),
            okHttpClient = OkHttpClient(),
            baseRetrofit = buildRetrofit(mockWebServer),
            baseUrl = mockWebServer.url("/v2/").toString()
        )

        provider.runSearch(
            filter = com.bangersoul.aivance.core.common.model.JobSearchFilter(
                query = "Android Engineer",
                location = "United States"
            ),
            sortOrder = com.bangersoul.aivance.core.common.enums.JobSortOrder.RELEVANCE,
            page = 1
        )

        val runRequest = mockWebServer.takeRequest()
        assertEquals("POST", runRequest.method)
        val body = runRequest.body.readUtf8()
        // Live-verified input shape (QA E2E 2026-08-11): the actor keys off
        // `keywords` + `location`/`country`; `positions` alone or the legacy
        // `search` key either error or return evergreen junk.
        assertTrue("expected keywords in body: $body", body.contains("\"keywords\":\"Android Engineer\""))
        assertTrue("expected location in body: $body", body.contains("\"location\":\"United States\""))
        assertTrue("expected country in body: $body", body.contains("\"country\":\"US\""))
        assertTrue("expected maxItems in body: $body", body.contains("\"maxItems\":100"))
        assertTrue("must not send ignored positions/search keys: $body", !body.contains("positions") && !body.contains("\"search\""))
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun `provider metadata and lifecycle are correct`() = runTest {
        val provider = TestApifyProvider(
            jobCache = mockk(relaxed = true),
            okHttpClient = OkHttpClient(),
            baseRetrofit = buildRetrofit(mockWebServer)
        )
        assertEquals("test-actor", provider.metadata.id)
        assertEquals(ProviderType.JOB, provider.metadata.type)
        assertTrue(provider.hasCapability(ProviderCapability.JobSearch))

        assertEquals(ProviderStatus.Uninitialized, provider.status)
        provider.onInitialize()
        assertEquals(ProviderStatus.Ready, provider.status)
        provider.onStart()
        assertEquals(ProviderStatus.Active, provider.status)
        provider.onDispose()
        assertEquals(ProviderStatus.Disposed, provider.status)
    }

    private fun buildApi(server: MockWebServer): ApifyApi =
        buildRetrofit(server).create(ApifyApi::class.java)

    private fun buildRetrofit(server: MockWebServer): Retrofit {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private class TestApifyProvider(
        jobCache: JobCache,
        okHttpClient: OkHttpClient,
        baseRetrofit: Retrofit,
        baseUrl: String = "https://api.apify.com/v2/"
    ) : ApifyJobProvider(
        metadata = ProviderMetadata(
            id = "test-actor",
            name = "Test Actor",
            type = ProviderType.JOB,
            version = "1.0.0",
            description = "Apify test actor.",
            author = "Test"
        ),
        apiKey = "test-key",
        actorId = "test-actor",
        jobCache = jobCache,
        okHttpClient = okHttpClient,
        baseRetrofit = baseRetrofit,
        baseUrl = baseUrl
    ) {
        /** Public bridge so the test can drive the protected executeSearch. */
        suspend fun runSearch(
            filter: com.bangersoul.aivance.core.common.model.JobSearchFilter,
            sortOrder: com.bangersoul.aivance.core.common.enums.JobSortOrder,
            page: Int
        ): List<com.bangersoul.aivance.core.common.model.JobListing> =
            executeSearch(filter, sortOrder, page)
    }
}
