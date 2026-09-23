package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.graph.CareerEdgeType
import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.graph.CareerGraphEdge
import com.bangersoul.aivance.core.common.graph.CareerGraphNode
import com.bangersoul.aivance.core.common.graph.CareerNodeType
import com.bangersoul.aivance.core.database.dao.GraphDao
import com.bangersoul.aivance.core.database.model.GraphEdgeEntity
import com.bangersoul.aivance.core.database.model.GraphNodeEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit coverage for [CareerGraphRepositoryImpl]'s mapping and identity contracts using an
 * in-memory fake DAO — proves a projected graph persists and hydrates back identically, and that
 * re-projection upserts (deterministic edge IDs) rather than duplicating.
 */
class CareerGraphRepositoryImplTest {

    private class FakeGraphDao : GraphDao {
        val nodes = LinkedHashMap<String, GraphNodeEntity>()
        val edges = LinkedHashMap<String, GraphEdgeEntity>()

        override suspend fun upsertNodes(nodes: List<GraphNodeEntity>) {
            nodes.forEach { this.nodes[it.id] = it }
        }
        override suspend fun upsertEdges(edges: List<GraphEdgeEntity>) {
            edges.forEach { this.edges[it.id] = it }
        }
        override suspend fun getAllNodes(): List<GraphNodeEntity> = nodes.values.toList()
        override suspend fun getAllEdges(): List<GraphEdgeEntity> = edges.values.toList()
        override fun observeNodes() = throw UnsupportedOperationException()
        override suspend fun getNodesByType(type: String): List<GraphNodeEntity> =
            nodes.values.filter { it.type == type }
        override suspend fun clearNodes() = nodes.clear()
        override suspend fun clearEdges() = edges.clear()
        override suspend fun replaceGraph(nodes: List<GraphNodeEntity>, edges: List<GraphEdgeEntity>) {
            clearEdges(); clearNodes(); upsertNodes(nodes); upsertEdges(edges)
        }
    }

    private val dao = FakeGraphDao()
    private val repository = CareerGraphRepositoryImpl(dao)

    private fun sampleGraph() = CareerGraph(
        userId = "u1",
        nodes = mapOf(
            "user_u1" to CareerGraphNode("user_u1", CareerNodeType.PROFILE, "Alice", mapOf("targetRole" to "Eng")),
            "skill_kotlin" to CareerGraphNode("skill_kotlin", CareerNodeType.SKILL, "Kotlin")
        ),
        edges = listOf(
            CareerGraphEdge(sourceId = "user_u1", targetId = "skill_kotlin", relationType = CareerEdgeType.HAS_SKILL)
        )
    )

    @Test
    fun `persist then load round-trips nodes edges and properties`() = runTest {
        repository.persist(sampleGraph())

        val loaded = repository.loadGraph("u1")
        assertEquals(2, loaded.nodes.size)
        assertEquals(1, loaded.edges.size)
        assertEquals("Alice", loaded.getNode("user_u1")?.label)
        assertEquals("Eng", loaded.getNode("user_u1")?.properties?.get("targetRole"))
        assertEquals(CareerEdgeType.HAS_SKILL, loaded.edges.first().relationType)
    }

    @Test
    fun `re-projecting the same graph does not duplicate edges`() = runTest {
        repository.persist(sampleGraph())
        repository.persist(sampleGraph())

        val loaded = repository.loadGraph("u1")
        assertEquals(2, loaded.nodes.size)
        assertEquals(1, loaded.edges.size)
    }

    @Test
    fun `unknown persisted node type is skipped defensively on load`() = runTest {
        dao.nodes["weird"] = GraphNodeEntity("weird", "NOT_A_TYPE", "x", "{}", 1, 1)
        dao.nodes["skill_kotlin"] = GraphNodeEntity("skill_kotlin", "SKILL", "Kotlin", "{}", 1, 1)

        val loaded = repository.loadGraph("u1")
        assertEquals(1, loaded.nodes.size)
        assertTrue(loaded.getNode("skill_kotlin") != null)
    }
}
