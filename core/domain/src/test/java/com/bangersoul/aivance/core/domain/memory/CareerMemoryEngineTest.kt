package com.bangersoul.aivance.core.domain.memory

import com.bangersoul.aivance.core.domain.repository.CareerMemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CareerMemoryEngineTest {

    /**
     * In-memory fake standing in for the Room-backed repository. Lets the tests assert both the
     * synchronous in-memory contract and the write-through-to-persistence behavior.
     */
    private class FakeCareerMemoryRepository(
        seed: List<CareerMemoryEntry> = emptyList()
    ) : CareerMemoryRepository {
        val store = LinkedHashMap<String, CareerMemoryEntry>().apply {
            seed.forEach { put(it.memoryId, it) }
        }

        override suspend fun getAll(): List<CareerMemoryEntry> = store.values.toList()
        override suspend fun upsert(entry: CareerMemoryEntry) { store[entry.memoryId] = entry }
        override suspend fun getById(memoryId: String): CareerMemoryEntry? = store[memoryId]
        override suspend fun getByType(type: CareerMemoryType): List<CareerMemoryEntry> =
            store.values.filter { it.type == type }
    }

    private lateinit var repository: FakeCareerMemoryRepository
    private lateinit var engine: CareerMemoryEngine

    @Before
    fun setUp() {
        repository = FakeCareerMemoryRepository()
        engine = CareerMemoryEngine(repository, TestScope(StandardTestDispatcher()))
    }

    @Test
    fun `recordUserFact marks entry as confirmed by user with 100 percent confidence`() {
        val fact = engine.recordUserFact(
            type = CareerMemoryType.FACT,
            content = "Holds AWS Certified Solutions Architect",
            evidenceRef = "cert_aws_123"
        )

        assertTrue(fact.isUserConfirmed)
        assertEquals(1.0f, fact.confidence, 0.001f)
        assertEquals(1, fact.evidenceReferences.size)

        val retrieved = engine.getMemoriesByType(CareerMemoryType.FACT)
        assertEquals(1, retrieved.size)
        assertEquals(fact.memoryId, retrieved.first().memoryId)
    }

    @Test
    fun `recordAiInference marks entry as unconfirmed until explicitly confirmed`() {
        val inference = engine.recordAiInference(
            type = CareerMemoryType.INTERVIEW_WEAKNESS,
            content = "Pacing was too fast during behavioral questions",
            confidence = 0.85f,
            sourceEventId = "session_round_1"
        )

        assertFalse(inference.isUserConfirmed)
        assertEquals(0.85f, inference.confidence, 0.001f)

        // Confirm inference
        val confirmResult = engine.confirmInference(inference.memoryId)
        assertTrue(confirmResult.isSuccess)

        val confirmed = confirmResult.getOrThrow()
        assertTrue(confirmed.isUserConfirmed)
        assertEquals(1.0f, confirmed.confidence, 0.001f)
    }

    @Test
    fun `getRecurringWeaknesses returns only interview weakness entries`() {
        engine.recordAiInference(CareerMemoryType.INTERVIEW_WEAKNESS, "Needs deeper explanation of Kotlin channels", 0.9f)
        engine.recordUserFact(CareerMemoryType.PREFERENCE, "Prefers 100% remote roles")

        val weaknesses = engine.getRecurringWeaknesses()
        assertEquals(1, weaknesses.size)
        assertEquals(CareerMemoryType.INTERVIEW_WEAKNESS, weaknesses.first().type)
    }

    @Test
    fun `hydrate loads persisted entries into the in-memory cache`() = runTest {
        val seeded = CareerMemoryEntry(
            type = CareerMemoryType.FACT,
            content = "Ships Android apps",
            isUserConfirmed = true
        )
        val seededRepo = FakeCareerMemoryRepository(listOf(seeded))
        val hydratedEngine = CareerMemoryEngine(seededRepo, TestScope(StandardTestDispatcher(testScheduler)))

        hydratedEngine.hydrate()

        val facts = hydratedEngine.getMemoriesByType(CareerMemoryType.FACT)
        assertEquals(1, facts.size)
        assertEquals(seeded.memoryId, facts.first().memoryId)
    }

    @Test
    fun `mutations write through to the repository`() = runTest {
        val writeThroughRepo = FakeCareerMemoryRepository()
        val writeEngine = CareerMemoryEngine(writeThroughRepo, TestScope(StandardTestDispatcher(testScheduler)))

        val fact = writeEngine.recordUserFact(CareerMemoryType.FACT, "Confirmed skill")
        // Persistence is launched on the engine scope; advance the shared test scheduler.
        testScheduler.advanceUntilIdle()

        assertEquals(fact, writeThroughRepo.getById(fact.memoryId))
    }
}
