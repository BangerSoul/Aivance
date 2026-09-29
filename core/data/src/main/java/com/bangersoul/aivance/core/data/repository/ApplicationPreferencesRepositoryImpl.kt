package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.datastore.PreferencesManager
import com.bangersoul.aivance.core.domain.repository.ApplicationPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationPreferencesRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ApplicationPreferencesRepository {

    override val dailyApplicationCap: Flow<Int> =
        preferencesManager.getIntFlow(KEY_DAILY_CAP, ApplicationPreferencesRepository.DEFAULT_DAILY_CAP)
            .map { it.coerceIn(1, MAX_CAP) }

    override suspend fun setDailyApplicationCap(cap: Int) {
        preferencesManager.putInt(KEY_DAILY_CAP, cap.coerceIn(1, MAX_CAP))
    }

    private companion object {
        const val KEY_DAILY_CAP = "daily_application_cap"

        /** Sensible upper bound so a mis-entered value can't disable the counter. */
        const val MAX_CAP = 100
    }
}
