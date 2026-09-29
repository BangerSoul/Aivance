package com.bangersoul.aivance.sdk.infrastructure

import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.BaseProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating [BaseProvider] instances.
 *
 * Supports a plug-and-play architecture by allowing registration of custom provider factories.
 *
 * ## Factory contract (enforced at the SDK level)
 *
 * Every provider module (AI, JOB, ENRICHMENT) must register one
 * `@Provides @IntoMap @StringKey(providerId)` binding returning a
 * [Factory] — e.g. `:core:ai-providers` registers `gemini`…`gemma`,
 * `:core:enrichment-providers` registers `hunter`, and `:core:job-providers`
 * registers `adzuna`/`usajobs`. Without the binding, [createProvider] throws.
 *
 * The factory receives the config map produced by [ProviderConfiguration.toFactoryMap]:
 * - `"settings"` — plaintext, non-sensitive preferences (model name, base URL,
 *   non-secret IDs such as Adzuna's `appId`, `isEnabled`, …).
 * - `"secrets"` — sensitive credentials (API keys), encrypted at rest via
 *   `:core:datastore` → `SecretsManager`. Read with [credential], which checks
 *   `secrets` first and falls back to `settings` for configs saved before the
 *   secret-routing fix.
 */
@Singleton
class ProviderFactory @Inject constructor(
    private val injectedFactories: Map<String, @JvmSuppressWildcards Factory>
) {

    /**
     * Interface for individual provider factories.
     */
    fun interface Factory {
        /**
         * Creates a new instance of a provider.
         *
         * @param config Optional configuration parameters for the provider.
         * @return A new instance of [BaseProvider].
         */
        fun create(config: Map<String, Any>?): BaseProvider
    }

    private val customFactories = ConcurrentHashMap<String, Factory>()

    /**
     * Registers a factory for a specific provider type.
     *
     * @param type The unique type identifier for the provider (e.g., "openai", "gemini").
     * @param factory The factory implementation responsible for creating instances.
     */
    fun registerFactory(type: String, factory: Factory) {
        customFactories[type] = factory
    }

    /**
     * Unregisters a factory for a specific provider type.
     *
     * @param type The type identifier to unregister.
     */
    fun unregisterFactory(type: String) {
        customFactories.remove(type)
    }

    /**
     * Creates a provider instance for the given type and configuration.
     *
     * @param type The type identifier of the provider to create.
     * @param config Optional configuration for the provider instance.
     * @return A new [BaseProvider] instance.
     * @throws IllegalArgumentException if no factory is registered for the given type.
     */
    fun createProvider(type: String, config: Map<String, Any>? = null): BaseProvider {
        val factory = customFactories[type] ?: injectedFactories[type]
            ?: throw IllegalArgumentException("No factory registered for provider type: $type")
        return factory.create(config)
    }

    /**
     * Creates a provider instance from a typed [ProviderConfiguration], routing
     * its `settings`/`secrets` maps to the registered factory's config-map
     * contract via [ProviderConfiguration.toFactoryMap].
     *
     * This is the formal, type-safe entry point for constructing providers from
     * persisted configuration (hydration, tests, previews) — see the class KDoc
     * for the registration contract.
     *
     * @param config The provider configuration (id, settings, secrets).
     * @return A new [BaseProvider] instance.
     * @throws IllegalArgumentException if no factory is registered for [ProviderConfiguration.providerId].
     */
    fun createProvider(config: ProviderConfiguration): BaseProvider {
        return createProvider(config.providerId, config.toFactoryMap())
    }

    /**
     * Checks if a factory is registered for a specific provider type.
     *
     * @param type The type identifier to check.
     * @return True if a factory exists, false otherwise.
     */
    fun hasFactory(type: String): Boolean {
        return customFactories.containsKey(type) || injectedFactories.containsKey(type)
    }
}

/**
 * Serializes a [ProviderConfiguration] into the factory config-map contract.
 *
 * Factories receive `"settings"` (plaintext preferences) and `"secrets"`
 * (encrypted credentials) as separate maps so they can route each field
 * correctly — see the [ProviderFactory] class KDoc.
 */
fun ProviderConfiguration.toFactoryMap(): Map<String, Any> = mapOf(
    "settings" to settings,
    "secrets" to secrets
)

/**
 * Reads a single credential from a factory config map produced by
 * [ProviderConfiguration.toFactoryMap].
 *
 * Checks `"secrets"` first (the encrypted, current location for credentials),
 * then `"settings"` (fallback for configs saved before the secret-routing fix).
 *
 * @param key The credential field key (e.g. `apiKey`, `appKey`).
 * @param default Value returned when the key is absent from both maps.
 */
fun Map<String, Any>?.credential(key: String, default: String = ""): String {
    val secrets = this?.get("secrets") as? Map<String, String> ?: emptyMap()
    val settings = this?.get("settings") as? Map<String, String> ?: emptyMap()
    return secrets[key] ?: settings[key] ?: default
}
