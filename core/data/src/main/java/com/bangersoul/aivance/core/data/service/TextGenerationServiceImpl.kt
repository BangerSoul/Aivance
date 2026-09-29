package com.bangersoul.aivance.core.data.service

import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.service.TextGenerationService
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [TextGenerationService].
 *
 * Delegates provider selection to the canonical [ProviderManager] resolver
 * instead of scanning the registry itself. This gives text generation the same
 * deterministic, capability-aware, credentials-preferring selection every other
 * AI feature already uses (assistant, ATS, job-fit, CRM agents):
 *
 *  - Selection is **deterministic**: [ProviderManager.getBestProviderFor] sorts
 *    candidates by id, so the winner never depends on registry/Set iteration
 *    order.
 *  - Selection is **capability-aware**: only providers advertising
 *    [ProviderCapability.AI.TextGeneration] are considered.
 *  - Selection is **preference/credentials-aware**: a configured, keyed provider
 *    (the user's real choice) outranks a keyless one; Active outranks Ready.
 *  - Selection is **fallback-aware**: if the best provider's request fails, the
 *    remaining capable providers are tried in the same deterministic order via
 *    [ProviderManager.resolveCapabilityFallbackChain] before giving up.
 *  - Failures are **truthful**: no provider → explicit failure; every provider
 *    failing → the last real error is preserved (no fabricated success).
 *
 * The previous implementation picked the first [com.bangersoul.aivance.sdk.core.ProviderStatus.Ready]
 * `AIProvider` from the registry's unordered map — non-deterministic when
 * several were configured, and it silently skipped providers that had already
 * been started (moved to Active).
 */
@Singleton
class TextGenerationServiceImpl @Inject constructor(
    private val providerManager: ProviderManager
) : TextGenerationService {

    private val capability = ProviderCapability.AI.TextGeneration

    override suspend fun generateText(prompt: String): CoreResult<String> {
        // Deterministic, credentials-preferring primary selection.
        val primary = providerManager.getBestProviderFor(capability) as? AIProvider
        if (primary == null) {
            Timber.w("No AI provider available for text generation")
            return Result.Failure(DomainError("No AI provider configured"))
        }

        // Ordered fallback chain (preferred/primary -> other remotes -> local),
        // deterministic because resolveCapabilityFallbackChain draws from the
        // same capability set. De-duplicated with the primary tried first so a
        // transient primary failure transparently rolls over to the next capable
        // provider instead of surfacing as a hard error.
        val chain = buildList {
            add(primary)
            providerManager.resolveCapabilityFallbackChain(capability, preferredId = primary.metadata.id)
                .filterIsInstance<AIProvider>()
                .forEach { if (it.metadata.id != primary.metadata.id) add(it) }
        }

        var lastError: com.bangersoul.aivance.core.common.result.CoreError? = null
        for (provider in chain) {
            when (val result = runProvider(provider, prompt)) {
                is Result.Success -> return result
                is Result.Failure -> lastError = result.error
            }
        }

        // Every capable provider failed — surface the real reason, never a
        // fabricated success. DomainError.message is provider-authored and does
        // not include credentials.
        return Result.Failure(lastError ?: DomainError("Text generation failed"))
    }

    private suspend fun runProvider(provider: AIProvider, prompt: String): CoreResult<String> {
        return try {
            when (val result = provider.generateText(prompt)) {
                is Result.Success -> result
                is Result.Failure -> {
                    // Log the provider id only — never the prompt or any secret.
                    Timber.w("Provider ${provider.metadata.id} text generation failed: ${result.error.message}")
                    Result.Failure(result.error)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Provider ${provider.metadata.id} threw during text generation")
            Result.Failure(DomainError("Text generation failed: ${e.message}", e))
        }
    }
}
