package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.enums.InterviewDifficulty
import com.bangersoul.aivance.core.common.model.InterviewFeedback
import com.bangersoul.aivance.core.common.model.InterviewMessage
import com.bangersoul.aivance.core.common.model.InterviewQuestion
import com.bangersoul.aivance.core.common.model.InterviewSession
import com.bangersoul.aivance.core.common.result.CoreResult
import kotlinx.coroutines.flow.Flow

interface InterviewRepository {
    fun getSessions(): Flow<CoreResult<List<InterviewSession>>>
    fun getSessionById(id: String): Flow<CoreResult<InterviewSession>>
    fun getQuestions(sessionId: String): Flow<CoreResult<List<InterviewQuestion>>>

    suspend fun startSession(
        role: String,
        company: String,
        difficulty: InterviewDifficulty,
        jobId: Long?,
        resumeVersionId: Long?,
        type: String
    ): CoreResult<InterviewSession>

    suspend fun generateQuestions(
        sessionId: String,
        count: Int
    ): CoreResult<Unit>

    /**
     * Persists a ready-made question pack (e.g. a generated STAR pack, R-05)
     * onto an existing session so the user's answers — submitted through
     * [submitAnswer] — are recorded against real session rows and survive a
     * session reload. Questions are stored with the session id, exactly like
     * [generateQuestions]-produced ones.
     */
    suspend fun persistPackQuestions(
        sessionId: String,
        questions: List<InterviewQuestion>
    ): CoreResult<Unit>

    suspend fun submitAnswer(
        sessionId: String,
        message: InterviewMessage
    ): CoreResult<Unit>

    suspend fun evaluateAnswer(
        messageId: String
    ): CoreResult<Unit>

    suspend fun completeSession(sessionId: String): CoreResult<Unit>
    suspend fun deleteSession(sessionId: String): CoreResult<Unit>
}
