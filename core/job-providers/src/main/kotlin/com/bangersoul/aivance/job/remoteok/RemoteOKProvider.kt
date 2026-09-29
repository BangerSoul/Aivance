package com.bangersoul.aivance.job.remoteok

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
import com.bangersoul.aivance.job.remoteok.dto.RemoteOKJobDto
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Locale

class RemoteOKProvider(
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    override val baseUrl: String = "https://remoteok.com/"
) : RestJobProvider(
    metadata = ProviderMetadata(
        id = "remoteok",
        name = "RemoteOK",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Remote jobs for developers, designers, and more.",
        icon = "https://remoteok.com/assets/img/favicon.png",
        author = "BangerSoul"
    ),
    capabilities = setOf(ProviderCapability.JobSearch),
    jobCache = jobCache,
    baseOkHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
) {
    private val api: RemoteOKApi by lazy { retrofit.create(RemoteOKApi::class.java) }

    override suspend fun executeSearch(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder,
        page: Int
    ): List<JobListing> {
        val response = api.getJobs(tag = filter.query, location = filter.location)
        if (response.isSuccessful) {
            return response.body()?.filter { it.id != null }?.map { JobMapper.mapToJobListing(it, metadata.id) } ?: emptyList()
        } else {
            throw ProviderHttpException(
                providerId = metadata.id,
                statusCode = response.code(),
                message = "RemoteOK API failed",
                retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            )
        }
    }


}
