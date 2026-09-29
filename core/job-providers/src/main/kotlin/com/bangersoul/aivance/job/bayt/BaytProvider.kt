package com.bangersoul.aivance.job.bayt

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.job.base.ProviderHttpException
import com.bangersoul.aivance.job.base.RestJobProvider
import com.bangersoul.aivance.job.bayt.dto.BaytResponseDto
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.mapper.JobMapper
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.UUID

interface BaytApi {
    @GET("api/v1/jobs")
    suspend fun searchJobs(
        @Query("q") query: String?,
        @Query("l") location: String?,
        @Query("page") page: Int = 1
    ): Response<BaytResponseDto>
}

/**
 * Free Middle East & Gulf regional job board provider.
 */
class BaytProvider(
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://www.bayt.com/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "bayt",
        name = "Bayt",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Middle East & Gulf regional job search engine.",
        icon = "https://www.bayt.com/favicon.ico",
        author = "BangerSoul"
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: BaytApi by lazy { retrofit.create(BaytApi::class.java) }

    override suspend fun executeSearch(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): List<JobListing> {
        val response = api.searchJobs(
            query = filter.query.ifBlank { null },
            location = filter.location.ifBlank { null },
            page = page
        )
        if (response.isSuccessful) {
            val jobs = response.body()?.results ?: emptyList()
            return jobs.map { dto ->
                val loc = dto.location ?: "Middle East"
                val isRemote = loc.contains("Remote", ignoreCase = true) || filter.remoteType == RemoteType.REMOTE
                JobListing(
                    id = dto.id ?: UUID.randomUUID().toString(),
                    title = dto.title ?: "No Title",
                    company = dto.company ?: "Unknown Company",
                    location = loc,
                    salaryMin = JobMapper.parseSalary(dto.salary, true),
                    salaryMax = JobMapper.parseSalary(dto.salary, false),
                    currency = "USD",
                    remoteType = if (isRemote) RemoteType.REMOTE else RemoteType.ON_SITE,
                    isRemote = isRemote,
                    description = dto.description ?: "",
                    url = dto.link ?: "",
                    sourceProvider = metadata.id,
                    postedDate = JobMapper.parseDate(dto.datePosted)
                )
            }
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "Bayt API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }
}
