package com.bangersoul.aivance.core.domain.usecase.provider

import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.usecase.UseCase
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import javax.inject.Inject

/** Whether the user can be served by an AI provider right now. */
data class AiProviderAvailability(
    val isConfigured: Boolean,
    /** The provider that will serve the next AI request, when there is one. */
    val providerName: String? = null
)

/**
 * Pre-flight for AI-backed actions on the "continue without AI providers" path
 * (B2).
 *
 * Provider-optional onboarding is a supported choice, but every AI action used
 * to run anyway and fail with a raw transport message ("Streaming API Error"
 * from `openrouter/auto` with no key). Callers use this to explain the
 * requirement up front instead of surfacing a provider stack trace.
 *
 * Only AI chat providers count — a configured job board must never be mistaken
 * for an AI provider.
 */
class GetAiProviderAvailabilityUseCase @Inject constructor(
    private val providerManager: ProviderManager,
    private val providerRegistry: ProviderRegistry
) : UseCase<Unit, CoreResult<AiProviderAvailability>>() {

    override suspend operator fun invoke(input: Unit): CoreResult<AiProviderAvailability> =
        runCatchingCore {
            val aiProviderIds = providerRegistry
                .getProvidersByCapability(ProviderCapability.AI.Chat)
                .map { it.metadata.id }
                .toSet()

            val ready = providerManager.providerStatuses.value.entries.firstOrNull { (id, status) ->
                id in aiProviderIds && status in OPERATIONAL_STATUSES
            }

            AiProviderAvailability(
                isConfigured = ready != null,
                providerName = ready?.key
            )
        }

    private companion object {
        val OPERATIONAL_STATUSES = setOf(
            ProviderStatus.Ready,
            ProviderStatus.Active,
            ProviderStatus.Healthy
        )
    }
}
