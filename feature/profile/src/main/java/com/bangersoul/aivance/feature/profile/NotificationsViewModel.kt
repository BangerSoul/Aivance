package com.bangersoul.aivance.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.NotificationItem
import com.bangersoul.aivance.core.common.model.NotificationType
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.datastore.UserPreferencesRepository
import com.bangersoul.aivance.core.domain.repository.NotificationRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState
    data class Success(
        val notifications: List<NotificationItem> = emptyList(),
        val unreadCount: Int = 0,
        val jobAlertsEnabled: Boolean = true,
        val followUpRemindersEnabled: Boolean = true
    ) : NotificationsUiState
    data object Empty : NotificationsUiState
    data class Error(val message: String) : NotificationsUiState
}

sealed interface NotificationsUiEvent {
    data class MarkAsRead(val id: String) : NotificationsUiEvent
    data object MarkAllAsRead : NotificationsUiEvent
    data class ToggleJobAlerts(val enabled: Boolean) : NotificationsUiEvent
    data class ToggleFollowUpReminders(val enabled: Boolean) : NotificationsUiEvent
    data class DeleteNotification(val id: String) : NotificationsUiEvent
    data object Refresh : NotificationsUiEvent
}

sealed interface NotificationsUiEffect {
    data class ShowSnackbar(val message: String) : NotificationsUiEffect
    data class NavigateToTarget(val targetId: String) : NotificationsUiEffect
    data object RequestNotificationPermission : NotificationsUiEffect
}

/**
 * Backed by the persisted notifications inbox (Room v29) — workers and pipeline
 * events record entries through [NotificationRepository], and this ViewModel
 * observes that table reactively. Notification preferences (job alerts /
 * follow-up reminders) live in DataStore via [UserPreferencesRepository], the
 * same toggles the background workers honor.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val trackEventUseCase: TrackEventUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<NotificationsUiEffect>(Channel.BUFFERED)
    val effects: Flow<NotificationsUiEffect> = _effects.receiveAsFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        loadNotifications()
    }

    fun onEvent(event: NotificationsUiEvent) {
        when (event) {
            is NotificationsUiEvent.MarkAsRead -> markAsRead(event.id)
            NotificationsUiEvent.MarkAllAsRead -> markAllAsRead()
            is NotificationsUiEvent.ToggleJobAlerts -> toggleJobAlerts(event.enabled)
            is NotificationsUiEvent.ToggleFollowUpReminders -> toggleFollowUpReminders(event.enabled)
            is NotificationsUiEvent.DeleteNotification -> delete(event.id)
            NotificationsUiEvent.Refresh -> loadNotifications()
        }
    }

    /**
     * Subscribes to the persisted inbox and the user's notification preferences.
     * Room re-emits on every producer write (a worker firing in the background
     * updates this screen live), so Refresh only needs to re-subscribe.
     */
    private fun loadNotifications() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            trackEventUseCase(TrackEventRequest(eventName = "notifications_load"))

            combine(
                notificationRepository.getNotifications(),
                userPreferencesRepository.userPreferences
            ) { notifications, preferences ->
                val state: NotificationsUiState = if (notifications.isEmpty()) {
                    NotificationsUiState.Empty
                } else {
                    NotificationsUiState.Success(
                        notifications = notifications,
                        unreadCount = notifications.count { !it.isRead },
                        jobAlertsEnabled = preferences.jobAlertsEnabled,
                        followUpRemindersEnabled = preferences.followUpRemindersEnabled
                    )
                }
                state
            }
                .catch { throwable ->
                    emit(
                        NotificationsUiState.Error(
                            throwable.message ?: "Couldn't load notifications"
                        )
                    )
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    private fun markAsRead(id: String) {
        viewModelScope.launch {
            when (val result = notificationRepository.markAsRead(id)) {
                is Result.Success -> Unit // Row update re-emits through the observe flow.
                is Result.Failure -> _effects.send(
                    NotificationsUiEffect.ShowSnackbar(result.error.message)
                )
            }
        }
    }

    private fun markAllAsRead() {
        viewModelScope.launch {
            when (val result = notificationRepository.markAllAsRead()) {
                is Result.Success ->
                    _effects.send(NotificationsUiEffect.ShowSnackbar("All marked as read"))
                is Result.Failure -> _effects.send(
                    NotificationsUiEffect.ShowSnackbar(result.error.message)
                )
            }
        }
    }

    private fun toggleJobAlerts(enabled: Boolean) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest(eventName = "notifications_toggle_job_alerts_$enabled"))
            userPreferencesRepository.updateJobAlertsEnabled(enabled)
        }
    }

    private fun toggleFollowUpReminders(enabled: Boolean) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest(eventName = "notifications_toggle_followup_$enabled"))
            userPreferencesRepository.updateFollowUpRemindersEnabled(enabled)
        }
    }

    private fun delete(id: String) {
        viewModelScope.launch {
            when (val result = notificationRepository.delete(id)) {
                is Result.Success -> Unit
                is Result.Failure -> _effects.send(
                    NotificationsUiEffect.ShowSnackbar(result.error.message)
                )
            }
        }
    }
}
