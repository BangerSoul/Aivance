package com.bangersoul.aivance.core.database.model

/**
 * Persisted `graph_nodes.type` discriminator strings owned by the database layer.
 *
 * These mirror the `com.bangersoul.aivance.core.common.graph.CareerNodeType` enum names but are
 * declared here so the DAO can reference the replay-owned slice discriminator without the database
 * module depending on the domain/common graph model. Keep in lock-step with that enum.
 */
object GraphNodeTypes {
    /**
     * The replay-owned event-provenance slice discriminator (M04-B / M05). The live entity graph
     * projection must never clear rows of this type; only the event replay engine owns them.
     */
    const val CAREER_EVENT: String = "CAREER_EVENT"
}
