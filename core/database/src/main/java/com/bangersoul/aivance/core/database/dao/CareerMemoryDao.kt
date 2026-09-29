package com.bangersoul.aivance.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bangersoul.aivance.core.database.model.CareerMemoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for persisted Career Memory entries (Room v26).
 *
 * Upserts on [CareerMemoryEntity.memoryId] so confirming or updating an entry rewrites the same
 * row, letting the [com.bangersoul.aivance.core.domain.memory.CareerMemoryEngine] hydrate its
 * StateFlow from disk and write through on every mutation.
 */
@Dao
interface CareerMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CareerMemoryEntity)

    @Query("SELECT * FROM career_memory_entries")
    suspend fun getAll(): List<CareerMemoryEntity>

    @Query("SELECT * FROM career_memory_entries")
    fun observeAll(): Flow<List<CareerMemoryEntity>>

    @Query("SELECT * FROM career_memory_entries WHERE memoryId = :memoryId")
    suspend fun getById(memoryId: String): CareerMemoryEntity?

    @Query("SELECT * FROM career_memory_entries WHERE type = :type")
    suspend fun getByType(type: String): List<CareerMemoryEntity>

    @Query("DELETE FROM career_memory_entries WHERE memoryId = :memoryId")
    suspend fun delete(memoryId: String)
}
