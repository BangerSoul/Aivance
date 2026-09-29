package com.bangersoul.aivance.core.database.dao

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bangersoul.aivance.core.database.AivanceDatabase
import com.bangersoul.aivance.core.database.buildTestDatabase
import com.bangersoul.aivance.core.database.model.CareerEventLogEntity
import com.bangersoul.aivance.core.database.model.CareerMemoryEntity
import com.bangersoul.aivance.core.database.model.GraphEdgeEntity
import com.bangersoul.aivance.core.database.model.GraphNodeEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round-trip coverage for the v26 Career Knowledge OS foundation DAOs: proves the graph, event
 * log, and memory persist and reload identically, and that the idempotency/upsert contracts hold.
 */
@RunWith(AndroidJUnit4::class)
class CareerOsDaoTest {

    private lateinit var db: AivanceDatabase
    private lateinit var graphDao: GraphDao
    private lateinit var eventLogDao: CareerEventLogDao
    private lateinit var memoryDao: CareerMemoryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = buildTestDatabase(context)
        graphDao = db.graphDao()
        eventLogDao = db.careerEventLogDao()
        memoryDao = db.careerMemoryDao()
    }

    @After
    fun cleanup() {
        db.close()
    }

    @Test
    fun graphRoundTripsThroughReplaceGraph() = runTest {
        val nodes = listOf(
            GraphNodeEntity("user_1", "PROFILE", "Alice", "{}", 1, 1),
            GraphNodeEntity("skill_kotlin", "SKILL", "Kotlin", "{}", 1, 1)
        )
        val edges = listOf(
            GraphEdgeEntity("user_1|HAS_SKILL|skill_kotlin", "user_1", "skill_kotlin", "HAS_SKILL", 1.0f, "{}", 1)
        )

        graphDao.replaceGraph(nodes, edges)

        assertThat(graphDao.getAllNodes()).hasSize(2)
        assertThat(graphDao.getAllEdges()).hasSize(1)
        assertThat(graphDao.getNodesByType("SKILL").single().label).isEqualTo("Kotlin")

        // Re-projecting the same graph replaces rather than duplicates.
        graphDao.replaceGraph(nodes, edges)
        assertThat(graphDao.getAllNodes()).hasSize(2)
        assertThat(graphDao.getAllEdges()).hasSize(1)
    }

    @Test
    fun eventLogAppendIsIdempotentOnEventId() = runTest {
        val event = CareerEventLogEntity(
            eventId = "evt_1",
            timestamp = 100,
            sourceModule = "feature:resume",
            eventType = "ResumeCreated",
            schemaVersion = 1,
            payloadJson = "{}"
        )

        eventLogDao.append(event)
        // Same eventId re-delivered: IGNORE strategy keeps a single row.
        eventLogDao.append(event.copy(timestamp = 200))

        assertThat(eventLogDao.count()).isEqualTo(1)
        assertThat(eventLogDao.getAll().single().timestamp).isEqualTo(100)
    }

    @Test
    fun eventLogPersistsAndReloadsExplicitSchemaVersion() = runTest {
        eventLogDao.append(
            CareerEventLogEntity(
                eventId = "evt_v",
                timestamp = 100,
                sourceModule = "feature:resume",
                eventType = "ResumeAnalysisCompleted",
                schemaVersion = 1,
                payloadJson = "{\"atsScore\":\"88\"}"
            )
        )

        val row = eventLogDao.getAll().single()
        assertThat(row.schemaVersion).isEqualTo(1)
        assertThat(row.payloadJson).contains("88")
    }

    @Test
    fun replaceNodesOfTypeRebuildsOnlyThatSliceAndIsIdempotent() = runTest {
        // A relational-entity projection (PROFILE + SKILL) plus an initial CAREER_EVENT slice.
        graphDao.replaceGraph(
            nodes = listOf(
                GraphNodeEntity("user_1", "PROFILE", "Alice", "{}", 1, 1),
                GraphNodeEntity("skill_kotlin", "SKILL", "Kotlin", "{}", 1, 1)
            ),
            edges = emptyList()
        )
        graphDao.replaceNodesOfType(
            "CAREER_EVENT",
            listOf(
                GraphNodeEntity("event_e1", "CAREER_EVENT", "ResumeCreated", "{}", 1, 1),
                GraphNodeEntity("event_e2", "CAREER_EVENT", "JobSaved", "{}", 1, 1)
            )
        )

        assertThat(graphDao.getNodesByType("CAREER_EVENT")).hasSize(2)
        // Entity slice untouched by the event-slice rebuild.
        assertThat(graphDao.getNodesByType("PROFILE")).hasSize(1)
        assertThat(graphDao.getNodesByType("SKILL")).hasSize(1)

        // Replaying a smaller event set replaces the whole slice with no stale/duplicate rows.
        graphDao.replaceNodesOfType(
            "CAREER_EVENT",
            listOf(GraphNodeEntity("event_e1", "CAREER_EVENT", "ResumeCreated", "{}", 1, 1))
        )
        assertThat(graphDao.getNodesByType("CAREER_EVENT")).hasSize(1)
        assertThat(graphDao.getNodesByType("PROFILE")).hasSize(1)
    }

    @Test
    fun replaceEntityProjectionLeavesCareerEventSliceIntact() = runTest {
        // Replay writes the event-provenance slice first.
        graphDao.replaceNodesOfType(
            "CAREER_EVENT",
            listOf(
                GraphNodeEntity("event_e1", "CAREER_EVENT", "ResumeCreated", "{}", 1, 1),
                GraphNodeEntity("event_e2", "CAREER_EVENT", "JobSaved", "{}", 1, 1)
            )
        )

        // A live entity projection replaces entity nodes + edges but must NOT erase CAREER_EVENT.
        graphDao.replaceEntityProjection(
            nodes = listOf(
                GraphNodeEntity("user_1", "PROFILE", "Alice", "{}", 1, 1),
                GraphNodeEntity("skill_kotlin", "SKILL", "Kotlin", "{}", 1, 1)
            ),
            edges = listOf(
                GraphEdgeEntity("user_1|HAS_SKILL|skill_kotlin", "user_1", "skill_kotlin", "HAS_SKILL", 1.0f, "{}", 1)
            )
        )

        assertThat(graphDao.getNodesByType("CAREER_EVENT")).hasSize(2)
        assertThat(graphDao.getNodesByType("PROFILE")).hasSize(1)
        assertThat(graphDao.getNodesByType("SKILL")).hasSize(1)
        assertThat(graphDao.getAllEdges()).hasSize(1)

        // Re-projecting the entity slice again is idempotent and still preserves the event slice.
        graphDao.replaceEntityProjection(
            nodes = listOf(GraphNodeEntity("user_1", "PROFILE", "Alice", "{}", 1, 1)),
            edges = emptyList()
        )
        assertThat(graphDao.getNodesByType("CAREER_EVENT")).hasSize(2)
        assertThat(graphDao.getNodesByType("PROFILE")).hasSize(1)
        assertThat(graphDao.getNodesByType("SKILL")).isEmpty()
    }

    @Test
    fun memoryUpsertReplacesSameIdAndSurvivesReload() = runTest {
        val entry = CareerMemoryEntity(
            memoryId = "mem_1",
            type = "FACT",
            content = "Ships Android apps",
            createdAt = 1,
            updatedAt = 1,
            confidence = 0.5f,
            sourceEventIdsJson = "[]",
            evidenceRefsJson = "[]",
            isUserConfirmed = false
        )
        memoryDao.upsert(entry)

        // Confirming the inference rewrites the same row.
        memoryDao.upsert(entry.copy(confidence = 1.0f, isUserConfirmed = true, updatedAt = 2))

        val reloaded = memoryDao.getById("mem_1")
        assertThat(memoryDao.getAll()).hasSize(1)
        assertThat(reloaded?.isUserConfirmed).isTrue()
        assertThat(reloaded?.confidence).isEqualTo(1.0f)
    }
}
