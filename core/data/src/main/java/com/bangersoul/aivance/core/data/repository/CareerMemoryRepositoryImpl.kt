package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.database.dao.CareerMemoryDao
import com.bangersoul.aivance.core.database.model.CareerMemoryEntity
import com.bangersoul.aivance.core.domain.memory.CareerMemoryEntry
import com.bangersoul.aivance.core.domain.memory.CareerMemoryType
import com.bangersoul.aivance.core.domain.repository.CareerMemoryRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [CareerMemoryRepository] over the v26 `career_memory_entries` table.
 *
 * Expired entries are filtered on read so the engine's hydrated StateFlow matches its in-memory
 * contract. Unknown persisted type strings are skipped defensively.
 */
@Singleton
class CareerMemoryRepositoryImpl @Inject constructor(
    private val careerMemoryDao: CareerMemoryDao
) : CareerMemoryRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    override suspend fun getAll(): List<CareerMemoryEntry> =
        careerMemoryDao.getAll().mapNotNull { it.toDomainOrNull() }.filterNot { it.isExpired }

    override suspend fun upsert(entry: CareerMemoryEntry) {
        careerMemoryDao.upsert(entry.toEntity())
    }

    override suspend fun getById(memoryId: String): CareerMemoryEntry? =
        careerMemoryDao.getById(memoryId)?.toDomainOrNull()

    override suspend fun getByType(type: CareerMemoryType): List<CareerMemoryEntry> =
        careerMemoryDao.getByType(type.name).mapNotNull { it.toDomainOrNull() }.filterNot { it.isExpired }

    private fun CareerMemoryEntry.toEntity(): CareerMemoryEntity = CareerMemoryEntity(
        memoryId = memoryId,
        type = type.name,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        confidence = confidence,
        sourceEventIdsJson = json.encodeToString(listSerializer, sourceEventIds),
        evidenceRefsJson = json.encodeToString(listSerializer, evidenceReferences),
        isUserConfirmed = isUserConfirmed,
        expirationTimestamp = expirationTimestamp
    )

    private fun CareerMemoryEntity.toDomainOrNull(): CareerMemoryEntry? {
        val memoryType = runCatching { CareerMemoryType.valueOf(type) }.getOrNull() ?: return null
        return CareerMemoryEntry(
            memoryId = memoryId,
            type = memoryType,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            confidence = confidence,
            sourceEventIds = decodeList(sourceEventIdsJson),
            evidenceReferences = decodeList(evidenceRefsJson),
            isUserConfirmed = isUserConfirmed,
            expirationTimestamp = expirationTimestamp
        )
    }

    private fun decodeList(raw: String): List<String> =
        runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
}
