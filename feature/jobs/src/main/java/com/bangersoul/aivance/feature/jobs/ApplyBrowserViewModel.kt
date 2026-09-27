package com.bangersoul.aivance.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.Recruiter
import com.bangersoul.aivance.core.common.model.TimelineEvent
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.AtsStreamEvent
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.repository.crm.CompanyIntelligenceRepository
import com.bangersoul.aivance.core.domain.repository.crm.RecruiterIntelligenceRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.ats.AnalyzeJobDescriptionUseCase
import com.bangersoul.aivance.core.domain.usecase.ats.AtsAnalysisRequest
import com.bangersoul.aivance.core.domain.usecase.ats.StreamAtsAnalysisUseCase
import com.bangersoul.aivance.core.domain.usecase.coverletter.GenerateCoverLetterRequest
import com.bangersoul.aivance.core.domain.usecase.coverletter.StreamGenerateCoverLetterUseCase
import com.bangersoul.aivance.core.domain.usecase.job.GetJobDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the in-app apply surface. [applyUrl] is the resolved, normalized
 * external apply page loaded into the WebView; the three panel sections each
 * carry their own async state so one can run while another is idle.
 */
data class ApplyBrowserUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val job: JobListing? = null,
    val applyUrl: String? = null,
    val ats: AtsSuggestionState = AtsSuggestionState.Idle,
    val coverLetter: CoverLetterSuggestionState = CoverLetterSuggestionState.Idle,
    val recruiters: RecruiterSuggestionState = RecruiterSuggestionState.Idle,
    /**
     * True once a submission on the apply page has been detected and the job
     * has been recorded in the Pipeline at the APPLIED stage. Set once and
     * never cleared for the life of the screen.
     */
    val applicationTracked: Boolean = false
)

sealed interface AtsSuggestionState {
    data object Idle : AtsSuggestionState
    data class Running(val streamingText: String = "") : AtsSuggestionState
    data class Ready(
        val matchPercentage: Int,
        val matchedKeywords: List<String>,
        val missingKeywords: List<String>
    ) : AtsSuggestionState
    data class Failed(val message: String) : AtsSuggestionState
}

sealed interface CoverLetterSuggestionState {
    data object Idle : CoverLetterSuggestionState
    data class Running(val streamingText: String = "") : CoverLetterSuggestionState
    data class Ready(val text: String) : CoverLetterSuggestionState
    data class Failed(val message: String) : CoverLetterSuggestionState
}

sealed interface RecruiterSuggestionState {
    data object Idle : RecruiterSuggestionState
    data object Running : RecruiterSuggestionState
    data class Ready(val recruiters: List<Recruiter>) : RecruiterSuggestionState
    data class Failed(val message: String) : RecruiterSuggestionState
}

sealed interface ApplyBrowserUiEffect {
    data class ShowSnackbar(val message: String) : ApplyBrowserUiEffect
    /**
     * A snackbar with a single reversible action. [actionLabel] is the button
     * copy; when the user taps it the screen calls [ApplyBrowserViewModel.undoTracking].
     */
    data class ShowUndoableSnackbar(val message: String, val actionLabel: String) : ApplyBrowserUiEffect
    data class CopyText(val text: String) : ApplyBrowserUiEffect
    data class OpenExternalUrl(val url: String) : ApplyBrowserUiEffect
}

@HiltViewModel
class ApplyBrowserViewModel @Inject constructor(
    private val getJobDetailsUseCase: GetJobDetailsUseCase,
    private val jobRepository: JobRepository,
    private val applicationWorkflowRepository: ApplicationWorkflowRepository,
    private val resumeRepository: ResumeRepository,
    private val companyIntelligenceRepository: CompanyIntelligenceRepository,
    private val recruiterIntelligenceRepository: RecruiterIntelligenceRepository,
    private val analyzeJobDescriptionUseCase: AnalyzeJobDescriptionUseCase,
    private val streamAtsAnalysisUseCase: StreamAtsAnalysisUseCase,
    private val streamGenerateCoverLetterUseCase: StreamGenerateCoverLetterUseCase,
    private val trackEventUseCase: TrackEventUseCase
) : ViewModel() {

    private var jobId: String = ""

    private val _uiState = MutableStateFlow(ApplyBrowserUiState())
    val uiState: StateFlow<ApplyBrowserUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ApplyBrowserUiEffect>(Channel.BUFFERED)
    val effects: Flow<ApplyBrowserUiEffect> = _effects.receiveAsFlow()

    fun load(jobId: String) {
        this.jobId = jobId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            trackEventUseCase(TrackEventRequest("apply_browser_open"))
            when (val result = getJobDetailsUseCase(jobId)) {
                is Result.Success -> {
                    val job = result.data
                    val resolved = JobDetailsViewModel.resolveApplyUrl(
                        job.url, job.sourceUrl, job.descriptionHtml
                    )
                    _uiState.update {
                        it.copy(isLoading = false, job = job, applyUrl = resolved)
                    }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.error.message)
                    }
                }
            }
        }
    }

    fun openExternal() {
        val url = _uiState.value.applyUrl ?: return
        viewModelScope.launch { _effects.send(ApplyBrowserUiEffect.OpenExternalUrl(url)) }
    }

    // ── Submission detection → Pipeline entry ────────────────────────────

    /**
     * Guards against the many redundant signals a single application submit
     * produces (form `submit` event + confirmation-page navigation + reloads),
     * so the Pipeline entry is created exactly once per screen visit.
     */
    private var submissionHandled = false

    /**
     * Everything the last successful tracking created, captured so [undoTracking]
     * can reverse *exactly* that and nothing a user did before. For a brand-new
     * Application we delete the row (its timeline cascades); for one that already
     * existed we restore the prior stage/dateApplied/lastModified and delete only
     * the timeline event this action appended.
     */
    private data class TrackingUndo(
        val applicationId: Long,
        val timelineEventId: Long?,
        val wasNewApplication: Boolean,
        val previous: Application?
    )

    private var lastTrackingUndo: TrackingUndo? = null

    /**
     * Called when the WebView detects the user submitted the application —
     * either a form `submit` event fired inside the page or a navigation to a
     * confirmation/thank-you URL. Caches the job (so the FK is valid), then
     * moves an existing Application for this job to APPLIED or creates one,
     * and appends a timeline event. Idempotent within a screen visit.
     *
     * Trust note: the form-submit signal originates from an untrusted page via
     * a JS bridge, so the worst a hostile page can do is create one Pipeline
     * row for the job the user chose to open — no external side effects.
     */
    fun onApplicationSubmitted() {
        if (submissionHandled) return
        val job = _uiState.value.job ?: return
        submissionHandled = true

        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("apply_browser_submitted"))

            val dbJobId = (jobRepository.cacheJob(job) as? Result.Success)?.data ?: run {
                submissionHandled = false
                return@launch
            }

            // Reuse an existing tracked application for this job so a manual
            // "Apply & Track" followed by an in-app submit doesn't duplicate.
            val existing = (applicationWorkflowRepository.getApplications().first() as? Result.Success)
                ?.data
                ?.firstOrNull { it.jobId == dbJobId }

            val now = System.currentTimeMillis()
            val application = (existing ?: Application(jobId = dbJobId)).copy(
                currentStageId = "APPLIED",
                status = "ACTIVE",
                dateApplied = existing?.dateApplied ?: now,
                lastModified = now
            )

            when (val saveResult = applicationWorkflowRepository.saveApplication(application)) {
                is Result.Success -> {
                    val applicationId = saveResult.data
                    val timelineEventId = (
                        applicationWorkflowRepository.addTimelineEvent(
                            TimelineEvent(
                                applicationId = applicationId,
                                eventType = "APPLIED",
                                title = "Applied in-app",
                                description = job.title
                            )
                        ) as? Result.Success
                    )?.data
                    lastTrackingUndo = TrackingUndo(
                        applicationId = applicationId,
                        timelineEventId = timelineEventId,
                        wasNewApplication = existing == null,
                        previous = existing
                    )
                    _uiState.update { it.copy(applicationTracked = true) }
                    _effects.send(
                        ApplyBrowserUiEffect.ShowUndoableSnackbar(
                            message = "Application tracked in your Pipeline.",
                            actionLabel = "Undo"
                        )
                    )
                }
                is Result.Failure -> {
                    // Allow a later signal to retry the tracking.
                    submissionHandled = false
                }
            }
        }
    }

    /**
     * Reverses the most recent auto-tracking. If tracking created a fresh
     * Application the whole row is deleted (its timeline events cascade); if it
     * moved an application that already existed, the prior stage/dates are
     * restored and only the timeline event this action added is removed, so a
     * user's earlier history is never touched. Clears [applicationTracked] and
     * re-arms submission detection so a later submit can track again.
     */
    fun undoTracking() {
        val undo = lastTrackingUndo ?: return
        lastTrackingUndo = null

        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("apply_browser_undo_track"))

            val reverted = if (undo.wasNewApplication || undo.previous == null) {
                applicationWorkflowRepository.deleteApplication(undo.applicationId) is Result.Success
            } else {
                // Remove only the timeline event we added, then restore the
                // application's pre-tracking state.
                undo.timelineEventId?.let {
                    applicationWorkflowRepository.deleteTimelineEvent(it)
                }
                applicationWorkflowRepository.saveApplication(undo.previous) is Result.Success
            }

            if (reverted) {
                submissionHandled = false
                _uiState.update { it.copy(applicationTracked = false) }
                _effects.send(ApplyBrowserUiEffect.ShowSnackbar("Removed from your Pipeline."))
            } else {
                // Restore undo affordance so the user can retry the reversal.
                lastTrackingUndo = undo
                _effects.send(ApplyBrowserUiEffect.ShowSnackbar("Couldn't undo — try again."))
            }
        }
    }

    // ── ATS scoring ──────────────────────────────────────────────────────

    fun runAtsScore() {
        val job = _uiState.value.job ?: return
        val current = _uiState.value.ats
        if (current is AtsSuggestionState.Running) return

        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("apply_browser_ats"))
            val (resumeId, versionId) = primaryResumeAndVersion() ?: run {
                _uiState.update { it.copy(ats = AtsSuggestionState.Failed("no_resume")) }
                _effects.send(ApplyBrowserUiEffect.ShowSnackbar("Import a resume first to use AI assistance."))
                return@launch
            }

            _uiState.update { it.copy(ats = AtsSuggestionState.Running("")) }

            // Parse the job description into a structured JD first.
            val jdResult = analyzeJobDescriptionUseCase(job.description)
            val jdId = when (jdResult) {
                is Result.Success -> jdResult.data
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(ats = AtsSuggestionState.Failed(jdResult.error.message))
                    }
                    return@launch
                }
            }

            streamAtsAnalysisUseCase(AtsAnalysisRequest(resumeId, versionId, jdId)).collect { event ->
                when (event) {
                    is AtsStreamEvent.Chunk -> _uiState.update { state ->
                        val running = state.ats as? AtsSuggestionState.Running
                            ?: AtsSuggestionState.Running("")
                        state.copy(ats = running.copy(streamingText = running.streamingText + event.text))
                    }
                    is AtsStreamEvent.Completed -> _uiState.update { state ->
                        state.copy(
                            ats = AtsSuggestionState.Ready(
                                matchPercentage = event.report.matchPercentage,
                                matchedKeywords = event.report.matchedKeywords,
                                missingKeywords = event.report.missingKeywords
                            )
                        )
                    }
                    is AtsStreamEvent.Failed -> _uiState.update {
                        it.copy(ats = AtsSuggestionState.Failed(event.message))
                    }
                }
            }
        }
    }

    // ── Cover letter ─────────────────────────────────────────────────────

    fun generateCoverLetter() {
        val job = _uiState.value.job ?: return
        val current = _uiState.value.coverLetter
        if (current is CoverLetterSuggestionState.Running) return

        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("apply_browser_cover_letter"))
            val (resumeId, versionId) = primaryResumeAndVersion() ?: run {
                _uiState.update { it.copy(coverLetter = CoverLetterSuggestionState.Failed("no_resume")) }
                _effects.send(ApplyBrowserUiEffect.ShowSnackbar("Import a resume first to use AI assistance."))
                return@launch
            }

            // Cache the job so the cover-letter engine has a real DB id to target.
            val dbJobId = (jobRepository.cacheJob(job) as? Result.Success)?.data

            _uiState.update { it.copy(coverLetter = CoverLetterSuggestionState.Running("")) }
            try {
                var full = ""
                streamGenerateCoverLetterUseCase.stream(
                    GenerateCoverLetterRequest(
                        resumeId = resumeId,
                        resumeVersionId = versionId,
                        jobId = dbJobId,
                        recruiterId = null
                    )
                ).collect { chunk ->
                    full += chunk
                    _uiState.update { it.copy(coverLetter = CoverLetterSuggestionState.Running(full)) }
                }
                _uiState.update {
                    it.copy(
                        coverLetter = if (full.isBlank()) {
                            CoverLetterSuggestionState.Failed("Generation produced no text")
                        } else {
                            CoverLetterSuggestionState.Ready(full)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        coverLetter = CoverLetterSuggestionState.Failed(
                            e.message?.takeIf { m -> m.isNotBlank() } ?: "Generation failed"
                        )
                    )
                }
            }
        }
    }

    fun copyCoverLetter() {
        val text = (_uiState.value.coverLetter as? CoverLetterSuggestionState.Ready)?.text ?: return
        viewModelScope.launch { _effects.send(ApplyBrowserUiEffect.CopyText(text)) }
    }

    // ── Recruiter emails ─────────────────────────────────────────────────

    fun findRecruiters() {
        val job = _uiState.value.job ?: return
        val current = _uiState.value.recruiters
        if (current is RecruiterSuggestionState.Running) return

        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("apply_browser_recruiters"))
            val company = companyIntelligenceRepository.getCompanyByName(job.company)
            val domain = company?.domain
            if (domain.isNullOrBlank()) {
                _uiState.update { it.copy(recruiters = RecruiterSuggestionState.Failed("no_domain")) }
                _effects.send(ApplyBrowserUiEffect.ShowSnackbar("No company domain available to search recruiters."))
                return@launch
            }

            _uiState.update { it.copy(recruiters = RecruiterSuggestionState.Running) }
            when (val result = recruiterIntelligenceRepository.findRecruiters(domain)) {
                is Result.Success -> _uiState.update {
                    it.copy(recruiters = RecruiterSuggestionState.Ready(result.data))
                }
                is Result.Failure -> _uiState.update {
                    it.copy(recruiters = RecruiterSuggestionState.Failed(result.error.message))
                }
            }
        }
    }

    fun copyRecruiterEmail(email: String) {
        viewModelScope.launch { _effects.send(ApplyBrowserUiEffect.CopyText(email)) }
    }

    /**
     * Resolves the user's primary resume + version, mirroring
     * CoverLetterViewModel.generateFromPrimaryInternal. Returns null when no
     * resume exists yet, so callers can surface a "import a resume" prompt
     * instead of hanging.
     */
    private suspend fun primaryResumeAndVersion(): Pair<Long, Long>? {
        val resumesResult = resumeRepository.getResumes().first()
        val resume = (resumesResult as? Result.Success)?.data?.firstOrNull() ?: return null
        val version = resume.versions.firstOrNull { it.id == resume.primaryVersionId }
            ?: resume.versions.firstOrNull()
            ?: return null
        return resume.id to version.id
    }
}
