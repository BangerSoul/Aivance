package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.model.NotificationItem
import com.bangersoul.aivance.core.common.model.NotificationType
import com.bangersoul.aivance.core.common.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Durable persistence for the user-facing notifications inbox (Room v29
 * `notifications` table).
 *
 * Producers (WorkManager workers, pipeline stage transitions) append entries
 * with a stable id so re-recording the same logical event upserts in place.
 * The inbox screen observes [getNotifications] reactively and mutates read
 * state through [markAsRead]/[markAllAsRead]; deletes are user-driven
 * dismissals. Recording never fails the producer: callers decide whether a
 * failed write matters.
 */
interface NotificationRepository {

    /** The newest-first inbox, observed reactively. */
    fun getNotifications(): Flow<List<NotificationItem>>

    /** Current unread count, observed reactively (drives "Mark all read"). */
    fun observeUnreadCount(): Flow<Int>

    /**
     * Records (or updates) [notification]. Upsert semantics on the id: the
     * same logical event written twice keeps a single row.
     */
    suspend fun record(notification: NotificationItem): Result<Unit>

    /** Marks a single notification read; no-op for unknown ids. */
    suspend fun markAsRead(id: String): Result<Unit>

    /** Marks every notification read. */
    suspend fun markAllAsRead(): Result<Unit>

    /** Dismisses (deletes) a notification; no-op for unknown ids. */
    suspend fun delete(id: String): Result<Unit>

    /** Convenience factory so producers never hand-map the type vocabulary. */
    suspend fun record(
        id: String,
        type: NotificationType,
        title: String,
        message: String,
        timestamp: Long = System.currentTimeMillis()
    ): Result<Unit>
}
