package com.bangersoul.aivance.feature.jobs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.job.ScoreJobFitRequest
import com.bangersoul.aivance.core.domain.usecase.job.ScoreJobFitUseCase
import com.bangersoul.aivance.core.domain.usecase.job.SearchJobsRequest
import com.bangersoul.aivance.core.domain.usecase.job.SearchJobsUseCase
import com.bangersoul.aivance.core.domain.usecase.job.ToggleJobBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface JobsUiState {
    data object Loading : JobsUiState
    data class Success(
        val jobs: List<JobListing> = emptyList(),
        val filter: JobSearchFilter = JobSearchFilter(),
        val isSearching: Boolean = false,
        val careerContext: com.bangersoul.aivance.core.common.model.CareerState? = null,
        /**
         * Merged fit scores (job id → 0..100): LLM-assisted when the AI provider
         * is configured, deterministic rule-based otherwise. Empty while a search
         * is in flight or when no profile exists.
         */
        val fitScores: Map<String, Int> = emptyMap()
    ) : JobsUiState
    data class Error(val message: String) : JobsUiState
}

sealed interface JobsUiEvent {
    data class Search(val query: String) : JobsUiEvent
    /**
     * A one-shot search seed from a deep link (e.g. a dashboard skill-gap chip).
     * Unlike [Search] it is idempotent per seed value — re-arriving with the same
     * seed after rotation/process death won't clobber a query the user has since
     * edited.
     */
    data class SeedSearch(val query: String) : JobsUiEvent
    data class UpdateFilter(val filter: JobSearchFilter) : JobsUiEvent
    data object ClearFilters : JobsUiEvent
    data class ToggleBookmark(val jobId: String) : JobsUiEvent
    data class ViewDetails(val jobId: String) : JobsUiEvent
    data object Refresh : JobsUiEvent
}

sealed interface JobsUiEffect {
    data class ShowSnackbar(val message: String) : JobsUiEffect
    data class NavigateToDetails(val jobId: String) : JobsUiEffect
}

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val searchJobsUseCase: SearchJobsUseCase,
    private val toggleJobBookmarkUseCase: ToggleJobBookmarkUseCase,
    private val careerStateEngine: CareerStateEngine,
    private val scoreJobFitUseCase: ScoreJobFitUseCase,
    private val trackEventUseCase: TrackEventUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Typed search input survives rotation/process death: the query is written
     * to [savedStateHandle] on every search and restored here, so the user's
     * typed data is never silently wiped until app data is cleared.
     */
    private val restoredQuery: String = savedStateHandle.get<String>(KEY_SAVED_QUERY).orEmpty()

    private val _manualSearchState = MutableStateFlow<SearchState>(
        SearchState(filter = JobSearchFilter(query = restoredQuery))
    )

    val uiState: StateFlow<JobsUiState> = combine(
        careerStateEngine.state,
        _manualSearchState
    ) { careerState, manualSearch ->
        val currentJobs = manualSearch.jobs
        val profile = careerState.profile

        JobsUiState.Success(
            jobs = currentJobs,
            // Only an explicit user choice (Workplace dropdown) sets remoteType.
            // The profile's workPreference is a ranking signal (see JobFitScorer),
            // not a hard filter: promoting it here made the UI show "Remote" as
            // pre-selected on every search and silently zeroed out remote-friendly
            // boards (e.g. Arbeitnow, whose API returns most listings as ON_SITE)
            // — the job-search "zero-results" trap.
            filter = manualSearch.filter.copy(
                query = manualSearch.filter.query.ifBlank { profile.targetRole }
            ),
            isSearching = manualSearch.isSearching,
            careerContext = careerState,
            fitScores = manualSearch.fitScores
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JobsUiState.Loading
    )

    private val _effects = Channel<JobsUiEffect>(Channel.BUFFERED)
    val effects: Flow<JobsUiEffect> = _effects.receiveAsFlow()

    fun onEvent(event: JobsUiEvent) {
        when (event) {
            is JobsUiEvent.Search -> search(event.query)
            is JobsUiEvent.SeedSearch -> seedSearch(event.query)
            is JobsUiEvent.UpdateFilter -> updateFilter(event.filter)
            JobsUiEvent.ClearFilters -> clearFilters()
            is JobsUiEvent.ToggleBookmark -> toggleBookmark(event.jobId)
            is JobsUiEvent.ViewDetails -> viewModelScope.launch { _effects.send(JobsUiEffect.NavigateToDetails(event.jobId)) }
            JobsUiEvent.Refresh -> search()
        }
    }

    /**
     * Applies a deep-link search seed exactly once. Guarded by [savedStateHandle]
     * so a configuration change or process death that replays the same seed does
     * not overwrite a query the user has edited in the meantime.
     */
    private fun seedSearch(query: String) {
        if (query.isBlank()) return
        val alreadySeeded = savedStateHandle.get<String>(KEY_SEEDED_QUERY)
        if (alreadySeeded == query) return
        savedStateHandle[KEY_SEEDED_QUERY] = query
        search(query)
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun search(query: String? = null) {
        val current = _manualSearchState.value
        val newFilter = query?.let { current.filter.copy(query = it) } ?: current.filter
        savedStateHandle[KEY_SAVED_QUERY] = newFilter.query

        _manualSearchState.value = current.copy(filter = newFilter, isSearching = true, fitScores = emptyMap())

        searchJob?.cancel()
        fitScoreJob?.cancel()
        searchJob = viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("job_discovery_search"))

            val result = searchJobsUseCase(SearchJobsRequest(filter = newFilter))
            when (result) {
                is Result.Success -> {
                    val jobs = result.data
                    _manualSearchState.value = SearchState(jobs = jobs, filter = newFilter, isSearching = false)
                    scoreFit(jobs)
                }
                is Result.Failure -> {
                    val message = result.error.message ?: "Failed to load jobs"
                    _manualSearchState.value = current.copy(isSearching = false, fitScores = emptyMap())
                    _effects.send(JobsUiEffect.ShowSnackbar(message))
                }
            }
        }
    }

    private fun updateFilter(filter: JobSearchFilter) {
        _manualSearchState.value = _manualSearchState.value.copy(filter = filter, isSearching = true)
        search()
    }

    private fun clearFilters() {
        val cleared = JobSearchFilter(query = _manualSearchState.value.filter.query)
        _manualSearchState.value = _manualSearchState.value.copy(filter = cleared, isSearching = true)
        search(cleared.query)
    }

    private var fitScoreJob: kotlinx.coroutines.Job? = null

    /**
     * Computes the merged fit-score map for the latest search results (R-04):
     * LLM-assisted scores where the AI provider answered, rule-based
     * [JobFitScorer] everywhere else. Single-flight — a newer search cancels
     * the previous scoring run, and a stale run never overwrites newer results.
     */
    private fun scoreFit(jobs: List<JobListing>) {
        fitScoreJob?.cancel()
        fitScoreJob = viewModelScope.launch {
            val profile = careerStateEngine.state.value.profile ?: return@launch
            val aiScores = scoreJobFitUseCase(ScoreJobFitRequest(jobs = jobs, profile = profile))
            val current = _manualSearchState.value
            // A newer search replaced this result set — discard the stale run.
            if (current.jobs !== jobs) return@launch
            val merged = jobs.associate { job ->
                job.id to (aiScores[job.id] ?: JobFitScorer.calculateFitScore(job, profile))
            }
            _manualSearchState.value = current.copy(fitScores = merged)
        }
    }

    private fun toggleBookmark(jobId: String) {
        viewModelScope.launch {
            val result = toggleJobBookmarkUseCase(jobId)
            when (result) {
                is Result.Success -> {
                    val message = if (result.data) "Job bookmarked" else "Bookmark removed"
                    _effects.send(JobsUiEffect.ShowSnackbar(message))
                }
                is Result.Failure -> {
                    _effects.send(JobsUiEffect.ShowSnackbar(result.error.message ?: "Failed to update bookmark"))
                }
            }
        }
    }

    private data class SearchState(
        val jobs: List<JobListing> = emptyList(),
        val filter: JobSearchFilter = JobSearchFilter(),
        val isSearching: Boolean = false,
        val fitScores: Map<String, Int> = emptyMap()
    )

    private companion object {
        const val KEY_SAVED_QUERY = "jobs_search_query"
        const val KEY_SEEDED_QUERY = "jobs_seeded_query"
    }
}
