@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.bangersoul.aivance.core.designsystem.theme

import android.os.Build
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.bangersoul.aivance.core.designsystem.R

/**
 * Bundled font families for the F1 type system (offline-safe — no download
 * provider). The TTFs are variable fonts (OFL-licensed, redistributed under
 * res/font with their license texts), so every weight maps onto a single file
 * through the `wght` variation axis.
 *
 * Variable settings require API 26+ ([Build.VERSION_CODES.O]); on older API
 * levels we degrade to the system sans family. The project's minSdk is 26, so
 * the fallback is purely defensive.
 */
object AuroraTypefaces {

    private val supportsVariableFonts = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /** Inter — body/UI workhorse (wght 400–800). */
    val inter: FontFamily = if (supportsVariableFonts) {
        FontFamily(
            Font(
                R.font.inter_variable,
                variationSettings = FontVariation.Settings(FontVariation.weight(400))
            ),
            Font(
                R.font.inter_variable,
                FontWeight.Medium,
                variationSettings = FontVariation.Settings(FontVariation.weight(500))
            ),
            Font(
                R.font.inter_variable,
                FontWeight.SemiBold,
                variationSettings = FontVariation.Settings(FontVariation.weight(600))
            ),
            Font(
                R.font.inter_variable,
                FontWeight.Bold,
                variationSettings = FontVariation.Settings(FontVariation.weight(700))
            ),
            Font(
                R.font.inter_variable,
                FontWeight.ExtraBold,
                variationSettings = FontVariation.Settings(FontVariation.weight(800))
            )
        )
    } else {
        FontFamily.SansSerif
    }

    /** Space Grotesk — display/headline voice (wght 300–700). */
    val spaceGrotesk: FontFamily = if (supportsVariableFonts) {
        FontFamily(
            Font(
                R.font.spacegrotesk_variable,
                variationSettings = FontVariation.Settings(FontVariation.weight(400))
            ),
            Font(
                R.font.spacegrotesk_variable,
                FontWeight.Medium,
                variationSettings = FontVariation.Settings(FontVariation.weight(500))
            ),
            Font(
                R.font.spacegrotesk_variable,
                FontWeight.SemiBold,
                variationSettings = FontVariation.Settings(FontVariation.weight(600))
            ),
            Font(
                R.font.spacegrotesk_variable,
                FontWeight.Bold,
                variationSettings = FontVariation.Settings(FontVariation.weight(700))
            )
        )
    } else {
        FontFamily.SansSerif
    }
}
