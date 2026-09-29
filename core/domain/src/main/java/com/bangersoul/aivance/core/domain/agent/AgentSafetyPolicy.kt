package com.bangersoul.aivance.core.domain.agent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of evaluating an incoming input or planned action against agent safety invariants.
 */
sealed interface SafetyEvaluationResult {
    data object Safe : SafetyEvaluationResult
    data class Blocked(val reason: String, val violationType: ViolationType) : SafetyEvaluationResult
}

enum class ViolationType {
    PROMPT_INJECTION,
    MALICIOUS_CONTENT,
    RUNAWAY_LOOP,
    REPLAY_ATTACK,
    UNAUTHORIZED_SIDE_EFFECT
}

/**
 * Agent Safety Policy Engine.
 *
 * Implements defensive validation against:
 * 1. Prompt Injection (jailbreak attempts, system instruction override, credential exfiltration).
 * 2. Malicious Content in Job Descriptions or Recruiter Inboxes (embedded scripts, hidden instructions).
 * 3. Infinite Agent Loops & Runaway Plans (step thresholds, duplicate action spam).
 * 4. Replay attacks on approved proposals.
 */
@Singleton
class AgentSafetyPolicy @Inject constructor() {

    companion object {
        const val MAX_PLAN_STEPS_THRESHOLD = 30
        const val MAX_REPEATED_ACTIONS_THRESHOLD = 3

        private val INJECTION_PATTERNS = listOf(
            Regex("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
            Regex("(?i)disregard\\s+prior\\s+system\\s+prompt"),
            Regex("(?i)reveal\\s+(all\\s+)?api\\s+keys"),
            Regex("(?i)system\\s+prompt\\s+override"),
            Regex("(?i)exfiltrate\\s+user\\s+data"),
            Regex("(?i)<script.*?>.*?</script.*?>"),
            Regex("(?i)DROP\\s+TABLE"),
            Regex("(?i)DELETE\\s+FROM\\s+users")
        )
    }

    /**
     * Inspects untrusted text (job descriptions, user prompts, web content) for prompt injection.
     */
    fun evaluatePromptSafety(input: String): SafetyEvaluationResult {
        for (pattern in INJECTION_PATTERNS) {
            if (pattern.containsMatchIn(input)) {
                return SafetyEvaluationResult.Blocked(
                    reason = "Prompt contains forbidden pattern matching injection heuristic: ${pattern.pattern}",
                    violationType = ViolationType.PROMPT_INJECTION
                )
            }
        }
        return SafetyEvaluationResult.Safe
    }

    /**
     * Sanitizes external text before feeding into AI context, stripping script tags and command overrides.
     */
    fun sanitizeExternalContent(raw: String): String {
        var clean = raw
        for (pattern in INJECTION_PATTERNS) {
            clean = clean.replace(pattern, "[BLOCKED_SUSPICIOUS_CONTENT]")
        }
        return clean
    }

    /**
     * Verifies plan invariants to prevent runaway execution or infinite looping.
     */
    fun evaluatePlanInvariants(plan: AgentPlan): SafetyEvaluationResult {
        if (plan.steps.size > MAX_PLAN_STEPS_THRESHOLD) {
            return SafetyEvaluationResult.Blocked(
                reason = "Plan exceeds maximum allowable step limit ($MAX_PLAN_STEPS_THRESHOLD steps)",
                violationType = ViolationType.RUNAWAY_LOOP
            )
        }

        // Detect duplicate action spam
        val actionCounts = plan.steps.groupBy { "${it.stepType}_${it.description.take(40)}" }
        val repeated = actionCounts.entries.firstOrNull { it.value.size > MAX_REPEATED_ACTIONS_THRESHOLD }
        if (repeated != null) {
            return SafetyEvaluationResult.Blocked(
                reason = "Plan contains duplicate action spam loop: ${repeated.key} repeated ${repeated.value.size} times",
                violationType = ViolationType.RUNAWAY_LOOP
            )
        }

        return SafetyEvaluationResult.Safe
    }
}
