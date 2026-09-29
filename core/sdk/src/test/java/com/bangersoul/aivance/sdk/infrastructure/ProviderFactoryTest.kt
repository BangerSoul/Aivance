package com.bangersoul.aivance.sdk.infrastructure

import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.BaseProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProviderFactoryTest {

    private class FakeProvider : BaseProvider(
        metadata = ProviderMetadata(
            id = "demo",
            name = "Demo",
            type = ProviderType.JOB,
            version = "1.0.0",
            description = "test provider",
            author = "test"
        ),
        capabilities = setOf(ProviderCapability.JobSearch)
    ) {
        override suspend fun onInitialize() {}
        override suspend fun onStart() {}
        override suspend fun onStop() {}
        override suspend fun onDispose() {}
    }

    private var capturedConfig: Map<String, Any>? = null

    private fun createFactory() = ProviderFactory(
        mapOf(
            "demo" to ProviderFactory.Factory { config ->
                capturedConfig = config
                FakeProvider()
            }
        )
    )

    @Test
    fun `typed createProvider routes settings and secrets to the factory`() {
        val factory = createFactory()
        val config = ProviderConfiguration(
            providerId = "demo",
            settings = mapOf("appId" to "1234", "isEnabled" to "true"),
            secrets = mapOf("appKey" to "sk-secret")
        )

        val provider = factory.createProvider(config)

        assertTrue(provider is FakeProvider)
        assertEquals("demo", provider.metadata.id)
        assertEquals(mapOf("appId" to "1234", "isEnabled" to "true"), capturedConfig?.get("settings"))
        assertEquals(mapOf("appKey" to "sk-secret"), capturedConfig?.get("secrets"))
    }

    @Test
    fun `map-based createProvider still works with a raw config map`() {
        val factory = createFactory()

        factory.createProvider("demo", mapOf("settings" to emptyMap<String, String>()))

        assertTrue(capturedConfig != null)
    }

    @Test
    fun `createProvider with unknown type throws IllegalArgumentException`() {
        val factory = createFactory()
        try {
            factory.createProvider("nope")
            fail("Expected IllegalArgumentException for unknown provider type")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `hasFactory reflects injected and custom factories`() {
        val factory = createFactory()
        assertTrue(factory.hasFactory("demo"))
        assertFalse(factory.hasFactory("custom"))

        factory.registerFactory("custom", ProviderFactory.Factory { FakeProvider() })
        assertTrue(factory.hasFactory("custom"))

        factory.unregisterFactory("custom")
        assertFalse(factory.hasFactory("custom"))
    }

    @Test
    fun `credential reads secrets first then settings then default`() {
        val configMap = mapOf<String, Any>(
            "settings" to mapOf("appKey" to "from-settings"),
            "secrets" to mapOf("appKey" to "from-secrets")
        )
        assertEquals("from-secrets", configMap.credential("appKey"))

        val settingsOnly = mapOf<String, Any>("settings" to mapOf("appKey" to "from-settings"))
        assertEquals("from-settings", settingsOnly.credential("appKey"))

        // Legacy fallback: a config that predates secret-routing stores the
        // credential in plaintext settings.
        assertEquals("", configMap.credential("missing"))
        assertEquals("fallback", configMap.credential("missing", "fallback"))
        assertEquals("", (null as Map<String, Any>?).credential("appKey"))
    }

    @Test
    fun `toFactoryMap serializes the provider configuration contract`() {
        val config = ProviderConfiguration(
            providerId = "demo",
            settings = mapOf("isEnabled" to "true"),
            secrets = mapOf("apiKey" to "k")
        )
        val map = config.toFactoryMap()
        assertEquals(mapOf("isEnabled" to "true"), map["settings"])
        assertEquals(mapOf("apiKey" to "k"), map["secrets"])
    }
}
