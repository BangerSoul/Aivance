package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.events.CareerEvent
import com.bangersoul.aivance.core.common.events.CareerEventCodec
import com.bangersoul.aivance.core.common.events.CareerEventDecodeResult
import com.bangersoul.aivance.core.database.dao.CareerEventLogDao
import com.bangersoul.aivance.core.database.model.CareerEventLogEntity
import com.bangersoul.aivance.core.domain.repository.CareerEventLogRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [CareerEventLogRepository] over the v27 `career_event_log` table.
 *
 * Durable logging only — no replay path. The primary key is the event's own id, so [append] is
 * idempotent (INSERT ... ON CONFLICT IGNORE) and redelivery of the same event is a no-op.
 *
 * Both the write path ([append]) and the read path ([decodeAll]) share the single
 * [CareerEventCodec], so a payload is always encoded and decoded identically. Each persisted row
 * carries the event's explicit [CareerEvent.schemaVersion] (M04-A), and [decodeAll] validates every
 * row against the versioned contract, surfacing unknown types, unsupported versions, and malformed
 * payloads as distinct [CareerEventDecodeResult]s rather than silently coercing them.
 */
@Singleton
class CareerEventLogRepositoryImpl @Inject constructor(
    private val careerEventLogDao: CareerEventLogDao
) : CareerEventLogRepository {

    override suspend fun append(event: CareerEvent) {
        careerEventLogDao.append(
            CareerEventLogEntity(
                eventId = event.eventId,
                timestamp = event.timestamp,
                correlationId = event.correlationId,
                causationId = event.causationId,
                sourceModule = event.sourceModule,
                eventType = event.eventType,
                schemaVersion = event.schemaVersion,
                payloadJson = CareerEventCodec.encodePayload(event.payload)
            )
        )
    }

    override suspend fun count(): Int = careerEventLogDao.count()

    override suspend fun decodeAll(): List<CareerEventDecodeResult> =
        careerEventLogDao.getAll().map { row ->
            CareerEventCodec.decode(
                eventId = row.eventId,
                schemaVersion = row.schemaVersion,
                eventType = row.eventType,
                sourceModule = row.sourceModule,
                timestamp = row.timestamp,
                correlationId = row.correlationId,
                causationId = row.causationId,
                payloadJson = row.payloadJson
            )
        }
}
