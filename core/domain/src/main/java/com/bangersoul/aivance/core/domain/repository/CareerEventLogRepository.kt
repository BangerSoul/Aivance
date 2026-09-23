package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.events.CareerEvent

/**
 * Durable append-only persistence for dispatched [CareerEvent]s (Room v26 `career_event_log`).
 *
 * This is logging ONLY — there is no read/replay engine consuming the log yet. Appends are
 * idempotent on the event's own [CareerEvent.eventId], so redelivery never duplicates a row.
 */
interface CareerEventLogRepository {
    /** Persists [event] to the durable log (no-op if an event with the same id already exists). */
    suspend fun append(event: CareerEvent)

    /** Total number of persisted events (primarily for verification/diagnostics). */
    suspend fun count(): Int
}
