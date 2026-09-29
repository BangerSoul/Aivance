package com.bangersoul.aivance.job.mapper

import com.bangersoul.aivance.core.common.enums.EmploymentType
import com.bangersoul.aivance.core.common.enums.ExperienceLevel
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.job.adzuna.dto.AdzunaCompanyDto
import com.bangersoul.aivance.job.adzuna.dto.AdzunaJobDto
import com.bangersoul.aivance.job.adzuna.dto.AdzunaLocationDto
import com.bangersoul.aivance.job.apify.dto.ApifyDatasetItem
import com.bangersoul.aivance.job.arbeitnow.dto.ArbeitnowJobDto
import com.bangersoul.aivance.job.jobicy.dto.JobicyJobDto
import com.bangersoul.aivance.job.remoteok.dto.RemoteOKJobDto
import com.bangersoul.aivance.job.remotive.dto.RemotiveJobDto
import com.bangersoul.aivance.job.usajobs.dto.USAJobsDescriptorDto
import com.bangersoul.aivance.job.usajobs.dto.USAJobsLocationDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobMapperTest {

    @Test
    fun `mapToJobListing from ApifyDatasetItem with all fields`() {
        val item = ApifyDatasetItem(
            id = "apify-1",
            title = "Senior Android Developer",
            company = "TechCorp",
            companyLogo = "https://techcorp.com/logo.png",
            location = "San Francisco, CA",
            salary = "$120k - $150k",
            description = "We need an Android expert.",
            descriptionHtml = "<p>We need an Android expert.</p>",
            url = "https://techcorp.com/jobs/1",
            postedAt = "2026-07-01T00:00:00.000Z",
            type = "full-time",
            experienceLevel = "senior",
            isRemote = true
        )

        val result = JobMapper.mapToJobListing(item, "apify-linkedin")

        assertEquals("apify-1", result.id)
        assertEquals("Senior Android Developer", result.title)
        assertEquals("TechCorp", result.company)
        assertEquals("https://techcorp.com/logo.png", result.companyLogoUrl)
        assertEquals("San Francisco, CA", result.location)
        assertEquals("$120k - $150k", result.salaryRange)
        assertEquals(120000.0, result.salaryMin!!, 0.001)
        assertEquals(150000.0, result.salaryMax!!, 0.001)
        assertEquals(EmploymentType.FULL_TIME, result.employmentType)
        assertEquals(ExperienceLevel.SENIOR_LEVEL, result.experienceLevel)
        assertEquals(RemoteType.REMOTE, result.remoteType)
        assertTrue(result.isRemote)
        assertEquals("We need an Android expert.", result.description)
        assertEquals("<p>We need an Android expert.</p>", result.descriptionHtml)
        assertEquals("https://techcorp.com/jobs/1", result.url)
        assertEquals("apify-linkedin", result.sourceProvider)
        assertEquals("USD", result.currency)
    }

    @Test
    fun `mapToJobListing from real LinkedIn actor schema uses companyName postedDate contractType`() {
        val item = ApifyDatasetItem(
            id = "4059975053",
            title = "General Apply",
            companyName = "LJ Inc.",
            location = "Swartz Creek, MI",
            salary = "",
            description = "Evergreen posting.",
            descriptionHtml = "<p>Evergreen posting.</p>",
            url = "https://www.linkedin.com/jobs/view/general-apply-at-lj-inc-4059975053",
            postedDate = "2024-10-26T00:00:00.000Z",
            contractType = "Full-time",
            experienceLevel = "Not Applicable"
        )

        val result = JobMapper.mapToJobListing(item, "linkedin")

        assertEquals("General Apply", result.title)
        assertEquals("LJ Inc.", result.company)
        assertEquals("Swartz Creek, MI", result.location)
        assertEquals(EmploymentType.FULL_TIME, result.employmentType)
        assertEquals(2024, java.time.Instant.ofEpochMilli(result.postedDate)
            .atZone(java.time.ZoneOffset.UTC).year)
        assertEquals("linkedin", result.sourceProvider)
    }

    @Test
    fun `real Apify actor JSON parses into ApifyDatasetItem`() {
        // Captured from a live curious_coder~linkedin-jobs-scraper run (2026-08-11).
        val raw = """
        {
          "id": "4059975053",
          "url": "https://www.linkedin.com/jobs/view/general-apply-at-lj-inc-4059975053",
          "title": "General Apply",
          "location": "Swartz Creek, MI",
          "postedDate": "2024-10-26T00:00:00.000Z",
          "companyName": "LJ Inc.",
          "companyUrl": "https://www.linkedin.com/company/ljincwedothat",
          "recruiterName": "",
          "experienceLevel": "Not Applicable",
          "contractType": "Full-time",
          "workType": "Other",
          "sector": "Industrial Machinery Manufacturing",
          "salary": "",
          "applyType": "EXTERNAL",
          "postedTimeAgo": "1 year ago",
          "applicationsCount": "Be among the first 25 applicants",
          "description": "LJ Inc. is always interested in hearing from you.",
          "descriptionHtml": "<p>LJ Inc.</p>",
          "applyUrl": ""
        }
        """.trimIndent()

        val item = Json { ignoreUnknownKeys = true }.decodeFromString<ApifyDatasetItem>(raw)

        assertEquals("General Apply", item.title)
        assertEquals("LJ Inc.", item.companyName)
        assertNull(item.company) // legacy key absent in the real schema
        assertEquals("Full-time", item.contractType)
        assertNull(item.type)
        assertEquals("2024-10-26T00:00:00.000Z", item.postedDate)
        assertNull(item.postedAt)
    }

    @Test
    fun `mapToJobListing from ApifyDatasetItem with null fields yields defaults`() {
        val item = ApifyDatasetItem()

        val result = JobMapper.mapToJobListing(item, "apify-test")

        assertNotNull(result.id)
        assertEquals("No Title", result.title)
        assertEquals("Unknown Company", result.company)
        assertEquals("Remote", result.location)
        assertNull(result.salaryRange)
        assertNull(result.salaryMin)
        assertNull(result.salaryMax)
        assertEquals(EmploymentType.FULL_TIME, result.employmentType)
        assertEquals(ExperienceLevel.NOT_SPECIFIED, result.experienceLevel)
        assertEquals(RemoteType.ON_SITE, result.remoteType)
        assertEquals(false, result.isRemote)
    }

    @Test
    fun `mapToJobListing from RemoteOKJobDto with all fields`() {
        val dto = RemoteOKJobDto(
            id = "rok-1",
            company = "StartupXYZ",
            companyLogo = "https://startup.xyz/logo.png",
            position = "Full Stack Developer",
            tags = listOf("react", "kotlin"),
            description = "Build the future.",
            location = "Remote (Global)",
            salaryMin = 80000.0,
            salaryMax = 120000.0,
            date = "2026-06-15",
            url = "https://remoteok.com/remote-jobs/full-stack-developer-1",
            legal = "No visa sponsorship"
        )

        val result = JobMapper.mapToJobListing(dto, "remoteok")

        assertEquals("rok-1", result.id)
        assertEquals("Full Stack Developer", result.title)
        assertEquals("StartupXYZ", result.company)
        assertEquals("https://startup.xyz/logo.png", result.companyLogoUrl)
        assertEquals("Remote (Global)", result.location)
        assertEquals(80000.0, result.salaryMin!!, 0.001)
        assertEquals(120000.0, result.salaryMax!!, 0.001)
        assertEquals(EmploymentType.FULL_TIME, result.employmentType)
        assertEquals(RemoteType.REMOTE, result.remoteType)
        assertTrue(result.isRemote)
        assertEquals("Build the future.", result.description)
        assertEquals("https://remoteok.com/remote-jobs/full-stack-developer-1", result.url)
        assertEquals("remoteok", result.sourceProvider)
    }

    @Test
    fun `mapToJobListing from RemoteOKJobDto with null id generates UUID`() {
        val dto = RemoteOKJobDto(position = "Dev", company = "Co")

        val result = JobMapper.mapToJobListing(dto, "remoteok")

        assertNotNull(result.id)
        assertTrue(result.id.isNotEmpty())
    }

    @Test
    fun `mapToJobListing from RemotiveJobDto with all fields`() {
        val dto = RemotiveJobDto(
            id = 42L,
            url = "https://remotive.com/job/42",
            title = "Backend Engineer",
            companyName = "DataFlow",
            companyLogo = "https://dataflow.io/logo.png",
            category = "Engineering",
            tags = listOf("python", "aws"),
            jobType = "full_time",
            publicationDate = "2026-07-10T14:30:00",
            candidateLocation = "Worldwide",
            salary = "$130k",
            description = "Backend role at DataFlow."
        )

        val result = JobMapper.mapToJobListing(dto, "remotive")

        assertEquals("42", result.id)
        assertEquals("Backend Engineer", result.title)
        assertEquals("DataFlow", result.company)
        assertEquals("https://dataflow.io/logo.png", result.companyLogoUrl)
        assertEquals("Worldwide", result.location)
        assertEquals(EmploymentType.FULL_TIME, result.employmentType)
        assertEquals(RemoteType.REMOTE, result.remoteType)
        assertTrue(result.isRemote)
        assertEquals("Backend role at DataFlow.", result.description)
        assertEquals("https://remotive.com/job/42", result.url)
        assertEquals("remotive", result.sourceProvider)
    }

    @Test
    fun `mapToJobListing from RemotiveJobDto with null fields`() {
        val dto = RemotiveJobDto()

        val result = JobMapper.mapToJobListing(dto, "remotive")

        assertNotNull(result.id)
        assertEquals("No Title", result.title)
        assertEquals("Unknown Company", result.company)
        assertEquals("Remote", result.location)
        assertEquals(EmploymentType.FULL_TIME, result.employmentType)
        assertEquals(RemoteType.REMOTE, result.remoteType)
        assertTrue(result.isRemote)
    }

    @Test
    fun `mapToJobListing from ArbeitnowJobDto with all fields`() {
        val dto = ArbeitnowJobDto(
            slug = "android-dev-berlin-123",
            companyName = "TechBerlin",
            title = "Android Developer",
            description = "Kotlin role in Berlin.",
            remote = true,
            url = "https://www.arbeitnow.com/jobs/android-dev-berlin-123",
            tags = buildJsonArray { add(JsonPrimitive("android")); add(JsonPrimitive("kotlin")) },
            jobTypes = listOf("full-time"),
            location = "Berlin",
            createdAt = 1785499244
        )

        val result = JobMapper.mapToJobListing(dto, "arbeitnow")

        assertEquals("android-dev-berlin-123", result.id)
        assertEquals("Android Developer", result.title)
        assertEquals("TechBerlin", result.company)
        assertEquals("Berlin", result.location)
        assertEquals(EmploymentType.FULL_TIME, result.employmentType)
        assertEquals(RemoteType.REMOTE, result.remoteType)
        assertTrue(result.isRemote)
        assertEquals("Kotlin role in Berlin.", result.description)
        assertEquals("https://www.arbeitnow.com/jobs/android-dev-berlin-123", result.url)
        assertEquals("arbeitnow", result.sourceProvider)
        assertEquals(1785499244000L, result.postedDate)
    }

    @Test
    fun `mapToJobListing from ArbeitnowJobDto with null fields yields defaults`() {
        val dto = ArbeitnowJobDto()

        val result = JobMapper.mapToJobListing(dto, "arbeitnow")

        assertNotNull(result.id)
        assertEquals("No Title", result.title)
        assertEquals("Unknown Company", result.company)
        assertEquals("Germany", result.location)
        assertEquals(RemoteType.ON_SITE, result.remoteType)
        assertTrue("Posted date should be valid", result.postedDate > 0)
    }

    @Test
    fun `mapToJobListing from JobicyJobDto with all fields`() {
        val dto = JobicyJobDto(
            id = 147706L,
            url = "https://jobicy.com/jobs/147706",
            jobSlug = "147706-account-lead",
            jobTitle = "Account Technical Lead",
            companyName = "Autodesk",
            companyLogo = "https://jobicy.com/logo.png",
            jobIndustry = listOf("Technical Support"),
            jobType = listOf("Full-Time"),
            jobGeo = "APAC, Australia",
            jobLevel = "Senior",
            jobExcerpt = "Leads technical engagements.",
            jobDescription = "<p>Full description</p>",
            pubDate = "2026-07-30T19:45:05+00:00"
        )

        val result = JobMapper.mapToJobListing(dto, "jobicy")

        assertEquals("147706", result.id)
        assertEquals("Account Technical Lead", result.title)
        assertEquals("Autodesk", result.company)
        assertEquals("https://jobicy.com/logo.png", result.companyLogoUrl)
        assertEquals("APAC, Australia", result.location)
        assertEquals(EmploymentType.FULL_TIME, result.employmentType)
        assertEquals(RemoteType.REMOTE, result.remoteType)
        assertTrue(result.isRemote)
        assertEquals("<p>Full description</p>", result.descriptionHtml)
        assertEquals("https://jobicy.com/jobs/147706", result.url)
        assertEquals("jobicy", result.sourceProvider)
    }

    @Test
    fun `mapToJobListing from JobicyJobDto with null fields yields defaults`() {
        val dto = JobicyJobDto()

        val result = JobMapper.mapToJobListing(dto, "jobicy")

        assertNotNull(result.id)
        assertEquals("No Title", result.title)
        assertEquals("Unknown Company", result.company)
        assertEquals("Remote", result.location)
        assertTrue(result.isRemote)
    }

    @Test
    fun `mapToJobListing from AdzunaJobDto with all fields`() {
        val dto = AdzunaJobDto(
            id = "adz-1",
            title = "Backend Engineer",
            company = AdzunaCompanyDto(displayName = "CloudCorp", logo = "https://cloudcorp.com/logo.png"),
            location = AdzunaLocationDto(displayName = "Remote, US"),
            salaryMin = 110000.0,
            salaryMax = 140000.0,
            description = "Backend role.",
            redirectUrl = "https://www.adzuna.com/land/ad/adz-1",
            created = "2026-07-20T10:00:00Z",
            contractType = "full_time"
        )

        val result = JobMapper.mapToJobListing(dto, "adzuna", "us")

        assertEquals("adz-1", result.id)
        assertEquals("Backend Engineer", result.title)
        assertEquals("CloudCorp", result.company)
        assertEquals("https://cloudcorp.com/logo.png", result.companyLogoUrl)
        assertEquals("Remote, US", result.location)
        assertEquals(110000.0, result.salaryMin!!, 0.001)
        assertEquals(140000.0, result.salaryMax!!, 0.001)
        assertEquals("USD", result.currency)
        assertEquals(EmploymentType.FULL_TIME, result.employmentType)
        assertEquals(RemoteType.REMOTE, result.remoteType)
        assertTrue(result.isRemote)
        assertEquals("https://www.adzuna.com/land/ad/adz-1", result.url)
        assertEquals("adzuna", result.sourceProvider)
    }

    @Test
    fun `mapToJobListing from AdzunaJobDto uses country currency`() {
        val dto = AdzunaJobDto(id = "adz-2", title = "Engineer")

        val gbResult = JobMapper.mapToJobListing(dto, "adzuna", "gb")
        val deResult = JobMapper.mapToJobListing(dto, "adzuna", "de")

        assertEquals("GBP", gbResult.currency)
        assertEquals("EUR", deResult.currency)
    }

    @Test
    fun `mapToJobListing from USAJobsDescriptorDto with all fields`() {
        val dto = USAJobsDescriptorDto(
            positionId = "usa-1",
            positionTitle = "Software Engineer",
            organizationName = "Department of Defense",
            positions = listOf(USAJobsLocationDto(locationName = "Remote, Arlington, VA")),
            schedules = listOf(com.bangersoul.aivance.job.usajobs.dto.USAJobsLabelDto(name = "Full-time")),
            offeringTypes = listOf(com.bangersoul.aivance.job.usajobs.dto.USAJobsLabelDto(name = "Permanent")),
            minimumRange = 90000.0,
            maximumRange = 120000.0,
            qualificationSummary = "Degree required.",
            applyUri = "https://www.usajobs.gov/GetJob/ViewDetails/1",
            startDate = "2026-07-01T00:00:00"
        )

        val result = JobMapper.mapToJobListing(dto, "usajobs")

        assertEquals("usa-1", result.id)
        assertEquals("Software Engineer", result.title)
        assertEquals("Department of Defense", result.company)
        assertEquals("Remote, Arlington, VA", result.location)
        assertEquals(90000.0, result.salaryMin!!, 0.001)
        assertEquals(120000.0, result.salaryMax!!, 0.001)
        assertEquals("USD", result.currency)
        assertEquals(EmploymentType.FULL_TIME, result.employmentType)
        assertTrue(result.isRemote)
        assertEquals("Degree required.", result.description)
        assertEquals("https://www.usajobs.gov/GetJob/ViewDetails/1", result.url)
        assertEquals("usajobs", result.sourceProvider)
    }

    @Test
    fun `mapToJobListing from USAJobsDescriptorDto with null fields yields defaults`() {
        val dto = USAJobsDescriptorDto()

        val result = JobMapper.mapToJobListing(dto, "usajobs")

        assertNotNull(result.id)
        assertEquals("No Title", result.title)
        assertEquals("US Government", result.company)
        assertEquals("United States", result.location)
        assertEquals("USD", result.currency)
    }

    @Test
    fun `parseSalary extracts min correctly`() {
        assertEquals(100000.0, JobMapper.parseSalary("$100k - $150k", true)!!, 0.001)
        assertEquals(80.0, JobMapper.parseSalary("$80 - $120", true)!!, 0.001)
        assertEquals(100000.0, JobMapper.parseSalary("100k - 150k", true)!!, 0.001)
        assertNull(JobMapper.parseSalary(null, true))
        assertNull(JobMapper.parseSalary("Negotiable", true))
    }

    @Test
    fun `parseSalary extracts max correctly`() {
        assertEquals(150000.0, JobMapper.parseSalary("$100k - $150k", false)!!, 0.001)
        assertEquals(120.0, JobMapper.parseSalary("$80 - $120", false)!!, 0.001)
        assertEquals(150000.0, JobMapper.parseSalary("100k - 150k", false)!!, 0.001)
        assertNull(JobMapper.parseSalary(null, false))
    }

    @Test
    fun `parseSalary handles k suffix`() {
        assertEquals(100000.0, JobMapper.parseSalary("100k", true)!!, 0.001)
        assertEquals(200000.0, JobMapper.parseSalary("200K", true)!!, 0.001)
    }

    @Test
    fun `parseDate handles multiple formats`() {
        assertTrue("ISO8601 timestamp should be positive", JobMapper.parseDate("2026-07-01T00:00:00.000Z") > 0)
        assertTrue("Simple date should be positive", JobMapper.parseDate("2026-06-15") > 0)
        assertTrue("Date time should be positive", JobMapper.parseDate("2026-07-10T14:30:00") > 0)
    }

    @Test
    fun `parseDate returns valid timestamp for null or invalid input`() {
        val result = JobMapper.parseDate(null)
        assertTrue("Expected valid timestamp for null, got $result", result > 0 && result < 2000000000000)

        val invalidResult = JobMapper.parseDate("not-a-date")
        assertTrue("Expected valid timestamp for invalid date, got $invalidResult", invalidResult > 0 && invalidResult < 2000000000000)
    }
}
