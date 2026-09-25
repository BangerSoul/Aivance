package com.bangersoul.aivance.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationTest {

    @Test
    fun `root destinations are exactly the five main tabs`() {
        assertEquals(
            listOf(
                Destination.Dashboard,
                Destination.Intelligence,
                Destination.Discovery,
                Destination.Pipeline,
                Destination.PrepStudio
            ),
            Destination.rootDestinations
        )
    }

    @Test
    fun `every root destination is authenticated`() {
        Destination.rootDestinations.forEach { dest ->
            assertTrue("${dest.label} must be authenticated", dest.isAuthenticatedDestination())
        }
    }

    @Test
    fun `every root destination has an icon`() {
        Destination.rootDestinations.forEach { dest ->
            assertNotNull("${dest.label} must have an icon", dest.icon)
        }
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
        assertTrue(Destination.TrackApplication("job-1").isAuthenticatedDestination())
        assertTrue(Destination.DiscoverBySkill("Kotlin").isAuthenticatedDestination())
        assertTrue(Destination.LearnSkill("Kotlin").isAuthenticatedDestination())
    }

    @Test
    fun `resume engine carries an optional preloaded job description`() {
        assertEquals(null, Destination.ResumeEngine().jobDescription)
        assertEquals("Senior Android Engineer…", Destination.ResumeEngine(jobDescription = "Senior Android Engineer…").jobDescription)
    }

    @Test
    fun `track application carries the source job id and maps to pipeline`() {
        assertEquals("job-1", Destination.TrackApplication("job-1").jobId)
        assertEquals("Pipeline", Destination.TrackApplication("job-1").label)
        assertNotNull(Destination.TrackApplication("job-1").icon)
    }

    @Test
    fun `skill-gap deep links carry the skill and map to their workspaces`() {
        val discover = Destination.DiscoverBySkill("Kubernetes")
        assertEquals("Kubernetes", discover.skill)
        assertEquals("Job Discovery", discover.label)
        assertNotNull(discover.icon)

        val learn = Destination.LearnSkill("Kubernetes")
        assertEquals("Kubernetes", learn.skill)
        assertEquals("Prep Studio", learn.label)
        assertNotNull(learn.icon)
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
    fun `v2 career destinations carry labels`() {
        assertEquals("Prep Studio", Destination.PrepStudio.label)
        assertEquals("Pipeline", Destination.Pipeline.label)
        assertEquals("Identity Hub", Destination.IdentityHub.label)
        assertEquals("Company", Destination.CompanyDetail("x").label)
        assertEquals("Provider Setup", Destination.ProviderSetup.label)
        assertEquals("Sign In", Destination.Auth.label)
    }
}
