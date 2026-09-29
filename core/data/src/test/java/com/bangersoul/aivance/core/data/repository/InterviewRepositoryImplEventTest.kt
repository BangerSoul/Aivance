package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.model.InterviewFeedback
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.database.dao.InterviewDao
import com.bangersoul.aivance.core.database.dao.JobDao
import com.bangersoul.aivance.core.database.dao.ResumeDao
import com.bangersoul.aivance.core.database.model.InterviewSessionEntity
import com.bangersoul.aivance.core.database.model.InterviewSessionWithMessages
import com.bangersoul.aivance.core.domain.events.CareerEventDispatcher
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves the interview-completion production mutation is wired to the V2 career
 * event pipeline: a successful [InterviewRepositoryImpl.completeSession] persists
 * the completed session and then publishes a real [InterviewEvent.Completed]
 * through [CareerEventDispatcher]; a failed completion emits nothing.
 */
class InterviewRepositoryImplEventTest {

    private lateinit var repository: InterviewRepositoryImpl
    private val interviewDao: InterviewDao = mockk()
    private val resumeDao: ResumeDao = mockk()
    private val jobDao: JobDao = mockk()
    private val providerManager: ProviderManager = mockk()
    private val careerEventDispatcher: CareerEventDispatcher = mockk(relaxed = true)

    @Before
    fun setUp() {
        repository = InterviewRepositoryImpl(
            interviewDao = interviewDao,
            resumeDao = resumeDao,
            jobDao = jobDao,
            providerManager = providerManager,
            careerEventDispatcher = careerEventDispatcher
        )
    }

    private fun sessionWithMessages(id: Long) = InterviewSessionWithMessages(
        session = InterviewSessionEntity(
            id = id,
            targetRole = "Android Engineer",
            difficulty = "MEDIUM"
        ),
        messages = emptyList()
    )

    @Test
    fun `completeSession emits InterviewCompleted on success`() = runTest {
        val sessionId = 5L
        coEvery { interviewDao.getInterviewSessionWithMessagesById(sessionId) } returns sessionWithMessages(sessionId)
        // No provider -> generateSessionFeedback short-circuits to null feedback,
        // but the session is still persisted as completed (definitive success).
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns null
        coEvery { interviewDao.insertSession(any()) } returns sessionId

        val result = repository.completeSession(sessionId.toString())

        assertTrue(result.isSuccess)
        verify {
            careerEventDispatcher.onInterviewCompleted(
                sessionId = sessionId.toString(),
                score = 0,
                weaknesses = emptyList()
            )
        }
    }

    @Test
    fun `completeSession does not emit when the session is missing`() = runTest {
        val sessionId = 9L
        coEvery { interviewDao.getInterviewSessionWithMessagesById(sessionId) } returns null

        val result = repository.completeSession(sessionId.toString())

        assertTrue(result.isFailure)
        verify(exactly = 0) {
            careerEventDispatcher.onInterviewCompleted(any(), any(), any(), any())
        }
    }
}
