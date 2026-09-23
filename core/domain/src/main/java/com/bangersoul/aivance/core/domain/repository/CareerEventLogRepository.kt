package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.events.CareerEvent
import com.bangersoul.aivance.core.common.events.CareerEventDecodeResult

/**
 * Durable append-only persistence for dispatched [CareerEvent]s (Room v27 `career_event_log`).
 *
 * This is logging ONLY — there is no read/replay engine consuming the log yet. Appends are
 * idempotent on the event's own [CareerEvent.eventId], so redelivery never duplicates a row.
 *
 * Every persisted row carries an explicit [CareerEvent.schemaVersion] (M04-A). [decodeAll] surfaces
 * each row as a contract-checked [CareerEventDecodeResult] so a future replay engine can distinguish
 * decodable events from unknown types, unsupported versions, and malformed payloads — without
 * inferring the version from payload shape. Decoding here performs no projection or rehydration.
 */
interface CareerEventLogRepository {
    /** Persists [event] to the durable log (no-op if an event with the same id already exists). */
    suspend fun append(event: CareerEvent)

    /** Total number of persisted events (primarily for verification/diagnostics). */
    suspend fun count(): Int

    /**
     * Reads every persisted event in stable order and decodes each against the versioned event
     * contract. Failures are represented explicitly, never silently dropped or coerced.
     */
    suspend fun decodeAll(): List<CareerEventDecodeResult>
}
