package com.bangersoul.aivance.feature.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.designsystem.components.AivanceScreen
import com.bangersoul.aivance.core.designsystem.components.DashboardCard
import com.bangersoul.aivance.core.designsystem.components.MetricChip
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import com.bangersoul.aivance.core.designsystem.theme.Zinc800

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedJobsScreen(
    viewModel: SavedJobsViewModel,
    onBack: () -> Unit = {},
    onJobClick: (String) -> Unit = {},
    onCreateResume: (JobListing) -> Unit = {},
    onTrackApplication: (JobListing) -> Unit = {},
    onAssistantForJob: (JobListing) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AivanceScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.saved_jobs_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        isLoading = uiState is SavedJobsUiState.Loading,
        error = (uiState as? SavedJobsUiState.Error)?.message,
        isEmpty = uiState is SavedJobsUiState.Empty,
        emptyTitle = stringResource(R.string.no_saved_jobs),
        emptyDescription = stringResource(R.string.no_saved_jobs_desc)
    ) {
        when (val state = uiState) {
            is SavedJobsUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.jobs, key = { it.id }) { job ->
                        SavedJobItem(
                            job = job,
                            onRemove = { viewModel.onEvent(SavedJobsUiEvent.RemoveJob(job.id)) },
                            onViewDetails = { onJobClick(job.id) },
                            onCreateResume = { onCreateResume(job) },
                            onTrackApplication = { onTrackApplication(job) },
                            onAskAssistant = { onAssistantForJob(job) }
                        )
                    }
                }
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedJobItem(
    job: JobListing,
    onRemove: () -> Unit,
    onViewDetails: () -> Unit,
    onCreateResume: () -> Unit = {},
    onTrackApplication: () -> Unit = {},
    onAskAssistant: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    DashboardCard(modifier = modifier.fillMaxWidth(), onClick = onViewDetails) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = job.company,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetricChip(label = job.location)
                        if (job.isRemote) MetricChip(label = stringResource(R.string.remote))
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.remove),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Interlinking actions: tailor a resume for this job, track it in
            // the pipeline, or ask the assistant with this job as context.
            // FlowRow so the chips wrap gracefully on narrow screens.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChip(
                    text = stringResource(R.string.create_tailored_resume),
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = onCreateResume
                )
                ActionChip(
                    text = stringResource(R.string.track_application),
                    icon = Icons.Rounded.PlaylistAdd,
                    onClick = onTrackApplication
                )
                ActionChip(
                    text = stringResource(R.string.ask_ai),
                    icon = Icons.Rounded.SmartToy,
                    onClick = onAskAssistant
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(text, maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SavedJobsScreenPreview() {
    AivanceTheme(darkTheme = true) {
        SavedJobsScreen(
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        )
    }
}
