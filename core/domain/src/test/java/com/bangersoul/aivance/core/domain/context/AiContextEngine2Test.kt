package com.bangersoul.aivance.core.domain.context

import com.bangersoul.aivance.core.common.events.CareerEvent
import com.bangersoul.aivance.core.common.events.SystemEvent
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.UserProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiContextEngine2Test {

    private lateinit var engine: AiContextEngine2

    @Before
    fun setUp() {
        engine = AiContextEngine2()
    }

    @Test
    fun `sanitizePii correctly redacts emails, phones, and SSNs`() {
        val raw = "Contact me at alice.smith@example.com or call +1 555-123-4567. Tax ID 123-45-6789."
        val sanitized = engine.sanitizePii(raw)

        assertFalse(sanitized.contains("alice.smith@example.com"))
        assertFalse(sanitized.contains("555-123-4567"))
        assertFalse(sanitized.contains("123-45-6789"))
        assertTrue(sanitized.contains("[USER_EMAIL]"))
        assertTrue(sanitized.contains("[USER_PHONE]"))
        assertTrue(sanitized.contains("[USER_ID]"))
    }

    @Test
    fun `buildContext prioritizes CRITICAL target job over LOW recent events under tight token budget`() {
        val job = JobListing(
            id = "job-1",
            title = "Staff Mobile Engineer",
            company = "Tech Corp",
            description = "Build Android systems",
            url = "https://example.com/job/1",
            sourceProvider = "Greenhouse"
        )

        val profile = UserProfile(
            id = "u1",
            fullName = "Alice Developer",
            email = "alice@example.com",
            skills = listOf("Kotlin", "Compose")
        )

        val dummyEvents = (1..20).map {
            SystemEvent(
                action = "TestEvent_$it",
                eventId = "ev-$it",
                sourceModule = "test"
            )
        }

        // Run with tight token budget
        val result = engine.buildContext(
            profile = profile,
            graph = null,
            activeJob = job,
            recentEvents = dummyEvents,
            tokenBudget = 60 // extremely small budget
        )

        // CRITICAL active job must be included; LOW recent events must be dropped
        assertTrue(result.includedBlocks.contains("active_job"))
        assertTrue(result.droppedBlocks.contains("recent_events"))
        assertTrue(result.totalEstimatedTokens <= 60)
    }
}
