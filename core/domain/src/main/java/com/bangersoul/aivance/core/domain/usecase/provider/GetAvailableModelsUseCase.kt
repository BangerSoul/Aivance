package com.bangersoul.aivance.core.domain.usecase.provider

import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.ValidationError
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.usecase.UseCase
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import javax.inject.Inject

/**
 * Retrieves the list of available models for a given provider.
 *
 * Business rules:
 * - Provider must be registered and initialized.
 * - Returns model identifiers as strings.
 * - Delegates to the provider's own API for available models.
 */
class GetAvailableModelsUseCase @Inject constructor(
    private val providerManager: ProviderManager
) : UseCase<String, CoreResult<List<String>>>() {

    override suspend operator fun invoke(providerId: String): CoreResult<List<String>> {
        if (providerId.isBlank()) {
            return Result.Failure(ValidationError("providerId", "Provider ID cannot be blank."))
        }

        return runCatchingCore {
            val provider = providerManager.getBestProviderFor(
                com.bangersoul.aivance.sdk.core.ProviderCapability.AI.TextGeneration
            ) as? AIProvider

            if (provider == null) {
                // Return default models per provider
                getDefaultModels(providerId)
            } else {
                val result = provider.listModels()
                when (result) {
                    is Result.Success -> result.data
                    is Result.Failure -> getDefaultModels(providerId)
                }
            }
        }
    }

    private fun getDefaultModels(providerId: String): List<String> {
        return when (providerId) {
            "GEMINI" -> listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash")
            "OPENAI" -> listOf("gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo")
            "GROQ" -> listOf("llama-3.3-70b-versatile", "mixtral-8x7b-32768", "gemma2-9b-it")
            "CLAUDE" -> listOf("claude-3-5-sonnet", "claude-3-haiku")
            "OLLAMA" -> listOf("llama3", "mistral", "codellama", "phi3")
            "OPENROUTER" -> listOf("openrouter/auto", "meta-llama/llama-3.1-70b-instruct")
            "GEMMA" -> listOf("gemma-3n-e2b-it-int4")
            else -> listOf("unknown")
        }
    }
}
