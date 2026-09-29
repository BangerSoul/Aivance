package com.bangersoul.aivance.feature.profile

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads real device constraints from the Android framework:
 * - free storage on the app-data volume via [StatFs] (the on-device model is
 *   downloaded into app-private storage, so this is the volume that matters);
 * - total physical RAM via [android.app.ActivityManager.MemoryInfo.totalMem].
 *
 * Framework calls are moved off the main thread (filesystem syscalls + binder).
 */
class AndroidDeviceCapabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceCapabilityProvider {

    override suspend fun currentCapability(): DeviceCapability = withContext(Dispatchers.IO) {
        val stats = StatFs(context.filesDir.path)
        val freeStorageBytes = stats.availableBytes

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        DeviceCapability(
            freeStorageBytes = freeStorageBytes,
            totalRamBytes = memInfo.totalMem
        )
    }
}
