package com.bangersoul.aivance.core.domain.analytics

import com.bangersoul.aivance.core.common.model.InterviewFeedback
import com.bangersoul.aivance.core.common.model.InterviewSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R3-3 — one authoritative owner for Interview Readiness.
 *
 * Before this the concept had two disagreeing owners that were both visible on the same device:
 * the analytics path divided a hardcoded `75` into the career composite (rendering `18` at zero
 * data) while Prep Studio added `careerScore / 10` to the last session score (rendering `1%` at
 * zero data, off that same fabricated 18). Both now call this calculator.
 */
class InterviewReadinessCalculatorTest {

    private val calculator = InterviewReadinessCalculator()

    private fun session(id: String, score: Int?) = InterviewSession(
        id = id,
        targetRole = "Engineer",
        feedback = score?.let { InterviewFeedback(overallScore = it) }
    )

    @Test
    fun `readiness is not measured when there are no sessions`() {
        assertNull(calculator.calculate(emptyList()))
    }

    @Test
    fun `readiness is not measured when no session produced feedback`() {
        val sessions = listOf(session("s1", null), session("s2", null))

        assertNull("no feedback means nothing was measured — not zero, and not a default", calculator.calculate(sessions))
    }

    @Test
    fun `readiness is the mean of earned session feedback`() {
        val sessions = listOf(session("s1", 90), session("s2", 80))

        assertEquals(85, calculator.calculate(sessions))
    }

    @Test
    fun `sessions without feedback do not dilute measured readiness`() {
        val sessions = listOf(session("s1", 90), session("s2", null))

        assertEquals(90, calculator.calculate(sessions))
    }

    @Test
    fun `readiness is bounded to a percentage`() {
        assertEquals(100, calculator.calculate(listOf(session("s1", 140))))
        assertEquals(0, calculator.calculate(listOf(session("s1", -20))))
    }
}
