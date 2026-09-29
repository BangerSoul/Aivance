package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.database.dao.CompanyDao
import com.bangersoul.aivance.core.database.dao.JobDao
import com.bangersoul.aivance.core.database.dao.SearchDao
import com.bangersoul.aivance.core.data.source.JobLocalDataSourceImpl
import com.bangersoul.aivance.core.database.model.CompanyEntity
import com.bangersoul.aivance.core.database.model.JobEntity
import com.bangersoul.aivance.core.database.model.JobWithDetails
import com.bangersoul.aivance.core.database.model.SavedSearchEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Repository integration tests using MockK for backing DAOs.
 *
 * Tests verify the data layer mapping from Room entities to domain models,
 * covering the full flow: DAO → Entity → Mapper → Domain → Repository.
 */
class JobRepositoryIntegrationTest {

    private lateinit var mockJobDao: JobDao
    private lateinit var mockCompanyDao: CompanyDao
    private lateinit var mockSearchDao: SearchDao
    private lateinit var localDataSource: JobLocalDataSourceImpl

    @Before
    fun setup() {
        mockJobDao = mockk()
        mockCompanyDao = mockk()
        mockSearchDao = mockk()
        localDataSource = JobLocalDataSourceImpl(
            jobDao = mockJobDao,
            companyDao = mockCompanyDao,
            searchDao = mockSearchDao
        )
    }

    private fun sampleCompany() = CompanyEntity(
        id = 1, name = "Tech Corp", domain = null,
        logoUrl = null, website = null, industry = "Technology", headquarters = null
    )

    private fun sampleJob(
        id: Long,
        title: String,
        location: String = "Remote",
        description: String = "Build apps",
        sourceProviderId: String = "PROVIDER"
    ) = JobEntity(
        id = id, companyId = 1, title = title,
        location = location, type = "FULL_TIME", remoteType = "ON_SITE",
        experienceLevel = "SENIOR", salaryMin = 150_000.0, salaryMax = 200_000.0,
        currency = "USD", description = description,
        descriptionHtml = null, url = "https://tech.com/job/$id",
        sourceProviderId = sourceProviderId, postedDate = System.currentTimeMillis()
    )

    @Test
    fun `getJobs maps entities to domain correctly`() = runBlocking {
        val companyEntity = sampleCompany()
        val sampleEntities = listOf(
            sampleJob(1, "Android Engineer", location = "Mountain View"),
            sampleJob(2, "iOS Engineer", location = "Cupertino")
        )

        every { mockJobDao.getJobsWithDetails() } returns flowOf(
            sampleEntities.map { entity ->
                JobWithDetails(job = entity, company = companyEntity)
            }
        )

        val jobs = localDataSource.getJobs().first()

        assertEquals(2, jobs.size)
        assertEquals("Android Engineer", jobs[0].title)
        assertEquals("Tech Corp", jobs[0].company)
        assertEquals("iOS Engineer", jobs[1].title)
    }

    @Test
    fun `saveJob inserts entity and returns id`() = runBlocking {
        val sampleJob = com.bangersoul.aivance.core.common.model.JobListing(
            id = "1", title = "Backend Engineer", company = "DataCo",
            location = "Remote", description = "Build APIs", url = "",
            sourceProvider = "REMOTE_OK"
        )

        coEvery { mockCompanyDao.insertCompany(any()) } returns 1L
        coEvery { mockJobDao.insertJob(any()) } returns 1L

        val companyId = localDataSource.saveCompany(
            com.bangersoul.aivance.core.common.model.Company(
                id = "1", name = "DataCo"
            )
        )
        val jobId = localDataSource.saveJob(sampleJob, companyId)

        assertTrue(jobId > 0)
    }

    @Test
    fun `getSavedSearches maps search entities`() = runBlocking {
        val sampleEntities = listOf(
            SavedSearchEntity(
                query = "Android Developer",
                filters = mapOf("location" to "Remote", "isRemote" to "true"),
                dateCreated = java.time.Instant.now()
            )
        )

        every { mockSearchDao.getSavedSearches() } returns flowOf(sampleEntities)

        val searches = localDataSource.getSavedSearches().first()

        assertEquals(1, searches.size)
        assertEquals("Android Developer", searches[0].keywords)
        assertTrue(searches[0].isRemote)
    }

    @Test
    fun `getJobById returns null when not found`() = runBlocking {
        coEvery { mockJobDao.getJobWithDetailsById(any()) } returns null
        val job = localDataSource.getJobById(999L)
        assertEquals(null, job)
    }
}
