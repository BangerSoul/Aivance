package com.bangersoul.aivance.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.bangersoul.aivance.R
import com.bangersoul.aivance.core.database.dao.JobDao
import com.bangersoul.aivance.core.database.dao.WorkflowDao
import com.bangersoul.aivance.core.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Periodic worker that checks for stale job applications (3+ days with no follow-up)
 * and sends follow-up reminder notifications.
 *
 * Reads the canonical `applications` table (R1). The retired `job_applications`
 * table expressed "submitted" as `status == "APPLIED"`; on the canonical model that
 * signal is `currentStageId == "APPLIED"` while the row is still `status == "ACTIVE"`.
 * The reminder population is therefore unchanged by the consolidation.
 */
@HiltWorker
class FollowUpWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val workflowDao: WorkflowDao,
    private val jobDao: JobDao,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS).toEpochMilli()

        val apps = workflowDao.getAllApplications().firstOrNull() ?: emptyList()
        for (app in apps) {
            // dateApplied is nullable on the canonical table: an application that was
            // only saved (never submitted) has no applied date and can never be stale.
            val appliedAt = app.dateApplied ?: continue
            val isStale = appliedAt <= threeDaysAgo &&
                app.currentStageId == STAGE_APPLIED &&
                app.status == STATUS_ACTIVE
            if (isStale) {
                val companyName = jobDao.getJobWithDetailsById(app.jobId)?.company?.name.orEmpty()
                notificationHelper.showFollowUpNotification(
                    id = app.id.toInt(),
                    title = applicationContext.getString(R.string.worker_followup_title),
                    message = applicationContext.getString(R.string.worker_followup_message, companyName)
                )
            }
        }

        return ListenableWorker.Result.success()
    }

    private companion object {
        const val STAGE_APPLIED = "APPLIED"
        const val STATUS_ACTIVE = "ACTIVE"
    }
}
