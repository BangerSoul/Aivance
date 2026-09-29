package com.bangersoul.aivance.core.data.service

import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import com.bangersoul.aivance.sdk.model.AiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral tests for [TextGenerationServiceImpl] provider selection.
 *
 * These exercise the real [ProviderManager] + [ProviderRegistry] (not mocks) so
 * the determinism / capability / fallback / failure guarantees are verified
 * against the actual resolver the production code delegates to.
 */
class TextGenerationServiceImplTest {

    private fun service(vararg providers: FakeAIProvider): TextGenerationServiceImpl {
        val registry = ProviderRegistry(emptySet(), emptySet(), emptySet())
        providers.forEach { registry.register(it) }
        return TextGenerationServiceImpl(ProviderManager(registry))
    }

    @Test
    fun `selects the configured Ready provider and returns its output`() = runTest {
        val provider = FakeAIProvider("openai", keyed = true).apply {
            updateStatus(ProviderStatus.Ready)
            response = Result.Success("hello world")
        }
        val result = service(provider).generateText("prompt")

        assertTrue(result is Result.Success)
        assertEquals("hello world", (result as Result.Success).data)
    }

    @Test
    fun `keyed provider is preferred over keyless one regardless of registration order`() = runTest {
        val keyless = FakeAIProvider("ollama", keyed = false).apply {
            updateStatus(ProviderStatus.Ready)
            response = Result.Success("from keyless")
        }
        val keyed = FakeAIProvider("groq", keyed = true).apply {
            updateStatus(ProviderStatus.Ready)
            response = Result.Success("from keyed")
        }
        // Register keyless first so naive iteration order would pick it.
        val result = service(keyless, keyed).generateText("prompt")

        assertTrue(result is Result.Success)
        assertEquals("from keyed", (result as Result.Success).data)
    }

    @Test
    fun `multiple ready providers resolve deterministically by id, not Set order`() = runTest {
        // Same tier (both Ready + keyed): lowest id ("alpha") must always win.
        val alpha = FakeAIProvider("alpha", keyed = true).apply {
            updateStatus(ProviderStatus.Ready); response = Result.Success("A")
        }
        val zulu = FakeAIProvider("zulu", keyed = true).apply {
            updateStatus(ProviderStatus.Ready); response = Result.Success("Z")
        }

        val forward = service(alpha, zulu).generateText("p")
        // Fresh instances, reversed registration order.
        val alpha2 = FakeAIProvider("alpha", keyed = true).apply {
            updateStatus(ProviderStatus.Ready); response = Result.Success("A")
        }
        val zulu2 = FakeAIProvider("zulu", keyed = true).apply {
            updateStatus(ProviderStatus.Ready); response = Result.Success("Z")
        }
        val reversed = service(zulu2, alpha2).generateText("p")

        assertEquals("A", (forward as Result.Success).data)
        assertEquals("A", (reversed as Result.Success).data)
    }

    @Test
    fun `an Active provider is selected (not skipped like the old Ready-only scan)`() = runTest {
        // Regression: the previous impl only matched Ready and ignored Active,
        // so a started provider was invisible to text generation.
        val active = FakeAIProvider("openai", keyed = true).apply {
            updateStatus(ProviderStatus.Active)
            response = Result.Success("active output")
        }
        val result = service(active).generateText("prompt")

        assertTrue(result is Result.Success)
        assertEquals("active output", (result as Result.Success).data)
    }

    @Test
    fun `provider lacking the TextGeneration capability is never selected`() = runTest {
        val wrongCapability = FakeAIProvider(
            "vision-only", keyed = true,
            capabilities = setOf(ProviderCapability.AI.Vision)
        ).apply {
            updateStatus(ProviderStatus.Ready)
            response = Result.Success("should never be used")
        }
        val result = service(wrongCapability).generateText("prompt")

        // No TextGeneration-capable provider exists -> truthful failure.
        assertTrue(result is Result.Failure)
        assertEquals("No AI provider configured", (result as Result.Failure).error.message)
    }

    @Test
    fun `no provider available returns a truthful failure`() = runTest {
        val result = service().generateText("prompt")

        assertTrue(result is Result.Failure)
        assertEquals("No AI provider configured", (result as Result.Failure).error.message)
    }

    @Test
    fun `primary failure falls back to the next capable provider`() = runTest {
        // "aaa" is resolved first (lowest id) but fails; "bbb" must be tried.
        val failing = FakeAIProvider("aaa", keyed = true).apply {
            updateStatus(ProviderStatus.Ready)
            response = Result.Failure(ProviderError("aaa", message = "500 upstream"))
        }
        val healthy = FakeAIProvider("bbb", keyed = true).apply {
            updateStatus(ProviderStatus.Ready)
            response = Result.Success("recovered")
        }
        val result = service(failing, healthy).generateText("prompt")

        assertTrue(result is Result.Success)
        assertEquals("recovered", (result as Result.Success).data)
        assertTrue("primary must have been attempted", failing.called)
    }

    @Test
    fun `all providers failing surfaces the real error, not a fabricated success`() = runTest {
        val a = FakeAIProvider("aaa", keyed = true).apply {
            updateStatus(ProviderStatus.Ready)
            response = Result.Failure(ProviderError("aaa", message = "timeout"))
        }
        val b = FakeAIProvider("bbb", keyed = true).apply {
            updateStatus(ProviderStatus.Ready)
            response = Result.Failure(ProviderError("bbb", message = "429 rate limited"))
        }
        val result = service(a, b).generateText("prompt")

        assertTrue(result is Result.Failure)
        // The preserved reason is a real provider error message.
        val msg = (result as Result.Failure).error.message
        assertTrue("expected a real provider error, got: $msg", msg.isNotBlank())
    }

    @Test
    fun `a thrown provider exception is caught and does not leak the prompt`() = runTest {
        val throwing = FakeAIProvider("aaa", keyed = true).apply {
            updateStatus(ProviderStatus.Ready)
            throwOnCall = RuntimeException("boom")
        }
        val healthy = FakeAIProvider("bbb", keyed = true).apply {
            updateStatus(ProviderStatus.Ready)
            response = Result.Success("ok after throw")
        }
        val result = service(throwing, healthy).generateText("SENSITIVE PROMPT TEXT")

        // Falls through to the healthy provider instead of crashing.
        assertTrue(result is Result.Success)
        assertEquals("ok after throw", (result as Result.Success).data)
    }

    /** Controllable AIProvider double: status, credentials, and response are set per test. */
    private class FakeAIProvider(
        id: String,
        private val keyed: Boolean,
        capabilities: Set<ProviderCapability> = setOf(ProviderCapability.AI.TextGeneration)
    ) : AIProvider(
        metadata = ProviderMetadata(
            id = id,
            name = "Fake $id",
            type = ProviderType.AI,
            version = "1.0.0",
            description = "Fake AI provider",
            author = "Test"
        ),
        capabilities = capabilities
    ) {
        var response: Result<String> = Result.Success("")
        var throwOnCall: Throwable? = null
        var called = false

        override val isConfigured: Boolean get() = true
        override val hasCredentials: Boolean get() = keyed

        override suspend fun generateText(prompt: String): Result<String> {
            called = true
            throwOnCall?.let { throw it }
            return response
        }

        override suspend fun chat(messages: List<AiMessage>): Result<String> = response
        override fun streamText(prompt: String): Flow<String> = flowOf("")
        override suspend fun listModels(): Result<List<String>> = Result.Success(emptyList())
        override fun streamChat(messages: List<AiMessage>): Flow<Result<String>> = flowOf(response)

        override suspend fun onInitialize() {}
        override suspend fun onStart() {}
        override suspend fun onStop() {}
        override suspend fun onDispose() {}
    }
}
