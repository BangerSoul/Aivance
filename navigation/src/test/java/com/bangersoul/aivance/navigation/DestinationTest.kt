package com.bangersoul.aivance.navigation

import com.bangersoul.aivance.core.designsystem.icon.IconVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationTest {

    @Test
    fun `root destinations are exactly the four N1 workspaces`() {
        assertEquals(
            listOf(
                Destination.Dashboard,
                Destination.Discovery(),
                Destination.Pipeline(),
                Destination.Studio()
            ),
            Destination.rootDestinations
        )
    }

    @Test
    fun `AI orb is not a standard root tab`() {
        assertFalse(
            "AssistantOrb must not be in rootDestinations (rendered as the orb slot)",
            Destination.AssistantOrb in Destination.rootDestinations
        )
    }

    @Test
    fun `every root destination is authenticated`() {
        Destination.rootDestinations.forEach { dest ->
            assertTrue("${dest.label} must be authenticated", dest.isAuthenticatedDestination())
        }
    }

    @Test
    fun `every root destination resolves duotone variants in both states`() {
        Destination.rootDestinations.forEach { dest ->
            assertNotNull(
                "${dest.label} must resolve an outlined icon",
                dest.iconIntent.forVariant(IconVariant.OUTLINED)
            )
            assertNotNull(
                "${dest.label} must resolve a filled icon",
                dest.iconIntent.forVariant(IconVariant.FILLED)
            )
        }
    }

    @Test
    fun `workspace icons are custom paired vectors with distinct variants`() {
        val intents = Destination.rootDestinations.map { it.iconIntent }
        intents.forEach { intent ->
            assertNotNull("workspace must have custom outlined icon", intent.outlined)
            assertNotNull("workspace must have custom filled icon", intent.filled)
        }
        // Duotone contract: the filled twin must be a different vector, not a tint flip.
        intents.forEach { intent ->
            assertTrue(
                "outlined and filled variants must be distinct vectors",
                intent.outlined !== intent.filled
            )
        }
    }

    @Test
    fun `assistant orb carries no standard tab icon`() {
        val intent = Destination.AssistantOrb.iconIntent
        assertNull("orb renders via AiOrbIcon, not a vector", intent.outlined)
        assertNull(intent.filled)
        assertTrue(Destination.AssistantOrb.isAuthenticatedDestination())
    }

    @Test
    fun `orb and studio are authenticated surfaces`() {
        assertTrue(Destination.AssistantOrb.isAuthenticatedDestination())
        assertTrue(Destination.Studio().isAuthenticatedDestination())
    }

    @Test
    fun `resources is guarded like every other authenticated surface`() {
        // Guard gap (AUDIT §3.2): without this, a deep link could push Resources
        // onto the auth backstack before sign-in.
        assertTrue(Destination.Resources in Destination.authenticatedDestinations)
        assertTrue(Destination.Resources.isAuthenticatedDestination())
    }

    @Test
    fun `provider setup is not an authenticated workspace destination`() {
        // ProviderSetup is the gate's remediation surface, never a workspace.
        assertFalse(Destination.ProviderSetup.isAuthenticatedDestination())
    }

    @Test
    fun `auth graph destinations are not authenticated and have no icon`() {
        Destination.authDestinations.forEach { dest ->
            assertFalse("${dest.label} must not be authenticated", dest.isAuthenticatedDestination())
            assertNull("${dest.label} must not have a nav icon", dest.icon)
        }
    }

    @Test
    fun `parameterized detail destinations resolve as authenticated by type`() {
        assertTrue(Destination.Ats(jobDescription = "JD").isAuthenticatedDestination())
        assertTrue(Destination.CoverLetter(jobId = 1L).isAuthenticatedDestination())
        assertTrue(Destination.JobDetails("job-1").isAuthenticatedDestination())
        assertTrue(Destination.RecruiterDashboard("job-1").isAuthenticatedDestination())
        assertTrue(Destination.CompanyDetail("acme").isAuthenticatedDestination())
        assertTrue(Destination.ResumeDetail(1L).isAuthenticatedDestination())
        assertTrue(Destination.ResumeEngine(jobDescription = "JD").isAuthenticatedDestination())
    }

    @Test
    fun `seeded workspace variants stay authenticated despite differing from the canonical tab`() {
        // Discovery/Pipeline/Studio are parameterised: a seeded instance is not
        // equal to the set member, so membership alone would funnel these to the
        // auth backstack.
        assertTrue(Destination.Discovery(query = "Kotlin").isAuthenticatedDestination())
        assertTrue(Destination.Pipeline(jobId = "job-1").isAuthenticatedDestination())
        assertTrue(Destination.Studio(segment = StudioSegment.PRACTICE).isAuthenticatedDestination())
        assertTrue(
            Destination.Studio(segment = StudioSegment.PRACTICE, learnSkill = "Kotlin")
                .isAuthenticatedDestination()
        )
    }

    @Test
    fun `resume engine carries an optional preloaded job description`() {
        assertEquals(null, Destination.ResumeEngine().jobDescription)
        assertEquals("Senior Android Engineer…", Destination.ResumeEngine(jobDescription = "Senior Android Engineer…").jobDescription)
    }

    @Test
    fun `pipeline absorbs the tracked application id as a nav argument`() {
        // The TrackApplication spoke is gone; the job id rides on the tab.
        assertEquals(null, Destination.Pipeline().jobId)
        assertEquals("job-1", Destination.Pipeline(jobId = "job-1").jobId)
        assertEquals("Pipeline", Destination.Pipeline(jobId = "job-1").label)
        assertNotNull(Destination.Pipeline(jobId = "job-1").icon)
    }

    @Test
    fun `discovery absorbs the skill-gap query as a nav argument`() {
        val discover = Destination.Discovery(query = "Kubernetes")
        assertEquals("Kubernetes", discover.query)
        assertEquals("Job Discovery", discover.label)
        assertNotNull(discover.icon)
        assertEquals(null, Destination.Discovery().query)
    }

    @Test
    fun `studio absorbs the segment and learn-skill seeds as nav arguments`() {
        assertEquals(StudioSegment.RESUMES, Destination.Studio().segment)
        assertEquals(null, Destination.Studio().learnSkill)

        val practice = Destination.Studio(segment = StudioSegment.PRACTICE, learnSkill = "Kubernetes")
        assertEquals(StudioSegment.PRACTICE, practice.segment)
        assertEquals("Kubernetes", practice.learnSkill)
        assertEquals("Studio", practice.label)
        assertNotNull(practice.icon)
    }

    @Test
    fun `auth and authenticated destination sets do not overlap`() {
        val overlap = Destination.authDestinations.intersect(Destination.authenticatedDestinations)
        assertTrue("No overlap expected but found $overlap", overlap.isEmpty())
    }

    @Test
    fun `default ATS and cover letter arguments are null`() {
        assertEquals(null, Destination.Ats().jobDescription)
        assertEquals(null, Destination.Ats().reportId)
        assertEquals(null, Destination.CoverLetter().jobId)
    }

    @Test
    fun `ATS destination carries an optional saved report id`() {
        assertEquals(42L, Destination.Ats(reportId = 42L).reportId)
        assertEquals("JD", Destination.Ats(jobDescription = "JD", reportId = 42L).jobDescription)
        assertEquals(42L, Destination.Ats(jobDescription = "JD", reportId = 42L).reportId)
    }

    @Test
    fun `provider setup is the single onboarding gate`() {
        // Onboarding was removed as a separate destination — ProviderSetup is
        // the only onboarding/provider-remediation surface.
        assertEquals("Provider Setup", Destination.ProviderSetup.label)
    }

    @Test
    fun `v2 career destinations carry labels`() {
        assertEquals("Studio", Destination.Studio().label)
        assertEquals("Pipeline", Destination.Pipeline().label)
        assertEquals("Identity Hub", Destination.IdentityHub.label)
        assertEquals("Company", Destination.CompanyDetail("x").label)
        assertEquals("Provider Setup", Destination.ProviderSetup.label)
        assertEquals("Sign In", Destination.Auth.label)
    }
}
