package com.bangersoul.aivance.core.data.job

import com.bangersoul.aivance.core.common.enums.EmploymentType
import com.bangersoul.aivance.core.common.enums.ExperienceLevel
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side job filtering + relevance scoring.
 *
 * Provider APIs only honour a subset of [JobSearchFilter] (mostly free-text
 * query and location), so the aggregated result set is post-filtered here to
 * guarantee that every filter dimension the user picks is actually respected.
 * This is what turns "random" provider dumps into genuinely filtered results.
 */
@Singleton
class JobFilterMatcher @Inject constructor() {

    /**
     * True when [job] satisfies every populated dimension of [filter].
     */
    fun matches(job: JobListing, filter: JobSearchFilter): Boolean {
        if (filter.query.isNotBlank() && !matchesQuery(job, filter.query)) return false

        // Structured location (country/state/city) OR free-text location.
        if (filter.hasStructuredLocation) {
            if (!matchesStructuredLocation(job, filter)) return false
        } else if (filter.location.isNotBlank()) {
            if (!job.location.contains(filter.location, ignoreCase = true)) return false
        }

        val remoteType = filter.remoteType
        if (remoteType != null && remoteType != RemoteType.OTHER) {
            if (!matchesRemoteType(job, remoteType)) return false
        }

        if (filter.employmentTypes.isNotEmpty() &&
            job.employmentType != EmploymentType.OTHER &&
            filter.employmentTypes.none { it == job.employmentType }
        ) {
            return false
        }

        if (filter.experienceLevels.isNotEmpty() &&
            job.experienceLevel != ExperienceLevel.NOT_SPECIFIED &&
            filter.experienceLevels.none { it == job.experienceLevel }
        ) {
            return false
        }

        if (!matchesExperienceYears(job, filter)) return false

        if (!matchesSalary(job, filter)) return false

        if (!matchesIncludedKeywords(job, filter)) return false
        if (!matchesExcludedKeywords(job, filter)) return false

        return true
    }

    private fun matchesQuery(job: JobListing, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val haystack = listOf(job.title, job.company, job.location, job.description)
            .joinToString(" ")
            .lowercase()
        return terms.all { haystack.contains(it.lowercase()) }
    }

    /**
     * Apply-assist whitelist (R-07): every include keyword must appear in the
     * listing's title/company/description. Mirrors the blacklist/whitelist UX
     * pattern of job-applier tools, reimplemented from scratch.
     */
    private fun matchesIncludedKeywords(job: JobListing, filter: JobSearchFilter): Boolean {
        val terms = filter.includedKeywords.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val haystack = listOf(job.title, job.company, job.description).joinToString(" ").lowercase()
        return terms.all { haystack.contains(it) }
    }

    /**
     * Apply-assist blacklist (R-07): no exclude keyword may appear in the
     * listing's title/company/description (e.g. "unpaid", "commission-only").
     */
    private fun matchesExcludedKeywords(job: JobListing, filter: JobSearchFilter): Boolean {
        val terms = filter.excludedKeywords.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val haystack = listOf(job.title, job.company, job.description).joinToString(" ").lowercase()
        return terms.none { haystack.contains(it) }
    }

    private fun matchesStructuredLocation(job: JobListing, filter: JobSearchFilter): Boolean {
        val jobLoc = job.location.lowercase()
        if (filter.country == "Remote" || filter.location.equals("Remote", ignoreCase = true)) {
            return job.isRemote || job.remoteType == RemoteType.REMOTE || jobLoc.contains("remote")
        }
        val cityMatch = filter.city.isBlank() || jobLoc.contains(filter.city.lowercase())
        val stateMatch = filter.state.isBlank() || jobLoc.contains(filter.state.lowercase())
        val countryMatch = filter.country.isBlank() ||
            jobLoc.contains(filter.country.lowercase()) ||
            (filter.country == "United States" && (jobLoc.contains("us") || jobLoc.contains("usa") || stateMatch || cityMatch)) ||
            (filter.country == "India" && (jobLoc.contains("india") || jobLoc.contains("in") || stateMatch || cityMatch))
        return cityMatch && stateMatch && countryMatch
    }

    /**
     * REMOTE also accepts listings whose description positively signals remote
     * (e.g. "fully remote", "work from home", "home office"), even when the
     * board's structured field says ON_SITE. Boards like Arbeitnow ship most
     * remote-friendly roles with `remote: false` — without this fallback a
     * Remote filter silently drops their entire feed. Explicit negatives
     * ("not remote", "on-site only") never match.
     */
    private fun matchesRemoteType(job: JobListing, remoteType: RemoteType): Boolean {
        return when (remoteType) {
            RemoteType.REMOTE -> job.remoteType == RemoteType.REMOTE || job.isRemote ||
                hasRemoteSignal(job)
            RemoteType.HYBRID -> job.remoteType == RemoteType.HYBRID
            RemoteType.ON_SITE -> job.remoteType == RemoteType.ON_SITE ||
                (job.remoteType == RemoteType.OTHER && !job.isRemote)
            RemoteType.OTHER -> true
        }
    }

    private fun hasRemoteSignal(job: JobListing): Boolean {
        val description = job.description.lowercase()
        if (REMOTE_NEGATIVES.any { description.contains(it) }) return false
        return REMOTE_SIGNALS.any { description.contains(it) }
    }

    private companion object {
        /** Positive remote phrases — bounded to avoid matching incidental uses. */
        val REMOTE_SIGNALS = listOf(
            "fully remote", "remote-first", "remote first", "remote position",
            "remote role", "remote job", "100% remote", "work from home",
            "work-from-home", "work remotely", "remote work", "home office"
        )

        /** Explicit denials that must never count as remote signals. */
        val REMOTE_NEGATIVES = listOf(
            "not remote", "no remote", "non-remote", "not a remote",
            "on-site only", "onsite only", "on site only",
            "in-office only", "in office only"
        )
    }

    /**
     * Numeric experience bounds are mapped to coarse level buckets when the job
     * only exposes an [ExperienceLevel], and to exact years when a numeric field
     * is available. Unknown levels pass only when no numeric bound is set.
     *
     * Each bound is evaluated independently, so a min-only or max-only filter
     * still applies (a common UI shape for "0–15+" experience dropdowns).
     */
    private fun matchesExperienceYears(job: JobListing, filter: JobSearchFilter): Boolean {
        val minYears = filter.minExperienceYears
        val maxYears = filter.maxExperienceYears
        if (minYears == null && maxYears == null) return true

        // Prefer an exact numeric signal if present.
        val years = jobYears(job) ?: levelMidpoint(job.experienceLevel) ?: return true
        if (minYears != null && years < minYears) return false
        if (maxYears != null && years > maxYears) return false
        return true
    }

    private fun jobYears(job: JobListing): Int? {
        // JobListing has no numeric years field today; when it gains one this
        // returns it. Kept as an explicit seam for future providers.
        return null
    }

    /** Midpoint years used to bucket coarse experience levels. */
    private fun levelMidpoint(level: ExperienceLevel): Int? = when (level) {
        ExperienceLevel.ENTRY_LEVEL -> 1
        ExperienceLevel.MID_LEVEL -> 4
        ExperienceLevel.SENIOR_LEVEL -> 7
        ExperienceLevel.EXECUTIVE -> 12
        ExperienceLevel.NOT_SPECIFIED -> null
    }

    private fun matchesSalary(job: JobListing, filter: JobSearchFilter): Boolean {
        val min = filter.minSalary
        val max = filter.maxSalary
        if (min == null && max == null) return true

        // Jobs without salary data pass (lenient by design); each bound is
        // enforced independently so min-only / max-only filters still apply.
        val jobMin = job.salaryMin ?: return true
        val jobMax = job.salaryMax ?: job.salaryMin ?: return true
        if (min != null && jobMax < min) return false
        if (max != null && jobMin > max) return false
        // Overlapping ranges.
        return true
    }

    /**
     * 0..1 relevance score for sorting. Higher = more relevant.
     * Title matches weigh most, followed by company, then description.
     */
    fun relevanceScore(job: JobListing, query: String): Double {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0.0
        val title = job.title.lowercase()
        val company = job.company.lowercase()
        val description = job.description.lowercase()

        var score = 0.0
        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (term in terms) {
            if (title == term) score += 1.0
            else if (title.startsWith(term)) score += 0.8
            else if (title.contains(term)) score += 0.6
            if (company.contains(term)) score += 0.4
            if (description.contains(term)) score += 0.2
        }
        return score
    }

    /**
     * Filters [jobs] by [filter] and sorts: relevance (when a query is present)
     * then recency. Deduplicates by id + url to avoid cross-provider duplicates.
     */
    fun filterAndRank(
        jobs: List<JobListing>,
        filter: JobSearchFilter
    ): List<JobListing> {
        return jobs
            .asSequence()
            .filter { matches(it, filter) }
            .distinctBy { it.id.ifBlank { it.url } }
            .sortedWith { a, b ->
                val relevance = compareBy<JobListing> {
                    -relevanceScore(it, filter.query)
                }
                val recency = compareByDescending<JobListing> { it.postedDate }
                if (filter.query.isNotBlank()) {
                    relevance.then(recency).compare(a, b)
                } else {
                    recency.compare(a, b)
                }
            }
            .toList()
    }
}
