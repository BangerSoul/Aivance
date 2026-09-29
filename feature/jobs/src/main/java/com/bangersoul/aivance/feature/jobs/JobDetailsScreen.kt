package com.bangersoul.aivance.feature.jobs

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.common.model.AssistantJobContext
import com.bangersoul.aivance.core.common.model.Company
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.Recruiter
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.shell.LocalAppShellState
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailsScreen(
    viewModel: JobDetailsViewModel,
    jobId: String,
    onNavigateBack: () -> Unit,
    onNavigateToApplyBrowser: (String) -> Unit = {},
    onNavigateToRecruiters: (String) -> Unit = {},
    onNavigateToCoverLetter: (Long) -> Unit = {},
    onNavigateToPipeline: () -> Unit = {},
    onNavigateToAts: (String) -> Unit = {},
    onNavigateToCompany: (String) -> Unit = {},
    onNavigateToPrepStudio: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val shellState = LocalAppShellState.current
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(jobId) {
        viewModel.load(jobId)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is JobDetailsUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is JobDetailsUiEffect.OpenExternalUrl -> openExternalUrl(context, effect.url)
                is JobDetailsUiEffect.NavigateToApplyBrowser -> onNavigateToApplyBrowser(effect.jobId)
                is JobDetailsUiEffect.NavigateToRecruiters -> onNavigateToRecruiters(effect.jobId)
                is JobDetailsUiEffect.NavigateToCoverLetter -> onNavigateToCoverLetter(effect.jobId)
                is JobDetailsUiEffect.NavigateToAts -> onNavigateToAts(effect.jobDescription)
                JobDetailsUiEffect.NavigateToPipeline -> onNavigateToPipeline()
            }
        }
    }

    AivanceWorkspaceScaffold(
        title = stringResource(R.string.job_details_title),
        onBack = onNavigateBack,
        showAssistantAction = true,
        onAssistantClick = {
            // Surface the assistant with this job as context so replies stay
            // on-target for the role being viewed.
            (uiState as? JobDetailsUiState.Success)?.let { state ->
                shellState.setAssistantJobContext(
                    AssistantJobContext(
                        jobId = state.job.id,
                        title = state.job.title,
                        company = state.job.company,
                        description = state.job.description
                    )
                )
            }
            shellState.toggleAssistant(true)
        },
        topBarActions = {
            val isBookmarked = (uiState as? JobDetailsUiState.Success)?.isBookmarked ?: false
            IconButton(onClick = { viewModel.onEvent(JobDetailsUiEvent.ToggleBookmark) }) {
                Icon(
                    if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = stringResource(R.string.bookmark),
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        isLoading = uiState is JobDetailsUiState.Loading,
        error = (uiState as? JobDetailsUiState.Error)?.message,
        onRetry = { viewModel.onEvent(JobDetailsUiEvent.Reload) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) {
        Column(Modifier.fillMaxSize()) {
            (uiState as? JobDetailsUiState.Success)?.let { state ->
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text(
                            "Overview",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text(
                            "Readiness",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                        Text(
                            "Intelligence",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "TabContentTransition"
                    ) { tab ->
                        when (tab) {
                            0 -> JobOverviewContent(
                                job = state.job,
                                onApplyClick = { viewModel.onEvent(JobDetailsUiEvent.ApplyInApp) },
                                onApplyAndTrack = { viewModel.onEvent(JobDetailsUiEvent.ApplyAndTrack) },
                                onCompanyClick = { onNavigateToCompany(state.job.company) }
                            )
                            1 -> JobReadinessContent(
                                score = state.readinessScore,
                                onOpenAts = { viewModel.onEvent(JobDetailsUiEvent.OpenAts) },
                                onGenerateCoverLetter = { viewModel.onEvent(JobDetailsUiEvent.GenerateCoverLetter) },
                                onNavigateToPrepStudio = onNavigateToPrepStudio
                            )
                            2 -> JobIntelligenceContent(
                                company = state.company,
                                recruiters = state.recruiters,
                                onFindRecruiters = { viewModel.onEvent(JobDetailsUiEvent.FindRecruiters) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

@Composable
private fun JobOverviewContent(
    job: JobListing,
    onApplyClick: () -> Unit,
    onApplyAndTrack: () -> Unit,
    onCompanyClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(job.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            // Tapping the company name opens the company's detail screen.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onCompanyClick)
            ) {
                Icon(Icons.Rounded.Business, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(job.company, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocationOn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(job.location, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AivancePrimaryButton(
                text = "Apply Now",
                onClick = onApplyClick,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Public
            )
            AivanceSecondaryButton(
                text = "Track",
                onClick = onApplyAndTrack,
                modifier = Modifier.weight(0.6f),
                icon = Icons.Rounded.PlaylistAdd
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.job_description), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(job.description, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun JobReadinessContent(
    score: Int?,
    onOpenAts: () -> Unit,
    onGenerateCoverLetter: () -> Unit,
    onNavigateToPrepStudio: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AivanceWorkspaceCard {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (score != null) {
                    ScoreGauge(score = score, size = 80.dp)
                    Column {
                        Text("Match Readiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("How prepared you are for this specific role.", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Column {
                        Text("Match Readiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Add your target role and skills to your profile to see how well you match this role.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        SectionHeader(title = "Required Steps")

        ReadinessCard(
            title = "ATS Optimization",
            description = if (score != null) {
                "Your current resume match is ${score}%. Fix missing keywords to pass filters."
            } else {
                "Run an ATS scan to see how well your resume matches this role."
            },
            actionLabel = "Run ATS Scan",
            icon = Icons.Rounded.Search,
            onClick = onOpenAts
        )

        ReadinessCard(
            title = "Cover Letter",
            description = "A tailored cover letter increases your interview chance by 40%.",
            actionLabel = "Generate with AI",
            icon = Icons.Rounded.HistoryEdu,
            onClick = onGenerateCoverLetter
        )

        ReadinessCard(
            title = "Interview Prep",
            description = "We found 12 specific interview questions for this role.",
            actionLabel = "Start Prep",
            icon = Icons.Rounded.RecordVoiceOver,
            onClick = onNavigateToPrepStudio
        )
    }
}

@Composable
private fun ReadinessCard(
    title: String,
    description: String,
    actionLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    AivanceWorkspaceCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                    Icon(icon, null, Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(title, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            AivanceTertiaryButton(text = actionLabel, onClick = onClick, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun JobIntelligenceContent(
    company: Company?,
    recruiters: List<Recruiter>,
    onFindRecruiters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (company != null) {
            SectionHeader(title = "Company Insights")
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp)) {
                    Text(company.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(company.industry, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(12.dp))
                    Text(company.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionHeader(title = "Hiring Team")
        if (recruiters.isEmpty()) {
            AivanceEmptyState(
                title = "No recruiters found",
                description = "We can try to find hiring managers and contacts for this role.",
                icon = Icons.Rounded.PersonSearch,
                primaryActionText = "Search Recruiters",
                onPrimaryAction = onFindRecruiters
            )
        } else {
            recruiters.forEach { recruiter ->
                RecruiterCard(recruiter)
            }
        }
    }
}

@Composable
private fun RecruiterCard(recruiter: Recruiter) {
    AivanceWorkspaceCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)) {
                Icon(Icons.Rounded.AccountCircle, null, Modifier.padding(10.dp).size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(recruiter.name, fontWeight = FontWeight.Bold)
                Text(recruiter.title ?: "Recruiter", style = MaterialTheme.typography.bodySmall)
            }
            if (recruiter.contacts.any { it.isVerified }) {
                Icon(Icons.Rounded.Verified, "Verified Contact", tint = AivanceTheme.colors.info, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AivanceWorkspaceCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AivanceTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        content()
    }
}
