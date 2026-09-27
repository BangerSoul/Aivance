package com.bangersoul.aivance.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * BYOX Phase 1 — single source of truth for the design language.
 *
 * Every kit-expressive value (color stops, radii, border weights, surface
 * luminance, gradients) lives here instead of being scattered through
 * components. [DesignKit] selects which token set [AivanceTheme] composes;
 * components read tokens through [LocalDesignTokens] so a kit switch restyles
 * the whole app without touching feature code.
 */
enum class DesignKit(val label: String, val description: String) {
    /** Kit A — Executive Slate: light, flat, indigo-on-neutral (today's language, hardened). */
    EXECUTIVE_SLATE("Executive Slate", "Light · flat · indigo"),

    /** Kit B — Aurora Glass: dark glassmorphism, teal→indigo aurora accents. Default. */
    AURORA_GLASS("Aurora Glass", "Dark · glass · aurora gradient"),

    /** Kit C — Paper Studio: warm paper tones, serif-forward, softer radii. */
    PAPER_STUDIO("Paper Studio", "Warm paper · serif · soft")
}

/**
 * Aurora color stops — Kit B's glass palette. Neutral stops descend from
 * near-black blue slate (not pure zinc) so glass panels can pick up hue from
 * the aurora accents; the accent ramp pairs teal (energy) with indigo (trust)
 * as a gradient pair.
 */
object AuroraColors {
    /** Page background — deep blue-slate, never pure black (glass needs a hue to blur). */
    val bg = Color(0xFF070A13)

    /** Primary glass surface — 2-step lift above bg, the "sheet of smoked glass". */
    val surface = Color(0xFF0D1220)

    /** Elevated glass — cards, sheets, dialogs. */
    val surfaceHigh = Color(0xFF141B2E)

    /** Highest glass — floating chrome (nav bar, FAB, assistant panel). */
    val surfaceHighest = Color(0xFF1B2440)

    /** Hairline stroke for glass panel edges. */
    val stroke = Color(0xFF232E4E)

    /** Softer stroke for nested dividers. */
    val strokeSoft = Color(0xFF1A2338)

    /** Primary body text on glass. */
    val textPrimary = Color(0xFFEDF1FB)

    /** Secondary text — de-emphasized but ≥ 4.5:1 on surface. */
    val textSecondary = Color(0xFFA3AECD)

    /** Disabled/hint text — decorative only, never body copy. */
    val textTertiary = Color(0xFF6B7899)

    /** Aurora accent start — teal (the "energy" end of the gradient). */
    val accentTeal = Color(0xFF2DD4BF)

    /** Aurora accent end — indigo (the "trust" end of the gradient). */
    val accentIndigo = Color(0xFF818CF8)

    /** Readable solid derived from the gradient midpoint, for single-color uses. */
    val accentSolid = Color(0xFF54B1E8)

    /** On-accent text/icon color — near-black indigo for ≥ 4.5:1 on teal or indigo. */
    val onAccent = Color(0xFF06122B)

    /** Glass fill for hover/pressed states — a 6% white veil. */
    val veil = Color(0xFFFFFFFF).copy(alpha = 0.06f)

    /** Error ramp kept warm against the cool glass. */
    val error = Color(0xFFF87171)
}

/**
 * Kit-expressive structural tokens. Pure data — no Composables — so it is
 * unit-testable (Gate P5) and previewable.
 */
@Immutable
data class AivanceDesignTokens(
    /** Corner radius family. Aurora pushes radii up ~33% for the "soft glass" feel. */
    val radiusSmall: Int,
    val radiusMedium: Int,
    val radiusLarge: Int,
    val radiusExtraLarge: Int,

    /** Hairline stroke width for outlined/glass-panel edges, in dp. */
    val strokeWidthDp: Float,

    /** 0..1 — how much surfaces lift from the background (drives elevation + glass alpha). */
    val surfaceLuminance: Float,

    /** Gradient pair for aurora accents (progress rings, orb, active indicators). */
    val gradientStart: Color,
    val gradientEnd: Color,

    /** Readable solid for single-color accent uses (icon tint, text links). */
    val accentSolid: Color,

    /** Glass veil applied to pressed/hovered surfaces. */
    val surfaceVeil: Color
) {
    /** Maps to the shape system consumed via [AivanceShapes]. */
    val shapes: AivanceShapes
        get() = AivanceShapes(
            small = RoundedCornerShape(radiusSmall.dp),
            medium = RoundedCornerShape(radiusMedium.dp),
            large = RoundedCornerShape(radiusLarge.dp),
            extraLarge = RoundedCornerShape(radiusExtraLarge.dp)
        )

    companion object {
        /** Kit A — flat light: today's geometry, hardened as explicit tokens. */
        val ExecutiveSlate = AivanceDesignTokens(
            radiusSmall = 8,
            radiusMedium = 12,
            radiusLarge = 16,
            radiusExtraLarge = 24,
            strokeWidthDp = 1f,
            surfaceLuminance = 0.08f,
            gradientStart = Color(0xFF4F46E5),
            gradientEnd = Color(0xFF7C3AED),
            accentSolid = Color(0xFF4F46E5),
            surfaceVeil = Color(0xFF000000).copy(alpha = 0.04f)
        )

        /** Kit B — Aurora Glass: dark glass, larger radii, aurora gradient pair. */
        val AuroraGlass = AivanceDesignTokens(
            radiusSmall = 10,
            radiusMedium = 14,
            radiusLarge = 20,
            radiusExtraLarge = 28,
            strokeWidthDp = 1f,
            surfaceLuminance = 0.32f,
            gradientStart = AuroraColors.accentTeal,
            gradientEnd = AuroraColors.accentIndigo,
            accentSolid = AuroraColors.accentSolid,
            surfaceVeil = AuroraColors.veil
        )

        /** Kit C — Paper Studio: warm paper, softer smaller radii, amber→rose warmth. */
        val PaperStudio = AivanceDesignTokens(
            radiusSmall = 6,
            radiusMedium = 10,
            radiusLarge = 14,
            radiusExtraLarge = 20,
            strokeWidthDp = 1.2f,
            surfaceLuminance = 0.1f,
            gradientStart = Color(0xFFF59E0B),
            gradientEnd = Color(0xFFFB7185),
            accentSolid = Color(0xFFB45309),
            surfaceVeil = Color(0xFF78350F).copy(alpha = 0.05f)
        )

        /** Token set for a kit, resolved by [DesignKit]. */
        fun forKit(kit: DesignKit): AivanceDesignTokens = when (kit) {
            DesignKit.EXECUTIVE_SLATE -> ExecutiveSlate
            DesignKit.AURORA_GLASS -> AuroraGlass
            DesignKit.PAPER_STUDIO -> PaperStudio
        }
    }
}

val LocalDesignTokens = staticCompositionLocalOf { AivanceDesignTokens.AuroraGlass }
