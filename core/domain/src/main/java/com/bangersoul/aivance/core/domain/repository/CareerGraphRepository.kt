package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.graph.CareerGraph

/**
 * Durable persistence for the Career Knowledge Graph projection.
 *
 * Implementations back the graph with the `graph_nodes` / `graph_edges` tables (Room v26) using
 * upsert semantics on the engine's deterministic node/edge identities, so repeated event-driven
 * projections replace rather than duplicate rows. The whole projection is written atomically.
 */
interface CareerGraphRepository {
    /**
     * Atomically persists a freshly projected [graph]. The write is transactional: a partial
     * failure rolls back so readers never observe a half-applied projection.
     */
    suspend fun persist(graph: CareerGraph)

    /**
     * Hydrates the persisted graph for [userId] from disk. Returns an empty graph if nothing
     * has been persisted yet.
     */
    suspend fun loadGraph(userId: String): CareerGraph
}
