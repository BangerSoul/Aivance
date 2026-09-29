package com.bangersoul.aivance.feature.profile

/**
 * Snapshot of a device's on-device-model constraints.
 *
 * @param freeStorageBytes Free bytes on the app-data volume (the model downloads
 *   into app-private storage).
 * @param totalRamBytes Total physical RAM reported by the system.
 */
data class DeviceCapability(
    val freeStorageBytes: Long,
    val totalRamBytes: Long
) {
    companion object {
        /** Minimum free storage required before offering the primary model download. */
        const val MIN_REQUIRED_FREE_STORAGE_BYTES: Long = 2L * 1024 * 1024 * 1024 // 2 GiB

        /** Below this total RAM the primary model is offered with a warning. */
        const val MIN_RECOMMENDED_TOTAL_RAM_BYTES: Long = 4L * 1024 * 1024 * 1024 // 4 GiB
    }

    /** True when total RAM meets the ≥4 GiB recommendation. */
    val hasRecommendedRam: Boolean
        get() = totalRamBytes >= MIN_RECOMMENDED_TOTAL_RAM_BYTES
}

/** Supplies the device's current storage/RAM constraints. */
fun interface DeviceCapabilityProvider {
    suspend fun currentCapability(): DeviceCapability
}
