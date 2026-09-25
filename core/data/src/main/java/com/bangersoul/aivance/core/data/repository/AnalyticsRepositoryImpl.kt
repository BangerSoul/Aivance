package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.data.mapper.toDomain
import com.bangersoul.aivance.core.data.mapper.toEntity
import com.bangersoul.aivance.core.database.dao.AnalyticsDao
import com.bangersoul.aivance.core.database.dao.AtsDao
import com.bangersoul.aivance.core.domain.analytics.*
import com.bangersoul.aivance.core.domain.repository.AnalyticsRepository
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.crm.RecruiterIntelligenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val analyticsDao: AnalyticsDao,
    private val atsDao: AtsDao,
    private val workflowRepository: ApplicationWorkflowRepository,
    private val recruiterRepository: RecruiterIntelligenceRepository,
    private val userRepository: com.bangersoul.aivance.core.domain.repository.UserRepository,
    private val interviewRepository: com.bangersoul.aivance.core.domain.repository.InterviewRepository,
    private val kpiEngine: KPIEngine,
    private val scoreEngine: CareerScoreEngine,
    private val interviewReadinessCalculator: InterviewReadinessCalculator,
    private val intelEngine: CareerIntelligenceEngine,
    private val forecastEngine: CareerForecastEngine,
    private val recommendationEngine: RecommendationEngine
) : AnalyticsRepository {

    /**
     * M-03/P2-01 self-healing guarantee: the weekly AnalyticsSnapshotWorker was
     * the only producer of snapshots, so a new user's Trends timeline stayed
     * empty for up to a week. [getSnapshots] now captures a real baseline
     * snapshot (derived from actual applications / sessions / ATS results —
     * never fabricated) whenever history is empty, before the Room flow is
     * forwarded. Every consumer — analytics dashboard, career state engine,
     * assistant context — inherits the same guarantee.
     */
    private val baselineMutex = Mutex()

    override fun getSnapshots(): Flow<CoreResult<List<AnalyticsSnapshot>>> = flow {
        ensureBaseline()
        emitAll(
            analyticsDao.getSnapshots().map { entities ->
                runCatchingCore { entities.map { it.toDomain() } }
            }
        )
    }

    /**
     * Inserts a baseline snapshot iff history is empty. Idempotent by
     * construction: the emptiness check runs again inside the mutex, so
     * concurrent collectors of [getSnapshots] can never double-insert (the
     * weekly worker calling [createSnapshot] directly can still race a first
     * view into a second row — acceptable; the empty-table guard covers the
     * steady state). A failed baseline (createSnapshot is failure-tolerant) is
     * retried on the next collection.
     */
    private suspend fun ensureBaseline() {
        baselineMutex.withLock {
            // Failure-tolerant read: a DB error here must not crash collectors —
            // the emitted flow is failure-tolerant below, and the weekly worker
            // remains the backstop. Skip-heal and retry on the next collection.
            val hasHistory = runCatchingCore { analyticsDao.getSnapshots().first().isNotEmpty() }.getOrNull()
            if (hasHistory == false) {
                createSnapshot()
            }
        }
    }

    /**
     * Records a snapshot of the *measured* career score.
     *
     * A snapshot is a historical record of a score, so it is only written when at least one
     * score dimension actually has evidence (R3-1). At zero data there is nothing measured to
     * record, and a snapshot carrying a `0` would both chart a fake data point in Trends and
     * give [getSnapshots] a fabricated value to fall back on. The caller receives a failure
     * describing the absence, which [ensureBaseline] treats as "retry once there is real data".
     */
    override suspend fun createSnapshot(): CoreResult<Long> = runCatchingCore {
        val apps = workflowRepository.getApplications().firstOrNull()?.getOrNull() ?: emptyList()
        val sessions = interviewRepository.getSessions().firstOrNull()?.getOrNull() ?: emptyList()
        val reports = atsDao.getAllReports().firstOrNull()?.map { it.toDomain() } ?: emptyList()
        val readiness = interviewReadinessCalculator.calculate(sessions)
        val recruiters = collectRecruiters(apps)

        val interviewRate = kpiEngine.calculateInterviewRate(apps)
        val scoreBreakdown = scoreEngine.calculateCompositeScore(reports, recruiters, apps.size, readiness)
        val overall = scoreBreakdown.overall
            ?: throw com.bangersoul.aivance.core.common.exception.DomainException(
                errorCode = "NO_MEASURED_SCORE",
                message = "No measured career score yet — a snapshot is only recorded once data exists"
            )

        val snapshot = AnalyticsSnapshot(
            kpis = mapOf("interview_rate" to interviewRate),
            careerScore = overall,
            // The composite is recorded alongside its inputs so the Trends chart keeps the
            // same shape it had before the dimensions became evidence-gated.
            dimensionScores = scoreBreakdown.dimensions + (CareerScoreEngine.DIM_OVERALL to overall)
        )

        analyticsDao.insertSnapshot(snapshot.toEntity())
    }

    override fun getActiveRecommendations(): Flow<CoreResult<List<CareerRecommendation>>> {
        return analyticsDao.getActiveRecommendations().map { entities ->
            runCatchingCore { entities.map { it.toDomain() } }
        }
    }

    override suspend fun refreshRecommendations(): CoreResult<Unit> = runCatchingCore {
        val summary = "System generated snapshot review"
        val result = recommendationEngine.generateRecommendations(summary)
        result.getOrNull()?.forEach { recommendation ->
            analyticsDao.insertRecommendation(recommendation.toEntity())
        }
    }

    override suspend fun dismissRecommendation(id: Long): CoreResult<Unit> = runCatchingCore {
        analyticsDao.dismissRecommendation(id)
    }

    override fun getGoals(): Flow<CoreResult<List<CareerGoal>>> {
        return analyticsDao.getGoals().map { entities ->
            runCatchingCore { entities.map { it.toDomain() } }
        }
    }

    override suspend fun saveGoal(goal: CareerGoal): CoreResult<Long> = runCatchingCore {
        analyticsDao.insertGoal(goal.toEntity())
    }

    override suspend fun updateGoalProgress(id: Long, progress: Double): CoreResult<Unit> = runCatchingCore {
        analyticsDao.updateGoalProgress(id, progress)
    }

    override fun getCareerIntelligence(): Flow<CoreResult<CareerIntelligence>> {
        return kotlinx.coroutines.flow.combine(
            atsDao.getAllReports(),
            workflowRepository.getApplications(),
            userRepository.getProfile(),
            interviewRepository.getSessions(),
            analyticsDao.getSnapshots()
                ) { allReports, appsRes, profileRes, interviewRes, snapshots ->
            runCatchingCore {
                val apps = appsRes.getOrNull() ?: emptyList()
                val profile = profileRes.getOrNull()
                val sessions = interviewRes.getOrNull() ?: emptyList()
                val recruiters = collectRecruiters(apps)
                val reports = allReports.map { it.toDomain() }
                val readiness = interviewReadinessCalculator.calculate(sessions)

                intelEngine.calculateIntelligence(
                    latestAtsReports = reports,
                    recruiters = recruiters,
                    applications = apps,
                    interviewReadiness = readiness
                ).copy(
                    weeklyReview = if (apps.isNotEmpty() || sessions.isNotEmpty()) {
                        "Hey ${profile?.fullName?.substringBefore(" ") ?: ""}, this week you applied to ${apps.size} roles, engaged with ${recruiters.size} recruiters, and completed ${sessions.size} mock interview sessions."
                    } else null
                )
            }
        }
    }

    override suspend fun runSimulation(ats: Int?, readiness: Int?): CoreResult<CareerIntelligence> = runCatchingCore {
        val current = getCareerIntelligence().first().getOrNull() ?: throw Exception("No intelligence data")
        forecastEngine.simulate(current, ats, readiness)
    }

    // ── Shared derivation helpers ─────────────────────────────
    // Extracted so createSnapshot and getCareerIntelligence derive the same
    // inputs from the same real data (previously duplicated inline).


    private suspend fun collectRecruiters(apps: List<Application>): List<Recruiter> =
        apps.flatMap { app ->
            recruiterRepository.getRecruitersForCompany(app.jobId).firstOrNull()?.getOrNull() ?: emptyList()
        }.distinctBy { it.id }
}
