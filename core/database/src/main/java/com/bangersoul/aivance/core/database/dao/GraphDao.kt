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

    @Query("DELETE FROM graph_nodes WHERE type = :type")
    suspend fun clearNodesByType(type: String)

    @Query("DELETE FROM graph_nodes WHERE type != :excludedType")
    suspend fun clearNodesExceptType(excludedType: String)

    @Query("SELECT * FROM graph_nodes WHERE type = :type")
    suspend fun getNodesOfType(type: String): List<GraphNodeEntity>

    /**
     * Atomically replaces the entire persisted graph with a freshly projected one.
     * Runs in a single transaction: partial failure rolls back, so readers never observe
     * a half-applied projection.
     *
     * NOTE (M05): this is the *whole-graph* rewrite and is destructive across ALL node types,
     * including the replay-owned `CAREER_EVENT` provenance slice. The live entity-projection path
     * must use [replaceEntityProjection] instead so it never erases the replay slice. This method
     * is retained for full-reset scenarios and DAO tests.
     */
    @Transaction
    suspend fun replaceGraph(nodes: List<GraphNodeEntity>, edges: List<GraphEdgeEntity>) {
        clearEdges()
        clearNodes()
        upsertNodes(nodes)
        upsertEdges(edges)
    }

    /**
     * Atomically replaces the ENTITY-owned projection (every node type except the replay-owned
     * `CAREER_EVENT` provenance slice) plus all edges, leaving the `CAREER_EVENT` nodes intact
     * (M05).
     *
     * This is the write target of the live [com.bangersoul.aivance.core.domain.engine.CareerStateEngine]
     * graph projection. Ownership is disjoint from [replaceNodesOfType]: the entity projection owns
     * all non-`CAREER_EVENT` nodes and all edges; the event-provenance projection owns only the
     * `CAREER_EVENT` nodes (and produces no edges). Because the two slices never overlap, a live
     * entity re-projection can no longer erase a replay-rebuilt provenance slice, and vice versa.
     *
     * Runs in one transaction: partial failure rolls back, so readers never observe a half-applied
     * projection. [nodes] is expected to contain no `CAREER_EVENT` rows (the engine never produces
     * them); any that slip through are upserted rather than clearing the replay slice.
     */
    @Transaction
    suspend fun replaceEntityProjection(nodes: List<GraphNodeEntity>, edges: List<GraphEdgeEntity>) {
        clearEdges()
        clearNodesExceptType(com.bangersoul.aivance.core.database.model.GraphNodeTypes.CAREER_EVENT)
        upsertNodes(nodes)
        upsertEdges(edges)
    }

    /**
     * Atomically rebuilds a single node-type slice of the graph, leaving every other type
     * untouched. Used by the M04-B event replay engine to deterministically re-project the
     * event-provenance layer (`CAREER_EVENT` nodes) from the durable log without wiping the
     * relational entity projection written by [replaceGraph].
     *
     * Runs in one transaction: partial failure rolls back, so a malformed rebuild never leaves
     * a half-projected slice. Node identities are the caller's deterministic ids, so repeated
     * rebuilds converge to the same rows (idempotent).
     */
    @Transaction
    suspend fun replaceNodesOfType(type: String, nodes: List<GraphNodeEntity>) {
        clearNodesByType(type)
        upsertNodes(nodes)
    }
}
