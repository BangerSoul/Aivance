package com.bangersoul.aivance.feature.profile

import app.cash.turbine.test
import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.ProviderRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.GetAvailableModelsUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.GetProviderHealthUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.ProviderHealth
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.api.CompactModel
import com.bangersoul.aivance.sdk.api.JobProvider
import com.bangersoul.aivance.sdk.api.ModelDownloadable
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.ConfigField
import com.bangersoul.aivance.sdk.core.FieldType
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import com.bangersoul.aivance.sdk.model.AiMessage
import com.bangersoul.aivance.feature.profile.worker.ModelDownloadScheduler
import com.bangersoul.aivance.feature.profile.worker.ModelDownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val registry: ProviderRegistry = mockk()
    private val manager: ProviderManager = mockk()
    private val providerRepository: ProviderRepository = mockk()
    private val getProviderHealthUseCase: GetProviderHealthUseCase = mockk()
    private val getAvailableModelsUseCase: GetAvailableModelsUseCase = mockk()
    private val trackEventUseCase: TrackEventUseCase = mockk()
    private val deviceCapabilityProvider: DeviceCapabilityProvider = mockk()
    private val modelDownloadScheduler: ModelDownloadScheduler = mockk()

    /** Fake keyless on-device AI provider that implements ModelDownloadable. */
    private class FakeOnDeviceProvider(
        var modelDownloaded: Boolean = false,
        var progressSteps: MutableList<Float> = mutableListOf()
    ) : AIProvider(
        metadata = ProviderMetadata(
            id = "gemma",
            name = "Gemma (On-device)",
            type = ProviderType.AI,
            version = "1.0.0",
            description = "on-device",
            author = "test"
        ),
        capabilities = setOf(ProviderCapability.AI.Chat)
    ), ModelDownloadable {

        override val isModelReady: Boolean get() = modelDownloaded
        override val modelSizeBytes: Long = 3_000_000_000L
        override val compactModel: CompactModel = CompactModel(
            name = "FunctionGemma 270M",
            sizeBytes = 300_000_000L,
            url = "https://example.com/compact.task"
        )

        override suspend fun downloadModel(url: String?, onProgress: (Float) -> Unit): Result<Unit> {
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { p ->
                progressSteps.add(p)
                onProgress(p)
            }
            modelDownloaded = true
            updateStatus(ProviderStatus.Ready)
            return Result.Success(Unit)
        }

        override suspend fun deleteModel(): Result<Unit> {
            modelDownloaded = false
            updateStatus(ProviderStatus.InvalidConfiguration)
            return Result.Success(Unit)
        }

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

    /** Fake keyed job provider mirroring Adzuna's metadata config fields. */
    private class FakeAdzunaProvider : JobProvider(
        metadata = ProviderMetadata(
            id = "adzuna",
            name = "Adzuna",
            type = ProviderType.JOB,
            version = "1.0.0",
            description = "adzuna",
            author = "test",
            configFields = listOf(
                ConfigField(key = "appId", label = "Adzuna App ID"),
                ConfigField(
                    key = "appKey",
                    label = "Adzuna API Key",
                    isSensitive = true,
                    fieldType = FieldType.PASSWORD
                )
            )
        ),
        capabilities = setOf(ProviderCapability.JobSearch)
    ) {
        override suspend fun searchJobs(
            filter: JobSearchFilter,
            sortOrder: JobSortOrder,
            page: Int
        ): Result<List<JobListing>> = Result.Success(emptyList())

        override suspend fun getJobDetails(jobId: String): Result<JobListing> = Result.Success(
            JobListing(
                id = "1", title = "x", company = "c",
                description = "d", url = "https://example.com/x", sourceProvider = "adzuna"
            )
        )

        override suspend fun onInitialize() {}
        override suspend fun onStart() {}
        override suspend fun onStop() {}
        override suspend fun onDispose() {}
    }

    private val fakeProvider = FakeOnDeviceProvider()

    private fun createViewModel() = ProviderManagementViewModel(
        providerRegistry = registry,
        providerManager = manager,
        providerRepository = providerRepository,
        getProviderHealthUseCase = getProviderHealthUseCase,
        getAvailableModelsUseCase = getAvailableModelsUseCase,
        trackEventUseCase = trackEventUseCase,
        deviceCapabilityProvider = deviceCapabilityProvider,
        modelDownloadScheduler = modelDownloadScheduler
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { trackEventUseCase(any<TrackEventRequest>()) } returns Result.Success(Unit)
        coEvery { providerRepository.getProviderConfig(any()) } returns null
        coEvery { providerRepository.getProviderConfigs() } returns flowOf(emptyList())
        coEvery { providerRepository.saveProviderConfig(any()) } returns Result.Success(Unit)
        coEvery { getProviderHealthUseCase(any()) } returns Result.Success(
            ProviderHealth("gemma", ProviderStatus.Ready, true)
        )
        coEvery { getAvailableModelsUseCase(any()) } returns Result.Success(emptyList())
        every { registry.getAllProviders() } returns listOf(fakeProvider)
        every { registry.getProvider("gemma") } returns fakeProvider
        every { manager.providerStatuses } returns MutableStateFlow(mapOf("gemma" to ProviderStatus.Uninitialized))
        coEvery { deviceCapabilityProvider.currentCapability() } returns DeviceCapability(
            freeStorageBytes = 20L * 1024 * 1024 * 1024, // 20 GiB free
            totalRamBytes = 8L * 1024 * 1024 * 1024 // 8 GiB RAM
        )
        // The worker is the downloader now — the VM just enqueues and observes.
        every { modelDownloadScheduler.observe() } returns MutableStateFlow(ModelDownloadStatus.Idle)
        every { modelDownloadScheduler.cancel() } returns Unit
        every { modelDownloadScheduler.enqueue(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `keyed job provider credentials route to settings and secrets on save`() = runTest {
        val adzuna = FakeAdzunaProvider()
        every { registry.getAllProviders() } returns listOf(adzuna, fakeProvider)
        every { registry.getProvider("adzuna") } returns adzuna
        every { manager.providerStatuses } returns MutableStateFlow(
            mapOf(
                "adzuna" to ProviderStatus.InvalidConfiguration,
                "gemma" to ProviderStatus.Uninitialized
            )
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ProviderManagementUiEvent.SetCredential("adzuna", "appId", "my-app-id"))
        vm.onEvent(ProviderManagementUiEvent.SetCredential("adzuna", "appKey", "sk-adzuna"))
        vm.onEvent(ProviderManagementUiEvent.SaveProvider("adzuna"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Metadata-driven routing (T-03): non-sensitive appId -> plaintext
        // settings, sensitive appKey -> encrypted secrets. No hardcoded
        // "appId:appKey" single-field parsing.
        val slot = slot<ProviderConfiguration>()
        coVerify { providerRepository.saveProviderConfig(capture(slot)) }
        val saved = slot.captured
        assertEquals("adzuna", saved.providerId)
        assertEquals("my-app-id", saved.settings["appId"])
        assertEquals("sk-adzuna", saved.secrets["appKey"])
        assertFalse("apiKey" in saved.secrets)
    }

    @Test
    fun `on-device provider is flagged and initially not downloaded`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value as ProviderManagementUiState.Success
        val gemma = state.providers.first { it.id == "gemma" }
        assertTrue(gemma.isOnDevice)
        assertFalse(gemma.modelDownloaded)
        assertFalse(gemma.apiKeyConfigured)
    }

    @Test
    fun `download tap on capable device shows confirmation dialog with exact size`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ProviderManagementUiEvent.DownloadModel("gemma"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value as ProviderManagementUiState.Success
        val dialog = state.modelDownloadDialog
        assertNotNull(dialog)
        assertEquals("gemma", dialog!!.providerId)
        assertEquals(3_000_000_000L, dialog.modelSizeBytes)
        assertFalse(dialog.storageBlocked)
        assertFalse(dialog.ramWarning)
        assertFalse(dialog.offersCompact)
        // Nothing downloaded yet — only the dialog is shown.
        assertFalse(state.providers.first { it.id == "gemma" }.modelDownloaded)
        coVerify { trackEventUseCase(TrackEventRequest("provider_download_model_gemma")) }
    }

    @Test
    fun `confirming the dialog enqueues the worker and shows downloading state`() = runTest {
        // The worker runs the download; the VM observes its progress flow.
        val statusFlow = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)
        every { modelDownloadScheduler.observe() } returns statusFlow

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ProviderManagementUiEvent.DownloadModel("gemma"))
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ProviderManagementUiEvent.ConfirmModelDownload("gemma", useCompactModel = false))
        testDispatcher.scheduler.advanceUntilIdle()

        // The worker was enqueued with the primary (null) URL; the dialog closed
        // and the UI shows a downloading state immediately.
        io.mockk.verify { modelDownloadScheduler.enqueue("gemma", null) }
        coVerify { trackEventUseCase(TrackEventRequest("provider_confirm_download_gemma_primary")) }
        var state = vm.uiState.value as ProviderManagementUiState.Success
        assertNull(state.modelDownloadDialog)
        assertEquals("gemma", state.downloadingProviderId)
        assertEquals(0f, state.modelDownloadProgress)

        // Progress streams in from the worker while the app is backgrounded.
        statusFlow.value = ModelDownloadStatus.Running(0.5f)
        testDispatcher.scheduler.advanceUntilIdle()
        state = vm.uiState.value as ProviderManagementUiState.Success
        assertEquals(0.5f, state.modelDownloadProgress)
        assertEquals("gemma", state.downloadingProviderId)

        // Completion refreshes the provider to ready and clears the spinner.
        statusFlow.value = ModelDownloadStatus.Succeeded("gemma")
        testDispatcher.scheduler.advanceUntilIdle()
        state = vm.uiState.value as ProviderManagementUiState.Success
        assertNull(state.downloadingProviderId)
        assertNull(state.modelDownloadProgress)
    }

    @Test
    fun `worker failure surfaces a snackbar and clears the downloading state`() = runTest {
        val statusFlow = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)
        every { modelDownloadScheduler.observe() } returns statusFlow

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ProviderManagementUiEvent.DownloadModel("gemma"))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ProviderManagementUiEvent.ConfirmModelDownload("gemma", useCompactModel = false))
        testDispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            // A real failure is observed as Running (progress) then Failed;
            // terminal states only surface as snackbars when this session saw
            // the work running.
            statusFlow.value = ModelDownloadStatus.Running(0.5f)
            testDispatcher.scheduler.advanceUntilIdle()
            statusFlow.value = ModelDownloadStatus.Failed("gemma")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.uiState.value as ProviderManagementUiState.Success
            assertNull(state.downloadingProviderId)
            assertNull(state.modelDownloadProgress)

            val effect = awaitItem()
            assertTrue(effect is ProviderManagementUiEffect.ShowSnackbar)
            assertTrue((effect as ProviderManagementUiEffect.ShowSnackbar).message.contains("failed"))
        }
    }

    @Test
    fun `low RAM device is warned and offered the compact model`() = runTest {
        coEvery { deviceCapabilityProvider.currentCapability() } returns DeviceCapability(
            freeStorageBytes = 20L * 1024 * 1024 * 1024, // plenty of storage
            totalRamBytes = 3L * 1024 * 1024 * 1024 // 3 GiB — below 4 GiB recommendation
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ProviderManagementUiEvent.DownloadModel("gemma"))
        testDispatcher.scheduler.advanceUntilIdle()

        val dialog = (vm.uiState.value as ProviderManagementUiState.Success).modelDownloadDialog
        assertNotNull(dialog)
        assertFalse(dialog!!.storageBlocked)
        assertTrue(dialog.ramWarning)
        assertTrue(dialog.offersCompact)
        assertEquals("FunctionGemma 270M", dialog.compactName)
    }

    @Test
    fun `low storage blocks the primary model and offers only the compact`() = runTest {
        coEvery { deviceCapabilityProvider.currentCapability() } returns DeviceCapability(
            freeStorageBytes = 1L * 1024 * 1024 * 1024, // 1 GiB — below the 2 GiB requirement
            totalRamBytes = 8L * 1024 * 1024 * 1024
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ProviderManagementUiEvent.DownloadModel("gemma"))
        testDispatcher.scheduler.advanceUntilIdle()

        val dialog = (vm.uiState.value as ProviderManagementUiState.Success).modelDownloadDialog
        assertNotNull(dialog)
        assertTrue(dialog!!.storageBlocked)
        assertTrue(dialog.offersCompact)
    }

    @Test
    fun `storage above 2 GiB floor but below model size still blocks the primary`() = runTest {
        // 3,400,000,000 bytes free: above the 2 GiB requirement but below the
        // 3,450,000,000 bytes (3 GiB model + 15% headroom) needed for the primary
        // file — the compact must be offered, and the copy must not claim a flat
        // "2 GB" requirement.
        coEvery { deviceCapabilityProvider.currentCapability() } returns DeviceCapability(
            freeStorageBytes = 3_400_000_000L,
            totalRamBytes = 8L * 1024 * 1024 * 1024
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ProviderManagementUiEvent.DownloadModel("gemma"))
        testDispatcher.scheduler.advanceUntilIdle()

        val dialog = (vm.uiState.value as ProviderManagementUiState.Success).modelDownloadDialog
        assertNotNull(dialog)
        assertTrue(dialog!!.storageBlocked)
        assertTrue(dialog.offersCompact)
    }

    @Test
    fun `no storage for either model hard-blocks with a snackbar`() = runTest {
        coEvery { deviceCapabilityProvider.currentCapability() } returns DeviceCapability(
            freeStorageBytes = 50L * 1024 * 1024, // 50 MiB — nothing fits
            totalRamBytes = 8L * 1024 * 1024 * 1024
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(ProviderManagementUiEvent.DownloadModel("gemma"))
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.uiState.value as ProviderManagementUiState.Success
            assertNull(state.modelDownloadDialog)
            assertFalse(state.providers.first { it.id == "gemma" }.modelDownloaded)

            val effect = awaitItem()
            assertTrue(effect is ProviderManagementUiEffect.ShowSnackbar)
            assertTrue((effect as ProviderManagementUiEffect.ShowSnackbar).message.contains("Not enough free storage"))
            coVerify { trackEventUseCase(TrackEventRequest("provider_download_model_gemma")) }
        }
    }

    @Test
    fun `dismissing the dialog does not start a download`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ProviderManagementUiEvent.DownloadModel("gemma"))
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ProviderManagementUiEvent.DismissModelDownloadDialog)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value as ProviderManagementUiState.Success
        assertNull(state.modelDownloadDialog)
        assertFalse(state.providers.first { it.id == "gemma" }.modelDownloaded)
    }

    @Test
    fun `deleteModel cancels the worker and flips the provider back to not downloaded`() = runTest {
        val statusFlow = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)
        every { modelDownloadScheduler.observe() } returns statusFlow

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        // Simulate the worker completing so the model is present.
        fakeProvider.modelDownloaded = true
        statusFlow.value = ModelDownloadStatus.Succeeded("gemma")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue((vm.uiState.value as ProviderManagementUiState.Success)
            .providers.first { it.id == "gemma" }.modelDownloaded)

        vm.onEvent(ProviderManagementUiEvent.DeleteModel("gemma"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { modelDownloadScheduler.cancel() }
        val state = vm.uiState.value as ProviderManagementUiState.Success
        assertFalse(state.providers.first { it.id == "gemma" }.modelDownloaded)
        assertFalse(state.providers.first { it.id == "gemma" }.apiKeyConfigured)
    }
}
