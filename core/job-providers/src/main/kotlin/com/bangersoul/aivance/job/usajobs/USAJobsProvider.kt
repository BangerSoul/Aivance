package com.bangersoul.aivance.job.usajobs

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.job.base.ProviderHttpException
import com.bangersoul.aivance.job.base.RestJobProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.mapper.JobMapper
import com.bangersoul.aivance.sdk.core.ConfigField
import com.bangersoul.aivance.sdk.core.FieldType
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * USAJobs - free official US federal government job search.
 * Requires a free API key from developer.usajobs.gov.
 */
class USAJobsProvider(
    private var apiKey: String,
    private val userAgent: String = "aivance-android@example.com",
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://data.usajobs.gov/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "usajobs",
        name = "USAJobs",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "US federal government jobs (free API).",
        icon = "https://www.usajobs.gov/favicon.ico",
        author = "BangerSoul",
        configFields = listOf(
            ConfigField(
                key = "apiKey",
                label = "USAJobs API Key",
                isRequired = true,
                isSensitive = true,
                fieldType = FieldType.PASSWORD,
                hint = "Free key from developer.usajobs.gov"
            )
        )
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: USAJobsApi by lazy { retrofit.create(USAJobsApi::class.java) }

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    override val hasCredentials: Boolean
        get() = isConfigured

    override suspend fun applyConfiguration(config: com.bangersoul.aivance.sdk.config.ProviderConfiguration) {
        apiKey = config.secrets["apiKey"] ?: apiKey
    }

    override suspend fun executeSearch(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): List<JobListing> {
        val response = api.search(
            apiKey = apiKey,
            userAgent = userAgent,
            keyword = filter.query.ifBlank { null },
            locationName = filter.location.ifBlank { null },
            remoteIndicator = filter.remoteType?.let { it == RemoteType.REMOTE },
            page = page
        )
        if (response.isSuccessful) {
            return response.body()?.searchResult?.items
                ?.mapNotNull { it.descriptor }
                ?.map { JobMapper.mapToJobListing(it, metadata.id) }
                ?: emptyList()
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "USAJobs API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }



    override suspend fun performHealthCheck() {
        if (apiKey.isBlank()) {
            throw Exception("USAJobs API Key not configured")
        }
    }

    override suspend fun onInitialize() {
        super.onInitialize()
        // Stay out of Active/Ready until the user provides a real API key,
        // so search aggregation filters this provider out.
        if (apiKey.isBlank()) {
            updateStatus(ProviderStatus.InvalidConfiguration)
        }
    }
}
