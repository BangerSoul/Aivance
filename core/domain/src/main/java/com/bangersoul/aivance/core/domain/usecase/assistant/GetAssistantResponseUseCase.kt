package com.bangersoul.aivance.core.domain.usecase.assistant

import com.bangersoul.aivance.core.common.enums.MessageRole
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.assistant.AssistantContextEngine
import com.bangersoul.aivance.core.domain.assistant.CapabilityRouter
import com.bangersoul.aivance.core.domain.usecase.UseCase
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.model.AiMessage as SdkAiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

data class AssistantRequest(
    val conversationId: String,
    val userMessage: String,
    /**
     * The user's original message, before any orchestrator wrapping. Intent
     * detection runs against this raw text: the orchestrated [userMessage]
     * embeds platform context (e.g. "Latest ATS Score: 0%"), and keyword
     * matching against it would misroute every prompt to ANALYZE_RESUME and
     * starve the LLM of open-ended conversation.
     */
    val rawUserMessage: String = userMessage
)

/**
 * Orchestrates the AI Assistant response generation.
 *
 * Two-stage pipeline:
 *  1. Intent detection + capability routing — so the Assistant can execute
 *     platform workflows (resume analysis, job search, roadmap) directly.
 *  2. Context-aware LLM fallback for open-ended conversation.
 *
 * Two entry points:
 *  - [invoke] returns the full response as a single [CoreResult].
 *  - [stream] emits token chunks in real time (when the active provider
 *    supports [ProviderCapability.AI.Streaming]), falling back to a single
 *    emission for providers that only support one-shot generation.
 */
class GetAssistantResponseUseCase @Inject constructor(
    private val contextEngine: AssistantContextEngine,
    private val capabilityRouter: CapabilityRouter,
    private val providerManager: ProviderManager
) : UseCase<AssistantRequest, CoreResult<String>>() {

    override suspend operator fun invoke(input: AssistantRequest): CoreResult<String> = runCatchingCore {
        generateResponse(input.userMessage, input.rawUserMessage)
    }

    /**
     * Streaming variant. Every emitted string is a partial token chunk; the
     * stream completes when the full response has been generated. Routed
     * intents (resume analysis, job search, roadmap, interview) execute
     * synchronously and emit their result as a single chunk.
     *
     * Falls back gracefully to one-shot generation when no streaming-capable
     * provider is configured, so the Assistant never breaks on older setups.
     */
    fun stream(input: AssistantRequest): Flow<String> = flow {
        // Stage 1: detect + execute concrete intents before falling back to chat.
        routeOrNull(input.rawUserMessage)?.let {
            emit(it)
            return@flow
        }

        // Stage 2: context-aware LLM chat with the active provider.
        val platformContext = contextEngine.buildActiveContext()
        val systemPrompt = buildSystemPrompt(platformContext)

        val sdkMessages = listOf(
            SdkAiMessage(MessageRole.SYSTEM, systemPrompt),
            SdkAiMessage(MessageRole.USER, input.userMessage)
        )

        var primaryProvider: AIProvider? = null

        // Prefer a streaming-capable provider for real-time token delivery.
        val streamingProvider =
            providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) as? AIProvider

        val primaryResult: StreamOutcome = if (streamingProvider != null) {
            primaryProvider = streamingProvider
            streamChat(streamingProvider, sdkMessages) { chunk -> emit(chunk) }
        } else {
            // Non-streaming provider emits the full answer once.
            val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            primaryProvider = provider
            val text = provider?.chat(sdkMessages)?.getOrNull().orEmpty()
            if (text.isNotBlank()) emit(text)
            StreamOutcome(text, failedAfterEmitting = false)
        }

        // Case B (fail-after-partial): the primary provider emitted real tokens
        // and THEN failed. We must NOT silently present the truncated text as a
        // complete answer, and we must NOT switch providers and concatenate a
        // second provider's output onto the first's partial tokens (the
        // streamChat contract has no safe mid-stream continuation point). Preserve
        // the partial output already emitted and end the flow with a truthful
        // terminal failure so the UI shows a retry/failed state (see
        // AssistantViewModel: streamFailed branch).
        if (primaryResult.failedAfterEmitting) {
            throw StreamInterruptedException(primaryResult.text)
        }

        var fullResponse = primaryResult.text

        // Zero-connectivity fallback: the on-device model (Gemma) works without
        // any network once its model file is downloaded. Only reached when the
        // primary produced NOTHING (Case A: failed before the first token, or no
        // provider) — safe because there is no partial output to corrupt.
        if (fullResponse.isBlank()) {
            val onDeviceProvider =
                providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Streaming) as? AIProvider
                    ?: providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            // Guard against re-trying the same instance (e.g. when the on-device
            // model is already the best configured provider).
            if (onDeviceProvider != null && onDeviceProvider !== primaryProvider) {
                val fallback = streamChat(onDeviceProvider, sdkMessages) { chunk -> emit(chunk) }
                // If the on-device fallback itself fails after emitting, surface
                // that truthfully too rather than masking it as completion.
                if (fallback.failedAfterEmitting) {
                    throw StreamInterruptedException(fallback.text)
                }
                fullResponse = fallback.text
            }
        }

        if (fullResponse.isBlank()) {
            emit(generateCopilotFallback(input.userMessage, platformContext))
        }
    }

    /**
     * Result of a single provider streaming attempt.
     *
     * @property text the accumulated tokens actually emitted.
     * @property failedAfterEmitting true when the provider emitted at least one
     *   token and THEN errored — the caller must treat this as a truthful
     *   terminal failure (partial output preserved), never as a completed answer
     *   and never as a cue to concatenate a different provider's output.
     */
    private data class StreamOutcome(
        val text: String,
        val failedAfterEmitting: Boolean
    )

    /**
     * Streams a chat response from [provider], emitting each chunk via [emit].
     *
     * Failure semantics (truthful, no fabricated completion):
     *  - Failure BEFORE any token → returns blank text, [StreamOutcome.failedAfterEmitting]
     *    = false, letting the caller safely fall back to another provider.
     *  - Failure AFTER partial tokens → returns the partial text with
     *    [StreamOutcome.failedAfterEmitting] = true so the caller ends in a
     *    truthful terminal failure instead of presenting a truncated answer.
     *  - [kotlinx.coroutines.CancellationException] is re-thrown, never swallowed,
     *    so user cancellation stays cancellation and structured concurrency is
     *    preserved (it must not degrade into provider fallback or a fake error).
     */
    private suspend fun streamChat(
        provider: AIProvider,
        messages: List<SdkAiMessage>,
        emit: suspend (String) -> Unit
    ): StreamOutcome {
        var fullResponse = ""
        var emittedAny = false
        try {
            provider.streamChat(messages).collect { chunkResult ->
                when (chunkResult) {
                    is Result.Success -> {
                        emit(chunkResult.data)
                        if (chunkResult.data.isNotEmpty()) emittedAny = true
                        fullResponse += chunkResult.data
                    }
                    // A malformed/failed chunk terminates the stream. If we had
                    // already emitted, this is a mid-stream failure (Case B/D).
                    is Result.Failure -> return@collect run {
                        throw StreamInterruptedException(fullResponse)
                    }
                }
            }
        } catch (ce: kotlin.coroutines.cancellation.CancellationException) {
            // Case C: propagate cancellation unchanged.
            throw ce
        } catch (interrupted: StreamInterruptedException) {
            return StreamOutcome(interrupted.partial, failedAfterEmitting = interrupted.partial.isNotBlank())
        } catch (_: Exception) {
            // Provider threw (network drop, malformed stream). Whether we emitted
            // anything decides if this is a safe-to-fallback pre-token failure or
            // a mid-stream failure that must surface truthfully.
            return StreamOutcome(fullResponse, failedAfterEmitting = emittedAny)
        }
        return StreamOutcome(fullResponse, failedAfterEmitting = false)
    }

    /**
     * Signals that a provider stream ended in failure after emitting partial
     * output. Carries the partial text so the caller can preserve what the user
     * already saw while still reporting a truthful terminal failure.
     */
    private class StreamInterruptedException(val partial: String) : Exception()

    /**
     * Runs the full generation pipeline for one-shot (non-streaming) callers.
     * Routed intents short-circuit; everything else goes to the context-aware
     * LLM chat path.
     */
    private suspend fun generateResponse(userMessage: String, rawUserMessage: String): String {
        // Stage 1: detect + execute concrete intents before falling back to chat.
        routeOrNull(rawUserMessage)?.let { return it }

        // Stage 2: context-aware LLM chat with the active provider.
        val platformContext = contextEngine.buildActiveContext()
        val systemPrompt = buildSystemPrompt(platformContext)
        val prompt = "$systemPrompt\n\nUser: $userMessage"

        val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
        val onDeviceProvider =
            providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Chat) as? AIProvider
        val response = provider?.generateText(prompt)?.getOrNull()

        // Zero-connectivity fallback: the on-device model (Gemma) works without
        // any network once its model file is downloaded. Guard against re-trying
        // the same instance (when the on-device model is already the best chat
        // provider).
        if (response.isNullOrBlank() && onDeviceProvider != null && onDeviceProvider !== provider) {
            onDeviceProvider.generateText(prompt)?.getOrNull()?.let { return it }
        }

        if (!response.isNullOrBlank()) return response

        // No provider produced an answer: surface a clear error only when there
        // is truly nothing to route to (no cloud provider and no on-device model).
        if (provider == null && onDeviceProvider == null) {
            throw Exception("No AI provider configured — open Settings → Providers to connect one.")
        }
        return generateCopilotFallback(userMessage, platformContext)
    }

    /**
     * Runs Stage 1 of the pipeline: intent detection + capability routing.
     * Returns the routed result when the intent was executable, null when the
     * message should fall back to the context-aware LLM chat path.
     */
    private suspend fun routeOrNull(message: String): String? {
        val intent = detectIntent(message) ?: return null
        return capabilityRouter.routeIntent(intent.first, intent.second).getOrNull()
    }

    private fun buildSystemPrompt(platformContext: String): String = """
            You are the AiVance Career Assistant.
            Your goal is to help the user manage their job applications, resumes, and career growth.

            Current Platform Context:
            $platformContext

            Based on this context, provide proactive, data-driven advice.
            If the user asks to perform an action (e.g., "Analyze my resume"), identify the intent.
        """.trimIndent()

    /**
     * Maps a free-form user message to a (intent, params) pair using keyword
     * detection. Returns null when the message should fall back to chat.
     */
    private suspend fun detectIntent(message: String): Pair<String, Map<String, String>>? {
        val lower = message.lowercase().trim()
        val keywordIntent = when {
            (lower.contains("resume") && (lower.contains("analyz") || lower.contains("score") || lower.contains("optimiz"))) ||
                lower.contains("ats") -> {
                val jd = extractAfter(message, listOf("against", "for", "with"))
                "ANALYZE_RESUME" to mapOf("jobDescription" to jd)
            }
            (lower.contains("job") || lower.contains("role") || lower.contains("position")) &&
                (lower.contains("search") || lower.contains("find") || lower.contains("look for") || lower.contains("match")) -> {
                val query = extractAfter(message, listOf("for", "as", "matching"))
                "SEARCH_JOBS" to mapOf("query" to query.ifBlank { message })
            }
            lower.contains("roadmap") || lower.contains("career path") || lower.contains("plan my career") -> {
                val role = extractAfter(message, listOf("to", "toward", "for"))
                "GENERATE_ROADMAP" to mapOf("targetRole" to role)
            }
            lower.contains("interview") && (lower.contains("mock") || lower.contains("practice") || lower.contains("prepare")) -> {
                val role = extractAfter(message, listOf("for", "as"))
                "START_INTERVIEW" to mapOf("targetRole" to role)
            }
            else -> null
        }

        val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            ?: providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            ?: return keywordIntent

        val routingPrompt = "You are a routing agent for the AiVance Career Assistant. Analyze the user's message and categorize it into one of these intents: ANALYZE_RESUME, SEARCH_JOBS, GENERATE_ROADMAP, START_INTERVIEW, or CHAT. Response Format: JSON only. Example: {\"intent\": \"SEARCH_JOBS\", \"params\": {\"query\": \"Senior Android Developer\"}}. User Message: \"$message\""

        val response = provider.generateText(routingPrompt).getOrNull() ?: return keywordIntent
        
        return try {
            val start = response.indexOf("{")
            val end = response.lastIndexOf("}") + 1
            if (start == -1 || end == 0) return keywordIntent
            val json = response.substring(start, end)
            if (json.contains("CHAT")) return null
            
            val intent = if (json.contains("ANALYZE_RESUME")) "ANALYZE_RESUME"
            else if (json.contains("SEARCH_JOBS")) "SEARCH_JOBS"
            else if (json.contains("GENERATE_ROADMAP")) "GENERATE_ROADMAP"
            else if (json.contains("START_INTERVIEW")) "START_INTERVIEW"
            else null
            
            if (intent == null) return keywordIntent
            intent to emptyMap()
        } catch (e: Exception) {
            keywordIntent
        }
    }

    private fun extractAfter(message: String, markers: List<String>): String {
        val lower = message.lowercase()
        markers.forEach { marker ->
            val idx = lower.indexOf(marker)
            if (idx >= 0) {
                val rest = message.substring(idx + marker.length).trim()
                if (rest.isNotBlank()) return rest.trimEnd('?', '.', '!')
            }
        }
        return ""
    }

    private fun generateCopilotFallback(message: String, platformContext: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") -> {
                "Hello! I am your AiVance Copilot. I am actively monitoring your career workspace.\n\n" +
                "How can I assist you today? You can ask me to search jobs, analyze your resume, prepare for interviews, or optimize your applications!"
            }
            lower.contains("resume") || lower.contains("cv") || lower.contains("ats") -> {
                "Here is guidance based on your active resume context:\n\n" +
                "• Highlight top core technical competencies in your summary section.\n" +
                "• Quantify your achievements (e.g., 'Reduced response latency by 40%').\n" +
                "• Run an ATS Scan in the Resume Engine to tailor your resume for specific positions."
            }
            lower.contains("job") || lower.contains("search") || lower.contains("apply") -> {
                "Here are strategic recommendations for your job search:\n\n" +
                "1. Filter jobs in the Discovery tab by location and workplace preference (Remote, Hybrid, On-site).\n" +
                "2. Maintain active applications in your Pipeline Tracker.\n" +
                "3. Use the Prep Studio to practice mock questions for upcoming interview rounds."
            }
            else -> {
                "I've evaluated your career profile:\n\n" +
                "1. Tailor your resume for target applications using the Resume Engine.\n" +
                "2. Practice interactive mock interviews in the Prep Studio.\n" +
                "3. Track your active applications and scheduled interviews in the Pipeline board."
            }
        }
    }
}
