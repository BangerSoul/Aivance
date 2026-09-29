package com.bangersoul.aivance.feature.profile.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads an on-device model in the background via WorkManager.
 *
 * [enqueue] replaces any in-flight download (one unique work per provider is
 * sufficient since the underlying file is shared), and [observe] streams live
 * progress + terminal state so the Provider Management screen can reflect a
 * download that survives app backgrounding.
 */
interface ModelDownloadScheduler {

    /** Enqueues (or re-enqueues) the model download for [providerId]. */
    fun enqueue(providerId: String, modelUrl: String? = null)

    /** Live progress/status of the unique model-download work. */
    fun observe(): Flow<ModelDownloadStatus>

    /** Cancels any in-flight model download (e.g. when deleting the model). */
    fun cancel()
}

/** Progress + terminal state of the background model download. */
sealed interface ModelDownloadStatus {
    data object Idle : ModelDownloadStatus
    data class Running(val progress: Float) : ModelDownloadStatus
    data class Succeeded(val providerId: String) : ModelDownloadStatus
    data class Failed(val providerId: String) : ModelDownloadStatus
}

@Singleton
class WorkManagerModelDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelDownloadScheduler {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun enqueue(providerId: String, modelUrl: String?) {
        val request = OneTimeWorkRequestBuilder<GemmaModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    GemmaModelDownloadWorker.KEY_PROVIDER_ID to providerId,
                    GemmaModelDownloadWorker.KEY_MODEL_URL to (modelUrl ?: "")
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(GemmaModelDownloadWorker.UNIQUE_WORK_NAME)
            .build()

        workManager.enqueueUniqueWork(
            GemmaModelDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun observe(): Flow<ModelDownloadStatus> {
        return workManager
            .getWorkInfosForUniqueWorkFlow(GemmaModelDownloadWorker.UNIQUE_WORK_NAME)
            .map { infos -> infos.lastOrNull()?.toStatus() ?: ModelDownloadStatus.Idle }
    }

    override fun cancel() {
        workManager.cancelUniqueWork(GemmaModelDownloadWorker.UNIQUE_WORK_NAME)
    }

    private fun WorkInfo.toStatus(): ModelDownloadStatus {
        // WorkInfo does not expose its input data publicly; the worker echoes the
        // provider id into the progress Data, which WorkInfo does expose.
        val providerId = progress.getString(GemmaModelDownloadWorker.KEY_PROVIDER_ID) ?: "gemma"
        return when (state) {
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED ->
                ModelDownloadStatus.Running(
                    progress.getFloat(GemmaModelDownloadWorker.KEY_PROGRESS, 0f)
                )
            WorkInfo.State.SUCCEEDED -> ModelDownloadStatus.Succeeded(providerId)
            // CANCELLED only happens when the user deletes the model (the VM
            // cancels the work explicitly), so it must not surface as a failure
            // with the "will retry" messaging.
            WorkInfo.State.FAILED -> ModelDownloadStatus.Failed(providerId)
            else -> ModelDownloadStatus.Idle
        }
    }
}
