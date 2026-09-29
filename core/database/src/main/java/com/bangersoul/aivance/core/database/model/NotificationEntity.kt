package com.bangersoul.aivance.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A persisted inbox notification (Room v29).
 *
 * Primary key is the producer-supplied stable string id ([NotificationItem.id]
 * domain-side), so re-recording the same logical event (e.g. a periodic worker
 * re-running with identical work-name ids) upserts instead of duplicating.
 */
@Entity(
    tableName = "notifications",
    indices = [Index(value = ["timestamp"], name = "idx_notifications_timestamp")]
)
data class NotificationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    /** Stored as 0/1; true once the user has seen the notification. */
    val isRead: Boolean,
    /**
     * One of the [com.bangersoul.aivance.core.common.model.NotificationType]
     * names; unknown names decode to GENERAL on read rather than crashing.
     */
    val type: String
)
