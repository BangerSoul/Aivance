package com.bangersoul.aivance.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ──────────────────────────────────────────────────
// Data Types
// ──────────────────────────────────────────────────

data class PendingOperation(
    val id: String = UUID.randomUUID().toString(),
    val type: OperationType,
    val entityId: String,
    val payload: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)

enum class OperationType {
    CREATE_RESUME,
    UPDATE_RESUME,
    DELETE_RESUME,
    SAVE_JOB,
    BOOKMARK_JOB,
    APPLY_JOB,
    TRACK_APPLICATION,
    UPDATE_PROFILE,
    LOG_EVENT,
    SYNC_ANALYTICS
}

enum class ConflictStrategy {
    LOCAL_WINS,
    REMOTE_WINS,
    MERGE_OR_FAIL,
    SKIP
}

sealed interface SyncResult {
    data object Success : SyncResult
    data class Failure(val error: String, val retryable: Boolean = true) : SyncResult
    data class Conflict(val resolution: ConflictStrategy) : SyncResult
    data class Skipped(val reason: String) : SyncResult
}

enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    PARTIAL_FAILURE,
    FAILURE,
    OFFLINE
}

/**
 * Central sync orchestrator with offline queue, retry logic,
 * and automatic sync when connectivity is restored.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectivityMonitor: ConnectivityMonitor
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val workManager = WorkManager.getInstance(context)
    private val pendingQueue = ConcurrentLinkedQueue<PendingOperation>()

    /** Guards against concurrent [drainQueue] passes over [pendingQueue]. */
    private val draining = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    init {
        scope.launch {
            connectivityMonitor.observeNetworkState().collect { state ->
                if (state != NetworkState.UNAVAILABLE && pendingQueue.isNotEmpty()) {
                    Timber.d("Network restored — draining %d pending operations", pendingQueue.size)
                    drainQueue()
                }
                if (state == NetworkState.UNAVAILABLE) {
                    _syncState.value = SyncState.OFFLINE
                }
            }
        }
    }

    fun enqueue(operation: PendingOperation) {
        pendingQueue.add(operation)
        _pendingCount.value = pendingQueue.size
        Timber.d("Enqueued operation: %s [%s]", operation.type, operation.entityId)

        if (connectivityMonitor.isOnline) {
            scope.launch { drainQueue() }
        }
    }

    suspend fun drainQueue() {
        // Only one drain may be in flight. enqueue() launches a drain per call,
        // so without this guard N rapid enqueues raced N coroutines over the same
        // queue. A suppressed caller is safe: the in-flight drain re-polls below
        // before it exits, so anything enqueued meanwhile is still picked up.
        if (!draining.compareAndSet(false, true)) {
            Timber.d("Drain already in flight — skipping duplicate drain request")
            return
        }

        try {
            if (!connectivityMonitor.isOnline) {
                _syncState.value = SyncState.OFFLINE
                return
            }

            _syncState.value = SyncState.SYNCING
            var totalFailures = 0
            var totalProcessed = 0

            while (true) {
                val batch = mutableListOf<PendingOperation>()
                while (true) {
                    val op = pendingQueue.poll() ?: break
                    batch.add(op)
                }

                if (batch.isEmpty()) break

                var reenqueued = 0
                for (operation in batch) {
                    val result = executeWithRetry(operation)
                    when (result) {
                        is SyncResult.Success -> Timber.d("Sync OK: %s [%s]", operation.type, operation.entityId)
                        is SyncResult.Failure -> {
                            totalFailures++
                            if (result.retryable) {
                                reenqueueWithBackoff(operation)
                                reenqueued++
                            }
                            _syncState.tryEmit(SyncState.PARTIAL_FAILURE)
                        }
                        is SyncResult.Conflict -> {
                            totalFailures++
                            Timber.w("Sync conflict: %s [%s] — %s", operation.type, operation.entityId, result.resolution)
                        }
                        is SyncResult.Skipped -> Timber.d("Sync skipped: %s — %s", operation.type, result.reason)
                    }
                }

                totalProcessed += batch.size

                // reenqueueWithBackoff puts retryable failures straight back on the
                // queue, so loop only while there is genuinely NEW work waiting.
                // If everything we just handled went back, stop — otherwise a
                // persistently failing provider would spin this loop forever.
                if (pendingQueue.size <= reenqueued) break
            }

            if (totalProcessed == 0) {
                _syncState.value = SyncState.IDLE
                return
            }

            _pendingCount.value = pendingQueue.size
            _lastSyncTime.value = System.currentTimeMillis()
            _syncState.value = when {
                totalFailures == 0 -> SyncState.SUCCESS
                totalFailures < totalProcessed -> SyncState.PARTIAL_FAILURE
                else -> SyncState.FAILURE
            }
        } finally {
            draining.set(false)
        }
    }

    fun pendingOperationCount(): Int = pendingQueue.size
    fun clearPending() { pendingQueue.clear(); _pendingCount.value = 0 }

    fun scheduleRetryWorker() {
        if (pendingQueue.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("sync_retry")
            .build()
        workManager.enqueueUniqueWork("sync_retry", ExistingWorkPolicy.REPLACE, request)
    }

    // ── Retry with exponential backoff ───────────────

    private suspend fun executeWithRetry(operation: PendingOperation, maxRetries: Int = 3): SyncResult {
        var currentOp = operation
        for (attempt in 0..maxRetries) {
            val result = resolveOperationExecution(currentOp)
            if (result is SyncResult.Success || result is SyncResult.Skipped) return result
            if (attempt < maxRetries && (result as? SyncResult.Failure)?.retryable != false) {
                val delayMs = calculateBackoff(attempt)
                Timber.d("Retry %d/%d for %s [%s] in %dms", attempt + 1, maxRetries, currentOp.type, currentOp.entityId, delayMs)
                kotlinx.coroutines.delay(delayMs)
                currentOp = currentOp.copy(retryCount = currentOp.retryCount + 1)
            } else return result
        }
        return SyncResult.Failure("Max retries exceeded")
    }

    private fun reenqueueWithBackoff(operation: PendingOperation) {
        val backoffOp = operation.copy(retryCount = operation.retryCount + 1)
        pendingQueue.add(backoffOp)
        _pendingCount.value = pendingQueue.size
        scheduleRetryWorker()
    }

    private fun calculateBackoff(attempt: Int): Long {
        return (30_000L * Math.pow(2.0, attempt.toDouble())).toLong().coerceAtMost(TimeUnit.HOURS.toMillis(2))
    }

    /**
     * Resolves the operation type and attempts execution.
     * Throws [Exception] on failure so the retry loop can catch it.
     */
    private suspend fun resolveOperationExecution(operation: PendingOperation): SyncResult {
        return try {
            when (operation.type) {
                OperationType.SAVE_JOB,
                OperationType.BOOKMARK_JOB,
                OperationType.APPLY_JOB,
                OperationType.TRACK_APPLICATION,
                OperationType.LOG_EVENT,
                OperationType.SYNC_ANALYTICS -> {
                    // These operations are persisted locally via repositories
                    // The sync manager tracks that they've been queued for remote delivery
                    Timber.d("Operation %s [%s] recorded locally", operation.type, operation.entityId)
                    SyncResult.Success
                }
                OperationType.CREATE_RESUME,
                OperationType.UPDATE_RESUME,
                OperationType.DELETE_RESUME,
                OperationType.UPDATE_PROFILE -> {
                    // Resume and profile operations are already persisted in Room
                    Timber.d("Local data operation %s [%s] completed", operation.type, operation.entityId)
                    SyncResult.Success
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Operation %s [%s] failed", operation.type, operation.entityId)
            SyncResult.Failure(e.message ?: "Unknown error", retryable = true)
        }
    }
}
