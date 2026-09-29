package com.bangersoul.aivance.core.domain.usecase.resume

import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.OptimizationTip
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateATSScoreUseCaseTest {

    private lateinit var resumeRepository: ResumeRepository
    private lateinit var useCase: CalculateATSScoreUseCase

    @Before
    fun setUp() {
        resumeRepository = mockk()
        useCase = CalculateATSScoreUseCase(resumeRepository)
    }

    @Test
    fun `should calculate ATS score successfully`() = runTest {
        val resumeText = buildString {
            appendLine("test@email.com")
            appendLine("+1234567890")
            appendLine("Experienced Kotlin developer")
            appendLine("- Led Android team")
        }
        val resume = Resume(id = 1L, name = "resume.pdf", rawText = resumeText)
        val analysis = AtsReport(
            resumeVersionId = 2L,
            jobDescriptionId = 1L,
            overallScore = 80,
            matchPercentage = 80,
            matchedKeywords = listOf("Kotlin"),
            missingKeywords = listOf("Compose"),
            optimizationTips = listOf(OptimizationTip("AI", "Good match", "MEDIUM"))
        )

        coEvery { resumeRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        coEvery { resumeRepository.analyzeResume(1L, 2L, any()) } returns Result.Success(analysis)

        val result = useCase(
            AtsScoreRequest(resumeId = 1L, versionId = 2L, jobDescription = "Kotlin developer needed")
        )

        assertTrue(result.isSuccess)
        val report = (result as Result.Success).data
        assertEquals(80, report.overallScore)
        assertTrue(report.matchedKeywords.contains("Kotlin"))
        assertTrue(report.missingKeywords.contains("Compose"))
        // Resume uses bullet points and short lines -> formatting stays at 100,
        // so the AI tip is untouched.
        assertTrue(report.optimizationTips.none { it.category == "Formatting" })
    }

    @Test
    fun `should fail for invalid resume ID`() = runTest {
        val result = useCase(
            AtsScoreRequest(resumeId = 0L, versionId = 2L, jobDescription = "Job description")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `should fail for blank job description`() = runTest {
        val result = useCase(
            AtsScoreRequest(resumeId = 1L, versionId = 2L, jobDescription = "")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `should clamp score between 0 and 100`() = runTest {
        val resume = Resume(id = 1L, name = "resume.pdf", rawText = "Test content")
        val analysis = AtsReport(
            resumeVersionId = 2L,
            jobDescriptionId = 1L,
            overallScore = 150,
            matchPercentage = 150
        )

        coEvery { resumeRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        coEvery { resumeRepository.analyzeResume(1L, 2L, any()) } returns Result.Success(analysis)

        val result = useCase(
            AtsScoreRequest(resumeId = 1L, versionId = 2L, jobDescription = "Test job")
        )

        assertTrue(result.isSuccess)
        val report = (result as Result.Success).data
        assertEquals(100, report.overallScore)
        assertEquals(100, report.matchPercentage)
    }

    @Test
    fun `poorly formatted resume adds a formatting optimization tip`() = runTest {
        val longLine = "x".repeat(200)
        val resume = Resume(id = 1L, name = "resume.pdf", rawText = longLine)
        val analysis = AtsReport(
            resumeVersionId = 2L,
            jobDescriptionId = 1L,
            overallScore = 70,
            matchPercentage = 70
        )

        coEvery { resumeRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        coEvery { resumeRepository.analyzeResume(1L, 2L, any()) } returns Result.Success(analysis)

        val result = useCase(
            AtsScoreRequest(resumeId = 1L, versionId = 2L, jobDescription = "Test job")
        )

        assertTrue(result.isSuccess)
        val report = (result as Result.Success).data
        val formattingTip = report.optimizationTips.firstOrNull { it.category == "Formatting" }
        assertTrue("expected a formatting tip", formattingTip != null)
        assertTrue(formattingTip!!.description.contains("90/100"))
    }
}
