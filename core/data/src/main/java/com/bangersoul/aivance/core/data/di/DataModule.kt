package com.bangersoul.aivance.core.data.di

import android.content.Context
import com.bangersoul.aivance.core.common.model.AiProviderConfig
import com.bangersoul.aivance.core.common.model.UserProfile
import com.bangersoul.aivance.core.data.cache.CacheManager
import com.bangersoul.aivance.core.data.company.CompanyCatalog
import com.bangersoul.aivance.core.data.cache.MemoryCache
import com.bangersoul.aivance.core.data.config.PlayIntegrityManagerImpl
import com.bangersoul.aivance.core.data.config.PrivacyManagerImpl
import com.bangersoul.aivance.core.data.util.Clock
import com.bangersoul.aivance.core.data.util.DefaultClock
import com.bangersoul.aivance.core.data.service.TextGenerationServiceImpl
import com.bangersoul.aivance.core.domain.config.PlayIntegrityManager
import com.bangersoul.aivance.core.domain.config.PrivacyManager
import com.bangersoul.aivance.core.domain.service.TextGenerationService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindPrivacyManager(impl: PrivacyManagerImpl): PrivacyManager

    @Binds
    @Singleton
    abstract fun bindPlayIntegrityManager(impl: PlayIntegrityManagerImpl): PlayIntegrityManager

    @Binds
    @Singleton
    abstract fun bindTextGenerationService(
        impl: TextGenerationServiceImpl
    ): TextGenerationService

    companion object {
        @Provides
        @Singleton
        fun provideCompanyCatalog(@ApplicationContext context: Context): CompanyCatalog =
            CompanyCatalog.fromAsset(context)

        @Provides
        @Singleton
        fun provideClock(): Clock = DefaultClock()

        @Provides
        @Singleton
        fun provideUserProfileCache(clock: Clock): CacheManager<String, UserProfile> = MemoryCache(clock)

        @Provides
        @Singleton
        fun provideAiConfigCache(clock: Clock): CacheManager<String, List<AiProviderConfig>> = MemoryCache(clock)
    }
}
