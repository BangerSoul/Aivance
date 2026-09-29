package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.events.CareerEventContract
import com.bangersoul.aivance.core.common.events.CareerEventDecodeResult
import com.bangersoul.aivance.core.common.events.ResumeEvent
import com.bangersoul.aivance.core.database.dao.CareerEventLogDao
import com.bangersoul.aivance.core.database.model.CareerEventLogEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit coverage for [CareerEventLogRepositoryImpl]: proves a dispatched CareerEvent maps into
 * a durable log row preserving its full envelope (including the explicit payload schemaVersion),
 * that redelivery is idempotent on eventId, and that the read path decodes rows against the
 * versioned contract with explicit failure states.
 */
class CareerEventLogRepositoryImplTest {

    private class FakeEventLogDao : CareerEventLogDao {
        val rows = LinkedHashMap<String, CareerEventLogEntity>()
        override suspend fun append(event: CareerEventLogEntity): Long {
            // IGNORE-on-conflict semantics: first write wins.
            if (!rows.containsKey(event.eventId)) rows[event.eventId] = event
            return rows.size.toLong()
        }
        override suspend fun getAll(): List<CareerEventLogEntity> = rows.values.toList()
        override suspend fun getByType(type: String): List<CareerEventLogEntity> =
            rows.values.filter { it.eventType == type }
        override suspend fun count(): Int = rows.size
        override suspend fun prune(beforeTimestamp: Long) {
            rows.values.removeAll { it.timestamp < beforeTimestamp }
        }
    }

    private val dao = FakeEventLogDao()
    private val repository = CareerEventLogRepositoryImpl(dao)

    @Test
    fun `append persists the full event envelope`() = runTest {
        val event = ResumeEvent.Created(resumeId = "5", name = "My Resume", correlationId = "corr_1")
        repository.append(event)

        val row = dao.getAll().single()
        assertEquals(event.eventId, row.eventId)
        assertEquals("ResumeCreated", row.eventType)
        assertEquals("feature:resume", row.sourceModule)
        assertEquals("corr_1", row.correlationId)
        assertTrue(row.payloadJson.contains("My Resume"))
    }

    @Test
    fun `append assigns the explicit current payload schema version`() = runTest {
        val event = ResumeEvent.Created(resumeId = "5", name = "My Resume")
        repository.append(event)

        assertEquals(CareerEventContract.CURRENT_PAYLOAD_VERSION, dao.getAll().single().schemaVersion)
    }

    @Test
    fun `redelivery of the same event id does not duplicate`() = runTest {
        val event = ResumeEvent.Created(resumeId = "5", name = "My Resume")
        repository.append(event)
        repository.append(event)

        assertEquals(1, repository.count())
    }

    @Test
    fun `decodeAll round-trips a persisted event preserving version and payload`() = runTest {
        val event = ResumeEvent.AnalysisCompleted(resumeId = "5", versionId = "9", atsScore = 88)
        repository.append(event)

        val decoded = repository.decodeAll().single()
        assertTrue(decoded is CareerEventDecodeResult.Decoded)
        val envelope = (decoded as CareerEventDecodeResult.Decoded).envelope
        assertEquals(event.eventId, envelope.eventId)
        assertEquals("ResumeAnalysisCompleted", envelope.eventType)
        assertEquals(event.schemaVersion, envelope.schemaVersion)
        assertEquals("88", envelope.payload["atsScore"])
    }

    @Test
    fun `decodeAll reports an unsupported version explicitly without coercion`() = runTest {
        // A row persisted under a future, unsupported payload version.
        dao.append(
            CareerEventLogEntity(
                eventId = "evt_future",
                timestamp = 1,
                sourceModule = "feature:resume",
                eventType = "ResumeCreated",
                schemaVersion = 999,
                payloadJson = "{\"name\":\"x\"}"
            )
        )

        val result = repository.decodeAll().single()
        assertTrue(result is CareerEventDecodeResult.UnsupportedVersion)
        result as CareerEventDecodeResult.UnsupportedVersion
        assertEquals("ResumeCreated", result.eventType)
        assertEquals(999, result.schemaVersion)
    }

    @Test
    fun `decodeAll reports an unknown event type explicitly`() = runTest {
        dao.append(
            CareerEventLogEntity(
                eventId = "evt_unknown",
                timestamp = 1,
                sourceModule = "feature:mystery",
                eventType = "SomethingNeverContracted",
                schemaVersion = 1,
                payloadJson = "{}"
            )
        )

        val result = repository.decodeAll().single()
        assertTrue(result is CareerEventDecodeResult.UnknownType)
        assertEquals("SomethingNeverContracted", (result as CareerEventDecodeResult.UnknownType).eventType)
    }

    @Test
    fun `decodeAll reports a malformed payload explicitly`() = runTest {
        dao.append(
            CareerEventLogEntity(
                eventId = "evt_bad",
                timestamp = 1,
                sourceModule = "feature:resume",
                eventType = "ResumeCreated",
                schemaVersion = 1,
                payloadJson = "not-json"
            )
        )

        val result = repository.decodeAll().single()
        assertTrue(result is CareerEventDecodeResult.Malformed)
    }
}
