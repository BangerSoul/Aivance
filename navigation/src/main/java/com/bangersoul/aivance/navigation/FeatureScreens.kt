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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.designsystem.components.AivanceEmptyState
import com.bangersoul.aivance.core.designsystem.components.AivanceScreen
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import com.bangersoul.aivance.core.common.model.NotificationItem
import com.bangersoul.aivance.feature.profile.NotificationsUiEvent
import com.bangersoul.aivance.feature.profile.NotificationsUiState
import com.bangersoul.aivance.feature.profile.NotificationsViewModel
import com.bangersoul.aivance.core.common.model.NotificationType


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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

