package com.bangersoul.aivance.job.glassdoor

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.job.base.ProviderHttpException
import com.bangersoul.aivance.job.base.RestJobProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.glassdoor.dto.GlassdoorResponseDto
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.UUID

interface GlassdoorApi {
    @GET("api/jobs/search")
    suspend fun searchJobs(
        @Query("keyword") keyword: String?,
        @Query("location") location: String?,
        @Query("page") page: Int = 1
    ): Response<GlassdoorResponseDto>
}

/**
 * Free job provider modeling JobSpy's direct Glassdoor search request flow.
 */
class GlassdoorProvider(
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://www.glassdoor.com/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "glassdoor",
        name = "Glassdoor",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Direct Glassdoor job search engine.",
        icon = "https://www.glassdoor.com/favicon.ico",
        author = "BangerSoul"
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: GlassdoorApi by lazy { retrofit.create(GlassdoorApi::class.java) }

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
            val jobs = response.body()?.jobs ?: emptyList()
            return jobs.map { dto ->
                val loc = dto.locationName ?: "Unknown"
                val isRemote = loc.contains("Remote", ignoreCase = true) || filter.remoteType == RemoteType.REMOTE
                var min = dto.payMin
                var max = dto.payMax
                if (dto.payPeriod?.equals("HOURLY", ignoreCase = true) == true) {
                    min = min?.times(2080)
                    max = max?.times(2080)
                }
                JobListing(
                    id = dto.jobId ?: UUID.randomUUID().toString(),
                    title = dto.jobTitle ?: "No Title",
                    company = dto.employerName ?: "Unknown Company",
                    location = loc,
                    salaryMin = min,
                    salaryMax = max,
                    currency = "USD",
                    remoteType = if (isRemote) RemoteType.REMOTE else RemoteType.ON_SITE,
                    isRemote = isRemote,
                    description = dto.jobDescription ?: "",
                    url = dto.jobUrl ?: "",
                    sourceProvider = metadata.id,
                    postedDate = System.currentTimeMillis() - ((dto.ageInDays ?: 0) * 86400000L)
                )
            }
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "Glassdoor API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }
}
