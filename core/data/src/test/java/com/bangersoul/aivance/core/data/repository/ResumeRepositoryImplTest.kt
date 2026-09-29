package com.bangersoul.aivance.core.data.repository

import android.content.Context
import app.cash.turbine.test
import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.data.resume.ResumeParser
import com.bangersoul.aivance.core.data.source.ResumeLocalDataSource
import com.bangersoul.aivance.core.database.dao.AtsDao
import com.bangersoul.aivance.core.domain.events.CareerEventDispatcher
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResumeRepositoryImplTest {

    private lateinit var repository: ResumeRepositoryImpl
    private val context: Context = mockk()
    private val localDataSource: ResumeLocalDataSource = mockk()
    private val providerManager: ProviderManager = mockk()
    private val resumeParser: ResumeParser = mockk()
    private val atsDao: AtsDao = mockk()
    private val careerEventDispatcher: CareerEventDispatcher = mockk(relaxed = true)
    private val mockAIProvider: AIProvider = mockk()

    @Before
    fun setUp() {
        repository = ResumeRepositoryImpl(
            context = context,
            localDataSource = localDataSource,
            providerManager = providerManager,
            resumeParser = resumeParser,
            atsDao = atsDao,
            careerEventDispatcher = careerEventDispatcher
        )
    }

    private fun sampleResume(id: Long = 1L, fileName: String = "resume.pdf") = Resume(
        id = id,
        name = fileName.substringBeforeLast("."),
        fileName = fileName,
        fileUri = "",
        rawText = "text"
    )

    @Test
    fun `getResumes returns success with list of resumes`() = runTest {
        val resumes = listOf(sampleResume())
        every { localDataSource.getResumes() } returns flowOf(resumes)

        repository.getResumes().test {
            val result = awaitItem()
            assertTrue(result is Result.Success)
            assertEquals(resumes, (result as Result.Success).data)
            awaitComplete()
        }
    }

    @Test
    fun `getResumeById returns success when resume exists`() = runTest {
        val resumeId = 1L
        val resumes = listOf(sampleResume(resumeId))
        every { localDataSource.getResumes() } returns flowOf(resumes)

        repository.getResumeById(resumeId).test {
            val result = awaitItem()
            assertTrue(result is Result.Success)
            assertEquals(resumes.first(), (result as Result.Success).data)
            awaitComplete()
        }
    }

    @Test
    fun `getResumeById returns failure when resume does not exist`() = runTest {
        val resumeId = 1L
        every { localDataSource.getResumes() } returns flowOf(emptyList())

        // runCatchingCore wraps every Throwable into Result.Failure.
        repository.getResumeById(resumeId).test {
            val result = awaitItem()
            assertTrue(result.isFailure)
            assertEquals("Resume not found", (result as Result.Failure).error.message)
            awaitComplete()
        }
    }

    @Test
    fun `saveResume calls localDataSource and returns id`() = runTest {
        val resume = sampleResume()
        coEvery { localDataSource.saveResume(resume) } returns 1L

        val result = repository.saveResume(resume)

        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrNull())
        coVerify { localDataSource.saveResume(resume) }
    }

    @Test
    fun `deleteResume calls localDataSource when resume exists`() = runTest {
        val resumeId = 1L
        val resume = sampleResume(resumeId)
        coEvery { localDataSource.getResumeById(resumeId) } returns resume
        coEvery { localDataSource.deleteResume(resume) } returns Unit

        val result = repository.deleteResume(resumeId)

        assertTrue(result.isSuccess)
        coVerify { localDataSource.deleteResume(resume) }
    }

    @Test
    fun `getVersions returns versions from localDataSource`() = runTest {
        val versions = listOf(ResumeVersion(resumeId = 1L, versionName = "Original Import"))
        every { localDataSource.getVersionsForResume(1L) } returns flowOf(versions)

        repository.getVersions(1L).test {
            val result = awaitItem()
            assertTrue(result is Result.Success)
            assertEquals(versions, (result as Result.Success).data)
            awaitComplete()
        }
    }

    @Test
    fun `analyzeResume returns analysis from AI provider`() = runTest {
        val resumeId = 1L
        val versionId = 1L
        val version = ResumeVersion(id = versionId, resumeId = resumeId, versionName = "Original Import")
        coEvery { localDataSource.getVersionsForResume(resumeId) } returns flowOf(listOf(version))
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns mockAIProvider
        coEvery { mockAIProvider.generateText(any()) } returns Result.Success("AI feedback")
        coEvery { atsDao.insertJobDescription(any()) } returns 1L
        coEvery { atsDao.insertReport(any()) } returns 42L

        val result = repository.analyzeResume(resumeId, versionId, "job description")

        assertTrue(result.isSuccess)
        val report = result.getOrNull()
        assertEquals(80, report?.overallScore)
        // The AtsReport is persisted (id from the DAO insert) with the JD linkage.
        assertEquals(42L, report?.id)
        assertEquals(1L, report?.jobDescriptionId)
        assertEquals("AI feedback", report?.optimizationTips?.single()?.description)
        coVerify { atsDao.insertJobDescription(any()) }
        coVerify { atsDao.insertReport(any()) }
    }

    @Test
    fun `analyzeResume emits ResumeAnalysisCompleted with the persisted score on success`() = runTest {
        val resumeId = 1L
        val versionId = 1L
        val version = ResumeVersion(id = versionId, resumeId = resumeId, versionName = "Original Import")
        coEvery { localDataSource.getVersionsForResume(resumeId) } returns flowOf(listOf(version))
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns mockAIProvider
        coEvery { mockAIProvider.generateText(any()) } returns Result.Success("The overall match score is 87/100.")
        coEvery { atsDao.insertJobDescription(any()) } returns 1L
        coEvery { atsDao.insertReport(any()) } returns 42L

        val result = repository.analyzeResume(resumeId, versionId, "job description")

        assertTrue(result.isSuccess)
        // Success boundary is the persisted report; the event carries its score.
        io.mockk.verify {
            careerEventDispatcher.onResumeAnalysisCompleted(
                resumeId = resumeId,
                versionId = versionId,
                atsScore = 87
            )
        }
    }

    @Test
    fun `analyzeResume does not emit an event when the version is missing`() = runTest {
        val resumeId = 1L
        val versionId = 1L
        // No matching version -> analyzeResume throws -> Result.Failure, no persist.
        coEvery { localDataSource.getVersionsForResume(resumeId) } returns flowOf(emptyList())

        val result = repository.analyzeResume(resumeId, versionId, "job description")

        assertTrue(result.isFailure)
        io.mockk.verify(exactly = 0) {
            careerEventDispatcher.onResumeAnalysisCompleted(any(), any(), any(), any())
        }
    }

    private suspend fun analyzeWithResponse(response: String): AtsReport? {
        val resumeId = 1L
        val versionId = 1L
        val version = ResumeVersion(id = versionId, resumeId = resumeId, versionName = "Original Import")
        coEvery { localDataSource.getVersionsForResume(resumeId) } returns flowOf(listOf(version))
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns mockAIProvider
        coEvery { mockAIProvider.generateText(any()) } returns Result.Success(response)
        coEvery { atsDao.insertJobDescription(any()) } returns 1L
        coEvery { atsDao.insertReport(any()) } returns 42L
        return repository.analyzeResume(resumeId, versionId, "job description").getOrNull()
    }

    @Test
    fun `analyzeResume parses JSON overallScore from AI response`() = runTest {
        val report = analyzeWithResponse(
            """```json
            {"overallScore": 91, "matchedKeywords": ["Kotlin", "Jetpack Compose"]}
            ```"""
        )

        assertTrue(report != null)
        assertEquals(91, report?.overallScore)
        assertEquals(91, report?.matchPercentage)
    }

    @Test
    fun `analyzeResume parses prose score from AI response`() = runTest {
        val report = analyzeWithResponse(
            "The overall match score is 87/100. Keywords: Kotlin, Compose, MVVM."
        )

        assertTrue(report != null)
        assertEquals(87, report?.overallScore)
        assertEquals(87, report?.matchPercentage)
    }

    @Test
    fun `analyzeResume falls back to 80 when AI response has no parseable score`() = runTest {
        // "0-100" is the prompt's range description, not a score of 0.
        val report = analyzeWithResponse(
            "Overall match score 0-100. Matched keywords: ATS. Missing: leadership."
        )

        assertTrue(report != null)
        assertEquals(80, report?.overallScore)
        assertEquals(80, report?.matchPercentage)
    }

}
