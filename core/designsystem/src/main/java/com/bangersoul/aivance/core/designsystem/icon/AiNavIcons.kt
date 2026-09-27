package com.bangersoul.aivance.core.designsystem.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.designsystem.theme.LocalDesignTokens

/**
 * How a destination renders its navigation icon under I3 (duotone selected
 * state): an outlined stroke when the workspace is inactive, a filled form
 * when active.
 */
enum class IconVariant { OUTLINED, FILLED }

/**
 * Icon intent for a destination — semantic, not literal. The nav shell
 * resolves intents to concrete vectors so the icon language can evolve
 * (unify / custom line set / duotone) without touching navigation code.
 *
 * Workspaces ship paired [outlined]/[filled] variants (I3 duotone); non-tab
 * destinations carry a single [outlined] vector used in both states. Surfaces
 * flagged [isAccent] render with the kit's aurora solid accent by default.
 */
data class DestinationIconIntent(
    val outlined: ImageVector? = null,
    val filled: ImageVector? = null,
    val isAccent: Boolean = false
) {
    /** Resolves the variant, falling back to the outlined vector when unpaired. */
    fun forVariant(variant: IconVariant): ImageVector? = when (variant) {
        IconVariant.FILLED -> filled ?: outlined
        IconVariant.OUTLINED -> outlined
    }
}

/**
 * Custom icon set (I2 DNA): 1.7dp-stroke geometry drawn as inline
 * [ImageVector]s — no icon-font dependency — with filled twins for the I3
 * duotone selected state. Only the four workspaces + the AI orb live here;
 * secondary destinations keep Material icons via their intents.
 */
object AiNavIcons {

    private const val SW = 1.7f
    private const val K = 0.5523f // circle-to-cubic kappa

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )

    private fun PathBuilder.roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        val k = K * r
        moveTo(x + r, y)
        lineTo(x + w - r, y)
        curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r)
        lineTo(x + w, y + h - r)
        curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h)
        lineTo(x + r, y + h)
        curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r)
        lineTo(x, y + r)
        curveTo(x, y + r - k, x + r - k, y, x + r, y)
        close()
    }

    private fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit) = path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = SW,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        fill = null,
        pathBuilder = block
    )

    private fun ImageVector.Builder.fillPath(block: PathBuilder.() -> Unit) = path(
        fill = SolidColor(Color.Black),
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero,
        pathBuilder = block
    )

    private fun ImageVector.Builder.dualPath(outlined: Boolean, block: PathBuilder.() -> Unit) =
        if (outlined) {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = SW,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                fill = null,
                pathBuilder = block
            )
        } else {
            path(fill = SolidColor(Color.Black), pathBuilder = block)
        }

    // ── Dashboard: 2×2 rounded grid ────────────────────────────────────────

    private fun dashboardCells(pb: PathBuilder) {
        pb.roundedRect(3f, 3f, 7.6f, 7.6f, 2.2f)
        pb.roundedRect(13.4f, 3f, 7.6f, 7.6f, 2.2f)
        pb.roundedRect(3f, 13.4f, 7.6f, 7.6f, 2.2f)
        pb.roundedRect(13.4f, 13.4f, 7.6f, 7.6f, 2.2f)
    }

    private fun dashboard(outlined: Boolean) = builder("AiDashboard").apply {
        dualPath(outlined) { dashboardCells(this) }
    }.build()

    // ── Pipeline: kanban columns ───────────────────────────────────────────

    private fun pipelineColumns(pb: PathBuilder) {
        pb.roundedRect(4.6f, 4.6f, 4f, 14.8f, 1.7f)
        pb.roundedRect(10f, 4.6f, 4f, 14.8f, 1.7f)
        pb.roundedRect(15.4f, 4.6f, 4f, 9.4f, 1.7f)
    }

    private fun pipeline(outlined: Boolean) = builder("AiPipeline").apply {
        dualPath(outlined) { pipelineColumns(this) }
    }.build()

    // ── Discovery: work briefcase ──────────────────────────────────────────

    private fun briefcase(v: ImageVector.Builder, outlined: Boolean) {
        // Handle stays a stroke in both variants.
        v.path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = SW,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            fill = null
    ) { roundedRect(9.3f, 3.6f, 5.4f, 4f, 1.3f) }
    v.dualPath(outlined) { roundedRect(3.1f, 7.6f, 17.8f, 11.5f, 2.2f) }
        if (outlined) {
            v.strokePath {
                moveTo(3.1f, 12.6f)
                lineTo(20.9f, 12.6f)
            }
        }
    }

    private fun discovery(outlined: Boolean) = builder("AiDiscovery").apply {
        briefcase(this, outlined)
    }.build()

    // ── Studio: mortarboard ────────────────────────────────────────────────

    private fun mortarboard(v: ImageVector.Builder, outlined: Boolean) {
        v.dualPath(outlined) {
            moveTo(12f, 4.2f)
            lineTo(21.4f, 8.9f)
            lineTo(12f, 13.6f)
            lineTo(2.6f, 8.9f)
            close()
        }
        v.strokePath {
            moveTo(6.2f, 10.9f)
            lineTo(6.2f, 15.2f)
            curveTo(6.2f, 16.9f, 8.8f, 18.5f, 12f, 18.5f)
            curveTo(15.2f, 18.5f, 17.8f, 16.9f, 17.8f, 15.2f)
            lineTo(17.8f, 10.9f)
        }
        v.strokePath {
            moveTo(21.4f, 8.9f)
            lineTo(21.4f, 14.8f)
        }
    }

    private fun studio(outlined: Boolean) = builder("AiStudio").apply {
        mortarboard(this, outlined)
    }.build()

    // ── Public icons ───────────────────────────────────────────────────────

    /** Paired workspace icons (I3 duotone). */
    val DashboardOutlined = dashboard(outlined = true)
    val DashboardFilled = dashboard(outlined = false)
    val PipelineOutlined = pipeline(outlined = true)
    val PipelineFilled = pipeline(outlined = false)
    val DiscoveryOutlined = discovery(outlined = true)
    val DiscoveryFilled = discovery(outlined = false)
    val StudioOutlined = studio(outlined = true)
    val StudioFilled = studio(outlined = false)

    /**
     * The AI orb — Assistant's identity mark: an open ring, a solid core and
     * two orbit nodes, evoking a satellite intelligence. Drawn as one vector;
     * the aurora gradient comes from the composable wrapper.
     */
    val Orb: ImageVector = builder("AiOrb").apply {
        // Ring (r = 9)
        strokePath {
            moveTo(3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            close()
        }
        // Core (r = 4.2)
        fillPath {
            moveTo(12f, 7.8f)
            curveTo(14.32f, 7.8f, 16.2f, 9.68f, 16.2f, 12f)
            curveTo(16.2f, 14.32f, 14.32f, 16.2f, 12f, 16.2f)
            curveTo(9.68f, 16.2f, 7.8f, 14.32f, 7.8f, 12f)
            curveTo(7.8f, 9.68f, 9.68f, 7.8f, 12f, 7.8f)
            close()
        }
        // Orbit nodes
        fillPath {
            moveTo(20.35f, 12f)
            curveTo(20.35f, 11.42f, 20.82f, 10.95f, 21.4f, 10.95f)
            curveTo(21.98f, 10.95f, 22.45f, 11.42f, 22.45f, 12f)
            curveTo(22.45f, 12.58f, 21.98f, 13.05f, 21.4f, 13.05f)
            curveTo(20.82f, 13.05f, 20.35f, 12.58f, 20.35f, 12f)
            close()
        }
        fillPath {
            moveTo(1.55f, 12f)
            curveTo(1.55f, 11.42f, 2.02f, 10.95f, 2.6f, 10.95f)
            curveTo(3.18f, 10.95f, 3.65f, 11.42f, 3.65f, 12f)
            curveTo(3.65f, 12.58f, 3.18f, 13.05f, 2.6f, 13.05f)
            curveTo(2.02f, 13.05f, 1.55f, 12.58f, 1.55f, 12f)
            close()
        }
    }.build()
}

/**
 * The AI-orb action rendered with the kit's aurora gradient — used by the nav
 * shell's Assistant slot and anywhere else the orb marks "talk to the AI".
 */
@Composable
fun AiOrbIcon(size: Dp, contentDescription: String?, modifier: Modifier = Modifier) {
    val tokens = designTokens
    Box(modifier) {
        // Gradient bloom behind the solid glyph, driven by the kit's gradient pair.
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
            drawCircle(
                brush = Brush.linearGradient(listOf(tokens.gradientStart, tokens.gradientEnd))
            )
        }
        Icon(
            imageVector = AiNavIcons.Orb,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size)
        )
    }
}

/** Current kit tokens — thin accessor so icon code reads like the rest of the DS. */
val designTokens: com.bangersoul.aivance.core.designsystem.theme.AivanceDesignTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalDesignTokens.current
