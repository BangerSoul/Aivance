package com.bangersoul.aivance.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToProviderSetup: () -> Unit = {},
    onDiscoverBySkill: (String) -> Unit = {},
    onLearnSkill: (String) -> Unit = {}
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
            // Tapping a skill chip both records durable engagement (so the dashboard
            // shows momentum) and navigates. The ViewModel owns the log/persist; the
            // nav callback owns the destination.
            onDiscoverBySkill = { skill ->
                viewModel.onEvent(DashboardUiEvent.ExploreSkillJobs(skill))
                onDiscoverBySkill(skill)
            },
            onLearnSkill = { skill ->
                viewModel.onEvent(DashboardUiEvent.LearnSkill(skill))
                onLearnSkill(skill)
            },
            onActionClick = { route ->
                when (route) {
                    "resume_import" -> onNavigateToResume()
                    "job_search" -> onNavigateToJobs()
                    "prep_studio" -> onNavigateToInterview()
                    // The lifecycle engine's onboarding intent targets provider
                    // setup — the assistant is not a remediation surface.
                    "provider_setup" -> onNavigateToProviderSetup()
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
    onDiscoverBySkill: (String) -> Unit = {},
    onLearnSkill: (String) -> Unit = {},
    onActionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Next Best Action (Hero Card) — no invented copy: the description
        // shows only what the engine actually produced.
        (state.nextBestAction as? com.bangersoul.aivance.core.domain.engine.NavigationIntent.Action)?.let { action ->
            item {
                AivanceHeroCard(
                    title = action.label,
                    description = state.aiRecommendation.orEmpty(),
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
                    // Unmeasured renders as an em dash, never as a confident "0" (R3-1).
                    value = state.atsScore?.toString() ?: stringResource(R.string.dash_no_data),
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
                onTracker = onNavigateToTracker,
                onAnalytics = onNavigateToAnalytics
            )
        }

        // 5. Career Knowledge Graph insights (skill gaps + application context)
        if (state.graphInsights.available) {
            item {
                SectionHeader(title = stringResource(R.string.dash_graph_insights))
                Spacer(Modifier.height(10.dp))
                SkillGapCard(
                    insights = state.graphInsights,
                    onExplore = onNavigateToJobs,
                    onDiscoverBySkill = onDiscoverBySkill,
                    onLearnSkill = onLearnSkill
                )
            }
            if (state.graphInsights.applicationContexts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    ApplicationContextCard(
                        contexts = state.graphInsights.applicationContexts,
                        onOpenPipeline = onNavigateToTracker
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CareerScoreCard(score: Int?, onNavigateToAnalytics: () -> Unit) {
    val animated by animateFloatAsState(
        targetValue = (score ?: 0).coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 1000),
        label = "CareerScore"
    )
    val color = scoreColor(score)
    // The chip's `else` branch is "Not scored yet", which is exactly what an unmeasured score
    // means — the difference is that the card no longer prints a number for it.
    val chipScore = score ?: 0
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
                        // "Not scored yet" is rendered as an em dash — the score genuinely does
                        // not exist until at least one dimension has been measured.
                        text = score?.toString() ?: stringResource(R.string.dash_no_data),
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
                        chipScore >= 80 -> stringResource(R.string.dash_chip_top_tier)
                        chipScore >= 60 -> stringResource(R.string.dash_chip_strong)
                        chipScore > 0 -> stringResource(R.string.dash_chip_building)
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
private fun SkillGapCard(
    insights: CareerGraphInsightsUi,
    onExplore: () -> Unit,
    onDiscoverBySkill: (String) -> Unit = {},
    onLearnSkill: (String) -> Unit = {}
) {
    val color = scoreColor(insights.skillMatchPercent)
    DashboardCard(onClick = onExplore, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Rounded.Hub,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.dash_skill_match),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.dash_skill_match_subtitle,
                            insights.demonstratedSkillCount,
                            insights.targetSkillCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = insights.skillMatchPercent?.let { "$it%" } ?: stringResource(R.string.dash_no_data),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            if (insights.targetSkillCount == 0) {
                // Nothing was demanded of the candidate, so there is no match to report — and
                // no cause to celebrate. Saying "you demonstrate every skill your target jobs
                // ask for" when there are no target jobs is what made a 100% appear at zero
                // data (R3-2).
                Text(
                    text = stringResource(R.string.dash_skill_match_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (insights.missingSkills.isEmpty()) {
                Text(
                    text = stringResource(R.string.dash_no_skill_gaps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.dash_missing_skills),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.dash_missing_skills_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Momentum toward closing the surfaced gaps — driven by recorded
                // engagement, distinct from graph-derived skill possession.
                if (insights.gapsActedOn > 0) {
                    GapProgressBar(
                        gapsActedOn = insights.gapsActedOn,
                        totalGaps = insights.missingSkills.size,
                        progress = insights.gapProgress
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    insights.missingSkills.forEach { skill ->
                        MissingSkillRow(
                            skill = skill,
                            onFilterJobs = { onDiscoverBySkill(skill.skill) },
                            onLearn = { onLearnSkill(skill.skill) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingSkillRow(
    skill: MissingSkillUi,
    onFilterJobs: () -> Unit,
    onLearn: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Tapping the row filters Job Discovery by this skill.
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onFilterJobs)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val acted = skill.engagement != SkillEngagementUi.NONE
        Icon(
            if (acted) Icons.Rounded.CheckCircle else Icons.Rounded.TrendingUp,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (acted) AivanceTheme.colors.success else AivanceTheme.colors.warning
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skill.skill,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            skill.engagement.labelRes()?.let { labelRes ->
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = AivanceTheme.colors.success
                )
            }
        }
        MetricChip(
            label = pluralJobs(skill.demandedByJobs),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Secondary action: targeted learning recommendations for the skill.
        IconButton(
            onClick = onLearn,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Rounded.School,
                contentDescription = stringResource(R.string.dash_learn_skill_cd, skill.skill),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Sub-label describing how far the user has engaged with a gap, or null if untouched. */
@Composable
private fun SkillEngagementUi.labelRes(): Int? = when (this) {
    SkillEngagementUi.NONE -> null
    SkillEngagementUi.EXPLORED_JOBS -> R.string.dash_skill_explored
    SkillEngagementUi.STARTED_LEARNING -> R.string.dash_skill_learning
}

/** A slim progress bar showing momentum toward closing the surfaced skill gaps. */
@Composable
private fun GapProgressBar(
    gapsActedOn: Int,
    totalGaps: Int,
    progress: Float
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "GapProgress"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.dash_gap_progress, gapsActedOn, totalGaps),
            style = MaterialTheme.typography.labelMedium,
            color = AivanceTheme.colors.success,
            fontWeight = FontWeight.SemiBold
        )
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = AivanceTheme.colors.success,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun pluralJobs(count: Int): String =
    if (count == 1) stringResource(R.string.dash_demanded_by_one_job)
    else stringResource(R.string.dash_demanded_by_jobs, count)

@Composable
private fun ApplicationContextCard(
    contexts: List<ApplicationContextUi>,
    onOpenPipeline: () -> Unit
) {
    DashboardCard(onClick = onOpenPipeline, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Rounded.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(R.string.dash_active_context),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            contexts.forEach { ctx ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = ctx.jobTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (ctx.interviewCount > 0) {
                            stringResource(R.string.dash_context_with_interviews, ctx.company, ctx.interviewCount)
                        } else {
                            ctx.company
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                label = stringResource(R.string.dash_action_pipeline),
                icon = Icons.Rounded.ViewKanban,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onTracker,
                modifier = Modifier.weight(1f)
            )
        }
        QuickActionTile(
            label = stringResource(R.string.dash_action_insights),
            icon = Icons.Rounded.BarChart,
            tint = MaterialTheme.colorScheme.secondary,
            onClick = onAnalytics,
            modifier = Modifier.fillMaxWidth()
        )
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
private fun scoreColor(score: Int?): Color {
    // An unmeasured score is neutral rather than "bad".
    if (score == null) return MaterialTheme.colorScheme.surfaceVariant
    return when {
        score >= 80 -> AivanceTheme.colors.success
        score >= 60 -> AivanceTheme.colors.accent
        score > 0 -> AivanceTheme.colors.warning
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun scoreTitle(score: Int?): String {
    if (score == null) return stringResource(R.string.dash_score_title_unlock)
    return when {
        score >= 80 -> stringResource(R.string.dash_score_title_ready)
        score >= 60 -> stringResource(R.string.dash_score_title_strong)
        score > 0 -> stringResource(R.string.dash_score_title_building)
        else -> stringResource(R.string.dash_score_title_unlock)
    }
}

@Composable
private fun scoreMessage(score: Int?): String {
    if (score == null) return stringResource(R.string.dash_score_msg_unlock)
    return when {
        score >= 80 -> stringResource(R.string.dash_score_msg_ready)
        score >= 60 -> stringResource(R.string.dash_score_msg_strong)
        score > 0 -> stringResource(R.string.dash_score_msg_building)
        else -> stringResource(R.string.dash_score_msg_unlock)
    }
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
