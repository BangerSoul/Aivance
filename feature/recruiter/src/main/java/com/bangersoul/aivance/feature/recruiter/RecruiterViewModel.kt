package com.bangersoul.aivance.feature.recruiter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.OutreachDraft
import com.bangersoul.aivance.core.common.model.Recruiter
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.crm.FindRecruitersUseCase
import com.bangersoul.aivance.core.domain.usecase.crm.GenerateOutreachDraftUseCase
import com.bangersoul.aivance.core.domain.usecase.crm.OutreachRequest
import com.bangersoul.aivance.core.domain.usecase.job.GetJobDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RecruiterUiState {
    data object Loading : RecruiterUiState
    data class Success(
        val recruiters: List<Recruiter> = emptyList(),
        val selectedRecruiter: Recruiter? = null,
        val draft: OutreachDraft? = null,
        val isGenerating: Boolean = false,
        val outreachError: String? = null
    ) : RecruiterUiState
    data class Error(val message: String) : RecruiterUiState
}

sealed interface RecruiterUiEvent {
    data object Load : RecruiterUiEvent
    data class SelectRecruiter(val recruiter: Recruiter) : RecruiterUiEvent
    data class GenerateOutreach(val type: String) : RecruiterUiEvent
}

@HiltViewModel
class RecruiterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val findRecruitersUseCase: FindRecruitersUseCase,
    private val getJobDetailsUseCase: GetJobDetailsUseCase,
    private val generateOutreachDraftUseCase: GenerateOutreachDraftUseCase,
    private val resumeRepository: ResumeRepository,
    private val trackEventUseCase: TrackEventUseCase
) : ViewModel() {

    private var jobId: String = savedStateHandle.get<String>("jobId") ?: ""

    private val _uiState = MutableStateFlow<RecruiterUiState>(RecruiterUiState.Loading)
    val uiState: StateFlow<RecruiterUiState> = _uiState.asStateFlow()

    init {
        // The custom navigation back stack does not seed SavedStateHandle with
        // destination arguments, so the screen supplies the real job ID via load().
        if (jobId.isNotBlank()) {
            loadRecruiters()
        }
    }

    /** Loads recruiters for [jobId], driven by the screen's destination argument. */
    fun load(jobId: String) {
        this.jobId = jobId
        loadRecruiters()
    }

    fun onEvent(event: RecruiterUiEvent) {
        when (event) {
            RecruiterUiEvent.Load -> loadRecruiters()
            is RecruiterUiEvent.SelectRecruiter -> {
                val current = _uiState.value as? RecruiterUiState.Success ?: return
                _uiState.value = current.copy(selectedRecruiter = event.recruiter, draft = null)
            }
            is RecruiterUiEvent.GenerateOutreach -> generateOutreach(event.type)
        }
    }

    private fun loadRecruiters() {
        viewModelScope.launch {
            _uiState.value = RecruiterUiState.Loading

            // 1. Get Job Details to find Company Domain
            val jobResult = getJobDetailsUseCase(jobId)
            if (jobResult is Result.Success) {
                val companyName = jobResult.data.company
                // Simplification: In a real app we'd resolve domain properly
                val domain = companyName.lowercase().replace(" ", "") + ".com"

                // 2. Find Recruiters
                val recruiterResult = findRecruitersUseCase(domain)
                if (recruiterResult is Result.Success) {
                    _uiState.value = RecruiterUiState.Success(recruiters = recruiterResult.data)
                } else {
                    _uiState.value = RecruiterUiState.Error("Failed to find recruiters")
                }
            } else {
                _uiState.value = RecruiterUiState.Error("Job not found")
            }
        }
    }

    private fun generateOutreach(type: String) {
        val current = _uiState.value as? RecruiterUiState.Success ?: return
        val recruiter = current.selectedRecruiter ?: return

        viewModelScope.launch {
            _uiState.value = current.copy(isGenerating = true, outreachError = null)
            trackEventUseCase(TrackEventRequest("crm_generate_outreach"))

            // Resolve the user's real primary resume + version instead of the
            // previously hardcoded (1L, 1L). Without a resume the outreach draft
            // has no candidate context, so surface a clear, actionable error
            // rather than silently generating against a non-existent resume.
            val resume = resumeRepository.getResumes().firstOrNull()?.getOrNull()?.firstOrNull()
            val versionId = resume?.primaryVersionId
                ?: resume?.let { resumeRepository.getVersions(it.id).firstOrNull()?.getOrNull()?.firstOrNull()?.id }

            if (resume == null || versionId == null) {
                _uiState.value = current.copy(
                    isGenerating = false,
                    outreachError = "Add a resume in Resume Intelligence first — outreach drafts are tailored from your resume."
                )
                return@launch
            }

            val result = generateOutreachDraftUseCase(
                OutreachRequest(resume.id, versionId, recruiter.id, jobId, type)
            )

            when (result) {
                is Result.Success ->
                    _uiState.value = current.copy(isGenerating = false, draft = result.data, outreachError = null)
                is Result.Failure ->
                    _uiState.value = current.copy(
                        isGenerating = false,
                        outreachError = result.error.message ?: "Failed to generate outreach draft."
                    )
            }
        }
    }
}
