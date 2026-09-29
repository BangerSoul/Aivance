package com.bangersoul.aivance.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.domain.repository.ProviderRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.GetAvailableModelsUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.GetProviderHealthUseCase
import com.bangersoul.aivance.sdk.api.CompactModel
import com.bangersoul.aivance.sdk.api.ModelDownloadable
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.FieldType
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import com.bangersoul.aivance.feature.profile.worker.ModelDownloadScheduler
import com.bangersoul.aivance.feature.profile.worker.ModelDownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProviderManagementUiState {
    data object Loading : ProviderManagementUiState
    data class Success(
        val providers: List<ProviderInfo> = emptyList(),
        val selectedProviderId: String? = null,
        val isTestingConnection: Boolean = false,
        val testingProviderId: String? = null,
        val credentialDrafts: Map<String, Map<String, String>> = emptyMap(),
        val downloadingProviderId: String? = null,
        val modelDownloadProgress: Float? = null,
        val modelDownloadDialog: ModelDownloadDialog? = null
    ) : ProviderManagementUiState
    data class Error(val message: String) : ProviderManagementUiState
}

sealed interface ProviderManagementUiEvent {
    data class SelectProvider(val providerId: String) : ProviderManagementUiEvent
    /** Opens the provider config dialog, seeding credential drafts from the persisted config. */
    data class EditProvider(val providerId: String) : ProviderManagementUiEvent
    data object DismissProviderDialog : ProviderManagementUiEvent
    data class ToggleProvider(val providerId: String, val enabled: Boolean) : ProviderManagementUiEvent
    data class TestConnection(val providerId: String) : ProviderManagementUiEvent
    data class SetCredential(val providerId: String, val fieldKey: String, val value: String) : ProviderManagementUiEvent
    data class SelectModel(val providerId: String, val model: String) : ProviderManagementUiEvent
    data class SaveProvider(val providerId: String) : ProviderManagementUiEvent
    data class DownloadModel(val providerId: String) : ProviderManagementUiEvent
    data class ConfirmModelDownload(val providerId: String, val useCompactModel: Boolean) : ProviderManagementUiEvent
    data object DismissModelDownloadDialog : ProviderManagementUiEvent
    data class DeleteModel(val providerId: String) : ProviderManagementUiEvent
    data object Refresh : ProviderManagementUiEvent
}

sealed interface ProviderManagementUiEffect {
    data class ShowSnackbar(val message: String) : ProviderManagementUiEffect
    data class ConnectionTestResult(val providerId: String, val success: Boolean, val message: String) : ProviderManagementUiEffect
    data object ProviderChanged : ProviderManagementUiEffect
}

@HiltViewModel
class ProviderManagementViewModel @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val providerManager: ProviderManager,
    private val providerRepository: ProviderRepository,
    private val getProviderHealthUseCase: GetProviderHealthUseCase,
    private val getAvailableModelsUseCase: GetAvailableModelsUseCase,
    private val trackEventUseCase: TrackEventUseCase,
    private val deviceCapabilityProvider: DeviceCapabilityProvider,
    private val modelDownloadScheduler: ModelDownloadScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProviderManagementUiState>(ProviderManagementUiState.Loading)
    val uiState: StateFlow<ProviderManagementUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ProviderManagementUiEffect>(Channel.BUFFERED)
    val effects: Flow<ProviderManagementUiEffect> = _effects.receiveAsFlow()

    init {
        loadProviders()
        observeModelDownload()
    }

    /**
     * Reflects the background model-download worker in the UI: live progress
     * while running, and completion/refresh once it finishes (including across
     * app restarts, since the work survives backgrounding).
     */
    private fun observeModelDownload() {
        viewModelScope.launch {
            // Terminal states are only surfaced as snackbars when this session
            // actually observed the work running; otherwise a completed work
            // from a previous session would re-notify on every app launch.
            // (Background completion is already covered by the worker's own
            // system notification.)
            var sawRunningThisSession = false
            modelDownloadScheduler.observe().collect { status ->
                when (status) {
                    is ModelDownloadStatus.Idle -> sawRunningThisSession = false
                    is ModelDownloadStatus.Running -> {
                        sawRunningThisSession = true
                        val current = _uiState.value as? ProviderManagementUiState.Success
                        _uiState.value = (current ?: return@collect).copy(
                            modelDownloadProgress = status.progress,
                            downloadingProviderId = current.downloadingProviderId
                                ?: current.providers.firstOrNull { it.isOnDevice }?.id
                        )
                    }
                    is ModelDownloadStatus.Succeeded -> {
                        val current = _uiState.value as? ProviderManagementUiState.Success
                        _uiState.value = (current ?: return@collect).copy(
                            downloadingProviderId = null,
                            modelDownloadProgress = null
                        )
                        refreshProvider(status.providerId)
                        if (sawRunningThisSession) {
                            _effects.send(ProviderManagementUiEffect.ShowSnackbar("On-device model downloaded and ready"))
                        }
                        sawRunningThisSession = false
                    }
                    is ModelDownloadStatus.Failed -> {
                        val current = _uiState.value as? ProviderManagementUiState.Success
                        _uiState.value = (current ?: return@collect).copy(
                            downloadingProviderId = null,
                            modelDownloadProgress = null
                        )
                        if (sawRunningThisSession) {
                            _effects.send(ProviderManagementUiEffect.ShowSnackbar(
                                "Model download failed. It will retry in the background."
                            ))
                        }
                        sawRunningThisSession = false
                    }
                }
            }
        }
    }

    fun onEvent(event: ProviderManagementUiEvent) {
        when (event) {
            is ProviderManagementUiEvent.SelectProvider -> selectProvider(event.providerId)
            is ProviderManagementUiEvent.EditProvider -> editProvider(event.providerId)
            ProviderManagementUiEvent.DismissProviderDialog -> dismissProviderDialog()
            is ProviderManagementUiEvent.ToggleProvider -> toggleProvider(event.providerId, event.enabled)
            is ProviderManagementUiEvent.TestConnection -> testConnection(event.providerId)
            is ProviderManagementUiEvent.SetCredential -> setCredential(event.providerId, event.fieldKey, event.value)
            is ProviderManagementUiEvent.SelectModel -> selectModel(event.providerId, event.model)
            is ProviderManagementUiEvent.SaveProvider -> saveProvider(event.providerId)
            is ProviderManagementUiEvent.DownloadModel -> onDownloadModelRequested(event.providerId)
            is ProviderManagementUiEvent.ConfirmModelDownload -> confirmModelDownload(event.providerId, event.useCompactModel)
            ProviderManagementUiEvent.DismissModelDownloadDialog -> dismissModelDownloadDialog()
            is ProviderManagementUiEvent.DeleteModel -> deleteModel(event.providerId)
            ProviderManagementUiEvent.Refresh -> loadProviders()
        }
    }

    /** Builds the full provider list from the SDK registry + persisted configs. */
    private fun loadProviders() {
        viewModelScope.launch {
            _uiState.value = ProviderManagementUiState.Loading
            trackEventUseCase(TrackEventRequest("provider_mgmt_load"))

            val statuses = providerManager.providerStatuses.value
            val providers = providerRegistry.getAllProviders().map { base ->
                val meta = base.metadata
                val persisted = providerRepository.getProviderConfig(meta.id)
                val models = meta.supportedModels.ifEmpty {
                    getAvailableModelsUseCase(meta.id).getOrNull() ?: emptyList()
                }
                val selectedModel = persisted?.settings?.get("selectedModel")
                    ?: persisted?.settings?.get("model")
                    ?: models.firstOrNull()
                    ?: ""

                val secretValue = persisted?.secrets?.values?.firstOrNull { it.isNotBlank() }
                val downloadable = base as? ModelDownloadable
                ProviderInfo(
                    id = meta.id,
                    name = meta.name,
                    category = when (meta.type) {
                        ProviderType.AI -> ProviderCategory.AI
                        ProviderType.JOB -> ProviderCategory.JOB
                        ProviderType.ENRICHMENT -> ProviderCategory.ENRICHMENT
                    },
                    description = meta.description,
                    isEnabled = persisted?.settings?.get("isEnabled")?.toBoolean()
                        ?: (statuses[meta.id] == ProviderStatus.Active || statuses[meta.id] == ProviderStatus.Ready),
                    isConnected = if (downloadable != null) downloadable.isModelReady else secretValue != null,
                    selectedModel = selectedModel,
                    availableModels = models,
                    apiKeyConfigured = if (downloadable != null) downloadable.isModelReady else secretValue != null,
                    maskedApiKey = secretValue?.let { maskKey(it) }.orEmpty(),
                    healthStatus = mapStatus(statuses[meta.id] ?: base.status),
                    isOnDevice = downloadable != null,
                    modelDownloaded = downloadable?.isModelReady == true,
                    configFields = meta.configFields
                )
            }.sortedBy { it.category.ordinal }

            _uiState.value = ProviderManagementUiState.Success(
                providers = providers,
                selectedProviderId = providers.firstOrNull { it.isEnabled }?.id
            )
        }
    }

    /**
     * Opens the config dialog for a provider, seeding the credential drafts
     * from whatever is already persisted so the form is prefilled and saving
     * with untouched fields never wipes existing credentials.
     */
    private fun editProvider(providerId: String) {
        val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return
        viewModelScope.launch {
            val persisted = providerRepository.getProviderConfig(providerId)
            val settings = (persisted?.settings ?: emptyMap())
                .filterKeys { it != "isEnabled" && it != "selectedModel" && it != "model" && it != "type" }
            val seeded = (persisted?.secrets ?: emptyMap()) + settings
            _uiState.value = currentState.copy(
                selectedProviderId = providerId,
                credentialDrafts = currentState.credentialDrafts + (providerId to seeded)
            )
        }
    }

    private fun dismissProviderDialog() {
        val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return
        _uiState.value = currentState.copy(selectedProviderId = null)
    }

    private fun selectProvider(providerId: String) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("provider_select_$providerId"))
            val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return@launch

            // Persist selection + enable it so ProviderManager picks it up.
            val provider = currentState.providers.find { it.id == providerId }
            val config = buildConfig(provider, enabled = true)
            providerRepository.saveProviderConfig(config)

            _uiState.value = currentState.copy(selectedProviderId = providerId)
            _effects.send(ProviderManagementUiEffect.ProviderChanged)
            _effects.send(ProviderManagementUiEffect.ShowSnackbar("Provider changed to ${provider?.name ?: providerId}"))
        }
    }

    private fun toggleProvider(providerId: String, enabled: Boolean) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("provider_toggle_${providerId}_$enabled"))
            val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return@launch
            val provider = currentState.providers.find { it.id == providerId } ?: return@launch

            val config = buildConfig(provider, enabled = enabled)
            val result = providerRepository.saveProviderConfig(config)

            val updatedProviders = currentState.providers.map {
                if (it.id == providerId) it.copy(isEnabled = enabled) else it
            }
            _uiState.value = currentState.copy(providers = updatedProviders)

            _effects.send(ProviderManagementUiEffect.ShowSnackbar(
                when {
                    result is Result.Success && enabled -> "Provider enabled"
                    result is Result.Success -> "Provider disabled"
                    result is Result.Failure -> result.error.message
                    else -> "Provider updated"
                }
            ))
        }
    }

    private fun setCredential(providerId: String, fieldKey: String, value: String) {
        val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return
        val drafts = currentState.credentialDrafts[providerId].orEmpty() + (fieldKey to value)
        _uiState.value = currentState.copy(
            credentialDrafts = currentState.credentialDrafts + (providerId to drafts)
        )
    }

    private fun selectModel(providerId: String, model: String) {
        val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return
        val updatedProviders = currentState.providers.map {
            if (it.id == providerId) it.copy(selectedModel = model) else it
        }
        _uiState.value = currentState.copy(providers = updatedProviders)
    }

    /** Persists the drafted credentials + selected model for a provider. */
    private fun saveProvider(providerId: String) {
        viewModelScope.launch {
            val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return@launch
            val provider = currentState.providers.find { it.id == providerId } ?: return@launch
            val drafts = currentState.credentialDrafts[providerId].orEmpty()

            val config = buildConfig(
                provider = provider,
                credentials = drafts,
                enabled = provider.isEnabled
            )
            val result = providerRepository.saveProviderConfig(config)

            // Refresh persisted state so health/connection reflect the new key.
            refreshProvider(providerId)

            _effects.send(ProviderManagementUiEffect.ShowSnackbar(
                if (result is Result.Success) "${provider.name} saved" else (result as? Result.Failure)?.error?.message ?: "Failed to save ${provider.name}"
            ))
        }
    }

    /**
     * Gate before downloading an on-device model: inspects free storage and
     * total RAM, then shows a confirmation dialog with the exact model size
     * (or a block message when even the compact model cannot fit).
     */
    private fun onDownloadModelRequested(providerId: String) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("provider_download_model_$providerId"))
            val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return@launch
            val downloadable = providerRegistry.getProvider(providerId) as? ModelDownloadable
                ?: return@launch
            val provider = currentState.providers.find { it.id == providerId } ?: return@launch

            val capability = deviceCapabilityProvider.currentCapability()
            val compact = downloadable.compactModel

            // Primary model requires ≥2 GiB free storage AND enough room for the file.
            val primaryFits = capability.freeStorageBytes >= maxOf(
                DeviceCapability.MIN_REQUIRED_FREE_STORAGE_BYTES,
                withHeadroom(downloadable.modelSizeBytes)
            )
            val compactFits = compact != null &&
                capability.freeStorageBytes >= withHeadroom(compact.sizeBytes)

            // Nothing fits — hard block, no dialog.
            if (!primaryFits && !compactFits) {
                _effects.send(ProviderManagementUiEffect.ShowSnackbar(
                    "Not enough free storage to download an on-device model. " +
                        "Free up space and try again."
                ))
                return@launch
            }

            val offersCompact = compact != null &&
                compactFits && (!primaryFits || !capability.hasRecommendedRam)

            _uiState.value = currentState.copy(
                modelDownloadDialog = ModelDownloadDialog(
                    providerId = providerId,
                    modelName = provider.name,
                    modelSizeBytes = downloadable.modelSizeBytes,
                    compactName = compact?.name,
                    compactSizeBytes = compact?.sizeBytes ?: 0L,
                    freeStorageBytes = capability.freeStorageBytes,
                    storageBlocked = !primaryFits,
                    ramWarning = !capability.hasRecommendedRam,
                    offersCompact = offersCompact
                )
            )
        }
    }

    /**
     * Starts the download once the user confirms the dialog. The heavy lifting
     * runs in [GemmaModelDownloadWorker] via WorkManager so the ~3 GB download
     * survives app backgrounding; the UI reflects progress through
     * [observeModelDownload].
     */
    private fun confirmModelDownload(providerId: String, useCompactModel: Boolean) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("provider_confirm_download_${providerId}_${if (useCompactModel) "compact" else "primary"}"))
            val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return@launch
            val downloadable = providerRegistry.getProvider(providerId) as? ModelDownloadable
                ?: return@launch

            // Show an immediate in-UI downloading state; the worker drives the rest.
            _uiState.value = currentState.copy(
                modelDownloadDialog = null,
                downloadingProviderId = providerId,
                modelDownloadProgress = 0f
            )

            val url = if (useCompactModel) downloadable.compactModel?.url else null
            modelDownloadScheduler.enqueue(providerId, url)
        }
    }

    private fun dismissModelDownloadDialog() {
        val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return
        _uiState.value = currentState.copy(modelDownloadDialog = null)
    }

    /** Removes a downloaded on-device model to free storage. */
    private fun deleteModel(providerId: String) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("provider_delete_model_$providerId"))
            // Cancel any in-flight download so it can't re-create the model
            // right after deletion.
            modelDownloadScheduler.cancel()
            val currentState = _uiState.value as? ProviderManagementUiState.Success
            _uiState.value = (currentState ?: return@launch).copy(
                downloadingProviderId = null,
                modelDownloadProgress = null
            )
            val downloadable = providerRegistry.getProvider(providerId) as? ModelDownloadable ?: return@launch
            val result = downloadable.deleteModel()
            refreshProvider(providerId)
            _effects.send(ProviderManagementUiEffect.ShowSnackbar(
                if (result is Result.Success) "On-device model deleted"
                else (result as? Result.Failure)?.error?.message ?: "Failed to delete model"
            ))
        }
    }

    /** Runs a real credential validation through the SDK's validateProvider. */
    private fun testConnection(providerId: String) {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("provider_test_connection_$providerId"))
            val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return@launch
            val provider = currentState.providers.find { it.id == providerId } ?: return@launch
            _uiState.value = currentState.copy(isTestingConnection = true, testingProviderId = providerId)

            val drafts = currentState.credentialDrafts[providerId].orEmpty()
            val config = buildConfig(provider, credentials = drafts, enabled = provider.isEnabled)

            // Persist the draft credentials first so validation runs against the real ones.
            providerRepository.saveProviderConfig(config)

            val result = providerManager.validateProvider(providerId, config)
            val status = providerManager.providerStatuses.value[providerId] ?: baseStatusFor(providerId)
            val isHealthy = result is Result.Success

            val updatedProviders = currentState.providers.map {
                if (it.id == providerId) it.copy(
                    isConnected = isHealthy,
                    apiKeyConfigured = isHealthy || it.apiKeyConfigured,
                    healthStatus = if (isHealthy) ProviderHealthStatus.HEALTHY else mapStatus(status)
                ) else it
            }
            _uiState.value = currentState.copy(
                providers = updatedProviders,
                isTestingConnection = false,
                testingProviderId = null
            )
            _effects.send(ProviderManagementUiEffect.ConnectionTestResult(
                providerId = providerId,
                success = isHealthy,
                message = if (isHealthy) "Connected successfully" else (result as? Result.Failure)?.error?.message ?: "Connection failed"
            ))
        }
    }

    private suspend fun refreshProvider(providerId: String) {
        val currentState = _uiState.value as? ProviderManagementUiState.Success ?: return
        val provider = currentState.providers.find { it.id == providerId } ?: return
        val persisted = providerRepository.getProviderConfig(providerId)
        val health = getProviderHealthUseCase(providerId)
        val healthStatus = when {
            health is Result.Success && health.data.isOperational -> ProviderHealthStatus.HEALTHY
            health is Result.Failure -> ProviderHealthStatus.UNHEALTHY
            else -> mapStatus(providerManager.providerStatuses.value[providerId] ?: baseStatusFor(providerId))
        }
        // On-device providers report readiness from the live model file, not from secrets.
        val liveProvider = providerRegistry.getProvider(providerId)
        val downloadable = liveProvider as? ModelDownloadable
        val hasCredential = persisted?.secrets?.values?.any { s -> s.isNotBlank() } == true
        val ready = if (downloadable != null) downloadable.isModelReady else hasCredential
        val updatedProviders = currentState.providers.map {
            if (it.id == providerId) it.copy(
                isEnabled = persisted?.settings?.get("isEnabled")?.toBoolean() ?: it.isEnabled,
                selectedModel = persisted?.settings?.get("selectedModel") ?: it.selectedModel,
                apiKeyConfigured = ready,
                isConnected = ready,
                modelDownloaded = downloadable?.isModelReady ?: it.modelDownloaded,
                healthStatus = healthStatus
            ) else it
        }
        _uiState.value = currentState.copy(providers = updatedProviders)
    }

    /**
     * Builds a [ProviderConfiguration] from the provider's metadata-declared
     * [com.bangersoul.aivance.sdk.core.ConfigField]s (T-03). Each entered field
     * is routed exactly like onboarding:
     *  - PASSWORD/sensitive fields (e.g. Adzuna `appKey`, USAJobs `apiKey`) go
     *    into [ProviderConfiguration.secrets] — encrypted at rest,
     *  - everything else (e.g. Adzuna's non-secret `appId`, model names) goes
     *    into [ProviderConfiguration.settings].
     *
     * Replaces the previous hardcoded single-API-key input with an `adzuna`
     * "appId:appKey" special-case.
     */
    private fun buildConfig(
        provider: ProviderInfo?,
        credentials: Map<String, String> = emptyMap(),
        enabled: Boolean
    ): ProviderConfiguration {
        val settings = mutableMapOf(
            "isEnabled" to enabled.toString(),
            "selectedModel" to (provider?.selectedModel ?: ""),
            "model" to (provider?.selectedModel ?: "")
        )
        val secrets = mutableMapOf<String, String>()
        val fields = provider?.configFields.orEmpty()
        credentials.forEach { (key, rawValue) ->
            val value = rawValue.trim()
            if (value.isBlank()) return@forEach
            val field = fields.find { it.key == key }
            val isSecret = field?.isSensitive == true || field?.fieldType == FieldType.PASSWORD
            if (isSecret) secrets[key] = value else settings[key] = value
        }
        return ProviderConfiguration(
            providerId = provider?.id ?: "",
            settings = settings,
            secrets = secrets
        )
    }

    /**
     * Masks a credential for display: keeps the first 4 and last 4 characters
     * so users can confirm which key is configured without exposing it.
     */
    private fun maskKey(key: String): String {
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••${key.takeLast(4)}"
    }

    private companion object {
        /** Headroom above the file size used to decide whether a model fits. */
        const val STORAGE_HEADROOM_PERCENT = 15L

        fun withHeadroom(bytes: Long): Long = bytes + (bytes * STORAGE_HEADROOM_PERCENT / 100L)
    }

    private fun baseStatusFor(providerId: String): ProviderStatus {
        return providerRegistry.getProvider(providerId)?.status ?: ProviderStatus.Uninitialized
    }

    private fun mapStatus(status: ProviderStatus): ProviderHealthStatus = when (status) {
        ProviderStatus.Active, ProviderStatus.Ready, ProviderStatus.Healthy -> ProviderHealthStatus.HEALTHY
        ProviderStatus.Degraded -> ProviderHealthStatus.DEGRADED
        ProviderStatus.Uninitialized, ProviderStatus.Initializing -> ProviderHealthStatus.UNKNOWN
        else -> ProviderHealthStatus.UNHEALTHY
    }
}
