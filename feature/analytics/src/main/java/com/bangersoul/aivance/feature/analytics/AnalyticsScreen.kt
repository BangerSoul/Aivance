package com.bangersoul.aivance.feature.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    onBack: () -> Unit,
    onNavigateToIntelligence: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AivanceWorkspaceScaffold(
        title = stringResource(R.string.analytics_intelligence_center),
        subtitle = stringResource(R.string.analytics_predictive_insights),
        onBack = onBack,
        backContentDescription = stringResource(R.string.back)
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "AnalyticsTransition"
        ) { state ->
            when (state) {
                is AnalyticsUiState.Loading -> SkeletonDashboard(modifier = Modifier.fillMaxSize())
                is AnalyticsUiState.Error -> AivanceError(
                    message = state.message,
                    onRetry = { viewModel.refresh() }
                )
                is AnalyticsUiState.Success -> CareerHealthSurface(
                    intelligence = state.intelligence,
                    snapshots = state.historicalSnapshots,
                    simulation = state.simulation,
                    viewModel = viewModel,
                    onNavigateToIntelligence = onNavigateToIntelligence
                )
            }
        }
    }
}

/**
 * The whole spoke on one surface (AUDIT 19). Health, Trends and Simulator were
 * three tabs over a single data source, and on a fresh account every one of them
 * rendered `—` / `0%`: the Trends tab drew an empty 160 dp canvas, and the
 * Simulator drew a "Projected Score —" card. The progression chart is now folded
 * in here, and each section is present only when its own numbers exist.
 */
@Composable
private fun CareerHealthSurface(
    intelligence: CareerIntelligence?,
    snapshots: List<AnalyticsSnapshot>,
    simulation: CareerIntelligence?,
    viewModel: AnalyticsViewModel,
    onNavigateToIntelligence: () -> Unit
) {
    if (intelligence == null) {
        LoadingPanel("Analyzing career data...")
        return
    }

    val careerScore = intelligence.careerScore
    // `careerScore` is null exactly when nothing has been measured (R3-1: never a
    // fabricated default), and `health` is derived from the same evidence.
    val hasMeasurement = careerScore != null || intelligence.health.isNotEmpty()
    if (!hasMeasurement) {
        AivanceEmptyState(
            title = stringResource(R.string.analytics_not_measured),
            description = stringResource(R.string.analytics_not_measured_detail),
            icon = Icons.Rounded.Assessment,
            primaryActionText = stringResource(R.string.analytics_not_measured_action),
            onPrimaryAction = onNavigateToIntelligence
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (careerScore != null) {
            item {
                AivanceHeroCard(
                    title = stringResource(R.string.analytics_hireability_score, careerScore),
                    description = intelligence.predictions.successExplanation,
                    actionLabel = stringResource(R.string.analytics_boost_score),
                    onClick = onNavigateToIntelligence
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = stringResource(R.string.analytics_interview_chance),
                        value = "${intelligence.predictions.interviewProbability}%",
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.AutoAwesome
                    )
                    MetricCard(
                        label = stringResource(R.string.analytics_offer_chance),
                        value = "${intelligence.predictions.offerProbability}%",
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Celebration
                    )
                }
            }
        }

        if (intelligence.health.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.analytics_health_dimensions))
            }

            items(intelligence.health) { dimension ->
                AivanceWorkspaceCard {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ScoreGauge(score = dimension.score, size = 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(dimension.category, fontWeight = FontWeight.Bold)
                            Text(dimension.recommendation, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(
                            imageVector = if (dimension.trend == "UP") Icons.Rounded.TrendingUp else Icons.Rounded.TrendingFlat,
                            contentDescription = null,
                            tint = if (dimension.trend == "UP") AivanceTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Folded in from the deleted Trends tab. A "progression" needs at least two
        // observations, so with a single snapshot this section is absent rather than
        // an empty chart card.
        if (snapshots.size > 1) {
            item {
                ProgressionSection(snapshots)
            }
        }

        // Folded in from the deleted Simulator tab, and only reachable once a score
        // exists — three sliders-worth of projection off an unscored profile is the
        // "Not enough data yet" surface the audit removed.
        if (careerScore != null) {
            item {
                SimulatorSection(
                    intelligence = intelligence,
                    simulation = simulation,
                    viewModel = viewModel
                )
            }
        }
    }
}

/**
 * The one chart worth keeping out of the old Trends tab: how the score moved over
 * time. The "Dimension Trends" bar chart is gone — it plotted the same dimensions
 * the Health list above already shows, from the same snapshot.
 */
@Composable
private fun ProgressionSection(snapshots: List<AnalyticsSnapshot>) {
    val ordered = snapshots.sortedBy { it.timestamp }

    Column {
        SectionHeader(title = stringResource(R.string.analytics_score_progression))
        Spacer(Modifier.height(8.dp))
        AivanceWorkspaceCard {
            Column(Modifier.padding(16.dp)) {
                LineChart(
                    values = ordered.map { it.careerScore.toFloat() },
                    contentDescription = stringResource(R.string.analytics_score_progression_chart),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SimulatorSection(
    intelligence: CareerIntelligence,
    simulation: CareerIntelligence?,
    viewModel: AnalyticsViewModel
) {
    var atsValue by remember { mutableFloatStateOf(intelligence.dimensionScores["ATS_READINESS"]?.toFloat() ?: 70f) }
    var prepValue by remember { mutableFloatStateOf(intelligence.dimensionScores["INTERVIEW_READINESS"]?.toFloat() ?: 60f) }

    val projected = simulation ?: intelligence
    // The current score is known to be non-null here, so a simulation that fails to
    // score never reintroduces the `—` placeholder.
    val projectedScore = projected.careerScore ?: intelligence.careerScore

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text(stringResource(R.string.analytics_outcome_simulator), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.analytics_outcome_simulator_detail), style = MaterialTheme.typography.bodySmall)
        }

        AivanceWorkspaceCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(stringResource(R.string.analytics_target_ats_score, atsValue.toInt()), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = atsValue,
                        onValueChange = { atsValue = it; viewModel.runSimulation(it.toInt(), prepValue.toInt()) },
                        valueRange = 0f..100f
                    )
                }
                Column {
                    Text(stringResource(R.string.analytics_target_prep, prepValue.toInt()), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = prepValue,
                        onValueChange = { prepValue = it; viewModel.runSimulation(atsValue.toInt(), it.toInt()) },
                        valueRange = 0f..100f
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AivanceTheme.colors.accent.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, AivanceTheme.colors.accent.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.analytics_simulated_outcome), fontWeight = FontWeight.Bold, color = AivanceTheme.colors.accent)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = stringResource(R.string.analytics_projected_score),
                        value = projectedScore?.toString() ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = stringResource(R.string.analytics_int_probability),
                        value = "${projected.predictions.interviewProbability}%",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(projected.predictions.successExplanation, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LoadingPanel(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AivanceWorkspaceCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AivanceTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        content()
    }
}
