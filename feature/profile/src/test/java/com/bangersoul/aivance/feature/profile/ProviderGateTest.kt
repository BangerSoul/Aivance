package com.bangersoul.aivance.feature.profile

import com.bangersoul.aivance.core.datastore.UserPreferences
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.ProviderStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * R2.2 provider-gate matrix.
 *
 * Covers every state the gate contract must distinguish: fresh user, valid
 * provider, invalid provider, no provider, the deliberate provider-optional
 * choice, onboarding already persisted, provider removed after onboarding, and
 * provider invalidated after onboarding — at the level of the persisted +
 * live inputs the gate actually reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderGateTest {

    /** House pattern (matches sibling ViewModel tests): class-level scheduler + @Before/@After Main swap. */
    private val testDispatcher = StandardTestDispatcher()

    // region — pure gate function ------------------------------------------------

    @Test
    fun `fresh user with nothing persisted is UNCONFIGURED`() {
        val gate = evaluate(
            prefs = UserPreferences(),
            savedConfigs = emptyList()
        )
        assertEquals(ProviderGateState.UNCONFIGURED, gate)
    }

    @Test
    fun `valid persisted provider is CONFIGURED even before live hydration lands`() {
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true),
            savedConfigs = listOf(ProviderConfiguration(providerId = "groq")),
            liveStatuses = mapOf("groq" to ProviderStatus.Uninitialized)
        )
        assertEquals(ProviderGateState.CONFIGURED, gate)
    }

    @Test
    fun `onboarding persisted with zero providers and zero optional choice is UNCONFIGURED`() {
        // The legacy bypass state: onboardingCompleted = true, nothing else.
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true),
            savedConfigs = emptyList()
        )
        assertEquals(ProviderGateState.UNCONFIGURED, gate)
    }

    @Test
    fun `explicit provider-optional choice is OPTIONAL`() {
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true, providerOptional = true),
            savedConfigs = emptyList()
        )
        assertEquals(ProviderGateState.OPTIONAL, gate)
    }

    @Test
    fun `optional choice still holds when live statuses are transiently broken`() {
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true, providerOptional = true),
            savedConfigs = emptyList(),
            liveStatuses = mapOf("gemini" to ProviderStatus.Error)
        )
        assertEquals(ProviderGateState.OPTIONAL, gate)
    }

    @Test
    fun `invalidated provider after onboarding is INVALID`() {
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true),
            savedConfigs = listOf(ProviderConfiguration(providerId = "groq")),
            liveStatuses = mapOf("groq" to ProviderStatus.AuthenticationFailed)
        )
        assertEquals(ProviderGateState.INVALID, gate)
    }

    @Test
    fun `invalid configuration after onboarding is INVALID`() {
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true),
            savedConfigs = listOf(ProviderConfiguration(providerId = "gemini")),
            liveStatuses = mapOf("gemini" to ProviderStatus.InvalidConfiguration)
        )
        assertEquals(ProviderGateState.INVALID, gate)
    }

    @Test
    fun `transient offline status does not lock a configured user out`() {
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true),
            savedConfigs = listOf(ProviderConfiguration(providerId = "groq")),
            liveStatuses = mapOf("groq" to ProviderStatus.Offline)
        )
        assertEquals(ProviderGateState.CONFIGURED, gate)
    }

    @Test
    fun `transient error status does not lock a configured user out`() {
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true),
            savedConfigs = listOf(ProviderConfiguration(providerId = "groq")),
            liveStatuses = mapOf("groq" to ProviderStatus.Error)
        )
        assertEquals(ProviderGateState.CONFIGURED, gate)
    }

    @Test
    fun `a usable live status for a different provider does not mask an invalid one`() {
        // gemini configured and fine, groq configured and broken → INVALID wins.
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true),
            savedConfigs = listOf(
                ProviderConfiguration(providerId = "gemini"),
                ProviderConfiguration(providerId = "groq")
            ),
            liveStatuses = mapOf(
                "gemini" to ProviderStatus.Ready,
                "groq" to ProviderStatus.AuthenticationFailed
            )
        )
        assertEquals(ProviderGateState.INVALID, gate)
    }

    @Test
    fun `saved configuration for a job provider only does not satisfy the AI gate`() {
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true),
            savedConfigs = listOf(ProviderConfiguration(providerId = "arbeitnow")),
            liveStatuses = mapOf("arbeitnow" to ProviderStatus.Active)
        )
        assertEquals(ProviderGateState.UNCONFIGURED, gate)
    }

    @Test
    fun `provider removed after onboarding is UNCONFIGURED`() {
        val gate = evaluate(
            prefs = UserPreferences(onboardingCompleted = true),
            savedConfigs = emptyList()
        )
        assertEquals(ProviderGateState.UNCONFIGURED, gate)
    }

    @Test
    fun `onboarding completion alone never bypasses the gate`() {
        // The exact contract from R2.2: no persisted flag substitutes for
        // provider validity or the explicit optional choice.
        val bypassAttempt = evaluate(
            prefs = UserPreferences(onboardingCompleted = true),
            savedConfigs = emptyList(),
            liveStatuses = emptyMap()
        )
        assertEquals(ProviderGateState.UNCONFIGURED, bypassAttempt)
    }

    // endregion

    // region — ViewModel wiring ---------------------------------------------------

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `gate flow combines persisted and live state reactively`() = runTest(testDispatcher) {
        val statuses = MutableStateFlow(mapOf("groq" to ProviderStatus.Ready))
        val vm = buildViewModel(
            prefs = UserPreferences(onboardingCompleted = true),
            configs = listOf(ProviderConfiguration(providerId = "groq")),
            statuses = statuses
        )
        assertEquals(ProviderGateState.CONFIGURED, vm.providerGate.first { it != null })

        // Provider invalidated at runtime → gate flips without a restart.
        statuses.value = mapOf("groq" to ProviderStatus.AuthenticationFailed)
        assertEquals(ProviderGateState.INVALID, vm.providerGate.first { it == ProviderGateState.INVALID })
    }

    @Test
    fun `resolveProviderGate reads settled persisted data for the splash contract`() = runTest(testDispatcher) {
        val vm = buildViewModel(
            prefs = UserPreferences(onboardingCompleted = true, providerOptional = true),
            configs = emptyList(),
            statuses = MutableStateFlow(emptyMap())
        )
        assertEquals(ProviderGateState.OPTIONAL, vm.resolveProviderGate())
    }

    @Test
    fun `logout clears the provider-optional choice with the session`() = runTest(testDispatcher) {
        var stored = UserPreferences(onboardingCompleted = true, providerOptional = true)
        val prefsFlow = MutableStateFlow(stored)
        val repo = mockk<com.bangersoul.aivance.core.datastore.UserPreferencesRepository>(relaxed = true)
        every { repo.userPreferences } returns prefsFlow
        coEvery { repo.updateProviderOptional(any()) } answers { stored = stored.copy(providerOptional = firstArg()) }

        val vm = buildViewModel(prefs = stored, configs = emptyList(), statuses = MutableStateFlow(emptyMap()), repoOverride = repo)
        vm.onEvent(AuthenticationUiEvent.Logout)
        testDispatcher.scheduler.advanceUntilIdle()
        // updateProviderOptional(false) must have been requested alongside the
        // onboarding + session clears so the next cold start cannot inherit the
        // choice of a signed-out session.
        coVerify { repo.updateProviderOptional(false) }
        coVerify { repo.updateOnboardingCompleted(false) }
        coVerify { repo.clearSession() }
    }

    // endregion

    // region — helpers ------------------------------------------------------------

    private fun evaluate(
        prefs: UserPreferences,
        savedConfigs: List<ProviderConfiguration>,
        liveStatuses: Map<String, ProviderStatus> = emptyMap()
    ): ProviderGateState {
        val vm = buildViewModel(
            prefs = prefs,
            configs = savedConfigs,
            statuses = MutableStateFlow(liveStatuses)
        )
        return vm.evaluateProviderGate(
            optional = prefs.providerOptional,
            onboarded = prefs.onboardingCompleted,
            hasSavedAiProvider = savedConfigs.any { it.providerId in AI_PROVIDER_IDS },
            liveAiStatuses = liveStatuses.filterKeys { it in AI_PROVIDER_IDS }
        )
    }

    private fun buildViewModel(
        prefs: UserPreferences,
        configs: List<ProviderConfiguration>,
        statuses: MutableStateFlow<Map<String, ProviderStatus>>,
        repoOverride: com.bangersoul.aivance.core.datastore.UserPreferencesRepository? = null
    ): AuthenticationViewModel {
        val repo = repoOverride ?: mockk(relaxed = true) {
            every { userPreferences } returns MutableStateFlow(prefs)
        }
        val providerRepo = mockk<com.bangersoul.aivance.core.domain.repository.ProviderRepository> {
            every { getProviderConfigs() } returns flowOf(configs)
        }
        val providerManager = mockk<com.bangersoul.aivance.sdk.infrastructure.ProviderManager>(relaxed = true) {
            every { providerStatuses } returns statuses
        }
        val providerRegistry = mockk<com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry> {
            every { getAllProviders() } returns AI_PROVIDER_IDS.map { id ->
                val metadata = mockk<com.bangersoul.aivance.sdk.core.ProviderMetadata> {
                    every { this@mockk.id } returns id
                    every { this@mockk.type } returns com.bangersoul.aivance.sdk.core.ProviderType.AI
                }
                mockk {
                    every { this@mockk.metadata } returns metadata
                }
            }
        }
        val authVm = AuthenticationViewModel(
            userPreferencesRepository = repo,
            getProviderHealthUseCase = mockk(relaxed = true),
            trackEventUseCase = mockk(relaxed = true),
            userDao = mockk(relaxed = true),
            providerManager = providerManager,
            providerRegistry = providerRegistry,
            providerRepository = providerRepo,
            appContext = mockk(relaxed = true)
        )
        return authVm
    }

    private companion object {
        val AI_PROVIDER_IDS = setOf("gemini", "groq", "anthropic", "openai", "openrouter", "ollama", "gemma")
    }

    // endregion
}
