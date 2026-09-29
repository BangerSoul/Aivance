package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.graph.CareerGraphNode

/**
 * Durable persistence for the Career Knowledge Graph projection.
 *
 * The graph is split into two disjoint, independently-owned projection slices (M05):
 *
 *  - **Entity projection** — every node type EXCEPT `CAREER_EVENT`, plus all edges. Owned by the
 *    live [com.bangersoul.aivance.core.domain.engine.CareerStateEngine], projected from
 *    authoritative Room entities via [persist].
 *  - **Event-provenance projection** — only the `CAREER_EVENT` nodes. Owned by the M04-B
 *    [com.bangersoul.aivance.core.domain.replay.CareerEventReplayEngine], rebuilt from the durable
 *    event log via [replaceEventProjection].
 *
 * Because the two slices never overlap, a live entity re-projection can no longer erase a
 * replay-rebuilt provenance slice, and vice versa. Implementations back the graph with the
 * `graph_nodes` / `graph_edges` tables (Room v26) using upsert semantics on the engine's
 * deterministic node/edge identities; each slice is written atomically.
 */
interface CareerGraphRepository {
    /**
     * Atomically persists a freshly projected [graph] as the ENTITY projection slice: it replaces
     * every non-`CAREER_EVENT` node and all edges, leaving the replay-owned `CAREER_EVENT`
     * provenance slice untouched (M05). The write is transactional: a partial failure rolls back
     * so readers never observe a half-applied projection.
     */
    suspend fun persist(graph: CareerGraph)

    /**
     * Hydrates the persisted graph for [userId] from disk. Returns an empty graph if nothing
     * has been persisted yet.
     */
    suspend fun loadGraph(userId: String): CareerGraph

    /**
     * Atomically replaces ONLY the `CAREER_EVENT` provenance slice of the graph with [eventNodes],
     * leaving every other node type (and all edges) untouched. This is the write target of the
     * M04-B event replay engine: it re-projects the durable event log into the graph's event layer
     * without disturbing the relational-entity projection written by [persist].
     *
     * The whole slice is rewritten in a single transaction (clear-then-upsert on deterministic
     * `event_<eventId>` ids), so a failed rebuild never leaves a half-projected event layer and
     * repeated replays converge to the same rows.
     */
    suspend fun replaceEventProjection(eventNodes: List<CareerGraphNode>)
}
