package com.bangersoul.aivance.feature.assistant

import android.app.Activity
import android.content.Intent
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.core.common.model.AssistantJobContext
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import java.util.Calendar

/**
 * The AI Career Assistant — an operating-system style command surface.
 *
 * v2: personalized greeting header, intent quick-action chips, live provider
 * status bar, and an input bar with voice/document/photo affordances.
 *
 * [initialJobContext] carries the job the user was looking at when the
 * assistant was surfaced (saved jobs / job details), so replies are tailored
 * to that role.
 */
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onSwitchProvider: () -> Unit = {},
    initialJobContext: AssistantJobContext? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val providerStatus by viewModel.providerStatus.collectAsStateWithLifecycle()
    val careerState by viewModel.careerState.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(initialJobContext) {
        viewModel.setJobContext(initialJobContext)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AssistantHeader(
            userName = userName,
            providerStatus = providerStatus,
            onSwitchProvider = onSwitchProvider
        )

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "AssistantTransition"
            ) { state ->
                when (state) {
                    is AssistantUiState.Idle -> AssistantCopilotWorkspace(
                        careerState = careerState,
                        providerReady = providerStatus.isReady,
                        onPromptClick = { prompt -> viewModel.sendMessage(prompt) },
                        onConfigureProvider = onSwitchProvider
                    )
                    is AssistantUiState.Loading -> SkeletonDashboard(modifier = Modifier.fillMaxSize())
                    is AssistantUiState.Chatting -> ChatContent(
                        messages = state.messages,
                        isTyping = state.isTyping,
                        streamingContent = state.streamingContent,
                        streamFailed = state.streamFailed,
                        onRetry = { viewModel.retry() }
                    )
                    is AssistantUiState.Error -> AivanceError(
                        message = state.message,
                        onRetry = { viewModel.retry() },
                        title = stringResource(R.string.assistant_provider_unavailable_title),
                        detail = stringResource(R.string.assistant_provider_unavailable_detail),
                        primaryActionText = stringResource(R.string.assistant_retry),
                        secondaryActionText = stringResource(R.string.assistant_switch_provider),
                        onSecondaryAction = onSwitchProvider
                    )
                }
            }
        }

        AssistantInputBar(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                viewModel.sendMessage(it)
                inputText = ""
            }
        )
    }
}

@Composable
private fun greetingForTime(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..11 -> stringResource(R.string.assistant_good_morning)
    in 12..16 -> stringResource(R.string.assistant_good_afternoon)
    else -> stringResource(R.string.assistant_good_evening)
}

@Composable
private fun AssistantHeader(
    userName: String,
    providerStatus: ProviderStatusUi,
    onSwitchProvider: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${greetingForTime()}, ${userName.ifBlank { stringResource(R.string.assistant_greeting_there) }}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.assistant_achievement_question),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProviderStatusChip(providerStatus, onSwitchProvider)
        }
    }
}

@Composable
private fun ProviderStatusChip(status: ProviderStatusUi, onSwitchProvider: () -> Unit) {
    val (dotColor, label) = if (status.isReady) {
        AivanceTheme.colors.success to (status.providerName?.let { "$it · ${status.statusLabel}" } ?: stringResource(R.string.assistant_active))
    } else {
        AivanceTheme.colors.warning to stringResource(R.string.assistant_no_provider)
    }
    Surface(
        onClick = onSwitchProvider,
        shape = RoundedCornerShape(100.dp),
        color = dotColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun suggestedPrompts(): List<String> = listOf(
    stringResource(R.string.assistant_prompt_resume),
    stringResource(R.string.assistant_prompt_followup),
    stringResource(R.string.assistant_prompt_outreach),
    stringResource(R.string.assistant_prompt_ats),
    stringResource(R.string.assistant_prompt_roadmap),
    stringResource(R.string.assistant_prompt_behavioral)
)

@Composable
private fun AssistantCopilotWorkspace(
    careerState: com.bangersoul.aivance.core.common.model.CareerState,
    providerReady: Boolean,
    onPromptClick: (String) -> Unit,
    onConfigureProvider: () -> Unit
) {
    val prompts = suggestedPrompts()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Career Snapshot
        // Career Snapshot renders only once something has actually been
        // measured — an unpopulated "— / —" pair told the user nothing and only
        // padded the workspace (AUDIT 15).
        val careerScore = careerState.growth.careerScore
        val atsScore = careerState.intelligence.atsScore
        if (careerScore != null || atsScore != null) {
            item {
                Text(
                    text = stringResource(R.string.assistant_snapshot),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = stringResource(R.string.assistant_career_score),
                        value = careerScore?.toString() ?: "—",
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.TrendingUp
                    )
                    MetricCard(
                        label = stringResource(R.string.assistant_ats_match),
                        value = atsScore?.let { "$it%" } ?: "—",
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.FactCheck
                    )
                }
            }
        }

        // 2. Next Best Actions (Hero Card if available)
        careerState.nextBestAction?.let { action ->
            item {
                AivanceHeroCard(
                    title = action.title,
                    description = action.description,
                    actionLabel = "Execute",
                    onClick = { onPromptClick(action.title) }
                )
            }
        }

        // 3. Quick Commands
        item {
            SectionHeader(title = stringResource(R.string.assistant_quick_commands))
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    QuickActionChip(stringResource(R.string.assistant_chip_optimize), Icons.Rounded.Description, AivanceTheme.colors.accent) {
                        onPromptClick("Help me optimize my resume sections.")
                    }
                }
                item {
                    QuickActionChip(stringResource(R.string.assistant_chip_find_jobs), Icons.Rounded.WorkOutline, AivanceTheme.colors.info) {
                        onPromptClick("Find the best job matches for my current profile.")
                    }
                }
                item {
                    QuickActionChip(stringResource(R.string.assistant_chip_mock), Icons.Rounded.RecordVoiceOver, AivanceTheme.colors.warning) {
                        onPromptClick("Start a mock interview session for my target role.")
                    }
                }
            }
        }

        // 4. Inline provider setup card
        if (!providerReady) {
            item {
                ProviderSetupCard(onConfigureProvider)
            }
        }

        // 5. Try a Prompt
        item {
            SectionHeader(title = stringResource(R.string.assistant_suggested))
        }

        items(prompts.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { prompt ->
                    Surface(
                        onClick = { onPromptClick(prompt) },
                        shape = AivanceTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            prompt,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 6. Recent Intelligence (Timeline)
        item {
            SectionHeader(title = stringResource(R.string.assistant_recent_insights))
            Spacer(Modifier.height(8.dp))
        }

        if (careerState.recommendations.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.assistant_no_insights),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(careerState.recommendations.take(3)) { rec ->
                AivanceWorkspaceCard(onClick = { onPromptClick("Tell me more about: ${rec.title}") }) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = AivanceTheme.colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(rec.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(rec.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AivanceWorkspaceCard(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = AivanceTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        content()
    }
}

@Composable
private fun ProviderSetupCard(onConfigureProvider: () -> Unit) {
    DashboardCard(onClick = onConfigureProvider, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = AivanceTheme.colors.warning.copy(alpha = 0.15f)
            ) {
                Icon(
                    Icons.Rounded.Key,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = AivanceTheme.colors.warning
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.assistant_no_provider_configured),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.assistant_no_provider_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AivanceTertiaryButton(text = stringResource(R.string.assistant_configure), onClick = onConfigureProvider)
        }
    }
}

@Composable
private fun QuickActionChip(label: String, icon: ImageVector, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = AivanceTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f)) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp).size(16.dp),
                    tint = tint
                )
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ChatContent(
    messages: List<AssistantChatMessage>,
    isTyping: Boolean,
    streamingContent: String?,
    streamFailed: Boolean,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    val hasStreaming = streamingContent != null
    val itemCount = messages.size + if (hasStreaming) 1 else 0

    // Follow the newest content. Chunk arrivals update streamingContent many
    // times per second, so use an instant (non-animated) jump to avoid scroll
    // jitter fighting the user; animate only when a new message lands.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(streamingContent) {
        if (hasStreaming) listState.scrollToItem(itemCount - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages) { msg ->
            AssistantBubble(msg)
        }
        if (hasStreaming) {
            item {
                StreamingBubble(
                    content = streamingContent.orEmpty(),
                    failed = streamFailed,
                    onRetry = onRetry
                )
            }
        } else if (isTyping) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypingIndicator()
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.assistant_thinking),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Live assistant bubble that grows as token chunks stream in. While generating
 * it shows a blinking caret; if the stream failed it stops the caret and offers
 * a retry so the partial text never reads as "still thinking".
 */
@Composable
private fun StreamingBubble(
    content: String,
    failed: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = if (failed) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!failed) {
                    BlinkingCaret()
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.assistant_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        if (failed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 6.dp, top = 4.dp)
            ) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    stringResource(R.string.assistant_response_interrupted),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.assistant_retry))
                }
            }
        }
    }
}

/** A small caret that pulses while the assistant is generating. */
@Composable
private fun BlinkingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )
    Text(
        text = "▌",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    )
}

@Composable
private fun AssistantBubble(msg: AssistantChatMessage) {
    val isUser = msg.role == "USER"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = if (isUser) {
                RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
            } else {
                RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
            },
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    msg.content,
                    style = MaterialTheme.typography.bodyMedium
                )

                // AI Action Card detection (Simplified)
                if (!isUser) {
                    val content = msg.content.lowercase()
                    when {
                        content.contains("optimize") -> ActionCard("Optimize Resume", Icons.Rounded.AutoAwesome) { /* Navigate */ }
                        content.contains("search") || content.contains("job") -> ActionCard("Find Jobs", Icons.Rounded.Search) { /* Navigate */ }
                        content.contains("interview") -> ActionCard("Start Practice", Icons.Rounded.RecordVoiceOver) { /* Navigate */ }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (isUser) stringResource(R.string.assistant_you) else stringResource(R.string.assistant_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

@Composable
private fun ActionCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Surface(
        onClick = onClick,
        shape = AivanceTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun AssistantInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: (String) -> Unit
) {
    val context = LocalContext.current
    val permissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    // ── Voice input via SpeechRecognizer ────────────────────────────────────
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcript = results?.firstOrNull()?.takeIf { it.isNotBlank() }
            if (transcript != null) {
                onValueChange(if (value.isBlank()) transcript else "$value $transcript")
            }
        }
    }

    // ── Document / file attach ───────────────────────────────────────────────
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Resolve display name for context without reading file bytes
            val name = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: context.getString(R.string.assistant_document_fallback)
            val prefix = context.getString(R.string.assistant_attached_file, name)
            onValueChange(prefix + value)
        }
    }

    // ── Photo / image attach ─────────────────────────────────────────────────
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val marker = context.getString(R.string.assistant_photo_attached)
            onValueChange(marker + value)
        }
    }

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                // Lift the composer above the keyboard (edge-to-edge IME insets).
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mic — launches Android SpeechRecognizer
            IconButton(onClick = {
                if (permissionState.status.isGranted) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.assistant_speak_prompt))
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    speechLauncher.launch(intent)
                } else {
                    permissionState.launchPermissionRequest()
                }
            }) {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = stringResource(R.string.assistant_voice_input),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Attach — opens any document (PDF, DOCX, TXT…)
            IconButton(onClick = {
                documentLauncher.launch(
                    arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/plain"
                    )
                )
            }) {
                Icon(
                    Icons.Rounded.AttachFile,
                    contentDescription = stringResource(R.string.assistant_attach_document),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Photo — opens system image picker
            IconButton(onClick = {
                photoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) {
                Icon(
                    Icons.Rounded.PhotoCamera,
                    contentDescription = stringResource(R.string.assistant_attach_photo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.assistant_input_placeholder)) },
                shape = AivanceTheme.shapes.large,
                maxLines = 4,
                trailingIcon = {
                    AnimatedVisibility(
                        visible = value.isNotBlank(),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        IconButton(onClick = { onSend(value) }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.assistant_send),
                                tint = AivanceTheme.colors.accent
                            )
                        }
                    }
                }
            )
        }
    }
}
