package com.bangersoul.aivance.feature.recruiter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.common.model.Recruiter
import com.bangersoul.aivance.core.designsystem.components.ActionButton
import com.bangersoul.aivance.core.designsystem.components.AivanceEmptyState
import com.bangersoul.aivance.core.designsystem.components.AivanceScreen
import com.bangersoul.aivance.core.designsystem.components.DashboardCard
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import androidx.compose.material.icons.rounded.PersonSearch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecruiterDashboardScreen(
    viewModel: RecruiterViewModel,
    jobId: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The custom back stack passes the destination's job ID here directly (it does
    // not populate SavedStateHandle), so drive the load from the destination arg.
    LaunchedEffect(jobId) {
        viewModel.load(jobId)
    }

    AivanceScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recruiter_intelligence_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        isLoading = uiState is RecruiterUiState.Loading,
        error = (uiState as? RecruiterUiState.Error)?.message
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "RecruiterTransition"
        ) { state ->
            when (state) {
                is RecruiterUiState.Success -> {
                    if (state.recruiters.isEmpty()) {
                        AivanceEmptyState(
                            title = stringResource(R.string.no_recruiters),
                            description = stringResource(R.string.no_recruiters_desc),
                            icon = Icons.Rounded.PersonSearch
                        )
                    } else {
                        RecruiterListContent(
                            state = state,
                            onSelect = { viewModel.onEvent(RecruiterUiEvent.SelectRecruiter(it)) },
                            onGenerate = { viewModel.onEvent(RecruiterUiEvent.GenerateOutreach(it)) }
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun RecruiterListContent(
    state: RecruiterUiState.Success,
    onSelect: (Recruiter) -> Unit,
    onGenerate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.verified_contacts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.recruiters, key = { it.id }) { recruiter ->
                Card(
                    onClick = { onSelect(recruiter) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.selectedRecruiter?.id == recruiter.id)
                            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Person, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(recruiter.name, fontWeight = FontWeight.Bold)
                            Text(recruiter.title ?: stringResource(R.string.recruiter_fallback), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (state.selectedRecruiter != null) {
            Spacer(Modifier.height(24.dp))
            OutreachSection(
                recruiter = state.selectedRecruiter,
                isGenerating = state.isGenerating,
                draftContent = state.draft?.content,
                outreachError = state.outreachError,
                onGenerate = onGenerate
            )
        }
    }
}

@Composable
private fun OutreachSection(
    recruiter: Recruiter,
    isGenerating: Boolean,
    draftContent: String?,
    outreachError: String?,
    onGenerate: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.personalized_outreach), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = stringResource(R.string.cold_email),
                onClick = { onGenerate("COLD_EMAIL") },
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Email
            )
            ActionButton(
                text = stringResource(R.string.linkedin),
                onClick = { onGenerate("LINKEDIN_REQUEST") },
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.AutoAwesome
            )
        }

        if (isGenerating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }

        if (outreachError != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = outreachError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (draftContent != null) {
            Spacer(Modifier.height(16.dp))
            DashboardCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.ai_draft_for, recruiter.name), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(draftContent, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { clipboard.setText(AnnotatedString(draftContent)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.copy_draft))
                        }
                        Button(
                            onClick = { onGenerate("COLD_EMAIL") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(stringResource(R.string.regenerate), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}
