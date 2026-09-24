package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.graph.CareerEdgeType
import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.graph.CareerGraphEdge
import com.bangersoul.aivance.core.common.graph.CareerGraphNode
import com.bangersoul.aivance.core.common.graph.CareerNodeType
import com.bangersoul.aivance.core.database.dao.GraphDao
import com.bangersoul.aivance.core.database.model.GraphEdgeEntity
import com.bangersoul.aivance.core.database.model.GraphNodeEntity
import com.bangersoul.aivance.core.domain.repository.CareerGraphRepository
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [CareerGraphRepository] persisting the Career Knowledge Graph into the v26
 * `graph_nodes` / `graph_edges` tables.
 *
 * - Node/edge identities are the engine's deterministic IDs, so [persist] upserts the same rows
 *   rather than duplicating them across repeated event-driven projections.
 * - [persist] writes the ENTITY projection slice atomically inside [GraphDao.replaceEntityProjection]
 *   (single transaction), replacing every non-`CAREER_EVENT` node and all edges while leaving the
 *   replay-owned `CAREER_EVENT` provenance slice intact (M05).
 * - [replaceEventProjection] atomically rebuilds ONLY the `CAREER_EVENT` provenance slice (M04-B
 *   replay), leaving the entity projection untouched.
 * - Unknown persisted type strings are skipped defensively on hydration so a forward-compatible
 *   row never crashes an older reader.
 */
@Singleton
class CareerGraphRepositoryImpl @Inject constructor(
    private val graphDao: GraphDao
) : CareerGraphRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    override suspend fun persist(graph: CareerGraph) {
        val nodeEntities = graph.nodes.values.map { it.toEntity() }
        val edgeEntities = graph.edges.map { edge ->
            GraphEdgeEntity(
                // Deterministic identity so a re-projected relationship upserts the same row
                // rather than accumulating duplicates (the model's default id is a random UUID).
                id = "${edge.sourceId}|${edge.relationType.name}|${edge.targetId}",
                sourceId = edge.sourceId,
                targetId = edge.targetId,
                relationType = edge.relationType.name,
                weight = edge.weight,
                propertiesJson = json.encodeToString(mapSerializer, edge.properties),
                createdAt = edge.createdAt
            )
        }
        graphDao.replaceEntityProjection(nodeEntities, edgeEntities)
    }

    override suspend fun loadGraph(userId: String): CareerGraph {
        val nodes = graphDao.getAllNodes().mapNotNull { entity ->
            val type = runCatching { CareerNodeType.valueOf(entity.type) }.getOrNull() ?: return@mapNotNull null
            entity.id to CareerGraphNode(
                id = entity.id,
                type = type,
                label = entity.label,
                properties = decodeMap(entity.propertiesJson),
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }.toMap()

        val edges = graphDao.getAllEdges().mapNotNull { entity ->
            val relation = runCatching { CareerEdgeType.valueOf(entity.relationType) }.getOrNull()
                ?: return@mapNotNull null
            CareerGraphEdge(
                id = entity.id,
                sourceId = entity.sourceId,
                targetId = entity.targetId,
                relationType = relation,
                weight = entity.weight,
                properties = decodeMap(entity.propertiesJson),
                createdAt = entity.createdAt
            )
        }

        return CareerGraph(userId = userId, nodes = nodes, edges = edges)
    }

    override suspend fun replaceEventProjection(eventNodes: List<CareerGraphNode>) {
        graphDao.replaceNodesOfType(
            type = CareerNodeType.CAREER_EVENT.name,
            nodes = eventNodes.map { it.toEntity() }
        )
    }

    private fun CareerGraphNode.toEntity(): GraphNodeEntity = GraphNodeEntity(
        id = id,
        type = type.name,
        label = label,
        propertiesJson = json.encodeToString(mapSerializer, properties),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun decodeMap(raw: String): Map<String, String> =
        runCatching { json.decodeFromString(mapSerializer, raw) }.getOrDefault(emptyMap())
}
