package com.bangersoul.aivance.worker

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.bangersoul.aivance.core.common.enums.ExperienceLevel
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.model.SearchResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.database.dao.JobDao
import com.bangersoul.aivance.core.database.model.CompanyEntity
import com.bangersoul.aivance.core.database.model.JobEntity
import com.bangersoul.aivance.core.database.model.JobWithDetails
import com.bangersoul.aivance.core.common.model.NotificationType
import com.bangersoul.aivance.core.datastore.UserPreferences
import com.bangersoul.aivance.core.datastore.UserPreferencesRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.repository.NotificationRepository
import com.bangersoul.aivance.core.domain.repository.SearchRepository
import com.bangersoul.aivance.core.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the completed JobAlertWorker: saved-search driven queries,
 * genuinely-new-job detection, and real notification posting.
 */
class JobAlertWorkerTest {

    private val context = mockk<android.content.Context>(relaxed = true)
    private val params = mockk<WorkerParameters>(relaxed = true)
    private val jobRepository = mockk<JobRepository>()
    private val searchRepository = mockk<SearchRepository>()
    private val jobDao = mockk<JobDao>()
    private val notificationHelper = mockk<NotificationHelper>(relaxed = true)
    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>()

    @Before
    fun setUp() {
        every { userPreferencesRepository.userPreferences } returns flowOf(UserPreferences())
    }

    private fun jobListing(id: String, url: String) = JobListing(
        id = id,
        title = "Senior Android Engineer",
        company = "TestCorp",
        description = "Kotlin, Compose, Hilt",
        url = url,
        sourceProvider = "test",
        experienceLevel = ExperienceLevel.SENIOR_LEVEL
    )

    private fun cachedEntity(url: String) = JobWithDetails(
        job = JobEntity(
            companyId = 1,
            title = "Cached Job",
            location = null,
            type = null,
            remoteType = null,
            experienceLevel = null,
            salaryMin = null,
            salaryMax = null,
            currency = null,
            description = null,
            descriptionHtml = null,
            url = url,
            sourceProviderId = "test",
            postedDate = System.currentTimeMillis()
        ),
        company = CompanyEntity(
            name = "CachedCorp",
            domain = null,
            logoUrl = null,
            website = null,
            industry = null,
            headquarters = null
        )
    )

    @Test
    fun postsNotification_whenNewJobsFound_withFallbackQuery() = runBlocking {
        val found = listOf(
            jobListing("1", "https://example.com/jobs/1"),
            jobListing("2", "https://example.com/jobs/2"),
            jobListing("3", "https://example.com/jobs/3")
        )
        every { searchRepository.getSavedSearches() } returns flowOf(Result.Success(emptyList()))
        every { jobDao.getJobsWithDetails() } returns flowOf(emptyList())
        coEvery { jobRepository.searchJobs(any(), any()) } returns Result.Success(found)

        val worker = JobAlertWorker(
            context, params, jobRepository, searchRepository, jobDao,
            notificationHelper, notificationRepository, userPreferencesRepository
        )
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify { notificationHelper.showJobAlert(any(), any(), any()) }
        // The alert is also recorded in the persisted inbox with the JOB_ALERT type.
        // (timestamp = any(): the default pins the captured call time otherwise.)
        coVerify {
            notificationRepository.record(
                id = "job_alert_periodic_job_alert",
                type = NotificationType.JOB_ALERT,
                title = any(),
                message = any(),
                timestamp = any()
            )
        }
    }

    @Test
    fun skipsNotification_whenNoNewJobs() = runBlocking {
        val found = listOf(jobListing("1", "https://example.com/jobs/1"))
        every { searchRepository.getSavedSearches() } returns flowOf(Result.Success(emptyList()))
        every { jobDao.getJobsWithDetails() } returns flowOf(listOf(cachedEntity("https://example.com/jobs/1")))
        coEvery { jobRepository.searchJobs(any(), any()) } returns Result.Success(found)

        val worker = JobAlertWorker(
            context, params, jobRepository, searchRepository, jobDao,
            notificationHelper, notificationRepository, userPreferencesRepository
        )
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { notificationHelper.showJobAlert(any(), any(), any()) }
        // No new jobs → no inbox entry either.
        coVerify(exactly = 0) {
            notificationRepository.record(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun usesSavedSearchQueries_whenPresent() = runBlocking {
        val saved = SearchResult(
            id = "1",
            query = "Kotlin Engineer",
            totalResults = 0,
            page = 0,
            timestamp = System.currentTimeMillis()
        )
        every { searchRepository.getSavedSearches() } returns flowOf(Result.Success(listOf(saved)))
        every { jobDao.getJobsWithDetails() } returns flowOf(emptyList())
        coEvery { jobRepository.searchJobs(any(), any()) } returns Result.Success(emptyList())

        val worker = JobAlertWorker(
            context, params, jobRepository, searchRepository, jobDao,
            notificationHelper, notificationRepository, userPreferencesRepository
        )
        worker.doWork()

        coVerify {
            jobRepository.searchJobs(
                JobSearchFilter(query = "Kotlin Engineer"),
                com.bangersoul.aivance.core.common.enums.JobSortOrder.RELEVANCE
            )
        }
    }

    @Test
    fun remainsSuccess_whenSearchFails() = runBlocking {
        every { searchRepository.getSavedSearches() } returns flowOf(Result.Success(emptyList()))
        every { jobDao.getJobsWithDetails() } returns flowOf(emptyList())
        coEvery { jobRepository.searchJobs(any(), any()) } returns
            Result.Failure(com.bangersoul.aivance.core.common.result.DomainError("provider down"))

        val worker = JobAlertWorker(
            context, params, jobRepository, searchRepository, jobDao,
            notificationHelper, notificationRepository, userPreferencesRepository
        )
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { notificationHelper.showJobAlert(any(), any(), any()) }
    }

    @Test
    fun retries_whenUnexpectedExceptionThrown() = runBlocking {
        every { searchRepository.getSavedSearches() } throws RuntimeException("boom")

        val worker = JobAlertWorker(
            context, params, jobRepository, searchRepository, jobDao,
            notificationHelper, notificationRepository, userPreferencesRepository
        )
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun skipsTrayNotification_whenJobAlertsDisabled_butStillRecordsInboxEntry() = runBlocking {
        val found = listOf(jobListing("1", "https://example.com/jobs/1"))
        every { searchRepository.getSavedSearches() } returns flowOf(Result.Success(emptyList()))
        every { jobDao.getJobsWithDetails() } returns flowOf(emptyList())
        coEvery { jobRepository.searchJobs(any(), any()) } returns Result.Success(found)
        every { userPreferencesRepository.userPreferences } returns
            flowOf(UserPreferences(jobAlertsEnabled = false))

        val worker = JobAlertWorker(
            context, params, jobRepository, searchRepository, jobDao,
            notificationHelper, notificationRepository, userPreferencesRepository
        )
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { notificationHelper.showJobAlert(any(), any(), any()) }
        coVerify {
            notificationRepository.record(
                id = any(),
                type = NotificationType.JOB_ALERT,
                title = any(),
                message = any(),
                timestamp = any()
            )
        }
    }
}
