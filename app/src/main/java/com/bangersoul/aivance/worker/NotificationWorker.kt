package com.bangersoul.aivance.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.bangersoul.aivance.MainActivity
import com.bangersoul.aivance.R
import com.bangersoul.aivance.core.common.model.NotificationType as InboxNotificationType
import com.bangersoul.aivance.core.domain.repository.NotificationRepository
import com.bangersoul.aivance.core.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Notification types supported by [NotificationWorker].
 */
enum class NotificationType {
    FOLLOW_UP_REMINDER,
    INTERVIEW_REMINDER,
    APPLICATION_UPDATE,
    SYNC_COMPLETED,
    SYNC_FAILED,
    RESUME_ANALYSIS_COMPLETE,
    JOB_ALERT
}

/**
 * Worker that sends push notifications to the user with deep-link actions.
 *
 * Every dispatched notification is also recorded in the persisted notifications
 * inbox (Room v29) under its worker-specific id, so a re-enqueued worker updates
 * its entry in place instead of duplicating it.
 */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationHelper: NotificationHelper,
    private val notificationRepository: NotificationRepository
) : CoroutineWorker(context, params) {

    private val appContext: Context = context

    override suspend fun doWork(): ListenableWorker.Result {
        val inputData = this.inputData
        val notificationType = inputData.getString(EXTRA_NOTIFICATION_TYPE)
            ?.let { try { NotificationType.valueOf(it) } catch (_: Exception) { null } }
            ?: return ListenableWorker.Result.failure()

        val title = inputData.getString(EXTRA_TITLE) ?: appContext.getString(R.string.app_name)
        val message = inputData.getString(EXTRA_MESSAGE) ?: ""
        val deepLinkUri = inputData.getString(EXTRA_DEEP_LINK)

        Timber.d("NotificationWorker — sending %s: %s", notificationType, title)

        try {
            val channelId = when (notificationType) {
                NotificationType.FOLLOW_UP_REMINDER -> NotificationHelper.CHANNEL_FOLLOW_UPS
                NotificationType.INTERVIEW_REMINDER -> NotificationHelper.CHANNEL_INTERVIEWS
                NotificationType.APPLICATION_UPDATE -> NotificationHelper.CHANNEL_APPLICATIONS
                NotificationType.SYNC_COMPLETED, NotificationType.SYNC_FAILED -> NotificationHelper.CHANNEL_SYNC
                NotificationType.RESUME_ANALYSIS_COMPLETE -> NotificationHelper.CHANNEL_FOLLOW_UPS
                NotificationType.JOB_ALERT -> NotificationHelper.CHANNEL_APPLICATIONS
            }

            val deepLinkIntent = if (deepLinkUri != null) {
                Intent(Intent.ACTION_VIEW).apply {
                    data = deepLinkUri.toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                appContext,
                (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                deepLinkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            when (notificationType) {
                NotificationType.INTERVIEW_REMINDER -> {
                    val interviewIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = "aivance://interview".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val interviewPI = PendingIntent.getActivity(
                        appContext, 0, interviewIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(android.R.drawable.ic_media_play, "Start Interview", interviewPI)
                }
                NotificationType.APPLICATION_UPDATE -> {
                    val trackerIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = "aivance://saved".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val trackerPI = PendingIntent.getActivity(
                        appContext, 1, trackerIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(android.R.drawable.ic_menu_view, "View Tracker", trackerPI)
                }
                NotificationType.SYNC_FAILED -> {
                    val settingsIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = "aivance://settings".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val settingsPI = PendingIntent.getActivity(
                        appContext, 2, settingsIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(android.R.drawable.ic_menu_manage, "Settings", settingsPI)
                }
                else -> { }
            }

            // Durable inbox record — independent of tray delivery so a missed
            // notification (permission denied, Doze) is still visible in-app.
            notificationRepository.record(
                id = "worker_notification_$notificationType",
                type = notificationType.toInboxType(),
                title = title,
                message = message
            )

            try {
                NotificationManagerCompat.from(appContext).notify(
                    (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                    builder.build()
                )
                Timber.d("Notification sent: %s", notificationType)
            } catch (e: SecurityException) {
                Timber.w("Notification permission not granted for %s", notificationType)
            }

            return ListenableWorker.Result.success()

        } catch (e: Exception) {
            Timber.e(e, "NotificationWorker failed")
            return if (runAttemptCount < 3) ListenableWorker.Result.retry() else ListenableWorker.Result.failure()
        }
    }

    companion object {
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_DEEP_LINK = "deep_link_uri"

        /**
         * Worker-side event vocabulary → user-facing inbox category.
         * Unmappable events land in GENERAL rather than being dropped.
         */
        private fun NotificationType.toInboxType(): InboxNotificationType = when (this) {
            NotificationType.FOLLOW_UP_REMINDER -> InboxNotificationType.APPLICATION_UPDATE
            NotificationType.INTERVIEW_REMINDER -> InboxNotificationType.INTERVIEW_REMINDER
            NotificationType.APPLICATION_UPDATE -> InboxNotificationType.APPLICATION_UPDATE
            NotificationType.JOB_ALERT -> InboxNotificationType.JOB_ALERT
            NotificationType.SYNC_COMPLETED,
            NotificationType.SYNC_FAILED,
            NotificationType.RESUME_ANALYSIS_COMPLETE -> InboxNotificationType.GENERAL
        }
    }
}
