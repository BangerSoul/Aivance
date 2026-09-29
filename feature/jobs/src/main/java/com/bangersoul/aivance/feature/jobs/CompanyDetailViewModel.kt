package com.bangersoul.aivance.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.CompanyCatalogEntry
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.CompanyCatalogRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CompanyDetailUiState {
    data object Loading : CompanyDetailUiState
    data class Success(
        val companyName: String,
        val companyLogoUrl: String? = null,
        val location: String = "",
        val openRoles: List<JobListing> = emptyList(),
        /**
         * Bundled remote-company catalog entry (R-02) when the company is
         * indexed — carries remote policy, size, region, tech stack, careers URL.
         */
        val catalog: CompanyCatalogEntry? = null
    ) : CompanyDetailUiState
    data class Error(val message: String) : CompanyDetailUiState
}

/**
 * Resolves a company profile from the real job dataset. The domain layer has no
 * standalone company repository, so the profile is derived from the jobs that
 * carry the company name/logo/location, and open roles are surfaced alongside.
 */
@HiltViewModel
class CompanyDetailViewModel @Inject constructor(
    private val jobRepository: JobRepository,
    private val companyCatalogRepository: CompanyCatalogRepository,
    private val trackEventUseCase: TrackEventUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CompanyDetailUiState>(CompanyDetailUiState.Loading)
    val uiState: StateFlow<CompanyDetailUiState> = _uiState.asStateFlow()

    fun load(companyId: String) {
        viewModelScope.launch {
            _uiState.value = CompanyDetailUiState.Loading
            trackEventUseCase(TrackEventRequest(eventName = "company_detail_open"))

            val result = jobRepository.getJobs().firstOrNull()
            val jobs = when (result) {
                is Result.Success -> result.data
                is Result.Failure -> {
                    _uiState.value = CompanyDetailUiState.Error(
                        result.error.message ?: "Failed to load company"
                    )
                    return@launch
                }
                null -> {
                    _uiState.value = CompanyDetailUiState.Error("Failed to load company")
                    return@launch
                }
            }

            // Match by company id (job id) first, then by normalized company name.
            val matching = jobs.filter {
                it.id == companyId || it.company.equals(companyId, ignoreCase = true)
            }
            val companyJob = matching.firstOrNull() ?: jobs.firstOrNull()

            if (companyJob == null) {
                _uiState.value = CompanyDetailUiState.Error("Company not found")
            } else {
                // Prefer the exact name lookup; fall back to the registry domain
                // when the listing only carries a domain-style company string.
                val catalog = companyCatalogRepository.findCompany(companyJob.company)
                    ?: companyCatalogRepository.findCompanyByDomain(companyJob.company)
                _uiState.value = CompanyDetailUiState.Success(
                    companyName = companyJob.company,
                    companyLogoUrl = companyJob.companyLogoUrl,
                    location = companyJob.location,
                    openRoles = matching,
                    catalog = catalog
                )
            }
        }
    }
}
