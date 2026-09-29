package com.bangersoul.aivance.feature.profile.worker

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.sdk.api.ModelDownloadable
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Background worker that downloads the on-device Gemma model so it survives
 * app backgrounding.
 *
 * - **Foreground notification**: an ongoing progress notification keeps the
 *   download visible (and the process elevated) while the app is backgrounded.
 * - **Resumable**: [OkHttpModelFileDownloader] writes to a `.part` file and
 *   resumes from it via HTTP Range on retry.
 * - **Retryable**: transient failures (no network, interrupted transfer) return
 *   [ListenableWorker.Result.retry], which WorkManager re-runs with backoff;
 *   permanent failures (HTTP 4xx, invalid provider) return failure.
 *
 * Input:
 * - [KEY_PROVIDER_ID] — the provider whose model is being downloaded.
 * - [KEY_MODEL_URL] — optional explicit URL (e.g. the compact model); when
 *   blank the provider's configured/default URL is used.
 */
@HiltWorker
class GemmaModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val providerRegistry: ProviderRegistry,
    private val trackEventUseCase: TrackEventUseCase
) : CoroutineWorker(appContext, params) {

    private val notifier = ModelDownloadNotifier(appContext)

    override suspend fun doWork(): ListenableWorker.Result {
        val providerId = inputData.getString(KEY_PROVIDER_ID) ?: return failure("Missing provider id")
        val modelUrl = inputData.getString(KEY_MODEL_URL)?.takeIf { it.isNotBlank() }

        val downloadable = providerRegistry.getProvider(providerId) as? ModelDownloadable
        if (downloadable == null) {
            Timber.w("GemmaModelDownloadWorker — %s is not a ModelDownloadable", providerId)
            return failure("Provider $providerId cannot download a model")
        }

        // Already downloaded (e.g. a previous attempt finished) — nothing to do.
        if (downloadable.isModelReady) {
            Timber.d("GemmaModelDownloadWorker — model already ready for %s", providerId)
            return ListenableWorker.Result.success()
        }

        if (!isOnline()) {
            Timber.d("GemmaModelDownloadWorker — offline, deferring")
            return ListenableWorker.Result.retry()
        }

        try {
            // Promote to a foreground service with the progress notification so
            // the ~3 GB download continues (and is visible) when backgrounded.
            setForeground(foregroundInfo(0f))
            trackEventUseCase(TrackEventRequest("gemma_download_worker_start"))

            // setProgress/setForeground are suspend, but the downloader's progress
            // callback is not — launch each (throttled) update on the worker's own
            // coroutine context, so the launches are cancelled automatically when
            // WorkManager stops this worker. Each launch isolates its own failures.
            val updateScope = CoroutineScope(coroutineContext)
            var lastForegroundUpdateMs = 0L
            var lastProgressUpdateMs = 0L
            var lastPublishedPercent = -1

            val result = downloadable.downloadModel(modelUrl) { progress ->
                val now = SystemClock.elapsedRealtime()
                val percent = (progress * 100f).toInt()
                val updateForeground =
                    progress > 0f && now - lastForegroundUpdateMs >= FOREGROUND_UPDATE_INTERVAL_MS

                // setProgress persists a row to the WorkManager database, and the
                // downloader invokes this callback once per 64 KiB chunk — roughly
                // 49k times for a 3 GB model. Only publish when the whole-percent
                // value actually changed AND the minimum interval has elapsed,
                // which bounds the writes to ~100 across a full download.
                val percentChanged = percent != lastPublishedPercent
                val intervalElapsed = now - lastProgressUpdateMs >= PROGRESS_UPDATE_INTERVAL_MS
                if (percentChanged && intervalElapsed) {
                    lastPublishedPercent = percent
                    lastProgressUpdateMs = now
                    if (updateForeground) lastForegroundUpdateMs = now

                    updateScope.launch {
                        try {
                            setProgress(workDataOf(KEY_PROGRESS to progress, KEY_PROVIDER_ID to providerId))
                            if (updateForeground) setForeground(foregroundInfo(progress))
                        } catch (t: Throwable) {
                            Timber.w(t, "GemmaModelDownloadWorker — progress update failed")
                        }
                    }
                }
            }

            return when (result) {
                is com.bangersoul.aivance.core.common.result.Result.Success -> {
                    trackEventUseCase(TrackEventRequest("gemma_download_worker_success"))
                    notifyFinished(success = true)
                    ListenableWorker.Result.success()
                }
                is com.bangersoul.aivance.core.common.result.Result.Failure -> {
                    trackEventUseCase(TrackEventRequest("gemma_download_worker_failed"))
                    notifyFinished(success = false)
                    when {
                        isPermanentFailure(result.error) -> ListenableWorker.Result.failure()
                        runAttemptCount >= MAX_RETRIES -> ListenableWorker.Result.failure()
                        else -> ListenableWorker.Result.retry()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "GemmaModelDownloadWorker failed for %s", providerId)
            notifyFinished(success = false)
            val errorData = workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName))
            return if (runAttemptCount < MAX_RETRIES) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.failure(errorData)
            }
        }
    }

    private fun foregroundInfo(progress: Float): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID,
            notifier.progressNotification(progress),
            // Android 14+ requires a declared foreground-service type; dataSync
            // is the fit for a long-running download (permission declared in the
            // library manifest so it merges into the app manifest).
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

    /** Posts the final success/failure notification (worker-run, not ongoing). */
    private suspend fun notifyFinished(success: Boolean) {
        withContext(Dispatchers.IO) {
            val notification = if (success) notifier.successNotification() else notifier.failureNotification()
            val nm = androidx.core.app.NotificationManagerCompat.from(applicationContext)
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                nm.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * 4xx responses (except 408/429 which are transient) are permanent: retrying
     * a 403/404 will never succeed. 5xx + IO/network failures are transient.
     */
    private fun isPermanentFailure(error: com.bangersoul.aivance.core.common.result.CoreError): Boolean {
        val code = (error as? ProviderError)?.statusCode ?: 0
        return code in 400..499 && code != 408 && code != 429
    }

    private fun failure(message: String): ListenableWorker.Result {
        Timber.w("GemmaModelDownloadWorker — %s", message)
        return ListenableWorker.Result.failure(workDataOf(KEY_ERROR to message))
    }

    companion object {
        const val UNIQUE_WORK_NAME = "gemma_model_download"
        const val KEY_PROVIDER_ID = "provider_id"
        const val KEY_MODEL_URL = "model_url"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        private const val NOTIFICATION_ID = 2001
        private const val MAX_RETRIES = 5

        /** Foreground notification is refreshed at most this often (throttle). */
        private const val FOREGROUND_UPDATE_INTERVAL_MS = 1_000L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
    }
}
