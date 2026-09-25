package com.bangersoul.aivance.feature.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Health", "Trends", "Simulator")

    AivanceWorkspaceScaffold(
        title = "Intelligence Center",
        subtitle = "Predictive Career Insights",
        onBack = onBack,
        showAssistantAction = true
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

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
                    is AnalyticsUiState.Success -> {
                        when (selectedTab) {
                            0 -> CareerHealthTab(state.intelligence, state.recommendations, viewModel, onNavigateToIntelligence)
                            1 -> CareerTrendsTab(state.historicalSnapshots)
                            2 -> CareerSimulatorTab(state.intelligence, state.simulation, viewModel)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun CareerHealthTab(
    intelligence: CareerIntelligence?,
    recommendations: List<CareerRecommendation>,
    viewModel: AnalyticsViewModel,
    onNavigateToIntelligence: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (intelligence == null) {
            item { LoadingPanel("Analyzing career data...") }
            return@LazyColumn
        }

        // Hero Score & Predictions
        item {
            AivanceHeroCard(
                title = "Hireability Score: ${intelligence.careerScore ?: "not scored yet"}",
                description = intelligence.predictions.successExplanation,
                actionLabel = "Boost Score",
                onClick = onNavigateToIntelligence
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "Interview Chance",
                    value = "${intelligence.predictions.interviewProbability}%",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.AutoAwesome
                )
                MetricCard(
                    label = "Offer Chance",
                    value = "${intelligence.predictions.offerProbability}%",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Celebration
                )
            }
        }

        item {
            SectionHeader(title = "Health Dimensions")
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
}

@Composable
private fun CareerTrendsTab(snapshots: List<AnalyticsSnapshot>) {
    val sorted = snapshots.sortedBy { it.timestamp }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SectionHeader(title = "Score Progression")
            Spacer(Modifier.height(8.dp))
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp)) {
                    LineChart(
                        values = sorted.map { it.careerScore.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                }
            }
        }

        item {
            SectionHeader(title = "Dimension Trends")
            Spacer(Modifier.height(8.dp))
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp)) {
                    val latest = sorted.lastOrNull()
                    latest?.dimensionScores?.let { dims ->
                        BarChart(
                            data = dims.map { it.key to it.value / 100f },
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CareerSimulatorTab(
    intelligence: CareerIntelligence?,
    simulation: CareerIntelligence?,
    viewModel: AnalyticsViewModel
) {
    var atsValue by remember { mutableFloatStateOf(intelligence?.dimensionScores?.get("ATS_READINESS")?.toFloat() ?: 70f) }
    var prepValue by remember { mutableFloatStateOf(intelligence?.dimensionScores?.get("INTERVIEW_READINESS")?.toFloat() ?: 60f) }

    val displayIntel = simulation ?: intelligence

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("Outcome Simulator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Adjust values to see projected impacts on your career.", style = MaterialTheme.typography.bodySmall)
        }

        item {
            AivanceWorkspaceCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Target ATS Score: ${atsValue.toInt()}%", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = atsValue,
                            onValueChange = { atsValue = it; viewModel.runSimulation(it.toInt(), prepValue.toInt()) },
                            valueRange = 0f..100f
                        )
                    }
                    Column {
                        Text("Target Interview Prep: ${prepValue.toInt()}%", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = prepValue,
                            onValueChange = { prepValue = it; viewModel.runSimulation(atsValue.toInt(), it.toInt()) },
                            valueRange = 0f..100f
                        )
                    }
                }
            }
        }

        if (displayIntel != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AivanceTheme.colors.accent.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, AivanceTheme.colors.accent.copy(alpha = 0.3f))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Simulated Outcome", fontWeight = FontWeight.Bold, color = AivanceTheme.colors.accent)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                label = "Projected Score",
                                value = displayIntel.careerScore?.toString() ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                label = "Int. Probability",
                                value = "${displayIntel.predictions.interviewProbability}%",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(displayIntel.predictions.successExplanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
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

@Composable
/**
 * Builds a simple 7xN heat map from snapshot career scores,
 * one column per snapshot week.
 */
private fun buildHeatMapData(sortedSnapshots: List<AnalyticsSnapshot>): List<List<Float>> {
    if (sortedSnapshots.isEmpty()) return emptyList()
    val rows = 7
    return (0 until rows).map { row ->
        sortedSnapshots.mapIndexed { index, snap ->
            val base = snap.careerScore / 100f
            // Offset each row slightly so the grid reads as depth/activity.
            val rowFactor = ((index + row) % 5) * 0.03f
            (base - rowFactor).coerceIn(0f, 1f)
        }
    }
}

@Composable
private fun RecommendationCard(rec: CareerRecommendation, onDismiss: () -> Unit) {
    val tone = if (rec.priority == "HIGH") BannerTone.WARNING else BannerTone.INFO
    DashboardCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = when (rec.priority) {
                    "HIGH" -> AivanceTheme.colors.warning
                    else -> AivanceTheme.colors.info
                },
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rec.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(rec.description, style = MaterialTheme.typography.bodyMedium)
            }
            StatusChip(text = rec.priority, tone = tone)
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Top)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.analytics_dismiss), modifier = Modifier.size(18.dp))
            }
        }
    }
}
