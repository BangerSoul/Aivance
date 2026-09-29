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
        override suspend fun clearNodesByType(type: String) {
            nodes.values.removeAll { it.type == type }
        }
        override suspend fun clearNodesExceptType(excludedType: String) {
            nodes.values.removeAll { it.type != excludedType }
        }
        override suspend fun getNodesOfType(type: String): List<GraphNodeEntity> =
            nodes.values.filter { it.type == type }
        override suspend fun replaceGraph(nodes: List<GraphNodeEntity>, edges: List<GraphEdgeEntity>) {
            clearEdges(); clearNodes(); upsertNodes(nodes); upsertEdges(edges)
        }
        override suspend fun replaceEntityProjection(nodes: List<GraphNodeEntity>, edges: List<GraphEdgeEntity>) {
            clearEdges(); clearNodesExceptType("CAREER_EVENT"); upsertNodes(nodes); upsertEdges(edges)
        }
        override suspend fun replaceNodesOfType(type: String, nodes: List<GraphNodeEntity>) {
            clearNodesByType(type); upsertNodes(nodes)
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

    @Test
    fun `replaceEventProjection rebuilds only the CAREER_EVENT slice and leaves entity nodes intact`() = runTest {
        // Seed a relational-entity projection (profile + skill).
        repository.persist(sampleGraph())

        // Replay projects two event-provenance nodes; entity nodes must survive untouched.
        repository.replaceEventProjection(
            listOf(
                CareerGraphNode("event_e1", CareerNodeType.CAREER_EVENT, "ResumeCreated"),
                CareerGraphNode("event_e2", CareerNodeType.CAREER_EVENT, "JobSaved")
            )
        )

        val afterFirst = repository.loadGraph("u1")
        assertEquals(2, afterFirst.getNodesByType(CareerNodeType.CAREER_EVENT).size)
        assertTrue(afterFirst.getNode("user_u1") != null)
        assertTrue(afterFirst.getNode("skill_kotlin") != null)

        // Re-projecting a smaller event set replaces the whole slice (idempotent, no stale rows).
        repository.replaceEventProjection(
            listOf(CareerGraphNode("event_e1", CareerNodeType.CAREER_EVENT, "ResumeCreated"))
        )
        val afterSecond = repository.loadGraph("u1")
        assertEquals(1, afterSecond.getNodesByType(CareerNodeType.CAREER_EVENT).size)
        assertTrue(afterSecond.getNode("user_u1") != null)
    }

    @Test
    fun `live entity persist after replay preserves the CAREER_EVENT provenance slice (M05 co-writer)`() = runTest {
        // Replay writes the event-provenance slice first.
        repository.replaceEventProjection(
            listOf(
                CareerGraphNode("event_e1", CareerNodeType.CAREER_EVENT, "ResumeCreated"),
                CareerGraphNode("event_e2", CareerNodeType.CAREER_EVENT, "JobSaved")
            )
        )
        // A subsequent LIVE entity projection must NOT erase the replay-owned slice.
        repository.persist(sampleGraph())

        val loaded = repository.loadGraph("u1")
        assertEquals(2, loaded.getNodesByType(CareerNodeType.CAREER_EVENT).size)
        assertTrue(loaded.getNode("user_u1") != null)
        assertTrue(loaded.getNode("skill_kotlin") != null)
        assertEquals(1, loaded.edges.size)
    }

    @Test
    fun `replay after live entity persist preserves the entity projection (M05 co-writer inverse)`() = runTest {
        // Live entity projection first.
        repository.persist(sampleGraph())
        // Replay rebuilds only the event slice; entity nodes/edges must survive.
        repository.replaceEventProjection(
            listOf(CareerGraphNode("event_e1", CareerNodeType.CAREER_EVENT, "ResumeCreated"))
        )

        val loaded = repository.loadGraph("u1")
        assertTrue(loaded.getNode("user_u1") != null)
        assertTrue(loaded.getNode("skill_kotlin") != null)
        assertEquals(1, loaded.edges.size)
        assertEquals(1, loaded.getNodesByType(CareerNodeType.CAREER_EVENT).size)
    }

    @Test
    fun `repeated live entity persist replaces entity slice without duplicating or touching events`() = runTest {
        repository.replaceEventProjection(
            listOf(CareerGraphNode("event_e1", CareerNodeType.CAREER_EVENT, "ResumeCreated"))
        )
        repository.persist(sampleGraph())
        repository.persist(sampleGraph())

        val loaded = repository.loadGraph("u1")
        assertEquals(2, loaded.nodes.filterValues { it.type != CareerNodeType.CAREER_EVENT }.size)
        assertEquals(1, loaded.edges.size)
        assertEquals(1, loaded.getNodesByType(CareerNodeType.CAREER_EVENT).size)
    }
}
