package com.bangersoul.aivance.feature.jobs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.model.Company
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.Recruiter
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.repository.crm.CompanyIntelligenceRepository
import com.bangersoul.aivance.core.domain.repository.crm.RecruiterIntelligenceRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.job.GetJobDetailsUseCase
import com.bangersoul.aivance.core.domain.usecase.job.ToggleJobBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface JobDetailsUiState {
    data object Loading : JobDetailsUiState
    data class Success(
        val job: JobListing,
        val company: Company? = null,
        val recruiters: List<Recruiter> = emptyList(),
        val isBookmarked: Boolean = false,
        /**
         * Profile-aware match readiness (0..100), or null when there is no
         * usable profile to score against. Null renders an explicit
         * "complete your profile" state instead of a fabricated number.
         */
        val readinessScore: Int? = null
    ) : JobDetailsUiState
    data class Error(val message: String) : JobDetailsUiState
}

sealed interface JobDetailsUiEvent {
    data object ToggleBookmark : JobDetailsUiEvent
    data object OpenUrl : JobDetailsUiEvent
    data object ApplyInApp : JobDetailsUiEvent
    data object ApplyAndTrack : JobDetailsUiEvent
    data object FindRecruiters : JobDetailsUiEvent
    data object GenerateCoverLetter : JobDetailsUiEvent
    data object OpenAts : JobDetailsUiEvent
    data object Reload : JobDetailsUiEvent
}

sealed interface JobDetailsUiEffect {
    data class ShowSnackbar(val message: String) : JobDetailsUiEffect
    data class OpenExternalUrl(val url: String) : JobDetailsUiEffect
    data class NavigateToRecruiters(val jobId: String) : JobDetailsUiEffect
    data class NavigateToApplyBrowser(val jobId: String) : JobDetailsUiEffect
    data class NavigateToCoverLetter(val jobId: Long) : JobDetailsUiEffect
    data class NavigateToAts(val jobDescription: String) : JobDetailsUiEffect
    data object NavigateToPipeline : JobDetailsUiEffect
}

@HiltViewModel
class JobDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getJobDetailsUseCase: GetJobDetailsUseCase,
    private val toggleJobBookmarkUseCase: ToggleJobBookmarkUseCase,
    private val jobRepository: JobRepository,
    private val applicationWorkflowRepository: ApplicationWorkflowRepository,
    private val companyIntelligenceRepository: CompanyIntelligenceRepository,
    private val recruiterIntelligenceRepository: RecruiterIntelligenceRepository,
    private val careerStateEngine: CareerStateEngine,
    private val trackEventUseCase: TrackEventUseCase
) : ViewModel() {

    private var jobId: String = savedStateHandle.get<String>("jobId") ?: ""

    private val _uiState = MutableStateFlow<JobDetailsUiState>(JobDetailsUiState.Loading)
    val uiState: StateFlow<JobDetailsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<JobDetailsUiEffect>(Channel.BUFFERED)
    val effects: Flow<JobDetailsUiEffect> = _effects.receiveAsFlow()

    init {
        // The custom navigation back stack does not seed SavedStateHandle with
        // destination arguments, so the screen supplies the real job ID via load().
        if (jobId.isNotBlank()) {
            loadJobDetails()
        }
    }

    /**
     * Loads details for [jobId]. Called by the screen on first composition and
     * whenever the destination's job ID changes, so a reused ViewModel instance
     * correctly reloads for a different job.
     */
    fun load(jobId: String) {
        this.jobId = jobId
        loadJobDetails()
    }

    fun onEvent(event: JobDetailsUiEvent) {
        when (event) {
            JobDetailsUiEvent.ToggleBookmark -> toggleBookmark()
            JobDetailsUiEvent.OpenUrl -> openUrl()
            JobDetailsUiEvent.ApplyInApp -> applyInApp()
            JobDetailsUiEvent.ApplyAndTrack -> applyAndTrack()
            JobDetailsUiEvent.FindRecruiters -> findRecruiters()
            JobDetailsUiEvent.GenerateCoverLetter -> generateCoverLetter()
            JobDetailsUiEvent.OpenAts -> openAts()
            JobDetailsUiEvent.Reload -> loadJobDetails()
        }
    }

    private fun loadJobDetails() {
        viewModelScope.launch {
            _uiState.value = JobDetailsUiState.Loading
            val result = getJobDetailsUseCase(jobId)
            when (result) {
                is Result.Success -> {
                    val job = result.data
                    val company = companyIntelligenceRepository.getCompanyByName(job.company)
                    val recruiters = company?.domain?.let {
                        recruiterIntelligenceRepository.findRecruiters(it).getOrNull()
                    } ?: emptyList()

                    _uiState.value = JobDetailsUiState.Success(
                        job = job,
                        company = company,
                        recruiters = recruiters,
                        readinessScore = calculateReadiness(job)
                    )
                }
                is Result.Failure -> {
                    _uiState.value = JobDetailsUiState.Error(result.error.message)
                }
            }
        }
    }

    /**
     * Readiness is the same profile-aware "match" concept the Jobs screen shows
     * via [JobFitScorer]. Without a usable profile (no target role and no
     * skills) there is nothing to score against, so we return null and let the
     * UI show a "complete your profile" state rather than inventing a number.
     */
    private fun calculateReadiness(job: JobListing): Int? {
        val profile = careerStateEngine.state.value.profile
        val hasProfile = profile.targetRole.isNotBlank() || profile.skills.isNotEmpty()
        if (!hasProfile) return null
        return JobFitScorer.calculateFitScore(job, profile).coerceIn(0, 100)
    }

    private fun toggleBookmark() {
        viewModelScope.launch {
            val state = _uiState.value as? JobDetailsUiState.Success ?: return@launch
            val result = toggleJobBookmarkUseCase(jobId)
            when (result) {
                is Result.Success -> {
                    _uiState.value = state.copy(isBookmarked = result.data)
                    _effects.send(JobDetailsUiEffect.ShowSnackbar(if (result.data) "Bookmarked" else "Bookmark removed"))
                }
                is Result.Failure -> {
                    _effects.send(JobDetailsUiEffect.ShowSnackbar(result.error.message ?: "Failed to update bookmark"))
                }
            }
        }
    }

    /**
     * Opens the in-app apply surface (hosted WebView + AI suggestions) instead
     * of bouncing straight to an external browser. Only navigates when a real
     * apply link resolves, so the WebView never opens on a dead page.
     */
    private fun applyInApp() {
        val state = _uiState.value as? JobDetailsUiState.Success ?: return
        val job = state.job
        val resolved = resolveApplyUrl(job.url, job.sourceUrl, job.descriptionHtml)
        if (resolved == null) {
            _effects.trySend(JobDetailsUiEffect.ShowSnackbar("No apply link available for this job"))
            return
        }
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("job_details_apply_in_app"))
            _effects.send(JobDetailsUiEffect.NavigateToApplyBrowser(job.id))
        }
    }

    private fun openUrl() {
        val state = _uiState.value as? JobDetailsUiState.Success ?: return
        val job = state.job
        val resolved = resolveApplyUrl(job.url, job.sourceUrl, job.descriptionHtml)
        if (resolved == null) {
            _effects.trySend(JobDetailsUiEffect.ShowSnackbar("No apply link available for this job"))
            return
        }
        viewModelScope.launch {
            _effects.send(JobDetailsUiEffect.OpenExternalUrl(resolved))
            trackEventUseCase(TrackEventRequest("job_details_open_url"))
        }
    }

    companion object {
        /**
         * Resolves the best, normalized apply URL for a job.
         *
         * Priority: explicit [url] → [sourceUrl] → an href extracted from the
         * description HTML. Blank or placeholder values are skipped, and the
         * result is normalized to an absolute http(s) URL so the browser opens
         * the real apply page instead of failing silently.
         */
        fun resolveApplyUrl(
            url: String?,
            sourceUrl: String?,
            descriptionHtml: String?
        ): String? {
            val candidate = listOf(url, sourceUrl)
                .mapNotNull { it }
                .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) && it != "#" }
                ?: extractHref(descriptionHtml)
            return candidate?.let { normalizeUrl(it) }
        }

        private fun extractHref(descriptionHtml: String?): String? {
            if (descriptionHtml.isNullOrBlank()) return null
            val hrefRegex = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val match = hrefRegex.find(descriptionHtml) ?: return null
            return match.groupValues[1].takeIf { it.isNotBlank() && it != "#" }
        }

        fun normalizeUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null

            // Any explicit scheme (https, http, mailto:, tel:, custom app deep
            // links) is already absolute — leave it untouched. Without this
            // guard a mailto:/tel: link would be mangled into https://mailto:….
            val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.+-]*:").containsMatchIn(trimmed)
            if (hasScheme) return trimmed

            val clean = trimmed
                .trimStart('/')
                .trim()
            if (clean.isBlank()) return null
            return "https://$clean"
        }
    }

    /**
     * Apply & Track: persists the job (so the FK is valid) and creates an
     * [Application] in the SAVED stage, then routes the user to the Pipeline.
     */
    private fun applyAndTrack() {
        val state = _uiState.value as? JobDetailsUiState.Success ?: return
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("job_apply_and_track"))
            val cacheResult = jobRepository.cacheJob(state.job)
            val dbJobId = when (cacheResult) {
                is Result.Success -> cacheResult.data
                is Result.Failure -> {
                    _effects.send(JobDetailsUiEffect.ShowSnackbar(
                        cacheResult.error.message ?: "Failed to save this job"
                    ))
                    return@launch
                }
            }

            val now = System.currentTimeMillis()
            val application = Application(
                jobId = dbJobId,
                currentStageId = "SAVED",
                status = "ACTIVE",
                dateApplied = now,
                lastModified = now
            )
            val saveResult = applicationWorkflowRepository.saveApplication(application)
            if (saveResult is Result.Success) {
                _effects.send(JobDetailsUiEffect.NavigateToPipeline)
                _effects.send(JobDetailsUiEffect.ShowSnackbar("Added to Pipeline"))
            } else {
                _effects.send(JobDetailsUiEffect.ShowSnackbar(
                    (saveResult as? Result.Failure)?.error?.message ?: "Failed to add application"
                ))
            }
        }
    }

    private fun findRecruiters() {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("job_find_recruiters"))
            _effects.send(JobDetailsUiEffect.NavigateToRecruiters(jobId))
        }
    }

    /**
     * Routes to the ATS intelligence engine pre-filled with this job's
     * description so the user can scan their resume against it directly.
     */
    fun openAts() {
        val state = _uiState.value as? JobDetailsUiState.Success ?: return
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("job_open_ats"))
            _effects.send(JobDetailsUiEffect.NavigateToAts(state.job.description))
        }
    }

    private fun generateCoverLetter() {
        val state = _uiState.value as? JobDetailsUiState.Success ?: return
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("job_generate_cover_letter"))
            // Cache the job so the Cover Letter engine has a real DB id to target.
            val cacheResult = jobRepository.cacheJob(state.job)
            when (cacheResult) {
                is Result.Success -> _effects.send(JobDetailsUiEffect.NavigateToCoverLetter(cacheResult.data))
                is Result.Failure -> _effects.send(JobDetailsUiEffect.ShowSnackbar(
                    cacheResult.error.message ?: "Failed to prepare cover letter"
                ))
            }
        }
    }
}
