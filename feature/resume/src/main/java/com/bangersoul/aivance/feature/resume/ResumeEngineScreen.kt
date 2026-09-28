package com.bangersoul.aivance.feature.resume

import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.designsystem.components.*
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L

private val ENGINE_STEPS = listOf(
    R.string.step_import, R.string.step_parsing, R.string.step_preview,
    R.string.step_ats, R.string.step_optimize, R.string.step_save, R.string.step_export
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeEngineScreen(
    viewModel: ResumeEngineViewModel,
    onBack: () -> Unit,
    initialJobDescription: String? = null
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Preload a job description (e.g. from a saved job's "Create tailored
    // resume" action) so the ATS scan step arrives with the JD ready to run.
    LaunchedEffect(initialJobDescription) {
        if (!initialJobDescription.isNullOrBlank()) {
            viewModel.onEvent(ResumeEngineEvent.SetInitialJobDescription(initialJobDescription))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ResumeEngineEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is ResumeEngineEffect.ExportResult -> shareResumeFile(context, effect.text, effect.fileName)
                is ResumeEngineEffect.ExportPdf -> shareResumeExportFile(context, effect.uri, "application/pdf")
                is ResumeEngineEffect.ExportDocx -> shareResumeExportFile(context, effect.uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                ResumeEngineEffect.Finished -> onBack()
            }
        }
    }

    BackHandler(enabled = true) {
        if (state is ResumeEngineState.Import) onBack()
        else viewModel.onEvent(ResumeEngineEvent.Back)
    }

    AivanceWorkspaceScaffold(
        title = stringResource(R.string.resume_engine_title),
        subtitle = stringResource(R.string.resume_engine_subtitle),
        backContentDescription = stringResource(R.string.back),
        onBack = {
            if (state is ResumeEngineState.Import) onBack()
            else viewModel.onEvent(ResumeEngineEvent.Back)
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            EngineStepper(currentStep = state.stepIndex())

            AnimatedContent(
                targetState = state.stepIndex(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ResumeEngineTransition"
            ) { _ ->
                when (val current = state) {
                    is ResumeEngineState.Import -> ImportStep(
                        onFileImported = { viewModel.onEvent(ResumeEngineEvent.ImportFile(it)) },
                        onOcrTextExtracted = { viewModel.onEvent(ResumeEngineEvent.ImportOcrText(it)) },
                        onJsonImported = { viewModel.onEvent(ResumeEngineEvent.ImportJsonText(it)) },
                        onExit = onBack
                    )
                    is ResumeEngineState.Parsing -> ParsingStep(current.progress)
                    is ResumeEngineState.Preview -> PreviewStep(
                        version = current.version,
                        onContinue = { viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview) },
                        onEdit = { title, content ->
                            viewModel.onEvent(ResumeEngineEvent.UpdateSectionContent(title, content))
                        }
                    )
                    is ResumeEngineState.AtsScanning -> AtsScanStep(
                        jdText = current.jdText,
                        onJdTextChange = { viewModel.onEvent(ResumeEngineEvent.UpdateJdText(it)) },
                        onRunScan = { viewModel.onEvent(ResumeEngineEvent.RunAtsScan) },
                        onSkip = { viewModel.onEvent(ResumeEngineEvent.SkipAts) }
                    )
                    is ResumeEngineState.AtsResult -> AtsResultStep(
                        score = current.score,
                        analysis = current.analysis,
                        onContinue = { viewModel.onEvent(ResumeEngineEvent.SkipAts) }
                    )
                    is ResumeEngineState.Optimizing -> OptimizingStep(
                        version = current.version,
                        sectionInProgress = current.sectionInProgress,
                        streamingContent = current.streamingContent,
                        suggestions = current.suggestions,
                        onImprove = { viewModel.onEvent(ResumeEngineEvent.ImproveSection(it)) },
                        onAccept = { viewModel.onEvent(ResumeEngineEvent.AcceptSuggestion(it)) },
                        onDiscard = { viewModel.onEvent(ResumeEngineEvent.DiscardSuggestion(it)) },
                        onSave = { viewModel.onEvent(ResumeEngineEvent.SaveVersion(it)) }
                    )
                    is ResumeEngineState.Saving -> SavingStep()
                    is ResumeEngineState.Exporting -> ExportStep(
                        versionName = current.version.versionName,
                        onExportPdf = { viewModel.onEvent(ResumeEngineEvent.ExportPdf) },
                        onExportDocx = { viewModel.onEvent(ResumeEngineEvent.ExportDocx) },
                        onExportJson = { viewModel.onEvent(ResumeEngineEvent.ExportJson) },
                        onDone = { viewModel.onEvent(ResumeEngineEvent.Finish) }
                    )
                    is ResumeEngineState.Error -> ErrorStep(
                        step = current.step,
                        message = current.message,
                        canRetry = current.canRetry,
                        onRetry = { viewModel.onEvent(ResumeEngineEvent.Retry) },
                        onBack = { viewModel.onEvent(ResumeEngineEvent.Back) }
                    )
                }
            }
        }
    }
}

/** Maps a state to its 0-based step index for the stepper header. */
private fun ResumeEngineState.stepIndex(): Int = when (this) {
    is ResumeEngineState.Import -> 0
    is ResumeEngineState.Parsing -> 1
    is ResumeEngineState.Preview -> 2
    is ResumeEngineState.AtsScanning, is ResumeEngineState.AtsResult -> 3
    is ResumeEngineState.Optimizing -> 4
    is ResumeEngineState.Saving -> 5
    is ResumeEngineState.Exporting -> 6
    is ResumeEngineState.Error -> 0
}

@Composable
private fun EngineStepper(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ENGINE_STEPS.forEachIndexed { index, label ->
            val isActive = index == currentStep
            val isDone = index < currentStep
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            color = when {
                                isActive -> MaterialTheme.colorScheme.primary
                                isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isDone -> Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        isActive -> Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        else -> Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ── Step 1: Import ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportStep(
    onFileImported: (Uri) -> Unit,
    onOcrTextExtracted: (String) -> Unit,
    onJsonImported: (String) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var selectedSize by remember { mutableStateOf<Long?>(null) }
    var sizeError by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraNote by remember { mutableStateOf<String?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val (name, size) = resolveFileMeta(context, uri)
                if (size != null && size > MAX_FILE_SIZE_BYTES) {
                    sizeError = context.getString(R.string.file_too_large)
                } else {
                    sizeError = null
                    cameraNote = null
                    selectedUri = uri
                    selectedName = name
                    selectedSize = size
                }
            }
        }
    )
    val docxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val (name, size) = resolveFileMeta(context, uri)
                if (size != null && size > MAX_FILE_SIZE_BYTES) {
                    sizeError = context.getString(R.string.file_too_large)
                } else {
                    sizeError = null
                    cameraNote = null
                    selectedUri = uri
                    selectedName = name
                    selectedSize = size
                }
            }
        }
    )
    val jsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                if (text.isNullOrBlank()) {
                    cameraNote = context.getString(R.string.json_import_empty)
                } else {
                    onJsonImported(text)
                }
            }
        }
    )
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                val uri = cameraUri ?: return@rememberLauncherForActivityResult
                try {
                    val image = InputImage.fromFilePath(context, uri)
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        .process(image)
                        .addOnSuccessListener { visionText ->
                            val extracted = visionText.text.trim()
                            if (extracted.isNotBlank()) {
                                onOcrTextExtracted(extracted)
                            } else {
                                cameraNote = context.getString(R.string.no_text_found)
                            }
                        }
                        .addOnFailureListener {
                            cameraNote = context.getString(R.string.ocr_failed, it.message ?: context.getString(R.string.unknown_error))
                        }
                } catch (e: Exception) {
                    cameraNote = context.getString(R.string.ocr_load_failed, e.message ?: context.getString(R.string.unknown_error))
                }
            }
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.import_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        item {
            ImportOptionCard(
                icon = Icons.Rounded.Description,
                title = stringResource(R.string.pick_pdf),
                subtitle = "application/pdf",
                onClick = {
                    sizeError = null
                    pdfLauncher.launch(arrayOf("application/pdf"))
                }
            )
        }
        item {
            ImportOptionCard(
                icon = Icons.Rounded.DocumentScanner,
                title = stringResource(R.string.pick_docx),
                subtitle = stringResource(R.string.docx_subtitle),
                onClick = {
                    sizeError = null
                    docxLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    )
                }
            )
        }
        item {
            ImportOptionCard(
                icon = Icons.Rounded.PhotoCamera,
                title = stringResource(R.string.scan_camera),
                subtitle = stringResource(R.string.scan_camera_subtitle),
                onClick = {
                    sizeError = null
                    val file = File(context.cacheDir, "camera_resume_${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                }
            )
        }
        item {
            ImportOptionCard(
                icon = Icons.Rounded.FileDownload,
                title = stringResource(R.string.import_json_resume),
                subtitle = stringResource(R.string.json_resume_subtitle),
                onClick = {
                    sizeError = null
                    jsonLauncher.launch(
                        arrayOf("application/json", "application/octet-stream", "text/plain")
                    )
                }
            )
        }

        if (selectedName != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(selectedName ?: "", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (selectedSize != null) formatFileSize(selectedSize!!) else stringResource(R.string.size_unknown),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            selectedUri = null
                            selectedName = null
                            selectedSize = null
                            cameraNote = null
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove_file))
                        }
                    }
                }
            }
        }

        if (sizeError != null) {
            item {
                Text(sizeError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (cameraNote != null) {
            item {
                Text(
                    cameraNote!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            AivancePrimaryButton(
                text = stringResource(R.string.continue_button),
                onClick = { selectedUri?.let(onFileImported) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.FileUpload,
                enabled = selectedUri != null && sizeError == null
            )
            Spacer(Modifier.height(8.dp))
            AivanceSecondaryButton(
                text = stringResource(R.string.cancel),
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

private data class FileMeta(val name: String, val size: Long?)

private fun resolveFileMeta(context: android.content.Context, uri: Uri): FileMeta {
    var name: String? = null
    var size: Long? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIdx >= 0) name = cursor.getString(nameIdx)
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
        }
    }
    return FileMeta(
        name ?: uri.lastPathSegment ?: context.getString(R.string.imported_resume),
        size
    )
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
}

@Composable
private fun ImportOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Step 2: Parsing ──────────────────────────────────────────────────────────

@Composable
private fun ParsingStep(progress: Float) {
    val animated = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "parsingProgress"
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            strokeWidth = 6.dp,
            progress = { animated.value }
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.parsing_resume), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.extracting_sections),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { animated.value },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Step 3: Preview ──────────────────────────────────────────────────────────

@Composable
private fun PreviewStep(
    version: ResumeVersion,
    onContinue: () -> Unit,
    onEdit: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.preview_subtitle, version.sections.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            StatusChip(
                text = stringResource(R.string.sections_detected, version.sections.size),
                tone = BannerTone.SUCCESS
            )
            Spacer(Modifier.height(8.dp))
        }

        items(version.sections) { section ->
            val isExpanded = section.title in expanded
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(section.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = {
                            expanded = if (isExpanded) expanded - section.title else expanded + section.title
                        }) {
                            Icon(
                                if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand)
                            )
                        }
                    }
                    if (isExpanded) {
                        OutlinedTextField(
                            value = section.content,
                            onValueChange = { onEdit(section.title, it) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            minLines = 4
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            AivancePrimaryButton(
                text = stringResource(R.string.looks_good),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

// ── Step 4: ATS Scan ─────────────────────────────────────────────────────────

@Composable
private fun AtsScanStep(
    jdText: String,
    onJdTextChange: (String) -> Unit,
    onRunScan: () -> Unit,
    onSkip: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.ats_scan_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.ats_scan_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = jdText,
            onValueChange = onJdTextChange,
            label = { Text(stringResource(R.string.job_description)) },
            modifier = Modifier.fillMaxWidth().height(280.dp),
            placeholder = { Text(stringResource(R.string.jd_placeholder)) }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ats_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        AivancePrimaryButton(
            text = stringResource(R.string.run_ats_scan),
            onClick = onRunScan,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Search,
            enabled = jdText.length > 50
        )
        Spacer(Modifier.height(8.dp))
        AivanceSecondaryButton(
            text = stringResource(R.string.skip_ats_scan),
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(48.dp))
    }
}

// ── Step 4b: ATS Result ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AtsResultStep(
    score: Int,
    analysis: AtsReport,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(stringResource(R.string.ats_match_report), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    Modifier.padding(24.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    ScoreGauge(score = score, size = 100.dp)
                    Column {
                        Text(stringResource(R.string.overall_ats_score), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        StatusChip(
                            text = when {
                                score > 80 -> stringResource(R.string.ready_to_apply)
                                score > 50 -> stringResource(R.string.needs_improvement)
                                else -> stringResource(R.string.weak_match)
                            },
                            tone = when {
                                score > 80 -> BannerTone.SUCCESS
                                score > 50 -> BannerTone.WARNING
                                else -> BannerTone.ERROR
                            }
                        )
                    }
                }
            }
        }

        item {
            Text(stringResource(R.string.keywords), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (analysis.matchedKeywords.isEmpty() && analysis.missingKeywords.isEmpty()) {
                Text(
                    stringResource(R.string.no_keyword_analysis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    analysis.matchedKeywords.forEach { KeywordChip(text = it, isMatched = true) }
                    analysis.missingKeywords.forEach { KeywordChip(text = it, isMatched = false) }
                }
            }
        }

        if (analysis.optimizationTips.isNotEmpty()) {
            item {
                Text(stringResource(R.string.improvement_suggestions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
            items(analysis.optimizationTips) { tip ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        tip.description,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            AivancePrimaryButton(
                text = stringResource(R.string.continue_to_optimization),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.AutoAwesome
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

// ── Step 5: AI Optimization ──────────────────────────────────────────────────

@Composable
private fun OptimizingStep(
    version: ResumeVersion,
    sectionInProgress: String?,
    streamingContent: String?,
    suggestions: Map<String, String>,
    onImprove: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onSave: (String) -> Unit
) {
    // Pre-fill with a date-stamped version name per spec: "v{n} — {date}".
    // v1 is the original import, so the first optimized save is v2.
    var versionName by remember {
        mutableStateOf("v2 — ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())}")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.optimize_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.optimize_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        itemsIndexed(version.sections) { _, section ->
            val suggestion = suggestions[section.title]
            val isImproving = sectionInProgress == section.title

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(section.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (isImproving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { onImprove(section.title) }) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = stringResource(R.string.improve_with_ai),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (isImproving) {
                        Spacer(Modifier.height(8.dp))
                        // Live token stream — the section's improved text as it arrives.
                        if (!streamingContent.isNullOrEmpty()) {
                            StreamingOptimizationText(streamingContent)
                        } else {
                            Text(
                                stringResource(R.string.improving_with_ai),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = section.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (suggestion != null) {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        stringResource(R.string.ai_suggestion),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    TypewriterText(suggestion)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AivancePrimaryButton(
                                            text = stringResource(R.string.accept),
                                            onClick = { onAccept(section.title) },
                                            icon = Icons.Rounded.Check
                                        )
                                        AivanceSecondaryButton(
                                            text = stringResource(R.string.discard),
                                            onClick = { onDiscard(section.title) },
                                            icon = Icons.Rounded.Close
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = versionName,
                onValueChange = { versionName = it },
                label = { Text(stringResource(R.string.version_name_optional)) },
                placeholder = {
                    Text(stringResource(R.string.version_name_placeholder, SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())))
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            AivancePrimaryButton(
                text = stringResource(R.string.save_version_continue),
                onClick = { onSave(versionName) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Save
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

/** Live streaming text with a blinking caret (real token stream). */
@Composable
private fun StreamingOptimizationText(content: String) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "optimizeCaret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(420),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "optimizeCaretAlpha"
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

/** Reveals text progressively to mimic streaming AI output. */
@Composable
private fun TypewriterText(text: String) {
    var visibleChars by remember { mutableStateOf(0) }
    LaunchedEffect(text) {
        visibleChars = 0
        while (visibleChars < text.length) {
            delay(12)
            visibleChars = (visibleChars + 3).coerceAtMost(text.length)
        }
    }
    Text(
        text = text.take(visibleChars),
        style = MaterialTheme.typography.bodyMedium
    )
}

// ── Step 6: Save ─────────────────────────────────────────────────────────────

@Composable
private fun SavingStep() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 5.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.saving_version), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.storing_version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Step 7: Export ───────────────────────────────────────────────────────────

@Composable
private fun ExportStep(
    versionName: String,
    onExportPdf: () -> Unit,
    onExportDocx: () -> Unit,
    onExportJson: () -> Unit,
    onDone: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp)) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.version_saved), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.version_ready_export, versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        AivancePrimaryButton(
            text = stringResource(R.string.export_as_pdf),
            onClick = onExportPdf,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.FileDownload
        )
        Spacer(Modifier.height(8.dp))
        AivanceSecondaryButton(
            text = stringResource(R.string.export_as_docx),
            onClick = onExportDocx,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        AivanceSecondaryButton(
            text = stringResource(R.string.export_as_json),
            onClick = onExportJson,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        AivancePrimaryButton(
            text = stringResource(R.string.done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(48.dp))
    }
}

// ── Error ────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorStep(
    step: String,
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.step_failed, step), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(24.dp))
        if (canRetry) {
            AivancePrimaryButton(text = stringResource(R.string.retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            AivanceSecondaryButton(text = stringResource(R.string.back), onClick = onBack, modifier = Modifier.fillMaxWidth())
        } else {
            // For ATS/Optimization/Save failures Retry would only step back, so
            // surface a single honest "Back" action instead.
            AivancePrimaryButton(text = stringResource(R.string.back), onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Shares a generated PDF/DOCX file via ACTION_SEND with the app's FileProvider. */
private fun shareResumeExportFile(context: android.content.Context, uri: Uri, mimeType: String) {
    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooserIntent = android.content.Intent.createChooser(shareIntent, context.getString(R.string.export_resume)).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        android.util.Log.e("ResumeEngine", "Failed to launch share chooser", e)
    }
}

/** Writes [text] to a cache file (wrapping it as HTML when it's a .doc so
 *  Word opens it correctly) and fires a share sheet. */
private fun shareResumeFile(context: android.content.Context, text: String, fileName: String) {
    val file = File(context.cacheDir, fileName)
    val content = if (fileName.endsWith(".doc", ignoreCase = true)) {
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body><pre>$text</pre></body></html>"
    } else {
        text
    }
    file.writeText(content)
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val mimeType = when {
        fileName.endsWith(".doc", ignoreCase = true) -> "application/msword"
        fileName.endsWith(".json", ignoreCase = true) -> "application/json"
        else -> "text/plain"
    }
    shareResumeExportFile(context, uri, mimeType)
}


