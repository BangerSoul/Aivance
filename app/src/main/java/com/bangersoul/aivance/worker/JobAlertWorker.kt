package com.bangersoul.aivance.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.model.NotificationType
import com.bangersoul.aivance.core.database.dao.JobDao
import com.bangersoul.aivance.core.datastore.UserPreferencesRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.repository.NotificationRepository
import com.bangersoul.aivance.core.domain.repository.SearchRepository
import com.bangersoul.aivance.core.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

/**
 * Background worker that periodically polls job providers for candidate target
 * alerts. It scans the user's saved searches (falling back to a default query
 * before any search is saved), detects genuinely NEW listings that were not
 * already cached locally, and posts a notification when new high-relevance
 * jobs matching the saved criteria are found.
 *
 * Registered as a unique daily periodic worker ("periodic_job_alert") in
 * [com.bangersoul.aivance.AivanceApp]. Every posted alert is also recorded in the
 * persisted notifications inbox (Room v29) so it survives tray dismissal. The
 * stable id upserts in place, so a daily worker re-running on the same day never
 * duplicates the inbox entry.
 */
@HiltWorker
class JobAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val jobRepository: JobRepository,
    private val searchRepository: SearchRepository,
    private val jobDao: JobDao,
    private val notificationHelper: NotificationHelper,
    private val notificationRepository: NotificationRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.i("JobAlertWorker: executing background job alert scan")
        return try {
            // Target queries come from the user's saved searches. Before any
            // search has been saved, fall back to a sensible default so the
            // alert system still exercises the full pipeline.
            val savedSearches = (searchRepository.getSavedSearches().firstOrNull() as? com.bangersoul.aivance.core.common.result.Result.Success)?.data.orEmpty()
            val queries = savedSearches.map { it.query }.filter { it.isNotBlank() }
                .ifEmpty { listOf(DEFAULT_QUERY) }

            // Snapshot the URLs already in the local cache BEFORE searching, so
            // we can count genuinely new matches (searchJobs re-caches results,
            // so diffing afterwards would always yield zero).
            val preExistingUrls = jobDao.getJobsWithDetails().firstOrNull()
                ?.map { it.job.url }
                ?.toSet()
                .orEmpty()

            var totalMatches = 0
            var totalNew = 0
            // URL-deduped across ALL queries so a job matched by two saved
            // searches is only counted once in the notification message.
            val newUrls = mutableSetOf<String>()
            queries.take(MAX_QUERIES_PER_RUN).forEach { query ->
                val filter = JobSearchFilter(query = query)
                when (val results = jobRepository.searchJobs(filter, JobSortOrder.RELEVANCE)) {
                    is com.bangersoul.aivance.core.common.result.Result.Success -> {
                        totalMatches += results.data.size
                        results.data.forEach { job ->
                            if (job.url.isNotBlank() && job.url !in preExistingUrls && newUrls.add(job.url)) {
                                totalNew++
                            }
                        }
                        Timber.i(
                            "JobAlertWorker: query '%s' → %d matching jobs (%d new total)",
                            query, results.data.size, totalNew
                        )
                    }
                    is com.bangersoul.aivance.core.common.result.Result.Failure -> {
                        Timber.w("JobAlertWorker: search failed for '%s': %s", query, results.error.message)
                    }
                }
            }

            if (totalNew > 0) {
                val title = applicationContext.getString(com.bangersoul.aivance.R.string.worker_job_alert_title)
                val message = applicationContext.getString(
                    com.bangersoul.aivance.R.string.worker_job_alert_message,
                    totalNew
                )

                if (userPreferencesRepository.userPreferences.firstOrNull()
                        ?.jobAlertsEnabled ?: true
                ) {
                    notificationHelper.showJobAlert(
                        id = NOTIFICATION_ID,
                        title = title,
                        message = message
                    )
                }
                // Inbox entry regardless of the tray toggle: the durable record of the
                // new matches. Same-day re-runs upsert this row rather than duplicating.
                notificationRepository.record(
                    id = "job_alert_$WORK_NAME",
                    type = NotificationType.JOB_ALERT,
                    title = title,
                    message = message
                )
                Timber.i("JobAlertWorker: posted alert for %d new jobs", totalNew)
            } else {
                Timber.i("JobAlertWorker: no new jobs found (total matches: %d)", totalMatches)
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "JobAlertWorker: background scan failed")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "periodic_job_alert"
        const val NOTIFICATION_ID = 9001

        /** Fallback query used when the user has not saved any searches yet. */
        const val DEFAULT_QUERY = "Android Developer"

        /** Upper bound on saved searches scanned per run to bound API cost. */
        const val MAX_QUERIES_PER_RUN = 3
    }
}
