package com.bangersoul.aivance.feature.interview.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import com.bangersoul.aivance.feature.interview.R
import com.bangersoul.aivance.feature.interview.InterviewUiEvent
import com.bangersoul.aivance.feature.interview.InterviewUiState
import com.bangersoul.aivance.feature.interview.InterviewViewModel
import com.bangersoul.aivance.feature.interview.QuestionBankUiEvent
import com.bangersoul.aivance.feature.interview.QuestionBankUiState
import com.bangersoul.aivance.feature.interview.QuestionBankViewModel
import com.bangersoul.aivance.feature.interview.LearningHubUiState
import com.bangersoul.aivance.feature.interview.LearningHubViewModel
import com.bangersoul.aivance.feature.interview.LearningHubUiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepStudioScreen(
    interviewViewModel: InterviewViewModel,
    questionBankViewModel: QuestionBankViewModel = hiltViewModel(),
    learningViewModel: LearningHubViewModel = hiltViewModel(),
    initialLearnSkill: String? = null,
    onBack: (() -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        interviewViewModel.effects.collect {
            snackbarHostState.showSnackbar("Action completed")
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    // Two tabs replace the five-way wall (AUDIT 10–14): Research and History are
    // now sections of Practice, and Learn is reachable only through the
    // skill-gap chip that seeds it.
    val tabs = listOf(
        stringResource(R.string.practice),
        stringResource(R.string.question_bank)
    )

    AivanceWorkspaceScaffold(
        title = "Prep Studio",
        subtitle = "Master your next interview",
        backContentDescription = stringResource(R.string.back),
        onBack = onBack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Scrollable: five domain tabs must never wrap mid-label at
            // narrow widths (fixed TabRow wrapped "Question Bank" on Inter).
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
            when (selectedTab) {
                0 -> PracticeTab(interviewViewModel, learningViewModel, initialLearnSkill)
                else -> QuestionBankTab(questionBankViewModel)
            }
        }
    }
}

@Composable
private fun PracticeTab(
    viewModel: InterviewViewModel,
    learningViewModel: LearningHubViewModel,
    initialLearnSkill: String?
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is InterviewUiState.Idle -> {
            if (!initialLearnSkill.isNullOrBlank()) {
                // Arriving from a dashboard skill-gap chip: the focused Learn
                // surface replaces the hub, and it is the only way in — Learn is
                // no longer one of the tabs.
                LearnTab(learningViewModel, initialSkill = initialLearnSkill)
                return
            }
            // One scrolling surface for the whole Practice tab: hero, hub,
            // session history and the folded role-intelligence card, instead of
            // three more tabs competing for the same screen.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Hero Section
                val upcoming = state.careerState?.pipeline?.upcomingInterviews?.firstOrNull()
                PrepStudioHero(
                    readinessScore = state.readinessScore,
                    upcomingInterview = upcoming,
                    onQuickPractice = {
                        // Start a behavioral mock immediately — for the
                        // scheduled interview when one exists, otherwise a
                        // general session so the button is never dead.
                        viewModel.onEvent(
                            InterviewUiEvent.StartSession(
                                role = upcoming?.role ?: "Software Engineer",
                                company = upcoming?.company ?: "General",
                                type = "BEHAVIORAL",
                                jobId = upcoming?.id?.toLongOrNull()
                            )
                        )
                    }
                )

                PracticeHub(
                    upcomingInterviews = state.careerState?.pipeline?.upcomingInterviews.orEmpty(),
                    starPack = state.starPack,
                    isGeneratingPack = state.isGeneratingPack,
                    onGeneratePack = { role -> viewModel.onEvent(InterviewUiEvent.GenerateStarPack(role)) },
                    onStart = { r, c, t, j, pack -> viewModel.onEvent(InterviewUiEvent.StartSession(r, c, t, j, pack)) }
                )

                RoleIntelligenceCard(state)

                HistorySection(viewModel)
            }
        }
        is InterviewUiState.Preparing -> LoadingPanel("Configuring your AI interview coach...")
        is InterviewUiState.Active -> ActiveSessionPanel(
            state = state,
            onAnswer = { viewModel.onEvent(InterviewUiEvent.SubmitAnswer(it)) },
            onNext = { viewModel.onEvent(InterviewUiEvent.NextQuestion) },
            onComplete = { viewModel.onEvent(InterviewUiEvent.Complete) }
        )
        is InterviewUiState.Review -> SessionReviewPanel(
            session = state.session,
            onNewSession = { viewModel.onEvent(InterviewUiEvent.Reset) }
        )
        is InterviewUiState.Error -> AivanceEmptyState(
            title = "Session Configuration Failed",
            description = state.message,
            icon = Icons.Rounded.ErrorOutline,
            primaryActionText = "Retry",
            onPrimaryAction = { viewModel.onEvent(InterviewUiEvent.Reset) }
        )
    }
}

@Composable
private fun PrepStudioHero(
    readinessScore: Int?,
    upcomingInterview: com.bangersoul.aivance.core.common.model.UpcomingInterviewShort?,
    onQuickPractice: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        AivanceHeroCard(
            title = if (upcomingInterview != null) "Prep for ${upcomingInterview.company}" else "Interview Readiness",
            description = if (upcomingInterview != null)
                "You have an interview for ${upcomingInterview.role} scheduled for ${upcomingInterview.dateTime}."
                else "Complete mock sessions to increase your score and confidence.",
            actionLabel = "Quick Practice",
            onClick = onQuickPractice
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = AivanceTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Not measured yet: an empty gauge and an em dash, never an invented score.
                    ScoreGauge(score = readinessScore ?: 0, size = 48.dp)
                    Column {
                        Text("Readiness", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(readinessScore?.let { "$it%" } ?: "—", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = AivanceTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = AivanceTheme.colors.success.copy(alpha = 0.1f)) {
                        Icon(Icons.Rounded.Timer, null, Modifier.padding(8.dp).size(20.dp), tint = AivanceTheme.colors.success)
                    }
                    Column {
                        Text("Practice", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            readinessScore?.let { "${String.format("%.1f", it * 0.15)} hrs" } ?: "—",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeHub(
    upcomingInterviews: List<com.bangersoul.aivance.core.common.model.UpcomingInterviewShort>,
    starPack: List<com.bangersoul.aivance.core.common.model.InterviewQuestion>?,
    isGeneratingPack: Boolean,
    onGeneratePack: (String) -> Unit,
    onStart: (String, String, String, Long?, List<com.bangersoul.aivance.core.common.model.InterviewQuestion>?) -> Unit
) {
    var role by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BEHAVIORAL") }
    var selectedJobId by remember { mutableStateOf<Long?>(null) }
    var packRole by remember { mutableStateOf("") }

    val types = listOf(
        "TECHNICAL" to "Technical",
        "BEHAVIORAL" to "Behavioral",
        "SYSTEM_DESIGN" to "System Design"
    )

    // No scroll of its own: the Practice tab owns the single scroll surface, and
    // a nested same-direction scrollable would blow up at measure time.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (upcomingInterviews.isNotEmpty()) {
            SectionHeader(title = "Scheduled Interviews")
            upcomingInterviews.forEach { interview ->
                AivanceWorkspaceCard(
                    onClick = { onStart(interview.role, interview.company, "BEHAVIORAL", interview.id.toLongOrNull(), null) }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                            Icon(Icons.Rounded.Business, null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(interview.company, fontWeight = FontWeight.Bold)
                            Text(interview.role, style = MaterialTheme.typography.bodySmall)
                        }
                        AivanceTertiaryButton(text = "Prep", onClick = { onStart(interview.role, interview.company, "BEHAVIORAL", interview.id.toLongOrNull(), null) })
                    }
                }
            }
        }

        SectionHeader(title = "Custom Mock Session")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AivanceTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Configure Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Target Role") },
                    placeholder = { Text("e.g. Android Engineer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AivanceTheme.shapes.medium
                )

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AivanceTheme.shapes.medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { (id, label) ->
                        FilterChip(
                            selected = type == id,
                            onClick = { type = id },
                            label = { Text(label) }
                        )
                    }
                }

                AivancePrimaryButton(
                    text = "Start Mock Interview",
                    onClick = { onStart(role, company, type, null, null) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = role.isNotBlank(),
                    icon = Icons.Rounded.PlayArrow
                )
            }
        }

        SectionHeader(title = "STAR Prep Packs")
        Text(
            "Role-specific STAR-format question packs with worked answers — generate one, then practice it as a mock session.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AivanceTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = packRole,
                    onValueChange = { packRole = it },
                    label = { Text("Target Role") },
                    placeholder = { Text("e.g. Android Engineer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AivanceTheme.shapes.medium
                )
                AivancePrimaryButton(
                    text = if (isGeneratingPack) "Generating pack…" else "Generate STAR Pack",
                    onClick = { onGeneratePack(packRole) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = packRole.isNotBlank() && !isGeneratingPack,
                    icon = Icons.Rounded.AutoAwesome
                )
            }
        }

        if (starPack != null && starPack.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            starPack.forEach { question ->
                AivanceWorkspaceCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(question.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(text = question.category.replace('_', ' '), tone = BannerTone.INFO)
                            StatusChip(
                                text = question.difficulty.replace('_', ' '),
                                tone = if (question.difficulty == "HARD") BannerTone.ERROR else BannerTone.INFO
                            )
                        }
                        if (question.expectedKeyPoints.isNotEmpty()) {
                            Text(
                                "STAR: ${question.expectedKeyPoints.joinToString(" · ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            AivanceSecondaryButton(
                text = "Practice this pack",
                onClick = { onStart(packRole, "", "BEHAVIORAL", null, starPack) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.PlayArrow
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

/**
 * Role intelligence, folded out of the old Research tab into Practice.
 *
 * It renders only what the account actually knows — the target role and the
 * skills on the profile. The tab it replaces also printed a generic "Company
 * Research" overview and two canned "interview edge" bullets that were
 * identical for every user and led nowhere (AUDIT 11).
 */
@Composable
private fun RoleIntelligenceCard(state: InterviewUiState.Idle) {
    val profile = state.careerState?.profile ?: return
    val role = profile.targetRole.ifBlank { return }
    val topSkills = profile.skills.take(3).joinToString(", ")
    if (topSkills.isBlank()) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(title = stringResource(R.string.practice_role_intelligence))
        InsightCard(
            text = stringResource(R.string.practice_role_intelligence_body, role, topSkills),
            icon = Icons.Rounded.AutoAwesome
        )
    }
}
    @Composable
private fun LoadingPanel(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActiveSessionPanel(
    state: InterviewUiState.Active,
    onAnswer: (String) -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit
) {
    var answerText by remember { mutableStateOf("") }
    val session = state.session
    val question = session.questions.getOrNull(state.currentQuestionIndex)
    val total = session.questions.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(session.targetRole, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (session.companyName.isNotBlank()) {
                    Text(session.companyName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusChip(
                text = if (total > 0) {
                    stringResource(R.string.question_progress, state.currentQuestionIndex + 1, total)
                } else {
                    stringResource(R.string.preparing)
                },
                tone = BannerTone.INFO
            )
        }

        if (total > 0) {
            LinearProgressIndicator(
                progress = { (state.currentQuestionIndex + 1).coerceAtMost(total) / total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = AivanceTheme.colors.accent
            )
        }

        question?.let { q ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.question), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(q.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (q.category.isNotBlank()) StatusChip(text = q.category, tone = BannerTone.INFO)
                }
            }
        } ?: LoadingPanel(stringResource(R.string.preparing_questions))

        OutlinedTextField(
            value = answerText,
            onValueChange = { answerText = it },
            label = { Text(stringResource(R.string.your_answer)) },
            placeholder = { Text(stringResource(R.string.answer_hint)) },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            enabled = !state.isSubmitting
        )

        AivancePrimaryButton(
            text = stringResource(R.string.submit_answer),
            onClick = {
                onAnswer(answerText)
                answerText = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = answerText.length > 20 && !state.isSubmitting
        )

        if (question != null) {
            OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.next_question))
            }
        }
        TextButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.end_session), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SessionReviewPanel(
    session: com.bangersoul.aivance.core.common.model.InterviewSession,
    onNewSession: () -> Unit
) {
    val feedback = session.feedback
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Evaluation Hub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (feedback != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ScoreGauge(score = feedback.overallScore, size = 100.dp)
                    Column {
                        Text("Overall Readiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Based on technical and behavioral performance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Skill Scores
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(label = "Communication", value = "85%", icon = Icons.Rounded.RecordVoiceOver, modifier = Modifier.weight(1f))
                StatCard(label = "STAR Method", value = "70%", icon = Icons.Rounded.Star, modifier = Modifier.weight(1f))
            }

            if (feedback.detailedSummary.isNotBlank()) {
                InsightCard(text = feedback.detailedSummary, icon = Icons.Rounded.AutoAwesome)
            }

            if (feedback.improvements.isNotEmpty()) {
                SectionHeader(title = "Improvement Plan")
                feedback.improvements.forEach { tip ->
                    AivanceWorkspaceCard {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.TrendingUp, null, tint = AivanceTheme.colors.accent, modifier = Modifier.size(18.dp))
                            Text(tip, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            AivanceEmptyState(
                title = "Analysis in Progress",
                description = "AI is evaluating your session and generating your improvement plan.",
                icon = Icons.Rounded.TrendingUp,
                compact = true
            )
        }

        Spacer(Modifier.height(16.dp))
        AivancePrimaryButton(
            text = "Start a New Session",
            onClick = onNewSession,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = AivanceTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Session history as a section of the Practice tab (it used to be its own tab).
 * A plain column: the Practice surface already scrolls.
 */
@Composable
private fun HistorySection(viewModel: InterviewViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history = (uiState as? InterviewUiState.Idle)?.history.orEmpty()

    if (history.isEmpty()) {
        AivanceEmptyState(
            title = stringResource(R.string.no_sessions),
            description = stringResource(R.string.no_sessions_desc),
            icon = Icons.Rounded.History
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(title = stringResource(R.string.history))
        history.forEach { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (session.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RecordVoiceOver,
                        null,
                        tint = if (session.isCompleted) AivanceTheme.colors.success else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(session.targetRole, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            buildString {
                                append(session.type.replace('_', ' '))
                                if (session.companyName.isNotBlank()) append(" · ").append(session.companyName)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    session.feedback?.overallScore?.let {
                        Text("$it", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AivanceTheme.colors.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionBankTab(viewModel: QuestionBankViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories = listOf(
        "ALL" to stringResource(R.string.all),
        "FAVORITES" to stringResource(R.string.favorites),
        "BEHAVIORAL" to stringResource(R.string.behavioral),
        "TECHNICAL" to stringResource(R.string.technical),
        "LEADERSHIP" to stringResource(R.string.leadership),
        "GENERAL" to stringResource(R.string.general)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val current = uiState as? QuestionBankUiState.Content
            categories.forEach { (id, label) ->
                FilterChip(
                    selected = current?.category == id,
                    onClick = { viewModel.onEvent(QuestionBankUiEvent.SelectCategory(id)) },
                    label = { Text(label) }
                )
            }
        }

        when (val state = uiState) {
            is QuestionBankUiState.Loading -> LoadingPanel(stringResource(R.string.loading_question_bank))
            is QuestionBankUiState.Error -> AivanceEmptyState(
                title = stringResource(R.string.load_questions_failed),
                description = state.message,
                icon = Icons.Rounded.ErrorOutline,
                primaryActionText = stringResource(R.string.try_again),
                onPrimaryAction = { viewModel.onEvent(QuestionBankUiEvent.Retry) }
            )
            is QuestionBankUiState.Content -> {
                if (state.questions.isEmpty()) {
                    AivanceEmptyState(
                        title = stringResource(R.string.no_questions),
                        description = stringResource(R.string.no_questions_desc),
                        icon = Icons.Rounded.MenuBook,
                        compact = true
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.questions) { question ->
                            QuestionBankCard(
                                question = question,
                                isFavorite = question.isFavorite,
                                isIdealAnswerLoading = state.isIdealAnswerLoading && state.idealAnswerFor == question.id,
                                idealAnswer = if (state.idealAnswerFor == question.id) state.idealAnswer else null,
                                idealAnswerError = if (state.idealAnswerFor == question.id) state.idealAnswerError else null,
                                onToggleFavorite = { viewModel.onEvent(QuestionBankUiEvent.ToggleFavorite(question.id)) },
                                onViewIdealAnswer = { viewModel.onEvent(QuestionBankUiEvent.ViewIdealAnswer(question.id)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionBankCard(
    question: com.bangersoul.aivance.core.common.model.InterviewQuestion,
    isFavorite: Boolean,
    isIdealAnswerLoading: Boolean,
    idealAnswer: String?,
    idealAnswerError: String?,
    onToggleFavorite: () -> Unit,
    onViewIdealAnswer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(question.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(text = question.category.replace('_', ' '), tone = BannerTone.INFO)
                        StatusChip(
                            text = question.difficulty.replace('_', ' '),
                            tone = if (question.difficulty == "HARD") BannerTone.ERROR else BannerTone.INFO
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (isFavorite) stringResource(R.string.remove_favorite) else stringResource(R.string.mark_favorite),
                        tint = if (isFavorite) AivanceTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isIdealAnswerLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.generating_ideal_answer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (idealAnswer != null) {
                InsightCard(text = idealAnswer, icon = Icons.Rounded.Lightbulb)
            } else if (idealAnswerError != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text(
                        idealAnswerError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                TextButton(onClick = onViewIdealAnswer) {
                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.view_ideal_answer))
                }
            }
        }
    }
}

@Composable
private fun LearnTab(viewModel: LearningHubViewModel, initialSkill: String? = null) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var targetRole by remember { mutableStateOf(initialSkill.orEmpty()) }

    // Seeded from a dashboard skill-gap chip: prefill and fetch recommendations
    // once, so the user lands on results for the skill they lack.
    LaunchedEffect(initialSkill) {
        if (!initialSkill.isNullOrBlank()) {
            targetRole = initialSkill
            viewModel.onEvent(
                LearningHubUiEvent.GetRecommendations(
                    currentSkills = initialSkill,
                    targetRole = initialSkill
                )
            )
        }
    }

    when (val state = uiState) {
        is LearningHubUiState.Idle, is LearningHubUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(stringResource(R.string.personalized_learning), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.learning_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetRole,
                    onValueChange = { targetRole = it },
                    label = { Text(stringResource(R.string.target_role)) },
                    placeholder = { Text(stringResource(R.string.target_role_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AivancePrimaryButton(
                    text = if (uiState is LearningHubUiState.Loading) stringResource(R.string.generating) else stringResource(R.string.generate_recommendations),
                    onClick = {
                        viewModel.onEvent(
                            LearningHubUiEvent.GetRecommendations(
                                currentSkills = targetRole,
                                targetRole = targetRole
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = targetRole.isNotBlank() && uiState !is LearningHubUiState.Loading
                )
            }
        }
        is LearningHubUiState.Error -> AivanceEmptyState(
            title = stringResource(R.string.load_resources_failed),
            description = state.message,
            icon = Icons.Rounded.ErrorOutline,
            primaryActionText = stringResource(R.string.try_again),
            onPrimaryAction = { viewModel.onEvent(LearningHubUiEvent.Reset) }
        )
        is LearningHubUiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.recommendedSkills.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.recommended_skills), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(state.recommendedSkills) { skill ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Rounded.School, null, tint = AivanceTheme.colors.accent, modifier = Modifier.size(18.dp))
                                Text(skill, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.resources), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (state.suggestedResources.isEmpty()) {
                    item {
                        AivanceEmptyState(
                            title = stringResource(R.string.no_resources),
                            description = stringResource(R.string.no_resources_desc),
                            icon = Icons.Rounded.MenuBook,
                            compact = true
                        )
                    }
                } else {
                    items(state.suggestedResources) { resource ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(resource.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(resource.type.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
