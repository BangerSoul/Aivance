package com.bangersoul.aivance.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceHubCatalogTest {

    @Test
    fun `every category has at least one resource`() {
        val categories = ResourceHubCatalog.categories.map { it.first }
        assertEquals(4, categories.size)
        categories.forEach { category ->
            assertTrue(
                "Category '$category' has no resources",
                ResourceHubCatalog.resourcesFor(category).isNotEmpty()
            )
        }
    }

    @Test
    fun `every resource maps to a known category and a valid URL`() {
        val categories = ResourceHubCatalog.categories.map { it.first }.toSet()
        ResourceHubCatalog.resources.forEach { resource ->
            assertTrue(
                "Unknown category '${resource.category}' on '${resource.title}'",
                resource.category in categories
            )
            assertTrue(
                "Invalid URL for '${resource.title}': ${resource.url}",
                resource.url.startsWith("https://") || resource.url.startsWith("http://")
            )
            assertTrue("Blank title", resource.title.isNotBlank())
        }
    }

    @Test
    fun `catalog covers the core categories promised by R-06`() {
        val titles = ResourceHubCatalog.resources.joinToString("\n") { it.category }.lowercase()
        assertTrue("missing job boards", titles.contains("job boards"))
        assertTrue("missing interview prep", titles.contains("interview prep"))
        assertTrue("missing remote companies", titles.contains("remote companies"))
    }
}
