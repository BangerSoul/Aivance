package com.bangersoul.aivance.core.domain.usecase.interview

/**
 * Shared STAR-coaching prompt fragments for every interview AI path (Option C —
 * the prompt-based STAR coach).
 *
 * Single source of truth so the STAR guidance (and the evaluation rubric) is
 * identical whether the prompt is built by [GenerateStarPackUseCase] or by the
 * data-layer session/evaluation prompts, and so the guidance reaches the
 * on-device Gemma provider (which receives these prompts verbatim as raw text)
 * exactly as it reaches cloud providers.
 */
object STARCoachingPrompts {

    /** The four component names — single source of truth for labels. */
    val COMPONENTS = listOf("Situation", "Task", "Action", "Result")

    /**
     * Prompt fragment instructing the model to write STAR-shaped `idealAnswer`
     * values: one labeled paragraph per component, in the same shape the
     * deterministic [STARPrepGenerator] pool uses.
     */
    val IDEAL_ANSWER_GUIDANCE: String = buildString {
        appendLine("Each \"idealAnswer\" MUST be a worked STAR answer written as exactly four labeled paragraphs:")
        COMPONENTS.forEach { appendLine("\"$it: <content>\"") }
        appendLine("Use the labels verbatim — the review screen parses them to grade STAR completeness.")
    }

    /**
     * Builds the question-generation prompt for [GenerateStarPackUseCase].
     *
     * @param role Target role for the pack.
     * @param count Number of questions requested.
     */
    fun buildPackPrompt(role: String, count: Int): String = buildString {
        appendLine("You are an expert interview coach. Generate $count STAR-format (Situation, Task, Action, Result) interview questions for a $role candidate.")
        appendLine("Each question must be answerable with the STAR method and specific to the $role role.")
        appendLine("Return ONLY a JSON array of objects with:")
        appendLine("\"text\": String,")
        appendLine("\"category\": String (BEHAVIORAL, TECHNICAL or LEADERSHIP),")
        appendLine("\"difficulty\": String (EASY, MEDIUM or HARD),")
        appendLine("\"expectedKeyPoints\": [String] — the S/T/A/R framework points for the answer,")
        appendLine("\"idealAnswer\": String — a short worked STAR example answer.")
        append(IDEAL_ANSWER_GUIDANCE)
    }

    /**
     * Builds the mock-session question-generation prompt used by the data-layer
     * session path (the same schema [GenerateStarPackUseCase] consumes).
     */
    fun buildSessionQuestionPrompt(
        targetRole: String,
        companyName: String,
        type: String,
        difficulty: String,
        count: Int
    ): String = buildString {
        appendLine("Generate $count interview questions for a $difficulty level interview for the role of $targetRole at $companyName.")
        appendLine("Type of interview: $type.")
        appendLine("Each question must be answerable with the STAR method (Situation, Task, Action, Result).")
        appendLine("Return ONLY a JSON array of objects with:")
        appendLine("\"text\": String,")
        appendLine("\"category\": String (e.g. Technical, Behavioral),")
        appendLine("\"difficulty\": String (Easy, Medium, Hard),")
        appendLine("\"expectedKeyPoints\": [String],")
        appendLine("\"idealAnswer\": String.")
        append(IDEAL_ANSWER_GUIDANCE)
    }

    /**
     * Builds the answer-evaluation prompt. Explicitly asks the model to grade
     * each STAR component and produce `starMethodScore`, which the caller fills
     * deterministically via [STARAnswerScorer] when the model omits it.
     */
    fun buildEvaluationPrompt(targetRole: String, answer: String): String = buildString {
        appendLine("Evaluate the following candidate interview answer for the role $targetRole.")
        appendLine("Answer: \"$answer\"")
        appendLine("")
        appendLine("Grade STAR completeness explicitly: for each component (Situation, Task, Action, Result) decide whether the answer includes it.")
        appendLine("Return ONLY a JSON object with:")
        appendLine("\"scoreClarity\": Int (0-100),")
        appendLine("\"scoreAccuracy\": Int (0-100),")
        appendLine("\"scoreTone\": Int (0-100),")
        appendLine("\"starMethodScore\": Int (0-100 — proportion of STAR components present, 25 per component),")
        appendLine("\"feedback\": String,")
        appendLine("\"improvementTips\": [String]")
    }
}
