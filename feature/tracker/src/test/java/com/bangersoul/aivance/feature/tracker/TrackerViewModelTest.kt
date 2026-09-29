package com.bangersoul.aivance.feature.tracker

import app.cash.turbine.test
import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.model.ApplicationStage
import com.bangersoul.aivance.core.common.model.CareerState
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.analytics.KPIEngine
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.repository.AnalyticsRepository
import com.bangersoul.aivance.core.domain.repository.ApplicationPreferencesRepository
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.workflow.TaskGeneratorUseCase
import com.bangersoul.aivance.core.domain.workflow.WorkflowEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class TrackerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository: ApplicationWorkflowRepository = mockk()
    private val mockAnalyticsRepository: AnalyticsRepository = mockk()
    private val mockTaskGenerator: TaskGeneratorUseCase = mockk()
    private val mockCareerStateEngine: CareerStateEngine = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()
    private val mockJobRepository: JobRepository = mockk()
    private val mockApplicationPreferences: ApplicationPreferencesRepository = mockk()

    private lateinit var viewModel: TrackerViewModel

    private fun buildViewModel(): TrackerViewModel {
        val workflowEngine = WorkflowEngine(
            mockRepository,
            mockAnalyticsRepository,
            mockTaskGenerator,
            mockk(relaxed = true),
            // NotificationRepository — added to WorkflowEngine when pipeline
            // events started recording into the inbox; this fixture was never
            // updated, so the module's unit tests did not compile.
            mockk(relaxed = true)
        )
        return TrackerViewModel(
            mockRepository, workflowEngine, mockCareerStateEngine, mockTrackEvent, mockJobRepository,
            mockApplicationPreferences, KPIEngine()
        )
    }

    private val stages = listOf(
        ApplicationStage(id = "SAVED", label = "Saved", order = 1),
        ApplicationStage(id = "APPLIED", label = "Applied", order = 2),
        ApplicationStage(id = "INTERVIEWING", label = "Interviewing", order = 3)
    )

    private fun sampleApp(stageId: String = "SAVED") = Application(
        id = 1L,
        jobId = 10L,
        currentStageId = stageId,
        status = "ACTIVE"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockTrackEvent(any()) } returns Result.Success(Unit)
        coEvery { mockAnalyticsRepository.createSnapshot() } returns Result.Success(1L)
        coEvery { mockRepository.getStages() } returns flowOf(Result.Success(stages))
        every { mockCareerStateEngine.state } returns MutableStateFlow(CareerState())
        coEvery { mockTaskGenerator.invoke(any()) } returns Result.Success(Unit)
        every { mockApplicationPreferences.dailyApplicationCap } returns flowOf(5)
        coEvery { mockApplicationPreferences.setDailyApplicationCap(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(emptyList()))

        viewModel = buildViewModel()

        assertEquals(TrackerUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `empty applications show Success with empty list`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(emptyList()))

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TrackerUiState.Success)
        assertEquals(0, (state as TrackerUiState.Success).applications.size)
    }

    @Test
    fun `pipeline metrics report null conversion when nothing applied`() = runTest {
        // Only a bookmarked (SAVED) job: the interview/offer ratios are 0/0 and must be null
        // so the UI renders "not enough data" instead of a fabricated 0% conversion (R2).
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(listOf(sampleApp("SAVED"))))

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as TrackerUiState.Success
        assertNull(state.pipelineMetrics.interviewRate)
        assertNull(state.pipelineMetrics.offerRate)
    }

    @Test
    fun `pipeline metrics compute conversion over applied applications`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(
            Result.Success(listOf(sampleApp("APPLIED"), sampleApp("INTERVIEW")))
        )

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as TrackerUiState.Success
        // 1 of 2 applied reached the interview stage.
        assertEquals(50, state.pipelineMetrics.interviewRate)
        assertEquals(0, state.pipelineMetrics.offerRate)
    }

    @Test
    fun `applications show in Success state`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(listOf(sampleApp())))

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TrackerUiState.Success)
        assertEquals(1, (state as TrackerUiState.Success).applications.size)
        assertEquals(stages.size, (state as TrackerUiState.Success).stages.size)
    }

    @Test
    fun `error when repository fails to load`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(
            Result.Failure(com.bangersoul.aivance.core.common.result.DomainError("Failed"))
        )

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is TrackerUiState.Error)
    }

    @Test
    fun `update stage saves application and tracks event`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(listOf(sampleApp())))
        coEvery { mockRepository.getApplicationById(1L) } returns flowOf(Result.Success(sampleApp()))
        coEvery { mockRepository.saveApplication(any()) } returns Result.Success(1L)
        coEvery { mockRepository.addTimelineEvent(any()) } returns Result.Success(1L)

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TrackerUiEvent.UpdateStage(applicationId = 1L, stageId = "INTERVIEWING"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.saveApplication(any()) }
        coVerify { mockRepository.addTimelineEvent(any()) }
        coVerify { mockTrackEvent(TrackEventRequest("tracker_stage_update")) }
    }

    @Test
    fun `delete application calls repository`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(listOf(sampleApp())))
        coEvery { mockRepository.deleteApplication(1L) } returns Result.Success(Unit)

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TrackerUiEvent.DeleteApplication(id = 1L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.deleteApplication(1L) }
    }

    @Test
    fun `manual add application caches job and saves application`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(listOf(sampleApp())))
        coEvery { mockJobRepository.cacheJob(any()) } returns Result.Success(10L)
        coEvery { mockRepository.saveApplication(any()) } returns Result.Success(7L)
        coEvery { mockRepository.addTimelineEvent(any()) } returns Result.Success(1L)

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TrackerUiEvent.AddApplication("Acme", "Android Engineer", "SAVED"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockJobRepository.cacheJob(any()) }
        coVerify { mockRepository.saveApplication(match { it.jobId == 10L && it.currentStageId == "SAVED" }) }
        coVerify { mockRepository.addTimelineEvent(any()) }
        coVerify { mockTrackEvent(TrackEventRequest("tracker_manual_add")) }
    }

    @Test
    fun `manual add with blank role shows snackbar and does not save`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(listOf(sampleApp())))

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TrackerUiEvent.AddApplication("Acme", "  ", "SAVED"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { mockJobRepository.cacheJob(any()) }
        coVerify(exactly = 0) { mockRepository.saveApplication(any()) }
    }

    @Test
    fun `trackJob with already-tracked job selects its application`() = runTest {
        // The application's joined job carries the cached job's DB id as a string,
        // which the repository normalizes the external id into before matching.
        val trackedJob = com.bangersoul.aivance.core.common.model.JobListing(
            id = "10", title = "Android Engineer", company = "Acme",
            description = "Kotlin", url = "https://acme.com/jobs/1", sourceProvider = "test"
        )
        coEvery { mockRepository.getApplications() } returns flowOf(
            Result.Success(listOf(sampleApp().copy(id = 7L, jobId = 10L, job = trackedJob)))
        )
        coEvery { mockJobRepository.getJobById("job-1") } returns Result.Success(
            trackedJob
        )

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TrackerUiEvent.TrackJob("job-1"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as TrackerUiState.Success
        assertEquals(7L, state.selectedApplicationId)
        assertEquals(null, state.pendingTrackJob)
    }

    @Test
    fun `trackJob with untracked job pre-fills pending job for the add dialog`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(emptyList()))
        coEvery { mockJobRepository.getJobById("job-1") } returns Result.Success(
            com.bangersoul.aivance.core.common.model.JobListing(
                id = "job-1",
                title = "Android Engineer",
                company = "Acme",
                description = "Kotlin + Compose",
                url = "https://acme.com/jobs/android",
                sourceProvider = "test"
            )
        )

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TrackerUiEvent.TrackJob("job-1"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as TrackerUiState.Success
        assertEquals("Android Engineer", state.pendingTrackJob?.title)
        assertEquals("Acme", state.pendingTrackJob?.company)
    }

    @Test
    fun `clearPendingTrackJob drops the prefilled job`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(emptyList()))
        coEvery { mockJobRepository.getJobById("job-1") } returns Result.Success(
            com.bangersoul.aivance.core.common.model.JobListing(
                id = "job-1", title = "Engineer", company = "Acme",
                description = "", url = "https://acme.com", sourceProvider = "test"
            )
        )

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(TrackerUiEvent.TrackJob("job-1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TrackerUiEvent.ClearPendingTrackJob)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as TrackerUiState.Success
        assertEquals(null, state.pendingTrackJob)
    }

    @Test
    fun `success state carries today's application count and cap`() = runTest {
        val now = System.currentTimeMillis()
        coEvery { mockRepository.getApplications() } returns flowOf(
            Result.Success(
                listOf(
                    sampleApp().copy(id = 1L, dateApplied = now),
                    sampleApp().copy(id = 2L, dateApplied = now - 86_400_000L) // yesterday
                )
            )
        )

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as TrackerUiState.Success
        assertEquals(1, state.todayAppliedCount)
        assertEquals(5, state.dailyCap)
    }

    @Test
    fun `application without date is not counted as today`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(
            Result.Success(listOf(sampleApp())) // dateApplied = null
        )

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as TrackerUiState.Success
        assertEquals(0, state.todayAppliedCount)
    }

    @Test
    fun `set daily cap persists the new cap`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(emptyList()))

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TrackerUiEvent.SetDailyCap(10))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockApplicationPreferences.setDailyApplicationCap(10) }
        coVerify { mockTrackEvent(TrackEventRequest("tracker_daily_cap_set")) }
    }

    @Test
    fun `refresh reloads applications`() = runTest {
        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(listOf(sampleApp())))

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { mockRepository.getApplications() } returns flowOf(Result.Success(emptyList()))
        viewModel.onEvent(TrackerUiEvent.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TrackerUiState.Success)
        assertEquals(0, (state as TrackerUiState.Success).applications.size)
    }
}
