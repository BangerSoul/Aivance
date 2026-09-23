package com.bangersoul.aivance.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.engine.NavigationWorkflowEngine
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val stateEngine: CareerStateEngine,
    private val navWorkflowEngine: NavigationWorkflowEngine,
    private val trackEventUseCase: TrackEventUseCase
) : ViewModel() {

    private val _effects = Channel<DashboardUiEffect>(Channel.BUFFERED)
    val effects: Flow<DashboardUiEffect> = _effects.receiveAsFlow()

    val uiState: StateFlow<DashboardUiState> = stateEngine.state
        .map { state ->
            DashboardUiState(
                isLoading = false,
                greeting = "Hello, ${state.profile.name.substringBefore(' ')}",
                userDesignation = state.profile.targetRole,
                careerScore = state.growth.careerScore,
                atsScore = state.intelligence.atsScore,
                activeApplications = state.pipeline.activeApplications,
                nextInterview = state.pipeline.upcomingInterviews.firstOrNull()?.dateTime,
                savedJobs = state.discovery.savedJobsCount,
                aiRecommendation = state.recommendations.firstOrNull()?.let {
                    "AI Tip: ${it.title}"
                },
                nextBestAction = navWorkflowEngine.getRecommendedDestination(state),
                agentMissions = listOf(
                    AgentMission("1", "Global Job Scan", MissionStatus.RUNNING, 0.65f, "2m ago", "jobs"),
                    AgentMission("2", "Resume Optimization", MissionStatus.REVIEW_REQUIRED, 1.0f, "1h ago", "resume"),
                    AgentMission("3", "Interview Prep", MissionStatus.PENDING, 0f, "Yesterday", "interview")
                ),
                activeTask = AgentTask("t1", "Scraping LinkedIn for Senior Android roles...", MissionStatus.RUNNING, 0.42f)
            )
        }
        .onStart { emit(DashboardUiState(isLoading = true)) }
        .catch { e ->
            emit(DashboardUiState(isLoading = false, error = e.message ?: "Failed to load dashboard"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(isLoading = true)
        )

    fun onEvent(event: DashboardUiEvent) {
        when (event) {
            DashboardUiEvent.Refresh -> trackEvent("dashboard_refresh")
            DashboardUiEvent.Retry -> trackEvent("dashboard_retry")
            DashboardUiEvent.NavigateToResume -> sendEffect(DashboardUiEffect.NavigateTo("resume"))
            DashboardUiEvent.NavigateToCoverLetter -> sendEffect(DashboardUiEffect.NavigateTo("cover_letter"))
            DashboardUiEvent.NavigateToInterview -> sendEffect(DashboardUiEffect.NavigateTo("interview"))
            DashboardUiEvent.NavigateToJobs -> sendEffect(DashboardUiEffect.NavigateTo("jobs"))
            DashboardUiEvent.NavigateToTracker -> sendEffect(DashboardUiEffect.NavigateTo("tracker"))
            DashboardUiEvent.NavigateToSettings -> sendEffect(DashboardUiEffect.OpenSettings)
        }
    }

    private fun sendEffect(effect: DashboardUiEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    fun trackEvent(name: String) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest(eventName = name))
        }
    }
}
