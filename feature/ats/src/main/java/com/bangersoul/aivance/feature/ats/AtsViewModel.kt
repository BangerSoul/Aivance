package com.bangersoul.aivance.feature.ats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.AtsRepository
import com.bangersoul.aivance.core.domain.repository.AtsStreamEvent
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.ats.AnalyzeJobDescriptionUseCase
import com.bangersoul.aivance.core.domain.usecase.ats.AtsAnalysisRequest
import com.bangersoul.aivance.core.domain.usecase.ats.StreamAtsAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AtsUiState {
    data object SelectingResume : AtsUiState
    data class InputJobDescription(val resume: Resume, val selectedVersion: ResumeVersion) : AtsUiState

    /** Live analysis — [streamingText] grows token-by-token as the AI responds. */
    data class Analyzing(val streamingText: String = "") : AtsUiState
    data class DisplayReport(val report: AtsReport) : AtsUiState
    data class Error(val message: String) : AtsUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AtsViewModel @Inject constructor(
    private val resumeRepository: ResumeRepository,
    private val analyzeJobDescriptionUseCase: AnalyzeJobDescriptionUseCase,
    private val streamAtsAnalysisUseCase: StreamAtsAnalysisUseCase,
    private val atsRepository: AtsRepository,
    private val trackEventUseCase: TrackEventUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AtsUiState>(AtsUiState.SelectingResume)
    val uiState: StateFlow<AtsUiState> = _uiState.asStateFlow()

    private val _resumes = MutableStateFlow<List<Resume>>(emptyList())
    val resumes: StateFlow<List<Resume>> = _resumes.asStateFlow()

    private val _effects = Channel<AtsUiEffect>(Channel.BUFFERED)
    val effects: Flow<AtsUiEffect> = _effects.receiveAsFlow()

    /** Job description text as the user types — the live-scoring input. */
    private val _jdText = MutableStateFlow("")
    val jdText: StateFlow<String> = _jdText.asStateFlow()

    /** Currently selected resume version id — the other live-scoring input. */
    private val _selectedVersionId = MutableStateFlow<Long?>(null)

    private var currentResume: Resume? = null

    /**
     * Live-reactive ATS scoring. The score recalculates automatically whenever
     * either the selected resume version OR the job description changes:
     * the two inputs are combined, a JD under 50 chars is ignored, and the
     * pair is debounced 800ms after the last keystroke before re-running the
     * full parse → match pipeline. Tokens stream into [AtsUiState.Analyzing]
     * so the user sees the analysis as it happens.
     */
    private val analysisFlow: Flow<AtsStreamEvent> =
        combine(_selectedVersionId, _jdText) { versionId, jd ->
            if (versionId != null && jd.length > 50) Pair(versionId, jd) else null
        }
            .filterNotNull()
            .debounce(800)
            .flatMapLatest { (versionId, jd) ->
                flow {
                    _uiState.value = AtsUiState.Analyzing("")
                    trackEventUseCase(TrackEventRequest("ats_analyze_start"))

                    // Step 1: Parse the job description into a structured model.
                    val jdResult = analyzeJobDescriptionUseCase(jd)
                    val jdId = when (jdResult) {
                        is Result.Success -> jdResult.data
                        is Result.Failure -> {
                            _uiState.value =
                                AtsUiState.Error("Failed to parse Job Description: ${jdResult.error.message}")
                            return@flow
                        }
                    }

                    // Step 2: Stream the match analysis against the selected version.
                    val resumeId = currentResume?.id ?: return@flow
                    streamAtsAnalysisUseCase(AtsAnalysisRequest(resumeId, versionId, jdId))
                        .collect { emit(it) }
                }
            }

    init {
        loadResumes()
        viewModelScope.launch {
            analysisFlow.collect { event ->
                when (event) {
                    is AtsStreamEvent.Chunk -> {
                        val current = _uiState.value
                        if (current is AtsUiState.Analyzing) {
                            _uiState.value = current.copy(streamingText = current.streamingText + event.text)
                        }
                    }
                    is AtsStreamEvent.Completed -> {
                        _uiState.value = AtsUiState.DisplayReport(event.report)
                        trackEventUseCase(TrackEventRequest("ats_analyze_success"))
                    }
                    is AtsStreamEvent.Failed -> {
                        _uiState.value = AtsUiState.Error("Analysis failed: ${event.message}")
                    }
                }
            }
        }
    }

    /**
     * Opens a previously saved ATS report directly, skipping the picker and the
     * analysis pipeline (used when arriving with a report id, e.g. from the
     * Intelligence Hub's recent-scans list).
     */
    fun loadReport(reportId: Long) {
        viewModelScope.launch {
            val report = atsRepository.getReportById(reportId)
            if (report != null) {
                _uiState.value = AtsUiState.DisplayReport(report)
            } else {
                _uiState.value = AtsUiState.Error("ATS report not found")
            }
        }
    }

    fun onEvent(event: AtsUiEvent) {
        when (event) {
            AtsUiEvent.Start -> reset()
            is AtsUiEvent.SelectResumeVersion -> {
                currentResume = event.resume
                _selectedVersionId.value = event.version.id
                if (_jdText.value.isBlank()) {
                    _jdText.value = ""
                }
                _uiState.value = AtsUiState.InputJobDescription(event.resume, event.version)
            }
            is AtsUiEvent.UpdateJobDescription -> {
                _jdText.value = event.jobDescriptionText
            }
            is AtsUiEvent.Analyze -> {
                // Explicit trigger — routes through the same debounced live flow.
                _jdText.value = event.jobDescriptionText
            }
            AtsUiEvent.Reset -> reset()
            AtsUiEvent.GenerateCoverLetter -> generateCoverLetter()
            AtsUiEvent.ExportReport -> exportReport()
        }
    }

    private fun reset() {
        currentResume = null
        _selectedVersionId.value = null
        _jdText.value = ""
        _uiState.value = AtsUiState.SelectingResume
    }

    private fun loadResumes() {
        viewModelScope.launch {
            resumeRepository.getResumes().collect { result ->
                when (result) {
                    is Result.Success -> _resumes.value = result.data
                    is Result.Failure -> _uiState.value =
                        AtsUiState.Error("Failed to load resumes: ${result.error.message}")
                }
            }
        }
    }

    private fun generateCoverLetter() {
        val report = (_uiState.value as? AtsUiState.DisplayReport)?.report ?: return
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("ats_cover_letter_request"))
            _effects.send(AtsUiEffect.NavigateToCoverLetter(report.id))
        }
    }

    private fun exportReport() {
        val report = (_uiState.value as? AtsUiState.DisplayReport)?.report ?: return
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("ats_report_export"))
            _effects.send(AtsUiEffect.ExportReport(buildReportText(report)))
        }
    }

    private fun buildReportText(report: AtsReport): String = buildString {
        appendLine("=== ATS Match Report ===")
        appendLine("Overall Score: ${report.overallScore}")
        appendLine("Match Probability: ${report.matchPercentage}%")
        appendLine()
        if (report.matchedKeywords.isNotEmpty()) {
            appendLine("Matched Keywords:")
            report.matchedKeywords.forEach { appendLine("  + $it") }
            appendLine()
        }
        if (report.missingKeywords.isNotEmpty()) {
            appendLine("Missing Keywords:")
            report.missingKeywords.forEach { appendLine("  - $it") }
            appendLine()
        }
        if (report.optimizationTips.isNotEmpty()) {
            appendLine("Optimization Suggestions:")
            report.optimizationTips.forEach { tip ->
                appendLine("  [${tip.priority}] ${tip.category}: ${tip.description}")
            }
        }
        appendLine()
        appendLine("Generated: ${java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(report.dateGenerated))}")
    }
}
