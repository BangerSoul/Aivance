package com.bangersoul.aivance.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// Neutral Palettes (Zinc family)
// ─────────────────────────────────────────────────────────────

// Light neutrals
val Zinc50 = Color(0xFFFAFAFA)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc500 = Color(0xFF71717A)
val Zinc600 = Color(0xFF52525B)
val Zinc700 = Color(0xFF3F3F46)
val Zinc800 = Color(0xFF27272A)
val Zinc900 = Color(0xFF18181B)
val Zinc950 = Color(0xFF09090B)

// ─────────────────────────────────────────────────────────────
// Accent Seed Palettes (Material-You inspired, static fallbacks)
// ─────────────────────────────────────────────────────────────

@Immutable
data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color
)

enum class AccentSeed {
    INDIGO,
    VIOLET,
    TEAL,
    EMERALD,
    ORANGE,
    ROSE,
    SKY;

    val label: String
        get() = when (this) {
            INDIGO -> "Indigo"
            VIOLET -> "Violet"
            TEAL -> "Teal"
            EMERALD -> "Emerald"
            ORANGE -> "Orange"
            ROSE -> "Rose"
            SKY -> "Sky"
        }
}

object AccentPalettes {

    fun light(seed: AccentSeed): AccentPalette = when (seed) {
        AccentSeed.INDIGO -> AccentPalette(
            primary = Color(0xFF4F46E5), onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF312E81),
            secondary = Color(0xFF7C3AED), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDE9FE), onSecondaryContainer = Color(0xFF5B21B6),
            tertiary = Color(0xFF0EA5E9), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE0F2FE), onTertiaryContainer = Color(0xFF0C4A6E)
        )
        AccentSeed.VIOLET -> AccentPalette(
            primary = Color(0xFF7C3AED), onPrimary = Color.White,
            primaryContainer = Color(0xFFEDE9FE), onPrimaryContainer = Color(0xFF5B21B6),
            secondary = Color(0xFFDB2777), onSecondary = Color.White,
            secondaryContainer = Color(0xFFFCE7F3), onSecondaryContainer = Color(0xFF9D174D),
            tertiary = Color(0xFF4F46E5), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE0E7FF), onTertiaryContainer = Color(0xFF312E81)
        )
        AccentSeed.TEAL -> AccentPalette(
            primary = Color(0xFF0D9488), onPrimary = Color.White,
            primaryContainer = Color(0xFFCCFBF1), onPrimaryContainer = Color(0xFF134E4A),
            secondary = Color(0xFF14B8A6), onSecondary = Color.White,
            secondaryContainer = Color(0xFF99F6E4), onSecondaryContainer = Color(0xFF115E59),
            tertiary = Color(0xFF0EA5E9), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE0F2FE), onTertiaryContainer = Color(0xFF0C4A6E)
        )
        AccentSeed.EMERALD -> AccentPalette(
            primary = Color(0xFF059669), onPrimary = Color.White,
            primaryContainer = Color(0xFFD1FAE5), onPrimaryContainer = Color(0xFF064E3B),
            secondary = Color(0xFF10B981), onSecondary = Color.White,
            secondaryContainer = Color(0xFFA7F3D0), onSecondaryContainer = Color(0xFF065F46),
            tertiary = Color(0xFFF59E0B), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFEF3C7), onTertiaryContainer = Color(0xFF92400E)
        )
        AccentSeed.ORANGE -> AccentPalette(
            primary = Color(0xFFEA580C), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFEDD5), onPrimaryContainer = Color(0xFF7C2D12),
            secondary = Color(0xFFF97316), onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFEDD5), onSecondaryContainer = Color(0xFF7C2D12),
            tertiary = Color(0xFFF59E0B), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFEF3C7), onTertiaryContainer = Color(0xFF92400E)
        )
        AccentSeed.ROSE -> AccentPalette(
            primary = Color(0xFFE11D48), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE4E6), onPrimaryContainer = Color(0xFF881337),
            secondary = Color(0xFFF43F5E), onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFE4E6), onSecondaryContainer = Color(0xFF881337),
            tertiary = Color(0xFF8B5CF6), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF5B21B6)
        )
        AccentSeed.SKY -> AccentPalette(
            primary = Color(0xFF0284C7), onPrimary = Color.White,
            primaryContainer = Color(0xFFE0F2FE), onPrimaryContainer = Color(0xFF0C4A6E),
            secondary = Color(0xFF38BDF8), onSecondary = Color(0xFF082F49),
            secondaryContainer = Color(0xFFBAE6FD), onSecondaryContainer = Color(0xFF075985),
            tertiary = Color(0xFF4F46E5), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE0E7FF), onTertiaryContainer = Color(0xFF312E81)
        )
    }

    fun dark(seed: AccentSeed): AccentPalette = when (seed) {
        AccentSeed.INDIGO -> AccentPalette(
            primary = Color(0xFF818CF8), onPrimary = Color(0xFF1E1B4B),
            primaryContainer = Color(0xFF3730A3), onPrimaryContainer = Color(0xFFE0E7FF),
            secondary = Color(0xFFA78BFA), onSecondary = Color(0xFF2E1065),
            secondaryContainer = Color(0xFF5B21B6), onSecondaryContainer = Color(0xFFEDE9FE),
            tertiary = Color(0xFF38BDF8), onTertiary = Color(0xFF082F49),
            tertiaryContainer = Color(0xFF0C4A6E), onTertiaryContainer = Color(0xFFE0F2FE)
        )
        AccentSeed.VIOLET -> AccentPalette(
            primary = Color(0xFFA78BFA), onPrimary = Color(0xFF2E1065),
            primaryContainer = Color(0xFF5B21B6), onPrimaryContainer = Color(0xFFEDE9FE),
            secondary = Color(0xFFF472B6), onSecondary = Color(0xFF500724),
            secondaryContainer = Color(0xFF9D174D), onSecondaryContainer = Color(0xFFFCE7F3),
            tertiary = Color(0xFF818CF8), onTertiary = Color(0xFF1E1B4B),
            tertiaryContainer = Color(0xFF3730A3), onTertiaryContainer = Color(0xFFE0E7FF)
        )
        AccentSeed.TEAL -> AccentPalette(
            primary = Color(0xFF2DD4BF), onPrimary = Color(0xFF042F2E),
            primaryContainer = Color(0xFF115E59), onPrimaryContainer = Color(0xFFCCFBF1),
            secondary = Color(0xFF5EEAD4), onSecondary = Color(0xFF042F2E),
            secondaryContainer = Color(0xFF134E4A), onSecondaryContainer = Color(0xFF99F6E4),
            tertiary = Color(0xFF38BDF8), onTertiary = Color(0xFF082F49),
            tertiaryContainer = Color(0xFF0C4A6E), onTertiaryContainer = Color(0xFFE0F2FE)
        )
        AccentSeed.EMERALD -> AccentPalette(
            primary = Color(0xFF34D399), onPrimary = Color(0xFF052E16),
            primaryContainer = Color(0xFF065F46), onPrimaryContainer = Color(0xFFD1FAE5),
            secondary = Color(0xFF6EE7B7), onSecondary = Color(0xFF052E16),
            secondaryContainer = Color(0xFF064E3B), onSecondaryContainer = Color(0xFFA7F3D0),
            tertiary = Color(0xFFFBBF24), onTertiary = Color(0xFF451A03),
            tertiaryContainer = Color(0xFF92400E), onTertiaryContainer = Color(0xFFFEF3C7)
        )
        AccentSeed.ORANGE -> AccentPalette(
            primary = Color(0xFFFB923C), onPrimary = Color(0xFF431407),
            primaryContainer = Color(0xFF9A3412), onPrimaryContainer = Color(0xFFFFEDD5),
            secondary = Color(0xFFFDBA74), onSecondary = Color(0xFF431407),
            secondaryContainer = Color(0xFF9A3412), onSecondaryContainer = Color(0xFFFFEDD5),
            tertiary = Color(0xFFFBBF24), onTertiary = Color(0xFF451A03),
            tertiaryContainer = Color(0xFF92400E), onTertiaryContainer = Color(0xFFFEF3C7)
        )
        AccentSeed.ROSE -> AccentPalette(
            primary = Color(0xFFFB7185), onPrimary = Color(0xFF4C0519),
            primaryContainer = Color(0xFF9F1239), onPrimaryContainer = Color(0xFFFFE4E6),
            secondary = Color(0xFFFDA4AF), onSecondary = Color(0xFF4C0519),
            secondaryContainer = Color(0xFF9F1239), onSecondaryContainer = Color(0xFFFFE4E6),
            tertiary = Color(0xFFA78BFA), onTertiary = Color(0xFF2E1065),
            tertiaryContainer = Color(0xFF5B21B6), onTertiaryContainer = Color(0xFFEDE9FE)
        )
        AccentSeed.SKY -> AccentPalette(
            primary = Color(0xFF38BDF8), onPrimary = Color(0xFF082F49),
            primaryContainer = Color(0xFF075985), onPrimaryContainer = Color(0xFFE0F2FE),
            secondary = Color(0xFF7DD3FC), onSecondary = Color(0xFF082F49),
            secondaryContainer = Color(0xFF0C4A6E), onSecondaryContainer = Color(0xFFBAE6FD),
            tertiary = Color(0xFF818CF8), onTertiary = Color(0xFF1E1B4B),
            tertiaryContainer = Color(0xFF3730A3), onTertiaryContainer = Color(0xFFE0E7FF)
        )
    }
}

/**
 * Kit B — Aurora Glass dark scheme. Cool blue-slate neutrals replace zinc so
 * glass panels carry hue; primary is the teal aurora end with a deep-slate
 * onPrimary (≥ 4.5:1 on both teal and indigo accents).
 */
fun buildAuroraDarkScheme(amoled: Boolean = false) = darkColorScheme(
    primary = AuroraColors.accentTeal,
    onPrimary = AuroraColors.onAccent,
    primaryContainer = Color(0xFF0E4F4A),
    onPrimaryContainer = Color(0xFFB8F1EA),
    secondary = AuroraColors.accentIndigo,
    onSecondary = Color(0xFF10143A),
    secondaryContainer = Color(0xFF343E7A),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF082F49),
    tertiaryContainer = Color(0xFF0C4A6E),
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = if (amoled) Color.Black else AuroraColors.bg,
    onBackground = AuroraColors.textPrimary,
    surface = if (amoled) Color.Black else AuroraColors.surface,
    onSurface = AuroraColors.textPrimary,
    surfaceVariant = if (amoled) AuroraColors.surface else AuroraColors.surfaceHigh,
    onSurfaceVariant = AuroraColors.textSecondary,
    surfaceContainer = if (amoled) Color(0xFF0A0E1A) else AuroraColors.surface,
    surfaceContainerHigh = AuroraColors.surfaceHigh,
    surfaceContainerHighest = AuroraColors.surfaceHighest,
    outline = AuroraColors.stroke,
    outlineVariant = AuroraColors.strokeSoft,
    error = AuroraColors.error,
    onError = Color(0xFF2B0708),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

// ─────────────────────────────────────────────────────────────
// Semantic extended colors (success / warning / info)
// ─────────────────────────────────────────────────────────────

@Immutable
data class AivanceExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val accent: Color,
    val onAccent: Color,
    val outlineSoft: Color
)

val LocalAivanceExtendedColors = staticCompositionLocalOf {
    AivanceExtendedColors(
        success = Color(0xFF16A34A), onSuccess = Color.White,
        successContainer = Color(0xFFDCFCE7), onSuccessContainer = Color(0xFF052E16),
        warning = Color(0xFFD97706), onWarning = Color.White,
        warningContainer = Color(0xFFFEF3C7), onWarningContainer = Color(0xFF451A03),
        info = Color(0xFF0284C7), onInfo = Color.White,
        infoContainer = Color(0xFFE0F2FE), onInfoContainer = Color(0xFF0C4A6E),
        accent = Color(0xFF4F46E5), onAccent = Color.White,
        outlineSoft = Color(0xFFE4E4E7)
    )
}

fun extendedColorsFor(isDark: Boolean): AivanceExtendedColors = if (isDark) {
    AivanceExtendedColors(
        success = Color(0xFF4ADE80), onSuccess = Color(0xFF052E16),
        successContainer = Color(0xFF14532D), onSuccessContainer = Color(0xFFDCFCE7),
        warning = Color(0xFFFBBF24), onWarning = Color(0xFF451A03),
        warningContainer = Color(0xFF92400E), onWarningContainer = Color(0xFFFEF3C7),
        info = Color(0xFF38BDF8), onInfo = Color(0xFF082F49),
        infoContainer = Color(0xFF0C4A6E), onInfoContainer = Color(0xFFE0F2FE),
        accent = Color(0xFF818CF8), onAccent = Color(0xFF1E1B4B),
        outlineSoft = Color(0xFF27272A)
    )
} else {
    AivanceExtendedColors(
        success = Color(0xFF16A34A), onSuccess = Color.White,
        successContainer = Color(0xFFDCFCE7), onSuccessContainer = Color(0xFF052E16),
        warning = Color(0xFFD97706), onWarning = Color.White,
        warningContainer = Color(0xFFFEF3C7), onWarningContainer = Color(0xFF451A03),
        info = Color(0xFF0284C7), onInfo = Color.White,
        infoContainer = Color(0xFFE0F2FE), onInfoContainer = Color(0xFF0C4A6E),
        accent = Color(0xFF4F46E5), onAccent = Color.White,
        outlineSoft = Color(0xFFE4E4E7)
    )
}

// ─────────────────────────────────────────────────────────────
// Legacy named colors (kept for backward compatibility)
// ─────────────────────────────────────────────────────────────

// Dark Palette (Linear/Vercel inspired)
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF09090B) // Zinc 950
val DarkSurfaceVariant = Color(0xFF18181B) // Zinc 900
val DarkPrimary = Color(0xFFFFFFFF)
val DarkOnPrimary = Color(0xFF000000)
val DarkSecondary = Color(0xFF71717A) // Zinc 500
val DarkOnSecondary = Color(0xFFFFFFFF)
val DarkTertiary = Color(0xFF3F3F46) // Zinc 700
val DarkAccent = Color(0xFF3B82F6) // Blue 500
val DarkError = Color(0xFFEF4444) // Red 500

// Light Palette (Refined fallback)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFAFAFA)
val LightSurfaceVariant = Color(0xFFF4F4F5) // Zinc 100
val LightPrimary = Color(0xFF000000)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightSecondary = Color(0xFF71717A) // Zinc 500
val LightOnSecondary = Color(0xFFFFFFFF)
val LightTertiary = Color(0xFFD4D4D8) // Zinc 300
val LightAccent = Color(0xFF2563EB) // Blue 600
val LightError = Color(0xFFDC2626) // Red 600

// Neutral Greys for Borders/Dividers
val ZincNeutral700 = Zinc700
val ZincNeutral800 = Zinc800
val ZincNeutral900 = Zinc900

/**
 * Builds a Material 3 color scheme from a given accent seed.
 * Used as the static fallback when dynamic color is disabled.
 */
fun buildAccentLightScheme(accent: AccentPalette) = lightColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    secondary = accent.secondary,
    onSecondary = accent.onSecondary,
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = accent.tertiary,
    onTertiary = accent.onTertiary,
    tertiaryContainer = accent.tertiaryContainer,
    onTertiaryContainer = accent.onTertiaryContainer,
    background = Zinc50,
    onBackground = Zinc950,
    surface = Color(0xFFFFFFFF),
    onSurface = Zinc950,
    surfaceVariant = Zinc100,
    onSurfaceVariant = Zinc600,
    surfaceContainer = Zinc100,
    surfaceContainerHigh = Zinc200,
    outline = Zinc300,
    outlineVariant = Zinc200,
    error = LightError,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

/**
 * Builds a Material 3 dark color scheme (and its AMOLED variant)
 * from a given accent seed.
 */
fun buildAccentDarkScheme(accent: AccentPalette, amoled: Boolean = false) = darkColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    secondary = accent.secondary,
    onSecondary = accent.onSecondary,
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = accent.tertiary,
    onTertiary = accent.onTertiary,
    tertiaryContainer = accent.tertiaryContainer,
    onTertiaryContainer = accent.onTertiaryContainer,
    background = if (amoled) Color.Black else Zinc950,
    onBackground = Color(0xFFF4F4F5),
    surface = if (amoled) Color.Black else DarkSurface,
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = if (amoled) Zinc900 else DarkSurfaceVariant,
    onSurfaceVariant = Zinc500,
    surfaceContainer = if (amoled) Zinc900 else Zinc900,
    surfaceContainerHigh = Zinc800,
    outline = Zinc700,
    outlineVariant = Zinc800,
    error = DarkError,
    onError = Color.Black,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)
