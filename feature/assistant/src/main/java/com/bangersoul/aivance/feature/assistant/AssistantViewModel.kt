package com.bangersoul.aivance.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.AssistantJobContext
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.engine.ContextEngine
import com.bangersoul.aivance.core.domain.engine.IntentEngine
import com.bangersoul.aivance.core.domain.engine.PromptOrchestrator
import com.bangersoul.aivance.core.domain.repository.AssistantRepository
import com.bangersoul.aivance.core.domain.repository.ProviderRepository
import com.bangersoul.aivance.core.domain.usecase.assistant.AssistantRequest
import com.bangersoul.aivance.core.domain.usecase.assistant.GetAssistantResponseUseCase
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AssistantUiState {
    data object Idle : AssistantUiState
    data object Loading : AssistantUiState

    data class Chatting(
        val messages: List<AssistantChatMessage> = emptyList(),
        val isTyping: Boolean = false,
        val streamingContent: String? = null,
        val streamFailed: Boolean = false
    ) : AssistantUiState

    data class Error(val message: String) : AssistantUiState
}

data class AssistantChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ProviderStatusUi(
    val isReady: Boolean = false,
    val providerName: String? = null,
    val statusLabel: String = "No provider configured"
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistantRepository: AssistantRepository,
    private val getAssistantResponseUseCase: GetAssistantResponseUseCase,
    private val providerManager: ProviderManager,
    private val providerRegistry: ProviderRegistry,
    private val providerRepository: ProviderRepository,
    private val stateEngine: CareerStateEngine,
    private val contextEngine: ContextEngine,
    private val intentEngine: IntentEngine,
    private val promptOrchestrator: PromptOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow<AssistantUiState>(AssistantUiState.Idle)
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    /** The full career state for the Copilot workspace. */
    val careerState: StateFlow<com.bangersoul.aivance.core.common.model.CareerState> = stateEngine.state

    private val readyStatuses = setOf(
        ProviderStatus.Ready,
        ProviderStatus.Active,
        ProviderStatus.Healthy
    )

    /** Only AI providers count as the assistant's chat provider — never job feeds. */
    private val aiProviderIds: Set<String> = providerRegistry
        .getProvidersByCapability(ProviderCapability.AI.Chat)
        .map { it.metadata.id }
        .toSet()

    /**
     * The badge states configuration that actually exists (AUDIT 15/41).
     *
     * A live status alone is not evidence: `ProviderManager.initializeAll()`
     * marks every registered provider `Ready` even with zero configuration, so a
     * fresh, provider-optional account was told "Gemini · Ready". Readiness now
     * requires a *persisted* provider configuration — the same authority the
     * provider gate uses — together with an operational live status.
     */
    val providerStatus: StateFlow<ProviderStatusUi> = combine(
        providerRepository.getProviderConfigs(),
        providerManager.providerStatuses
    ) { savedConfigs, statuses ->
        val configuredAiIds = savedConfigs
            .map { it.providerId }
            .filter { it in aiProviderIds }
            .toSet()
        val ready = statuses.entries.firstOrNull { (id, status) ->
            id in configuredAiIds && status in readyStatuses
        }
        if (ready != null) {
            ProviderStatusUi(
                isReady = true,
                providerName = friendlyName(ready.key),
                statusLabel = ready.value.name.replaceFirstChar { it.uppercase() }
            )
        } else {
            ProviderStatusUi(isReady = false)
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderStatusUi())

    val userName: StateFlow<String> = stateEngine.state
        .map { it.profile.name.substringBefore(' ') }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private var currentConversationId = "main_session"
    private var lastUserMessage: String? = null

    init {
        // Restore persisted transcript so history survives process death / app
        // restart (previously nothing was ever read back, and writes silently
        // failed on a foreign-key violation).
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val result = assistantRepository.getMessages(currentConversationId).firstOrNull() ?: return@launch
            val messages = (result as? com.bangersoul.aivance.core.common.result.Result.Success)?.data ?: return@launch
            if (messages.isEmpty()) return@launch
            // Only hydrate when the user hasn't already started interacting, so a
            // late DB emission never clobbers an in-flight conversation.
            if (_uiState.value is AssistantUiState.Idle) {
                _uiState.value = AssistantUiState.Chatting(
                    messages = messages.map { AssistantChatMessage(it.role, it.content, it.timestamp) }
                )
            }
        }
    }

    /**
     * Job the user is currently looking at (surfaced from saved jobs / job
     * details via the global assistant overlay). Included in the next prompt so
     * answers are tailored to that role.
     */
    private val _jobContext = MutableStateFlow<AssistantJobContext?>(null)
    val jobContext: StateFlow<AssistantJobContext?> = _jobContext.asStateFlow()

    fun setJobContext(context: AssistantJobContext?) {
        _jobContext.value = context
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val current = _uiState.value
        if (current is AssistantUiState.Chatting &&
            (current.isTyping || (current.streamingContent != null && !current.streamFailed))
        ) {
            return
        }

        lastUserMessage = text
        val userMsg = AssistantChatMessage("USER", text)
        val baseMessages = if (current is AssistantUiState.Chatting) current.messages else emptyList()
        val messages = baseMessages + userMsg

        _uiState.value = AssistantUiState.Chatting(messages, isTyping = true)

        viewModelScope.launch {
            // Persist the user turn exactly once, here — the repository guarantees
            // the parent conversation row exists first (CASCADE FK).
            assistantRepository.saveMessage(currentConversationId, "USER", text)
            runAssistant(rawText = text, messages = messages)
        }
    }

    /**
     * Re-runs generation for the last user message WITHOUT appending or
     * re-persisting a duplicate user turn. The previous user message is already
     * in the transcript and already saved, so retry must not create a second
     * copy (that corrupted history: two identical user rows per failed attempt).
     */
    fun retry() {
        val last = lastUserMessage ?: return
        val current = _uiState.value
        // Reuse the existing transcript (which already contains the user turn);
        // only synthesize a minimal one if somehow retried from a non-chat state.
        val messages = (current as? AssistantUiState.Chatting)?.messages
            ?: listOf(AssistantChatMessage("USER", last))
        _uiState.value = AssistantUiState.Chatting(messages, isTyping = true)
        viewModelScope.launch { runAssistant(rawText = last, messages = messages) }
    }

    /**
     * Streams an assistant response for [rawText] against the current [messages]
     * transcript.
     *
     * Persistence contract (truthful history):
     *  - SUCCESS → the complete assistant message is persisted and committed.
     *  - FAILURE after partial output → the partial text stays visible in-memory
     *    (streamingContent + streamFailed + retry) but is deliberately NOT
     *    persisted, so a failed/truncated turn can never reload as a completed
     *    assistant message. The failed partial is transient by contract.
     *  - CANCELLATION → propagated untouched; never persisted, never shown as an
     *    error (structured concurrency / VM teardown must stay clean).
     */
    private suspend fun runAssistant(rawText: String, messages: List<AssistantChatMessage>) {
        val state = stateEngine.state.value
        val intent = intentEngine.detectIntent(rawText, state)
        val orchestratedPrompt = promptOrchestrator.buildCopilotPrompt(
            rawText,
            state,
            intent,
            jobContext = _jobContext.value
        )

        var fullResponse = ""
        try {
            getAssistantResponseUseCase.stream(
                AssistantRequest(currentConversationId, orchestratedPrompt, rawUserMessage = rawText)
            ).collect { chunk ->
                fullResponse += chunk
                _uiState.value = AssistantUiState.Chatting(
                    messages = messages,
                    isTyping = false,
                    streamingContent = fullResponse
                )
            }

            if (fullResponse.isBlank()) {
                _uiState.value = AssistantUiState.Error("AI returned an empty response")
                return
            }

            // Only a fully-completed response is persisted.
            assistantRepository.saveMessage(currentConversationId, "ASSISTANT", fullResponse)
            val aiMsg = AssistantChatMessage("ASSISTANT", fullResponse)
            _uiState.value = AssistantUiState.Chatting(
                messages = messages + aiMsg,
                isTyping = false,
                streamingContent = null
            )
        } catch (ce: kotlin.coroutines.cancellation.CancellationException) {
            // User/VM cancellation must remain cancellation — never an error,
            // never a persisted failed turn.
            throw ce
        } catch (e: Exception) {
            if (fullResponse.isNotBlank()) {
                // Transient partial: shown with a retry affordance, NOT persisted,
                // so history never contains a fake-completed assistant message.
                _uiState.value = AssistantUiState.Chatting(
                    messages = messages,
                    isTyping = false,
                    streamingContent = fullResponse,
                    streamFailed = true
                )
            } else {
                _uiState.value = AssistantUiState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: "AI failed to respond"
                )
            }
        }
    }

    private fun friendlyName(providerId: String): String =
        providerId.split('_', '-').filter { it.isNotBlank() }.joinToString(" ") {
            it.replaceFirstChar { c -> c.uppercase() }
        }
}
