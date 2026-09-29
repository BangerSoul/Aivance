package com.bangersoul.aivance.core.common.model

import kotlinx.serialization.Serializable

/**
 * Inbox notification categories surfaced in the Notifications screen.
 *
 * This is the user-facing vocabulary shared by the persistence layer
 * (`notifications` table, Room v29) and every producer that records an
 * inbox entry (WorkManager workers, pipeline stage transitions).
 */
enum class NotificationType {
    /** Catch-all for system messages that fit no other category. */
    GENERAL,
    /** Application lifecycle changes from the pipeline (stage transitions, submissions). */
    APPLICATION_UPDATE,
    /** Upcoming or scheduled interview practice reminders. */
    INTERVIEW_REMINDER,
    /** New job matches found by the background alert worker. */
    JOB_ALERT,
    /** Career roadmap milestones and progress highlights. */
    ROADMAP_MILESTONE
}

/**
 * A single persisted inbox notification.
 *
 * [id] is the stable client id used for mark-read/delete and LazyColumn keys —
 * it is the primary key of the `notifications` table, not a DB row id, so
 * producers can pass any stable string (worker work names, timeline event ids).
 */
@Serializable
data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val type: NotificationType = NotificationType.GENERAL
)
