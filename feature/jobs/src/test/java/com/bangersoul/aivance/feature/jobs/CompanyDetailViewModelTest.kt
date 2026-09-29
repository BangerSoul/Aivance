package com.bangersoul.aivance.feature.jobs

import app.cash.turbine.test
import com.bangersoul.aivance.core.common.enums.RemotePolicy
import com.bangersoul.aivance.core.common.model.CompanyCatalogEntry
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.CompanyCatalogRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockJobRepository: JobRepository = mockk()
    private val mockCatalogRepository: CompanyCatalogRepository = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()

    private val sampleJob = JobListing(
        id = "job_1",
        title = "Android Engineer",
        company = "Automattic",
        location = "Remote",
        description = "Great role",
        url = "https://automattic.com/jobs/1",
        sourceProvider = "GREENHOUSE"
    )

    private val automatticEntry = CompanyCatalogEntry(
        name = "Automattic",
        website = "https://automattic.com/",
        careersUrl = "https://automattic.com/work-with-us/",
        region = "worldwide",
        remotePolicy = "fully-remote",
        companySize = "enterprise",
        technologies = listOf("kotlin", "javascript", "php")
    )

    private fun createViewModel() = CompanyDetailViewModel(
        jobRepository = mockJobRepository,
        companyCatalogRepository = mockCatalogRepository,
        trackEventUseCase = mockTrackEvent
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockTrackEvent(any()) } returns Result.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load resolves catalog entry by name into success state`() = runTest {
        coEvery { mockJobRepository.getJobs() } returns flowOf(
            Result.Success(listOf(sampleJob))
        )
        coEvery { mockCatalogRepository.findCompany("Automattic") } returns automatticEntry

        val viewModel = createViewModel()
        viewModel.uiState.test {
            viewModel.load("job_1")
            skipItems(1) // initial Loading
            val success = awaitItem() as CompanyDetailUiState.Success
            assertEquals("Automattic", success.companyName)
            assertEquals(RemotePolicy.FULLY_REMOTE, success.catalog?.policy)
            assertEquals("https://automattic.com/work-with-us/", success.catalog?.careersUrl)
            assertEquals(listOf("kotlin", "javascript", "php"), success.catalog?.technologies)
            assertEquals(1, success.openRoles.size)
        }
    }

    @Test
    fun `load falls back to domain lookup when name not indexed`() = runTest {
        coEvery { mockJobRepository.getJobs() } returns flowOf(
            Result.Success(listOf(sampleJob.copy(company = "automattic.com")))
        )
        coEvery { mockCatalogRepository.findCompany("automattic.com") } returns null
        coEvery { mockCatalogRepository.findCompanyByDomain("automattic.com") } returns automatticEntry

        val viewModel = createViewModel()
        viewModel.uiState.test {
            viewModel.load("job_1")
            skipItems(1) // initial Loading
            val success = awaitItem() as CompanyDetailUiState.Success
            assertEquals("Automattic", success.catalog?.name)
        }
    }

    @Test
    fun `load succeeds without catalog entry when company not indexed`() = runTest {
        coEvery { mockJobRepository.getJobs() } returns flowOf(
            Result.Success(listOf(sampleJob.copy(company = "Mystery Startup")))
        )
        coEvery { mockCatalogRepository.findCompany("Mystery Startup") } returns null
        coEvery { mockCatalogRepository.findCompanyByDomain("Mystery Startup") } returns null

        val viewModel = createViewModel()
        viewModel.uiState.test {
            viewModel.load("job_1")
            skipItems(1) // initial Loading
            val success = awaitItem() as CompanyDetailUiState.Success
            assertEquals("Mystery Startup", success.companyName)
            assertNull(success.catalog)
        }
    }

    @Test
    fun `load surfaces error when job feed fails`() = runTest {
        coEvery { mockJobRepository.getJobs() } returns flowOf(
            Result.Failure(DomainError("Feed down"))
        )

        val viewModel = createViewModel()
        viewModel.uiState.test {
            viewModel.load("job_1")
            skipItems(1) // initial Loading
            val state = awaitItem()
            assertTrue(state is CompanyDetailUiState.Error)
            assertTrue((state as CompanyDetailUiState.Error).message.contains("Feed down"))
        }
    }
}
