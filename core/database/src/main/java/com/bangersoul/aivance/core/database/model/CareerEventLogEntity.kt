package com.bangersoul.aivance.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable append-only Career Event Log (Room v26).
 *
 * Persists every [com.bangersoul.aivance.core.common.events.CareerEvent] dispatched through the
 * bus so the audit trail survives process death. The primary key is the event's own [eventId],
 * which makes writes idempotent — redelivery of the same event never creates a duplicate row.
 *
 * NOTE: This is durable logging only. No read/replay engine consumes this table yet.
 */
@Entity(
    tableName = "career_event_log",
    indices = [Index(value = ["eventType", "timestamp"], name = "idx_career_event_log_type_time")]
)
data class CareerEventLogEntity(
    @PrimaryKey
    val eventId: String,
    val timestamp: Long,
    val correlationId: String? = null,
    val causationId: String? = null,
    val sourceModule: String,
    val eventType: String,
    val payloadJson: String
)
