package com.bangersoul.aivance.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.engine.NavigationWorkflowEngine
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.career.CareerGraphInsights
import com.bangersoul.aivance.core.domain.usecase.career.GetCareerGraphInsightsUseCase
import com.bangersoul.aivance.core.domain.repository.SkillGapEngagement
import com.bangersoul.aivance.core.domain.usecase.career.RecordSkillGapEngagementRequest
import com.bangersoul.aivance.core.domain.usecase.career.RecordSkillGapEngagementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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
    private val getCareerGraphInsights: GetCareerGraphInsightsUseCase,
    private val recordSkillGapEngagement: RecordSkillGapEngagementUseCase,
    private val trackEventUseCase: TrackEventUseCase
) : ViewModel() {

    private val _effects = Channel<DashboardUiEffect>(Channel.BUFFERED)
    val effects: Flow<DashboardUiEffect> = _effects.receiveAsFlow()

    /**
     * Bumped whenever a skill-gap engagement is recorded, so the insights read
     * model recomputes immediately rather than waiting for the next
     * CareerStateEngine state emission.
     */
    private val _insightsRefresh = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        stateEngine.state,
        _insightsRefresh
    ) { state, _ -> state }
        .mapLatest { state ->
            // The graph is written by CareerStateEngine's own projection as state
            // recomputes, so reading it right after a state emission reflects the
            // freshly persisted entity slice. This read is off the hot path: it never
            // writes the graph and cannot disturb either projection slice (M05).
            val insights = runCatching { getCareerGraphInsights() }
                .getOrDefault(CareerGraphInsights.EMPTY)
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
                // No agent missions are surfaced. These fields previously carried three
                // hardcoded missions and a fabricated "Scraping LinkedIn…" task that nothing
                // rendered and no producer owned (R3-6). A dashboard must not invent an agent
                // that is not running, so the fields stay empty until a real producer exists.
                graphInsights = insights.toUi()
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
            is DashboardUiEvent.ExploreSkillJobs -> onSkillGapAction(
                skill = event.skill,
                engagement = SkillGapEngagement.EXPLORED_JOBS,
                eventName = "dashboard_skill_gap_explore_jobs"
            )
            is DashboardUiEvent.LearnSkill -> onSkillGapAction(
                skill = event.skill,
                engagement = SkillGapEngagement.STARTED_LEARNING,
                eventName = "dashboard_skill_gap_learn"
            )
        }
    }

    /**
     * Records analytics + durable engagement when a user acts on a missing-skill
     * chip, then re-emits so the dashboard reflects the new momentum. Navigation
     * itself is owned by the nav graph (the screen fires its own callback); this
     * only persists the signal.
     */
    private fun onSkillGapAction(
        skill: String,
        engagement: SkillGapEngagement,
        eventName: String
    ) {
        viewModelScope.launch {
            trackEventUseCase(
                TrackEventRequest(eventName = eventName, properties = mapOf("skill" to skill))
            )
            recordSkillGapEngagement(RecordSkillGapEngagementRequest(skill, engagement))
            // Refresh the insights read model so the chip's status updates without
            // waiting for the next CareerStateEngine emission.
            _insightsRefresh.value = _insightsRefresh.value + 1
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

private fun CareerGraphInsights.toUi(): CareerGraphInsightsUi = CareerGraphInsightsUi(
    available = hasGraph,
    skillMatchPercent = skillMatchPercent,
    demonstratedSkillCount = demonstratedSkillCount,
    targetSkillCount = targetSkillCount,
    missingSkills = topMissingSkills.map {
        MissingSkillUi(
            skill = it.skill,
            demandedByJobs = it.demandedByJobs,
            engagement = when (it.engagement) {
                SkillGapEngagement.NONE -> SkillEngagementUi.NONE
                SkillGapEngagement.EXPLORED_JOBS -> SkillEngagementUi.EXPLORED_JOBS
                SkillGapEngagement.STARTED_LEARNING -> SkillEngagementUi.STARTED_LEARNING
            }
        )
    },
    gapsActedOn = gapsActedOn,
    applicationContexts = activeApplicationContexts.map {
        ApplicationContextUi(
            applicationId = it.applicationId,
            jobTitle = it.jobTitle ?: "Untitled role",
            company = it.company ?: "Unknown company",
            interviewCount = it.interviewCount
        )
    }
)
