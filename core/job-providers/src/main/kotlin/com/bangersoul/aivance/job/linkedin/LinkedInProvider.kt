package com.bangersoul.aivance.job.linkedin

import com.bangersoul.aivance.job.apify.ApifyJobProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.sdk.core.ConfigField
import com.bangersoul.aivance.sdk.core.FieldType
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class LinkedInProvider(
    apiKey: String,
    jobCache: JobCache,
    okHttpClient: OkHttpClient,
    baseRetrofit: Retrofit
) : ApifyJobProvider(
    metadata = ProviderMetadata(
        id = "linkedin",
        name = "LinkedIn",
        type = ProviderType.JOB,
        version = "1.0.0",
        description = "Job listings from LinkedIn via Apify.",
        icon = "https://www.linkedin.com/favicon.ico",
        author = "BangerSoul",
        configFields = listOf(
            ConfigField(
                key = "apiKey",
                label = "Apify API Key",
                isSensitive = true,
                fieldType = FieldType.PASSWORD
            )
        )
    ),
    apiKey = apiKey,
    // Canonical LinkedIn jobs actor (accepts positions[] input; the previous
    // valig actor ignored search input and returned evergreen postings only).
    actorId = "curious_coder~linkedin-jobs-scraper",
    jobCache = jobCache,
    okHttpClient = okHttpClient,
    baseRetrofit = baseRetrofit
)
