package com.bangersoul.aivance.core.domain.events

import com.bangersoul.aivance.core.common.events.*
import com.bangersoul.aivance.core.domain.repository.CareerEventLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain-level dispatcher wrapping [CareerEventBus] to provide strongly-typed,
 * non-suspending event publishing with contextual correlation and causation tracking.
 *
 * Every dispatched event is ALSO appended to the durable [CareerEventLogRepository] at this
 * authoritative production boundary, so the audit trail survives process death. Persistence is
 * idempotent on the event id and runs on the dispatcher's own scope; it never blocks the emit
 * path. NOTE: this is durable logging only — no replay engine consumes the log yet.
 */
@Singleton
class CareerEventDispatcher @Inject constructor(
    private val eventBus: CareerEventBus,
    private val eventLogRepository: CareerEventLogRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Publishes any [CareerEvent] asynchronously to the event bus and appends it to the
     * durable event log. Log-append failures are isolated and never fold into the emit path.
     */
    fun dispatch(event: CareerEvent) {
        scope.launch {
            eventBus.emit(event)
        }
        scope.launch {
            runCatching { eventLogRepository.append(event) }
        }
    }

    // --- Resume Events ---
    fun onResumeCreated(resumeId: Long, name: String, correlationId: String? = null) {
        dispatch(ResumeEvent.Created(resumeId = resumeId.toString(), name = name, correlationId = correlationId))
    }

    fun onResumeUpdated(resumeId: Long, versionId: Long, correlationId: String? = null) {
        dispatch(ResumeEvent.Updated(resumeId = resumeId.toString(), versionId = versionId.toString(), correlationId = correlationId))
    }

    fun onResumeAnalysisCompleted(resumeId: Long, versionId: Long, atsScore: Int, correlationId: String? = null) {
        dispatch(ResumeEvent.AnalysisCompleted(resumeId = resumeId.toString(), versionId = versionId.toString(), atsScore = atsScore, correlationId = correlationId))
    }

    // --- ATS Events ---
    fun onAtsScanStarted(resumeVersionId: Long, jobId: Long, correlationId: String? = null) {
        dispatch(AtsEvent.ScanStarted(resumeVersionId = resumeVersionId.toString(), jobId = jobId.toString(), correlationId = correlationId))
    }

    fun onAtsScoreChanged(resumeVersionId: Long, jobId: Long, score: Int, matched: List<String>, missing: List<String>, correlationId: String? = null) {
        dispatch(AtsEvent.ScoreChanged(resumeVersionId = resumeVersionId.toString(), jobId = jobId.toString(), overallScore = score, matchedKeywords = matched, missingKeywords = missing, correlationId = correlationId))
    }

    // --- Job Events ---
    fun onJobSaved(jobId: String, company: String, title: String, correlationId: String? = null) {
        dispatch(JobEvent.Saved(jobId = jobId, company = company, title = title, correlationId = correlationId))
    }

    fun onJobViewed(jobId: String, correlationId: String? = null) {
        dispatch(JobEvent.Viewed(jobId = jobId, correlationId = correlationId))
    }

    // --- Application Events ---
    fun onApplicationCreated(applicationId: Long, jobId: Long, company: String, correlationId: String? = null) {
        dispatch(ApplicationEvent.Created(applicationId = applicationId.toString(), jobId = jobId.toString(), company = company, correlationId = correlationId))
    }

    fun onApplicationStageChanged(applicationId: Long, oldStage: String, newStage: String, correlationId: String? = null) {
        dispatch(ApplicationEvent.StageChanged(applicationId = applicationId.toString(), oldStage = oldStage, newStage = newStage, correlationId = correlationId))
    }

    // --- Interview Events ---
    fun onInterviewStarted(sessionId: String, role: String, company: String, correlationId: String? = null) {
        dispatch(InterviewEvent.Started(sessionId = sessionId, role = role, company = company, correlationId = correlationId))
    }

    fun onInterviewCompleted(sessionId: String, score: Int, weaknesses: List<String>, correlationId: String? = null) {
        dispatch(InterviewEvent.Completed(sessionId = sessionId, overallScore = score, topWeaknesses = weaknesses, correlationId = correlationId))
    }

    // --- Agent Events ---
    fun onAgentActionProposed(proposalId: String, actionType: String, riskLevel: String, correlationId: String? = null) {
        dispatch(AgentEvent.ActionProposed(proposalId = proposalId, actionType = actionType, riskLevel = riskLevel, correlationId = correlationId))
    }

    fun onAgentActionApproved(proposalId: String, correlationId: String? = null) {
        dispatch(AgentEvent.ActionApproved(proposalId = proposalId, correlationId = correlationId))
    }

    fun onAgentActionExecuted(proposalId: String, receiptId: String, status: String, correlationId: String? = null) {
        dispatch(AgentEvent.ActionExecuted(proposalId = proposalId, receiptId = receiptId, status = status, correlationId = correlationId))
    }
}
