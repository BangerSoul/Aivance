package com.bangersoul.aivance.feature.resume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.domain.repository.AtsRepository
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AtsScanItem(
    val report: AtsReport,
    val jobTitle: String? = null,
    val companyName: String? = null
)

data class IntelligenceHubUiState(
    val isLoading: Boolean = true,
    val resumes: List<Resume> = emptyList(),
    val atsScans: List<AtsScanItem> = emptyList()
)

/**
 * Backs the Intelligence Hub with real data: the persisted resume list and the
 * most recent ATS scans (with the job title/company resolved from the linked
 * job description). Replaces the previous hardcoded placeholder cards.
 */
@HiltViewModel
class IntelligenceHubViewModel @Inject constructor(
    private val resumeRepository: ResumeRepository,
    private val atsRepository: AtsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntelligenceHubUiState())
    val uiState: StateFlow<IntelligenceHubUiState> = _uiState.asStateFlow()

    init {
        observeResumes()
        observeAtsScans()
    }

    private fun observeResumes() {
        viewModelScope.launch {
            resumeRepository.getResumes().collectLatest { result ->
                val resumes = result.getOrNull().orEmpty()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resumes = resumes.sortedByDescending { it.lastModified }
                )
            }
        }
    }

    private fun observeAtsScans() {
        viewModelScope.launch {
            atsRepository.getAllReports().collectLatest { result ->
                val reports = result.getOrNull().orEmpty()
                val scans = reports.take(5).map { report ->
                    val jd = atsRepository.getJobDescription(report.jobDescriptionId)
                    AtsScanItem(
                        report = report,
                        jobTitle = jd?.jobTitle,
                        companyName = jd?.companyName
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    atsScans = scans
                )
            }
        }
    }

    /** Deletes a saved ATS report; the [getAllReports] flow then drops it from the hub. */
    fun deleteReport(reportId: Long) {
        viewModelScope.launch {
            atsRepository.deleteReport(reportId)
        }
    }
}
