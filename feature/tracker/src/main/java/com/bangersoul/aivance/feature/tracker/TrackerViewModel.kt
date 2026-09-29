package com.bangersoul.aivance.feature.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.model.ApplicationStage
import com.bangersoul.aivance.core.common.model.CareerState
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.analytics.KPIEngine
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.repository.ApplicationPreferencesRepository
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.workflow.WorkflowEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TrackerUiState {
    data object Loading : TrackerUiState
    data class Success(
        val applications: List<Application> = emptyList(),
        val stages: List<ApplicationStage> = emptyList(),
        val selectedApplicationId: Long? = null,
        val careerState: CareerState? = null,
        val pipelineMetrics: PipelineMetrics = PipelineMetrics(),
        /** Job handed in from a cross-feature jump (e.g. saved job's "Track
         *  application") — the screen pre-fills the Add dialog with it. */
        val pendingTrackJob: JobListing? = null,
        /** Applications created today (R-07) — compared against [dailyCap]. */
        val todayAppliedCount: Int = 0,
        /** Configurable daily application cap (R-07). */
        val dailyCap: Int = 5
    ) : TrackerUiState
    data class Error(val message: String) : TrackerUiState
}

data class PipelineMetrics(
    val activeCount: Int = 0,
    /** Interview conversion (0..100), or `null` when nothing has been applied yet (0/0). */
    val interviewRate: Int? = null,
    /** Offer conversion (0..100), or `null` when nothing has been applied yet (0/0). */
    val offerRate: Int? = null
)

sealed interface TrackerUiEvent {
    data class UpdateStage(val applicationId: Long, val stageId: String) : TrackerUiEvent
    data class DeleteApplication(val id: Long) : TrackerUiEvent
    data class SelectApplication(val applicationId: Long) : TrackerUiEvent
    data object CloseApplication : TrackerUiEvent
    data class UpdateNotes(val applicationId: Long, val notes: String) : TrackerUiEvent
    /** Manually add a job application (company + role) to the selected stage. */
    data class AddApplication(val company: String, val role: String, val stageId: String) : TrackerUiEvent
    /** Pre-select a job from another feature (saved jobs): selects the existing
     *  application for it, or pre-fills the Add dialog when it isn't tracked yet. */
    data class TrackJob(val jobId: String) : TrackerUiEvent
    /** Clears the pending cross-feature job once the Add dialog is dismissed. */
    data object ClearPendingTrackJob : TrackerUiEvent
    data class SetDailyCap(val cap: Int) : TrackerUiEvent
    data object Refresh : TrackerUiEvent
}

@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val repository: ApplicationWorkflowRepository,
    private val workflowEngine: WorkflowEngine,
    private val careerStateEngine: CareerStateEngine,
    private val trackEventUseCase: TrackEventUseCase,
    private val jobRepository: JobRepository,
    private val applicationPreferencesRepository: ApplicationPreferencesRepository,
    private val kpiEngine: KPIEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<TrackerUiState>(TrackerUiState.Loading)
    val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

    private val _effects = Channel<String>(Channel.BUFFERED)
    val effects: Flow<String> = _effects.receiveAsFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            combine(
                repository.getApplications(),
                repository.getStages(),
                careerStateEngine.state,
                applicationPreferencesRepository.dailyApplicationCap
            ) { appsRes, stagesRes, careerState, dailyCap ->
                if (appsRes is Result.Success && stagesRes is Result.Success) {
                    val apps = appsRes.data
                    TrackerUiState.Success(
                        applications = apps,
                        stages = stagesRes.data,
                        careerState = careerState,
                        pipelineMetrics = calculateMetrics(apps),
                        todayAppliedCount = todayAppliedCount(apps),
                        dailyCap = dailyCap
                    )
                } else {
                    TrackerUiState.Error("Failed to load pipeline")
                }
            }.collect { state ->
                val previous = _uiState.value as? TrackerUiState.Success
                _uiState.value = if (state is TrackerUiState.Success) {
                    state.copy(
                        selectedApplicationId = previous?.selectedApplicationId,
                        pendingTrackJob = previous?.pendingTrackJob
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun calculateMetrics(apps: List<Application>): PipelineMetrics {
        val active = apps.filter { it.status == "ACTIVE" }
        // KPIEngine is the single owner of conversion ratios (R4): it returns `null` for the
        // undefined 0/0 case so the UI renders "not enough data" instead of a fake 0%.
        return PipelineMetrics(
            activeCount = active.size,
            interviewRate = kpiEngine.calculateInterviewRate(apps)?.toInt(),
            offerRate = kpiEngine.calculateOfferRate(apps)?.toInt()
        )
    }

    fun onEvent(event: TrackerUiEvent) {
        when (event) {
            is TrackerUiEvent.UpdateStage -> updateStage(event.applicationId, event.stageId)
            is TrackerUiEvent.DeleteApplication -> deleteApplication(event.id)
            is TrackerUiEvent.SelectApplication -> selectApplication(event.applicationId)
            TrackerUiEvent.CloseApplication -> closeApplication()
            is TrackerUiEvent.UpdateNotes -> updateNotes(event.applicationId, event.notes)
            is TrackerUiEvent.AddApplication -> addApplication(event.company, event.role, event.stageId)
            is TrackerUiEvent.TrackJob -> trackJob(event.jobId)
            TrackerUiEvent.ClearPendingTrackJob -> clearPendingTrackJob()
            is TrackerUiEvent.SetDailyCap -> setDailyCap(event.cap)
            TrackerUiEvent.Refresh -> loadData()
        }
    }

    /**
     * Applications created today (R-07): dateApplied falls inside the current
     * local day window. Deleted applications drop out of the count naturally
     * because the list only reflects rows that still exist.
     */
    private fun todayAppliedCount(apps: List<Application>): Int {
        val startOfDay = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return apps.count { (it.dateApplied ?: 0L) >= startOfDay }
    }

    private fun setDailyCap(cap: Int) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("tracker_daily_cap_set"))
            applicationPreferencesRepository.setDailyApplicationCap(cap)
        }
    }

    private fun closeApplication() {
        val current = _uiState.value as? TrackerUiState.Success ?: return
        _uiState.value = current.copy(selectedApplicationId = null)
    }

    private fun updateStage(applicationId: Long, stageId: String) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("tracker_stage_update"))
            repository.getApplicationById(applicationId).firstOrNull()?.let { res ->
                if (res is Result.Success) {
                    val transition = workflowEngine.transitionTo(res.data, stageId)
                    if (transition is Result.Failure) {
                        _effects.send(
                            transition.error.message ?: "Failed to update stage"
                        )
                    }
                }
            }
        }
    }

    private fun deleteApplication(id: Long) {
        viewModelScope.launch {
            repository.deleteApplication(id)
            closeApplication()
        }
    }

    private fun selectApplication(applicationId: Long) {
        val current = _uiState.value as? TrackerUiState.Success ?: return
        _uiState.value = current.copy(selectedApplicationId = applicationId)
    }

    /**
     * Cross-feature jump (e.g. saved job's "Track application"): if the job is
     * already tracked, select its application; otherwise load the job and
     * pre-fill the Add dialog with its company/role so one tap adds it.
     */
    private fun trackJob(jobId: String) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("tracker_track_job"))
            val current = _uiState.value as? TrackerUiState.Success

            // Direct match first — the application's joined job carries the same id
            // when the caller already resolved the job (e.g. via the DB id).
            val direct = current?.applications?.firstOrNull { it.job?.id == jobId }
            if (direct != null) {
                selectApplication(direct.id)
                return@launch
            }

            // Otherwise resolve through the repository (DB -> cache -> provider),
            // which normalizes external ids to their cached DB id, then match again
            // so an already-tracked job is selected instead of duplicated.
            val result = jobRepository.getJobById(jobId)
            if (result is Result.Success) {
                val job = result.data
                val matched = current?.applications?.firstOrNull { app ->
                    val tracked = app.job
                    tracked != null && (
                        tracked.id == job.id ||
                            // External ids never survive the DB round-trip (jobs get
                            // auto-generated row ids), so fall back to stable identity
                            // to avoid tracking the same role twice.
                            (tracked.url.isNotBlank() && tracked.url == job.url) ||
                            (tracked.company.equals(job.company, ignoreCase = true) &&
                                tracked.title.equals(job.title, ignoreCase = true))
                        )
                }
                if (matched != null) {
                    selectApplication(matched.id)
                    return@launch
                }
                val success = _uiState.value as? TrackerUiState.Success
                if (success != null) {
                    _uiState.value = success.copy(pendingTrackJob = job)
                }
            } else {
                _effects.send(
                    (result as? Result.Failure)?.error?.message ?: "Job not found"
                )
            }
        }
    }

    private fun clearPendingTrackJob() {
        val current = _uiState.value as? TrackerUiState.Success ?: return
        _uiState.value = current.copy(pendingTrackJob = null)
    }

    private fun updateNotes(applicationId: Long, notes: String) {
        viewModelScope.launch {
            val result = repository.updateNotes(applicationId, notes)
            if (result is Result.Failure) {
                _effects.send(result.error.message ?: "Failed to save notes")
            }
        }
    }

    private fun addApplication(company: String, role: String, stageId: String) {
        if (company.isBlank() || role.isBlank()) {
            viewModelScope.launch { _effects.send("Enter a company and role to add an application.") }
            return
        }
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("tracker_manual_add"))
            val now = System.currentTimeMillis()
            val job = JobListing(
                id = "manual-${now}",
                title = role.trim(),
                company = company.trim(),
                description = "",
                url = "manual://${now}",
                sourceProvider = "manual"
            )
            val cacheResult = jobRepository.cacheJob(job)
            when (cacheResult) {
                is Result.Success -> {
                    val application = Application(
                        jobId = cacheResult.data,
                        currentStageId = stageId,
                        status = "ACTIVE",
                        dateApplied = now,
                        lastModified = now
                    )
                    val saveResult = repository.saveApplication(application)
                    if (saveResult is Result.Success) {
                        repository.addTimelineEvent(
                            com.bangersoul.aivance.core.common.model.TimelineEvent(
                                applicationId = saveResult.data,
                                eventType = "MANUAL_ADD",
                                title = "Added to pipeline",
                                description = "${role.trim()} at ${company.trim()} added manually",
                                timestamp = now
                            )
                        )
                        _effects.send("Added ${role.trim()} at ${company.trim()}")
                    } else {
                        _effects.send(
                            (saveResult as? Result.Failure)?.error?.message ?: "Failed to add application"
                        )
                    }
                }
                is Result.Failure -> {
                    _effects.send(cacheResult.error.message ?: "Failed to prepare job")
                }
            }
        }
    }
}
