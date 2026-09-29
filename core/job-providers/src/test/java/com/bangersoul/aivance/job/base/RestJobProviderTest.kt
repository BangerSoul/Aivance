package com.bangersoul.aivance.job.base

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.core.ProviderStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class RestJobProviderTest {

    private lateinit var jobCache: JobCache
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var retrofit: Retrofit
    private lateinit var provider: TestRestJobProvider

    @Before
    fun setUp() {
        jobCache = mockk(relaxed = true)
        okHttpClient = mockk(relaxed = true)
        retrofit = mockk(relaxed = true)
        provider = TestRestJobProvider(jobCache, okHttpClient, retrofit)
    }

    @Test
    fun `initial status is Uninitialized`() {
        assertEquals(ProviderStatus.Uninitialized, provider.currentStatus)
    }

    @Test
    fun `onInitialize transitions to Ready`() = runTest {
        provider.triggerInitialize()
        assertEquals(ProviderStatus.Ready, provider.currentStatus)
    }

    @Test
    fun `onStart transitions to Active`() = runTest {
        provider.triggerStart()
        assertEquals(ProviderStatus.Active, provider.currentStatus)
    }

    @Test
    fun `onStop transitions to Ready`() = runTest {
        provider.triggerStart()
        provider.triggerStop()
        assertEquals(ProviderStatus.Ready, provider.currentStatus)
    }

    @Test
    fun `onDispose transitions to Disposed`() = runTest {
        provider.triggerDispose()
        assertEquals(ProviderStatus.Disposed, provider.currentStatus)
    }

    @Test
    fun `searchJobs caches results on success`() = runTest {
        val jobs = listOf(
            JobListing(id = "1", title = "Engineer", company = "Co", description = "", url = "", sourceProvider = "test"),
            JobListing(id = "2", title = "Designer", company = "Co", description = "", url = "", sourceProvider = "test")
        )
        provider.searchJobsResult = jobs
        coEvery { jobCache.saveJobs(any()) } returns Unit

        val result = provider.searchJobs(JobSearchFilter(), JobSortOrder.RELEVANCE, 1)

        assertTrue(result is Result.Success)
        assertEquals(2, (result as Result.Success).data.size)
        coVerify { jobCache.saveJobs(jobs) }
    }

    @Test
    fun `searchJobs falls back to cache on failure`() = runTest {
        provider.searchJobsException = Exception("Network error")
        val cachedJobs = listOf(
            JobListing(id = "1", title = "Cached Job", company = "Co", description = "", url = "", sourceProvider = "test-provider")
        )
        coEvery { jobCache.getJobs() } returns cachedJobs

        val result = provider.searchJobs(JobSearchFilter(), JobSortOrder.RELEVANCE, 1)

        assertTrue(result is Result.Success)
        assertEquals("Cached Job", (result as Result.Success).data[0].title)
    }

    @Test
    fun `searchJobs never serves another providers cached jobs on failure`() = runTest {
        provider.searchJobsException = Exception("Network error")
        // The shared cache holds jobs stamped with a DIFFERENT provider's id —
        // they must not be echoed as this provider's results (the "dummy
        // results" bug where every unconfigured provider returned the whole DB).
        val otherProvidersJobs = listOf(
            JobListing(id = "9", title = "Someone Else's Cache", company = "Co", description = "", url = "", sourceProvider = "other-provider")
        )
        coEvery { jobCache.getJobs() } returns otherProvidersJobs

        val result = provider.searchJobs(JobSearchFilter(), JobSortOrder.RELEVANCE, 1)

        assertTrue(result is Result.Failure)
    }

    @Test
    fun `searchJobs fails if no cache available and network fails`() = runTest {
        provider.searchJobsException = Exception("Network error")
        coEvery { jobCache.getJobs() } returns emptyList()

        val result = provider.searchJobs(JobSearchFilter(), JobSortOrder.RELEVANCE, 1)

        assertTrue(result is Result.Failure)
        assertEquals("Network error", (result as Result.Failure).error.message)
    }

    @Test
    fun `circuit breaker degrades provider after error threshold`() = runTest {
        provider.searchJobsException = Exception("Fail")

        repeat(2) {
            provider.searchJobs(JobSearchFilter(), JobSortOrder.RELEVANCE, 1)
        }
        // After 2 failures (errorThreshold = 2 for test), should be Degraded
        assertEquals(ProviderStatus.Degraded, provider.currentStatus)
    }

    @Test
    fun `successful search resets error counter and restores Active`() = runTest {
        provider.searchJobsException = Exception("Fail")

        // Two failures to degrade
        repeat(2) {
            provider.searchJobs(JobSearchFilter(), JobSortOrder.RELEVANCE, 1)
        }
        assertEquals(ProviderStatus.Degraded, provider.currentStatus)

        // Now succeed
        provider.searchJobsException = null
        provider.searchJobsResult = listOf(JobListing(id = "1", title = "OK", company = "Co", description = "", url = "", sourceProvider = "test"))
        coEvery { jobCache.saveJobs(any()) } returns Unit

        val result = provider.searchJobs(JobSearchFilter(), JobSortOrder.RELEVANCE, 1)

        assertTrue(result is Result.Success)
        assertEquals(ProviderStatus.Active, provider.currentStatus)
    }

    @Test
    fun `searchJobs returns error immediately if status is Error`() = runTest {
        provider.setStatus(ProviderStatus.Error)

        val result = provider.searchJobs(JobSearchFilter(), JobSortOrder.RELEVANCE, 1)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message?.contains("Error state") == true)
    }

    private class TestRestJobProvider(
        jobCache: JobCache,
        okHttpClient: OkHttpClient,
        retrofit: Retrofit
    ) : RestJobProvider(
        metadata = ProviderMetadata(
            id = "test-provider",
            name = "Test Provider",
            type = ProviderType.JOB,
            version = "1.0.0",
            description = "Test",
            author = "Test"
        ),
        capabilities = setOf(ProviderCapability.JobSearch),
        jobCache = jobCache,
        baseOkHttpClient = okHttpClient,
        baseRetrofit = retrofit,
        errorThreshold = 2
    ) {
        override val baseUrl: String = "https://test.example.com/"

        var searchJobsResult: List<JobListing>? = null
        var searchJobsException: Exception? = null

        override suspend fun executeSearch(
            filter: JobSearchFilter,
            sortOrder: JobSortOrder,
            page: Int
        ): List<JobListing> {
            searchJobsException?.let { throw it }
            return searchJobsResult ?: emptyList()
        }



        suspend fun triggerInitialize() = onInitialize()
        suspend fun triggerStart() = onStart()
        suspend fun triggerStop() = onStop()
        suspend fun triggerDispose() = onDispose()

        fun setStatus(status: ProviderStatus) = updateStatus(status)

        val currentStatus: ProviderStatus get() = status
    }
}
