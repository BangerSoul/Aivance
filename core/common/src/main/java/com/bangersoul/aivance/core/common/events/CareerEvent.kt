package com.bangersoul.aivance.core.common.events

import java.util.UUID

/**
 * Root sealed interface representing any reactive career domain event in the
 * decentralized Career Knowledge OS.
 *
 * Every event contains sufficient correlation, causality, and origin information to
 * deterministically reconstruct originating workflows across modules.
 *
 * @property eventId Unique identifier for idempotency, tracking, and deduplication.
 * @property timestamp Epoch millisecond timestamp indicating when the event was generated.
 * @property correlationId Tracing/correlation identifier grouping operations in a single workflow.
 * @property causationId Identifier of the direct command/event that caused this event.
 * @property sourceModule Name of the origin module (e.g. "feature:resume", "core:agent").
 * @property eventType Canonical event type discriminator.
 * @property payload Structured, serializable telemetry and domain attributes.
 */
sealed interface CareerEvent {
    val eventId: String
    val timestamp: Long
    val correlationId: String?
    val causationId: String?
    val sourceModule: String
    val eventType: String
    val payload: Map<String, Any?>
}

// ============================================================================
// Resume Events
// ============================================================================

sealed interface ResumeEvent : CareerEvent {
    val resumeId: String
    override val sourceModule: String get() = "feature:resume"

    data class Created(
        override val resumeId: String,
        val name: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("name" to name)
    ) : ResumeEvent {
        override val eventType: String get() = "ResumeCreated"
        constructor(resumeId: Long, name: String, correlationId: String? = null, causationId: String? = null) :
                this(resumeId.toString(), name, UUID.randomUUID().toString(), System.currentTimeMillis(), correlationId, causationId)
    }

    data class Updated(
        override val resumeId: String,
        val versionId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("versionId" to versionId)
    ) : ResumeEvent {
        override val eventType: String get() = "ResumeUpdated"
        constructor(resumeId: Long, versionId: Long, correlationId: String? = null) :
                this(resumeId.toString(), versionId.toString(), UUID.randomUUID().toString(), System.currentTimeMillis(), correlationId)
    }

    data class VersionCreated(
        override val resumeId: String,
        val versionId: String,
        val versionName: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("versionId" to versionId, "versionName" to versionName)
    ) : ResumeEvent {
        override val eventType: String get() = "ResumeVersionCreated"
    }

    data class AnalysisCompleted(
        override val resumeId: String,
        val versionId: String,
        val atsScore: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("atsScore" to atsScore)
    ) : ResumeEvent {
        override val eventType: String get() = "ResumeAnalysisCompleted"
    }

    data class SectionModified(
        override val resumeId: String,
        val versionId: String,
        val sectionType: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("sectionType" to sectionType)
    ) : ResumeEvent {
        override val eventType: String get() = "ResumeSectionModified"
    }

    data class Deleted(
        override val resumeId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = emptyMap()
    ) : ResumeEvent {
        override val eventType: String get() = "ResumeDeleted"
    }
}

// ============================================================================
// ATS (Applicant Tracking System) Events
// ============================================================================

sealed interface AtsEvent : CareerEvent {
    val resumeVersionId: String
    val jobId: String
    override val sourceModule: String get() = "feature:ats"

    data class ScanStarted(
        override val resumeVersionId: String,
        override val jobId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = emptyMap()
    ) : AtsEvent {
        override val eventType: String get() = "AtsScanStarted"
    }

    data class ScoreChanged(
        override val resumeVersionId: String,
        override val jobId: String,
        val overallScore: Int,
        val matchedKeywords: List<String> = emptyList(),
        val missingKeywords: List<String> = emptyList(),
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf(
            "score" to overallScore,
            "matchedCount" to matchedKeywords.size,
            "missingCount" to missingKeywords.size
        )
    ) : AtsEvent {
        override val eventType: String get() = "AtsScoreChanged"
        constructor(resumeVersionId: Long, jobId: Long, overallScore: Int, matchedKeywords: List<String> = emptyList(), missingKeywords: List<String> = emptyList()) :
                this(resumeVersionId.toString(), jobId.toString(), overallScore, matchedKeywords, missingKeywords)
    }

    data class OptimizationCompleted(
        override val resumeVersionId: String,
        override val jobId: String,
        val tipsGeneratedCount: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("tipsCount" to tipsGeneratedCount)
    ) : AtsEvent {
        override val eventType: String get() = "AtsOptimizationCompleted"
    }
}

// ============================================================================
// Job Events
// ============================================================================

sealed interface JobEvent : CareerEvent {
    val jobId: String
    override val sourceModule: String get() = "feature:jobs"

    data class Discovered(
        override val jobId: String,
        val company: String,
        val title: String,
        val provider: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("company" to company, "title" to title, "provider" to provider)
    ) : JobEvent {
        override val eventType: String get() = "JobDiscovered"
    }

    data class Saved(
        override val jobId: String,
        val company: String,
        val title: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("company" to company, "title" to title)
    ) : JobEvent {
        override val eventType: String get() = "JobSaved"
        constructor(jobId: Long, company: String, title: String) : this(jobId.toString(), company, title)
    }

    data class Viewed(
        override val jobId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = emptyMap()
    ) : JobEvent {
        override val eventType: String get() = "JobViewed"
    }

    data class MatchCalculated(
        override val jobId: String,
        val matchScore: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("matchScore" to matchScore)
    ) : JobEvent {
        override val eventType: String get() = "JobMatchCalculated"
    }

    data class Applied(
        override val jobId: String,
        val applicationId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("applicationId" to applicationId)
    ) : JobEvent {
        override val eventType: String get() = "JobApplied"
    }

    data class Archived(
        override val jobId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = emptyMap()
    ) : JobEvent {
        override val eventType: String get() = "JobArchived"
    }
}

// ============================================================================
// Application Events
// ============================================================================

sealed interface ApplicationEvent : CareerEvent {
    val applicationId: String
    override val sourceModule: String get() = "feature:tracker"

    data class Created(
        override val applicationId: String,
        val jobId: String,
        val company: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("jobId" to jobId, "company" to company)
    ) : ApplicationEvent {
        override val eventType: String get() = "ApplicationCreated"
    }

    data class StageChanged(
        override val applicationId: String,
        val oldStage: String,
        val newStage: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("oldStage" to oldStage, "newStage" to newStage)
    ) : ApplicationEvent {
        override val eventType: String get() = "ApplicationStageChanged"
    }

    data class TaskCreated(
        override val applicationId: String,
        val taskId: String,
        val title: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("taskId" to taskId, "title" to title)
    ) : ApplicationEvent {
        override val eventType: String get() = "ApplicationTaskCreated"
    }

    data class TaskCompleted(
        override val applicationId: String,
        val taskId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("taskId" to taskId)
    ) : ApplicationEvent {
        override val eventType: String get() = "ApplicationTaskCompleted"
        constructor(applicationId: Long, taskId: Long) : this(applicationId.toString(), taskId.toString())
    }
}

// ============================================================================
// Interview Events
// ============================================================================

sealed interface InterviewEvent : CareerEvent {
    val sessionId: String
    override val sourceModule: String get() = "feature:interview"

    data class Scheduled(
        override val sessionId: String,
        val role: String,
        val company: String,
        val scheduledTime: Long,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("role" to role, "company" to company, "scheduledTime" to scheduledTime)
    ) : InterviewEvent {
        override val eventType: String get() = "InterviewScheduled"
    }

    data class Started(
        override val sessionId: String,
        val role: String,
        val company: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("role" to role, "company" to company)
    ) : InterviewEvent {
        override val eventType: String get() = "InterviewStarted"
    }

    data class TurnEvaluated(
        override val sessionId: String,
        val turnIndex: Int,
        val scoreClarity: Int,
        val scoreAccuracy: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("turnIndex" to turnIndex, "scoreClarity" to scoreClarity, "scoreAccuracy" to scoreAccuracy)
    ) : InterviewEvent {
        override val eventType: String get() = "InterviewTurnEvaluated"
    }

    data class Completed(
        override val sessionId: String,
        val overallScore: Int,
        val topWeaknesses: List<String> = emptyList(),
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("overallScore" to overallScore, "weaknesses" to topWeaknesses)
    ) : InterviewEvent {
        override val eventType: String get() = "InterviewCompleted"
    }

    data class Evaluated(
        override val sessionId: String,
        val feedbackSummary: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("feedbackSummary" to feedbackSummary)
    ) : InterviewEvent {
        override val eventType: String get() = "InterviewEvaluated"
    }
}

// ============================================================================
// Skill & Goal Events
// ============================================================================

sealed interface SkillEvent : CareerEvent {
    val skillName: String
    override val sourceModule: String get() = "core:careergraph"

    data class Detected(
        override val skillName: String,
        val sourceEntity: String,
        val confidence: Float = 1.0f,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("sourceEntity" to sourceEntity, "confidence" to confidence)
    ) : SkillEvent {
        override val eventType: String get() = "SkillDetected"
    }

    data class Updated(
        override val skillName: String,
        val newLevel: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("newLevel" to newLevel)
    ) : SkillEvent {
        override val eventType: String get() = "SkillUpdated"
    }
}

sealed interface GoalEvent : CareerEvent {
    val goalId: String
    override val sourceModule: String get() = "feature:profile"

    data class Created(
        override val goalId: String,
        val targetRole: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("targetRole" to targetRole)
    ) : GoalEvent {
        override val eventType: String get() = "GoalCreated"
    }

    data class Changed(
        override val goalId: String,
        val newTargetRole: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("newTargetRole" to newTargetRole)
    ) : GoalEvent {
        override val eventType: String get() = "CareerGoalChanged"
    }

    data class ProgressUpdated(
        override val goalId: String,
        val percentComplete: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("percentComplete" to percentComplete)
    ) : GoalEvent {
        override val eventType: String get() = "GoalProgressUpdated"
    }

    data class Achieved(
        override val goalId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = emptyMap()
    ) : GoalEvent {
        override val eventType: String get() = "GoalAchieved"
    }
}

// ============================================================================
// Cover Letter Events
// ============================================================================

sealed interface CoverLetterEvent : CareerEvent {
    val coverLetterId: String
    override val sourceModule: String get() = "feature:coverletter"

    data class Created(
        override val coverLetterId: String,
        val company: String,
        val role: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("company" to company, "role" to role)
    ) : CoverLetterEvent {
        override val eventType: String get() = "CoverLetterCreated"
    }

    data class Updated(
        override val coverLetterId: String,
        val versionId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("versionId" to versionId)
    ) : CoverLetterEvent {
        override val eventType: String get() = "CoverLetterUpdated"
    }
}

// ============================================================================
// Provider Health Events
// ============================================================================

sealed interface ProviderEvent : CareerEvent {
    val providerId: String
    override val sourceModule: String get() = "core:sdk"

    data class Connected(
        override val providerId: String,
        val providerType: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("providerType" to providerType)
    ) : ProviderEvent {
        override val eventType: String get() = "ProviderConnected"
    }

    data class Disconnected(
        override val providerId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = emptyMap()
    ) : ProviderEvent {
        override val eventType: String get() = "ProviderDisconnected"
    }

    data class HealthChanged(
        override val providerId: String,
        val oldStatus: String,
        val newStatus: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("oldStatus" to oldStatus, "newStatus" to newStatus)
    ) : ProviderEvent {
        override val eventType: String get() = "ProviderHealthChanged"
    }
}

// ============================================================================
// Career Analytics & Intelligence Events
// ============================================================================

sealed interface CareerAnalyticsEvent : CareerEvent {
    override val sourceModule: String get() = "core:domain:analytics"

    data class ScoreChanged(
        val oldScore: Int,
        val newScore: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("oldScore" to oldScore, "newScore" to newScore)
    ) : CareerAnalyticsEvent {
        override val eventType: String get() = "CareerScoreChanged"
    }

    data class InsightGenerated(
        val insightId: String,
        val title: String,
        val priority: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("insightId" to insightId, "title" to title, "priority" to priority)
    ) : CareerAnalyticsEvent {
        override val eventType: String get() = "CareerInsightGenerated"
    }
}

// ============================================================================
// Agent Events
// ============================================================================

sealed interface AgentEvent : CareerEvent {
    override val sourceModule: String get() = "core:domain:agent"

    data class GoalCreated(
        val goalId: String,
        val targetRole: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("goalId" to goalId, "targetRole" to targetRole)
    ) : AgentEvent {
        override val eventType: String get() = "AgentGoalCreated"
    }

    data class PlanCreated(
        val planId: String,
        val goalId: String,
        val stepCount: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("planId" to planId, "stepCount" to stepCount)
    ) : AgentEvent {
        override val eventType: String get() = "AgentPlanCreated"
    }

    data class ActionProposed(
        val proposalId: String,
        val actionType: String,
        val riskLevel: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("proposalId" to proposalId, "actionType" to actionType, "riskLevel" to riskLevel)
    ) : AgentEvent {
        override val eventType: String get() = "AgentActionProposed"
    }

    data class ActionApproved(
        val proposalId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("proposalId" to proposalId)
    ) : AgentEvent {
        override val eventType: String get() = "AgentActionApproved"
    }

    data class ActionRejected(
        val proposalId: String,
        val reason: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("proposalId" to proposalId, "reason" to reason)
    ) : AgentEvent {
        override val eventType: String get() = "AgentActionRejected"
    }

    data class ActionExecuted(
        val proposalId: String,
        val receiptId: String,
        val status: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null,
        override val payload: Map<String, Any?> = mapOf("proposalId" to proposalId, "receiptId" to receiptId, "status" to status)
    ) : AgentEvent {
        override val eventType: String get() = "AgentActionExecuted"
    }
}

// ============================================================================
// Automation Events
// ============================================================================

sealed interface AutomationEvent : CareerEvent {
    val ruleId: String
    override val sourceModule: String get() = "core:domain:workflow"

    data class RuleTriggered(
        override val ruleId: String,
        val actionType: String,
        override val payload: Map<String, Any?> = emptyMap(),
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val correlationId: String? = null,
        override val causationId: String? = null
    ) : AutomationEvent {
        override val eventType: String get() = "AutomationRuleTriggered"
    }
}

// ============================================================================
// System Lifecycle Events
// ============================================================================

data class SystemEvent(
    val action: String,
    override val eventId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val correlationId: String? = null,
    override val causationId: String? = null,
    override val sourceModule: String = "core:system",
    override val eventType: String = "SystemInitialized",
    override val payload: Map<String, Any?> = emptyMap()
) : CareerEvent
