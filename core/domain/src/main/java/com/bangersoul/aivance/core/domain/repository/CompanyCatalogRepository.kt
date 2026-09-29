package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.model.CompanyCatalogEntry

/**
 * Lookup access to the bundled remote-company catalog (R-02) — the ISC-licensed
 * remoteintech/remote-jobs snapshot shipped as an app asset. Provides
 * enrichment data (remote policy, size, region, technologies, careers URL) for
 * company views; discovery filtering consumes the catalog inside the job
 * repository itself.
 *
 * Implementations are in-memory (the snapshot is parsed once at startup), so
 * these are plain lookups rather than suspend calls.
 */
interface CompanyCatalogRepository {

    /** Number of indexed companies. */
    val size: Int

    /** Catalog entry by (normalized) company name, or null when not indexed. */
    fun findCompany(name: String): CompanyCatalogEntry?

    /** Catalog entry by registry domain (hostname without scheme/www). */
    fun findCompanyByDomain(domain: String): CompanyCatalogEntry?
}
