package com.bangersoul.aivance.feature.coverletter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.CoverLetter
import com.bangersoul.aivance.core.common.model.CoverLetterVersion
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.CoverLetterRepository
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.util.PdfExporter
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.coverletter.GenerateCoverLetterRequest
import com.bangersoul.aivance.core.domain.usecase.coverletter.GenerateCoverLetterUseCase
import com.bangersoul.aivance.core.domain.usecase.coverletter.RegenerateCoverLetterSectionUseCase
import com.bangersoul.aivance.core.domain.usecase.coverletter.RegenerateSectionRequest
import com.bangersoul.aivance.core.domain.usecase.coverletter.StreamGenerateCoverLetterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CoverLetterUiState {
    data object Idle : CoverLetterUiState
    data object Loading : CoverLetterUiState
    data class Success(
        val coverLetter: CoverLetter? = null,
        val selectedVersion: CoverLetterVersion? = null,
        val isGenerating: Boolean = false,
        /** Live token stream while a letter is being generated — rendered with
         *  a typewriter effect. Cleared once the full letter is persisted. */
        val streamingContent: String? = null,
        val isEditing: Boolean = false,
        /** Section index → draft text, populated when the user enters edit mode.
         *  Keyed by index (not section id) because freshly generated sections can
         *  share the default id 0, which would collapse drafts into one entry. */
        val sectionDrafts: Map<Int, String> = emptyMap()
    ) : CoverLetterUiState
    data class Error(val message: String) : CoverLetterUiState
}

sealed interface CoverLetterUiEvent {
    data class Generate(
        val resumeId: Long,
        val versionId: Long,
        /** Null when generating a generic letter from the primary resume only. */
        val jobId: Long?,
        val recruiterId: String?
    ) : CoverLetterUiEvent

    /**
     * Generates a letter from the primary resume without a job listing — the
     * "Generate Cover Letter" action on the empty state. Never dead-ends.
     */
    data object GenerateFromPrimary : CoverLetterUiEvent

    data class RegenerateSection(val versionId: Long, val sectionType: String) : CoverLetterUiEvent

    /**
     * Auto-generates a tailored letter for [jobId] using the user's primary
     * resume — fired when the screen is opened from Job Details.
     */
    data class GenerateForJob(val jobId: Long) : CoverLetterUiEvent
    data object Load : CoverLetterUiEvent

    /** Toggles between read-only and inline-edit mode. */
    data object ToggleEdit : CoverLetterUiEvent
    data class UpdateSection(val sectionIndex: Int, val content: String) : CoverLetterUiEvent
    data object SaveEdits : CoverLetterUiEvent
    data object CopyAll : CoverLetterUiEvent
    data object Export : CoverLetterUiEvent
}

sealed interface CoverLetterUiEffect {
    data class ShowSnackbar(val message: String) : CoverLetterUiEffect
    data class CopyText(val text: String) : CoverLetterUiEffect
    data class ExportPdf(val uri: Uri) : CoverLetterUiEffect
}

@HiltViewModel
class CoverLetterViewModel @Inject constructor(
    private val coverLetterRepository: CoverLetterRepository,
    private val resumeRepository: ResumeRepository,
    private val generateCoverLetterUseCase: GenerateCoverLetterUseCase,
    private val streamGenerateCoverLetterUseCase: StreamGenerateCoverLetterUseCase,
    private val regenerateCoverLetterSectionUseCase: RegenerateCoverLetterSectionUseCase,
    private val trackEventUseCase: TrackEventUseCase,
    private val pdfExporter: PdfExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow<CoverLetterUiState>(CoverLetterUiState.Idle)
    val uiState: StateFlow<CoverLetterUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CoverLetterUiEffect>(Channel.BUFFERED)
    val effects: Flow<CoverLetterUiEffect> = _effects.receiveAsFlow()

    fun onEvent(event: CoverLetterUiEvent) {
        when (event) {
            is CoverLetterUiEvent.Generate -> generate(event)
            is CoverLetterUiEvent.GenerateFromPrimary -> generateFromPrimary()
            is CoverLetterUiEvent.GenerateForJob -> generateForJob(event.jobId)
            is CoverLetterUiEvent.RegenerateSection -> regenerateSection(event)
            CoverLetterUiEvent.Load -> load()
            CoverLetterUiEvent.ToggleEdit -> toggleEdit()
            is CoverLetterUiEvent.UpdateSection -> updateSection(event.sectionIndex, event.content)
            CoverLetterUiEvent.SaveEdits -> saveEdits()
            CoverLetterUiEvent.CopyAll -> copyAll()
            CoverLetterUiEvent.Export -> export()
        }
    }

    /**
     * Resolves the user's primary resume + version and generates a tailored
     * letter for [jobId]. Surfaces a snackbar (instead of hanging on Loading)
     * when no resume exists yet.
     */
    private fun generateForJob(jobId: Long) {
        viewModelScope.launch {
            _uiState.value = CoverLetterUiState.Loading
            trackEventUseCase(TrackEventRequest("cover_letter_generate_for_job"))
            generateFromPrimaryInternal(jobId = jobId)
        }
    }

    /** Generates a generic letter (no job attached) from the primary resume. */
    private fun generateFromPrimary() {
        viewModelScope.launch {
            _uiState.value = CoverLetterUiState.Loading
            trackEventUseCase(TrackEventRequest("cover_letter_generate_primary"))
            generateFromPrimaryInternal(jobId = null)
        }
    }

    private suspend fun generateFromPrimaryInternal(jobId: Long?) {
        val resumesResult = resumeRepository.getResumes().first()
        val resume = (resumesResult as? Result.Success)?.data?.firstOrNull()
        val version = resume?.versions
            ?.firstOrNull { it.id == resume.primaryVersionId }
            ?: resume?.versions?.firstOrNull()

        if (resume == null || version == null) {
            _uiState.value = CoverLetterUiState.Idle
            _effects.send(
                CoverLetterUiEffect.ShowSnackbar("Import a resume first to generate a cover letter")
            )
            return
        }

        generate(
            CoverLetterUiEvent.Generate(
                resumeId = resume.id,
                versionId = version.id,
                jobId = jobId,
                recruiterId = null
            )
        )
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = CoverLetterUiState.Loading
            coverLetterRepository.getCoverLetters().collect { result ->
                when (result) {
                    is Result.Success -> {
                        val list = result.data
                        if (list.isNotEmpty()) {
                            val first = list.first()
                            _uiState.value = CoverLetterUiState.Success(coverLetter = first)
                        } else {
                            _uiState.value = CoverLetterUiState.Idle
                        }
                    }
                    is Result.Failure -> {
                        _uiState.value = CoverLetterUiState.Error(
                            "Failed to load cover letters: ${result.error.message}"
                        )
                    }
                }
            }
        }
    }

    private fun generate(event: CoverLetterUiEvent.Generate) {
        viewModelScope.launch {
            _uiState.value = CoverLetterUiState.Loading
            trackEventUseCase(TrackEventRequest("cover_letter_generate_start"))

            // Live-reactive state while tokens stream in.
            _uiState.value = CoverLetterUiState.Success(
                isGenerating = true,
                streamingContent = ""
            )

            try {
                val request = GenerateCoverLetterRequest(
                    resumeId = event.resumeId,
                    resumeVersionId = event.versionId,
                    jobId = event.jobId,
                    recruiterId = event.recruiterId
                )
                var full = ""
                streamGenerateCoverLetterUseCase.stream(request).collect { chunk ->
                    full += chunk
                    _uiState.value = CoverLetterUiState.Success(
                        isGenerating = true,
                        streamingContent = full
                    )
                }
                load()
            } catch (e: Exception) {
                _uiState.value = CoverLetterUiState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: "Generation failed"
                )
            }
        }
    }

    private fun regenerateSection(event: CoverLetterUiEvent.RegenerateSection) {
        viewModelScope.launch {
            val current = _uiState.value as? CoverLetterUiState.Success ?: return@launch
            _uiState.value = current.copy(isGenerating = true)

            val result = regenerateCoverLetterSectionUseCase(
                RegenerateSectionRequest(event.versionId, event.sectionType)
            )

            if (result is Result.Success) {
                load()
            } else {
                _uiState.value = current.copy(isGenerating = false)
                _effects.send(
                    CoverLetterUiEffect.ShowSnackbar(
                        (result as? Result.Failure)?.error?.message ?: "Failed to regenerate section"
                    )
                )
            }
        }
    }

    private fun toggleEdit() {
        val current = _uiState.value as? CoverLetterUiState.Success ?: return
        val version = current.selectedVersion ?: current.coverLetter?.versions?.firstOrNull() ?: return
        if (current.isEditing) {
            // Leaving edit mode discards unsaved drafts.
            _uiState.value = current.copy(isEditing = false, sectionDrafts = emptyMap())
        } else {
            _uiState.value = current.copy(
                isEditing = true,
                sectionDrafts = version.sections.mapIndexed { index, section -> index to section.content }.toMap()
            )
        }
    }

    private fun updateSection(sectionIndex: Int, content: String) {
        val current = _uiState.value as? CoverLetterUiState.Success ?: return
        if (!current.isEditing) return
        _uiState.value = current.copy(sectionDrafts = current.sectionDrafts + (sectionIndex to content))
    }

    private fun saveEdits() {
        val current = _uiState.value as? CoverLetterUiState.Success ?: return
        val version = current.selectedVersion ?: current.coverLetter?.versions?.firstOrNull() ?: return
        if (!current.isEditing) return

        viewModelScope.launch {
            val updated = version.copy(
                lastModified = System.currentTimeMillis(),
                sections = version.sections.mapIndexed { index, section ->
                    current.sectionDrafts[index]?.let { section.copy(content = it) } ?: section
                }
            )
            val result = coverLetterRepository.saveVersion(updated)
            if (result is Result.Success) {
                trackEventUseCase(TrackEventRequest("cover_letter_edit_save"))
                _uiState.value = current.copy(isEditing = false, sectionDrafts = emptyMap())
                load()
                _effects.send(CoverLetterUiEffect.ShowSnackbar("Cover letter updated"))
            } else {
                val failure = result as? Result.Failure
                _effects.send(
                    CoverLetterUiEffect.ShowSnackbar(
                        failure?.error?.message?.takeIf { it.isNotBlank() } ?: "Failed to save edits"
                    )
                )
            }
        }
    }

    private fun copyAll() {
        val current = _uiState.value as? CoverLetterUiState.Success ?: return
        val version = current.selectedVersion ?: current.coverLetter?.versions?.firstOrNull() ?: return
        val fullText = version.sections.joinToString("\n\n") { it.content }
        if (fullText.isBlank()) return

        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("cover_letter_copy"))
            _effects.send(CoverLetterUiEffect.CopyText(fullText))
            _effects.send(CoverLetterUiEffect.ShowSnackbar("Cover letter copied"))
        }
    }

    private fun export() {
        val current = _uiState.value as? CoverLetterUiState.Success ?: return
        val coverLetter = current.coverLetter ?: return
        val version = current.selectedVersion ?: coverLetter.versions.firstOrNull() ?: return

        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("cover_letter_export"))
            // Real PDF render (Phase 4 STEP 2) — resolves the old plaintext
            // share-sheet export. Section headings become PDF headers.
            val sections = version.sections.map { it.title to it.content }
            val result = pdfExporter.exportToPdf(
                title = "Cover Letter — ${coverLetter.company}",
                sections = sections
            )
            when (result) {
                is Result.Success -> {
                    _effects.send(CoverLetterUiEffect.ExportPdf(result.data))
                    _effects.send(CoverLetterUiEffect.ShowSnackbar("Cover letter exported as PDF"))
                }
                is Result.Failure -> {
                    _effects.send(
                        CoverLetterUiEffect.ShowSnackbar(
                            result.error.message.ifBlank { "Failed to export cover letter" }
                        )
                    )
                }
            }
        }
    }
}
