package com.bangersoul.aivance.core.domain.usecase.resume

import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.OptimizationTip
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnalyseResumeUseCaseTest {

    private lateinit var resumeRepository: ResumeRepository
    private lateinit var useCase: AnalyseResumeUseCase

    @Before
    fun setUp() {
        resumeRepository = mockk()
        useCase = AnalyseResumeUseCase(resumeRepository)
    }

    @Test
    fun `should analyse resume successfully`() = runTest {
        val analysis = AtsReport(
            resumeVersionId = 2L,
            jobDescriptionId = 1L,
            overallScore = 85,
            matchPercentage = 85,
            matchedKeywords = listOf("Kotlin", "Android"),
            missingKeywords = listOf("Compose"),
            optimizationTips = listOf(OptimizationTip("AI", "Add Compose experience", "MEDIUM"))
        )
        coEvery { resumeRepository.analyzeResume(1L, 2L, any()) } returns Result.Success(analysis)

        val result = useCase(
            AnalyseResumeRequest(
                resumeId = 1L,
                versionId = 2L,
                jobDescription = "Kotlin Android developer needed"
            )
        )

        assertTrue(result.isSuccess)
        val data = (result as Result.Success).data
        assertEquals(85, data.overallScore)
        assertTrue(data.matchedKeywords.contains("Kotlin"))
        assertEquals("Add Compose experience", data.optimizationTips.single().description)
    }

    @Test
    fun `should fail for invalid resume ID`() = runTest {
        val result = useCase(
            AnalyseResumeRequest(resumeId = 0L, versionId = 2L, jobDescription = "Job description")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `should fail for invalid version ID`() = runTest {
        val result = useCase(
            AnalyseResumeRequest(resumeId = 1L, versionId = 0L, jobDescription = "Job description")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `should fail for blank job description`() = runTest {
        val result = useCase(
            AnalyseResumeRequest(resumeId = 1L, versionId = 2L, jobDescription = "")
        )
        assertTrue(result.isFailure)
    }
}
