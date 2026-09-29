package com.bangersoul.aivance.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.datastore.ThemeConfig
import com.bangersoul.aivance.core.datastore.UserPreferencesRepository
import com.bangersoul.aivance.core.designsystem.theme.AccentSeed
import com.bangersoul.aivance.core.designsystem.theme.DesignKit
import com.bangersoul.aivance.core.designsystem.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Resolves persisted user preferences into theme configuration
 * consumed by [AivanceAppShell].
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val themeState: StateFlow<AppThemeState> = userPreferencesRepository.userPreferences
        .map { prefs ->
            AppThemeState(
                themeMode = prefs.themeConfig.toThemeMode(),
                accentSeed = runCatching {
                    AccentSeed.valueOf(prefs.accentSeed)
                }.getOrDefault(AccentSeed.INDIGO),
                dynamicColor = prefs.dynamicColor,
                designKit = runCatching {
                    DesignKit.valueOf(prefs.designKit)
                }.getOrDefault(DesignKit.AURORA_GLASS)
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppThemeState()
        )
}

data class AppThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentSeed: AccentSeed = AccentSeed.INDIGO,
    val dynamicColor: Boolean = true,
    val designKit: DesignKit = DesignKit.AURORA_GLASS
)

private fun ThemeConfig.toThemeMode(): ThemeMode = when (this) {
    ThemeConfig.FOLLOW_SYSTEM -> ThemeMode.SYSTEM
    ThemeConfig.LIGHT -> ThemeMode.LIGHT
    ThemeConfig.DARK -> ThemeMode.DARK
    ThemeConfig.AMOLED -> ThemeMode.AMOLED
}
