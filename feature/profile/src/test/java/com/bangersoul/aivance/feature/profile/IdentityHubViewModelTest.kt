package com.bangersoul.aivance.feature.profile

import app.cash.turbine.test
import com.bangersoul.aivance.core.common.model.UserProfile
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.datastore.UserPreferencesRepository
import com.bangersoul.aivance.core.domain.repository.ProviderRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.settings.LoadSettingsUseCase
import com.bangersoul.aivance.core.domain.usecase.settings.ResetSettingsUseCase
import com.bangersoul.aivance.core.domain.usecase.settings.SaveSettingsUseCase
import com.bangersoul.aivance.core.domain.usecase.user.LoadProfileUseCase
import com.bangersoul.aivance.core.domain.usecase.user.UpdateProfileUseCase
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdentityHubViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockLoadProfile: LoadProfileUseCase = mockk(relaxed = true)
    private val mockUpdateProfile: UpdateProfileUseCase = mockk(relaxed = true)
    private val mockLoadSettings: LoadSettingsUseCase = mockk(relaxed = true)
    private val mockSaveSettings: SaveSettingsUseCase = mockk(relaxed = true)
    private val mockResetSettings: ResetSettingsUseCase = mockk(relaxed = true)
    private val mockUserPreferences: UserPreferencesRepository = mockk(relaxed = true)
    private val mockProviderRegistry: ProviderRegistry = mockk(relaxed = true)
    private val mockProviderManager: ProviderManager = mockk(relaxed = true)
    private val mockProviderRepository: ProviderRepository = mockk(relaxed = true)
    private val mockTrackEvent: TrackEventUseCase = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): IdentityHubViewModel {
        // refresh() combines these flows on init — keep them all non-null.
        every { mockLoadProfile() } returns flowOf(Result.Success(mockk<UserProfile>(relaxed = true)))
        every { mockUserPreferences.userPreferences } returns
            flowOf(mockk<com.bangersoul.aivance.core.datastore.UserPreferences>(relaxed = true))
        every { mockProviderManager.providerStatuses } returns MutableStateFlow(emptyMap())
        every { mockProviderRegistry.getAllProviders() } returns emptyList()

        return IdentityHubViewModel(
            mockLoadProfile, mockUpdateProfile, mockLoadSettings, mockSaveSettings,
            mockResetSettings, mockUserPreferences, mockProviderRegistry, mockProviderManager,
            mockProviderRepository, mockTrackEvent
        )
    }

    @Test
    fun `sign out clears the session and emits SignOutCompleted`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { mockUserPreferences.clearSession() } returns Unit

        viewModel.effects.test {
            viewModel.onEvent(IdentityHubUiEvent.SignOut)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is IdentityHubUiEffect.SignOutCompleted)
            coVerify { mockUserPreferences.clearSession() }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
