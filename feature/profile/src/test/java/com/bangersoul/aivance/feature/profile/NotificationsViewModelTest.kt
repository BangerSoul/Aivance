package com.bangersoul.aivance.feature.profile

import app.cash.turbine.test
import com.bangersoul.aivance.core.common.model.NotificationItem
import com.bangersoul.aivance.core.common.model.NotificationType
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.datastore.UserPreferences
import com.bangersoul.aivance.core.datastore.UserPreferencesRepository
import com.bangersoul.aivance.core.domain.repository.NotificationRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository: NotificationRepository = mockk()
    private val mockUserPreferences: UserPreferencesRepository = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()

    private val preferencesFlow = MutableStateFlow(UserPreferences())

    private fun notification(
        id: String,
        isRead: Boolean = false,
        type: NotificationType = NotificationType.APPLICATION_UPDATE
    ) = NotificationItem(
        id = id,
        title = "Title $id",
        message = "Message $id",
        timestamp = 1_000L,
        isRead = isRead,
        type = type
    )

    private fun createViewModel() =
        NotificationsViewModel(mockRepository, mockUserPreferences, mockTrackEvent)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockTrackEvent(any()) } returns Result.Success(Unit)
        every { mockUserPreferences.userPreferences } returns preferencesFlow
        // Default: empty inbox. Individual tests override with their own flow.
        every { mockRepository.getNotifications() } returns flowOf(emptyList())
        every { mockRepository.observeUnreadCount() } returns flowOf(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty inbox emits Empty state`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value is NotificationsUiState.Empty)
    }

    @Test
    fun `initial state surfaces notifications with real preference values`() = runTest {
        preferencesFlow.value = UserPreferences(
            jobAlertsEnabled = false,
            followUpRemindersEnabled = false
        )
        every { mockRepository.getNotifications() } returns flowOf(
            listOf(notification("1"), notification("2", isRead = true))
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is NotificationsUiState.Success)
        state as NotificationsUiState.Success
        assertEquals(2, state.notifications.size)
        assertEquals(1, state.unreadCount)
        // The old stub hardcoded true here; these come from DataStore now.
        assertEquals(false, state.jobAlertsEnabled)
        assertEquals(false, state.followUpRemindersEnabled)
    }

    @Test
    fun `repository error emits Error state`() = runTest {
        every { mockRepository.getNotifications() } returns flow {
            emit(emptyList<NotificationItem>())
            throw RuntimeException("db closed")
        }
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value is NotificationsUiState.Error)
    }

    @Test
    fun `markAsRead persists through the repository`() = runTest {
        every { mockRepository.getNotifications() } returns flowOf(
            listOf(notification("1"))
        )
        coEvery { mockRepository.markAsRead("1") } returns Result.Success(Unit)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(NotificationsUiEvent.MarkAsRead("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.markAsRead("1") }
    }

    @Test
    fun `markAllAsRead persists and sends snackbar effect`() = runTest {
        every { mockRepository.getNotifications() } returns flowOf(
            listOf(notification("1"), notification("2"))
        )
        coEvery { mockRepository.markAllAsRead() } returns Result.Success(Unit)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(NotificationsUiEvent.MarkAllAsRead)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is NotificationsUiEffect.ShowSnackbar)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { mockRepository.markAllAsRead() }
    }

    @Test
    fun `deleteNotification persists through the repository`() = runTest {
        every { mockRepository.getNotifications() } returns flowOf(
            listOf(notification("1"))
        )
        coEvery { mockRepository.delete("1") } returns Result.Success(Unit)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(NotificationsUiEvent.DeleteNotification("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.delete("1") }
    }

    @Test
    fun `failed markAsRead surfaces a snackbar`() = runTest {
        every { mockRepository.getNotifications() } returns flowOf(
            listOf(notification("1"))
        )
        coEvery { mockRepository.markAsRead("1") } returns
            Result.Failure(com.bangersoul.aivance.core.common.result.DomainError("disk full"))

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(NotificationsUiEvent.MarkAsRead("1"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is NotificationsUiEffect.ShowSnackbar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleJobAlerts persists the preference and tracks event`() = runTest {
        every { mockRepository.getNotifications() } returns flowOf(
            listOf(notification("1"))
        )
        coEvery { mockUserPreferences.updateJobAlertsEnabled(any()) } returns Unit

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(NotificationsUiEvent.ToggleJobAlerts(false))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockUserPreferences.updateJobAlertsEnabled(false) }
        coVerify { mockTrackEvent(TrackEventRequest("notifications_toggle_job_alerts_false")) }
    }

    @Test
    fun `toggleFollowUpReminders persists the preference`() = runTest {
        every { mockRepository.getNotifications() } returns flowOf(
            listOf(notification("1"))
        )
        coEvery { mockUserPreferences.updateFollowUpRemindersEnabled(any()) } returns Unit

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(NotificationsUiEvent.ToggleFollowUpReminders(false))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockUserPreferences.updateFollowUpRemindersEnabled(false) }
    }

    @Test
    fun `refresh re-subscribes and tracks the load event`() = runTest {
        every { mockRepository.getNotifications() } returns flowOf(
            listOf(notification("1"))
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(NotificationsUiEvent.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value is NotificationsUiState.Success)
        coVerify { mockTrackEvent(TrackEventRequest("notifications_load")) }
    }
}
