package com.bangersoul.aivance.core.domain.careergraph

import com.bangersoul.aivance.core.common.graph.CareerGraph

/**
 * Canonical **content** signature of a projected [CareerGraph].
 *
 * Used by the live projection owner ([com.bangersoul.aivance.core.domain.engine.CareerStateEngine])
 * to skip a durable rewrite when a re-projection holds exactly the same content as the last one
 * that was written. Two rebuilds from the same underlying entities must therefore produce the
 * same signature, which is why this deliberately excludes:
 *
 *  - `createdAt` / `updatedAt` on nodes and edges — `CareerGraphNode`/`CareerGraphEdge` default
 *    them to `System.currentTimeMillis()`, so they differ on every rebuild while carrying no
 *    domain meaning; including them would make every emission look like a change.
 *  - `CareerGraphEdge.id` — the engine builds edges with the model's random-UUID default id, and
 *    the durable row id is derived from `source|relation|target` by the repository. The
 *    relationship itself, not the transient id, is the content.
 *
 * [CareerGraphNode.id] *is* included: the projection derives node ids deterministically from
 * entity ids/slugs, so a stable id is part of the content.
 */
fun CareerGraph.contentSignature(): String {
    val out = StringBuilder(64 + nodes.size * 48 + edges.size * 40)
    nodes.values.sortedBy { it.id }.forEach { node ->
        out.append(node.id).append('\u0001')
        out.append(node.type.name).append('\u0001')
        out.append(node.label).append('\u0001')
        node.properties.toSortedMap().forEach { (key, value) ->
            out.append(key).append('=').append(value).append('\u0002')
        }
        out.append('\u0003')
    }
    edges
        .map { "${it.sourceId}|${it.relationType.name}|${it.targetId}|${it.weight}" }
        .sorted()
        .forEach { out.append(it).append('\u0003') }
    return out.toString()
}
