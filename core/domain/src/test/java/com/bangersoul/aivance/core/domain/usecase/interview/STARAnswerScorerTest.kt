package com.bangersoul.aivance.core.domain.usecase.interview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class STARAnswerScorerTest {

    private val fullStar = """
        Situation: Our production service leaked memory under peak traffic.
        Task: As lead engineer I owned the fix.
        Action: I captured heap dumps and refactored the connection pools.
        Result: Latency dropped 40% and zero crashes occurred.
    """.trimIndent()

    @Test
    fun `fully labeled STAR answer scores 100`() {
        val s = STARAnswerScorer.score(fullStar)
        assertTrue(s.situation)
        assertTrue(s.task)
        assertTrue(s.action)
        assertTrue(s.result)
        assertEquals(4, s.componentsPresent)
        assertEquals(100, s.starMethodScore)
    }

    @Test
    fun `missing Result component scores 75`() {
        val partial = fullStar.substringBefore("Result:")
        val s = STARAnswerScorer.score(partial)
        assertTrue(s.situation)
        assertTrue(s.task)
        assertTrue(s.action)
        assertFalse(s.result)
        assertEquals(75, s.starMethodScore)
    }

    @Test
    fun `gibberish scores zero`() {
        val s = STARAnswerScorer.score("lorem ipsum dolor sit amet")
        assertEquals(0, s.starMethodScore)
        assertEquals(0, s.componentsPresent)
    }

    @Test
    fun `blank and null answers score zero`() {
        assertEquals(0, STARAnswerScorer.score("").starMethodScore)
        assertEquals(0, STARAnswerScorer.score("   \n  ").starMethodScore)
        assertEquals(0, STARAnswerScorer.score(null).starMethodScore)
    }

    @Test
    fun `lowercase labels are recognized`() {
        val lower = """
            situation: context here
            task: what i owned
            action: what i did
            result: outcome achieved
        """.trimIndent()
        assertEquals(100, STARAnswerScorer.score(lower).starMethodScore)
    }

    @Test
    fun `unlabeled answers still get credit via hints`() {
        val unlabeled = "In my previous company the context was tight timelines. " +
            "I was responsible for delivery. I implemented a monitoring dashboard " +
            "which reduced pager load by 60%."
        val s = STARAnswerScorer.score(unlabeled)
        assertEquals(4, s.componentsPresent)
        assertEquals(100, s.starMethodScore)
    }

    @Test
    fun `wordy but empty answer earns no components`() {
        val s = STARAnswerScorer.score("I am a great candidate and I work very hard every single day.")
        assertEquals(0, s.starMethodScore)
    }

    @Test
    fun `template ideal answers pass the rubric`() {
        // The deterministic pool's worked answers must satisfy our own gate.
        STARPrepGenerator.generateStarPack("Android Engineer", 6).forEach { q ->
            val s = STARAnswerScorer.score(q.idealAnswer)
            assertEquals("idealAnswer for '${q.text}' must be full STAR", 4, s.componentsPresent)
            assertEquals(100, s.starMethodScore)
        }
    }

    @Test
    fun `score is bounded even with repeated hints`() {
        // Repeating the same two components over and over never inflates past
        // the per-component cap — the score stays at 2 components = 50.
        val spam = buildString {
            repeat(50) { appendLine("Action: I did things. Result: % improved.") }
        }
        val s = STARAnswerScorer.score(spam)
        assertEquals(2, s.componentsPresent)
        assertEquals(50, s.starMethodScore)
    }
}
