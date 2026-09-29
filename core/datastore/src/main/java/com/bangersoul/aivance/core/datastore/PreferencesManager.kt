package com.bangersoul.aivance.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.privacyDataStore by preferencesDataStore(name = "privacy_preferences")

/**
 * Key-value preferences manager backed by AndroidX DataStore.
 *
 * Provides suspend-based get/set operations for common data types.
 * Designed for small, typed preference collections (e.g., privacy consent).
 */
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.privacyDataStore

    // ── Boolean ────────────────────────────────────────────────

    suspend fun getBoolean(key: String, default: Boolean): Boolean {
        return dataStore.data.first()[booleanPreferencesKey(key)] ?: default
    }

    suspend fun putBoolean(key: String, value: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(key)] = value
        }
    }

    // ── Int ────────────────────────────────────────────────────

    suspend fun getInt(key: String, default: Int): Int {
        return dataStore.data.first()[intPreferencesKey(key)] ?: default
    }

    /** Live variant of [getInt] — re-emits whenever the value changes. */
    fun getIntFlow(key: String, default: Int): Flow<Int> {
        return dataStore.data.map { prefs ->
            prefs[intPreferencesKey(key)] ?: default
        }
    }

    suspend fun putInt(key: String, value: Int) {
        dataStore.edit { prefs ->
            prefs[intPreferencesKey(key)] = value
        }
    }

    // ── Long ───────────────────────────────────────────────────

    suspend fun getLong(key: String, default: Long): Long {
        return dataStore.data.first()[longPreferencesKey(key)] ?: default
    }

    suspend fun putLong(key: String, value: Long) {
        dataStore.edit { prefs ->
            prefs[longPreferencesKey(key)] = value
        }
    }

    // ── String ─────────────────────────────────────────────────

    suspend fun getString(key: String, default: String): String {
        return dataStore.data.first()[stringPreferencesKey(key)] ?: default
    }

    /** Live variant of [getString] — re-emits whenever the value changes. */
    fun getStringFlow(key: String, default: String): Flow<String> {
        return dataStore.data.map { prefs ->
            prefs[stringPreferencesKey(key)] ?: default
        }
    }

    suspend fun putString(key: String, value: String) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(key)] = value
        }
    }

    // ── Bulk Operations ────────────────────────────────────────

    /**
     * Observe a boolean preference as a Flow.
     */
    fun observeBoolean(key: String, default: Boolean): Flow<Boolean> {
        return dataStore.data.map { prefs ->
            prefs[booleanPreferencesKey(key)] ?: default
        }
    }

    /**
     * Clear all preferences from this DataStore.
     */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    /**
     * Observe all stored preferences as a Flow.
     */
    fun observeAll(): Flow<Preferences> = dataStore.data
}
