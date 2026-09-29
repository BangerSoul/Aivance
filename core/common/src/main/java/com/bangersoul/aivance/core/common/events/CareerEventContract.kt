package com.bangersoul.aivance.core.common.events

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The explicit, versioned contract for persisted [CareerEvent]s (M04-A).
 *
 * This is the single authority that answers, for any persisted event, four questions a future
 * replay engine must never have to infer from payload shape:
 *
 *  - What event type is this?            → [isKnownType]
 *  - Which payload version is it?        → carried explicitly as `schemaVersion`
 *  - Can this version be decoded?        → [isSupportedVersion] / [supportedVersions]
 *  - What happened when decoding failed? → [CareerEventDecodeResult]
 *
 * ### Version semantics
 *
 *  - **Payload/schema version is distinct from the Room database version and the event-type
 *    discriminator.** A payload may evolve (`ResumeAnalysisCompleted` v1 → v2) without a Room
 *    migration, and the database may migrate without changing any payload version.
 *  - **New contract → version 1.** Every event type currently emitted carries
 *    [CURRENT_PAYLOAD_VERSION].
 *  - **Breaking payload change → new version.** Add the new version number to that type's entry in
 *    [SUPPORTED_VERSIONS]; a consumer must explicitly support each version it can decode.
 *  - **Unknown type / unsupported version / malformed payload are explicit, distinguishable
 *    failure states — never a silent fallback or reinterpretation** (see [CareerEventDecodeResult]).
 *
 * NOTE: This hardens the persisted contract only. No replay engine consumes the log yet.
 */
object CareerEventContract {

    /** The payload/schema version assigned to every event type under the current contract. */
    const val CURRENT_PAYLOAD_VERSION: Int = 1

    /**
     * Event types whose payload has evolved to v2 (M04-C) to carry stable entity identity that a
     * v1 audit payload omitted. Both versions remain decodable: already-persisted v1 rows decode
     * exactly as before (and are non-rehydratable because they lack the identity), while newly
     * emitted v2 rows carry the identity a replay engine needs to rebuild the entity node.
     */
    val ENTITY_IDENTITY_V2_TYPES: Set<String> = setOf(
        "ResumeAnalysisCompleted", // + resumeId, versionId
        "JobSaved",                // + jobId
        "ApplicationStageChanged", // + applicationId
        "InterviewCompleted"       // + sessionId
    )

    /**
     * Every canonical [CareerEvent.eventType] discriminator currently produced by
     * [CareerEventDispatcher], mapped to the set of payload versions this contract can decode.
     *
     * Keep this in lock-step with the `eventType` values declared in `CareerEvent.kt`. Adding a
     * breaking payload change means adding the new version number to the relevant entry here
     * (see [ENTITY_IDENTITY_V2_TYPES]).
     */
    val SUPPORTED_VERSIONS: Map<String, Set<Int>> = buildMap {
        val v1 = setOf(CURRENT_PAYLOAD_VERSION)
        val v1AndV2 = setOf(CURRENT_PAYLOAD_VERSION, 2)
        listOf(
            // Resume
            "ResumeCreated", "ResumeUpdated", "ResumeVersionCreated",
            "ResumeAnalysisCompleted", "ResumeSectionModified", "ResumeDeleted",
            // ATS
            "AtsScanStarted", "AtsScoreChanged", "AtsOptimizationCompleted",
            // Job
            "JobDiscovered", "JobSaved", "JobViewed", "JobMatchCalculated", "JobApplied", "JobArchived",
            // Application
            "ApplicationCreated", "ApplicationStageChanged", "ApplicationTaskCreated", "ApplicationTaskCompleted",
            // Interview
            "InterviewScheduled", "InterviewStarted", "InterviewTurnEvaluated", "InterviewCompleted", "InterviewEvaluated",
            // Skill
            "SkillDetected", "SkillUpdated",
            // Goal
            "GoalCreated", "CareerGoalChanged", "GoalProgressUpdated", "GoalAchieved",
            // Cover letter
            "CoverLetterCreated", "CoverLetterUpdated",
            // Provider
            "ProviderConnected", "ProviderDisconnected", "ProviderHealthChanged",
            // Career analytics
            "CareerScoreChanged", "CareerInsightGenerated",
            // Agent
            "AgentGoalCreated", "AgentPlanCreated", "AgentActionProposed",
            "AgentActionApproved", "AgentActionRejected", "AgentActionExecuted",
            // Automation
            "AutomationRuleTriggered",
            // System
            "SystemInitialized"
        ).forEach { put(it, if (it in ENTITY_IDENTITY_V2_TYPES) v1AndV2 else v1) }
    }

    /** True if [eventType] is a discriminator this contract recognizes. */
    fun isKnownType(eventType: String): Boolean = SUPPORTED_VERSIONS.containsKey(eventType)

    /** The payload versions decodable for [eventType]; empty if the type is unknown. */
    fun supportedVersions(eventType: String): Set<Int> = SUPPORTED_VERSIONS[eventType] ?: emptySet()

    /** True only if [eventType] is known AND [schemaVersion] is a version this contract can decode. */
    fun isSupportedVersion(eventType: String, schemaVersion: Int): Boolean =
        supportedVersions(eventType).contains(schemaVersion)
}

/**
 * The durable, decoded representation of a persisted [CareerEvent] — the envelope columns of the
 * `career_event_log` row plus its flattened string payload. Produced only by a successful
 * [CareerEventDecodeResult.Decoded]; a future replay engine projects from this, never from raw JSON.
 */
data class CareerEventEnvelope(
    val eventId: String,
    val schemaVersion: Int,
    val eventType: String,
    val sourceModule: String,
    val timestamp: Long,
    val correlationId: String?,
    val causationId: String?,
    val payload: Map<String, String?>
)

/**
 * The explicit outcome of decoding one persisted event against [CareerEventContract].
 *
 * Every non-[Decoded] state names the offending event type and (where known) version so a future
 * replay engine can fail loudly and precisely rather than silently coercing or dropping an event.
 */
sealed interface CareerEventDecodeResult {

    /** The event type is known, its version is supported, and its payload parsed cleanly. */
    data class Decoded(val envelope: CareerEventEnvelope) : CareerEventDecodeResult

    /** The event type is not part of the current contract. */
    data class UnknownType(val eventType: String, val schemaVersion: Int) : CareerEventDecodeResult

    /** The event type is known but the persisted payload version is not decodable by this contract. */
    data class UnsupportedVersion(
        val eventType: String,
        val schemaVersion: Int,
        val supportedVersions: Set<Int>
    ) : CareerEventDecodeResult

    /** The type/version were acceptable but the payload JSON could not be parsed as an object of scalars. */
    data class Malformed(val eventType: String?, val reason: String) : CareerEventDecodeResult
}

/**
 * Deterministic, contract-aware codec for the persisted event payload/envelope (M04-A).
 *
 * The write path ([encodePayload]) and any future read path ([decode]) share this single codec, so
 * a payload is always serialized and deserialized identically. Decoding is guarded by
 * [CareerEventContract]: version and type are validated from explicit metadata (never inferred from
 * payload shape) before the payload is parsed, and each failure maps to a distinct
 * [CareerEventDecodeResult]. There is deliberately no silent fallback and no replay/projection here.
 */
object CareerEventCodec {

    private val json = Json { encodeDefaults = true }

    /**
     * Flattens a structured [CareerEvent.payload] to a JSON object of string values, matching the
     * schema-light audit representation stored in `career_event_log.payloadJson`.
     */
    fun encodePayload(payload: Map<String, Any?>): String {
        val obj = JsonObject(payload.mapValues { (_, value) -> JsonPrimitive(value?.toString()) })
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    /**
     * Decodes one persisted event's envelope + payload into an explicit [CareerEventDecodeResult].
     *
     * Precedence is intentional and does not depend on payload contents:
     *  1. unknown [eventType]            → [CareerEventDecodeResult.UnknownType]
     *  2. unsupported [schemaVersion]    → [CareerEventDecodeResult.UnsupportedVersion]
     *  3. unparseable [payloadJson]      → [CareerEventDecodeResult.Malformed]
     *  4. otherwise                      → [CareerEventDecodeResult.Decoded]
     */
    fun decode(
        eventId: String,
        schemaVersion: Int,
        eventType: String,
        sourceModule: String,
        timestamp: Long,
        correlationId: String?,
        causationId: String?,
        payloadJson: String
    ): CareerEventDecodeResult {
        if (!CareerEventContract.isKnownType(eventType)) {
            return CareerEventDecodeResult.UnknownType(eventType, schemaVersion)
        }
        if (!CareerEventContract.isSupportedVersion(eventType, schemaVersion)) {
            return CareerEventDecodeResult.UnsupportedVersion(
                eventType = eventType,
                schemaVersion = schemaVersion,
                supportedVersions = CareerEventContract.supportedVersions(eventType)
            )
        }
        val payload = runCatching { decodePayload(payloadJson) }.getOrElse {
            return CareerEventDecodeResult.Malformed(
                eventType = eventType,
                reason = it.message ?: "Unparseable payload JSON"
            )
        }
        return CareerEventDecodeResult.Decoded(
            CareerEventEnvelope(
                eventId = eventId,
                schemaVersion = schemaVersion,
                eventType = eventType,
                sourceModule = sourceModule,
                timestamp = timestamp,
                correlationId = correlationId,
                causationId = causationId,
                payload = payload
            )
        )
    }

    /** Parses a JSON object of scalar values back into a string-valued payload map. */
    private fun decodePayload(payloadJson: String): Map<String, String?> {
        val element = json.parseToJsonElement(payloadJson)
        val obj = element as? JsonObject
            ?: throw IllegalArgumentException("Payload is not a JSON object")
        return obj.mapValues { (_, value) ->
            (value as? JsonPrimitive)?.contentOrNull
        }
    }
}
