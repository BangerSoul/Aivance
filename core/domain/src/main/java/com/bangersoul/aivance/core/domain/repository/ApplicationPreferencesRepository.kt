package com.bangersoul.aivance.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * User preferences for the application pipeline (R-07) — the configurable
 * daily application cap that the Tracker compares today's count against.
 */
interface ApplicationPreferencesRepository {

    /** The user's daily application cap (defaults to [DEFAULT_DAILY_CAP]). */
    val dailyApplicationCap: Flow<Int>

    suspend fun setDailyApplicationCap(cap: Int)

    companion object {
        const val DEFAULT_DAILY_CAP = 5
    }
}
