package com.bangersoul.aivance.core.common.model

import com.bangersoul.aivance.core.common.enums.RemotePolicy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One company in the bundled remote-company catalog (R-02).
 *
 * The JSON snapshot mirrors the remoteintech/remote-jobs dataset fields
 * (ISC-licensed) — see `refresh_company_catalog.py` for the generator. Parsed
 * from `core:data`'s `company_catalog.json` asset; used to filter discovery by
 * remote policy / tech stack and to enrich the company detail screen.
 */
@Serializable
data class CompanyCatalogEntry(
    val name: String,
    val website: String? = null,
    @SerialName("careers_url") val careersUrl: String? = null,
    val region: String? = null,
    @SerialName("remote_policy") val remotePolicy: String? = null,
    @SerialName("company_size") val companySize: String? = null,
    val technologies: List<String> = emptyList()
) {
    /** Registry domain (hostname without scheme/www) for name-independent lookup. */
    val domain: String?
        get() = website?.let { extractDomain(it) }

    val policy: RemotePolicy
        get() = RemotePolicy.fromDatasetString(remotePolicy)

    companion object {
        /** Normalized name key: lowercase, non-alphanumeric stripped. */
        fun normalizeName(name: String): String =
            name.lowercase().filter { it.isLetterOrDigit() }

        fun extractDomain(website: String): String? {
            val trimmed = website.trim()
            val withoutScheme = trimmed.substringAfter("://", trimmed)
            val host = withoutScheme.substringBefore('/').substringBefore(':')
            return host.removePrefix("www.").ifBlank { null }?.lowercase()
        }
    }
}
