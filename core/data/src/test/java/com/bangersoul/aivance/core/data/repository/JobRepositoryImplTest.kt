package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.enums.RemotePolicy
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.data.company.CompanyCatalog
import com.bangersoul.aivance.core.data.job.JobFilterMatcher
import com.bangersoul.aivance.core.data.job.JobNormalizer
import com.bangersoul.aivance.core.database.dao.CompanyDao
import com.bangersoul.aivance.core.database.dao.JobDao
import com.bangersoul.aivance.core.database.model.CompanyEntity
import com.bangersoul.aivance.core.database.model.JobEntity
import com.bangersoul.aivance.core.database.model.JobWithDetails
import com.bangersoul.aivance.core.domain.repository.ProviderRepository
import com.bangersoul.aivance.sdk.api.JobProvider
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JobRepositoryImplTest {

    private lateinit var repository: JobRepositoryImpl
    private val jobDao: JobDao = mockk()
    private val companyDao: CompanyDao = mockk()
    private val providerRegistry: ProviderRegistry = mockk()
    private val normalizer = JobNormalizer()
    private val filterMatcher = JobFilterMatcher()
    private val providerRepository: ProviderRepository = mockk()
    private val companyCatalog = CompanyCatalog.fromJson(
        """
        [
          {"name": "Fully Remote Inc", "remote_policy": "fully-remote", "technologies": ["kotlin"]},
          {"name": "On Site Ltd", "remote_policy": "hybrid", "technologies": ["java"]}
        ]
        """.trimIndent()
    )

    @Before
    fun setUp() {
        coEvery { jobDao.getJobsWithDetails() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { providerRepository.getProviderConfigs() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        repository = JobRepositoryImpl(jobDao, companyDao, providerRegistry, normalizer, filterMatcher, companyCatalog, providerRepository)
    }

    @Test
    fun `searchJobs returns empty success when no providers available`() = runTest {
        every { providerRegistry.getAllProviders() } returns emptyList()

        val result = repository.searchJobs(JobSearchFilter(query = "engineer"), JobSortOrder.RELEVANCE)

        assertTrue(result is Result.Success)
        assertEquals(emptyList<JobListing>(), (result as Result.Success).data)
    }

    @Test
    fun `searchJobs skips providers explicitly disabled in Provider Management`() = runTest {
        val disabledProvider = mockk<JobProvider>()
        every { disabledProvider.metadata } returns ProviderMetadata(
            id = "disabled",
            name = "Disabled",
            type = ProviderType.JOB,
            version = "1.0.0",
            description = "Disabled provider",
            author = "Test"
        )
        every { disabledProvider.status } returns ProviderStatus.Active
        coEvery { disabledProvider.searchJobs(any(), any(), any()) } returns Result.Success(
            listOf(
                JobListing(
                    id = "1", title = "Should Not Appear", company = "Fully Remote Inc",
                    location = "Remote", description = "d", url = "https://a.com/1", sourceProvider = "disabled"
                )
            )
        )
        // Provider has a persisted config with isEnabled=false — the search must
        // not query it, even though it is Active.
        coEvery { providerRepository.getProviderConfigs() } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                ProviderConfiguration(
                    providerId = "disabled",
                    settings = mapOf("isEnabled" to "false"),
                    secrets = emptyMap()
                )
            )
        )
        every { providerRegistry.getAllProviders() } returns listOf(disabledProvider)

        val result = repository.searchJobs(JobSearchFilter(query = "android"), JobSortOrder.RELEVANCE)

        assertTrue(result is Result.Success)
        assertEquals(emptyList<JobListing>(), (result as Result.Success).data)
    }

    @Test
    fun `searchJobs fails when every provider fails instead of masquerading cached DB as fresh`() = runTest {
        val provider = mockk<JobProvider>()
        every { provider.metadata } returns ProviderMetadata(
            id = "flaky",
            name = "Flaky",
            type = ProviderType.JOB,
            version = "1.0.0",
            description = "Flaky provider",
            author = "Test"
        )
        every { provider.status } returns ProviderStatus.Active
        coEvery { provider.searchJobs(any(), any(), any()) } returns Result.Failure(
            com.bangersoul.aivance.core.common.result.ProviderError("flaky", message = "network down")
        )
        every { providerRegistry.getAllProviders() } returns listOf(provider)
        // A populated cache table MUST NOT be served as a successful search result
        // when the only provider failed — that was the seed/cache deception.
        coEvery { jobDao.getJobsWithDetails() } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                JobWithDetails(
                    job = JobEntity(
                        id = 99L, companyId = 1L, title = "Stale Cached Job", location = "Remote",
                        type = "FULL_TIME", remoteType = "REMOTE", experienceLevel = "SENIOR",
                        salaryMin = null, salaryMax = null, currency = null, description = "d",
                        descriptionHtml = null, url = "https://stale.com/1", sourceProviderId = "flaky",
                        postedDate = System.currentTimeMillis()
                    ),
                    company = CompanyEntity(
                        id = 1L, name = "Stale Co", domain = null, logoUrl = null,
                        website = null, industry = null, headquarters = null
                    )
                )
            )
        )

        val result = repository.searchJobs(JobSearchFilter(query = "engineer"), JobSortOrder.RELEVANCE)

        assertTrue("all-provider failure must surface as Failure, not stale cache", result.isFailure)
    }

    @Test
    fun `searchJobs returns empty success when a provider succeeds with no matches`() = runTest {
        val provider = mockk<JobProvider>()
        every { provider.metadata } returns ProviderMetadata(
            id = "empty",
            name = "Empty",
            type = ProviderType.JOB,
            version = "1.0.0",
            description = "Empty provider",
            author = "Test"
        )
        every { provider.status } returns ProviderStatus.Active
        // A genuine "no matches" answer — the provider succeeded, just found nothing.
        coEvery { provider.searchJobs(any(), any(), any()) } returns Result.Success(emptyList())
        every { providerRegistry.getAllProviders() } returns listOf(provider)
        // Even with a populated cache, a successful-but-empty search stays empty.
        coEvery { jobDao.getJobsWithDetails() } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                JobWithDetails(
                    job = JobEntity(
                        id = 99L, companyId = 1L, title = "Cached Job", location = "Remote",
                        type = "FULL_TIME", remoteType = "REMOTE", experienceLevel = "SENIOR",
                        salaryMin = null, salaryMax = null, currency = null, description = "d",
                        descriptionHtml = null, url = "https://c.com/1", sourceProviderId = "empty",
                        postedDate = System.currentTimeMillis()
                    ),
                    company = CompanyEntity(
                        id = 1L, name = "Cached Co", domain = null, logoUrl = null,
                        website = null, industry = null, headquarters = null
                    )
                )
            )
        )

        val result = repository.searchJobs(JobSearchFilter(query = "engineer"), JobSortOrder.RELEVANCE)

        assertTrue(result is Result.Success)
        assertEquals(emptyList<JobListing>(), (result as Result.Success).data)
    }

    @Test
    fun `searchJobs applies remote-policy catalog filter to provider results`() = runTest {
        val provider = mockk<JobProvider>()
        every { provider.metadata } returns ProviderMetadata(
            id = "test",
            name = "Test",
            type = ProviderType.JOB,
            version = "1.0.0",
            description = "Test provider",
            author = "Test"
        )
        every { provider.status } returns ProviderStatus.Active
        coEvery { provider.searchJobs(any(), any(), any()) } returns Result.Success(
            listOf(
                JobListing(
                    id = "1", title = "Android Dev", company = "Fully Remote Inc",
                    location = "Remote", description = "d", url = "https://a.com/1", sourceProvider = "test"
                ),
                JobListing(
                    id = "2", title = "Java Dev", company = "On Site Ltd",
                    location = "Berlin", description = "d", url = "https://a.com/2", sourceProvider = "test"
                )
            )
        )
        every { providerRegistry.getAllProviders() } returns listOf(provider)
        // Caching path inside searchJobs.
        coEvery { jobDao.getJobByUrl(any()) } returns null
        coEvery { companyDao.insertCompany(any()) } returns 1L
        coEvery { jobDao.insertJob(any()) } returns 1L

        val result = repository.searchJobs(
            JobSearchFilter(remotePolicy = RemotePolicy.FULLY_REMOTE),
            JobSortOrder.RELEVANCE
        )

        assertTrue(result is Result.Success)
        val jobs = (result as Result.Success).data
        assertEquals(1, jobs.size)
        assertEquals("Fully Remote Inc", jobs.single().company)
    }

    @Test
    fun `getJobById returns success when job exists`() = runTest {
        val jobWithDetails = JobWithDetails(
            job = JobEntity(
                id = 1L,
                companyId = 1L,
                title = "Engineer",
                location = "Remote",
                type = "FULL_TIME",
                remoteType = "REMOTE",
                experienceLevel = "SENIOR",
                salaryMin = 100.0,
                salaryMax = 200.0,
                currency = "USD",
                description = "desc",
                descriptionHtml = null,
                url = "url",
                sourceProviderId = "provider",
                postedDate = System.currentTimeMillis()
            ),
            company = CompanyEntity(
                id = 1L,
                name = "Tech",
                domain = null,
                logoUrl = null,
                website = null,
                industry = null,
                headquarters = null
            )
        )
        coEvery { jobDao.getJobWithDetailsById(1L) } returns jobWithDetails

        val result = repository.getJobById("1")

        assertTrue(result is Result.Success)
        assertEquals("Engineer", (result as Result.Success).data.title)
        assertEquals("Tech", (result as Result.Success).data.company)
    }

    @Test
    fun `getJobById returns failure when job not found`() = runTest {
        // runCatchingCore wraps every Throwable into Result.Failure.
        coEvery { jobDao.getJobWithDetailsById(1L) } returns null
        every { providerRegistry.getAllProviders() } returns emptyList()

        val result = repository.getJobById("1")

        assertTrue(result.isFailure)
        assertEquals("Job not found: 1", (result as Result.Failure).error.message)
    }

    @Test
    fun `cacheJob returns the existing stable id, never the upsert update-path -1`() = runTest {
        // jobDao.insertJob is @Upsert: it returns -1L on the ON CONFLICT DO UPDATE
        // path. Re-caching a job already in the DB (matched here by URL) must hand
        // back that job's real, stable id — JobDetailsViewModel uses it as the
        // application / cover-letter foreign key, so -1L would corrupt the FK.
        val existing = JobEntity(
            id = 99L, companyId = 1L, title = "Cached", location = "Remote",
            type = "FULL_TIME", remoteType = null, experienceLevel = null,
            salaryMin = null, salaryMax = null, currency = null, description = "d",
            descriptionHtml = null, url = "https://x.com/1", sourceProviderId = "p",
            postedDate = 0L
        )
        coEvery { companyDao.insertCompany(any()) } returns 1L
        coEvery { jobDao.getJobByUrl("https://x.com/1") } returns existing
        coEvery { jobDao.insertJob(any()) } returns -1L // update path

        val result = repository.cacheJob(
            JobListing(
                id = "external-ref", title = "Cached", company = "Tech",
                location = "Remote", description = "d", url = "https://x.com/1",
                sourceProvider = "p"
            )
        )

        assertTrue(result is Result.Success)
        assertEquals(99L, (result as Result.Success).data)
    }

    @Test
    fun `cacheJob returns the fresh rowid for a job not previously cached`() = runTest {
        coEvery { companyDao.insertCompany(any()) } returns 1L
        coEvery { jobDao.getJobByUrl(any()) } returns null
        coEvery { jobDao.insertJob(any()) } returns 7L // fresh insert path

        val result = repository.cacheJob(
            JobListing(
                id = "external-ref", title = "New", company = "Tech",
                location = "Remote", description = "d", url = "https://y.com/2",
                sourceProvider = "p"
            )
        )

        assertTrue(result is Result.Success)
        assertEquals(7L, (result as Result.Success).data)
    }
}
