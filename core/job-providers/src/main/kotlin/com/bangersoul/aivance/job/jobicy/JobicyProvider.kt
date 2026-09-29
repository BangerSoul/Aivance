package com.bangersoul.aivance.job.jobicy

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.job.base.ProviderHttpException
import com.bangersoul.aivance.job.base.RestJobProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.mapper.JobMapper
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Free global remote job board. No API key required.
 */
class JobicyProvider(
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://jobicy.com/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "jobicy",
        name = "Jobicy",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Global remote jobs, free (no API key).",
        icon = "https://jobicy.com/favicon.ico",
        author = "BangerSoul"
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: JobicyApi by lazy { retrofit.create(JobicyApi::class.java) }

    override suspend fun executeSearch(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): List<JobListing> {
        // Jobicy v2 has no free-text search param - only geo/industry/tag.
        // Passing arbitrary free text as `industry`/`geo` made Jobicy respond
        // with HTTP 400 (it only accepts known values), which zeroed out every
        // queried search. Fetch recent global jobs and filter client-side.
        val response = api.getJobs(
            count = 100,
            geo = null,
            industry = null
        )
        if (response.isSuccessful) {
            return response.body()?.jobs
                ?.filter { job ->
                    filter.query.isBlank() ||
                        job.jobTitle?.contains(filter.query, ignoreCase = true) == true ||
                        job.companyName?.contains(filter.query, ignoreCase = true) == true
                }
                ?.map { JobMapper.mapToJobListing(it, metadata.id) }
                ?: emptyList()
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "Jobicy API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }


}
