package com.bangersoul.aivance.feature.ats

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PriorityHigh
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
import androidx.core.content.FileProvider
import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.designsystem.components.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtsScreen(
    viewModel: AtsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCoverLetter: () -> Unit = {},
    initialJobDescription: String? = null,
    initialReportId: Long? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resumes by viewModel.resumes.collectAsStateWithLifecycle()
    val jdText by viewModel.jdText.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialJobDescription) {
        if (!initialJobDescription.isNullOrBlank()) {
            viewModel.onEvent(AtsUiEvent.UpdateJobDescription(initialJobDescription))
        }
    }

    LaunchedEffect(initialReportId) {
        if (initialReportId != null) {
            viewModel.loadReport(initialReportId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AtsUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is AtsUiEffect.NavigateToCoverLetter -> onNavigateToCoverLetter()
                is AtsUiEffect.ExportReport -> shareReport(context, effect.text)
            }
        }
    }

    AivanceWorkspaceScaffold(
        title = stringResource(R.string.ats_intelligence_title),
        subtitle = "Match analysis",
        onBack = onNavigateBack,
        isLoading = uiState is AtsUiState.Analyzing,
        error = (uiState as? AtsUiState.Error)?.message,
        onRetry = { viewModel.onEvent(AtsUiEvent.Reset) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) {
        Box(Modifier.fillMaxSize()) {
            // Key the transition on the *step* (the state class), not the state
            // object: AtsUiState.Analyzing.streamingText grows as tokens arrive,
            // so keying on the object made AnimatedContent tear down and re-fade
            // the whole subtree on every single token. The content below still
            // reads `uiState` directly, so streaming text keeps updating.
            AnimatedContent(
                targetState = uiState::class,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "AtsStateTransition"
            ) {
                when (val state = uiState) {
                    AtsUiState.SelectingResume -> ResumeSelectionStep(
                        resumes = resumes,
                        onSelect = { r, v -> viewModel.onEvent(AtsUiEvent.SelectResumeVersion(r, v)) }
                    )
                    is AtsUiState.InputJobDescription -> JobDescriptionInputStep(
                        jdText = jdText,
                        resumeName = state.resume.name,
                        versionName = state.selectedVersion.versionName,
                        onJdTextChange = { viewModel.onEvent(AtsUiEvent.UpdateJobDescription(it)) },
                        onAnalyze = { viewModel.onEvent(AtsUiEvent.Analyze(it)) },
                        onBack = { viewModel.onEvent(AtsUiEvent.Reset) }
                    )
                    is AtsUiState.Analyzing -> AtsAnalyzingContent(streamingText = state.streamingText)
                    is AtsUiState.DisplayReport -> AtsReportContent(
                        report = state.report,
                        onNewScan = { viewModel.onEvent(AtsUiEvent.Reset) },
                        onGenerateCoverLetter = { viewModel.onEvent(AtsUiEvent.GenerateCoverLetter) },
                        onExportReport = { viewModel.onEvent(AtsUiEvent.ExportReport) }
                    )
                    else -> {}
                }
            }
        }
    }
}

/**
 * Live streaming preview while the AI runs the analysis. Shows the raw tokens
 * as they arrive with a pulsing caret, then the report card replaces it on
 * completion.
 */
@Composable
private fun AtsAnalyzingContent(streamingText: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.analyzing_match), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.analyzing_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DashboardCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = streamingText.ifEmpty { stringResource(R.string.waiting_response) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/** Writes [text] to a cache file and fires a share sheet so the report can be exported. */
private fun shareReport(context: Context, text: String) {
    val file = File(context.cacheDir, "ats_report.txt")
    file.writeText(text)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (shareIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_ats_report)))
    }
}

@Composable
private fun ResumeSelectionStep(
    resumes: List<Resume>,
    onSelect: (Resume, ResumeVersion) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.select_resume_analyze), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (resumes.isEmpty()) {
            Text(
                stringResource(R.string.import_resume_first),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(resumes, key = { it.id }) { resume ->
                    resume.versions.forEach { version ->
                        Card(
                            onClick = { onSelect(resume, version) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(resume.name, fontWeight = FontWeight.Bold)
                                    Text(version.versionName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobDescriptionInputStep(
    jdText: String,
    resumeName: String,
    versionName: String,
    onJdTextChange: (String) -> Unit,
    onAnalyze: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.target_job), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.analyzing_against, resumeName, versionName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = jdText,
            onValueChange = onJdTextChange,
            label = { Text(stringResource(R.string.paste_job_description)) },
            modifier = Modifier.fillMaxWidth().height(300.dp),
            placeholder = { Text(stringResource(R.string.jd_placeholder)) }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.auto_score_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        ActionButton(
            text = stringResource(R.string.start_match_analysis),
            onClick = { onAnalyze(jdText) },
            modifier = Modifier.fillMaxWidth(),
            enabled = jdText.length > 50
        )
        ActionButton(
            text = stringResource(R.string.back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AtsReportContent(
    report: AtsReport,
    onNewScan: () -> Unit,
    onGenerateCoverLetter: () -> Unit,
    onExportReport: () -> Unit
) {
    var expandedSections by remember { mutableStateOf<Set<String>>(emptySet()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.match_report), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onExportReport) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = stringResource(R.string.export_report))
                    }
                    ActionButton(
                        text = stringResource(R.string.new_scan),
                        onClick = onNewScan,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            DashboardCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ScoreGauge(score = report.overallScore, size = 100.dp)
                    Column {
                        Text(stringResource(R.string.overall_ats_score), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.match_probability, report.matchPercentage), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Text(stringResource(R.string.section_scores), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            report.sectionScores.forEach { (name, score) ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.labelLarge)
                        Text("$score%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (score > 70) Color(0xFF4CAF50) else if (score > 40) Color(0xFFFFC107) else Color(0xFFF44336),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        item {
            Text(stringResource(R.string.keyword_gap_analysis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            // Chunked Row wrap instead of the experimental FlowRow API: the
            // app's runtime foundation (forced to 1.11.x by Coil 3.5) ships a
            // different compiled FlowRow signature than the module's BOM (1.7),
            // which made the report screen crash with NoSuchMethodError.
            val keywordChips = report.matchedKeywords.map { it to true } +
                report.missingKeywords.map { it to false }
            if (keywordChips.isEmpty()) {
                Text(
                    stringResource(R.string.no_keywords_identified),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            keywordChips.chunked(2).forEach { rowChips ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowChips.forEach { (text, matched) ->
                        KeywordChip(
                            text = text,
                            isMatched = matched,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }

        item {
            Text(stringResource(R.string.optimization_suggestions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
        }

        // Group tips by category so each category is an expandable accordion section.
        val grouped = report.optimizationTips.groupBy { it.category }
        grouped.forEach { (category, tips) ->
            item {
                val expanded = category in expandedSections
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSections = if (expanded) expandedSections - category else expandedSections + category }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = if (tips.any { it.priority == "HIGH" }) Icons.Rounded.PriorityHigh else Icons.Rounded.Lightbulb,
                                    contentDescription = null,
                                    tint = if (tips.any { it.priority == "HIGH" }) Color(0xFFF44336) else Color(0xFFFFC107)
                                )
                                Text(category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(
                                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (expanded) {
                            tips.forEach { tip ->
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(tip.description, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
            ActionButton(
                text = stringResource(R.string.generate_tailored_cover_letter),
                onClick = onGenerateCoverLetter,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.HistoryEdu
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}
