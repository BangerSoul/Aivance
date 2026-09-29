package com.bangersoul.aivance.core.domain.usecase.interview

/**
 * Deterministic STAR (Situation, Task, Action, Result) answer rubric — the
 * "rubric-gated evaluation" of the on-device coaching design (Option C).
 *
 * Unlike the AI evaluation path ([com.bangersoul.aivance.core.domain.repository.InterviewRepository.evaluateAnswer]),
 * this scorer is a pure function: it detects the four STAR components in a
 * candidate answer and maps them to a bounded 0-100 `starMethodScore`, so the
 * review screen always has a grounded STAR score even when the AI omits
 * `starMethodScore` or no provider is available.
 *
 * Design mirrors the reward-verifier discipline from the LLM training notes:
 * deterministic, bounded, anti-gaming. Component detection accepts both the
 * labeled form ("Situation: ...") and unlabeled-but-sectional answers via
 * lightweight heuristics, but never invents components that are not present.
 */
object STARAnswerScorer {

    /** Per-component detection result. */
    data class StarScore(
        val situation: Boolean,
        val task: Boolean,
        val action: Boolean,
        val result: Boolean,
        /** Bounded 0-100 score — 25 per component. */
        val starMethodScore: Int
    ) {
        val componentsPresent: Int
            get() = listOf(situation, task, action, result).count { it }
    }

    /** Heuristic vocabulary scored per component when no label is present. */
    private val COMPONENT_HINTS = mapOf(
        "situation" to listOf("context", "background", "at my previous", "in my previous", "when i", "while working"),
        "task" to listOf("i was responsible", "my role", "i owned", "i needed to", "task:", "responsible for"),
        "action" to listOf("i did", "i implemented", "i built", "i led", "i redesigned", "i refactored", "i created", "i shipped", "i analyzed", "i proposed"),
        "result" to listOf("result:", "outcome", "as a result", "led to", "improved", "reduced", "increased", "dropped", "saved", "%")
    )

    /**
     * Scores [answer] for STAR completeness.
     *
     * A component is present if (a) its label appears at the start of a line
     * ("Situation:", case-insensitive) OR (b) the answer contains a hint from
     * its vocabulary. Labeled components win outright; hints only fill gaps so
     * a loosely structured answer is not unfairly zeroed.
     */
    fun score(answer: String?): StarScore {
        val text = answer?.trim().orEmpty()
        if (text.isBlank()) return StarScore(false, false, false, false, 0)

        val lower = text.lowercase()
        val lineStarts = text.lines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .map { it.substringBefore(':').trim() }
            .toSet()

        val situation = hasLabel(lineStarts, "situation") || hasHint(lower, "situation")
        val task = hasLabel(lineStarts, "task") || hasHint(lower, "task")
        val action = hasLabel(lineStarts, "action") || hasHint(lower, "action")
        val result = hasLabel(lineStarts, "result") || hasHint(lower, "result")

        val present = listOf(situation, task, action, result).count { it }
        return StarScore(
            situation = situation,
            task = task,
            action = action,
            result = result,
            starMethodScore = (present * 25).coerceIn(0, 100)
        )
    }

    private fun hasLabel(lineStarts: Set<String>, component: String): Boolean =
        lineStarts.any { it == component || it.startsWith("$component ") }

    private fun hasHint(lowerText: String, component: String): Boolean =
        COMPONENT_HINTS.getValue(component).any { lowerText.contains(it) }
}
