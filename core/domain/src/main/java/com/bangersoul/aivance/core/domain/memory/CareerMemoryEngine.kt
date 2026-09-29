package com.bangersoul.aivance.core.domain.memory

import com.bangersoul.aivance.core.domain.repository.CareerMemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Career Memory Engine.
 *
 * Manages auditable career memories, longitudinal weaknesses, skill evidence,
 * and user-confirmed facts. Enforces the safety invariant that AI inferences are never
 * silently converted into permanent facts without explicit user validation.
 *
 * Durable-backed: entries are hydrated from [CareerMemoryRepository] on construction and every
 * mutation writes through to disk, so confirmations and updates survive process death. An
 * in-memory [ConcurrentHashMap] cache fronts the store to preserve the original synchronous,
 * fast read contract; a [Mutex] serializes hydration against early writes.
 */
@Singleton
class CareerMemoryEngine @Inject constructor(
    private val repository: CareerMemoryRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val memoryStore = ConcurrentHashMap<String, CareerMemoryEntry>()
    private val _memoriesFlow = MutableStateFlow<List<CareerMemoryEntry>>(emptyList())
    val memories: StateFlow<List<CareerMemoryEntry>> = _memoriesFlow.asStateFlow()

    private val hydrationMutex = Mutex()

    init {
        scope.launch { hydrate() }
    }

    /**
     * Loads persisted entries into the in-memory cache. Idempotent and safe to call more than once.
     */
    suspend fun hydrate() {
        hydrationMutex.withLock {
            repository.getAll().forEach { entry -> memoryStore[entry.memoryId] = entry }
            updateState()
        }
    }

    private fun updateState() {
        val activeMemories = memoryStore.values.filter { !it.isExpired }.sortedByDescending { it.updatedAt }
        _memoriesFlow.value = activeMemories
    }

    private fun persistAsync(entry: CareerMemoryEntry) {
        scope.launch { repository.upsert(entry) }
    }

    /**
     * Records a permanent, user-confirmed fact or preference.
     */
    fun recordUserFact(
        type: CareerMemoryType,
        content: String,
        evidenceRef: String? = null
    ): CareerMemoryEntry {
        val entry = CareerMemoryEntry(
            type = type,
            content = content,
            confidence = 1.0f,
            isUserConfirmed = true,
            evidenceReferences = listOfNotNull(evidenceRef)
        )
        memoryStore[entry.memoryId] = entry
        updateState()
        persistAsync(entry)
        return entry
    }

    /**
     * Records an AI-generated observation or inference (isUserConfirmed = false).
     */
    fun recordAiInference(
        type: CareerMemoryType,
        content: String,
        confidence: Float,
        sourceEventId: String? = null,
        evidenceRef: String? = null
    ): CareerMemoryEntry {
        val entry = CareerMemoryEntry(
            type = type,
            content = content,
            confidence = confidence.coerceIn(0.0f, 1.0f),
            isUserConfirmed = false,
            sourceEventIds = listOfNotNull(sourceEventId),
            evidenceReferences = listOfNotNull(evidenceRef)
        )
        memoryStore[entry.memoryId] = entry
        updateState()
        persistAsync(entry)
        return entry
    }

    /**
     * Promotes an AI inference to a user-confirmed fact upon explicit candidate validation.
     */
    fun confirmInference(memoryId: String): Result<CareerMemoryEntry> {
        val existing = memoryStore[memoryId] ?: return Result.failure(NoSuchElementException("Memory $memoryId not found"))
        val confirmed = existing.confirmByUser()
        memoryStore[memoryId] = confirmed
        updateState()
        persistAsync(confirmed)
        return Result.success(confirmed)
    }

    fun getMemoriesByType(type: CareerMemoryType): List<CareerMemoryEntry> =
        memoryStore.values.filter { it.type == type && !it.isExpired }

    /**
     * Retrieves all recorded interview weaknesses to power longitudinal coaching.
     */
    fun getRecurringWeaknesses(): List<CareerMemoryEntry> =
        getMemoriesByType(CareerMemoryType.INTERVIEW_WEAKNESS)

    /**
     * Retrieves all empirical skill evidences for a target skill.
     */
    fun getSkillEvidences(skillName: String): List<CareerMemoryEntry> =
        getMemoriesByType(CareerMemoryType.SKILL_EVIDENCE)
            .filter { it.content.contains(skillName, ignoreCase = true) }
}
