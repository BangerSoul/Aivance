package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.model.Company
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.data.company.CompanyCatalog
import com.bangersoul.aivance.core.data.job.JobFilterMatcher
import com.bangersoul.aivance.core.data.job.JobNormalizer
import com.bangersoul.aivance.core.data.mapper.toDomain
import com.bangersoul.aivance.core.data.mapper.toEntity
import com.bangersoul.aivance.core.database.dao.CompanyDao
import com.bangersoul.aivance.core.database.dao.JobDao
import com.bangersoul.aivance.core.database.model.CompanyEntity
import com.bangersoul.aivance.core.database.model.SavedJobEntity
import com.bangersoul.aivance.core.database.model.ViewedJobEntity
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.repository.ProviderRepository
import com.bangersoul.aivance.sdk.api.EnrichmentProvider
import com.bangersoul.aivance.sdk.api.JobProvider
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobRepositoryImpl @Inject constructor(
    private val jobDao: JobDao,
    private val companyDao: CompanyDao,
    private val providerRegistry: ProviderRegistry,
    private val normalizer: JobNormalizer,
    private val filterMatcher: JobFilterMatcher,
    private val companyCatalog: CompanyCatalog,
    private val providerRepository: ProviderRepository
) : JobRepository {

    override fun getJobs(): Flow<CoreResult<List<JobListing>>> {
        return jobDao.getJobsWithDetails().map { entities ->
            runCatchingCore { entities.map { it.toDomain() } }
        }
    }

    override suspend fun searchJobs(
        filter: JobSearchFilter,
        sortOrder: JobSortOrder
    ): CoreResult<List<JobListing>> = coroutineScope {
        runCatchingCore {
            // Multi-provider selection: only providers the user has enabled run.
            // A provider with no persisted config is enabled by default (keyless
            // boards like Arbeitnow/RemoteOK work with zero setup); a provider
            // explicitly toggled off in Provider Management is skipped even if
            // it still carries valid credentials.
            val savedConfigs = providerRepository.getProviderConfigs().firstOrNull() ?: emptyList()
            val disabledIds = savedConfigs
                .filter { it.settings["isEnabled"]?.toBoolean() == false }
                .map { it.providerId }
                .toSet()
            val providers = providerRegistry.getAllProviders()
                .filterIsInstance<JobProvider>()
                .filter { it.metadata.id !in disabledIds }
                .filter { it.status == com.bangersoul.aivance.sdk.core.ProviderStatus.Active ||
                           it.status == com.bangersoul.aivance.sdk.core.ProviderStatus.Ready }

            val deferredResults = providers.map { provider ->
                async { provider to provider.searchJobs(filter, sortOrder, 1) }
            }

            val results = deferredResults.awaitAll()
            val providerResults = results.flatMap { (provider, result) ->
                when (result) {
                    is Result.Success -> result.data.map { normalizer.normalize(provider.metadata.id, it) }
                    is Result.Failure -> emptyList()
                }
            }

            // Truthful failure semantics (no seed/cache masquerade): if every
            // queried provider errored and produced nothing, surface the failure
            // instead of silently dumping the entire local jobs table dressed up
            // as fresh results. The per-provider layer (RestJobProvider) already
            // returns each provider's OWN previously-cached listings on a
            // transient network failure — those come back as Result.Success and
            // flow through normally — so reaching here with all-failures means
            // there was genuinely nothing (fresh/offline, no cache) to serve.
            // An empty result from a provider that *succeeded* stays empty: "no
            // matches" is a real, truthful answer, not an error.
            val allProvidersFailed = providers.isNotEmpty() && results.none { (_, r) -> r is Result.Success }
            if (allProvidersFailed && providerResults.isEmpty()) {
                throw Exception("All job providers failed. Check your connection and try again.")
            }

            val aggregated = providerResults

            // Client-side filtering: provider APIs only honour a subset of the
            // filter (mostly query + location), so apply every dimension here to
            // guarantee results actually respect the user's filters. Also ranks
            // by relevance when a query is present and dedups across providers.
            val filtered = filterMatcher.filterAndRank(aggregated, filter)
                // R-02 catalog dimensions (remote policy / tech stack) can't be
                // known from a listing alone — they come from the bundled
                // remote-company catalog, so apply them after the listing-level
                // matcher. Unknown companies fail catalog-filtered searches.
                .filter { companyCatalog.accepts(it.company, filter) }

            // Cache in background and remap each listing's id to the internal
            // DB row id. The Jobs list then hands getJobById an id it can
            // resolve from the local DB immediately — otherwise tapping a result
            // would fall through to the providers (which rarely answer for an
            // external id) and surface a "Job not found" error.
            val withDbIds = filtered.map { job ->
                val existing = jobDao.getJobByUrl(job.url)
                val dbId = if (existing != null) {
                    // Refresh the cached row with the freshest listing data
                    // under the same internal id, so the detail screen never
                    // disagrees with the card the user just tapped.
                    val companyId = companyDao.insertCompany(CompanyEntity(
                        name = job.company,
                        logoUrl = job.companyLogoUrl,
                        website = null,
                        industry = null,
                        domain = null,
                        headquarters = null,
                        socialLinks = emptyMap()
                    ))
                    jobDao.insertJob(job.copy(id = existing.id.toString()).toEntity(companyId))
                    existing.id
                } else {
                    val companyId = companyDao.insertCompany(CompanyEntity(
                        name = job.company,
                        logoUrl = job.companyLogoUrl,
                        website = null,
                        industry = null,
                        domain = null,
                        headquarters = null,
                        socialLinks = emptyMap()
                    ))
                    jobDao.insertJob(job.toEntity(companyId))
                }
                job.copy(id = dbId.toString())
            }

            withDbIds
        }
    }

    override suspend fun getJobById(id: String): CoreResult<JobListing> = runCatchingCore {
        // 1. Try DB first (internal ID or previously cached external ID mapped to internal)
        val longId = id.toLongOrNull()
        if (longId != null) {
            val dbJob = jobDao.getJobWithDetailsById(longId)?.toDomain()
            if (dbJob != null) return@runCatchingCore dbJob
        }

        // 1b. External id (non-numeric) — try the URL lookup so deep links and
        //     provider ids that never got remapped still resolve from cache.
        if (longId == null) {
            jobDao.getJobByUrl(id)?.let { entity ->
                jobDao.getJobWithDetailsById(entity.id)?.let { dbJob ->
                    return@runCatchingCore dbJob.toDomain()
                }
            }
        }

        // 2. Try all Job Providers (some might have it in their instance cache)
        val providers = providerRegistry.getAllProviders().filterIsInstance<JobProvider>()
        for (provider in providers) {
            val result = provider.getJobDetails(id)
            if (result is Result.Success) {
                // Found it! Enrich it before returning.
                val enriched = enrichJobListing(result.data)

                // Cache it in DB for future use
                val companyId = companyDao.insertCompany(Company(
                    id = "0",
                    name = enriched.company,
                    logoUrl = enriched.companyLogoUrl
                ).toEntity())
                jobDao.insertJob(enriched.toEntity(companyId))

                return@runCatchingCore enriched
            }
        }

        throw Exception("Job not found: $id")
    }

    private suspend fun enrichJobListing(job: JobListing): JobListing {
        val enrichmentProvider = providerRegistry.getProvidersByCapability(com.bangersoul.aivance.sdk.core.ProviderCapability.RecruiterDiscovery)
            .filterIsInstance<com.bangersoul.aivance.sdk.api.EnrichmentProvider>()
            .firstOrNull { it.status == com.bangersoul.aivance.sdk.core.ProviderStatus.Active ||
                           it.status == com.bangersoul.aivance.sdk.core.ProviderStatus.Ready }
            ?: return job

        val company = com.bangersoul.aivance.core.common.model.Company(
            id = "0",
            name = job.company,
            logoUrl = job.companyLogoUrl
        )

        return when (val result = enrichmentProvider.enrichCompany(company)) {
            is Result.Success -> {
                job.copy(
                    company = result.data.name,
                    companyLogoUrl = result.data.logoUrl ?: job.companyLogoUrl
                )
            }
            is Result.Failure -> job
        }
    }

    override fun getSavedJobs(): Flow<CoreResult<List<JobListing>>> {
        return jobDao.getJobsWithDetails().map { allJobs ->
            val savedIds = jobDao.getSavedJobIds().firstOrNull() ?: emptyList()
            runCatchingCore {
                allJobs.filter { savedIds.contains(it.job.id) }.map { it.toDomain() }
            }
        }
    }

    override suspend fun toggleBookmark(jobId: String): CoreResult<Boolean> = runCatchingCore {
        val id = jobId.toLongOrNull() ?: throw Exception("Invalid ID")
        val isSaved = jobDao.isJobSaved(id)
        if (isSaved) {
            jobDao.deleteSavedJob(SavedJobEntity(id))
            false
        } else {
            jobDao.insertSavedJob(SavedJobEntity(id))
            true
        }
    }

    override suspend fun markAsViewed(jobId: String): CoreResult<Unit> = runCatchingCore {
        val id = jobId.toLongOrNull() ?: throw Exception("Invalid ID")
        jobDao.insertViewedJob(ViewedJobEntity(id))
    }

    override suspend fun cacheJob(job: JobListing): CoreResult<Long> = runCatchingCore {
        val companyId = companyDao.insertCompany(CompanyEntity(
            name = job.company,
            logoUrl = job.companyLogoUrl,
            website = null,
            industry = null,
            domain = null,
            headquarters = null,
            socialLinks = emptyMap()
        ))

        // Resolve the stable DB id for this job BEFORE inserting. jobDao.insertJob
        // is now @Upsert, which returns the new rowid only on a fresh insert and
        // -1L on the ON CONFLICT DO UPDATE path. Callers (JobDetailsViewModel's
        // apply/cover-letter flows) use this return value as the applications /
        // cover-letter foreign key, so returning -1 would produce an FK violation
        // or an unresolvable "Job not found". Reuse the existing row's id when the
        // job is already cached — by provider URL first (the canonical dedupe key),
        // then by a numeric id that already resolves — so re-caching updates in
        // place and always hands back a valid jobs.id.
        val existingId = job.url.takeIf { it.isNotBlank() }?.let { jobDao.getJobByUrl(it)?.id }
            ?: job.id.toLongOrNull()?.takeIf { jobDao.getJobById(it) != null }

        if (existingId != null) {
            jobDao.insertJob(job.copy(id = existingId.toString()).toEntity(companyId))
            existingId
        } else {
            jobDao.insertJob(job.toEntity(companyId))
        }
    }
}
