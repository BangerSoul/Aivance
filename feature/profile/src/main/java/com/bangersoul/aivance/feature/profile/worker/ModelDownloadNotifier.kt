package com.bangersoul.aivance.feature.profile.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.bangersoul.aivance.feature.profile.R

/**
 * Owns the notification channel + notifications for on-device model downloads.
 *
 * The worker shows an ongoing progress notification (so the download is visible
 * while the app is backgrounded) and a final success/failure notification.
 * Channel creation is idempotent, so it is safe to call on every worker run.
 */
class ModelDownloadNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "model_downloads"
        const val NOTIFICATION_ID = 2001
    }

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.model_download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.model_download_channel_desc)
        }
        context.getSystemService(Context.NOTIFICATION_SERVICE)
            ?.let { it as? NotificationManager }
            ?.createNotificationChannel(channel)
    }

    /** Ongoing progress notification used by [androidx.work.WorkManager]'s foreground service. */
    fun progressNotification(progress: Float): android.app.Notification {
        val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.model_download_notification_title))
            .setContentText(context.getString(R.string.model_download_notification_percent, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun successNotification(): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.model_download_success_title))
            .setContentText(context.getString(R.string.model_download_success_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun failureNotification(): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.model_download_failed_title))
            .setContentText(context.getString(R.string.model_download_failed_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
}
