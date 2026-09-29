package com.bangersoul.aivance.core.domain.usecase.coverletter

import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.CoverLetterRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenerateCoverLetterUseCaseTest {

    private lateinit var coverLetterRepository: CoverLetterRepository
    private lateinit var useCase: GenerateCoverLetterUseCase

    @Before
    fun setUp() {
        coverLetterRepository = mockk()
        useCase = GenerateCoverLetterUseCase(coverLetterRepository)
    }

    @Test
    fun `should generate cover letter successfully`() = runTest {
        coEvery {
            coverLetterRepository.generateCoverLetter(
                resumeId = 1L,
                resumeVersionId = 1L,
                jobId = 2L,
                recruiterId = null,
                writingStyle = "PROFESSIONAL"
            )
        } returns Result.Success(42L)

        val result = useCase(
            GenerateCoverLetterRequest(resumeId = 1L, resumeVersionId = 1L, jobId = 2L)
        )

        assertTrue(result.isSuccess)
        assertEquals(42L, (result as Result.Success).data)
    }

    @Test
    fun `should fail for invalid resume id`() = runTest {
        val result = useCase(
            GenerateCoverLetterRequest(resumeId = 0L, resumeVersionId = 1L, jobId = 2L)
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `should fail for invalid job id`() = runTest {
        val result = useCase(
            GenerateCoverLetterRequest(resumeId = 1L, resumeVersionId = 1L, jobId = 0L)
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `null job id generates a generic letter without validation failure`() = runTest {
        coEvery {
            coverLetterRepository.generateCoverLetter(
                resumeId = 1L,
                resumeVersionId = 1L,
                jobId = null,
                recruiterId = null,
                writingStyle = "PROFESSIONAL"
            )
        } returns Result.Success(43L)

        // jobId omitted → null → the use case must forward it (not reject).
        val result = useCase(
            GenerateCoverLetterRequest(resumeId = 1L, resumeVersionId = 1L)
        )

        assertTrue(result.isSuccess)
        assertEquals(43L, (result as Result.Success).data)
    }
}
