package com.bangersoul.aivance.job.di

import com.bangersoul.aivance.job.adzuna.AdzunaProvider
import com.bangersoul.aivance.job.arbeitnow.ArbeitnowProvider
import com.bangersoul.aivance.job.bayt.BaytProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.cache.RoomJobCache
import com.bangersoul.aivance.job.glassdoor.GlassdoorProvider
import com.bangersoul.aivance.job.greenhouse.GreenhouseProvider
import com.bangersoul.aivance.job.indeed.IndeedProvider
import com.bangersoul.aivance.job.jobicy.JobicyProvider
import com.bangersoul.aivance.job.lever.LeverProvider
import com.bangersoul.aivance.job.linkedin.LinkedInProvider
import com.bangersoul.aivance.job.naukri.NaukriProvider
import com.bangersoul.aivance.job.remoteok.RemoteOKProvider
import com.bangersoul.aivance.job.remotive.RemotiveProvider
import com.bangersoul.aivance.job.usajobs.USAJobsProvider
import com.bangersoul.aivance.job.ziprecruiter.ZipRecruiterProvider
import com.bangersoul.aivance.sdk.api.JobProvider
import com.bangersoul.aivance.sdk.infrastructure.ProviderFactory
import com.bangersoul.aivance.sdk.infrastructure.credential
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import dagger.multibindings.StringKey
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class JobProvidersModule {

    @Binds
    @Singleton
    abstract fun bindJobCache(impl: RoomJobCache): JobCache

    companion object {
        @Provides
        @Singleton
        @IntoSet
        fun provideRemoteOKProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return RemoteOKProvider(jobCache, okHttpClient, retrofit)
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideRemotiveProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return RemotiveProvider(jobCache, okHttpClient, retrofit)
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideLinkedInProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return LinkedInProvider("", jobCache, okHttpClient, retrofit)
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideIndeedProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return IndeedProvider("", jobCache, okHttpClient, retrofit)
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideGreenhouseProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return GreenhouseProvider("", jobCache, okHttpClient, retrofit)
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideLeverProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return LeverProvider("", jobCache, okHttpClient, retrofit)
        }

        /**
         * Arbeitnow - free, no API key required. Jobs in Germany & the EU.
         */
        @Provides
        @Singleton
        @IntoSet
        fun provideArbeitnowProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return ArbeitnowProvider(jobCache, okHttpClient, retrofit)
        }

        /**
         * Jobicy - free, no API key required. Global remote jobs.
         */
        @Provides
        @Singleton
        @IntoSet
        fun provideJobicyProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return JobicyProvider(jobCache, okHttpClient, retrofit)
        }

        /**
         * Adzuna - free tier (16 countries). Requires app_id + app_key.
         */
        @Provides
        @Singleton
        @IntoSet
        fun provideAdzunaProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return AdzunaProvider("", "", "us", jobCache, okHttpClient, retrofit)
        }

        /**
         * USAJobs - free US federal job API. Requires a free API key.
         */
        @Provides
        @Singleton
        @IntoSet
        fun provideUSAJobsProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return USAJobsProvider("", jobCache = jobCache, okHttpClient = okHttpClient, baseRetrofit = retrofit)
        }

        /**
         * Adzuna — keyed job provider factory. Registered in [ProviderFactory] so
         * the SDK can construct a configured instance from persisted settings
         * (`appId`) + secrets (`appKey`) via the formal config path (T-03).
         */
        @Provides
        @IntoMap
        @StringKey("adzuna")
        fun provideAdzunaFactory(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): ProviderFactory.Factory {
            return ProviderFactory.Factory { config ->
                AdzunaProvider(
                    appId = config.credential("appId"),
                    appKey = config.credential("appKey"),
                    jobCache = jobCache,
                    okHttpClient = okHttpClient,
                    baseRetrofit = retrofit
                )
            }
        }

        /**
         * USAJobs — keyed job provider factory. See [provideAdzunaFactory].
         */
        @Provides
        @IntoMap
        @StringKey("usajobs")
        fun provideUSAJobsFactory(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): ProviderFactory.Factory {
            return ProviderFactory.Factory { config ->
                USAJobsProvider(
                    apiKey = config.credential("apiKey"),
                    jobCache = jobCache,
                    okHttpClient = okHttpClient,
                    baseRetrofit = retrofit
                )
            }
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideZipRecruiterProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return ZipRecruiterProvider(jobCache, okHttpClient, retrofit)
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideGlassdoorProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return GlassdoorProvider(jobCache, okHttpClient, retrofit)
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideBaytProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return BaytProvider(jobCache, okHttpClient, retrofit)
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideNaukriProvider(
            jobCache: JobCache,
            okHttpClient: OkHttpClient,
            retrofit: Retrofit
        ): JobProvider {
            return NaukriProvider(jobCache, okHttpClient, retrofit)
        }
    }
}
