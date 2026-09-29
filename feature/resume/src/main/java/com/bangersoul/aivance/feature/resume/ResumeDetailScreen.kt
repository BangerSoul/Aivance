package com.bangersoul.aivance.feature.resume

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.designsystem.components.AivanceEmptyState
import com.bangersoul.aivance.core.designsystem.components.AivanceError
import com.bangersoul.aivance.core.designsystem.components.AivanceScreen
import com.bangersoul.aivance.core.designsystem.components.SkeletonDashboard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeDetailScreen(
    viewModel: ResumeDetailViewModel,
    resumeId: Long,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(resumeId) {
        viewModel.load(resumeId)
    }

    AivanceScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.resume_detail_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ResumeDetailTransition"
        ) { state ->
            when (state) {
                is ResumeDetailUiState.Loading -> SkeletonDashboard(modifier = Modifier.fillMaxSize())
                is ResumeDetailUiState.Error -> AivanceError(
                    message = state.message,
                    onRetry = { viewModel.load(resumeId) }
                )
                is ResumeDetailUiState.Success -> {
                    val resume = state.resume
                    if (resume == null) {
                        AivanceEmptyState(
                            title = stringResource(R.string.resume_not_found),
                            description = stringResource(R.string.resume_not_found_desc),
                            icon = Icons.Rounded.Description
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Icon(
                                                Icons.Rounded.Description,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Column {
                                                Text(resume.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                                resume.fileName?.let {
                                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            stringResource(R.string.created_modified, formatDate(resume.dateCreated), formatDate(resume.lastModified)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            item {
                                Text(stringResource(R.string.versions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            if (state.versions.isEmpty()) {
                                item {
                                    AivanceEmptyState(
                                        title = stringResource(R.string.no_versions),
                                        description = stringResource(R.string.no_versions_desc),
                                        icon = Icons.Rounded.History,
                                        compact = true
                                    )
                                }
                            } else {
                                items(state.versions, key = { it.id }) { version ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(version.versionName, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    stringResource(R.string.modified, formatDate(version.lastModified)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (version.id == resume.primaryVersionId) {
                                                Text(
                                                    stringResource(R.string.primary),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
