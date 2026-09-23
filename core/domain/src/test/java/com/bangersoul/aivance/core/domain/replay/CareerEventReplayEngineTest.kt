package com.bangersoul.aivance.core.domain.replay

import com.bangersoul.aivance.core.common.events.CareerEvent
import com.bangersoul.aivance.core.common.events.CareerEventCodec
import com.bangersoul.aivance.core.common.events.CareerEventDecodeResult
import com.bangersoul.aivance.core.common.events.InterviewEvent
import com.bangersoul.aivance.core.common.events.ResumeEvent
import com.bangersoul.aivance.core.common.graph.CareerGraphNode
import com.bangersoul.aivance.core.common.graph.CareerNodeType
import com.bangersoul.aivance.core.domain.repository.CareerEventLogRepository
import com.bangersoul.aivance.core.domain.repository.CareerGraphRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage for [CareerEventReplayEngine] (M04-B). Proves deterministic ordering, idempotency,
 * loud failure on undecodable events, transactional (all-or-nothing) projection, and that replay
 * performs no historical command re-execution.
 */
class CareerEventReplayEngineTest {

    /**
     * Fake decode-only log. Rows are decoded through the real [CareerEventCodec], so version/type
     * validation matches production exactly. `rawRows` lets a test inject already-persisted
     * malformed/unknown/unsupported rows without going through a real event object.
     */
    private class FakeEventLogRepository : CareerEventLogRepository {
        data class Row(
            val eventId: String,
            val schemaVersion: Int,
            val eventType: String,
            val sourceModule: String,
            val timestamp: Long,
            val payloadJson: String,
            val correlationId: String? = null,
            val causationId: String? = null
        )

        val rows = mutableListOf<Row>()

        fun add(event: CareerEvent) {
            rows.add(
                Row(
                    eventId = event.eventId,
                    schemaVersion = event.schemaVersion,
                    eventType = event.eventType,
                    sourceModule = event.sourceModule,
                    timestamp = event.timestamp,
                    payloadJson = CareerEventCodec.encodePayload(event.payload),
                    correlationId = event.correlationId,
                    causationId = event.causationId
                )
            )
        }

        override suspend fun append(event: CareerEvent) = add(event)
        override suspend fun count(): Int = rows.size
        override suspend fun decodeAll(): List<CareerEventDecodeResult> = rows.map { r ->
            CareerEventCodec.decode(
                eventId = r.eventId,
                schemaVersion = r.schemaVersion,
                eventType = r.eventType,
                sourceModule = r.sourceModule,
                timestamp = r.timestamp,
                correlationId = r.correlationId,
                causationId = r.causationId,
                payloadJson = r.payloadJson
            )
        }
    }

    /** Records event-projection writes; can be armed to throw to simulate a persistence failure. */
    private class SpyGraphRepository : CareerGraphRepository {
        var eventProjection: List<CareerGraphNode>? = null
        var replaceEventProjectionCalls = 0
        var persistCalls = 0
        var failOnWrite = false

        override suspend fun persist(graph: com.bangersoul.aivance.core.common.graph.CareerGraph) {
            persistCalls++
        }

        override suspend fun loadGraph(userId: String): com.bangersoul.aivance.core.common.graph.CareerGraph =
            com.bangersoul.aivance.core.common.graph.CareerGraph(userId = userId)

        override suspend fun replaceEventProjection(eventNodes: List<CareerGraphNode>) {
            replaceEventProjectionCalls++
            if (failOnWrite) throw IllegalStateException("simulated write failure")
            eventProjection = eventNodes
        }
    }

    private val log = FakeEventLogRepository()
    private val graph = SpyGraphRepository()
    private val engine = CareerEventReplayEngine(log, graph)

    @Test
    fun `replay projects each event into a deterministic provenance node`() = runTest {
        log.add(ResumeEvent.AnalysisCompleted(resumeId = "5", versionId = "9", atsScore = 88))

        val result = engine.replayAll()

        assertTrue(result is CareerReplayResult.Success)
        val nodes = graph.eventProjection!!
        assertEquals(1, nodes.size)
        val node = nodes.single()
        assertEquals(CareerNodeType.CAREER_EVENT, node.type)
        assertEquals("ResumeAnalysisCompleted", node.label)
        assertTrue(node.id.startsWith("event_"))
        assertEquals("88", node.properties["payload.atsScore"])
        assertEquals("1", node.properties["schemaVersion"])
    }

    @Test
    fun `replay orders events by timestamp then eventId deterministically`() = runTest {
        // Same timestamp, unsorted eventIds — the engine must tie-break on eventId, not storage order.
        log.rows.add(FakeEventLogRepository.Row("evt_c", 1, "JobViewed", "feature:jobs", 100, "{}"))
        log.rows.add(FakeEventLogRepository.Row("evt_a", 1, "JobViewed", "feature:jobs", 100, "{}"))
        log.rows.add(FakeEventLogRepository.Row("evt_b", 1, "JobViewed", "feature:jobs", 50, "{}"))

        engine.replayAll()

        val ids = graph.eventProjection!!.map { it.id }
        assertEquals(listOf("event_evt_b", "event_evt_a", "event_evt_c"), ids)
    }

    @Test
    fun `replaying the same log twice is idempotent`() = runTest {
        log.add(ResumeEvent.Created(resumeId = "1", name = "R"))
        log.add(InterviewEvent.Completed(sessionId = "s1", overallScore = 70))

        engine.replayAll()
        val first = graph.eventProjection!!.map { it.id to it.label }
        engine.replayAll()
        val second = graph.eventProjection!!.map { it.id to it.label }

        assertEquals(first, second)
        assertEquals(2, second.size)
    }

    @Test
    fun `unsupported payload version fails loudly and writes nothing`() = runTest {
        log.rows.add(FakeEventLogRepository.Row("evt_future", 999, "ResumeCreated", "feature:resume", 1, "{\"name\":\"x\"}"))

        val result = engine.replayAll()

        assertTrue(result is CareerReplayResult.Failed)
        val failure = (result as CareerReplayResult.Failed).failure
        assertTrue(failure is CareerReplayFailure.UnsupportedVersion)
        assertEquals("ResumeCreated", (failure as CareerReplayFailure.UnsupportedVersion).eventType)
        assertEquals(999, failure.schemaVersion)
        assertEquals("no projection may be written when replay fails", 0, graph.replaceEventProjectionCalls)
    }

    @Test
    fun `unknown event type fails loudly and writes nothing`() = runTest {
        log.rows.add(FakeEventLogRepository.Row("evt_x", 1, "NeverContractedEvent", "feature:mystery", 1, "{}"))

        val result = engine.replayAll()

        assertTrue(result is CareerReplayResult.Failed)
        assertTrue((result as CareerReplayResult.Failed).failure is CareerReplayFailure.UnknownType)
        assertEquals(0, graph.replaceEventProjectionCalls)
    }

    @Test
    fun `malformed payload fails loudly and writes nothing`() = runTest {
        log.rows.add(FakeEventLogRepository.Row("evt_bad", 1, "ResumeCreated", "feature:resume", 1, "not-json"))

        val result = engine.replayAll()

        assertTrue(result is CareerReplayResult.Failed)
        assertTrue((result as CareerReplayResult.Failed).failure is CareerReplayFailure.MalformedPayload)
        assertEquals(0, graph.replaceEventProjectionCalls)
    }

    @Test
    fun `one bad event aborts the whole pass even when other events are decodable`() = runTest {
        log.add(ResumeEvent.Created(resumeId = "1", name = "Good"))
        log.rows.add(FakeEventLogRepository.Row("evt_bad", 1, "ResumeCreated", "feature:resume", 2, "not-json"))
        log.add(ResumeEvent.Created(resumeId = "2", name = "AlsoGood"))

        val result = engine.replayAll()

        assertTrue(result is CareerReplayResult.Failed)
        assertEquals("a bad event must produce no partial projection", 0, graph.replaceEventProjectionCalls)
    }

    @Test
    fun `projection write failure is reported and never a false success`() = runTest {
        log.add(ResumeEvent.Created(resumeId = "1", name = "R"))
        graph.failOnWrite = true

        val result = engine.replayAll()

        assertTrue(result is CareerReplayResult.Failed)
        assertTrue((result as CareerReplayResult.Failed).failure is CareerReplayFailure.ProjectionError)
    }

    @Test
    fun `replay never re-executes historical commands via the entity persist path`() = runTest {
        log.add(ResumeEvent.AnalysisCompleted(resumeId = "5", versionId = "9", atsScore = 88))
        log.add(InterviewEvent.Completed(sessionId = "s1", overallScore = 70))

        engine.replayAll()

        // The only write is the transactional event-provenance projection; the relational
        // entity projection (persist) is never invoked, proving no command re-execution.
        assertEquals(0, graph.persistCalls)
        assertEquals(1, graph.replaceEventProjectionCalls)
    }

    @Test
    fun `empty log replays to an empty projection successfully`() = runTest {
        val result = engine.replayAll()

        assertTrue(result is CareerReplayResult.Success)
        assertEquals(0, (result as CareerReplayResult.Success).eventsReplayed)
        assertFalse(graph.eventProjection!!.isNotEmpty())
    }
}
