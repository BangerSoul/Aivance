package com.bangersoul.aivance.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent Career Memory entry (Room v26).
 *
 * Backs [com.bangersoul.aivance.core.domain.memory.CareerMemoryEngine] so user-confirmed facts and
 * AI inferences survive process death. [isUserConfirmed] strictly separates permanent user facts
 * from provisional AI inferences — the auditable invariant of the memory engine.
 */
@Entity(
    tableName = "career_memory_entries",
    indices = [Index(value = ["type", "isUserConfirmed"], name = "idx_career_memory_type")]
)
data class CareerMemoryEntity(
    @PrimaryKey
    val memoryId: String,
    val type: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val confidence: Float,
    val sourceEventIdsJson: String,
    val evidenceRefsJson: String,
    val isUserConfirmed: Boolean = false,
    val expirationTimestamp: Long? = null
)
