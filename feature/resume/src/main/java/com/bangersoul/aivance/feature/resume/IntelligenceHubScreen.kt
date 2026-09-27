package com.bangersoul.aivance.feature.resume

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IntelligenceHubScreen(
    viewModel: IntelligenceHubViewModel,
    onNavigateToEngine: () -> Unit,
    onNavigateToAts: (Long) -> Unit,
    onBack: (() -> Unit)?
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<AtsScanItem?>(null) }

    pendingDelete?.let { scan ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ATS report?") },
            text = {
                Text(
                    "\"${scan.title}\" (${scan.report.overallScore}% Match) will be removed permanently."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReport(scan.report.id)
                    pendingDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    AivanceWorkspaceScaffold(
        title = "Intelligence Hub",
        subtitle = "Manage your career product",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToEngine,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Resume")
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(title = "Your Resumes")
                Spacer(Modifier.height(8.dp))
            }
            if (uiState.resumes.isEmpty()) {
                item {
                    AivanceEmptyState(
                        title = "No resumes yet",
                        description = "Tap + to import a PDF or DOCX and build your first resume.",
                        icon = Icons.Rounded.Description
                    )
                }
            } else {
                // LazyColumn keys share one namespace — namespace by type so a
                // resume and a scan with the same numeric id never collide.
                items(uiState.resumes, key = { "resume-${it.id}" }) { resume ->
                    ResumeCard(resume = resume, onClick = onNavigateToEngine)
                }
            }

            item {
                SectionHeader(title = "Recent ATS Scans")
                Spacer(Modifier.height(8.dp))
            }
            if (uiState.atsScans.isEmpty()) {
                item {
                    AivanceEmptyState(
                        title = "No ATS scans yet",
                        description = "Run an ATS scan from the Resume Engine to see your match history.",
                        icon = Icons.Rounded.History
                    )
                }
            } else {
                items(uiState.atsScans, key = { "scan-${it.report.id}" }) { scan ->
                    AtsScanCard(
                        scan = scan,
                        onClick = { onNavigateToAts(scan.report.id) },
                        onDelete = { pendingDelete = scan }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResumeCard(resume: Resume, onClick: () -> Unit) {
    AivanceWorkspaceCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = AivanceTheme.colors.accent)
            Column {
                Text(resume.name, fontWeight = FontWeight.Bold)
                Text(
                    "Last updated ${formatRelativeTime(resume.lastModified)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AtsScanCard(scan: AtsScanItem, onClick: () -> Unit, onDelete: () -> Unit) {
    AivanceWorkspaceCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = AivanceTheme.colors.info)
            Column(Modifier.weight(1f)) {
                Text(scan.title, fontWeight = FontWeight.Bold)
                Text(
                    "${scan.report.overallScore}% Match · ${formatReportDate(scan.report.dateGenerated)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete ATS report",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/** Card title: company - job title when resolved, else a neutral label. */
private val AtsScanItem.title: String
    get() = when {
        jobTitle != null && companyName != null -> "${companyName} - ${jobTitle}"
        jobTitle != null -> jobTitle
        else -> "ATS Scan"
    }

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMinutes = ((now - timestamp) / 60_000L).coerceAtLeast(0)
    return when {
        diffMinutes < 1 -> "just now"
        diffMinutes < 60 -> "$diffMinutes min ago"
        diffMinutes < 24 * 60 -> "${diffMinutes / 60} hr ago"
        diffMinutes < 7 * 24 * 60 -> "${diffMinutes / (24 * 60)} days ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatReportDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
}
