package com.bangersoul.aivance.core.domain.replay

import com.bangersoul.aivance.core.common.events.CareerEventDecodeResult
import com.bangersoul.aivance.core.common.events.CareerEventEnvelope
import com.bangersoul.aivance.core.common.graph.CareerGraphNode
import com.bangersoul.aivance.core.common.graph.CareerNodeType
import com.bangersoul.aivance.core.domain.repository.CareerEventLogRepository
import com.bangersoul.aivance.core.domain.repository.CareerGraphRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The category of a single replay failure. Carries enough context for a diagnostic to identify the
 * offending persisted event without re-reading the log.
 */
sealed interface CareerReplayFailure {
    /** The offending event's id, if the log row carried one. */
    val eventId: String?

    /** A persisted event type is not part of the current [com.bangersoul.aivance.core.common.events.CareerEventContract]. */
    data class UnknownType(override val eventId: String?, val eventType: String, val schemaVersion: Int) : CareerReplayFailure

    /** A persisted event's payload version is not decodable by the current contract. */
    data class UnsupportedVersion(
        override val eventId: String?,
        val eventType: String,
        val schemaVersion: Int,
        val supportedVersions: Set<Int>
    ) : CareerReplayFailure

    /** A persisted event's payload could not be parsed. */
    data class MalformedPayload(override val eventId: String?, val eventType: String?, val reason: String) : CareerReplayFailure

    /** A projection/persistence step threw while writing the reconstructed projection. */
    data class ProjectionError(override val eventId: String?, val reason: String) : CareerReplayFailure
}

/**
 * The outcome of a replay pass. Replay is all-or-nothing: it either commits a fully reconstructed
 * projection ([Success]) or commits nothing and reports the first blocking [failure] ([Failed]).
 * There is deliberately no partially-rebuilt success state.
 */
sealed interface CareerReplayResult {
    data class Success(val eventsReplayed: Int, val nodesProjected: Int) : CareerReplayResult
    data class Failed(val failure: CareerReplayFailure) : CareerReplayResult
}

/**
 * Deterministic, version-aware replay of the durable `career_event_log` into the Career Knowledge
 * Graph's **event-provenance layer** (M04-B).
 *
 * ### What this is
 *
 * Replay reads every persisted event, decodes it against the versioned contract, and rebuilds a
 * `CAREER_EVENT` node per event so the graph carries an authoritative, reconstructable record of
 * *what happened, when, and from where*. It is a pure **projection** operation:
 *
 *  - It never re-executes historical commands — no WorkflowEngine calls, no repository mutations of
 *    business entities, no AI/network/outreach side effects, and it never re-emits events onto the
 *    live bus.
 *  - The Room database remains the authoritative application state; this only reconstructs a
 *    derived projection slice.
 *
 * ### Scope (honest coverage)
 *
 * The persisted payloads are a flattened, string-valued **audit** representation and many event
 * types omit their entity identity (e.g. `ResumeAnalysisCompleted` records only `atsScore`, not
 * which resume). Reconstructing entity-level graph nodes or structured memory entries from the log
 * alone would require inventing missing historical data, so those projections are DEFERRED —
 * entity graph nodes stay owned by [CareerGraphEngine.buildGraph] (fed by authoritative Room
 * entities) and memory stays owned by [com.bangersoul.aivance.core.domain.memory.CareerMemoryEngine].
 * The event-provenance layer is the one projection the log can rebuild without invention.
 *
 * ### Invariants
 *
 *  - **Deterministic ordering** — events are projected in `(timestamp, eventId)` order, applied in
 *    the engine itself rather than trusting storage order.
 *  - **Idempotency** — each event maps to a stable `event_<eventId>` node id, so replaying once or
 *    many times converges to the identical set of rows.
 *  - **Version-aware** — decoding is governed by the contract; version is never inferred from
 *    payload shape.
 *  - **Loud failure** — an unknown type, unsupported version, or malformed payload aborts the whole
 *    pass with an explicit [CareerReplayFailure]; nothing is silently skipped or coerced.
 *  - **Transactional / no partial projection** — the reconstructed slice is written in a single
 *    transactional [CareerGraphRepository.replaceEventProjection]; if decoding fails, no write
 *    happens at all, and if the write itself fails it rolls back atomically.
 *
 * ### Rebuild semantics: FULL REBUILD
 *
 * Each pass rebuilds the entire `CAREER_EVENT` slice from the complete log. There is no incremental
 * checkpoint/resume and none is claimed.
 */
@Singleton
class CareerEventReplayEngine @Inject constructor(
    private val eventLogRepository: CareerEventLogRepository,
    private val graphRepository: CareerGraphRepository
) {

    /**
     * Replays the entire durable event log into the graph's event-provenance layer.
     *
     * Reads and decodes every row, fails loudly on the first non-decodable event (committing
     * nothing), and otherwise writes the full reconstructed slice in one transaction.
     */
    suspend fun replayAll(): CareerReplayResult {
        val decoded = eventLogRepository.decodeAll()

        // Loud failure: the first non-decodable event aborts the whole pass BEFORE any write,
        // so a malformed/unknown/unsupported event can never yield a falsely complete rebuild.
        val envelopes = ArrayList<CareerEventEnvelope>(decoded.size)
        for (result in decoded) {
            when (result) {
                is CareerEventDecodeResult.Decoded -> envelopes.add(result.envelope)
                is CareerEventDecodeResult.UnknownType ->
                    return CareerReplayResult.Failed(
                        CareerReplayFailure.UnknownType(eventId = null, eventType = result.eventType, schemaVersion = result.schemaVersion)
                    )
                is CareerEventDecodeResult.UnsupportedVersion ->
                    return CareerReplayResult.Failed(
                        CareerReplayFailure.UnsupportedVersion(null, result.eventType, result.schemaVersion, result.supportedVersions)
                    )
                is CareerEventDecodeResult.Malformed ->
                    return CareerReplayResult.Failed(
                        CareerReplayFailure.MalformedPayload(null, result.eventType, result.reason)
                    )
            }
        }

        // Deterministic ordering applied here rather than trusting storage order.
        val ordered = envelopes.sortedWith(compareBy({ it.timestamp }, { it.eventId }))
        val nodes = ordered.map { it.toProvenanceNode() }

        return runCatching { graphRepository.replaceEventProjection(nodes) }
            .fold(
                onSuccess = { CareerReplayResult.Success(eventsReplayed = ordered.size, nodesProjected = nodes.size) },
                onFailure = { CareerReplayResult.Failed(CareerReplayFailure.ProjectionError(null, it.message ?: "projection write failed")) }
            )
    }

    /**
     * Projects one decoded event into its deterministic, idempotent `CAREER_EVENT` provenance node.
     * The node id is derived solely from the event's own id so repeated replays never duplicate it.
     */
    private fun CareerEventEnvelope.toProvenanceNode(): CareerGraphNode {
        val properties = buildMap {
            put("eventType", eventType)
            put("schemaVersion", schemaVersion.toString())
            put("sourceModule", sourceModule)
            put("timestamp", timestamp.toString())
            correlationId?.let { put("correlationId", it) }
            causationId?.let { put("causationId", it) }
            payload.forEach { (key, value) -> put("payload.$key", value ?: "") }
        }
        return CareerGraphNode(
            id = "event_$eventId",
            type = CareerNodeType.CAREER_EVENT,
            label = eventType,
            properties = properties,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }
}
