package com.bangersoul.aivance.job.ziprecruiter

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.job.base.ProviderHttpException
import com.bangersoul.aivance.job.base.RestJobProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.mapper.JobMapper
import com.bangersoul.aivance.job.ziprecruiter.dto.ZipRecruiterResponseDto
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.UUID

interface ZipRecruiterApi {
    @GET("api/jobs/v1")
    suspend fun searchJobs(
        @Query("search") query: String?,
        @Query("location") location: String?,
        @Query("page") page: Int = 1
    ): Response<ZipRecruiterResponseDto>
}

/**
 * Free job provider modeling JobSpy's direct ZipRecruiter request API flow.
 */
class ZipRecruiterProvider(
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://api.ziprecruiter.com/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "ziprecruiter",
        name = "ZipRecruiter",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Direct ZipRecruiter search, free (no key required).",
        icon = "https://www.ziprecruiter.com/favicon.ico",
        author = "BangerSoul"
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: ZipRecruiterApi by lazy { retrofit.create(ZipRecruiterApi::class.java) }

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
            val jobs = response.body()?.jobs ?: emptyList()
            return jobs.map { dto ->
                val loc = dto.location ?: "Unknown"
                val isRemote = loc.contains("Remote", ignoreCase = true) || filter.remoteType == RemoteType.REMOTE
                JobListing(
                    id = dto.id ?: UUID.randomUUID().toString(),
                    title = dto.name ?: "No Title",
                    company = dto.hiredCompany?.name ?: "Unknown Company",
                    location = loc,
                    salaryMin = JobMapper.parseSalary(dto.salary, true),
                    salaryMax = JobMapper.parseSalary(dto.salary, false),
                    currency = "USD",
                    remoteType = if (isRemote) RemoteType.REMOTE else RemoteType.ON_SITE,
                    isRemote = isRemote,
                    description = dto.snippet ?: "",
                    url = dto.url ?: "",
                    sourceProvider = metadata.id,
                    postedDate = JobMapper.parseDate(dto.postedTime)
                )
            }
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "ZipRecruiter API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }
}
