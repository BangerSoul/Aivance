package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.enums.InterviewDifficulty
import com.bangersoul.aivance.core.common.model.InterviewFeedback
import com.bangersoul.aivance.core.common.model.InterviewMessage
import com.bangersoul.aivance.core.common.model.InterviewSession
import com.bangersoul.aivance.core.common.model.InterviewQuestion
import com.bangersoul.aivance.core.common.model.InterviewEvaluation
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.data.mapper.toDomain
import com.bangersoul.aivance.core.data.mapper.toEntity
import com.bangersoul.aivance.core.database.dao.InterviewDao
import com.bangersoul.aivance.core.database.dao.JobDao
import com.bangersoul.aivance.core.database.dao.ResumeDao
import com.bangersoul.aivance.core.database.model.InterviewSessionWithMessages
import com.bangersoul.aivance.core.domain.events.CareerEventDispatcher
import com.bangersoul.aivance.core.domain.repository.InterviewRepository
import com.bangersoul.aivance.core.domain.usecase.interview.STARAnswerScorer
import com.bangersoul.aivance.core.domain.usecase.interview.STARCoachingPrompts
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterviewRepositoryImpl @Inject constructor(
    private val interviewDao: InterviewDao,
    private val resumeDao: ResumeDao,
    private val jobDao: JobDao,
    private val providerManager: ProviderManager,
    private val careerEventDispatcher: CareerEventDispatcher
) : InterviewRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getSessions(): Flow<CoreResult<List<InterviewSession>>> {
        return interviewDao.getInterviewSessions().map { entities ->
            runCatchingCore { entities.map { it.toDomain() } }
        }
    }

    override fun getSessionById(id: String): Flow<CoreResult<InterviewSession>> {
        return interviewDao.getInterviewSessions().map { list ->
            runCatchingCore {
                val entity = list.find { it.session.id.toString() == id } ?: throw Exception("Session not found")
                entity.toDomain()
            }
        }
    }

    override fun getQuestions(sessionId: String): Flow<CoreResult<List<InterviewQuestion>>> {
        val sessionLongId = sessionId.toLongOrNull()
        return if (sessionLongId == null) {
            flow { emit(Result.Failure(DomainError("Invalid session id"))) }
        } else {
            interviewDao.getQuestionsForSession(sessionLongId).map { entities ->
                runCatchingCore { entities.map { it.toDomain() } }
            }
        }
    }

    override suspend fun startSession(
        role: String,
        company: String,
        difficulty: InterviewDifficulty,
        jobId: Long?,
        resumeVersionId: Long?,
        type: String
    ): CoreResult<InterviewSession> = runCatchingCore {
        val session = InterviewSession(
            id = "0",
            targetRole = role,
            companyName = company,
            difficulty = difficulty,
            jobId = jobId,
            resumeVersionId = resumeVersionId,
            type = type
        )
        val id = interviewDao.insertSession(session.toEntity())
        session.copy(id = id.toString())
    }

    override suspend fun generateQuestions(sessionId: String, count: Int): CoreResult<Unit> = runCatchingCore {
        val id = sessionId.toLongOrNull() ?: throw Exception("Invalid ID")
        val sessionWithData = interviewDao.getInterviewSessionWithMessagesById(id) ?: throw Exception("Session not found")
        val session = sessionWithData.toDomain()

        val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            ?: throw Exception("No AI provider available")

        val prompt = STARCoachingPrompts.buildSessionQuestionPrompt(
            targetRole = session.targetRole,
            companyName = session.companyName,
            type = session.type,
            difficulty = session.difficulty.toString(),
            count = count
        )

        val response = provider.generateText(prompt).getOrNull() ?: throw Exception("AI failed to generate questions")

        val jsonText = if (response.contains("```json")) {
            response.substringAfter("```json").substringBefore("```").trim()
        } else if (response.contains("[")) {
            response.substring(response.indexOf("["), response.lastIndexOf("]") + 1)
        } else response

        val questions = json.decodeFromString<List<InterviewQuestion>>(jsonText)
        questions.forEach { q ->
            interviewDao.insertQuestion(q.toEntity(id))
        }
    }

    override suspend fun persistPackQuestions(
        sessionId: String,
        questions: List<InterviewQuestion>
    ): CoreResult<Unit> = runCatchingCore {
        val id = sessionId.toLongOrNull() ?: throw Exception("Invalid ID")
        questions.forEach { question ->
            interviewDao.insertQuestion(question.toEntity(id))
        }
    }

    override suspend fun submitAnswer(sessionId: String, message: InterviewMessage): CoreResult<Unit> = runCatchingCore {
        val msgId = interviewDao.insertMessage(message.toEntity())
        evaluateAnswer(msgId.toString())
    }

    override suspend fun evaluateAnswer(messageId: String): CoreResult<Unit> = runCatchingCore {
        val msgId = messageId.toLongOrNull() ?: throw Exception("Invalid ID")
        val messageEntity = interviewDao.getMessageById(msgId) ?: throw Exception("Message not found")
        val sessionWithData = interviewDao.getInterviewSessionWithMessagesById(messageEntity.sessionId) ?: throw Exception("Session not found")

        val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            ?: throw Exception("No AI provider available")

        val prompt = STARCoachingPrompts.buildEvaluationPrompt(sessionWithData.session.targetRole, messageEntity.text)

        val response = provider.generateText(prompt).getOrNull() ?: throw Exception("AI evaluation failed")

        val jsonText = if (response.contains("```json")) {
            response.substringAfter("```json").substringBefore("```").trim()
        } else if (response.contains("{")) {
            response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1)
        } else response

        val decoded = json.decodeFromString<InterviewEvaluation>(jsonText)
        val evaluation = decoded.copy(
            messageId = messageId,
            // Rubric-gated fallback (Option C): when the AI omits starMethodScore,
            // fill it deterministically so the review screen always has a grounded
            // STAR score — mirrors the deterministic reward-verifier discipline.
            starMethodScore = decoded.starMethodScore
                ?: STARAnswerScorer.score(messageEntity.text).starMethodScore
        )

        interviewDao.insertEvaluation(evaluation.toEntity())
    }

    override suspend fun completeSession(sessionId: String): CoreResult<Unit> = runCatchingCore {
        val id = sessionId.toLongOrNull() ?: throw Exception("Invalid ID")
        val entity = interviewDao.getInterviewSessionWithMessagesById(id) ?: throw Exception("Not found")

        // Generate AI feedback from the transcript before marking the session complete,
        // so the review screen shows a real evaluation instead of a placeholder.
        val feedback = generateSessionFeedback(entity)
        val updated = entity.session.copy(isCompleted = true, overallFeedback = feedback)
        interviewDao.insertSession(updated)

        // The session is now persisted as completed (definitive success).
        // Publish the real domain event onto the shared CareerEventBus that the
        // V2 CareerStateEngine consumes. Dispatch is fire-and-forget and
        // non-suspending, so it neither blocks nor folds a bus failure into this
        // operation's success; it is only reached on the success path (a missing
        // session throws above and runCatchingCore returns Failure).
        careerEventDispatcher.onInterviewCompleted(
            sessionId = sessionId,
            score = feedback?.overallScore ?: 0,
            weaknesses = feedback?.improvements ?: emptyList()
        )
    }

    private suspend fun generateSessionFeedback(
        entity: InterviewSessionWithMessages
    ): InterviewFeedback? {
        val transcript = entity.messages
            .filter { it.role == "USER" || it.role == "ASSISTANT" }
            .joinToString("\n") { "${it.role}: ${it.text}" }
        if (transcript.isBlank()) return null

        val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            ?: return null

        val prompt = """
            You are an expert interview coach. Evaluate the candidate's performance for the role of ${entity.session.targetRole}.
            Interview transcript:
            $transcript

            Return ONLY a JSON object with:
            "overallScore": Int (0-100),
            "strengths": [String],
            "improvements": [String],
            "detailedSummary": String
        """.trimIndent()

        val response = provider.generateText(prompt).getOrNull() ?: return null
        val jsonText = if (response.contains("```json")) {
            response.substringAfter("```json").substringBefore("```").trim()
        } else if (response.contains("{")) {
            response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1)
        } else response
        return try {
            json.decodeFromString<InterviewFeedback>(jsonText)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteSession(sessionId: String): CoreResult<Unit> = runCatchingCore {
        val id = sessionId.toLongOrNull() ?: throw Exception("Invalid ID")
        val entity = interviewDao.getInterviewSessionWithMessagesById(id) ?: throw Exception("Not found")
        interviewDao.deleteSession(entity.session)
    }
}
