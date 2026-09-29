package com.bangersoul.aivance.ai.di

import android.content.Context
import com.bangersoul.aivance.ai.anthropic.ClaudeProvider
import com.bangersoul.aivance.ai.gemini.GeminiAIProvider
import com.bangersoul.aivance.ai.openai.GroqProvider
import com.bangersoul.aivance.ai.openai.OllamaProvider
import com.bangersoul.aivance.ai.offline.GemmaOnDeviceProvider
import com.bangersoul.aivance.ai.openai.OpenAIProvider
import com.bangersoul.aivance.ai.openai.OpenRouterProvider
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.infrastructure.ProviderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import dagger.multibindings.StringKey
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiProvidersModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideGeminiProvider(@ApplicationContext context: Context): AIProvider {
        return GeminiAIProvider(context, ProviderConfiguration("gemini"))
    }

    @Provides
    @Singleton
    @IntoSet
    fun provideOpenAIProvider(): AIProvider {
        return OpenAIProvider(ProviderConfiguration("openai"))
    }

    @Provides
    @Singleton
    @IntoSet
    fun provideGroqProvider(): AIProvider {
        return GroqProvider(ProviderConfiguration("groq"))
    }

    @Provides
    @Singleton
    @IntoSet
    fun provideOpenRouterProvider(): AIProvider {
        return OpenRouterProvider(ProviderConfiguration("openrouter"))
    }

    @Provides
    @Singleton
    @IntoSet
    fun provideOllamaProvider(): AIProvider {
        return OllamaProvider(ProviderConfiguration("ollama"))
    }

    @Provides
    @Singleton
    @IntoSet
    fun provideClaudeProvider(): AIProvider {
        return ClaudeProvider(ProviderConfiguration("anthropic"))
    }

    @Provides
    @Singleton
    @IntoSet
    fun provideGemmaOnDeviceProvider(@ApplicationContext context: Context): AIProvider {
        return GemmaOnDeviceProvider(context, ProviderConfiguration("gemma"))
    }

    @Provides
    @IntoMap
    @StringKey("gemini")
    fun provideGeminiFactory(@ApplicationContext context: Context): ProviderFactory.Factory {
        return ProviderFactory.Factory { config ->
            GeminiAIProvider(context, config.toProviderConfig("gemini"))
        }
    }

    @Provides
    @IntoMap
    @StringKey("openai")
    fun provideOpenAIFactory(): ProviderFactory.Factory {
        return ProviderFactory.Factory { config ->
            OpenAIProvider(config.toProviderConfig("openai"))
        }
    }

    @Provides
    @IntoMap
    @StringKey("groq")
    fun provideGroqFactory(): ProviderFactory.Factory {
        return ProviderFactory.Factory { config ->
            GroqProvider(config.toProviderConfig("groq"))
        }
    }

    @Provides
    @IntoMap
    @StringKey("openrouter")
    fun provideOpenRouterFactory(): ProviderFactory.Factory {
        return ProviderFactory.Factory { config ->
            OpenRouterProvider(config.toProviderConfig("openrouter"))
        }
    }

    @Provides
    @IntoMap
    @StringKey("ollama")
    fun provideOllamaFactory(): ProviderFactory.Factory {
        return ProviderFactory.Factory { config ->
            OllamaProvider(config.toProviderConfig("ollama"))
        }
    }

    @Provides
    @IntoMap
    @StringKey("anthropic")
    fun provideClaudeFactory(): ProviderFactory.Factory {
        return ProviderFactory.Factory { config ->
            ClaudeProvider(config.toProviderConfig("anthropic"))
        }
    }

    @Provides
    @IntoMap
    @StringKey("gemma")
    fun provideGemmaFactory(@ApplicationContext context: Context): ProviderFactory.Factory {
        return ProviderFactory.Factory { config ->
            GemmaOnDeviceProvider(context, config.toProviderConfig("gemma"))
        }
    }

    private fun Map<String, Any>?.toProviderConfig(id: String): ProviderConfiguration {
        @Suppress("UNCHECKED_CAST")
        val settings = (this?.get("settings") as? Map<String, String>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val secrets = (this?.get("secrets") as? Map<String, String>) ?: emptyMap()
        return ProviderConfiguration(id, settings, secrets)
    }
}
