package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.events.ResumeEvent
import com.bangersoul.aivance.core.database.dao.CareerEventLogDao
import com.bangersoul.aivance.core.database.model.CareerEventLogEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit coverage for [CareerEventLogRepositoryImpl]: proves a dispatched [CareerEvent] maps into
 * a durable log row preserving its full envelope, and that redelivery is idempotent on eventId.
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
    fun `redelivery of the same event id does not duplicate`() = runTest {
        val event = ResumeEvent.Created(resumeId = "5", name = "My Resume")
        repository.append(event)
        repository.append(event)

        assertEquals(1, repository.count())
    }
}
