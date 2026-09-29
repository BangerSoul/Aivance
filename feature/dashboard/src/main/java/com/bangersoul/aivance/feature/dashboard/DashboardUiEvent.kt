package com.bangersoul.aivance.feature.dashboard

sealed interface DashboardUiEvent {
    data object Refresh : DashboardUiEvent
    data object NavigateToResume : DashboardUiEvent
    data object NavigateToCoverLetter : DashboardUiEvent
    data object NavigateToInterview : DashboardUiEvent
    data object NavigateToJobs : DashboardUiEvent
    data object NavigateToTracker : DashboardUiEvent
    data object NavigateToSettings : DashboardUiEvent
    data object Retry : DashboardUiEvent

    /** The user tapped a missing-skill chip to see jobs demanding that skill. */
    data class ExploreSkillJobs(val skill: String) : DashboardUiEvent

    /** The user tapped the learn action on a missing-skill chip. */
    data class LearnSkill(val skill: String) : DashboardUiEvent
}
