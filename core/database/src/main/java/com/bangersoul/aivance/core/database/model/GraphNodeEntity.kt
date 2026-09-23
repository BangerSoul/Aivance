package com.bangersoul.aivance.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent Career Knowledge Graph node (Room v26).
 *
 * Backs the previously in-memory [com.bangersoul.aivance.core.domain.careergraph.CareerGraphEngine].
 * The [id] is a deterministic, engine-derived identity (e.g. `skill_kotlin`, `app_12`) so that
 * repeated event-driven projections upsert the same row rather than creating duplicates.
 */
@Entity(
    tableName = "graph_nodes",
    indices = [Index(value = ["type"], name = "idx_graph_nodes_type")]
)
data class GraphNodeEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val label: String,
    val propertiesJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
