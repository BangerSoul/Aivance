package com.bangersoul.aivance.feature.resume

import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.JobDescription
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.AtsRepository
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntelligenceHubViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockResumeRepository: ResumeRepository = mockk()
    private val mockAtsRepository: AtsRepository = mockk()

    private val resume = Resume(
        id = 1L,
        name = "DummyResume",
        primaryVersionId = 1L,
        lastModified = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
    )
    private val report = AtsReport(
        id = 1L,
        resumeVersionId = 1L,
        jobDescriptionId = 7L,
        overallScore = 85,
        matchPercentage = 85,
        dateGenerated = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = IntelligenceHubViewModel(mockResumeRepository, mockAtsRepository)

    @Test
    fun `hub exposes persisted resumes and scans with resolved job titles`() = runTest {
        every { mockResumeRepository.getResumes() } returns flowOf(Result.Success(listOf(resume)))
        every { mockAtsRepository.getAllReports() } returns flowOf(Result.Success(listOf(report)))
        coEvery { mockAtsRepository.getJobDescription(7L) } returns JobDescription(
            id = 7L,
            companyName = "Google",
            jobTitle = "Android Engineer",
            rawText = "jd"
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.resumes.size)
        assertEquals("DummyResume", state.resumes.first().name)
        assertEquals(1, state.atsScans.size)
        assertEquals("Google", state.atsScans.first().companyName)
        assertEquals("Android Engineer", state.atsScans.first().jobTitle)
        assertEquals(85, state.atsScans.first().report.overallScore)
        assertTrue(!state.isLoading)
    }

    @Test
    fun `hub falls back to generic labels when the job description is missing`() = runTest {
        every { mockResumeRepository.getResumes() } returns flowOf(Result.Success(listOf(resume)))
        every { mockAtsRepository.getAllReports() } returns flowOf(Result.Success(listOf(report)))
        coEvery { mockAtsRepository.getJobDescription(7L) } returns null

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val scan = viewModel.uiState.value.atsScans.first()
        assertEquals(null, scan.jobTitle)
        assertEquals(null, scan.companyName)
    }

    @Test
    fun `deleteReport delegates to the repository`() = runTest {
        every { mockResumeRepository.getResumes() } returns flowOf(Result.Success(listOf(resume)))
        every { mockAtsRepository.getAllReports() } returns flowOf(Result.Success(listOf(report)))
        coEvery { mockAtsRepository.getJobDescription(7L) } returns JobDescription(
            id = 7L,
            companyName = "Google",
            jobTitle = "Android Engineer",
            rawText = "jd"
        )
        coEvery { mockAtsRepository.deleteReport(1L) } returns Result.Success(Unit)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.atsScans.size)

        viewModel.deleteReport(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockAtsRepository.deleteReport(1L) }
    }

    @Test
    fun `hub shows empty lists when nothing is persisted`() = runTest {
        every { mockResumeRepository.getResumes() } returns flowOf(Result.Success(emptyList()))
        every { mockAtsRepository.getAllReports() } returns flowOf(Result.Success(emptyList()))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.resumes.isEmpty())
        assertTrue(state.atsScans.isEmpty())
        assertTrue(!state.isLoading)
    }
}
