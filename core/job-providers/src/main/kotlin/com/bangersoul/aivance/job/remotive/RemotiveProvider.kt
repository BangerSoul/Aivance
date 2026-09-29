package com.bangersoul.aivance.job.remotive

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
import com.bangersoul.aivance.job.remotive.dto.RemotiveJobDto
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Locale

class RemotiveProvider(
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://remotive.com/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "remotive",
        name = "Remotive",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Remote jobs curated by Remotive.",
        icon = "https://remotive.com/favicon.ico",
        author = "BangerSoul"
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: RemotiveApi by lazy { retrofit.create(RemotiveApi::class.java) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    override suspend fun executeSearch(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): List<JobListing> {
        val response = api.getJobs(query = filter.query)
        if (response.isSuccessful) {
            return response.body()?.jobs?.map { mapToJobListing(it) } ?: emptyList()
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "Remotive API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }



    private fun mapToJobListing(dto: RemotiveJobDto): JobListing {
        return JobListing(
            id = dto.id?.toString() ?: "",
            title = dto.title ?: "No Title",
            company = dto.companyName ?: "Unknown Company",
            companyLogoUrl = dto.companyLogo,
            location = dto.candidateLocation ?: "Remote",
            employmentType = parseEmploymentType(dto.jobType),
            remoteType = RemoteType.REMOTE,
            isRemote = true,
            description = dto.description ?: "",
            url = dto.url ?: "",
            sourceProvider = metadata.id,
            postedDate = try {
                dto.publicationDate?.let { dateFormat.parse(it)?.time } ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        )
    }

    private fun parseEmploymentType(type: String?): EmploymentType {
        return when (type?.lowercase()) {
            "full_time", "full-time" -> EmploymentType.FULL_TIME
            "part_time", "part-time" -> EmploymentType.PART_TIME
            "contract" -> EmploymentType.CONTRACT
            "internship", "intern" -> EmploymentType.INTERNSHIP
            "apprenticeship", "apprentice" -> EmploymentType.APPRENTICESHIP
            else -> EmploymentType.FULL_TIME
        }
    }
}
