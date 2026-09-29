package com.bangersoul.aivance.job.greenhouse

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
import com.bangersoul.aivance.job.greenhouse.dto.GreenhouseJobDto
import com.bangersoul.aivance.sdk.core.ConfigField
import com.bangersoul.aivance.sdk.core.FieldType
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Locale

class GreenhouseProvider(
    private var boardToken: String,
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://boards-api.greenhouse.io/v1/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "greenhouse",
        name = "Greenhouse",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Job listings from Greenhouse ATS.",
        icon = "https://www.greenhouse.io/favicon.ico",
        author = "BangerSoul",
        configFields = listOf(
            ConfigField(
                key = "boardToken",
                label = "Greenhouse Board Token",
                isRequired = true
            )
        )
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: GreenhouseApi by lazy { retrofit.create(GreenhouseApi::class.java) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    override val isConfigured: Boolean
        get() = boardToken.isNotBlank()

    override val hasCredentials: Boolean
        get() = isConfigured

    override suspend fun applyConfiguration(config: com.bangersoul.aivance.sdk.config.ProviderConfiguration) {
        // boardToken is a non-sensitive TEXT field, stored in settings.
        boardToken = config.settings["boardToken"] ?: boardToken
    }

    override suspend fun executeSearch(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): List<JobListing> {
        val response = api.getJobs(boardToken)
        if (response.isSuccessful) {
            return response.body()?.jobs?.filter {
                it.title?.contains(filter.query, ignoreCase = true) == true &&
                (filter.location.isBlank() || it.location?.name?.contains(filter.location, ignoreCase = true) == true)
            }?.map { mapToJobListing(it) } ?: emptyList()
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "Greenhouse API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }



    override suspend fun onInitialize() {
        super.onInitialize()
        // Stay out of Active/Ready until the user provides a real board token,
        // so search aggregation filters this provider out.
        if (boardToken.isBlank()) {
            updateStatus(ProviderStatus.InvalidConfiguration)
        }
    }

    private fun mapToJobListing(dto: GreenhouseJobDto): JobListing {
        return JobListing(
            id = dto.id?.toString() ?: "",
            title = dto.title ?: "No Title",
            company = boardToken.replaceFirstChar { it.uppercase() },
            location = dto.location?.name ?: "Unknown",
            employmentType = EmploymentType.FULL_TIME,
            remoteType = if (dto.location?.name?.contains("Remote", ignoreCase = true) == true) RemoteType.REMOTE else RemoteType.ON_SITE,
            isRemote = dto.location?.name?.contains("Remote", ignoreCase = true) == true,
            description = dto.content ?: "",
            url = dto.absoluteUrl ?: "",
            sourceProvider = metadata.id,
            postedDate = try {
                dto.updatedAt?.let { dateFormat.parse(it)?.time } ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        )
    }
}
