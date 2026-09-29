package com.bangersoul.aivance.core.domain.usecase.job

import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.ProfileState
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.model.AiMessage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScoreJobFitUseCaseTest {

    private val providerManager: ProviderManager = mockk()

    private lateinit var useCase: ScoreJobFitUseCase

    private val profile = ProfileState(
        targetRole = "Android Engineer",
        skills = listOf("Kotlin", "Compose", "Hilt"),
        workPreference = "REMOTE"
    )

    private val jobs = listOf(
        job(id = "1", title = "Senior Android Engineer", company = "Acme"),
        job(id = "2", title = "iOS Engineer", company = "Globex")
    )

    @Before
    fun setUp() {
        useCase = ScoreJobFitUseCase(providerManager)
    }

    private open class FakeAIProvider(var response: String = "") : AIProvider(
        metadata = ProviderMetadata(
            id = "fake",
            name = "Fake",
            type = ProviderType.AI,
            version = "1.0.0",
            description = "test provider",
            author = "test"
        ),
        capabilities = setOf(ProviderCapability.AI.Chat)
    ) {
        var calls = 0
        var lastPrompt: String? = null

        override suspend fun generateText(prompt: String): Result<String> {
            calls++
            lastPrompt = prompt
            return Result.Success(response)
        }

        override suspend fun chat(messages: List<AiMessage>): Result<String> = Result.Success(response)
        override fun streamText(prompt: String): Flow<String> = flowOf("")
        override fun streamChat(messages: List<AiMessage>): Flow<Result<String>> = flowOf(Result.Success(""))
        override suspend fun listModels(): Result<List<String>> = Result.Success(emptyList())
        override suspend fun onInitialize() {}
        override suspend fun onStart() {}
        override suspend fun onStop() {}
        override suspend fun onDispose() {}
    }

    private fun job(id: String, title: String, company: String) = JobListing(
        id = id,
        title = title,
        company = company,
        description = "Build and ship mobile features with Kotlin and Jetpack Compose.",
        url = "https://example.com/$id",
        sourceProvider = "TEST"
    )

    @Test
    fun `scores jobs from the AI provider response`() = runTest {
        val provider = FakeAIProvider(response = """{"1": 88, "2": 45}""")
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns provider

        val scores = useCase(ScoreJobFitRequest(jobs = jobs, profile = profile))

        assertEquals(88, scores["1"])
        assertEquals(45, scores["2"])
        assertEquals(1, provider.calls)
        assertTrue(provider.lastPrompt!!.contains("Android Engineer"))
        assertTrue(provider.lastPrompt!!.contains("Kotlin"))
    }

    @Test
    fun `tolerates markdown-fenced responses`() = runTest {
        val provider = FakeAIProvider(response = "```json\n{\"1\": 90}\n```")
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns provider

        val scores = useCase(ScoreJobFitRequest(jobs = jobs, profile = profile))

        assertEquals(90, scores["1"])
    }

    @Test
    fun `clamps scores into the zero to hundred range`() = runTest {
        val provider = FakeAIProvider(response = """{"1": 150, "2": -5}""")
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns provider

        val scores = useCase(ScoreJobFitRequest(jobs = jobs, profile = profile))

        assertEquals(100, scores["1"])
        assertEquals(0, scores["2"])
    }

    @Test
    fun `returns empty map when no AI provider is configured`() = runTest {
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns null

        val scores = useCase(ScoreJobFitRequest(jobs = jobs, profile = profile))

        assertTrue(scores.isEmpty())
    }

    @Test
    fun `returns empty map when the provider call fails`() = runTest {
        val failing = object : FakeAIProvider() {
            override suspend fun generateText(prompt: String): Result<String> =
                Result.Failure(com.bangersoul.aivance.core.common.result.ProviderError("fake", message = "down"))
        }
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns failing

        val scores = useCase(ScoreJobFitRequest(jobs = jobs, profile = profile))

        assertTrue(scores.isEmpty())
    }

    @Test
    fun `returns empty map on unparseable response`() = runTest {
        val provider = FakeAIProvider(response = "I am sorry, I cannot do that.")
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns provider

        val scores = useCase(ScoreJobFitRequest(jobs = jobs, profile = profile))

        assertTrue(scores.isEmpty())
    }

    @Test
    fun `returns empty map when the profile is null`() = runTest {
        val provider = FakeAIProvider(response = """{"1": 88}""")
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns provider

        val scores = useCase(ScoreJobFitRequest(jobs = jobs, profile = null))

        assertTrue(scores.isEmpty())
        assertEquals(0, provider.calls)
    }

    @Test
    fun `caches scores per profile so repeat scoring skips the provider`() = runTest {
        val provider = FakeAIProvider(response = """{"1": 88, "2": 45}""")
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns provider

        val first = useCase(ScoreJobFitRequest(jobs = jobs, profile = profile))
        val second = useCase(ScoreJobFitRequest(jobs = jobs, profile = profile))

        assertEquals(88, first["1"])
        assertEquals(first, second)
        // One AI call for both invocations — the second hit the cache.
        assertEquals(1, provider.calls)
    }

    @Test
    fun `caching is keyed by profile - a different profile re-scores`() = runTest {
        val provider = FakeAIProvider(response = """{"1": 70}""")
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns provider

        useCase(ScoreJobFitRequest(jobs = jobs, profile = profile))
        val other = profile.copy(targetRole = "Data Scientist")
        val second = useCase(ScoreJobFitRequest(jobs = jobs, profile = other))

        assertEquals(70, second["1"])
        assertEquals(2, provider.calls)
    }

    @Test
    fun `scores at most the batch limit and returns cached subset`() = runTest {
        val many = (1..15).map { job(id = it.toString(), title = "Job $it", company = "C") }
        val provider = FakeAIProvider(response = (1..15).joinToString(",") { "\"$it\": 50" }.let { "{$it}" })
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns provider

        val scores = useCase(ScoreJobFitRequest(jobs = many, profile = profile))

        // Only the first MAX_JOBS_PER_BATCH listings were sent to the provider;
        // the rest are absent (the caller falls back to rule-based scoring).
        assertEquals(ScoreJobFitUseCase.MAX_JOBS_PER_BATCH, scores.size)
    }
}
