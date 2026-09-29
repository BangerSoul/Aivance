package com.bangersoul.aivance.core.domain.usecase.interview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class STARCoachingPromptsTest {

    @Test
    fun `pack prompt carries the STAR ideal-answer guidance and schema`() {
        val prompt = STARCoachingPrompts.buildPackPrompt(role = "Android Engineer", count = 5)

        assertTrue(prompt.contains("Android Engineer"))
        assertTrue(prompt.contains("5 STAR-format"))
        // The four labels must appear verbatim so the review screen can parse them.
        STARCoachingPrompts.COMPONENTS.forEach { label ->
            assertTrue("\"$label: <content>\" missing", prompt.contains("\"$label: <content>\""))
        }
        assertTrue(prompt.contains("idealAnswer"))
        assertTrue(prompt.contains("expectedKeyPoints"))
    }

    @Test
    fun `session question prompt includes STAR guidance and interview context`() {
        val prompt = STARCoachingPrompts.buildSessionQuestionPrompt(
            targetRole = "Data Engineer",
            companyName = "Acme",
            type = "TECHNICAL",
            difficulty = "MEDIUM",
            count = 7
        )

        assertTrue(prompt.contains("7 interview questions"))
        assertTrue(prompt.contains("Data Engineer"))
        assertTrue(prompt.contains("Acme"))
        assertTrue(prompt.contains("TECHNICAL"))
        assertTrue(prompt.contains("MEDIUM"))
        assertTrue(prompt.contains("answerable with the STAR method"))
        assertTrue(prompt.contains("\"idealAnswer\": String"))
    }

    @Test
    fun `evaluation prompt asks for explicit STAR component grading`() {
        val prompt = STARCoachingPrompts.buildEvaluationPrompt(
            targetRole = "Backend Engineer",
            answer = "Situation: x. Task: y."
        )

        assertTrue(prompt.contains("Backend Engineer"))
        assertTrue(prompt.contains("Situation: x. Task: y."))
        assertTrue(prompt.contains("starMethodScore"))
        assertTrue(prompt.contains("25 per component"))
        assertTrue(prompt.contains("Situation, Task, Action, Result"))
    }

    @Test
    fun `ideal answer guidance demands labeled paragraphs`() {
        val guidance = STARCoachingPrompts.IDEAL_ANSWER_GUIDANCE
        assertTrue(guidance.contains("MUST be a worked STAR answer"))
        assertTrue(guidance.contains("exactly four labeled paragraphs"))
        // The four label lines, quoted verbatim.
        assertEquals(4, guidance.lines().count { it.startsWith("\"") })
        STARCoachingPrompts.COMPONENTS.forEach { label ->
            assertTrue(guidance.contains("\"$label: <content>\""))
        }
    }
}
