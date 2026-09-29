package com.bangersoul.aivance.feature.profile.di

import com.bangersoul.aivance.feature.profile.AndroidDeviceCapabilityProvider
import com.bangersoul.aivance.feature.profile.DeviceCapabilityProvider
import com.bangersoul.aivance.feature.profile.worker.ModelDownloadScheduler
import com.bangersoul.aivance.feature.profile.worker.WorkManagerModelDownloadScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {

    @Binds
    abstract fun bindDeviceCapabilityProvider(
        impl: AndroidDeviceCapabilityProvider
    ): DeviceCapabilityProvider

    @Binds
    abstract fun bindModelDownloadScheduler(
        impl: WorkManagerModelDownloadScheduler
    ): ModelDownloadScheduler
}
