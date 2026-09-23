package com.bangersoul.aivance.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.theme.*

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToResume: () -> Unit,
    onNavigateToTracker: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToInterview: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToJobs: () -> Unit = {},
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AivanceWorkspaceScaffold(
        title = uiState.greeting.ifBlank { stringResource(R.string.dash_greeting_fallback) },
        subtitle = uiState.userDesignation.ifBlank { stringResource(R.string.dash_designation_fallback) },
        isLoading = uiState.isLoading,
        error = uiState.error,
        onRetry = { viewModel.onEvent(DashboardUiEvent.Retry) },
        onAssistantClick = onNavigateToAssistant,
        topBarActions = {
            IconButton(onClick = onNavigateToNotifications) {
                Icon(Icons.Rounded.Notifications, contentDescription = "Notifications")
            }
            IconButton(onClick = onNavigateToProfile) {
                Icon(Icons.Rounded.AccountCircle, contentDescription = "Profile")
            }
        }
    ) {
        DashboardContent(
            state = uiState,
            onNavigateToResume = onNavigateToResume,
            onNavigateToJobs = onNavigateToJobs,
            onNavigateToInterview = onNavigateToInterview,
            onNavigateToAssistant = onNavigateToAssistant,
            onNavigateToTracker = onNavigateToTracker,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToAnalytics = onNavigateToAnalytics,
            onActionClick = { route ->
                when (route) {
                    "resume_import" -> onNavigateToResume()
                    "job_search" -> onNavigateToJobs()
                    "prep_studio" -> onNavigateToInterview()
                    "provider_setup" -> onNavigateToAssistant()
                    "ats_scanner" -> onNavigateToResume()
                }
            }
        )
    }
}

@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    onNavigateToResume: () -> Unit,
    onNavigateToJobs: () -> Unit,
    onNavigateToInterview: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToTracker: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAnalytics: () -> Unit = {},
    onActionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Next Best Action (Hero Card)
        (state.nextBestAction as? com.bangersoul.aivance.core.domain.engine.NavigationIntent.Action)?.let { action ->
            item {
                AivanceHeroCard(
                    title = action.label,
                    description = state.aiRecommendation ?: "The next step in your career journey.",
                    actionLabel = action.label,
                    onClick = { onActionClick(action.route) }
                )
            }
        }

        // 2. Career Score hero
        item {
            CareerScoreCard(
                score = state.careerScore,
                onNavigateToAnalytics = onNavigateToAnalytics
            )
        }

        // 3. Quick stats row: ATS | Active Apps | Saved Jobs
        item {
            SectionHeader(title = stringResource(R.string.dash_overview))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = stringResource(R.string.dash_ats_score),
                    value = "${state.atsScore}",
                    icon = Icons.Rounded.FactCheck,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.dash_active_apps),
                    value = "${state.activeApplications}",
                    icon = Icons.Rounded.Send,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.dash_saved_jobs),
                    value = "${state.savedJobs}",
                    icon = Icons.Rounded.Bookmark,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Quick Actions 2x2 grid
        item {
            SectionHeader(title = stringResource(R.string.dash_quick_actions))
            Spacer(Modifier.height(10.dp))
            QuickActionsGrid(
                onResume = onNavigateToResume,
                onJobs = onNavigateToJobs,
                onInterview = onNavigateToInterview,
                onAssistant = onNavigateToAssistant,
                onTracker = onNavigateToTracker,
                onAnalytics = onNavigateToAnalytics
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CareerScoreCard(score: Int, onNavigateToAnalytics: () -> Unit) {
    val animated by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 1000),
        label = "CareerScore"
    )
    val color = scoreColor(score)
    DashboardCard(onClick = onNavigateToAnalytics, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                CircularProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    strokeWidth = 10.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = stringResource(R.string.dash_career_score),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = scoreTitle(score),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = scoreMessage(score),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MetricChip(
                    label = when {
                        score >= 80 -> stringResource(R.string.dash_chip_top_tier)
                        score >= 60 -> stringResource(R.string.dash_chip_strong)
                        score > 0 -> stringResource(R.string.dash_chip_building)
                        else -> stringResource(R.string.dash_chip_not_scored)
                    },
                    containerColor = color.copy(alpha = 0.12f),
                    contentColor = color
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = AivanceTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onResume: () -> Unit,
    onJobs: () -> Unit,
    onInterview: () -> Unit,
    onAssistant: () -> Unit,
    onTracker: () -> Unit,
    onAnalytics: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionTile(
                label = stringResource(R.string.dash_action_resume),
                icon = Icons.Rounded.Description,
                tint = AivanceTheme.colors.accent,
                onClick = onResume,
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                label = stringResource(R.string.dash_action_jobs),
                icon = Icons.Rounded.WorkOutline,
                tint = AivanceTheme.colors.info,
                onClick = onJobs,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionTile(
                label = stringResource(R.string.dash_action_interview),
                icon = Icons.Rounded.RecordVoiceOver,
                tint = AivanceTheme.colors.warning,
                onClick = onInterview,
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                label = stringResource(R.string.dash_action_assistant),
                icon = Icons.Rounded.SmartToy,
                tint = AivanceTheme.colors.success,
                onClick = onAssistant,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionTile(
                label = stringResource(R.string.dash_action_pipeline),
                icon = Icons.Rounded.ViewKanban,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onTracker,
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                label = stringResource(R.string.dash_action_insights),
                icon = Icons.Rounded.BarChart,
                tint = MaterialTheme.colorScheme.secondary,
                onClick = onAnalytics,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = AivanceTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f)) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.padding(7.dp).size(18.dp),
                    tint = tint
                )
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun scoreColor(score: Int): Color = when {
    score >= 80 -> AivanceTheme.colors.success
    score >= 60 -> AivanceTheme.colors.accent
    score > 0 -> AivanceTheme.colors.warning
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun scoreTitle(score: Int): String = when {
    score >= 80 -> stringResource(R.string.dash_score_title_ready)
    score >= 60 -> stringResource(R.string.dash_score_title_strong)
    score > 0 -> stringResource(R.string.dash_score_title_building)
    else -> stringResource(R.string.dash_score_title_unlock)
}

@Composable
private fun scoreMessage(score: Int): String = when {
    score >= 80 -> stringResource(R.string.dash_score_msg_ready)
    score >= 60 -> stringResource(R.string.dash_score_msg_strong)
    score > 0 -> stringResource(R.string.dash_score_msg_building)
    else -> stringResource(R.string.dash_score_msg_unlock)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DashboardContentPreview() {
    AivanceTheme(darkTheme = true) {
        DashboardContent(
            state = DashboardUiState(
                isLoading = false,
                greeting = "Good Morning, Azmath",
                userDesignation = "Software Engineer at TCS",
                careerScore = 78,
                atsScore = 85,
                activeApplications = 6,
                nextInterview = "Fri 10:00",
                savedJobs = 4,
                aiRecommendation = "Tailor your resume for senior Android roles to boost your match rate."
            ),
            onNavigateToResume = {},
            onNavigateToJobs = {},
            onNavigateToInterview = {},
            onNavigateToAssistant = {},
            onNavigateToTracker = {},
            onNavigateToProfile = {},
            onNavigateToAnalytics = {}
        )
    }
}
