package com.bangersoul.aivance.core.domain.usecase.career

import com.bangersoul.aivance.core.common.events.CareerEventCodec
import com.bangersoul.aivance.core.common.events.CareerEventDecodeResult
import com.bangersoul.aivance.core.common.events.ResumeEvent
import com.bangersoul.aivance.core.common.events.CareerEvent
import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.graph.CareerGraphNode
import com.bangersoul.aivance.core.common.graph.CareerNodeType
import com.bangersoul.aivance.core.domain.repository.CareerEventLogRepository
import com.bangersoul.aivance.core.domain.repository.CareerGraphRepository
import com.bangersoul.aivance.core.domain.replay.CareerEventReplayEngine
import com.bangersoul.aivance.core.domain.replay.CareerReplayResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the M05 deliberate replay trigger: [RebuildCareerEventProjectionUseCase] invokes the
 * replay engine exactly once and returns its result, and that invoking it drives ONLY the
 * event-provenance projection write (never the live entity `persist` path).
 */
class RebuildCareerEventProjectionUseCaseTest {

    private class FakeEventLogRepository(private val events: List<CareerEvent>) : CareerEventLogRepository {
        override suspend fun append(event: CareerEvent) = Unit
        override suspend fun count(): Int = events.size
        override suspend fun decodeAll(): List<CareerEventDecodeResult> = events.map { e ->
            CareerEventCodec.decode(
                eventId = e.eventId,
                schemaVersion = e.schemaVersion,
                eventType = e.eventType,
                sourceModule = e.sourceModule,
                timestamp = e.timestamp,
                correlationId = e.correlationId,
                causationId = e.causationId,
                payloadJson = CareerEventCodec.encodePayload(e.payload)
            )
        }
    }

    private class SpyGraphRepository : CareerGraphRepository {
        var persistCalls = 0
        var eventProjectionCalls = 0
        var lastEventNodes: List<CareerGraphNode>? = null
        override suspend fun persist(graph: CareerGraph) { persistCalls++ }
        override suspend fun loadGraph(userId: String): CareerGraph = CareerGraph(userId = userId)
        override suspend fun replaceEventProjection(eventNodes: List<CareerGraphNode>) {
            eventProjectionCalls++
            lastEventNodes = eventNodes
        }
    }

    @Test
    fun `invoke rebuilds the event provenance slice and returns success`() = runTest {
        val log = FakeEventLogRepository(listOf(ResumeEvent.Created(resumeId = "1", name = "R")))
        val graph = SpyGraphRepository()
        val useCase = RebuildCareerEventProjectionUseCase(CareerEventReplayEngine(log, graph))

        val result = useCase()

        assertTrue(result is CareerReplayResult.Success)
        assertEquals(1, (result as CareerReplayResult.Success).eventsReplayed)
        assertEquals(1, graph.eventProjectionCalls)
        // The maintenance trigger must never touch the live entity projection path.
        assertEquals(0, graph.persistCalls)
        assertEquals(CareerNodeType.CAREER_EVENT, graph.lastEventNodes!!.single().type)
    }

    @Test
    fun `invoke on an empty log succeeds with no projected nodes`() = runTest {
        val graph = SpyGraphRepository()
        val useCase = RebuildCareerEventProjectionUseCase(CareerEventReplayEngine(FakeEventLogRepository(emptyList()), graph))

        val result = useCase()

        assertTrue(result is CareerReplayResult.Success)
        assertEquals(0, (result as CareerReplayResult.Success).eventsReplayed)
        assertEquals(0, graph.persistCalls)
    }
}
