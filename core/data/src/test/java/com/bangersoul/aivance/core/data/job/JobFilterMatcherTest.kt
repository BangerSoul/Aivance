package com.bangersoul.aivance.core.data.job

import com.bangersoul.aivance.core.common.enums.EmploymentType
import com.bangersoul.aivance.core.common.enums.ExperienceLevel
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JobFilterMatcherTest {

    private lateinit var matcher: JobFilterMatcher

    private fun job(
        id: String = "1",
        title: String = "Android Engineer",
        company: String = "Google",
        location: String = "San Francisco, California, United States",
        employmentType: EmploymentType = EmploymentType.FULL_TIME,
        remoteType: RemoteType = RemoteType.ON_SITE,
        isRemote: Boolean = false,
        experienceLevel: ExperienceLevel = ExperienceLevel.SENIOR_LEVEL,
        salaryMin: Double? = 100_000.0,
        salaryMax: Double? = 150_000.0,
        description: String = "Build Android apps with Kotlin",
        postedDate: Long = 1_000L
    ) = JobListing(
        id = id,
        title = title,
        company = company,
        location = location,
        employmentType = employmentType,
        remoteType = remoteType,
        isRemote = isRemote,
        experienceLevel = experienceLevel,
        salaryMin = salaryMin,
        salaryMax = salaryMax,
        description = description,
        url = "https://example.com/jobs/$id",
        sourceProvider = "test",
        postedDate = postedDate
    )

    @Before
    fun setUp() {
        matcher = JobFilterMatcher()
    }

    @Test
    fun `empty filter matches everything`() {
        assertTrue(matcher.matches(job(), JobSearchFilter()))
    }

    @Test
    fun `query matches title and description`() {
        val filter = JobSearchFilter(query = "kotlin")
        assertTrue(matcher.matches(job(), filter))
        assertFalse(matcher.matches(job(title = "iOS Engineer", description = "Swift only"), filter))
    }

    @Test
    fun `multi-term query requires all terms`() {
        val filter = JobSearchFilter(query = "android kotlin")
        assertTrue(matcher.matches(job(), filter))
        assertFalse(matcher.matches(job(description = "Android only"), filter))
    }

    @Test
    fun `structured location city filters jobs`() {
        val filter = JobSearchFilter(city = "San Francisco")
        assertTrue(matcher.matches(job(), filter))
        assertFalse(matcher.matches(job(location = "New York, New York, United States"), filter))
    }

    @Test
    fun `remote city passes remote jobs`() {
        val filter = JobSearchFilter(city = "Remote")
        assertTrue(matcher.matches(job(location = "Remote"), filter))
    }

    @Test
    fun `remote type filters on-site jobs out`() {
        val filter = JobSearchFilter(remoteType = RemoteType.REMOTE)
        assertFalse(matcher.matches(job(remoteType = RemoteType.ON_SITE), filter))
        assertTrue(matcher.matches(job(remoteType = RemoteType.REMOTE), filter))
        // isRemote flag also satisfies REMOTE
        assertTrue(matcher.matches(job(remoteType = RemoteType.ON_SITE, isRemote = true), filter))
    }

    @Test
    fun `remote filter accepts on-site-flagged jobs whose description signals remote`() {
        val filter = JobSearchFilter(remoteType = RemoteType.REMOTE)
        // Arbeitnow-style listing: board marks remote: false, description says remote.
        assertTrue(matcher.matches(
            job(remoteType = RemoteType.ON_SITE, description = "Fully remote position — work from home anywhere in the EU"),
            filter
        ))
        assertTrue(matcher.matches(
            job(remoteType = RemoteType.ON_SITE, description = "Home office möglich. Join our Berlin team."),
            filter
        ))
    }

    @Test
    fun `remote filter still rejects explicitly on-site listings`() {
        val filter = JobSearchFilter(remoteType = RemoteType.REMOTE)
        assertFalse(matcher.matches(
            job(remoteType = RemoteType.ON_SITE, description = "This is not a remote position — on-site only in Berlin"),
            filter
        ))
        assertFalse(matcher.matches(
            job(remoteType = RemoteType.ON_SITE, description = "On-site only. No home office."),
            filter
        ))
    }

    @Test
    fun `employment type filter respects jobs`() {
        val filter = JobSearchFilter(employmentTypes = listOf(EmploymentType.CONTRACT))
        assertTrue(matcher.matches(job(employmentType = EmploymentType.CONTRACT), filter))
        assertFalse(matcher.matches(job(employmentType = EmploymentType.FULL_TIME), filter))
    }

    @Test
    fun `experience level filter respects jobs`() {
        val filter = JobSearchFilter(experienceLevels = listOf(ExperienceLevel.ENTRY_LEVEL))
        assertTrue(matcher.matches(job(experienceLevel = ExperienceLevel.ENTRY_LEVEL), filter))
        assertFalse(matcher.matches(job(experienceLevel = ExperienceLevel.SENIOR_LEVEL), filter))
    }

    @Test
    fun `experience years bucket filters senior jobs out of entry bucket`() {
        // 0–2 years: ENTRY_LEVEL midpoint is 1 → pass; SENIOR_LEVEL midpoint is 7 → fail.
        val filter = JobSearchFilter(minExperienceYears = 0, maxExperienceYears = 2)
        assertTrue(matcher.matches(job(experienceLevel = ExperienceLevel.ENTRY_LEVEL), filter))
        assertFalse(matcher.matches(job(experienceLevel = ExperienceLevel.SENIOR_LEVEL), filter))
    }

    @Test
    fun `min experience bound alone is enforced`() {
        // Only a minimum set — ENTRY_LEVEL (1) fails, SENIOR_LEVEL (7) passes.
        val filter = JobSearchFilter(minExperienceYears = 5)
        assertFalse(matcher.matches(job(experienceLevel = ExperienceLevel.ENTRY_LEVEL), filter))
        assertTrue(matcher.matches(job(experienceLevel = ExperienceLevel.SENIOR_LEVEL), filter))
    }

    @Test
    fun `max experience bound alone is enforced`() {
        // Only a maximum set — ENTRY_LEVEL (1) passes, EXECUTIVE (12) fails.
        val filter = JobSearchFilter(maxExperienceYears = 5)
        assertTrue(matcher.matches(job(experienceLevel = ExperienceLevel.ENTRY_LEVEL), filter))
        assertFalse(matcher.matches(job(experienceLevel = ExperienceLevel.EXECUTIVE), filter))
    }

    @Test
    fun `salary overlap filter respects job range`() {
        val filter = JobSearchFilter(minSalary = 120_000.0, maxSalary = 180_000.0)
        assertTrue(matcher.matches(job(salaryMin = 100_000.0, salaryMax = 150_000.0), filter))
        assertFalse(matcher.matches(job(salaryMin = 20_000.0, salaryMax = 30_000.0), filter))
    }

    @Test
    fun `min salary bound alone is enforced`() {
        val filter = JobSearchFilter(minSalary = 120_000.0)
        assertTrue(matcher.matches(job(salaryMin = 100_000.0, salaryMax = 150_000.0), filter))
        assertFalse(matcher.matches(job(salaryMin = 20_000.0, salaryMax = 30_000.0), filter))
    }

    @Test
    fun `max salary bound alone is enforced`() {
        val filter = JobSearchFilter(maxSalary = 90_000.0)
        assertTrue(matcher.matches(job(salaryMin = 20_000.0, salaryMax = 80_000.0), filter))
        assertFalse(matcher.matches(job(salaryMin = 100_000.0, salaryMax = 150_000.0), filter))
    }

    @Test
    fun `job without salary data passes salary filters`() {
        val filter = JobSearchFilter(minSalary = 120_000.0, maxSalary = 180_000.0)
        assertTrue(matcher.matches(job(salaryMin = null, salaryMax = null), filter))
    }

    @Test
    fun `included keywords require all terms in the listing`() {
        val filter = JobSearchFilter(includedKeywords = listOf("kotlin", "android"))
        assertTrue(matcher.matches(job(), filter))
        // Kotlin present but no Android signal anywhere → rejected.
        assertFalse(matcher.matches(job(title = "iOS Engineer", description = "Kotlin only"), filter))
        assertFalse(matcher.matches(job(title = "iOS Engineer", description = "Swift"), filter))
    }

    @Test
    fun `included keywords match company and title too`() {
        val filter = JobSearchFilter(includedKeywords = listOf("google"))
        assertTrue(matcher.matches(job(description = "Build widgets"), filter))
        val byTitle = JobSearchFilter(includedKeywords = listOf("android"))
        assertTrue(matcher.matches(job(description = "unrelated"), byTitle))
    }

    @Test
    fun `excluded keywords reject listings containing any term`() {
        val filter = JobSearchFilter(excludedKeywords = listOf("unpaid", "commission-only"))
        assertTrue(matcher.matches(job(), filter))
        assertFalse(matcher.matches(job(description = "Great exposure but unpaid"), filter))
        assertFalse(matcher.matches(job(title = "Commission-only Sales Rep"), filter))
    }

    @Test
    fun `include and exclude combine as whitelist plus blacklist`() {
        val filter = JobSearchFilter(
            includedKeywords = listOf("kotlin"),
            excludedKeywords = listOf("senior")
        )
        assertTrue(matcher.matches(job(), filter))
        // Blacklist wins: contains 'senior' in the title.
        assertFalse(matcher.matches(job(title = "Senior Android Engineer", description = "Kotlin"), filter))
        // Whitelist fails: no Kotlin signal in title/company/description.
        assertFalse(matcher.matches(job(description = "Swift and SwiftUI only"), filter))
    }

    @Test
    fun `keyword matching is case-insensitive and blank-tolerant`() {
        val filter = JobSearchFilter(includedKeywords = listOf("KOTLIN", "  "))
        assertTrue(matcher.matches(job(), filter))
        val blankExclude = JobSearchFilter(excludedKeywords = listOf("", " "))
        assertTrue(matcher.matches(job(title = "Any job"), blankExclude))
    }

    @Test
    fun `filterAndRank sorts by relevance then recency`() {
        // The unrelated job's description must not contain the query, otherwise
        // it legitimately matches via the description haystack.
        val exact = job(id = "exact", title = "Android Engineer", postedDate = 1_000L)
        val partial = job(id = "partial", title = "Junior Android Role", postedDate = 3_000L)
        val unrelated = job(
            id = "unrelated",
            title = "DevOps Engineer",
            description = "CI/CD pipelines and infrastructure automation",
            postedDate = 5_000L
        )

        val ranked = matcher.filterAndRank(
            listOf(partial, unrelated, exact),
            JobSearchFilter(query = "android")
        )

        // Exact title match ranks above a partial one; the unrelated job is filtered out.
        assertEquals(listOf("exact", "partial"), ranked.map { it.id })
    }

    @Test
    fun `filterAndRank deduplicates by id`() {
        val dup = job(id = "dup", title = "Android Engineer")
        val ranked = matcher.filterAndRank(listOf(dup, dup.copy(url = "different")), JobSearchFilter())
        assertEquals(1, ranked.size)
    }

    @Test
    fun `relevance score ranks title matches above description matches`() {
        val titleMatch = job(id = "t", title = "Kotlin Expert")
        val descMatch = job(id = "d", title = "Engineer", description = "Kotlin everywhere")
        assertTrue(matcher.relevanceScore(titleMatch, "kotlin") > matcher.relevanceScore(descMatch, "kotlin"))
    }
}
