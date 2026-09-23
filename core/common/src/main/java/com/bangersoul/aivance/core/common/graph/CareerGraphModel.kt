package com.bangersoul.aivance.core.common.graph

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Canonical Career Knowledge Graph Nodes standardizing the entity layer of AiVance.
 *
 * Provides a unified, type-safe knowledge graph representation across existing Room entities
 * and the language-agnostic career-schema standard.
 */
@Serializable
enum class CareerNodeType {
    PROFILE,
    GOAL,
    SKILL,
    EXPERIENCE,
    RESUME,
    RESUME_VERSION,
    JOB,
    COMPANY,
    RECRUITER,
    APPLICATION,
    INTERVIEW,
    INTERVIEW_SESSION,
    COVER_LETTER,
    CAREER_EVENT,
    CAREER_MEMORY
}

/**
 * Strongly typed relationship edges connecting nodes in the Career Knowledge Graph.
 */
@Serializable
enum class CareerEdgeType {
    // User & Identity
    HAS_SKILL,
    HAS_GOAL,
    HAS_RESUME,
    
    // Resume & Experience
    CONTAINS_SKILL,
    DEMONSTRATES_EXPERIENCE,
    HAS_VERSION,
    
    // Jobs & Opportunities
    REQUIRES_SKILL,
    PREFERS_SKILL,
    BELONGS_TO,
    CONTACTED_BY,
    
    // Application Lifecycle
    APPLIED_TO,
    FOR_JOB,
    USES_RESUME,
    GENERATED_COVER_LETTER,
    HAS_INTERVIEW,
    
    // Interviews & Evaluation
    EVALUATED_BY,
    IDENTIFIED_WEAKNESS,
    IDENTIFIED_STRENGTH,
    
    // Career Memory & Evidence
    SUPPORTED_BY,
    RESOLVES_GAP,
    TRIGGERED_BY
}

/**
 * A single node in the canonical Career Knowledge Graph.
 */
@Serializable
data class CareerGraphNode(
    val id: String,
    val type: CareerNodeType,
    val label: String,
    val properties: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(
            type: CareerNodeType,
            label: String,
            id: String = "${type.name.lowercase()}_${UUID.randomUUID()}",
            properties: Map<String, String> = emptyMap()
        ): CareerGraphNode = CareerGraphNode(
            id = id,
            type = type,
            label = label,
            properties = properties
        )
    }
}

/**
 * A directed, weighted relationship edge between two nodes in the Career Knowledge Graph.
 */
@Serializable
data class CareerGraphEdge(
    val id: String = "edge_${UUID.randomUUID()}",
    val sourceId: String,
    val targetId: String,
    val relationType: CareerEdgeType,
    val weight: Float = 1.0f,
    val properties: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Immutable snapshot of the entire Career Knowledge Graph.
 */
@Serializable
data class CareerGraph(
    val userId: String,
    val nodes: Map<String, CareerGraphNode> = emptyMap(),
    val edges: List<CareerGraphEdge> = emptyList(),
    val version: Long = 1L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getNode(id: String): CareerGraphNode? = nodes[id]

    fun getEdgesFrom(sourceId: String): List<CareerGraphEdge> =
        edges.filter { it.sourceId == sourceId }

    fun getEdgesTo(targetId: String): List<CareerGraphEdge> =
        edges.filter { it.targetId == targetId }

    fun getOutgoingNeighbors(nodeId: String, relationType: CareerEdgeType? = null): List<CareerGraphNode> {
        val matchingEdges = edges.filter { it.sourceId == nodeId && (relationType == null || it.relationType == relationType) }
        return matchingEdges.mapNotNull { nodes[it.targetId] }
    }

    fun getIncomingNeighbors(nodeId: String, relationType: CareerEdgeType? = null): List<CareerGraphNode> {
        val matchingEdges = edges.filter { it.targetId == nodeId && (relationType == null || it.relationType == relationType) }
        return matchingEdges.mapNotNull { nodes[it.sourceId] }
    }

    fun getNodesByType(type: CareerNodeType): List<CareerGraphNode> =
        nodes.values.filter { it.type == type }
}
