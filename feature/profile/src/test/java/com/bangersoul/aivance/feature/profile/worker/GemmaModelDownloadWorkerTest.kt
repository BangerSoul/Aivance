package com.bangersoul.aivance.feature.profile.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.api.CompactModel
import com.bangersoul.aivance.sdk.api.ModelDownloadable
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import com.bangersoul.aivance.sdk.model.AiMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * Tests [GemmaModelDownloadWorker]'s WorkManager behavior: foreground progress,
 * retry vs permanent-failure classification, and already-ready short-circuit.
 * The underlying resumable transfer is covered by
 * `OkHttpModelFileDownloaderTest` in `core:ai-providers`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GemmaModelDownloadWorkerTest {

    private val registry: ProviderRegistry = mockk()
    private val trackEvent: TrackEventUseCase = mockk()

    /** Fake on-device provider whose download behavior is scripted per test. */
    private open class FakeModelDownloadable(
        var ready: Boolean = false,
        var downloadResult: Result<Unit> = Result.Success(Unit),
        var progressSteps: MutableList<Float> = mutableListOf()
    ) : AIProvider(
        metadata = ProviderMetadata(
            id = "gemma", name = "Gemma (On-device)", type = ProviderType.AI,
            version = "1.0.0", description = "on-device", author = "test"
        ),
        capabilities = setOf(ProviderCapability.AI.Chat)
    ), ModelDownloadable {

        override val isModelReady: Boolean get() = ready
        override val modelSizeBytes: Long = 3_000_000_000L
        override val compactModel: CompactModel = CompactModel(
            name = "FunctionGemma 270M", sizeBytes = 300_000_000L, url = "https://example.com/compact.task"
        )

        override suspend fun downloadModel(url: String?, onProgress: (Float) -> Unit): Result<Unit> {
            if (downloadResult is Result.Success) {
                listOf(0.25f, 0.5f, 1f).forEach { p ->
                    progressSteps.add(p)
                    onProgress(p)
                }
                ready = true
            }
            return downloadResult
        }

        override suspend fun deleteModel(): Result<Unit> = Result.Success(Unit)
        override suspend fun generateText(prompt: String): Result<String> = Result.Success("x")
        override suspend fun chat(messages: List<AiMessage>): Result<String> = Result.Success("x")
        override fun streamText(prompt: String): Flow<String> = flowOf("x")
        override fun streamChat(messages: List<AiMessage>): Flow<Result<String>> = flowOf(Result.Success("x"))
        override suspend fun listModels(): Result<List<String>> = Result.Success(emptyList())
        override suspend fun onInitialize() {}
        override suspend fun onStart() {}
        override suspend fun onStop() {}
        override suspend fun onDispose() {}
    }

    private val fakeProvider = FakeModelDownloadable()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        every { registry.getProvider("gemma") } returns fakeProvider
        coEvery { trackEvent(any<TrackEventRequest>()) } returns Result.Success(Unit)

        // Robolectric's ConnectivityManager has no active network by default;
        // give it one so the worker's online gate passes.
        setOnline(true)
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    /**
     * Drives [ConnectivityManager] through its Robolectric shadow: online means
     * an active WiFi network with INTERNET capability, offline clears it.
     *
     * Quirks of this shadow version: getActiveNetwork() returns null unless
     * `setDefaultNetworkActive(true)` was called, and it resolves the network
     * through a map keyed by the active NetworkInfo's *type* — so the network's
     * netId is created equal to TYPE_WIFI (1) to make that lookup hit.
     */
    private fun setOnline(online: Boolean) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = Shadows.shadowOf(cm)
        if (!online) {
            shadow.clearAllNetworks()
            shadow.setActiveNetworkInfo(null)
            shadow.setDefaultNetworkActive(false)
            return
        }
        val network = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
        // Built via the shadow: NetworkInfo's direct constructor hits a null
        // static stateMap under the Robolectric sandbox.
        val networkInfo = ShadowNetworkInfo.newInstance(
            NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI,
            0,
            true,
            NetworkInfo.State.CONNECTED
        )
        shadow.addNetwork(network, networkInfo)
        shadow.setActiveNetworkInfo(networkInfo)
        shadow.setDefaultNetworkActive(true)
        val capabilities = NetworkCapabilities()
        val shadowCaps = Shadows.shadowOf(capabilities)
        shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadow.setNetworkCapabilities(network, capabilities)
    }

    /**
     * Builds the worker through a custom [WorkerFactory]: the real constructor is
     * @AssistedInject (Context, WorkerParameters, registry, tracker), so the
     * builder's default reflection fallback (2-arg constructor) cannot create it.
     */
    private fun buildWorker(providerId: String = "gemma", modelUrl: String? = null): GemmaModelDownloadWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? {
                return if (workerClassName == GemmaModelDownloadWorker::class.java.name) {
                    GemmaModelDownloadWorker(appContext, workerParameters, registry, trackEvent)
                } else {
                    null
                }
            }
        }
        return TestListenableWorkerBuilder<GemmaModelDownloadWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(
                workDataOf(
                    GemmaModelDownloadWorker.KEY_PROVIDER_ID to providerId,
                    GemmaModelDownloadWorker.KEY_MODEL_URL to (modelUrl ?: "")
                )
            )
            .build()
    }

    @Test
    fun `success downloads the model and reports progress`() = runBlocking {
        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(fakeProvider.ready)
        // The worker forwarded every downloader progress tick to the provider
        // (which drives the foreground notification via setProgress/setForeground).
        assertEquals(listOf(0.25f, 0.5f, 1f), fakeProvider.progressSteps)
        coVerify { trackEvent(TrackEventRequest("gemma_download_worker_start")) }
        coVerify { trackEvent(TrackEventRequest("gemma_download_worker_success")) }
    }

    @Test
    fun `already-ready model short-circuits without downloading`() = runBlocking {
        fakeProvider.ready = true
        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(emptyList<Float>(), fakeProvider.progressSteps)
        coVerify(exactly = 0) { trackEvent(TrackEventRequest("gemma_download_worker_start")) }
    }

    @Test
    fun `permanent 4xx failure returns failure`() = runBlocking {
        fakeProvider.downloadResult = Result.Failure(
            ProviderError("gemma", statusCode = 403, message = "Forbidden")
        )
        val worker = buildWorker()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify { trackEvent(TrackEventRequest("gemma_download_worker_failed")) }
    }

    @Test
    fun `transient failure returns retry for workmanager backoff`() = runBlocking {
        fakeProvider.downloadResult = Result.Failure(
            ProviderError("gemma", message = "connection reset")
        )
        val worker = buildWorker()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        coVerify { trackEvent(TrackEventRequest("gemma_download_worker_failed")) }
    }

    @Test
    fun `missing provider returns failure`() = runBlocking {
        every { registry.getProvider("gemma") } returns null
        val worker = buildWorker()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `offline defers with retry`() = runBlocking {
        // Remove the network so the online gate fails.
        setOnline(false)

        val worker = buildWorker()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(emptyList<Float>(), fakeProvider.progressSteps)
    }

    @Test
    fun `compact url is forwarded to the provider`() = runBlocking {
        val captured = mutableListOf<String?>()
        // A subclass capturing the URL, rather than a MockK spy: the fake is a
        // private final class, which the inline mockmaker cannot instrument.
        val trackingProvider = object : FakeModelDownloadable() {
            override suspend fun downloadModel(url: String?, onProgress: (Float) -> Unit): Result<Unit> {
                captured.add(url)
                ready = true
                return Result.Success(Unit)
            }
        }
        every { registry.getProvider("gemma") } returns trackingProvider
        coEvery { trackEvent(any<TrackEventRequest>()) } returns Result.Success(Unit)

        val worker = buildWorker(modelUrl = "https://example.com/compact.task")
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("https://example.com/compact.task"), captured)
    }
}
