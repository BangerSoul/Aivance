package com.bangersoul.aivance.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bangersoul.aivance.core.database.model.CareerEventLogEntity

/**
 * DAO for the durable Career Event Log (Room v26).
 *
 * Inserts IGNORE on conflict: the primary key is the event's own id, so redelivering the same
 * event is a no-op rather than a duplicate row. This is durable logging only — no replay path.
 */
@Dao
interface CareerEventLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun append(event: CareerEventLogEntity): Long

    @Query("SELECT * FROM career_event_log ORDER BY timestamp ASC, eventId ASC")
    suspend fun getAll(): List<CareerEventLogEntity>

    @Query("SELECT * FROM career_event_log WHERE eventType = :type ORDER BY timestamp ASC")
    suspend fun getByType(type: String): List<CareerEventLogEntity>

    @Query("SELECT COUNT(*) FROM career_event_log")
    suspend fun count(): Int

    @Query("DELETE FROM career_event_log WHERE timestamp < :beforeTimestamp")
    suspend fun prune(beforeTimestamp: Long)
}
