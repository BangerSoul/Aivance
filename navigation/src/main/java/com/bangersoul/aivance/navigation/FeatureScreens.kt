package com.bangersoul.aivance.navigation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.bangersoul.aivance.core.designsystem.components.AivanceEmptyState
import com.bangersoul.aivance.core.designsystem.components.AivancePrimaryButton
import com.bangersoul.aivance.core.designsystem.components.AivanceScreen
import com.bangersoul.aivance.core.designsystem.components.AivanceSecondaryButton
import com.bangersoul.aivance.core.designsystem.components.BannerTone
import com.bangersoul.aivance.core.designsystem.components.StatusChip
import com.bangersoul.aivance.sdk.core.ConfigField
import com.bangersoul.aivance.sdk.core.FieldType
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import com.bangersoul.aivance.feature.profile.ProviderCategory
import com.bangersoul.aivance.feature.profile.ProviderHealthStatus
import com.bangersoul.aivance.feature.profile.ProviderManagementUiEvent
import com.bangersoul.aivance.feature.profile.ProviderManagementUiState
import com.bangersoul.aivance.feature.profile.NotificationItem
import com.bangersoul.aivance.feature.profile.NotificationsUiEvent
import com.bangersoul.aivance.feature.profile.NotificationsUiState
import com.bangersoul.aivance.feature.profile.NotificationsViewModel
import com.bangersoul.aivance.feature.profile.NotificationType
import com.bangersoul.aivance.feature.profile.ProviderManagementViewModel
import java.util.Locale


// ──────────────────────────────────────────────────
// Provider Management Screen
// ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderManagementScreen(
    viewModel: ProviderManagementViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is com.bangersoul.aivance.feature.profile.ProviderManagementUiEffect.ShowSnackbar ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is com.bangersoul.aivance.feature.profile.ProviderManagementUiEffect.ConnectionTestResult ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    AivanceScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.providers), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(ProviderManagementUiEvent.Refresh) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh_providers))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) {
        when (val state = uiState) {
            is ProviderManagementUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.loading_providers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is ProviderManagementUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    AivancePrimaryButton(
                        text = stringResource(R.string.retry),
                        onClick = { viewModel.onEvent(ProviderManagementUiEvent.Refresh) }
                    )
                }
            }
            is ProviderManagementUiState.Success -> {
                ProviderManagementList(
                    state = state,
                    onEvent = viewModel::onEvent
                )
                state.modelDownloadDialog?.let { dialog ->
                    ModelDownloadConfirmationDialog(
                        dialog = dialog,
                        onConfirm = { useCompact ->
                            viewModel.onEvent(
                                ProviderManagementUiEvent.ConfirmModelDownload(dialog.providerId, useCompact)
                            )
                        },
                        onDismiss = { viewModel.onEvent(ProviderManagementUiEvent.DismissModelDownloadDialog) }
                    )
                }
            }
        }
    }
}

/** Formats a byte count for display, e.g. `3.0 GB` or `271 MB`. */
private fun formatBytes(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    val mib = bytes / (1024.0 * 1024.0)
    return if (gib >= 1.0) {
        String.format(Locale.US, "%.1f GB", gib)
    } else {
        String.format(Locale.US, "%.0f MB", mib)
    }
}

@Composable
private fun ModelDownloadConfirmationDialog(
    dialog: com.bangersoul.aivance.feature.profile.ModelDownloadDialog,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_model_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.download_model_size, formatBytes(dialog.modelSizeBytes), dialog.modelSizeBytes),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.download_model_free_storage, formatBytes(dialog.freeStorageBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dialog.ramWarning) {
                    Text(
                        stringResource(R.string.download_model_ram_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (dialog.storageBlocked) {
                    Text(
                        stringResource(R.string.download_model_storage_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (dialog.offersCompact && dialog.compactName != null) {
                    Surface(
                        shape = AivanceTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.download_model_compact_label,
                                    formatBytes(dialog.compactSizeBytes)
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = stringResource(R.string.download_model_compact_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Storage-blocked devices cannot fit the full model — offer only the compact one.
                if (!dialog.storageBlocked) {
                    TextButton(onClick = { onConfirm(false) }) {
                        Text(stringResource(R.string.download_model_confirm))
                    }
                }
                if (dialog.offersCompact) {
                    TextButton(onClick = { onConfirm(true) }) {
                        Text(stringResource(R.string.download_model_compact_label, formatBytes(dialog.compactSizeBytes)))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ProviderManagementList(
    state: ProviderManagementUiState.Success,
    onEvent: (ProviderManagementUiEvent) -> Unit
) {
    val aiProviders = state.providers.filter { it.category == ProviderCategory.AI }
    val jobProviders = state.providers.filter { it.category == ProviderCategory.JOB }
    val enrichmentProviders = state.providers.filter { it.category == ProviderCategory.ENRICHMENT }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionLabel(stringResource(R.string.section_ai_providers)) }
        items(aiProviders, key = { it.id }) { provider ->
            ProviderCard(provider, state, onEvent)
        }
        if (jobProviders.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.section_job_providers)) }
            items(jobProviders, key = { it.id }) { provider ->
                ProviderCard(provider, state, onEvent)
            }
        }
        if (enrichmentProviders.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.section_enrichment_providers)) }
            items(enrichmentProviders, key = { it.id }) { provider ->
                ProviderCard(provider, state, onEvent)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ProviderCard(
    provider: com.bangersoul.aivance.feature.profile.ProviderInfo,
    state: ProviderManagementUiState.Success,
    onEvent: (ProviderManagementUiEvent) -> Unit
) {
    val credentialDrafts = state.credentialDrafts[provider.id].orEmpty()
    var modelMenuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AivanceTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (provider.description.isNotBlank()) {
                        Text(
                            provider.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    // Masked credential preview — never the full key.
                    if (provider.apiKeyConfigured && provider.maskedApiKey.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Key,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = provider.maskedApiKey,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                ProviderHealthChip(provider.healthStatus)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (provider.isEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (provider.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = { onEvent(ProviderManagementUiEvent.ToggleProvider(provider.id, it)) }
                )
            }

            if (provider.isOnDevice) {
                // Keyless on-device provider (e.g. Gemma): a model download replaces the API key.
                val isDownloading = state.downloadingProviderId == provider.id
                if (provider.modelDownloaded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Green "Downloaded" status: the model file is present and
                        // the provider is usable fully offline.
                        Surface(
                            shape = AivanceTheme.shapes.small,
                            color = AivanceTheme.colors.successContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = AivanceTheme.colors.onSuccessContainer
                                )
                                Text(
                                    stringResource(R.string.model_downloaded),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AivanceTheme.colors.onSuccessContainer
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        AivanceSecondaryButton(
                            text = stringResource(R.string.delete_model),
                            onClick = { onEvent(ProviderManagementUiEvent.DeleteModel(provider.id)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            stringResource(
                                R.string.downloading_model,
                                ((state.modelDownloadProgress ?: 0f) * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { state.modelDownloadProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            stringResource(R.string.model_not_downloaded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AivancePrimaryButton(
                            text = stringResource(R.string.download_model),
                            onClick = { onEvent(ProviderManagementUiEvent.DownloadModel(provider.id)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (provider.configFields.isNotEmpty()) {
                // Metadata-driven credential form: renders every ConfigField the
                // provider declares (e.g. Adzuna = App ID + API Key, USAJobs =
                // API Key), routing sensitive fields to encrypted secrets on save.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    provider.configFields.forEach { field ->
                        CredentialField(
                            field = field,
                            value = credentialDrafts[field.key].orEmpty(),
                            onValueChange = { onEvent(ProviderManagementUiEvent.SetCredential(provider.id, field.key, it)) }
                        )
                    }
                }
            }

            if (provider.availableModels.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.model), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.3f))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { modelMenuOpen = true },
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text(
                            provider.selectedModel.ifBlank { stringResource(R.string.select) },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false }
                    ) {
                        provider.availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    modelMenuOpen = false
                                    onEvent(ProviderManagementUiEvent.SelectModel(provider.id, model))
                                }
                            )
                        }
                    }
                }
            }

            // Keyless on-device providers need no credentials: Save/Test are
            // meaningless (Test would fail with "Configuration is incomplete"),
            // so the download/delete controls above are their only actions.
            if (!provider.isOnDevice) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AivanceSecondaryButton(
                        text = stringResource(R.string.save),
                        onClick = { onEvent(ProviderManagementUiEvent.SaveProvider(provider.id)) },
                        modifier = Modifier.weight(1f)
                    )
                    AivancePrimaryButton(
                        text = if (state.testingProviderId == provider.id) stringResource(R.string.testing) else stringResource(R.string.test),
                        onClick = { onEvent(ProviderManagementUiEvent.TestConnection(provider.id)) },
                        modifier = Modifier.weight(1f),
                        enabled = state.testingProviderId != provider.id
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialField(
    field: ConfigField,
    value: String,
    onValueChange: (String) -> Unit
) {
    val isPassword = field.fieldType == FieldType.PASSWORD
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(field.label) },
        placeholder = { field.hint?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None
        ),
        singleLine = true
    )
}

@Composable
private fun ProviderHealthChip(status: ProviderHealthStatus) {
    val (tone, labelRes) = when (status) {
        ProviderHealthStatus.HEALTHY -> BannerTone.SUCCESS to R.string.healthy
        ProviderHealthStatus.DEGRADED -> BannerTone.WARNING to R.string.degraded
        ProviderHealthStatus.UNHEALTHY -> BannerTone.ERROR to R.string.unhealthy
        ProviderHealthStatus.UNKNOWN -> BannerTone.INFO to R.string.unknown
    }
    StatusChip(text = stringResource(labelRes), tone = tone)
}

// ──────────────────────────────────────────────────
// Notifications Screen
// ──────────────────────────────────────────────────

/** Icon + tint for a notification's type. */
@Composable
private fun NotificationType.visual(): Pair<ImageVector, Color> = when (this) {
    NotificationType.APPLICATION_UPDATE -> Icons.Rounded.Send to AivanceTheme.colors.info
    NotificationType.INTERVIEW_REMINDER -> Icons.Rounded.RecordVoiceOver to AivanceTheme.colors.warning
    NotificationType.JOB_ALERT -> Icons.Rounded.WorkOutline to AivanceTheme.colors.success
    NotificationType.ROADMAP_MILESTONE -> Icons.Rounded.MilitaryTech to AivanceTheme.colors.accent
    NotificationType.GENERAL -> Icons.Rounded.Notifications to MaterialTheme.colorScheme.primary
}

/** Timestamp formatted for the list row: today shows time only, otherwise "Sep 26, 14:05". */
private fun formatNotificationTime(timestamp: Long): String {
    val local = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
    val timeOnly = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val dateAndTime = java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm")
    return if (local.toLocalDate() == java.time.LocalDate.now()) {
        local.format(timeOnly)
    } else {
        local.format(dateAndTime)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    AivanceScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val state = uiState as? NotificationsUiState.Success
                    if (state != null && state.unreadCount > 0) {
                        TextButton(onClick = { viewModel.onEvent(NotificationsUiEvent.MarkAllAsRead) }) {
                            Text(stringResource(R.string.mark_all_read))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) {
        when (val state = uiState) {
            is NotificationsUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is NotificationsUiState.Error -> AivanceEmptyState(
                title = stringResource(R.string.notifications_load_failed),
                description = state.message,
                icon = Icons.Rounded.ErrorOutline,
                iconTint = MaterialTheme.colorScheme.error,
                primaryActionText = stringResource(R.string.retry),
                onPrimaryAction = { viewModel.onEvent(NotificationsUiEvent.Refresh) }
            )

            is NotificationsUiState.Empty,
            is NotificationsUiState.Success -> {
                val notifications = (state as? NotificationsUiState.Success)?.notifications.orEmpty()
                if (notifications.isEmpty()) {
                    AivanceEmptyState(
                        title = stringResource(R.string.no_notifications),
                        description = stringResource(R.string.no_notifications_desc),
                        icon = Icons.Rounded.Notifications
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications, key = { it.id }) { notification ->
                            NotificationRow(
                                notification = notification,
                                onClick = {
                                    if (!notification.isRead) {
                                        viewModel.onEvent(NotificationsUiEvent.MarkAsRead(notification.id))
                                    }
                                },
                                onDismiss = {
                                    viewModel.onEvent(NotificationsUiEvent.DeleteNotification(notification.id))
                                }
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationRow(
    notification: NotificationItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val (icon, tint) = notification.type.visual()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = AivanceTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f)) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                    tint = tint
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold
                )
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatNotificationTime(notification.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.dismiss_notification),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

