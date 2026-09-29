package com.bangersoul.aivance.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class DesignTokensTest {

    // ── WCAG 2.x relative luminance / contrast (deterministic gate for G6) ──

    private fun linear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `each kit exposes its own geometry`() {
        assertEquals(listOf(8, 12, 16, 24), listOf(
            AivanceDesignTokens.ExecutiveSlate.radiusSmall,
            AivanceDesignTokens.ExecutiveSlate.radiusMedium,
            AivanceDesignTokens.ExecutiveSlate.radiusLarge,
            AivanceDesignTokens.ExecutiveSlate.radiusExtraLarge
        ))
        assertEquals(listOf(10, 14, 20, 28), listOf(
            AivanceDesignTokens.AuroraGlass.radiusSmall,
            AivanceDesignTokens.AuroraGlass.radiusMedium,
            AivanceDesignTokens.AuroraGlass.radiusLarge,
            AivanceDesignTokens.AuroraGlass.radiusExtraLarge
        ))
        assertEquals(listOf(6, 10, 14, 20), listOf(
            AivanceDesignTokens.PaperStudio.radiusSmall,
            AivanceDesignTokens.PaperStudio.radiusMedium,
            AivanceDesignTokens.PaperStudio.radiusLarge,
            AivanceDesignTokens.PaperStudio.radiusExtraLarge
        ))
    }

    @Test
    fun `forKit round-trips every kit`() {
        DesignKit.entries.forEach { kit ->
            assertSame(AivanceDesignTokens.forKit(kit), AivanceDesignTokens.forKit(kit))
        }
        assertSame(AivanceDesignTokens.AuroraGlass, AivanceDesignTokens.forKit(DesignKit.AURORA_GLASS))
        assertSame(AivanceDesignTokens.ExecutiveSlate, AivanceDesignTokens.forKit(DesignKit.EXECUTIVE_SLATE))
        assertSame(AivanceDesignTokens.PaperStudio, AivanceDesignTokens.forKit(DesignKit.PAPER_STUDIO))
    }

    @Test
    fun `aurora gradient pairs teal with indigo and ships a readable solid`() {
        val tokens = AivanceDesignTokens.AuroraGlass
        assertEquals(AuroraColors.accentTeal, tokens.gradientStart)
        assertEquals(AuroraColors.accentIndigo, tokens.gradientEnd)
        assertNotEquals(tokens.gradientStart, tokens.gradientEnd)
        // Solid midpoint must differ from both ends so single-tint uses stay legible.
        assertNotEquals(tokens.gradientStart, tokens.accentSolid)
        assertNotEquals(tokens.gradientEnd, tokens.accentSolid)
    }

    @Test
    fun `aurora on-accent color passes WCAG AA on both gradient ends`() {
        assertTrue(
            "onAccent vs teal ≥ 4.5 (got ${contrast(AuroraColors.onAccent, AuroraColors.accentTeal)})",
            contrast(AuroraColors.onAccent, AuroraColors.accentTeal) >= 4.5
        )
        assertTrue(
            "onAccent vs indigo ≥ 4.5 (got ${contrast(AuroraColors.onAccent, AuroraColors.accentIndigo)})",
            contrast(AuroraColors.onAccent, AuroraColors.accentIndigo) >= 4.5
        )
    }

    @Test
    fun `aurora text stops pass WCAG AA on glass surfaces`() {
        assertTrue(
            "textPrimary vs surface ≥ 4.5",
            contrast(AuroraColors.textPrimary, AuroraColors.surface) >= 4.5
        )
        assertTrue(
            "textPrimary vs bg ≥ 4.5",
            contrast(AuroraColors.textPrimary, AuroraColors.bg) >= 4.5
        )
        assertTrue(
            "textSecondary vs surface ≥ 4.5",
            contrast(AuroraColors.textSecondary, AuroraColors.surface) >= 4.5
        )
    }

    @Test
    fun `kit labels stay human readable`() {
        assertEquals("Executive Slate", DesignKit.EXECUTIVE_SLATE.label)
        assertEquals("Aurora Glass", DesignKit.AURORA_GLASS.label)
        assertEquals("Paper Studio", DesignKit.PAPER_STUDIO.label)
    }
}
