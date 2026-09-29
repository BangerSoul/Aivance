package com.bangersoul.aivance.job.arbeitnow

import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.sdk.core.ProviderCapability
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Verifies the Retrofit interface parses the real Arbeitnow API schema
 * (verified against the live https://www.arbeitnow.com/api/job-board-api response).
 */
class ArbeitnowProviderTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: ArbeitnowApi

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
    fun `getJobs parses the live Arbeitnow schema`() = runTest {
        val json = """
        {
          "data": [
            {
              "slug": "android-dev-berlin-123",
              "company_name": "TechBerlin",
              "title": "Android Developer",
              "description": "Kotlin role in Berlin.",
              "remote": true,
              "url": "https://www.arbeitnow.com/jobs/android-dev-berlin-123",
              "tags": ["android", "kotlin"],
              "job_types": ["full-time"],
              "location": "Berlin",
              "created_at": 1785499244
            }
          ],
          "meta": { "count": 1, "page": 1 }
        }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val response = api.getJobs(page = 1)

        assertTrue("Expected success but was code ${response.code()}", response.isSuccessful)
        val jobs = response.body()?.data ?: emptyList()
        assertEquals(1, jobs.size)
        val dto = jobs.first()
        assertEquals("android-dev-berlin-123", dto.slug)
        assertEquals("Android Developer", dto.title)
        assertEquals("TechBerlin", dto.companyName)
        assertEquals("Berlin", dto.location)
        assertEquals(1785499244L, dto.createdAt)
        assertTrue(dto.remote == true)
    }

    @Test
    fun `searchJobs tolerates object-shaped tags in the live API`() = runTest {
        // The live Arbeitnow API sometimes returns `tags` as an object instead of an array,
        // which used to throw JsonDecodingException and fail the entire provider.
        val json = """
        {
          "data": [
            {
              "slug": "tags-object-case",
              "company_name": "RobustCo",
              "title": "Platform Engineer",
              "description": "Tolerant parsing.",
              "remote": true,
              "url": "https://www.arbeitnow.com/jobs/tags-object-case",
              "tags": {"main": "engineering"},
              "job_types": ["full-time"],
              "location": "Remote",
              "created_at": 1785499244
            }
          ],
          "meta": { "count": 1, "page": 1 }
        }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val provider = ArbeitnowProvider(
            jobCache = mockk(relaxed = true),
            okHttpClient = OkHttpClient(),
            baseRetrofit = buildRetrofit(mockWebServer),
            baseUrl = mockWebServer.url("/").toString()
        )
        provider.onInitialize()
        provider.onStart()

        val result = provider.searchJobs(
            com.bangersoul.aivance.core.common.model.JobSearchFilter(),
            com.bangersoul.aivance.core.common.enums.JobSortOrder.RELEVANCE,
            1
        )

        assertTrue("Expected Success despite object-shaped tags, got $result", result is com.bangersoul.aivance.core.common.result.Result.Success)
        assertEquals(1, (result as com.bangersoul.aivance.core.common.result.Result.Success).data.size)
    }

    @Test
    fun `search does not send the no-op remote query param`() = runTest {
        // The API ignores `remote=true` (verified against the live API: identical
        // responses with and without it), so the provider must not imply
        // API-level remote filtering that never happens.
        val json = """
        {
          "data": [
            {
              "slug": "remote-noop-check",
              "company_name": "RemoteCo",
              "title": "Android Developer",
              "description": "Fully remote role.",
              "remote": false,
              "url": "https://www.arbeitnow.com/jobs/remote-noop-check",
              "tags": ["android"],
              "job_types": ["full-time"],
              "location": "Berlin",
              "created_at": 1785499244
            }
          ],
          "meta": { "count": 1, "page": 1 }
        }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val provider = ArbeitnowProvider(
            jobCache = mockk(relaxed = true),
            okHttpClient = OkHttpClient(),
            baseRetrofit = buildRetrofit(mockWebServer),
            baseUrl = mockWebServer.url("/").toString()
        )
        provider.onInitialize()
        provider.onStart()

        provider.searchJobs(
            com.bangersoul.aivance.core.common.model.JobSearchFilter(
                remoteType = com.bangersoul.aivance.core.common.enums.RemoteType.REMOTE
            ),
            com.bangersoul.aivance.core.common.enums.JobSortOrder.RELEVANCE,
            1
        )

        val recordedUrl = mockWebServer.takeRequest().path!!
        assertFalse(
            "Expected no remote= param (API ignores it), got: $recordedUrl",
            recordedUrl.contains("remote=")
        )
    }

    @Test
    fun `provider metadata is correct`() {
        val provider = ArbeitnowProvider(
            jobCache = mockk<JobCache>(),
            okHttpClient = OkHttpClient(),
            baseRetrofit = buildRetrofit(mockWebServer)
        )
        assertEquals("arbeitnow", provider.metadata.id)
        assertEquals("Arbeitnow", provider.metadata.name)
        assertTrue(provider.hasCapability(ProviderCapability.JobSearch))
    }

    private fun buildApi(server: MockWebServer): ArbeitnowApi =
        buildRetrofit(server).create(ArbeitnowApi::class.java)

    private fun buildRetrofit(server: MockWebServer): Retrofit {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
