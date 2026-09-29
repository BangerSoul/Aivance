package com.bangersoul.aivance.core.domain.context

import com.bangersoul.aivance.core.common.events.CareerEvent
import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.graph.CareerNodeType
import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.domain.agent.CareerGoal
import javax.inject.Inject
import javax.inject.Singleton

enum class ContextPriority(val weight: Int) {
    CRITICAL(4),
    HIGH(3),
    MEDIUM(2),
    LOW(1)
}

data class ContextBlock(
    val id: String,
    val priority: ContextPriority,
    val title: String,
    val content: String,
    val estimatedTokens: Int
)

data class AiContextResult(
    val promptContext: String,
    val totalEstimatedTokens: Int,
    val includedBlocks: List<String>,
    val droppedBlocks: List<String>
)

/**
 * AI Context Engine 2.0.
 *
 * Implements bounded, explainable, privacy-aware, token-budgeted prompt context assembly.
 * Strictly prioritizes context items across CRITICAL, HIGH, MEDIUM, and LOW tiers,
 * actively preventing database dumping and enforcing PII redaction.
 */
@Singleton
class AiContextEngine2 @Inject constructor() {

    companion object {
        const val DEFAULT_TOKEN_BUDGET: Int = 2048

        // Regex patterns for privacy filtering
        private val EMAIL_REGEX = Regex("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
        private val PHONE_REGEX = Regex("\\b(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b")
        private val SSN_REGEX = Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b")
    }

    /**
     * Sanitizes sensitive PII from content before it enters any AI context block.
     */
    fun sanitizePii(raw: String): String {
        return raw
            .replace(EMAIL_REGEX, "[USER_EMAIL]")
            .replace(PHONE_REGEX, "[USER_PHONE]")
            .replace(SSN_REGEX, "[USER_ID]")
    }

    /**
     * Estimates token count based on standard ~4 characters per token heuristic.
     */
    fun estimateTokens(text: String): Int = (text.length / 4) + 1

    /**
     * Assembles bounded, prioritized context within [tokenBudget].
     */
    fun buildContext(
        profile: UserProfile?,
        graph: CareerGraph?,
        activeJob: JobListing? = null,
        activeResume: Resume? = null,
        latestAts: AtsReport? = null,
        interviews: List<InterviewSession> = emptyList(),
        careerGoal: CareerGoal? = null,
        recentEvents: List<CareerEvent> = emptyList(),
        tokenBudget: Int = DEFAULT_TOKEN_BUDGET
    ): AiContextResult {
        val blocks = mutableListOf<ContextBlock>()

        // 1. CRITICAL TIER: Active Job, Active Resume
        activeJob?.let { job ->
            val content = sanitizePii("""
                Title: ${job.title}
                Company: ${job.company}
                Location: ${job.location} | Remote: ${job.isRemote}
                Description: ${job.description.take(500)}
            """.trimIndent())
            blocks.add(
                ContextBlock(
                    id = "active_job",
                    priority = ContextPriority.CRITICAL,
                    title = "TARGET JOB CONTEXT",
                    content = content,
                    estimatedTokens = estimateTokens(content)
                )
            )
        }

        activeResume?.let { resume ->
            val content = sanitizePii("""
                Resume Name: ${resume.name}
                Raw Summary: ${resume.rawText?.take(600) ?: "Not provided"}
            """.trimIndent())
            blocks.add(
                ContextBlock(
                    id = "active_resume",
                    priority = ContextPriority.CRITICAL,
                    title = "ACTIVE RESUME CONTEXT",
                    content = content,
                    estimatedTokens = estimateTokens(content)
                )
            )
        }

        // 2. HIGH TIER: Recent ATS, Interview Feedback, Career Goal
        latestAts?.let { ats ->
            val content = """
                Overall ATS Score: ${ats.overallScore}%
                Matched Keywords: ${ats.matchedKeywords.take(10).joinToString(", ")}
                Missing Keywords: ${ats.missingKeywords.take(10).joinToString(", ")}
            """.trimIndent()
            blocks.add(
                ContextBlock(
                    id = "recent_ats",
                    priority = ContextPriority.HIGH,
                    title = "ATS OPTIMIZATION RESULT",
                    content = content,
                    estimatedTokens = estimateTokens(content)
                )
            )
        }

        careerGoal?.let { goal ->
            val content = """
                Target Role: ${goal.targetRole}
                Timeframe: ${goal.timeframeDays} days | Min Salary: $${goal.minSalary.toInt()}
                Constraints: ${goal.constraints.joinToString(", ")}
            """.trimIndent()
            blocks.add(
                ContextBlock(
                    id = "career_goal",
                    priority = ContextPriority.HIGH,
                    title = "ACTIVE CAREER GOAL",
                    content = content,
                    estimatedTokens = estimateTokens(content)
                )
            )
        }

        interviews.firstOrNull { it.isCompleted }?.let { interview ->
            val content = """
                Role: ${interview.targetRole} (${interview.companyName})
                Overall Score: ${interview.feedback?.overallScore ?: "N/A"}/100
                Identified Weaknesses: ${interview.feedback?.improvements?.joinToString("; ") ?: "None"}
            """.trimIndent()
            blocks.add(
                ContextBlock(
                    id = "interview_history",
                    priority = ContextPriority.HIGH,
                    title = "RECENT INTERVIEW OUTCOME",
                    content = content,
                    estimatedTokens = estimateTokens(content)
                )
            )
        }

        // 3. MEDIUM TIER: Candidate Profile & Demonstrated Skills from Graph
        profile?.let { prof ->
            val skills = graph?.getNodesByType(CareerNodeType.SKILL)?.map { it.label }
                ?: prof.skills
            val content = sanitizePii("""
                Name: ${prof.fullName}
                Target Role: ${prof.targetRole}
                Work Preference: ${prof.workPreference}
                Verified Skills: ${skills.take(15).joinToString(", ")}
            """.trimIndent())
            blocks.add(
                ContextBlock(
                    id = "candidate_profile",
                    priority = ContextPriority.MEDIUM,
                    title = "CANDIDATE PROFILE",
                    content = content,
                    estimatedTokens = estimateTokens(content)
                )
            )
        }

        // 4. LOW TIER: Recent Events
        if (recentEvents.isNotEmpty()) {
            val content = recentEvents.takeLast(5).joinToString("\n") { event ->
                "- [${event.eventType}] source=${event.sourceModule}"
            }
            blocks.add(
                ContextBlock(
                    id = "recent_events",
                    priority = ContextPriority.LOW,
                    title = "RECENT CAREER EVENTS",
                    content = content,
                    estimatedTokens = estimateTokens(content)
                )
            )
        }

        // Bounded Packing: Sort by priority descending (CRITICAL -> HIGH -> MEDIUM -> LOW)
        val sortedBlocks = blocks.sortedByDescending { it.priority.weight }
        val included = mutableListOf<ContextBlock>()
        val dropped = mutableListOf<String>()
        var currentTokenCount = 0

        for (block in sortedBlocks) {
            if (currentTokenCount + block.estimatedTokens <= tokenBudget) {
                included.add(block)
                currentTokenCount += block.estimatedTokens
            } else {
                dropped.add(block.id)
            }
        }

        val promptContext = included.joinToString("\n\n") { block ->
            "--- ${block.title} (${block.priority.name}) ---\n${block.content}"
        }

        return AiContextResult(
            promptContext = promptContext,
            totalEstimatedTokens = currentTokenCount,
            includedBlocks = included.map { it.id },
            droppedBlocks = dropped
        )
    }
}
