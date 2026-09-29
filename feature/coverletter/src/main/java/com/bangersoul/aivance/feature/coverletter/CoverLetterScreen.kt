package com.bangersoul.aivance.feature.coverletter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.common.model.CoverLetterVersion
import com.bangersoul.aivance.core.designsystem.components.ActionButton
import com.bangersoul.aivance.core.designsystem.components.AivancePrimaryButton
import com.bangersoul.aivance.core.designsystem.components.AivanceScreen
import com.bangersoul.aivance.core.designsystem.components.AivanceSecondaryButton
import com.bangersoul.aivance.core.designsystem.components.DashboardCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverLetterScreen(
    viewModel: CoverLetterViewModel,
    onNavigateBack: () -> Unit,
    jobId: Long? = null,
    onFindJobs: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(jobId) {
        // Arriving from Job Details carries a cached DB job id → auto-generate
        // a tailored letter from the primary resume. Plain opens just load.
        if (jobId != null) {
            viewModel.onEvent(CoverLetterUiEvent.GenerateForJob(jobId))
        } else {
            viewModel.onEvent(CoverLetterUiEvent.Load)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CoverLetterUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is CoverLetterUiEffect.CopyText -> clipboardManager.setText(AnnotatedString(effect.text))
                is CoverLetterUiEffect.ExportPdf -> sharePdf(context, effect.uri)
            }
        }
    }

    AivanceScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cover_letter_intelligence_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        isLoading = uiState is CoverLetterUiState.Loading,
        error = (uiState as? CoverLetterUiState.Error)?.message
    ) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "CoverLetterTransition"
            ) { state ->
                when (state) {
                    CoverLetterUiState.Idle -> CoverLetterEmptyContent(
                        onGeneratePrimary = { viewModel.onEvent(CoverLetterUiEvent.GenerateFromPrimary) },
                        onFindJobs = onFindJobs
                    )
                    is CoverLetterUiState.Success -> CoverLetterEditorContent(
                        version = state.selectedVersion ?: state.coverLetter?.versions?.firstOrNull(),
                        isGenerating = state.isGenerating,
                        streamingContent = state.streamingContent,
                        isEditing = state.isEditing,
                        sectionDrafts = state.sectionDrafts,
                        onToggleEdit = { viewModel.onEvent(CoverLetterUiEvent.ToggleEdit) },
                        onUpdateSection = { index, content ->
                            viewModel.onEvent(CoverLetterUiEvent.UpdateSection(index, content))
                        },
                        onSaveEdits = { viewModel.onEvent(CoverLetterUiEvent.SaveEdits) },
                        onCopyAll = { viewModel.onEvent(CoverLetterUiEvent.CopyAll) },
                        onExport = { viewModel.onEvent(CoverLetterUiEvent.Export) },
                        onRegenerate = { viewModel.onEvent(CoverLetterUiEvent.RegenerateSection(it, "BODY")) }
                    )
                    else -> {}
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/** Shares a generated PDF (Phase 4 STEP 2) via ACTION_SEND with the app's FileProvider. */
private fun sharePdf(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, context.getString(R.string.export_cover_letter))
    if (shareIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(chooser)
    }
}

@Composable
private fun CoverLetterEmptyContent(
    onGeneratePrimary: () -> Unit,
    onFindJobs: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Description, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.no_cover_letter_yet), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.no_cover_letter_desc),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        AivancePrimaryButton(
            text = stringResource(R.string.generate_cover_letter),
            onClick = onGeneratePrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        AivanceSecondaryButton(
            text = stringResource(R.string.find_jobs),
            onClick = onFindJobs,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CoverLetterEditorContent(
    version: CoverLetterVersion?,
    isGenerating: Boolean,
    streamingContent: String?,
    isEditing: Boolean,
    sectionDrafts: Map<Int, String>,
    onToggleEdit: () -> Unit,
    onUpdateSection: (Int, String) -> Unit,
    onSaveEdits: () -> Unit,
    onCopyAll: () -> Unit,
    onExport: () -> Unit,
    onRegenerate: (Long) -> Unit
) {
    val isStreaming = streamingContent != null && version == null
    if (version == null && !isStreaming) return

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                version?.versionName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = onCopyAll) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.copy_all))
                }
                IconButton(onClick = onExport) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = stringResource(R.string.export))
                }
                IconButton(
                    onClick = { if (isEditing) onSaveEdits() else onToggleEdit() }
                ) {
                    Icon(
                        if (isEditing) Icons.Rounded.Save else Icons.Rounded.Edit,
                        contentDescription = if (isEditing) stringResource(R.string.save_edits) else stringResource(R.string.edit)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isGenerating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
        }

        // Live streaming preview while the letter is being generated.
        if (isStreaming) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.generating_letter),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            StreamingLetterText(streamingContent.orEmpty())
                        }
                    }
                }
            }
            return
        }

        val sections = version?.sections.orEmpty()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(sections, key = { index, section -> "${section.sectionType}_$index" }) { index, section ->
                SectionCard(
                    title = section.title,
                    content = if (isEditing) {
                        sectionDrafts[index] ?: section.content
                    } else {
                        section.content
                    },
                    isEditing = isEditing,
                    onContentChange = { onUpdateSection(index, it) },
                    onRegenerate = { version?.id?.let(onRegenerate) }
                )
            }
            if (isEditing) {
                item {
                    ActionButton(
                        text = stringResource(R.string.save_changes),
                        onClick = onSaveEdits,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Save
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

/** Live token stream rendered with a blinking caret (typewriter effect). */
@Composable
private fun StreamingLetterText(content: String) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "clCaret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(420),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "clCaretAlpha"
    )
    Row {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "▌",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: String,
    isEditing: Boolean,
    onContentChange: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    DashboardCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (!isEditing) {
                    Row {
                        IconButton(onClick = onRegenerate) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (isEditing) {
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    label = { Text(stringResource(R.string.edit_section, title.lowercase())) }
                )
            } else {
                Text(content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
