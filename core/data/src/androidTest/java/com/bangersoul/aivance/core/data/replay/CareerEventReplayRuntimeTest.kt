package com.bangersoul.aivance.core.data.replay

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bangersoul.aivance.core.common.events.InterviewEvent
import com.bangersoul.aivance.core.common.events.JobEvent
import com.bangersoul.aivance.core.common.events.ResumeEvent
import com.bangersoul.aivance.core.common.graph.CareerEdgeType
import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.graph.CareerGraphEdge
import com.bangersoul.aivance.core.common.graph.CareerGraphNode
import com.bangersoul.aivance.core.common.graph.CareerNodeType
import com.bangersoul.aivance.core.data.repository.CareerEventLogRepositoryImpl
import com.bangersoul.aivance.core.data.repository.CareerGraphRepositoryImpl
import com.bangersoul.aivance.core.database.AivanceDatabase
import com.bangersoul.aivance.core.database.converter.EncryptedTypeConverters
import com.bangersoul.aivance.core.database.model.CareerEventLogEntity
import com.bangersoul.aivance.core.database.security.EncryptionService
import com.bangersoul.aivance.core.domain.replay.CareerEventReplayEngine
import com.bangersoul.aivance.core.domain.replay.CareerReplayFailure
import com.bangersoul.aivance.core.domain.replay.CareerReplayResult
import com.bangersoul.aivance.core.domain.usecase.career.RebuildCareerEventProjectionUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end runtime acceptance for durable event replay (M06).
 *
 * Unlike the JVM unit tests (which use fakes), this exercises the ACTUAL production stack against a
 * real Android Room v27 database:
 *
 *   CareerEventLogRepositoryImpl → CareerEventLogDao → career_event_log
 *   RebuildCareerEventProjectionUseCase → CareerEventReplayEngine
 *     → CareerGraphRepositoryImpl → GraphDao.replaceNodesOfType("CAREER_EVENT")
 *
 * It proves persistence survives a database close/reopen, replay projects deterministic idempotent
 * provenance nodes, the entity/event graph slices never erase each other (the critical M05
 * regression), ordering is deterministic, and unknown/unsupported/malformed events fail loudly with
 * no partial projection.
 */
@RunWith(AndroidJUnit4::class)
class CareerEventReplayRuntimeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "m06-replay-runtime-test"

    private lateinit var db: AivanceDatabase
    private lateinit var logRepository: CareerEventLogRepositoryImpl
    private lateinit var graphRepository: CareerGraphRepositoryImpl
    private lateinit var rebuild: RebuildCareerEventProjectionUseCase

    /** File-backed (not in-memory) so a close/reopen genuinely re-reads persisted rows (TEST A). */
    private fun openDb(): AivanceDatabase =
        Room.databaseBuilder(context, AivanceDatabase::class.java, dbName)
            .addTypeConverter(EncryptedTypeConverters(EncryptionService(context)))
            .build()

    private fun wire(database: AivanceDatabase) {
        db = database
        logRepository = CareerEventLogRepositoryImpl(database.careerEventLogDao())
        graphRepository = CareerGraphRepositoryImpl(database.graphDao())
        rebuild = RebuildCareerEventProjectionUseCase(
            CareerEventReplayEngine(logRepository, graphRepository)
        )
    }

    @Before
    fun setup() {
        context.deleteDatabase(dbName)
        wire(openDb())
    }

    @After
    fun cleanup() {
        db.close()
        context.deleteDatabase(dbName)
    }

    // ---------------------------------------------------------------- TEST A

    @Test
    fun testA_eventLogPersistenceSurvivesReopen() = runTest {
        logRepository.append(
            ResumeEvent.AnalysisCompleted(
                resumeId = "5",
                versionId = "9",
                atsScore = 88,
                eventId = "evt_a",
                timestamp = 1_000,
                correlationId = "corr_1",
                causationId = "cause_1"
            )
        )
        db.close()

        // Reopen from disk and verify every envelope column + payload round-tripped exactly.
        wire(openDb())
        val row = db.careerEventLogDao().getAll().single()
        assertThat(row.eventId).isEqualTo("evt_a")
        assertThat(row.eventType).isEqualTo("ResumeAnalysisCompleted")
        assertThat(row.schemaVersion).isEqualTo(2) // evolved to payload v2 (M04-C)
        assertThat(row.timestamp).isEqualTo(1_000)
        assertThat(row.correlationId).isEqualTo("corr_1")
        assertThat(row.causationId).isEqualTo("cause_1")
        assertThat(row.sourceModule).isEqualTo("feature:resume")
        assertThat(row.payloadJson).contains("88")
    }

    // ---------------------------------------------------------------- TEST B

    @Test
    fun testB_replayCreatesProjectionWithStableIds() = runTest {
        logRepository.append(ResumeEvent.Created(resumeId = "1", name = "R", eventId = "e1", timestamp = 10))
        logRepository.append(JobEvent.Saved(jobId = "7", company = "Acme", title = "Eng").copy(eventId = "e2", timestamp = 20))

        val result = rebuild()

        assertThat(result).isInstanceOf(CareerReplayResult.Success::class.java)
        val nodes = db.graphDao().getNodesByType("CAREER_EVENT")
        assertThat(nodes.map { it.id }).containsExactly("event_e1", "event_e2")
        assertThat(nodes.first { it.id == "event_e1" }.label).isEqualTo("ResumeCreated")
    }

    // ---------------------------------------------------------------- TEST C

    @Test
    fun testC_replayIsIdempotent() = runTest {
        logRepository.append(ResumeEvent.Created(resumeId = "1", name = "R", eventId = "e1", timestamp = 10))
        logRepository.append(InterviewEvent.Completed(sessionId = "s1", overallScore = 70).copy(eventId = "e2", timestamp = 20))

        rebuild()
        val first = db.graphDao().getNodesByType("CAREER_EVENT").map { it.id to it.label }.sortedBy { it.first }
        rebuild()
        val second = db.graphDao().getNodesByType("CAREER_EVENT").map { it.id to it.label }.sortedBy { it.first }

        assertThat(second).isEqualTo(first)
        assertThat(second).hasSize(2)
    }

    // ---------------------------------------------------------------- TEST D

    @Test
    fun testD_replayPreservesEntityProjection() = runTest {
        val entityGraph = CareerGraph(
            userId = "u1",
            nodes = mapOf(
                "user_u1" to CareerGraphNode("user_u1", CareerNodeType.PROFILE, "Alice"),
                "skill_kotlin" to CareerGraphNode("skill_kotlin", CareerNodeType.SKILL, "Kotlin"),
                "resume_1" to CareerGraphNode("resume_1", CareerNodeType.RESUME, "R"),
                "job_7" to CareerGraphNode("job_7", CareerNodeType.JOB, "Eng"),
                "company_1" to CareerGraphNode("company_1", CareerNodeType.COMPANY, "Acme"),
                "app_1" to CareerGraphNode("app_1", CareerNodeType.APPLICATION, "App"),
                "session_1" to CareerGraphNode("session_1", CareerNodeType.INTERVIEW_SESSION, "Interview")
            ),
            edges = listOf(
                CareerGraphEdge(sourceId = "user_u1", targetId = "skill_kotlin", relationType = CareerEdgeType.HAS_SKILL)
            )
        )
        graphRepository.persist(entityGraph)
        logRepository.append(ResumeEvent.Created(resumeId = "1", name = "R", eventId = "e1", timestamp = 10))

        rebuild()

        // Every entity node type + the edge survive the event-slice rebuild untouched.
        assertThat(db.graphDao().getNodesByType("PROFILE")).hasSize(1)
        assertThat(db.graphDao().getNodesByType("SKILL")).hasSize(1)
        assertThat(db.graphDao().getNodesByType("RESUME")).hasSize(1)
        assertThat(db.graphDao().getNodesByType("JOB")).hasSize(1)
        assertThat(db.graphDao().getNodesByType("COMPANY")).hasSize(1)
        assertThat(db.graphDao().getNodesByType("APPLICATION")).hasSize(1)
        assertThat(db.graphDao().getNodesByType("INTERVIEW_SESSION")).hasSize(1)
        assertThat(db.graphDao().getAllEdges()).hasSize(1)
        assertThat(db.graphDao().getNodesByType("CAREER_EVENT")).hasSize(1)
    }

    // ---------------------------------------------------------------- TEST E

    @Test
    fun testE_liveProjectionPreservesReplayProjection() = runTest {
        // 1. Live entity projection.
        val entityGraph = CareerGraph(
            userId = "u1",
            nodes = mapOf(
                "user_u1" to CareerGraphNode("user_u1", CareerNodeType.PROFILE, "Alice"),
                "skill_kotlin" to CareerGraphNode("skill_kotlin", CareerNodeType.SKILL, "Kotlin")
            ),
            edges = listOf(
                CareerGraphEdge(sourceId = "user_u1", targetId = "skill_kotlin", relationType = CareerEdgeType.HAS_SKILL)
            )
        )
        graphRepository.persist(entityGraph)

        // 2. Replay CAREER_EVENT projection.
        logRepository.append(ResumeEvent.Created(resumeId = "1", name = "R", eventId = "e1", timestamp = 10))
        rebuild()
        assertThat(db.graphDao().getNodesByType("CAREER_EVENT")).hasSize(1)

        // 3. Trigger the live entity projection AGAIN — the M05 co-writer regression.
        graphRepository.persist(entityGraph)

        // 4/5/6. Event slice, entity nodes, and edges all still present.
        assertThat(db.graphDao().getNodesByType("CAREER_EVENT")).hasSize(1)
        assertThat(db.graphDao().getNodesByType("PROFILE")).hasSize(1)
        assertThat(db.graphDao().getNodesByType("SKILL")).hasSize(1)
        assertThat(db.graphDao().getAllEdges()).hasSize(1)
    }

    // ---------------------------------------------------------------- TEST F

    @Test
    fun testF_deterministicOrdering() = runTest {
        // Inserted out of (timestamp, eventId) order; two share a timestamp to force eventId tie-break.
        logRepository.append(JobEvent.Viewed(jobId = "1").copy(eventId = "evt_c", timestamp = 100))
        logRepository.append(JobEvent.Viewed(jobId = "1").copy(eventId = "evt_a", timestamp = 100))
        logRepository.append(JobEvent.Viewed(jobId = "1").copy(eventId = "evt_b", timestamp = 50))

        rebuild()

        // Snapshot ordering is by createdAt (== event timestamp) then id; assert the deterministic sequence.
        val ordered = db.graphDao().getNodesByType("CAREER_EVENT")
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
            .map { it.id }
        assertThat(ordered).containsExactly("event_evt_b", "event_evt_a", "event_evt_c").inOrder()
    }

    // ---------------------------------------------------------------- TEST G

    @Test
    fun testG_unsupportedVersionFailsLoudly() = runTest {
        // Insert a persisted row directly with an unsupported schemaVersion.
        db.careerEventLogDao().append(
            CareerEventLogEntity(
                eventId = "evt_future",
                timestamp = 1,
                sourceModule = "feature:resume",
                eventType = "ResumeCreated",
                schemaVersion = 999,
                payloadJson = "{\"name\":\"x\"}"
            )
        )

        val result = rebuild()

        assertThat(result).isInstanceOf(CareerReplayResult.Failed::class.java)
        val failure = (result as CareerReplayResult.Failed).failure
        assertThat(failure).isInstanceOf(CareerReplayFailure.UnsupportedVersion::class.java)
        // No fallback / no projection written.
        assertThat(db.graphDao().getNodesByType("CAREER_EVENT")).isEmpty()
    }

    // ---------------------------------------------------------------- TEST H

    @Test
    fun testH_malformedPayloadFailsLoudly() = runTest {
        db.careerEventLogDao().append(
            CareerEventLogEntity(
                eventId = "evt_bad",
                timestamp = 1,
                sourceModule = "feature:resume",
                eventType = "ResumeCreated",
                schemaVersion = 1,
                payloadJson = "not-json"
            )
        )

        val result = rebuild()

        assertThat(result).isInstanceOf(CareerReplayResult.Failed::class.java)
        assertThat((result as CareerReplayResult.Failed).failure)
            .isInstanceOf(CareerReplayFailure.MalformedPayload::class.java)
        assertThat(db.graphDao().getNodesByType("CAREER_EVENT")).isEmpty()
    }

    @Test
    fun testH_unknownEventTypeFailsLoudly() = runTest {
        db.careerEventLogDao().append(
            CareerEventLogEntity(
                eventId = "evt_x",
                timestamp = 1,
                sourceModule = "feature:mystery",
                eventType = "NeverContractedEvent",
                schemaVersion = 1,
                payloadJson = "{}"
            )
        )

        val result = rebuild()

        assertThat(result).isInstanceOf(CareerReplayResult.Failed::class.java)
        assertThat((result as CareerReplayResult.Failed).failure)
            .isInstanceOf(CareerReplayFailure.UnknownType::class.java)
        assertThat(db.graphDao().getNodesByType("CAREER_EVENT")).isEmpty()
    }

    // ---------------------------------------------------------------- TEST I

    @Test
    fun testI_emptyLogReplaysSafely() = runTest {
        val result = rebuild()

        assertThat(result).isInstanceOf(CareerReplayResult.Success::class.java)
        assertThat((result as CareerReplayResult.Success).eventsReplayed).isEqualTo(0)
        assertThat(db.graphDao().getNodesByType("CAREER_EVENT")).isEmpty()
    }
}
