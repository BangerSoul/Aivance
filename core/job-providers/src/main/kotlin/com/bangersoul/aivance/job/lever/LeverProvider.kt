package com.bangersoul.aivance.job.lever

import com.bangersoul.aivance.core.common.enums.EmploymentType
import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.job.base.ProviderHttpException
import com.bangersoul.aivance.job.base.RestJobProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.lever.dto.LeverJobDto
import com.bangersoul.aivance.sdk.core.ConfigField
import com.bangersoul.aivance.sdk.core.FieldType
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class LeverProvider(
    private var companyId: String,
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://api.lever.co/v0/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "lever",
        name = "Lever",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Job listings from Lever ATS.",
        icon = "https://www.lever.co/favicon.ico",
        author = "BangerSoul",
        configFields = listOf(
            ConfigField(
                key = "companyId",
                label = "Lever Company ID",
                isRequired = true
            )
        )
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: LeverApi by lazy { retrofit.create(LeverApi::class.java) }

    override val isConfigured: Boolean
        get() = companyId.isNotBlank()

    override val hasCredentials: Boolean
        get() = isConfigured

    override suspend fun applyConfiguration(config: com.bangersoul.aivance.sdk.config.ProviderConfiguration) {
        // companyId is a non-sensitive TEXT field, stored in settings.
        companyId = config.settings["companyId"] ?: companyId
    }

    override suspend fun executeSearch(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): List<JobListing> {
        val response = api.getJobs(companyId)
        if (response.isSuccessful) {
            return response.body()?.filter {
                it.text?.contains(filter.query, ignoreCase = true) == true &&
                (filter.location.isBlank() || it.categories?.location?.contains(filter.location, ignoreCase = true) == true)
            }?.map { mapToJobListing(it) } ?: emptyList()
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "Lever API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }



    override suspend fun onInitialize() {
        super.onInitialize()
        // Stay out of Active/Ready until the user provides a real company ID,
        // so search aggregation filters this provider out.
        if (companyId.isBlank()) {
            updateStatus(ProviderStatus.InvalidConfiguration)
        }
    }

    private fun mapToJobListing(dto: LeverJobDto): JobListing {
        return JobListing(
            id = dto.id ?: "",
            title = dto.text ?: "No Title",
            company = companyId.replaceFirstChar { it.uppercase() },
            location = dto.categories?.location ?: "Unknown",
            employmentType = parseEmploymentType(dto.categories?.commitment),
            remoteType = if (dto.categories?.location?.contains("Remote", ignoreCase = true) == true) RemoteType.REMOTE else RemoteType.ON_SITE,
            isRemote = dto.categories?.location?.contains("Remote", ignoreCase = true) == true,
            description = dto.description ?: "",
            url = dto.applyUrl ?: "",
            sourceProvider = metadata.id,
            postedDate = dto.createdAt ?: System.currentTimeMillis()
        )
    }

    private fun parseEmploymentType(commitment: String?): EmploymentType {
        return when (commitment?.lowercase()) {
            "full-time", "full time" -> EmploymentType.FULL_TIME
            "part-time", "part time" -> EmploymentType.PART_TIME
            "contract" -> EmploymentType.CONTRACT
            "internship" -> EmploymentType.INTERNSHIP
            else -> EmploymentType.FULL_TIME
        }
    }
}
