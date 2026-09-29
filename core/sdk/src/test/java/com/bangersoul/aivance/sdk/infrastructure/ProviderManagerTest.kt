package com.bangersoul.aivance.sdk.infrastructure

import com.bangersoul.aivance.sdk.api.ModelDownloadable
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.BaseProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderManagerTest {

    private lateinit var registry: ProviderRegistry
    private lateinit var manager: ProviderManager

    @Before
    fun setUp() {
        registry = ProviderRegistry(emptySet(), emptySet(), emptySet())
        manager = ProviderManager(registry)
    }

    @Test
    fun `initializeAll should move providers to Ready status`() = runTest {
        val provider1 = TestProvider("id1")
        val provider2 = TestProvider("id2")
        registry.register(provider1)
        registry.register(provider2)

        manager.initializeAll()

        assertEquals(ProviderStatus.Ready, provider1.status)
        assertEquals(ProviderStatus.Ready, provider2.status)
        assertTrue(provider1.initialized)
        assertTrue(provider2.initialized)
    }

    @Test
    fun `initializeAll preserves self-marked InvalidConfiguration`() = runTest {
        // Providers like Greenhouse/Lever/Apify mark themselves InvalidConfiguration
        // in onInitialize when credentials are missing; the orchestrator must not
        // clobber that with Ready (otherwise searches fire doomed 401/404 requests).
        val unconfigured = UnconfiguredTestProvider("unconfigured")
        val configured = TestProvider("configured")
        registry.register(unconfigured)
        registry.register(configured)

        manager.initializeAll()

        assertEquals(ProviderStatus.InvalidConfiguration, unconfigured.status)
        assertEquals(ProviderStatus.Ready, configured.status)
    }

    @Test
    fun `startProvider should move provider to Active status`() = runTest {
        val provider = TestProvider("id1")
        registry.register(provider)

        manager.startProvider("id1")

        assertEquals(ProviderStatus.Active, provider.status)
        assertTrue(provider.initialized)
        assertTrue(provider.started)
    }

    @Test
    fun `stopProvider should move provider back to Ready status`() = runTest {
        val provider = TestProvider("id1")
        registry.register(provider)

        manager.startProvider("id1")
        assertEquals(ProviderStatus.Active, provider.status)

        manager.stopProvider("id1")
        assertEquals(ProviderStatus.Ready, provider.status)
        assertTrue(provider.stopped)
    }

    @Test
    fun `reconfigure applies config and moves provider to Ready`() = runTest {
        val provider = ReconfigurableTestProvider("reconf")
        registry.register(provider)

        // Provider self-marks InvalidConfiguration because it has no key.
        manager.initializeAll()
        assertEquals(ProviderStatus.InvalidConfiguration, provider.status)

        val result = manager.reconfigure(
            "reconf",
            ProviderConfiguration("reconf", secrets = mapOf("apiKey" to "real-key"))
        )

        assertTrue(result is com.bangersoul.aivance.core.common.result.Result.Success<*>)
        assertEquals("real-key", provider.appliedKey)
        assertEquals(ProviderStatus.Ready, provider.status)
    }

    @Test
    fun `reconfigure for unknown provider returns failure`() = runTest {
        val result = manager.reconfigure("nope", ProviderConfiguration("nope"))
        assertTrue(result is com.bangersoul.aivance.core.common.result.Result.Failure)
    }

    @Test
    fun `validateProvider applies the candidate config before checking health`() = runTest {
        val provider = ReconfigurableTestProvider("val")
        registry.register(provider)

        val result = manager.validateProvider(
            "val",
            ProviderConfiguration("val", secrets = mapOf("apiKey" to "candidate-key"))
        )

        assertTrue(result is com.bangersoul.aivance.core.common.result.Result.Success<*>)
        assertEquals("candidate-key", provider.appliedKey)
    }

    @Test
    fun `getBestProviderFor should prioritize configured providers`() = runTest {
        // Both providers support TextAnalysis so both are candidates; only the
        // configured one must win despite both being Ready.
        val unconfigured = UnconfiguredTestProvider(
            "unconfigured-ai",
            setOf(ProviderCapability.TextAnalysis)
        )
        unconfigured.updateStatus(ProviderStatus.Ready)
        val configured = ReconfigurableTestProvider("configured-ai")
        configured.updateStatus(ProviderStatus.Ready)
        configured.applyConfiguration(
            ProviderConfiguration("configured-ai", secrets = mapOf("apiKey" to "k"))
        )
        registry.register(unconfigured)
        registry.register(configured)

        assertEquals(configured, manager.getBestProviderFor(ProviderCapability.TextAnalysis))
    }

    @Test
    fun `getBestProviderFor should prefer providers with real credentials over keyless ones`() = runTest {
        // Regression test for a bug found on-device: both the keyless Ollama and
        // the keyed Groq were Ready && isConfigured, so registry iteration order
        // picked Ollama (localhost:11434, no server) over the user's real Groq key.
        val keyless = TestProvider("keyless-ai", setOf(ProviderCapability.TextAnalysis))
        keyless.updateStatus(ProviderStatus.Ready)
        val keyed = ReconfigurableTestProvider("keyed-ai")
        keyed.updateStatus(ProviderStatus.Ready)
        keyed.applyConfiguration(
            ProviderConfiguration("keyed-ai", secrets = mapOf("apiKey" to "real-key"))
        )
        // Register the keyless one first so iteration order would favor it.
        registry.register(keyless)
        registry.register(keyed)

        assertEquals(keyed, manager.getBestProviderFor(ProviderCapability.TextAnalysis))
        assertTrue(keyless.hasCredentials.not())
        assertTrue(keyed.hasCredentials)
    }

    @Test
    fun `getOnDeviceProviderFor returns only providers with a downloaded model`() = runTest {
        val readyOnDevice = OnDeviceTestProvider("gemma-ready")
        readyOnDevice.modelDownloaded = true
        readyOnDevice.updateStatus(ProviderStatus.Ready)
        val notDownloaded = OnDeviceTestProvider("gemma-idle")
        registry.register(readyOnDevice)
        registry.register(notDownloaded)

        assertEquals(readyOnDevice, manager.getOnDeviceProviderFor(ProviderCapability.TextAnalysis))
    }

    @Test
    fun `getOnDeviceProviderFor returns null when no model is downloaded`() = runTest {
        val idle = OnDeviceTestProvider("gemma-idle")
        registry.register(idle)

        assertNull(manager.getOnDeviceProviderFor(ProviderCapability.TextAnalysis))
    }

    @Test
    fun `getOnDeviceProviderFor ignores cloud providers`() = runTest {
        val keyedCloud = ReconfigurableTestProvider("cloud-ai")
        keyedCloud.updateStatus(ProviderStatus.Ready)
        keyedCloud.applyConfiguration(
            ProviderConfiguration("cloud-ai", secrets = mapOf("apiKey" to "k"))
        )
        val readyOnDevice = OnDeviceTestProvider("gemma-ready")
        readyOnDevice.modelDownloaded = true
        readyOnDevice.updateStatus(ProviderStatus.Ready)
        registry.register(keyedCloud)
        registry.register(readyOnDevice)

        assertEquals(readyOnDevice, manager.getOnDeviceProviderFor(ProviderCapability.TextAnalysis))
    }

    @Test
    fun `getOnDeviceProviderFor prefers Active over Ready`() = runTest {
        val ready = OnDeviceTestProvider("gemma-ready")
        ready.modelDownloaded = true
        ready.updateStatus(ProviderStatus.Ready)
        val active = OnDeviceTestProvider("gemma-active")
        active.modelDownloaded = true
        active.updateStatus(ProviderStatus.Active)
        registry.register(ready)
        registry.register(active)

        assertEquals(active, manager.getOnDeviceProviderFor(ProviderCapability.TextAnalysis))
    }

    @Test
    fun `getBestProviderFor is deterministic regardless of registration order`() {
        // Two same-tier candidates (both Ready, both keyed): the winner must be
        // stable (lowest id) no matter which is registered first, so provider
        // Set/map iteration order can never change the resolved provider.
        fun resolveWith(firstId: String, secondId: String): String? {
            val reg = ProviderRegistry(emptySet(), emptySet(), emptySet())
            val mgr = ProviderManager(reg)
            val a = ReconfigurableTestProvider(firstId)
            a.updateStatus(ProviderStatus.Ready)
            val b = ReconfigurableTestProvider(secondId)
            b.updateStatus(ProviderStatus.Ready)
            // Both configured with real credentials -> identical priority tier.
            kotlinx.coroutines.runBlocking {
                a.applyConfiguration(ProviderConfiguration(firstId, secrets = mapOf("apiKey" to "k")))
                b.applyConfiguration(ProviderConfiguration(secondId, secrets = mapOf("apiKey" to "k")))
            }
            reg.register(a)
            reg.register(b)
            return mgr.getBestProviderFor(ProviderCapability.TextAnalysis)?.metadata?.id
        }

        // "alpha" < "zulu": whichever order they are registered, alpha wins.
        assertEquals("alpha", resolveWith("alpha", "zulu"))
        assertEquals("alpha", resolveWith("zulu", "alpha"))
    }

    @Test
    fun `getBestProviderFor should prioritize Active providers`() {
        val provider1 = TestProvider("id1", setOf(ProviderCapability.TextAnalysis))
        val provider2 = TestProvider("id2", setOf(ProviderCapability.TextAnalysis))
        registry.register(provider1)
        registry.register(provider2)

        // Initial state: both Uninitialized. Order not guaranteed.
        val initialBest = manager.getBestProviderFor(ProviderCapability.TextAnalysis)
        assertTrue(initialBest == provider1 || initialBest == provider2)

        // provider2 is Ready, provider1 is Uninitialized
        provider2.updateStatus(ProviderStatus.Ready)
        assertEquals(provider2, manager.getBestProviderFor(ProviderCapability.TextAnalysis))

        // provider1 is Active, provider2 is Ready
        provider1.updateStatus(ProviderStatus.Active)
        assertEquals(provider1, manager.getBestProviderFor(ProviderCapability.TextAnalysis))
    }

    private class ReconfigurableTestProvider(
        id: String,
        capabilities: Set<ProviderCapability> = setOf(ProviderCapability.TextAnalysis)
    ) : BaseProvider(
        metadata = ProviderMetadata(
            id = id,
            name = "Reconfigurable Provider $id",
            type = ProviderType.AI,
            version = "1.0.0",
            description = "Test Description",
            author = "Tester"
        ),
        capabilities = capabilities
    ) {
        var appliedKey: String? = null

        override val isConfigured: Boolean
            get() = !appliedKey.isNullOrBlank()

        override val hasCredentials: Boolean
            get() = !appliedKey.isNullOrBlank()

        override suspend fun applyConfiguration(config: ProviderConfiguration) {
            appliedKey = config.secrets["apiKey"]
            if (appliedKey.isNullOrBlank()) {
                updateStatus(ProviderStatus.InvalidConfiguration)
            }
        }

        override suspend fun onInitialize() {
            if (appliedKey.isNullOrBlank()) {
                updateStatus(ProviderStatus.InvalidConfiguration)
            } else {
                updateStatus(ProviderStatus.Ready)
            }
        }

        override suspend fun onStart() {}
        override suspend fun onStop() {}
        override suspend fun onDispose() {}
    }

    private class UnconfiguredTestProvider(
        id: String,
        capabilities: Set<ProviderCapability> = emptySet()
    ) : BaseProvider(
        metadata = ProviderMetadata(
            id = id,
            name = "Unconfigured Provider $id",
            type = ProviderType.AI,
            version = "1.0.0",
            description = "Test Description",
            author = "Tester"
        ),
        capabilities = emptySet()
    ) {
        override val isConfigured: Boolean = false

        override suspend fun onInitialize() {
            updateStatus(ProviderStatus.InvalidConfiguration)
        }

        override suspend fun onStart() {}
        override suspend fun onStop() {}
        override suspend fun onDispose() {}
    }

    /** Keyless provider whose readiness is driven by a downloaded model file. */
    private class OnDeviceTestProvider(
        id: String,
        capabilities: Set<ProviderCapability> = setOf(ProviderCapability.TextAnalysis)
    ) : BaseProvider(
        metadata = ProviderMetadata(
            id = id,
            name = "On-device $id",
            type = ProviderType.AI,
            version = "1.0.0",
            description = "Test on-device provider",
            author = "Tester"
        ),
        capabilities = capabilities
    ), ModelDownloadable {
        var modelDownloaded = false

        override val isModelReady: Boolean
            get() = modelDownloaded

        override val modelSizeBytes: Long = 1_000_000L

        override val compactModel: com.bangersoul.aivance.sdk.api.CompactModel? = null

        override suspend fun downloadModel(
            url: String?,
            onProgress: (Float) -> Unit
        ): com.bangersoul.aivance.core.common.result.Result<Unit> {
            modelDownloaded = true
            return com.bangersoul.aivance.core.common.result.Result.Success(Unit)
        }

        override suspend fun deleteModel(): com.bangersoul.aivance.core.common.result.Result<Unit> {
            modelDownloaded = false
            return com.bangersoul.aivance.core.common.result.Result.Success(Unit)
        }

        override suspend fun onInitialize() {}
        override suspend fun onStart() {}
        override suspend fun onStop() {}
        override suspend fun onDispose() {}
    }

    private class TestProvider(
        id: String,
        capabilities: Set<ProviderCapability> = emptySet()
    ) : BaseProvider(
        metadata = ProviderMetadata(
            id = id,
            name = "Test Provider $id",
            type = ProviderType.AI,
            version = "1.0.0",
            description = "Test Description",
            author = "Tester"
        ),
        capabilities = capabilities
    ) {
        var initialized = false
        var started = false
        var stopped = false
        var disposed = false

        override suspend fun onInitialize() {
            initialized = true
        }

        override suspend fun onStart() {
            started = true
        }

        override suspend fun onStop() {
            stopped = true
        }

        override suspend fun onDispose() {
            disposed = true
        }
    }
}
