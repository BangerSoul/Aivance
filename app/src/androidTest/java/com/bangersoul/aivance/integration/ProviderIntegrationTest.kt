package com.bangersoul.aivance.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bangersoul.aivance.BuildConfig
import com.bangersoul.aivance.ai.gemini.GeminiAIProvider
import com.bangersoul.aivance.ai.openai.GroqProvider
import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.enums.EmploymentType
import com.bangersoul.aivance.core.common.enums.ExperienceLevel
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.enrichment.hunter.HunterEnrichmentProvider
import com.bangersoul.aivance.feature.jobs.JobDetailsViewModel
import com.bangersoul.aivance.job.apify.ApifyJobProvider
import com.bangersoul.aivance.job.cache.JobCache
import com.bangersoul.aivance.job.remoteok.RemoteOKProvider
import com.bangersoul.aivance.job.remotive.RemotiveProvider
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Phase 4 (STEP 3) — Real-API end-to-end validation.
 *
 * These instrumented tests hit the LIVE provider APIs with the keys supplied
 * in the gitignored local.properties (exposed via BuildConfig). Keys are never
 * committed to source. Tests for which no key is configured are skipped via
 * JUnit Assume so the suite stays green on machines without keys.
 *
 * The two keyless job boards (RemoteOK, Remotive) cannot be gated on a key, so
 * they are gated on `runLiveApiTests=true` in local.properties instead. They
 * depend on a third party's live response shape and availability, which makes
 * them unsuitable as an unattended CI gate — RemoteOK returning zero results
 * for "kotlin" failed the emulator job on its first real run. Set the flag
 * locally to exercise them.
 *
 * Run with: ./gradlew connectedDebugAndroidTest  (device/emulator required)
 */
@RunWith(AndroidJUnit4::class)
class ProviderIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val jobCache = object : JobCache {
        override suspend fun getJobs(): List<JobListing> = emptyList()
        override suspend fun saveJobs(jobs: List<JobListing>) = Unit
        override suspend fun clear() = Unit
    }

    // ── AI providers ────────────────────────────────────────────────────

    @Test
    fun groqProvider_streamsRealResponse() = runTest(timeout = 60.seconds) {
        assumeTrue("GROQ_API_KEY not set in local.properties", BuildConfig.GROQ_API_KEY.isNotBlank())
        val provider = GroqProvider(
            ProviderConfiguration("groq", secrets = mapOf("apiKey" to BuildConfig.GROQ_API_KEY))
        )
        provider.onInitialize()

        val tokens = provider.streamText("Say hello in exactly 5 words").toList()
        val response = tokens.joinToString("")
        assertFalse("Groq stream produced no tokens", tokens.isEmpty())
        assertTrue("Groq streamed an empty response", response.isNotBlank())
    }

    @Test
    fun groqProvider_generatesText() = runTest(timeout = 60.seconds) {
        assumeTrue("GROQ_API_KEY not set in local.properties", BuildConfig.GROQ_API_KEY.isNotBlank())
        val provider = GroqProvider(
            ProviderConfiguration("groq", secrets = mapOf("apiKey" to BuildConfig.GROQ_API_KEY))
        )
        provider.onInitialize()

        val result = provider.generateText("Say hello in exactly 5 words")
        assertTrue(
            "Groq generateText failed: ${(result as? Result.Failure)?.error?.message}",
            result is Result.Success
        )
        assertTrue("Groq returned empty text", (result as Result.Success).data.isNotBlank())
    }

    @Test
    fun geminiProvider_analyzesText() = runTest(timeout = 60.seconds) {
        assumeTrue("GEMINI_API_KEY not set in local.properties", BuildConfig.GEMINI_API_KEY.isNotBlank())
        val provider = GeminiAIProvider(
            context,
            ProviderConfiguration("gemini", secrets = mapOf("apiKey" to BuildConfig.GEMINI_API_KEY))
        )
        provider.onInitialize()

        val result = provider.generateText(
            "Summarize in one sentence: Kotlin is a modern programming language."
        )
        assertTrue(
            "Gemini generateText failed: ${(result as? Result.Failure)?.error?.message}",
            result is Result.Success
        )
        assertTrue("Gemini returned empty text", (result as Result.Success).data.isNotBlank())
    }

    // ── Job providers ───────────────────────────────────────────────────

    @Test
    fun apifyProvider_searchesRealJobs() = runTest(timeout = 120.seconds) {
        assumeTrue("APIFY_API_KEY not set in local.properties", BuildConfig.APIFY_API_KEY.isNotBlank())
        // The actor ID is user-configurable in Provider Setup; this is the
        // project's default LinkedIn scraper actor.
        val provider = ApifyJobProvider(
            metadata = ProviderMetadata(
                id = "apify",
                name = "Apify",
                type = ProviderType.JOB,
                version = "1.0.0",
                description = "Integration test",
                author = "Aivance"
            ),
            apiKey = BuildConfig.APIFY_API_KEY,
            actorId = "valig~linkedin-jobs-scraper",
            jobCache = jobCache,
            okHttpClient = okHttp,
            baseRetrofit = retrofit("https://api.apify.com/v2/")
        )

        val result = provider.searchJobs(
            JobSearchFilter(query = "android developer", location = "remote"),
            JobSortOrder.RELEVANCE,
            page = 1
        )
        assertTrue(
            "Apify search failed: ${(result as? Result.Failure)?.error?.message}",
            result is Result.Success
        )
        assertTrue(
            "Apify returned no jobs — actor may be invalid, rate-limited, or the dataset was empty",
            (result as Result.Success).data.isNotEmpty()
        )
    }

    /**
     * Sweeps a matrix of varied, realistic search filters against the LIVE
     * Apify actor and asserts that every filter returns real listings AND that
     * each listing resolves to a non-blank, absolute http(s) apply URL via the
     * production [JobDetailsViewModel.resolveApplyUrl] logic — i.e. the results
     * are genuinely applicable, not just present. Skipped when no key is set.
     */
    @Test
    fun apifyProvider_variedFilters_returnRealApplicableRoles() = runTest(timeout = 300.seconds) {
        assumeTrue("APIFY_API_KEY not set in local.properties", BuildConfig.APIFY_API_KEY.isNotBlank())
        val provider = ApifyJobProvider(
            metadata = ProviderMetadata(
                id = "apify",
                name = "Apify",
                type = ProviderType.JOB,
                version = "1.0.0",
                description = "Integration test",
                author = "Aivance"
            ),
            apiKey = BuildConfig.APIFY_API_KEY,
            actorId = "valig~linkedin-jobs-scraper",
            jobCache = jobCache,
            okHttpClient = okHttp,
            baseRetrofit = retrofit("https://api.apify.com/v2/")
        )

        // A deliberately varied matrix: different roles, locations, remote
        // policies and experience levels, so a single lucky query can't mask a
        // filter that silently returns nothing.
        val filters = listOf(
            JobSearchFilter(query = "android developer", location = "remote"),
            JobSearchFilter(query = "data scientist", location = "New York"),
            JobSearchFilter(query = "product manager", location = "London"),
            JobSearchFilter(
                query = "backend engineer",
                remoteType = RemoteType.REMOTE,
                experienceLevels = listOf(ExperienceLevel.SENIOR_LEVEL)
            ),
            JobSearchFilter(
                query = "ux designer",
                employmentTypes = listOf(EmploymentType.CONTRACT)
            )
        )

        for (filter in filters) {
            val label = "query='${filter.query}', location='${filter.location}'"
            val result = provider.searchJobs(filter, JobSortOrder.RELEVANCE, page = 1)
            assertTrue(
                "Apify search failed for [$label]: ${(result as? Result.Failure)?.error?.message}",
                result is Result.Success
            )
            val jobs = (result as Result.Success).data
            assertTrue("Apify returned no jobs for [$label]", jobs.isNotEmpty())

            // Every returned role must be applicable: title/company present and
            // a resolvable absolute apply URL. This is the "lands on a real
            // apply page" guarantee the in-app WebView depends on.
            jobs.forEach { job ->
                assertTrue("Blank title for a role in [$label]", job.title.isNotBlank())
                assertTrue("Blank company for '${job.title}' in [$label]", job.company.isNotBlank())
                val applyUrl = JobDetailsViewModel.resolveApplyUrl(
                    job.url, job.sourceUrl, job.descriptionHtml
                )
                assertFalse(
                    "No resolvable apply URL for '${job.title}' @ '${job.company}' in [$label]",
                    applyUrl.isNullOrBlank()
                )
                assertTrue(
                    "Apply URL is not an absolute http(s) link for '${job.title}' in [$label]: $applyUrl",
                    applyUrl!!.startsWith("http://") || applyUrl.startsWith("https://")
                )
            }
        }
    }

    @Test
    fun remoteOkProvider_fetchesJobs() = runTest(timeout = 60.seconds) {
        assumeTrue("runLiveApiTests is not enabled in local.properties", BuildConfig.RUN_LIVE_API_TESTS)
        val provider = RemoteOKProvider(jobCache, okHttp, retrofit("https://remoteok.com/"))
        val result = provider.searchJobs(
            JobSearchFilter(query = "kotlin"),
            JobSortOrder.RELEVANCE,
            page = 1
        )
        assertTrue(
            "RemoteOK search failed: ${(result as? Result.Failure)?.error?.message}",
            result is Result.Success
        )
        assertTrue("RemoteOK returned no jobs for 'kotlin'", (result as Result.Success).data.isNotEmpty())
    }

    @Test
    fun remotiveProvider_fetchesJobs() = runTest(timeout = 60.seconds) {
        assumeTrue("runLiveApiTests is not enabled in local.properties", BuildConfig.RUN_LIVE_API_TESTS)
        val provider = RemotiveProvider(jobCache, okHttp, retrofit("https://remotive.com/"))
        val result = provider.searchJobs(
            JobSearchFilter(query = "software engineer"),
            JobSortOrder.RELEVANCE,
            page = 1
        )
        assertTrue(
            "Remotive search failed: ${(result as? Result.Failure)?.error?.message}",
            result is Result.Success
        )
        assertTrue("Remotive returned no jobs for 'software engineer'", (result as Result.Success).data.isNotEmpty())
    }

    // ── Enrichment providers ────────────────────────────────────────────

    @Test
    fun hunterProvider_searchesDomain() = runTest(timeout = 60.seconds) {
        assumeTrue("HUNTER_API_KEY not set in local.properties", BuildConfig.HUNTER_API_KEY.isNotBlank())
        val provider = HunterEnrichmentProvider(
            ProviderConfiguration("hunter", secrets = mapOf("apiKey" to BuildConfig.HUNTER_API_KEY)),
            okHttp,
            retrofit("https://api.hunter.io/")
        )

        val result = provider.findRecruiters("google.com")
        assertTrue(
            "Hunter domain search failed: ${(result as? Result.Failure)?.error?.message}",
            result is Result.Success
        )
    }
}
