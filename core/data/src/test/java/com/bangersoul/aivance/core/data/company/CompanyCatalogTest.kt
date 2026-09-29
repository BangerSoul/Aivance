package com.bangersoul.aivance.core.data.company

import com.bangersoul.aivance.core.common.enums.RemotePolicy
import com.bangersoul.aivance.core.common.model.CompanyCatalogEntry
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompanyCatalogTest {

    private val sampleJson = """
        [
          {
            "name": "Automattic",
            "website": "https://automattic.com/",
            "careers_url": "https://automattic.com/work-with-us/",
            "region": "worldwide",
            "remote_policy": "fully-remote",
            "company_size": "enterprise",
            "technologies": ["java", "javascript", "php", "python", "sql"]
          },
          {
            "name": "GitLab Inc.",
            "website": "https://about.gitlab.com/",
            "remote_policy": "remote-first",
            "technologies": ["ruby", "devops"]
          },
          {
            "name": "Shopify",
            "website": "https://www.shopify.com/",
            "remote_policy": "remote-friendly",
            "company_size": "large",
            "technologies": ["ruby", "kotlin"]
          },
          {
            "name": "Fully Hybrid Co",
            "website": "https://hybrid.example.com",
            "remote_policy": "hybrid",
            "technologies": []
          }
        ]
    """.trimIndent()

    private val catalog: CompanyCatalog = CompanyCatalog.fromJson(sampleJson)

    // --- Parsing ---

    @Test
    fun `parse tolerates unknown fields and missing optionals`() {
        val entries = CompanyCatalog.parse(
            """
            [{"name": "Acme", "website": "https://acme.com", "some_future_field": 42}]
            """.trimIndent()
        )
        assertEquals(1, entries.size)
        assertEquals("Acme", entries.single().name)
        assertNull(entries.single().remotePolicy)
        assertTrue(entries.single().technologies.isEmpty())
        assertEquals(RemotePolicy.UNKNOWN, entries.single().policy)
    }

    @Test
    fun `fromJson builds lookup indexes`() {
        assertEquals(4, catalog.size)
        assertNotNull(catalog.findByName("automattic"))
        assertNotNull(catalog.findByName("Shopify"))
    }

    // --- Lookup ---

    @Test
    fun `findByName normalizes case and punctuation`() {
        assertNotNull(catalog.findByName("GITLAB INC"))
        assertNotNull(catalog.findByName("GitLab, Inc."))
        assertNull(catalog.findByName("Nope Corp"))
    }

    @Test
    fun `findByDomain strips scheme and www`() {
        assertEquals("GitLab Inc.", catalog.findByDomain("about.gitlab.com")?.name)
        assertEquals("Shopify", catalog.findByDomain("https://www.shopify.com")?.name)
        assertNull(catalog.findByDomain("unknown.dev"))
    }

    @Test
    fun `extractDomain handles edge cases`() {
        assertEquals("acme.com", CompanyCatalogEntry.extractDomain("https://acme.com/path"))
        assertEquals("acme.com", CompanyCatalogEntry.extractDomain("http://www.acme.com"))
        assertEquals("acme.com", CompanyCatalogEntry.extractDomain("acme.com"))
        assertNull(CompanyCatalogEntry.extractDomain(""))
        assertNull(CompanyCatalogEntry.extractDomain("https://"))
    }

    // --- Remote policy mapping ---

    @Test
    fun `remote policy maps dataset strings`() {
        assertEquals(RemotePolicy.FULLY_REMOTE, RemotePolicy.fromDatasetString("fully-remote"))
        assertEquals(RemotePolicy.REMOTE_FIRST, RemotePolicy.fromDatasetString("remote-first"))
        assertEquals(RemotePolicy.REMOTE_FRIENDLY, RemotePolicy.fromDatasetString("remote-friendly"))
        assertEquals(RemotePolicy.HYBRID, RemotePolicy.fromDatasetString("hybrid"))
        assertEquals(RemotePolicy.UNKNOWN, RemotePolicy.fromDatasetString("office-first"))
        assertEquals(RemotePolicy.UNKNOWN, RemotePolicy.fromDatasetString(null))
        assertEquals(RemotePolicy.HYBRID, RemotePolicy.fromDatasetString("  Hybrid  "))
    }

    // --- accepts() ---

    @Test
    fun `accepts always true when no catalog dimensions set`() {
        assertTrue(catalog.accepts("Some Unknown Company", JobSearchFilter()))
    }

    @Test
    fun `accepts matches fully remote bucket`() {
        val filter = JobSearchFilter(remotePolicy = RemotePolicy.FULLY_REMOTE)
        assertTrue(catalog.accepts("Automattic", filter))
        // remote-first is the same distributed-first bucket.
        assertTrue(catalog.accepts("GitLab Inc.", filter))
        assertFalse(catalog.accepts("Shopify", filter))
        assertFalse(catalog.accepts("Fully Hybrid Co", filter))
    }

    @Test
    fun `accepts matches remote friendly and hybrid exactly`() {
        assertTrue(catalog.accepts("Shopify", JobSearchFilter(remotePolicy = RemotePolicy.REMOTE_FRIENDLY)))
        assertFalse(catalog.accepts("Automattic", JobSearchFilter(remotePolicy = RemotePolicy.REMOTE_FRIENDLY)))
        assertTrue(catalog.accepts("Fully Hybrid Co", JobSearchFilter(remotePolicy = RemotePolicy.HYBRID)))
        assertFalse(catalog.accepts("Automattic", JobSearchFilter(remotePolicy = RemotePolicy.HYBRID)))
    }

    @Test
    fun `accepts rejects unknown companies when policy filter set`() {
        assertFalse(catalog.accepts("Mystery Startup", JobSearchFilter(remotePolicy = RemotePolicy.FULLY_REMOTE)))
    }

    @Test
    fun `accepts matches technology intersection`() {
        val filter = JobSearchFilter(technologies = listOf("kotlin"))
        assertTrue(catalog.accepts("Shopify", filter))
        assertFalse(catalog.accepts("Automattic", filter))

        val multi = JobSearchFilter(technologies = listOf("ruby", "devops"))
        assertTrue(catalog.accepts("GitLab Inc.", multi))
        assertTrue(catalog.accepts("Shopify", multi)) // ruby intersects
    }

    @Test
    fun `accepts rejects unknown companies when tech filter set`() {
        assertFalse(catalog.accepts("Mystery Startup", JobSearchFilter(technologies = listOf("kotlin"))))
    }

    @Test
    fun `accepts rejects company with empty tech list`() {
        assertFalse(catalog.accepts("Fully Hybrid Co", JobSearchFilter(technologies = listOf("kotlin"))))
    }

    @Test
    fun `accepts combines policy and technology dimensions`() {
        val filter = JobSearchFilter(
            remotePolicy = RemotePolicy.REMOTE_FRIENDLY,
            technologies = listOf("kotlin")
        )
        assertTrue(catalog.accepts("Shopify", filter))
        assertFalse(catalog.accepts("Automattic", filter)) // wrong policy
        assertFalse(catalog.accepts("GitLab Inc.", filter)) // wrong policy (distributed-first)
    }

    // --- Bundled snapshot integrity ---

    @Test
    fun `bundled snapshot parses with expected entries`() {
        // Unit tests run with the module dir as working directory, so the
        // generated asset is reachable from the source tree. Guards the
        // snapshot produced by refresh_company_catalog.py against corruption.
        val file = File("src/main/assets/company_catalog.json")
        assertTrue("company_catalog.json asset missing — run refresh_company_catalog.py", file.exists())
        val snapshot = CompanyCatalog.fromJson(file.readText())
        assertTrue("snapshot should index hundreds of companies", snapshot.size > 500)
        val automattic = snapshot.findByName("Automattic")
        assertNotNull(automattic)
        assertEquals(RemotePolicy.FULLY_REMOTE, automattic?.policy)
        assertEquals("https://automattic.com/work-with-us/", automattic?.careersUrl)
    }
}
