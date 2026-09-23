package com.bangersoul.aivance.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent Career Knowledge Graph edge (Room v26).
 *
 * The [id] is deterministic (`source|relation|target`) so a re-projected graph upserts the same
 * relationship rather than accumulating duplicate edges.
 */
@Entity(
    tableName = "graph_edges",
    indices = [
        Index(value = ["sourceId", "relationType"], name = "idx_graph_edges_source"),
        Index(value = ["targetId", "relationType"], name = "idx_graph_edges_target")
    ]
)
data class GraphEdgeEntity(
    @PrimaryKey
    val id: String,
    val sourceId: String,
    val targetId: String,
    val relationType: String,
    val weight: Float = 1.0f,
    val propertiesJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
