package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.events.CareerEvent
import com.bangersoul.aivance.core.database.dao.CareerEventLogDao
import com.bangersoul.aivance.core.database.model.CareerEventLogEntity
import com.bangersoul.aivance.core.domain.repository.CareerEventLogRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [CareerEventLogRepository] over the v26 `career_event_log` table.
 *
 * Durable logging only — no replay path. The primary key is the event's own id, so [append] is
 * idempotent (INSERT ... ON CONFLICT IGNORE) and redelivery of the same event is a no-op.
 *
 * The event's structured [CareerEvent.payload] (`Map<String, Any?>`) is flattened to a JSON object
 * of string values so the log stays a stable, schema-light audit record.
 */
@Singleton
class CareerEventLogRepositoryImpl @Inject constructor(
    private val careerEventLogDao: CareerEventLogDao
) : CareerEventLogRepository {

    private val json = Json { encodeDefaults = true }

    override suspend fun append(event: CareerEvent) {
        careerEventLogDao.append(
            CareerEventLogEntity(
                eventId = event.eventId,
                timestamp = event.timestamp,
                correlationId = event.correlationId,
                causationId = event.causationId,
                sourceModule = event.sourceModule,
                eventType = event.eventType,
                payloadJson = encodePayload(event.payload)
            )
        )
    }

    override suspend fun count(): Int = careerEventLogDao.count()

    private fun encodePayload(payload: Map<String, Any?>): String {
        val obj = JsonObject(payload.mapValues { (_, value) -> JsonPrimitive(value?.toString()) })
        return json.encodeToString(JsonObject.serializer(), obj)
    }
}
