package com.bangersoul.aivance.sdk.infrastructure

import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.sdk.api.ModelDownloadable
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.BaseProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle and orchestration of all registered providers.
 *
 * This manager ensures that providers transition through their lifecycle states
 * in a controlled and thread-safe manner.
 */
@Singleton
class ProviderManager @Inject constructor(
    private val registry: ProviderRegistry
) {
    private val orchestrator = LifecycleOrchestrator()

    private val _providerStatuses = MutableStateFlow<Map<String, ProviderStatus>>(emptyMap())

    /**
     * Reactive map of all registered providers and their current status.
     */
    val providerStatuses: StateFlow<Map<String, ProviderStatus>> = _providerStatuses.asStateFlow()

    init {
        // Initialize status map
        val initialMap = mutableMapOf<String, ProviderStatus>()
        registry.getAllProviders().forEach { provider ->
            initialMap[provider.metadata.id] = provider.status
        }
        _providerStatuses.value = initialMap
    }

    /**
     * Initializes all currently registered providers.
     * Moves providers from [ProviderStatus.Uninitialized] to [ProviderStatus.Ready].
     */
    suspend fun initializeAll() {
        registry.getAllProviders().forEach { provider ->
            try {
                orchestrator.transitionTo(provider, ProviderStatus.Ready)
                updateInternalStatus(provider.metadata.id, provider.status)
            } catch (e: Exception) {
                updateInternalStatus(provider.metadata.id, ProviderStatus.Error)
            }
        }
    }

    /**
     * Starts a specific provider by its ID.
     * Moves the provider to [ProviderStatus.Active], ensuring it is initialized first if necessary.
     *
     * @param id The unique identifier of the provider to start.
     */
    suspend fun startProvider(id: String) {
        registry.getProvider(id)?.let { provider ->
            try {
                orchestrator.transitionTo(provider, ProviderStatus.Active)
                updateInternalStatus(id, provider.status)
            } catch (e: Exception) {
                updateInternalStatus(id, ProviderStatus.Error)
            }
        }
    }

    /**
     * Stops a specific provider by its ID.
     * Moves the provider from [ProviderStatus.Active] back to [ProviderStatus.Ready].
     *
     * @param id The unique identifier of the provider to stop.
     */
    suspend fun stopProvider(id: String) {
        registry.getProvider(id)?.let { provider ->
            try {
                orchestrator.transitionTo(provider, ProviderStatus.Ready)
                updateInternalStatus(id, provider.status)
            } catch (e: Exception) {
                updateInternalStatus(id, ProviderStatus.Error)
            }
        }
    }

    /**
     * Validates a provider configuration without persisting it.
     * Useful during onboarding or settings updates.
     *
     * The candidate [config] is applied to the live provider before initialization
     * and health checking, so validation genuinely exercises the entered credentials.
     */
    suspend fun validateProvider(id: String, config: ProviderConfiguration): Result<Unit> {
        val provider = registry.getProvider(id) ?: return Result.Failure(
            com.bangersoul.aivance.core.common.result.DomainError("Provider $id not found")
        )

        return try {
            updateInternalStatus(id, ProviderStatus.Initializing)

            // Apply the candidate config so onInitialize/checkHealth run against the
            // entered credentials (previously the config was silently ignored).
            provider.applyConfiguration(config)
            provider.onInitialize()
            val health = provider.checkHealth()

            updateInternalStatus(id, health)

            if (health == ProviderStatus.Ready || health == ProviderStatus.Active) {
                Result.Success(Unit)
            } else {
                Result.Failure(com.bangersoul.aivance.core.common.result.ProviderError(
                    providerId = id, message = friendlyValidationMessage(health)
                ))
            }
        } catch (e: Exception) {
            updateInternalStatus(id, ProviderStatus.Error)
            Result.Failure(com.bangersoul.aivance.core.common.result.ProviderError(
                providerId = id, message = e.message ?: "Validation failed", cause = e
            ))
        }
    }

    /**
     * Maps a rejected provider status to a message an end user can actually
     * act on, instead of leaking the raw lifecycle status name.
     */
    private fun friendlyValidationMessage(status: ProviderStatus): String {
        return when (status) {
            ProviderStatus.InvalidConfiguration ->
                "Configuration is incomplete — please fill in every required field."
            ProviderStatus.Error ->
                "Invalid API key or provider unreachable — please check and retry."
            ProviderStatus.Degraded ->
                "Provider is temporarily unavailable — please check your credentials and retry."
            else -> "Validation failed with status: $status"
        }
    }

    /**
     * Reconfigures a live provider with new credentials and re-initializes it.
     *
     * Closes the gap where [com.bangersoul.aivance.core.data.repository.ProviderRepositoryImpl.saveProviderConfig]
     * persisted credentials but never re-applied them to the DI-singleton provider
     * instance, so configured providers stayed unconfigured until app restart.
     *
     * @param id The provider identifier.
     * @param config The new configuration (secrets included).
     * @return Success if the provider reached Ready/Active after re-init.
     */
    suspend fun reconfigure(id: String, config: ProviderConfiguration): Result<Unit> {
        val provider = registry.getProvider(id) ?: return Result.Failure(
            com.bangersoul.aivance.core.common.result.DomainError("Provider $id not found")
        )

        return try {
            provider.applyConfiguration(config)
            // Force re-initialization so the new credentials take effect.
            provider.updateStatus(ProviderStatus.Uninitialized)
            orchestrator.transitionTo(provider, ProviderStatus.Ready)
            updateInternalStatus(id, provider.status)

            if (provider.status == ProviderStatus.Ready || provider.status == ProviderStatus.Active) {
                Result.Success(Unit)
            } else {
                Result.Failure(com.bangersoul.aivance.core.common.result.ProviderError(
                    providerId = id,
                    message = "Reconfiguration resulted in status ${provider.status}",
                ))
            }
        } catch (e: Exception) {
            updateInternalStatus(id, ProviderStatus.Error)
            Result.Failure(com.bangersoul.aivance.core.common.result.ProviderError(
                providerId = id, message = e.message ?: "Reconfiguration failed", cause = e
            ))
        }
    }

    /**
     * Retrieves the best available provider for a given capability.
     *
     * @param capability The capability required.
     * @return The best provider matching the capability, or null if none are available.
     */
    fun getBestProviderFor(capability: ProviderCapability): BaseProvider? {
        // Stable ordering first: the registry is backed by a ConcurrentHashMap,
        // so getProvidersByCapability returns candidates in an undefined order.
        // Sorting by the immutable provider id makes selection deterministic —
        // two providers in the SAME priority tier always resolve to the same
        // one regardless of map/Set iteration order (previously the winner was
        // whichever the map happened to yield first).
        val candidates = registry.getProvidersByCapability(capability)
            .sortedBy { it.metadata.id }

        // Priority tiers, best-first:
        //   1. Active  && holds real credentials (user-supplied key)
        //   2. Ready   && holds real credentials
        //   3. Active  && isConfigured (includes keyless providers like Ollama)
        //   4. Ready   && isConfigured
        //   5. Active / Ready / anything
        // The credentials tiers guarantee a keyed provider (e.g. Groq with a real
        // key) wins over a keyless one (e.g. Ollama pointing at localhost), instead
        // of leaving selection to registry iteration order.
        return candidates.firstOrNull { it.status == ProviderStatus.Active && it.hasCredentials }
            ?: candidates.firstOrNull { it.status == ProviderStatus.Ready && it.hasCredentials }
            ?: candidates.firstOrNull { it.status == ProviderStatus.Active && it.isConfigured }
            ?: candidates.firstOrNull { it.status == ProviderStatus.Ready && it.isConfigured }
            ?: candidates.firstOrNull { it.status == ProviderStatus.Active }
            ?: candidates.firstOrNull { it.status == ProviderStatus.Ready }
            ?: candidates.firstOrNull()
    }

    /**
     * Retrieves the best ready on-device provider for a given capability.
     *
     * On-device providers (implementing [ModelDownloadable], e.g. the offline
     * Gemma model) are keyless and work with **zero connectivity** once their
     * model file is present ([ModelDownloadable.isModelReady]). This is the
     * offline fallback target for features like the AI Assistant when no cloud
     * provider is configured, or when the configured cloud provider is
     * unreachable (airplane mode, provider outage).
     *
     * Selection mirrors [getBestProviderFor]: Active beats Ready, falling back
     * to any provider whose model is downloaded. A missing model is never
     * handed out (the caller would only get failures).
     *
     * @param capability The capability required.
     * @return The best ready on-device provider, or null when no model is
     *   downloaded.
     */
    fun getOnDeviceProviderFor(capability: ProviderCapability): BaseProvider? {
        val candidates = registry.getProvidersByCapability(capability)
            .filter { it is ModelDownloadable && it.isModelReady }

        return candidates.firstOrNull { it.status == ProviderStatus.Active }
            ?: candidates.firstOrNull { it.status == ProviderStatus.Ready }
            ?: candidates.firstOrNull()
    }

    /**
     * Triggers a health check for a specific provider.
     *
     * @param id The unique identifier of the provider.
     */
    suspend fun triggerHealthCheck(id: String) {
        val provider = registry.getProvider(id) ?: return
        try {
            val status = provider.checkHealth()
            updateInternalStatus(id, status)
        } catch (e: Exception) {
            updateInternalStatus(id, ProviderStatus.Error)
        }
    }

    private fun updateInternalStatus(id: String, status: ProviderStatus) {
        _providerStatuses.update { current ->
            current.toMutableMap().apply { put(id, status) }
        }
    }

    /**
     * Hybrid Router: intelligently selects between cloud and on-device providers
     * based on the estimated complexity of the task.
     * 
     * @param complexity If "LOW", prefers the on-device provider for speed/privacy.
     *                   If "HIGH", prefers the most powerful cloud provider.
     * @param capability The required provider capability.
     */
    fun getRoutedProvider(complexity: String, capability: ProviderCapability): BaseProvider? {
        return if (complexity.uppercase() == "LOW") {
            // Low complexity -> Prefer local first
            getOnDeviceProviderFor(capability) ?: getBestProviderFor(capability)
        } else {
            // High complexity -> Prefer cloud first
            getBestProviderFor(capability) ?: getOnDeviceProviderFor(capability)
        }
    }

    /**
     * Internal orchestrator that manages state transitions for providers.
     */
    private class LifecycleOrchestrator {
        private val providerLocks = ConcurrentHashMap<String, Mutex>()

        /**
         * Transitions a provider to the target status, ensuring all intermediate steps are taken.
         *
         * @param provider The provider to transition.
         * @param targetStatus The desired status.
         */
        suspend fun transitionTo(provider: BaseProvider, targetStatus: ProviderStatus) {
            val mutex = providerLocks.getOrPut(provider.metadata.id) { Mutex() }
            mutex.withLock {
                executeTransition(provider, targetStatus)
            }
        }

        private suspend fun executeTransition(provider: BaseProvider, targetStatus: ProviderStatus) {
            var currentStatus = provider.status
            if (currentStatus == targetStatus) return

            // Ensure Uninitialized -> Ready -> Active order
            when (targetStatus) {
                ProviderStatus.Ready -> {
                    if (currentStatus == ProviderStatus.Uninitialized) {
                        performInitialize(provider)
                    } else if (currentStatus == ProviderStatus.Active) {
                        performStop(provider)
                    }
                }
                ProviderStatus.Active -> {
                    if (currentStatus == ProviderStatus.Uninitialized) {
                        performInitialize(provider)
                        currentStatus = provider.status
                    }
                    if (currentStatus == ProviderStatus.Ready) {
                        performStart(provider)
                    }
                }
                ProviderStatus.Disposed -> {
                    performDispose(provider)
                }
                else -> {}
            }
        }

        private suspend fun performInitialize(provider: BaseProvider) {
            provider.updateStatus(ProviderStatus.Initializing)
            try {
                provider.onInitialize()
                // Respect providers that self-mark as InvalidConfiguration during
                // onInitialize (e.g. missing credentials), so search aggregation
                // and startup flows skip them instead of firing doomed requests.
                if (provider.status == ProviderStatus.InvalidConfiguration) {
                    return
                }
                provider.updateStatus(ProviderStatus.Ready)
            } catch (ignored: Exception) {
                provider.updateStatus(ProviderStatus.Error)
            }
        }

        private suspend fun performStart(provider: BaseProvider) {
            try {
                provider.onStart()
                provider.updateStatus(ProviderStatus.Active)
            } catch (ignored: Exception) {
                provider.updateStatus(ProviderStatus.Error)
            }
        }

        private suspend fun performStop(provider: BaseProvider) {
            try {
                provider.onStop()
                provider.updateStatus(ProviderStatus.Ready)
            } catch (ignored: Exception) {
                provider.updateStatus(ProviderStatus.Error)
            }
        }

        private suspend fun performDispose(provider: BaseProvider) {
            try {
                provider.onDispose()
                provider.updateStatus(ProviderStatus.Disposed)
            } catch (ignored: Exception) {
                provider.updateStatus(ProviderStatus.Error)
            }
        }
    }

    /**
     * Resolves a provider satisfying [capability] using the canonical fallback chain:
     * Preferred/Primary -> Secondary Remote -> Local Fallback -> Graceful Degradation.
     */
    fun resolveCapabilityFallbackChain(
        capability: ProviderCapability,
        preferredId: String? = null
    ): List<BaseProvider> {
        val available = registry.getProvidersByCapability(capability)
            .filter { it.status == ProviderStatus.Active || it.status == ProviderStatus.Ready || it.status == ProviderStatus.Healthy }

        val chain = mutableListOf<BaseProvider>()
        preferredId?.let { id ->
            available.firstOrNull { it.metadata.id == id }?.let { chain.add(it) }
        }
        val isLocalProvider: (BaseProvider) -> Boolean = {
            it.metadata.isLocal || it.hasCapability(ProviderCapability.AI.LocalExecution)
        }
        val remotes = available.filter { !isLocalProvider(it) && it.metadata.id != preferredId }
        chain.addAll(remotes)
        val locals = available.filter { isLocalProvider(it) && it.metadata.id != preferredId }
        chain.addAll(locals)

        return chain
    }
}
