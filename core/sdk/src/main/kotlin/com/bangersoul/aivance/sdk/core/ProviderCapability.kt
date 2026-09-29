package com.bangersoul.aivance.sdk.core

/**
 * Represents the various capabilities an AI provider can support.
 */
sealed class ProviderCapability {
    data object TextAnalysis : ProviderCapability()
    data object ImageProcessing : ProviderCapability()
    data object JobSearch : ProviderCapability()
    data object RecruiterDiscovery : ProviderCapability()
    data object EmailVerification : ProviderCapability()

    sealed class AI : ProviderCapability() {
        data object Chat : AI()
        data object TextGeneration : AI()
        data object Vision : AI()
        data object Streaming : AI()
        data object FunctionCalling : AI()
        data object StructuredOutput : AI()
        data object ToolCalling : AI()
        data object Embeddings : AI()
        data object LongContext : AI()
        data object LocalExecution : AI()
    }

    /**
     * For providers with unique or specialized capabilities not covered by standard types.
     */
    data class Custom(val name: String) : ProviderCapability()
}
