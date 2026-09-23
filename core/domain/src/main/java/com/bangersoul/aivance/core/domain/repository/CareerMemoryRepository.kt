package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.domain.memory.CareerMemoryEntry
import com.bangersoul.aivance.core.domain.memory.CareerMemoryType

/**
 * Durable persistence for [CareerMemoryEntry] records (Room v26 `career_memory_entries`).
 *
 * Backs [com.bangersoul.aivance.core.domain.memory.CareerMemoryEngine] so user-confirmed facts and
 * AI inferences survive process death. Upserts on the entry's stable [CareerMemoryEntry.memoryId].
 */
interface CareerMemoryRepository {
    /** Loads all non-expired entries from disk. */
    suspend fun getAll(): List<CareerMemoryEntry>

    /** Inserts or updates [entry] (upsert on memoryId). */
    suspend fun upsert(entry: CareerMemoryEntry)

    /** Returns a single entry by id, or null. */
    suspend fun getById(memoryId: String): CareerMemoryEntry?

    /** Returns all entries of [type]. */
    suspend fun getByType(type: CareerMemoryType): List<CareerMemoryEntry>
}
