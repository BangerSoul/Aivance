package com.bangersoul.aivance.job.naukri

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.job.base.ProviderHttpException
import com.bangersoul.aivance.job.base.RestJobProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.mapper.JobMapper
import com.bangersoul.aivance.job.naukri.dto.NaukriResponseDto
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.UUID

interface NaukriApi {
    @GET("v1/job-search")
    suspend fun searchJobs(
        @Query("k") keyword: String?,
        @Query("l") location: String?,
        @Query("pageNo") page: Int = 1
    ): Response<NaukriResponseDto>
}

/**
 * Free job provider modeling JobSpy direct Naukri request flow.
 */
class NaukriProvider(
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://www.naukri.com/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "naukri",
        name = "Naukri",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Direct Naukri job search engine.",
        icon = "https://www.naukri.com/favicon.ico",
        author = "BangerSoul"
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: NaukriApi by lazy { retrofit.create(NaukriApi::class.java) }

    override suspend fun executeSearch(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): List<JobListing> {
        val response = api.searchJobs(
            keyword = filter.query.ifBlank { null },
            location = filter.location.ifBlank { null },
            page = page
        )
        if (response.isSuccessful) {
            val jobs = response.body()?.jobDetails ?: emptyList()
            return jobs.map { dto ->
                val loc = dto.location ?: "India"
                val isRemote = loc.contains("Remote", ignoreCase = true) || filter.remoteType == RemoteType.REMOTE
                JobListing(
                    id = dto.jobId ?: UUID.randomUUID().toString(),
                    title = dto.title ?: "No Title",
                    company = dto.companyName ?: "Unknown Company",
                    location = loc,
                    salaryMin = JobMapper.parseSalary(dto.salary, true),
                    salaryMax = JobMapper.parseSalary(dto.salary, false),
                    currency = "INR",
                    remoteType = if (isRemote) RemoteType.REMOTE else RemoteType.ON_SITE,
                    isRemote = isRemote,
                    description = dto.jobDescription ?: "",
                    url = dto.staticUrl ?: "",
                    sourceProvider = metadata.id,
                    postedDate = dto.createdDate ?: System.currentTimeMillis()
                )
            }
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "Naukri API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }
}
