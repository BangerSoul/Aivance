package com.bangersoul.aivance.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.datastore.UserPreferencesRepository
import com.bangersoul.aivance.core.domain.repository.ProviderRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.FieldType
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import com.bangersoul.aivance.feature.profile.worker.ModelDownloadScheduler
import com.bangersoul.aivance.feature.profile.worker.ModelDownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OnboardingUiState {
    // The provider flow starts directly at AI provider selection; there is no
    // separate Welcome step any more (AUDIT 6 — the brand lives on Auth).

    data class ChooseAiProvider(
        val providers: List<ProviderMetadata>
    ) : OnboardingUiState

    data class ConfigureAiProvider(
        val provider: ProviderMetadata,
        val config: Map<String, String> = emptyMap(),
        val isValidating: Boolean = false,
        val error: String? = null,
        /** True when the selected provider runs on-device (e.g. Gemma) and needs a model download instead of an API key. */
        val isOnDevice: Boolean = false,
        /** For on-device providers: whether the model file is downloaded and usable. */
        val modelReady: Boolean = false,
        /** For on-device providers: a download is in flight (WorkManager-backed). */
        val isDownloading: Boolean = false,
        /** Live download progress 0f..1f while [isDownloading]. */
        val downloadProgress: Float? = null,
        /** Non-blocking notice, e.g. "not enough free storage" — never a fatal error. */
        val downloadMessage: String? = null
    ) : OnboardingUiState

    data class ChooseJobProvider(
        val providers: List<ProviderMetadata>
    ) : OnboardingUiState

    data class ConfigureJobProvider(
        val provider: ProviderMetadata,
        val config: Map<String, String> = emptyMap(),
        val isValidating: Boolean = false,
        val error: String? = null
    ) : OnboardingUiState

    data class ChooseEnrichmentProvider(
        val providers: List<ProviderMetadata>
    ) : OnboardingUiState

    data class ConfigureEnrichmentProvider(
        val provider: ProviderMetadata,
        val config: Map<String, String> = emptyMap(),
        val isValidating: Boolean = false,
        val error: String? = null
    ) : OnboardingUiState

    data class Summary(
        val aiProvider: String,
        val jobProvider: String,
        val enrichmentProvider: String? = null
    ) : OnboardingUiState

    data object Complete : OnboardingUiState
}

sealed interface OnboardingUiEvent {
    data object Start : OnboardingUiEvent

    /**
     * Explicit provider-optional choice (R2.2): job discovery keeps working
     * through the free keyless providers and the assistant uses its local
     * Copilot fallback; AI-dependent features surface their "needs a provider"
     * states. Persisted and honoured by the central provider gate.
     */
    data object ContinueWithoutProviders : OnboardingUiEvent
    data class SelectAiProvider(val providerId: String) : OnboardingUiEvent
    data class UpdateAiConfig(val key: String, val value: String) : OnboardingUiEvent
    data object ValidateAiProvider : OnboardingUiEvent

    /** Starts the on-device model download (keyless providers only). */
    data object DownloadModel : OnboardingUiEvent
    /** Dismisses the non-blocking download notice (e.g. storage warning). */
    data object DismissDownloadMessage : OnboardingUiEvent

    data class SelectJobProvider(val providerId: String) : OnboardingUiEvent
    data class UpdateJobConfig(val key: String, val value: String) : OnboardingUiEvent
    data object ValidateJobProvider : OnboardingUiEvent

    data class SelectEnrichmentProvider(val providerId: String) : OnboardingUiEvent
    data class UpdateEnrichmentConfig(val key: String, val value: String) : OnboardingUiEvent
    data object ValidateEnrichmentProvider : OnboardingUiEvent
    data object SkipEnrichment : OnboardingUiEvent

    data object Finish : OnboardingUiEvent

    data object Back : OnboardingUiEvent
}


@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val providerManager: ProviderManager,
    private val providerRepository: ProviderRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val trackEventUseCase: TrackEventUseCase,
    private val modelDownloadScheduler: ModelDownloadScheduler,
    private val deviceCapabilityProvider: DeviceCapabilityProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(
        OnboardingUiState.ChooseAiProvider(providers = emptyList())
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var selectedAiProviderId: String? = null
    private var selectedJobProviderId: String? = null
    private var selectedEnrichmentProviderId: String? = null

    init {
        // v2: begin directly at AI provider selection.
        _uiState.value = OnboardingUiState.ChooseAiProvider(
            providers = providerRegistry.getAllProviders()
                .filter { it.metadata.type == ProviderType.AI }
                .map { it.metadata }
        )
        observeModelDownload()
    }

    /**
     * Reflects the background model-download worker in the on-device config
     * step: live progress while running, and the green "Downloaded" state once
     * the file lands (which is what unlocks Validate &amp; Continue).
     */
    private fun observeModelDownload() {
        viewModelScope.launch {
            modelDownloadScheduler.observe().collect { status ->
                val current = _uiState.value as? OnboardingUiState.ConfigureAiProvider
                val isGemma = current?.isOnDevice == true &&
                    selectedAiProviderId == current.provider.id
                if (!isGemma) return@collect
                when (status) {
                    is ModelDownloadStatus.Idle -> {}
                    is ModelDownloadStatus.Running -> {
                        _uiState.value = current.copy(
                            isDownloading = true,
                            downloadProgress = status.progress,
                            downloadMessage = null
                        )
                    }
                    is ModelDownloadStatus.Succeeded -> {
                        _uiState.value = current.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
                            modelReady = true,
                            downloadMessage = null
                        )
                    }
                    is ModelDownloadStatus.Failed -> {
                        _uiState.value = current.copy(
                            isDownloading = false,
                            downloadProgress = null,
                            downloadMessage = "Download failed. Check your connection and retry."
                        )
                    }
                }
            }
        }
    }

    // Per-step draft configs retained across Back navigation so a typed (or
    // pasted) API key is never silently wiped when the user leaves and re-enters
    // a provider configuration step.
    private val aiConfigDraft = mutableMapOf<String, String>()
    private val jobConfigDraft = mutableMapOf<String, String>()
    private val enrichmentConfigDraft = mutableMapOf<String, String>()

    fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            // Kept for backward compatibility with the legacy Onboarding destination.
            OnboardingUiEvent.Start -> {
                _uiState.value = OnboardingUiState.ChooseAiProvider(
                    providers = providerRegistry.getAllProviders()
                        .filter { it.metadata.type == ProviderType.AI }
                        .map { it.metadata }
                )
            }
            OnboardingUiEvent.ContinueWithoutProviders -> acknowledgeProviderOptional()
            is OnboardingUiEvent.SelectAiProvider -> {
                // Drafts are per-step, not per-provider: never leak the previous
                // provider's key into a newly selected one's config screen.
                if (selectedAiProviderId != event.providerId) aiConfigDraft.clear()
                selectedAiProviderId = event.providerId
                providerRegistry.getProvider(event.providerId)?.let { registryProvider ->
                    // On-device providers (e.g. Gemma) skip the credential form
                    // entirely: the model download is the configuration. Surface
                    // the live readiness so the step can offer Download / show
                    // the green Downloaded status straight away.
                    val downloadable = registryProvider as? com.bangersoul.aivance.sdk.api.ModelDownloadable
                    _uiState.value = OnboardingUiState.ConfigureAiProvider(
                        provider = registryProvider.metadata,
                        config = aiConfigDraft.toMap(),
                        isOnDevice = downloadable != null,
                        modelReady = downloadable?.isModelReady == true
                    )
                }
            }
            is OnboardingUiEvent.UpdateAiConfig -> {
                val current = _uiState.value as? OnboardingUiState.ConfigureAiProvider ?: return
                aiConfigDraft[event.key] = event.value
                _uiState.value = current.copy(config = current.config + (event.key to event.value))
            }
            OnboardingUiEvent.ValidateAiProvider -> validateAiProvider()
            OnboardingUiEvent.DownloadModel -> startModelDownload()
            OnboardingUiEvent.DismissDownloadMessage -> dismissDownloadMessage()

            is OnboardingUiEvent.SelectJobProvider -> {
                if (selectedJobProviderId != event.providerId) jobConfigDraft.clear()
                selectedJobProviderId = event.providerId
                providerRegistry.getProvider(event.providerId)?.let {
                    _uiState.value = OnboardingUiState.ConfigureJobProvider(
                        provider = it.metadata,
                        config = jobConfigDraft.toMap()
                    )
                }
            }
            is OnboardingUiEvent.UpdateJobConfig -> {
                val current = _uiState.value as? OnboardingUiState.ConfigureJobProvider ?: return
                jobConfigDraft[event.key] = event.value
                _uiState.value = current.copy(config = current.config + (event.key to event.value))
            }
            OnboardingUiEvent.ValidateJobProvider -> validateJobProvider()

            is OnboardingUiEvent.SelectEnrichmentProvider -> {
                if (selectedEnrichmentProviderId != event.providerId) enrichmentConfigDraft.clear()
                selectedEnrichmentProviderId = event.providerId
                providerRegistry.getProvider(event.providerId)?.let {
                    _uiState.value = OnboardingUiState.ConfigureEnrichmentProvider(
                        provider = it.metadata,
                        config = enrichmentConfigDraft.toMap()
                    )
                }
            }
            is OnboardingUiEvent.UpdateEnrichmentConfig -> {
                val current = _uiState.value as? OnboardingUiState.ConfigureEnrichmentProvider ?: return
                enrichmentConfigDraft[event.key] = event.value
                _uiState.value = current.copy(config = current.config + (event.key to event.value))
            }
            OnboardingUiEvent.ValidateEnrichmentProvider -> validateEnrichmentProvider()
            OnboardingUiEvent.SkipEnrichment -> {
                selectedEnrichmentProviderId = null
                _uiState.value = OnboardingUiState.Summary(
                    aiProvider = providerRegistry.getProvider(selectedAiProviderId!!)?.metadata?.name ?: "Unknown",
                    jobProvider = providerRegistry.getProvider(selectedJobProviderId!!)?.metadata?.name ?: "Unknown",
                    enrichmentProvider = null
                )
            }

            OnboardingUiEvent.Finish -> {
                viewModelScope.launch {
                    try {
                        trackEventUseCase(TrackEventRequest(eventName = "onboarding_finish"))
                        // Persist completion so a restart doesn't re-show onboarding, and the
                        // auth guard treats the user as onboarded.
                        userPreferencesRepository.updateOnboardingCompleted(true)
                        // R2.2: exit the provider-optional mode the user may have
                        // chosen earlier — reaching Finish means a provider was
                        // actually configured and validated, which is a stronger
                        // contract than the deliberate opt-out.
                        userPreferencesRepository.updateProviderOptional(false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Non-blocking: onboarding must complete even if tracking or the
                        // persistence write hiccups.
                        android.util.Log.w("Onboarding", "Completion tracking/persistence failed", e)
                    } finally {
                        // The terminal state transition must always run so the
                        // "Go to Dashboard" button can never dead-end.
                        _uiState.value = OnboardingUiState.Complete
                    }
                }
            }
            OnboardingUiEvent.Back -> handleBack()
        }
    }

    /**
     * R2.2: persisting the explicit provider-optional choice is what makes
     * provider-free product entry legal under the central gate
     * (AuthenticationViewModel.evaluateProviderGate). Completing the onboarding
     * steps alone never does.
     */
    private fun acknowledgeProviderOptional() {
        viewModelScope.launch {
            try {
                trackEventUseCase(TrackEventRequest(eventName = "onboarding_provider_optional"))
                userPreferencesRepository.updateProviderOptional(true)
                userPreferencesRepository.updateOnboardingCompleted(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Non-blocking: the choice must complete even if tracking hiccups.
                android.util.Log.w("Onboarding", "Provider-optional persistence failed", e)
            } finally {
                _uiState.value = OnboardingUiState.Complete
            }
        }
    }

    private fun validateAiProvider() {
        val current = _uiState.value as? OnboardingUiState.ConfigureAiProvider ?: return
        val providerId = selectedAiProviderId ?: return

        // On-device providers (e.g. Gemma) have no credentials to validate — the
        // model file IS the configuration. Block Continue until it's downloaded
        // so the user can never sail past an unusable provider.
        if (current.isOnDevice) {
            if (!current.modelReady) {
                _uiState.value = current.copy(
                    isValidating = false,
                    error = "Download the on-device model first — Continue unlocks once the download finishes."
                )
                return
            }
            viewModelScope.launch {
                trackEventUseCase(TrackEventRequest(eventName = "onboarding_gemma_ready"))
                providerRepository.saveProviderConfig(
                    buildProviderConfig(
                        providerId = providerId,
                        type = "AI",
                        config = current.config,
                        metadata = current.provider
                    )
                )
                _uiState.value = OnboardingUiState.ChooseJobProvider(
                    providers = providerRegistry.getAllProviders()
                        .filter { it.metadata.type == ProviderType.JOB }
                        .map { it.metadata }
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isValidating = true, error = null)
            val config = buildProviderConfig(
                providerId = providerId,
                type = "AI",
                config = current.config,
                metadata = current.provider
            )
            val result = providerManager.validateProvider(providerId, config)
            if (result is Result.Success) {
                providerRepository.saveProviderConfig(config)
                _uiState.value = OnboardingUiState.ChooseJobProvider(
                    providers = providerRegistry.getAllProviders()
                        .filter { it.metadata.type == ProviderType.JOB }
                        .map { it.metadata }
                )
            } else {
                _uiState.value = current.copy(isValidating = false, error = (result as? Result.Failure)?.error?.message ?: "Validation failed")
            }
        }
    }

    /**
     * Starts the on-device model download after a storage sanity check. The
     * heavy lifting runs in the WorkManager [com.bangersoul.aivance.feature.profile.worker.GemmaModelDownloadWorker]
     * so it survives app backgrounding; [observeModelDownload] drives the UI.
     *
     * Onboarding keeps this simple: one button, primary model. Constrained
     * devices (or anyone wanting the ~271 MB compact variant) are pointed to
     * Provider Management, which offers the full storage/compact dialog.
     */
    private fun startModelDownload() {
        viewModelScope.launch {
            val current = _uiState.value as? OnboardingUiState.ConfigureAiProvider ?: return@launch
            val providerId = selectedAiProviderId ?: return@launch
            val downloadable = providerRegistry.getProvider(providerId)
                as? com.bangersoul.aivance.sdk.api.ModelDownloadable ?: return@launch
            if (downloadable.isModelReady) return@launch

            val capability = deviceCapabilityProvider.currentCapability()
            val required = maxOf(
                com.bangersoul.aivance.feature.profile.DeviceCapability.MIN_REQUIRED_FREE_STORAGE_BYTES,
                withStorageHeadroom(downloadable.modelSizeBytes)
            )
            if (capability.freeStorageBytes < required) {
                _uiState.value = current.copy(
                    downloadMessage = "Not enough free storage for the model — free up space, or use Provider Management to pick the smaller compact model."
                )
                return@launch
            }

            trackEventUseCase(TrackEventRequest(eventName = "onboarding_gemma_download_start"))
            _uiState.value = current.copy(
                isDownloading = true,
                downloadProgress = 0f,
                downloadMessage = null,
                error = null
            )
            modelDownloadScheduler.enqueue(providerId)
        }
    }

    private fun dismissDownloadMessage() {
        val current = _uiState.value as? OnboardingUiState.ConfigureAiProvider ?: return
        _uiState.value = current.copy(downloadMessage = null)
    }

    private companion object {
        /** Headroom above the file size used to decide whether a model fits. */
        const val STORAGE_HEADROOM_PERCENT = 15L

        fun withStorageHeadroom(bytes: Long): Long = bytes + (bytes * STORAGE_HEADROOM_PERCENT / 100L)
    }

    private fun validateJobProvider() {
        val current = _uiState.value as? OnboardingUiState.ConfigureJobProvider ?: return
        val providerId = selectedJobProviderId ?: return

        viewModelScope.launch {
            _uiState.value = current.copy(isValidating = true, error = null)
            val config = buildProviderConfig(
                providerId = providerId,
                type = "JOB",
                config = current.config,
                metadata = current.provider
            )
            val result = providerManager.validateProvider(providerId, config)
            if (result is Result.Success) {
                providerRepository.saveProviderConfig(config)
                _uiState.value = OnboardingUiState.ChooseEnrichmentProvider(
                    providers = providerRegistry.getAllProviders()
                        .filter { it.metadata.type == ProviderType.ENRICHMENT }
                        .map { it.metadata }
                )
            } else {
                _uiState.value = current.copy(isValidating = false, error = (result as? Result.Failure)?.error?.message ?: "Validation failed")
            }
        }
    }

    private fun validateEnrichmentProvider() {
        val current = _uiState.value as? OnboardingUiState.ConfigureEnrichmentProvider ?: return
        val providerId = selectedEnrichmentProviderId ?: return

        viewModelScope.launch {
            _uiState.value = current.copy(isValidating = true, error = null)
            val config = buildProviderConfig(
                providerId = providerId,
                type = "ENRICHMENT",
                config = current.config,
                metadata = current.provider
            )
            val result = providerManager.validateProvider(providerId, config)
            if (result is Result.Success) {
                providerRepository.saveProviderConfig(config)
                _uiState.value = OnboardingUiState.Summary(
                    aiProvider = providerRegistry.getProvider(selectedAiProviderId!!)?.metadata?.name ?: "Unknown",
                    jobProvider = providerRegistry.getProvider(selectedJobProviderId!!)?.metadata?.name ?: "Unknown",
                    enrichmentProvider = providerRegistry.getProvider(selectedEnrichmentProviderId!!)?.metadata?.name
                )
            } else {
                _uiState.value = current.copy(isValidating = false, error = (result as? Result.Failure)?.error?.message ?: "Validation failed")
            }
        }
    }

    /**
     * Builds a [ProviderConfiguration] from the entered form fields, splitting
     * credentials correctly:
     *  - PASSWORD/sensitive fields from the provider's own metadata go into
     *    [ProviderConfiguration.secrets] (encrypted at rest),
     *  - everything else (model names, base URLs, non-sensitive IDs) goes into
     *    [ProviderConfiguration.settings] (plaintext preferences).
     *
     * Previously only a hardcoded "apiKey" key was treated as a secret, which
     * meant multi-credential providers (e.g. Adzuna's `appKey`) had their
     * primary credential stored in plaintext settings and never applied to the
     * live provider instance.
     */
    private fun buildProviderConfig(
        providerId: String,
        type: String,
        config: Map<String, String>,
        metadata: ProviderMetadata
    ): ProviderConfiguration {
        val secretKeys = metadata.configFields
            .filter { it.fieldType == FieldType.PASSWORD || it.isSensitive }
            .map { it.key }
            .toSet()
        return ProviderConfiguration(
            providerId = providerId,
            secrets = config.filterKeys { it in secretKeys },
            settings = config.filterKeys { it !in secretKeys } + ("type" to type)
        )
    }

    private fun handleBack() {
        when (_uiState.value) {
            is OnboardingUiState.ChooseAiProvider -> {
                // v2: no Welcome step inside onboarding — backing out from the
                // first step exits the flow entirely.
                _uiState.value = OnboardingUiState.Complete
            }
            is OnboardingUiState.ConfigureAiProvider -> {
                 _uiState.value = OnboardingUiState.ChooseAiProvider(
                    providers = providerRegistry.getAllProviders()
                        .filter { it.metadata.type == ProviderType.AI }
                        .map { it.metadata }
                )
            }
            is OnboardingUiState.ChooseJobProvider -> {
                providerRegistry.getProvider(selectedAiProviderId!!)?.let {
                    _uiState.value = OnboardingUiState.ConfigureAiProvider(
                        provider = it.metadata,
                        config = aiConfigDraft.toMap()
                    )
                }
            }
            is OnboardingUiState.ConfigureJobProvider -> {
                _uiState.value = OnboardingUiState.ChooseJobProvider(
                    providers = providerRegistry.getAllProviders()
                        .filter { it.metadata.type == ProviderType.JOB }
                        .map { it.metadata }
                )
            }
            is OnboardingUiState.ChooseEnrichmentProvider -> {
                providerRegistry.getProvider(selectedJobProviderId!!)?.let {
                    _uiState.value = OnboardingUiState.ConfigureJobProvider(
                        provider = it.metadata,
                        config = jobConfigDraft.toMap()
                    )
                }
            }
            is OnboardingUiState.ConfigureEnrichmentProvider -> {
                _uiState.value = OnboardingUiState.ChooseEnrichmentProvider(
                    providers = providerRegistry.getAllProviders()
                        .filter { it.metadata.type == ProviderType.ENRICHMENT }
                        .map { it.metadata }
                )
            }
            is OnboardingUiState.Summary -> {
                if (selectedEnrichmentProviderId != null) {
                    providerRegistry.getProvider(selectedEnrichmentProviderId!!)?.let {
                        _uiState.value = OnboardingUiState.ConfigureEnrichmentProvider(
                            provider = it.metadata,
                            config = enrichmentConfigDraft.toMap()
                        )
                    }
                } else {
                    _uiState.value = OnboardingUiState.ChooseEnrichmentProvider(
                        providers = providerRegistry.getAllProviders()
                            .filter { it.metadata.type == ProviderType.ENRICHMENT }
                            .map { it.metadata }
                    )
                }
            }
            else -> {}
        }
    }
}
