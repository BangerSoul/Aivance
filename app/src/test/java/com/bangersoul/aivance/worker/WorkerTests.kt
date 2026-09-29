package com.bangersoul.aivance.worker

import android.app.NotificationManager
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bangersoul.aivance.core.database.dao.AiAnalyticsDao
import com.bangersoul.aivance.core.database.dao.JobDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for background workers and sync infrastructure.
 */
class WorkerTests {

    private lateinit var mockContext: Context
    private lateinit var mockParams: WorkerParameters
    private lateinit var mockConnectivityMonitor: ConnectivityMonitor

    @Before
    fun setup() {
        // `SyncManager`'s constructor calls `WorkManager.getInstance(context)`, which throws
        // in a JVM unit test unless the companion singleton is mocked. Note: `WorkManager.Companion`
        // (not mockkStatic) because Kotlin callers resolve @JvmStatic functions through the
        // companion's instance method.
        val mockWorkManager = mockk<WorkManager>()
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        // Relaxed mock: `ConnectivityMonitor(context)` calls `context.getSystemService(...)`
        // in its constructor. Stub the service accessors to return null so the monitor
        // degrades to NetworkState.UNAVAILABLE instead of throwing a ClassCastException
        // (the androidx KTX extension otherwise gets a bare Object back from the mock).
        mockContext = mockk(relaxed = true) {
            every { getSystemService(any<Class<*>>()) } returns null
            every { getSystemService(any<String>()) } returns null
            // UploadManager and DownloadManager both create NotificationChannels in
            // their init blocks, so the notification service must return a mock.
            every { getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
        }
        mockParams = mockk()
        mockConnectivityMonitor = mockk {
            // isOnline defaults to false so `SyncManager.enqueue` does NOT launch an
            // asynchronous auto-drain (keeps the queue-count assertions deterministic).
            every { isOnline } returns false
            every { isUnmetered } returns true
            every { networkState } returns MutableStateFlow(NetworkState.UNMETERED)
            every { powerState } returns MutableStateFlow(PowerState.NORMAL)
            every { isFavourableForSync() } returns true
            coEvery { isInternetReachable() } returns true
            // emptyFlow: `SyncManager.init` collects this and auto-drains when the queue is
            // non-empty. No emissions means the init-collect can never trigger a drain.
            every { observeNetworkState() } returns emptyFlow()
        }
    }

    @After
    fun tearDown() {
        // mockkObject/mockkStatic are JVM-global; release so other test classes in the same
        // JVM never silently receive a mocked WorkManager.
        unmockkAll()
    }

    // ── ConnectivityMonitor Tests ───────────────────

    @Test
    fun connectivityMonitor_initialStateIsAlwaysPopulated() {
        // With no system services available (mocked context), the monitor must still
        // expose a well-defined state instead of throwing or being null.
        val monitor = ConnectivityMonitor(mockContext)
        assertNotNull(monitor.networkState.value)
        assertFalse(monitor.isOnline)
    }

    @Test
    fun connectivityMonitor_isOnlineReflectsConfiguredState() {
        assertFalse(mockConnectivityMonitor.isOnline)
    }

    @Test
    fun connectivityMonitor_isFavourableForSync_whenOnline() {
        assertTrue(mockConnectivityMonitor.isFavourableForSync())
    }

    // ── SyncManager Tests ───────────────────────────

    @Test
    fun syncManager_initialStateIsIdle() {
        val syncManager = SyncManager(mockContext, mockConnectivityMonitor)
        assertEquals(SyncState.IDLE, syncManager.syncState.value)
    }

    @Test
    fun syncManager_enqueueOperation_increasesPendingCount() {
        val syncManager = SyncManager(mockContext, mockConnectivityMonitor)
        assertEquals(0, syncManager.pendingOperationCount())

        syncManager.enqueue(
            PendingOperation(
                type = OperationType.SAVE_JOB,
                entityId = "job_123"
            )
        )

        assertEquals(1, syncManager.pendingOperationCount())
    }

    @Test
    fun syncManager_drainQueue_whenOnline_processesOperations() = runBlocking {
        val syncManager = SyncManager(mockContext, mockConnectivityMonitor)

        // Enqueue while offline so no async auto-drain is launched, then enable
        // connectivity so the explicit drain below is the only drainer (deterministic).
        syncManager.enqueue(
            PendingOperation(type = OperationType.SAVE_JOB, entityId = "job_1")
        )
        syncManager.enqueue(
            PendingOperation(type = OperationType.LOG_EVENT, entityId = "evt_1")
        )

        every { mockConnectivityMonitor.isOnline } returns true
        syncManager.drainQueue()

        assertEquals(SyncState.SUCCESS, syncManager.syncState.value)
    }

    @Test
    fun syncManager_drainQueue_whenOffline_setsOfflineState() = runBlocking {
        val offlineMonitor = mockk<ConnectivityMonitor> {
            every { isOnline } returns false
            every { isUnmetered } returns false
            every { networkState } returns MutableStateFlow(NetworkState.UNAVAILABLE)
            every { powerState } returns MutableStateFlow(PowerState.NORMAL)
            every { isFavourableForSync() } returns false
            every { observeNetworkState() } returns MutableStateFlow(NetworkState.UNAVAILABLE)
        }
        val syncManager = SyncManager(mockContext, offlineMonitor)

        syncManager.enqueue(
            PendingOperation(type = OperationType.SAVE_JOB, entityId = "job_1")
        )
        syncManager.drainQueue()

        assertEquals(SyncState.OFFLINE, syncManager.syncState.value)
    }

    @Test
    fun syncManager_clearPending_removesAll() {
        val syncManager = SyncManager(mockContext, mockConnectivityMonitor)

        syncManager.enqueue(
            PendingOperation(type = OperationType.SAVE_JOB, entityId = "job_1")
        )
        syncManager.enqueue(
            PendingOperation(type = OperationType.APPLY_JOB, entityId = "job_2")
        )

        assertEquals(2, syncManager.pendingOperationCount())

        syncManager.clearPending()

        assertEquals(0, syncManager.pendingOperationCount())
    }

    @Test
    fun syncManager_lastSyncTime_isNullInitially() {
        val syncManager = SyncManager(mockContext, mockConnectivityMonitor)
        assertEquals(null, syncManager.lastSyncTime.value)
    }

    // ── PendingOperation Tests ──────────────────────

    @Test
    fun pendingOperation_generatesUniqueId() {
        val op1 = PendingOperation(type = OperationType.LOG_EVENT, entityId = "e1")
        val op2 = PendingOperation(type = OperationType.LOG_EVENT, entityId = "e2")
        assertNotNull(op1.id)
        assertNotNull(op2.id)
        assertTrue(op1.id.isNotBlank())
        assertTrue(op2.id.isNotBlank())
        assertTrue(op1.id != op2.id)
    }

    @Test
    fun pendingOperation_retryCountStartsAtZero() {
        val op = PendingOperation(type = OperationType.SAVE_JOB, entityId = "j1")
        assertEquals(0, op.retryCount)
    }

    @Test
    fun pendingOperation_createdAtIsSet() {
        val op = PendingOperation(type = OperationType.SAVE_JOB, entityId = "j1")
        assertTrue(op.createdAt > 0)
    }

    // ── CacheCleanupWorker Tests ────────────────────

    @Test
    fun cacheCleanupWorker_deletesStaleData() = runBlocking {
        val jobDao = mockk<JobDao>()
        val analyticsDao = mockk<AiAnalyticsDao>()
        coEvery { jobDao.deleteJobsOlderThan(any()) } returns 5
        coEvery { analyticsDao.deleteEventsBefore(any()) } returns 10
        coEvery { analyticsDao.deleteOldConversations(any()) } returns 3

        val worker = CacheCleanupWorker(mockContext, mockParams, jobDao, analyticsDao)
        val result = worker.doWork()

        // WorkManager 2.11 refactored Result into Success/Failure/Retry subclasses
        // that override equals; there is no succeeded() accessor anymore.
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { jobDao.deleteJobsOlderThan(any()) }
        coVerify { analyticsDao.deleteEventsBefore(any()) }
        coVerify { analyticsDao.deleteOldConversations(any()) }
    }

    // ── SyncWorker Tests ────────────────────────────

    @Test
    fun syncWorker_runsWithCorrectName() {
        val workerClass = SyncWorker::class.java
        assertEquals("SyncWorker", workerClass.simpleName)
    }

    // ── ProviderRefreshWorker Tests ─────────────────

    @Test
    fun providerRefreshWorker_hasKnownProviders() {
        assertEquals(7, ProviderRefreshWorker.knownProviders.size)
        assertTrue(ProviderRefreshWorker.knownProviders.contains("gemini"))
        assertTrue(ProviderRefreshWorker.knownProviders.contains("openai"))
        assertTrue(ProviderRefreshWorker.knownProviders.contains("groq"))
        assertTrue(ProviderRefreshWorker.knownProviders.contains("openrouter"))
        assertTrue(ProviderRefreshWorker.knownProviders.contains("ollama"))
        assertTrue(ProviderRefreshWorker.knownProviders.contains("anthropic"))
        assertTrue(ProviderRefreshWorker.knownProviders.contains("gemma"))
    }

    // ── DownloadManager Tests ───────────────────────

    @Test
    fun downloadManager_initializesWithChannel() {
        // DownloadManager's init block creates a NotificationChannel unconditionally
        // (minSdk 26); the shared setup() stub provides the NotificationManager mock.
        val dm = DownloadManager(mockContext)
        assertNotNull(dm)
    }

    // ── UploadManager Tests ─────────────────────────

    @Test
    fun uploadManager_initializesWithChannel() {
        // UploadManager's init block creates a NotificationChannel; the shared
        // setup() stub provides the NotificationManager mock.
        val um = UploadManager(mockContext, mockk())
        assertNotNull(um)
    }

    // ── NetworkState Tests ──────────────────────────

    @Test
    fun networkState_enum_containsCorrectValues() {
        val values = NetworkState.values()
        assertEquals(3, values.size)
        assertTrue(values.contains(NetworkState.UNAVAILABLE))
        assertTrue(values.contains(NetworkState.METERED))
        assertTrue(values.contains(NetworkState.UNMETERED))
    }

    @Test
    fun powerState_enum_containsCorrectValues() {
        val values = PowerState.values()
        assertEquals(3, values.size)
        assertTrue(values.contains(PowerState.NORMAL))
        assertTrue(values.contains(PowerState.CHARGING))
        assertTrue(values.contains(PowerState.BATTERY_SAVER))
    }

    // ── SyncState Tests ─────────────────────────────

    @Test
    fun syncState_enum_containsCorrectValues() {
        val values = SyncState.values()
        assertEquals(6, values.size)
        assertTrue(values.contains(SyncState.IDLE))
        assertTrue(values.contains(SyncState.SYNCING))
        assertTrue(values.contains(SyncState.SUCCESS))
        assertTrue(values.contains(SyncState.FAILURE))
        assertTrue(values.contains(SyncState.OFFLINE))
        assertTrue(values.contains(SyncState.PARTIAL_FAILURE))
    }

    // ── OperationType Tests ─────────────────────────

    @Test
    fun operationType_enum_containsAllValues() {
        val values = OperationType.values()
        assertEquals(10, values.size)
        assertTrue(values.contains(OperationType.SAVE_JOB))
        assertTrue(values.contains(OperationType.BOOKMARK_JOB))
        assertTrue(values.contains(OperationType.APPLY_JOB))
        assertTrue(values.contains(OperationType.TRACK_APPLICATION))
        assertTrue(values.contains(OperationType.LOG_EVENT))
        assertTrue(values.contains(OperationType.SYNC_ANALYTICS))
        assertTrue(values.contains(OperationType.CREATE_RESUME))
        assertTrue(values.contains(OperationType.UPDATE_RESUME))
        assertTrue(values.contains(OperationType.DELETE_RESUME))
        assertTrue(values.contains(OperationType.UPDATE_PROFILE))
    }

    // ── ConflictStrategy Tests ──────────────────────

    @Test
    fun conflictStrategy_enum_containsAllStrategies() {
        val values = ConflictStrategy.values()
        assertEquals(4, values.size)
        assertTrue(values.contains(ConflictStrategy.LOCAL_WINS))
        assertTrue(values.contains(ConflictStrategy.REMOTE_WINS))
        assertTrue(values.contains(ConflictStrategy.MERGE_OR_FAIL))
        assertTrue(values.contains(ConflictStrategy.SKIP))
    }
}
