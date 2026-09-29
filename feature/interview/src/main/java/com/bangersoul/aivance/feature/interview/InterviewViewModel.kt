package com.bangersoul.aivance.feature.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.enums.InterviewDifficulty
import com.bangersoul.aivance.core.common.enums.MessageSender
import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.domain.analytics.InterviewReadinessCalculator
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.repository.InterviewRepository
import com.bangersoul.aivance.core.domain.repository.crm.CompanyIntelligenceRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.interview.GenerateStarPackRequest
import com.bangersoul.aivance.core.domain.usecase.interview.GenerateStarPackUseCase
import com.bangersoul.aivance.core.domain.usecase.interview.STARPrepGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface InterviewUiState {
    data class Idle(
        val history: List<InterviewSession> = emptyList(),
        val careerState: CareerState? = null,
        /**
         * Interview readiness from [InterviewReadinessCalculator] — the single owner shared
         * with the analytics path — or `null` when no session has produced feedback yet.
         */
        val readinessScore: Int? = null,
        /**
         * Role-specific STAR pack generated for the Practice tab (R-05):
         * AI-generated via the streaming path, template fallback offline.
         */
        val starPack: List<InterviewQuestion>? = null,
        val isGeneratingPack: Boolean = false
    ) : InterviewUiState
    data object Preparing : InterviewUiState
    data class Active(
        val session: InterviewSession,
        val currentQuestionIndex: Int = 0,
        val isSubmitting: Boolean = false
    ) : InterviewUiState
    data class Review(val session: InterviewSession) : InterviewUiState
    data class Error(val message: String) : InterviewUiState
}

sealed interface InterviewUiEvent {
    data class StartSession(
        val role: String,
        val company: String,
        val type: String,
        val jobId: Long? = null,
        /**
         * When set, the session is seeded with this ready-made pack instead of
         * asking the AI provider for fresh questions — used by STAR pack
         * practice (R-05). The pack is persisted to the session row so answers
         * are recorded and survive reloads.
         */
        val packQuestions: List<InterviewQuestion>? = null
    ) : InterviewUiEvent
    data class GenerateStarPack(val role: String) : InterviewUiEvent
    data class SubmitAnswer(val text: String) : InterviewUiEvent
    data object NextQuestion : InterviewUiEvent
    data object Complete : InterviewUiEvent
    data object Reset : InterviewUiEvent
    data object LoadHistory : InterviewUiEvent
}

@HiltViewModel
class InterviewViewModel @Inject constructor(
    private val interviewRepository: InterviewRepository,
    private val careerStateEngine: CareerStateEngine,
    private val companyRepository: CompanyIntelligenceRepository,
    private val generateStarPackUseCase: GenerateStarPackUseCase,
    private val trackEventUseCase: TrackEventUseCase,
    private val interviewReadinessCalculator: InterviewReadinessCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow<InterviewUiState>(InterviewUiState.Idle())
    val uiState: StateFlow<InterviewUiState> = _uiState.asStateFlow()

    private val _effects = Channel<Unit>(Channel.BUFFERED)
    val effects: Flow<Unit> = _effects.receiveAsFlow()

    private var messageCounter = 0L
    private var historyJob: kotlinx.coroutines.Job? = null

    init {
        loadHistory()
        observeCareerState()
    }

    private fun observeCareerState() {
        viewModelScope.launch {
            careerStateEngine.state.collect { state ->
                val current = _uiState.value
                if (current is InterviewUiState.Idle) {
                    // Readiness is deliberately NOT derived from the career state here: it is a
                    // function of the candidate's own mock-session feedback, owned by
                    // [InterviewReadinessCalculator] and recomputed in [loadHistory] (R3-3).
                    // Prep Studio used to add `careerScore / 10` to it, which is how a
                    // fabricated career score of 18 surfaced as a "1%" readiness here while the
                    // analytics path reported 18 for the same data.
                    _uiState.value = current.copy(careerState = state)
                }
            }
        }
    }

    fun onEvent(event: InterviewUiEvent) {
        when (event) {
            is InterviewUiEvent.StartSession -> startSession(event)
            is InterviewUiEvent.GenerateStarPack -> generateStarPack(event.role)
            is InterviewUiEvent.SubmitAnswer -> submitAnswer(event.text)
            InterviewUiEvent.NextQuestion -> nextQuestion()
            InterviewUiEvent.Complete -> completeSession()
            InterviewUiEvent.Reset -> reset()
            InterviewUiEvent.LoadHistory -> loadHistory()
        }
    }

    /**
     * Generates a role-specific STAR pack (R-05) for the Practice tab. AI via
     * the streaming path when a provider is configured; the deterministic
     * template pack otherwise — the flow never fails, it always yields a pack.
     */
    private fun generateStarPack(role: String) {
        val current = _uiState.value as? InterviewUiState.Idle ?: return
        if (current.isGeneratingPack) return
        _uiState.value = current.copy(isGeneratingPack = true)
        viewModelScope.launch {
            val pack = generateStarPackUseCase(GenerateStarPackRequest(role = role, count = 5))
            val idle = _uiState.value as? InterviewUiState.Idle ?: return@launch
            _uiState.value = idle.copy(starPack = pack, isGeneratingPack = false)
        }
    }

    private fun startSession(event: InterviewUiEvent.StartSession) {
        viewModelScope.launch {
            _uiState.value = InterviewUiState.Preparing
            trackEventUseCase(TrackEventRequest("interview_session_start"))

            // Find the correct resume version for this job if possible
            val resumeVersionId = event.jobId?.let { jid ->
                // Logic to find best resume for this job context
                null
            }

            val result = interviewRepository.startSession(
                role = event.role,
                company = event.company,
                difficulty = InterviewDifficulty.MEDIUM,
                jobId = event.jobId,
                resumeVersionId = resumeVersionId,
                type = event.type
            )

            if (result is Result.Success) {
                val session = result.data
                _uiState.value = InterviewUiState.Active(session = session)

                // Every question path persists onto the session row (R-05), so
                // answers submitted through submitAnswer are recorded against
                // real session questions and survive a session reload:
                //  1. A ready-made pack explicitly supplied (STAR pack practice).
                //  2. AI-generated questions, when a provider is configured.
                //  3. The STAR prep pack fallback — now persisted too, instead
                //     of the previous in-memory-only questions.
                val persisted = when {
                    event.packQuestions != null -> {
                        interviewRepository.persistPackQuestions(session.id, event.packQuestions)
                        readSessionQuestions(session.id)
                    }
                    interviewRepository.generateQuestions(session.id, 5) is Result.Success -> {
                        readSessionQuestions(session.id)
                    }
                    else -> {
                        val fallback = generateStarPackUseCase(GenerateStarPackRequest(role = event.role, count = 5))
                        interviewRepository.persistPackQuestions(session.id, fallback)
                        readSessionQuestions(session.id)
                    }
                }

                val questions = persisted.ifEmpty {
                    // Persistence unavailable for some reason — keep the screen
                    // usable with an in-memory pack rather than dead-ending.
                    STARPrepGenerator.generateStarPack(event.role)
                }
                _uiState.value = InterviewUiState.Active(session = session.copy(questions = questions))
            } else {
                _uiState.value = InterviewUiState.Error(
                    (result as? Result.Failure)?.error?.message ?: "Failed to start session"
                )
            }
        }
    }

    private fun submitAnswer(answerText: String) {
        val current = _uiState.value as? InterviewUiState.Active ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isSubmitting = true)

            val messageId = "user-${System.currentTimeMillis()}-${messageCounter++}"
            val message = InterviewMessage(
                id = messageId,
                sessionId = current.session.id,
                sender = MessageSender.USER,
                text = answerText
            )

            val result = interviewRepository.submitAnswer(current.session.id, message)
            if (result is Result.Success) {
                _uiState.value = current.copy(isSubmitting = false)
            } else {
                // Surface the real cause instead of silently swallowing the failure.
                _uiState.value = InterviewUiState.Error(
                    "Failed to submit answer: ${(result as? Result.Failure)?.error?.message ?: "Unknown error"}"
                )
            }
        }
    }

    private fun nextQuestion() {
        val current = _uiState.value as? InterviewUiState.Active ?: return
        _uiState.value = current.copy(currentQuestionIndex = current.currentQuestionIndex + 1)
    }

    private fun completeSession() {
        val current = _uiState.value as? InterviewUiState.Active ?: return
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest("interview_session_complete"))
            interviewRepository.completeSession(current.session.id)
            // Reload the session to pick up the AI feedback generated on completion.
            val completed = interviewRepository.getSessionById(current.session.id).firstOrNull()?.getOrNull()
            val reviewSession = completed ?: current.session.copy(isCompleted = true)
            _uiState.value = InterviewUiState.Review(reviewSession)
            loadHistory()
        }
    }

    private suspend fun readSessionQuestions(sessionId: String): List<InterviewQuestion> =
        interviewRepository.getQuestions(sessionId).firstOrNull()?.getOrNull().orEmpty()

    private fun reset() {
        _uiState.value = InterviewUiState.Idle()
        loadHistory()
    }

    private fun loadHistory() {
        // Keep a single subscription to the history flow; cancel any previous
        // collector so repeated calls don't accumulate live Room observers.
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            interviewRepository.getSessions()
                .catch { emit(Result.Failure(com.bangersoul.aivance.core.common.result.DomainError(it.message ?: "Failed to load history"))) }
                .collect { result ->
                    val sessions = result.getOrNull()
                    if (sessions != null) {
                        val current = _uiState.value
                        if (current is InterviewUiState.Idle) {
                            _uiState.value = current.copy(
                                history = sessions,
                                readinessScore = interviewReadinessCalculator.calculate(sessions)
                            )
                        }
                    }
                }
        }
    }
}
