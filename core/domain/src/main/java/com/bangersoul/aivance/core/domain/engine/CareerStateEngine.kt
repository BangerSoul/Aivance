package com.bangersoul.aivance.core.domain.engine

import com.bangersoul.aivance.core.common.events.CareerEventBus
import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.common.util.DateUtils
import com.bangersoul.aivance.core.domain.analytics.CareerScoreEngine
import com.bangersoul.aivance.core.domain.careergraph.CareerGraphEngine
import com.bangersoul.aivance.core.domain.careergraph.contentSignature
import com.bangersoul.aivance.core.domain.repository.*
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canonical Reactive Career State Engine.
 *
 * Connects:
 *   Data Changes / Domain Events ──► CareerEventBus ──► CareerStateEngine Update
 *   ──► CareerGraph Recomputation ──► Intelligence Recalculation ──► Next Best Action
 *
 * Ensures that UI screens and Copilot agents observe one single source of truth for
 * career state without duplicate calculations.
 *
 * ## Graph persistence is off the emission path (R2)
 *
 * The entity projection used to be written *inside* the state transformation, so every
 * emission of any of the twelve combined flows — including emissions that could not have
 * changed the graph, such as a provider status ping or a bus event — ran a full transactional
 * replace of every `graph_nodes`/`graph_edges` row on the emitting coroutine and held up the
 * state it was computing.
 *
 * The transform now *hands the built projection to a writer* and returns the state
 * immediately. The handoff is a [MutableStateFlow] write, which never suspends, so the state
 * path can no longer be blocked by a database transaction. The writer coroutine
 *
 *  - keeps the latest projection only (a [MutableStateFlow] conflates, so a burst collapses to
 *    its newest value instead of queueing rewrites), and
 *  - skips the write entirely when the new projection is content-identical to the last one it
 *    wrote ([contentSignature]) — the overwhelmingly common case, since most emissions carry
 *    the same entities.
 *
 * Combined, the durable table is rewritten only when the graph's *content* actually changes,
 * and never on the caller's thread.
 *
 * ## Read-after-write
 *
 * Making the write asynchronous would otherwise let a graph reader (the dashboard's
 * [com.bangersoul.aivance.core.domain.usecase.career.GetCareerGraphInsightsUseCase]) observe a
 * slice that is one revision stale, because the reader is driven by state emissions. The writer
 * therefore bumps [persistedRevision] *after* a projection lands; that revision is the fifth
 * input to the combine, so the engine emits one follow-up state whose [CareerState.graphRevision]
 * differs. Readers re-read a slice that is guaranteed to be the one just written. The follow-up
 * terminates immediately: the re-projected graph is content-identical, so the writer skips it and
 * does not bump again.
 */
@Singleton
class CareerStateEngine @Inject constructor(
    private val userRepository: UserRepository,
    private val resumeRepository: ResumeRepository,
    private val workflowRepository: ApplicationWorkflowRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val jobRepository: JobRepository,
    private val interviewRepository: InterviewRepository,
    private val providerManager: ProviderManager,
    private val careerEventBus: CareerEventBus,
    private val careerGraphEngine: CareerGraphEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Revision of the graph projection that has been durably persisted. Bumped by the writer
     * only after a real write; folded into the combine so the post-write state is emitted.
     */
    private val persistedRevision = MutableStateFlow(0L)

    /** A projection waiting to be persisted, with its content signature for dedupe. */
    private data class PendingProjection(val graph: CareerGraph, val signature: String)

    /**
     * Non-blocking, conflating handoff from the state transform to [persistProjections].
     * Writing to a [MutableStateFlow] never suspends, so no database write can ever delay a
     * state emission.
     */
    private val pendingProjection = MutableStateFlow<PendingProjection?>(null)

    val state: StateFlow<CareerState> = combine(
        combine(
            userRepository.getProfile(),
            resumeRepository.getResumes(),
            workflowRepository.getApplications(),
            ::Triple
        ),
        combine(
            analyticsRepository.getSnapshots(),
            analyticsRepository.getActiveRecommendations(),
            analyticsRepository.getCareerIntelligence(),
            ::Triple
        ),
        combine(
            providerManager.providerStatuses,
            jobRepository.getSavedJobs(),
            interviewRepository.getSessions(),
            ::Triple
        ),
        careerEventBus.events.onStart { emit(com.bangersoul.aivance.core.common.events.SystemEvent(action = "init")) },
        persistedRevision
    ) { core, intel, runtime, latestEvent, graphRevision ->
        val (profileRes, resumesRes, appsRes) = core
        val (snapshotsRes, recsRes, intelHubRes) = intel
        val (providerStatuses, savedJobsRes, sessionsRes) = runtime
        val profile = profileRes.getOrNull()
        val resumes = resumesRes.getOrNull() ?: emptyList()
        val applications = appsRes.getOrNull() ?: emptyList()
        val savedJobs = savedJobsRes.getOrNull() ?: emptyList()
        val interviewSessions = sessionsRes.getOrNull() ?: emptyList()
        val latestSnapshot = snapshotsRes.getOrNull()?.firstOrNull()
        val recommendations = recsRes.getOrNull() ?: emptyList()
        // The live career-intelligence projection (AnalyticsRepository.getCareerIntelligence)
        // recomputes reactively from ATS reports and interview sessions, so it
        // reflects a ResumeAnalysisCompleted / InterviewCompleted event as soon
        // as it lands. The periodic analytics snapshot only refreshes on a stage
        // transition or the weekly worker, so snapshot-derived scores lag behind
        // those two events. Prefer the live hub (falling back to the snapshot)
        // as the single source for the score fields every consumer reads.
        val intelHub = intelHubRes.getOrNull()

        val activeApps = applications.filter { it.status == "ACTIVE" }
        val interviews = activeApps.filter { it.currentStageId.contains("INTERVIEW", ignoreCase = true) }

        val latestResume = resumes.firstOrNull()
        val lifecycleStage = determineLifecycleStage(resumes, activeApps, providerStatuses)

        // Construct Canonical Career Graph projection from the full live state:
        // profile + resumes + saved jobs + applications + interview sessions.
        val graph = careerGraphEngine.buildGraph(
            profile = profile,
            resumes = resumes,
            jobs = savedJobs,
            applications = applications,
            interviews = interviewSessions
        )
        // Hand the projection to the durable writer instead of writing it here (R2). The
        // signature lets the writer skip a rewrite when the content is unchanged.
        pendingProjection.value = PendingProjection(graph, graph.contentSignature())

        CareerState(
            profile = ProfileState(
                name = profile?.fullName ?: "",
                targetRole = profile?.targetRole ?: "",
                skills = profile?.skills ?: emptyList(),
                completionPercentage = calculateCompletion(resumes, activeApps, latestSnapshot),
                workPreference = profile?.workPreference ?: "REMOTE",
                salaryExpectation = profile?.salaryExpectation ?: "",
                visaRequired = profile?.visaRequired ?: false
            ),
            intelligence = IntelligenceState(
                latestResumeId = latestResume?.id,
                // The hub is authoritative whenever it produced a result — including when it
                // reports "no measured score" (null). The snapshot is only a fallback for when
                // the hub itself is unavailable, never an override of a deliberate null (R3-1).
                atsScore = if (intelHub != null) {
                    intelHub.dimensionScores[CareerScoreEngine.DIM_ATS_READINESS]
                } else {
                    latestSnapshot?.dimensionScores?.get(CareerScoreEngine.DIM_ATS_READINESS)
                },
                totalResumes = resumes.size
            ),
            discovery = DiscoveryState(
                // Owned by the saved-jobs table, not by an application stage (R3-4): a job the
                // user bookmarked without applying is still a saved job, and the previous
                // `applications.currentStageId == "SAVED"` count silently dropped it.
                savedJobsCount = savedJobs.size
            ),
            pipeline = PipelineState(
                activeApplications = activeApps.size,
                upcomingInterviews = upcomingInterviews(interviewSessions, applications),
                pipelineDistribution = activeApps.groupBy { it.currentStageId }.mapValues { it.value.size }
            ),
            growth = GrowthState(
                careerScore = if (intelHub != null) intelHub.careerScore else latestSnapshot?.careerScore,
                weeklyApplicationCount = 0
            ),
            recommendations = recommendations,
            nextBestAction = recommendations.firstOrNull(),
            lifecycleStage = lifecycleStage,
            intelligenceHub = intelHub,
            graphNodeCount = graph.nodes.size,
            graphEdgeCount = graph.edges.size,
            lastEventTimestamp = latestEvent.timestamp,
            graphRevision = graphRevision
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = CareerState()
    )

    init {
        scope.launch { persistProjections() }
    }

    /**
     * Durable writer for projected graphs, deliberately decoupled from [state].
     *
     * Conflates (a newer projection replaces one that has not been written yet) and skips
     * content-identical repeats, so the table is only rewritten when the graph genuinely
     * changed. Runs on its own coroutine: a slow transaction suspends here and nowhere else.
     */
    private suspend fun persistProjections() {
        var lastPersistedSignature: String? = null
        pendingProjection
            .filterNotNull()
            .collect { pending ->
                if (pending.signature == lastPersistedSignature) return@collect
                careerGraphEngine.persist(pending.graph)
                lastPersistedSignature = pending.signature
                // Only a real write advances the revision, so the follow-up emission (and any
                // reader re-read it triggers) corresponds to a slice that is actually on disk.
                persistedRevision.update { it + 1 }
            }
    }

    /**
     * Interviews the candidate actually has scheduled.
     *
     * Sourced from the interview sessions themselves (R3-5), using each session's real
     * `startTime`. The previous implementation derived an "upcoming interview" from
     * `application.dateApplied` — the date the user *applied* — and so displayed an application
     * date as if it were an interview date. Company and role come from the session, falling back
     * to the linked application's job when the session did not record them.
     */
    private fun upcomingInterviews(
        sessions: List<InterviewSession>,
        applications: List<Application>
    ): List<UpcomingInterviewShort> {
        val jobByApplicationJobId = applications
            .mapNotNull { app -> app.job?.let { app.jobId to it } }
            .toMap()

        return sessions
            .filter { !it.isCompleted }
            .sortedBy { it.startTime }
            .map { session ->
                val linkedJob = session.jobId?.let { jobByApplicationJobId[it] }
                UpcomingInterviewShort(
                    // The id is consumed as a job id by Prep Studio's "prep for this role"
                    // action, so it carries the session's job id (empty when unknown).
                    id = session.jobId?.toString() ?: "",
                    company = session.companyName.ifBlank { linkedJob?.company ?: "" },
                    role = session.targetRole.ifBlank { linkedJob?.title ?: "" },
                    dateTime = formatInterviewDateTime(session.startTime)
                )
            }
    }

    private fun formatInterviewDateTime(timestamp: Long): String =
        "${DateUtils.formatDateDisplay(timestamp)} · ${DateUtils.formatTimeDisplay(timestamp)}"

    private fun determineLifecycleStage(
        resumes: List<Resume>,
        apps: List<Application>,
        providerStatuses: Map<String, ProviderStatus>
    ): CareerLifecycleStage {
        val hasAI = providerStatuses.values.any { it == ProviderStatus.Healthy || it == ProviderStatus.Ready || it == ProviderStatus.Active }
        if (!hasAI) return CareerLifecycleStage.ONBOARDING
        if (resumes.isEmpty()) return CareerLifecycleStage.PREPARING
        if (apps.isEmpty()) return CareerLifecycleStage.EXPLORING
        if (apps.any { it.currentStageId.contains("INTERVIEW", ignoreCase = true) }) return CareerLifecycleStage.INTERVIEWING
        return CareerLifecycleStage.APPLYING
    }

    private fun calculateCompletion(resumes: List<Resume>, apps: List<Application>, snapshot: AnalyticsSnapshot?): Int {
        var score = 0
        if (resumes.isNotEmpty()) score += 30
        if (apps.isNotEmpty()) score += 30
        if (snapshot != null) score += 40
        return score.coerceAtMost(100)
    }
}
