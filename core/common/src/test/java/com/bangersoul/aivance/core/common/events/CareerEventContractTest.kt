package com.bangersoul.aivance.core.common.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level coverage for the hardened persisted event contract (M04-A):
 *
 *  - every event type currently emitted carries the explicit current payload version,
 *  - a payload round-trips through the shared codec preserving its semantics,
 *  - an unsupported version, an unknown type, and a malformed payload each fail explicitly and
 *    distinguishably, with no silent fallback or reinterpretation.
 */
class CareerEventContractTest {

    @Test
    fun `every dispatched event type is registered and supports its own emitted version`() {
        // Representative events across each producer family currently persisted by the dispatcher.
        // Four families carry a v2 payload (M04-C) that adds stable entity identity; the rest are v1.
        val events: List<CareerEvent> = listOf(
            ResumeEvent.Created(resumeId = "1", name = "R"),
            ResumeEvent.AnalysisCompleted(resumeId = "1", versionId = "2", atsScore = 90),
            AtsEvent.ScoreChanged(resumeVersionId = "1", jobId = "2", overallScore = 80),
            JobEvent.Saved(jobId = "1", company = "Acme", title = "Eng"),
            ApplicationEvent.StageChanged(applicationId = "1", oldStage = "A", newStage = "B"),
            InterviewEvent.Completed(sessionId = "1", overallScore = 70),
            AgentEvent.ActionProposed(proposalId = "1", actionType = "APPLY", riskLevel = "LOW"),
            SystemEvent(action = "init")
        )

        events.forEach { event ->
            assertTrue(
                "event type ${event.eventType} must be in the versioned contract",
                CareerEventContract.isKnownType(event.eventType)
            )
            assertTrue(
                "event type ${event.eventType} must support its own emitted version",
                CareerEventContract.isSupportedVersion(event.eventType, event.schemaVersion)
            )
        }
    }

    @Test
    fun `the four entity-identity families emit payload version 2`() {
        assertEquals(2, ResumeEvent.AnalysisCompleted(resumeId = "1", versionId = "2", atsScore = 90).schemaVersion)
        assertEquals(2, JobEvent.Saved(jobId = "1", company = "Acme", title = "Eng").schemaVersion)
        assertEquals(2, ApplicationEvent.StageChanged(applicationId = "1", oldStage = "A", newStage = "B").schemaVersion)
        assertEquals(2, InterviewEvent.Completed(sessionId = "1", overallScore = 70).schemaVersion)

        // Their v2 payloads carry the stable entity identity a replay engine needs to rehydrate.
        assertEquals("1", ResumeEvent.AnalysisCompleted(resumeId = "1", versionId = "2", atsScore = 90).payload["resumeId"])
        assertEquals("2", ResumeEvent.AnalysisCompleted(resumeId = "1", versionId = "2", atsScore = 90).payload["versionId"])
        assertEquals("1", JobEvent.Saved(jobId = "1", company = "Acme", title = "Eng").payload["jobId"])
        assertEquals("1", ApplicationEvent.StageChanged(applicationId = "1", oldStage = "A", newStage = "B").payload["applicationId"])
        assertEquals("1", InterviewEvent.Completed(sessionId = "1", overallScore = 70).payload["sessionId"])
    }

    @Test
    fun `entity-identity families accept both v1 and v2 while other types accept only v1`() {
        CareerEventContract.ENTITY_IDENTITY_V2_TYPES.forEach { type ->
            assertTrue("$type must decode v1", CareerEventContract.isSupportedVersion(type, 1))
            assertTrue("$type must decode v2", CareerEventContract.isSupportedVersion(type, 2))
        }
        // A representative non-evolved type still rejects v2.
        assertTrue(CareerEventContract.isSupportedVersion("ResumeCreated", 1))
        assertFalse(CareerEventContract.isSupportedVersion("ResumeCreated", 2))
    }

    @Test
    fun `a legacy v1 analysis payload without identity still decodes but carries no entity id`() {
        // Simulates an already-persisted v1 row: payload has only atsScore, no resumeId/versionId.
        val result = CareerEventCodec.decode(
            eventId = "legacy-1",
            schemaVersion = 1,
            eventType = "ResumeAnalysisCompleted",
            sourceModule = "feature:resume",
            timestamp = 100,
            correlationId = null,
            causationId = null,
            payloadJson = "{\"atsScore\":\"77\"}"
        )

        assertTrue(result is CareerEventDecodeResult.Decoded)
        val envelope = (result as CareerEventDecodeResult.Decoded).envelope
        assertEquals(1, envelope.schemaVersion)
        assertEquals("77", envelope.payload["atsScore"])
        assertFalse("legacy v1 payload must not contain entity identity", envelope.payload.containsKey("resumeId"))
    }

    @Test
    fun `a v2 analysis payload round-trips carrying stable entity identity`() {
        val event = ResumeEvent.AnalysisCompleted(resumeId = "5", versionId = "9", atsScore = 88)
        val payloadJson = CareerEventCodec.encodePayload(event.payload)

        val result = CareerEventCodec.decode(
            eventId = event.eventId,
            schemaVersion = event.schemaVersion,
            eventType = event.eventType,
            sourceModule = event.sourceModule,
            timestamp = event.timestamp,
            correlationId = event.correlationId,
            causationId = event.causationId,
            payloadJson = payloadJson
        )

        assertTrue(result is CareerEventDecodeResult.Decoded)
        val envelope = (result as CareerEventDecodeResult.Decoded).envelope
        assertEquals(2, envelope.schemaVersion)
        assertEquals("5", envelope.payload["resumeId"])
        assertEquals("9", envelope.payload["versionId"])
        assertEquals("88", envelope.payload["atsScore"])
    }

    @Test
    fun `an unsupported v3 version of an evolved type fails explicitly`() {
        val result = CareerEventCodec.decode(
            eventId = "e1",
            schemaVersion = 3,
            eventType = "InterviewCompleted",
            sourceModule = "feature:interview",
            timestamp = 1,
            correlationId = null,
            causationId = null,
            payloadJson = "{\"sessionId\":\"1\"}"
        )

        assertTrue(result is CareerEventDecodeResult.UnsupportedVersion)
        result as CareerEventDecodeResult.UnsupportedVersion
        assertEquals("InterviewCompleted", result.eventType)
        assertEquals(3, result.schemaVersion)
        assertEquals(setOf(1, 2), result.supportedVersions)
    }

    @Test
    fun `encode then decode preserves version type and payload semantics`() {
        val event = ResumeEvent.AnalysisCompleted(resumeId = "5", versionId = "9", atsScore = 88)
        val payloadJson = CareerEventCodec.encodePayload(event.payload)

        val result = CareerEventCodec.decode(
            eventId = event.eventId,
            schemaVersion = event.schemaVersion,
            eventType = event.eventType,
            sourceModule = event.sourceModule,
            timestamp = event.timestamp,
            correlationId = event.correlationId,
            causationId = event.causationId,
            payloadJson = payloadJson
        )

        assertTrue(result is CareerEventDecodeResult.Decoded)
        val envelope = (result as CareerEventDecodeResult.Decoded).envelope
        assertEquals(event.eventId, envelope.eventId)
        assertEquals(event.eventType, envelope.eventType)
        assertEquals(event.schemaVersion, envelope.schemaVersion)
        assertEquals("88", envelope.payload["atsScore"])
    }

    @Test
    fun `unsupported version fails explicitly and is never reinterpreted`() {
        val result = CareerEventCodec.decode(
            eventId = "e1",
            schemaVersion = 2, // no v2 exists yet for this type
            eventType = "ResumeCreated",
            sourceModule = "feature:resume",
            timestamp = 1,
            correlationId = null,
            causationId = null,
            payloadJson = "{\"name\":\"x\"}"
        )

        assertTrue(result is CareerEventDecodeResult.UnsupportedVersion)
        result as CareerEventDecodeResult.UnsupportedVersion
        assertEquals("ResumeCreated", result.eventType)
        assertEquals(2, result.schemaVersion)
        assertFalse("v2 must not be silently accepted", result.supportedVersions.contains(2))
        assertTrue(result.supportedVersions.contains(CareerEventContract.CURRENT_PAYLOAD_VERSION))
    }

    @Test
    fun `unknown type is reported as unknown rather than malformed`() {
        val result = CareerEventCodec.decode(
            eventId = "e1",
            schemaVersion = 1,
            eventType = "TotallyMadeUpEvent",
            sourceModule = "feature:mystery",
            timestamp = 1,
            correlationId = null,
            causationId = null,
            payloadJson = "{}"
        )

        assertTrue(result is CareerEventDecodeResult.UnknownType)
        assertEquals("TotallyMadeUpEvent", (result as CareerEventDecodeResult.UnknownType).eventType)
    }

    @Test
    fun `malformed payload of a supported event fails explicitly with no silent coercion`() {
        val result = CareerEventCodec.decode(
            eventId = "e1",
            schemaVersion = 1,
            eventType = "ResumeCreated",
            sourceModule = "feature:resume",
            timestamp = 1,
            correlationId = null,
            causationId = null,
            payloadJson = "this-is-not-json"
        )

        assertTrue(result is CareerEventDecodeResult.Malformed)
        assertEquals("ResumeCreated", (result as CareerEventDecodeResult.Malformed).eventType)
    }

    @Test
    fun `a json array payload is malformed not silently accepted`() {
        val result = CareerEventCodec.decode(
            eventId = "e1",
            schemaVersion = 1,
            eventType = "ResumeCreated",
            sourceModule = "feature:resume",
            timestamp = 1,
            correlationId = null,
            causationId = null,
            payloadJson = "[1,2,3]"
        )

        assertTrue(result is CareerEventDecodeResult.Malformed)
    }
}
