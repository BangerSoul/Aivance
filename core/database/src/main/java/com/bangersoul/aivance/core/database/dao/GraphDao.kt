package com.bangersoul.aivance.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.bangersoul.aivance.core.database.model.GraphEdgeEntity
import com.bangersoul.aivance.core.database.model.GraphNodeEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the persisted Career Knowledge Graph (Room v26).
 *
 * [replaceGraph] rewrites the whole projection in a single transaction so a crash never leaves a
 * half-written graph. Upserts (REPLACE on deterministic keys) keep repeated projections idempotent.
 */
@Dao
interface GraphDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodes(nodes: List<GraphNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdges(edges: List<GraphEdgeEntity>)

    @Query("SELECT * FROM graph_nodes")
    suspend fun getAllNodes(): List<GraphNodeEntity>

    @Query("SELECT * FROM graph_edges")
    suspend fun getAllEdges(): List<GraphEdgeEntity>

    @Query("SELECT * FROM graph_nodes")
    fun observeNodes(): Flow<List<GraphNodeEntity>>

    @Query("SELECT * FROM graph_nodes WHERE type = :type")
    suspend fun getNodesByType(type: String): List<GraphNodeEntity>

    @Query("DELETE FROM graph_nodes")
    suspend fun clearNodes()

    @Query("DELETE FROM graph_edges")
    suspend fun clearEdges()

    /**
     * Atomically replaces the entire persisted graph with a freshly projected one.
     * Runs in a single transaction: partial failure rolls back, so readers never observe
     * a half-applied projection.
     */
    @Transaction
    suspend fun replaceGraph(nodes: List<GraphNodeEntity>, edges: List<GraphEdgeEntity>) {
        clearEdges()
        clearNodes()
        upsertNodes(nodes)
        upsertEdges(edges)
    }
}
