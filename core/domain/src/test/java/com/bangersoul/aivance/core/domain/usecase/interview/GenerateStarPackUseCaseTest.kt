package com.bangersoul.aivance.core.domain.usecase.interview

import com.bangersoul.aivance.core.domain.repository.AiRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenerateStarPackUseCaseTest {

    private val aiRepository: AiRepository = mockk()
    private lateinit var useCase: GenerateStarPackUseCase

    @Before
    fun setUp() {
        useCase = GenerateStarPackUseCase(aiRepository)
    }

    private fun aiJson(): String = """
        [
          {
            "text": "Tell me about a hard bug you fixed as an Android Engineer.",
            "category": "TECHNICAL",
            "difficulty": "MEDIUM",
            "expectedKeyPoints": ["Situation", "Task", "Action", "Result"],
            "idealAnswer": "Situation: ...\nAction: ...\nResult: ..."
          },
          {
            "text": "Describe a time you led a team through a tight deadline as an Android Engineer.",
            "category": "LEADERSHIP",
            "difficulty": "HARD",
            "expectedKeyPoints": ["Situation", "Task", "Action", "Result"],
            "idealAnswer": "Situation: ..."
          }
        ]
    """.trimIndent()

    @Test
    fun `AI path returns parsed questions via the streaming path`() = runTest {
        // The stream is consumed in chunks, exactly like the streaming pipeline.
        every { aiRepository.streamAnalyzeText(any(), any()) } returns flowOf(
            aiJson().substring(0, 40),
            aiJson().substring(40)
        )

        val pack = useCase(GenerateStarPackRequest(role = "Android Engineer", count = 5))

        assertEquals(2, pack.size)
        assertEquals("TECHNICAL", pack[0].category)
        assertEquals(listOf("Situation", "Task", "Action", "Result"), pack[0].expectedKeyPoints)
        assertTrue(pack[0].text.contains("Android Engineer"))
    }

    @Test
    fun `AI prompt carries the STAR ideal-answer guidance`() = runTest {
        val promptSlot = io.mockk.slot<String>()
        every { aiRepository.streamAnalyzeText("Android Engineer", capture(promptSlot)) } returns flowOf(
            aiJson()
        )

        useCase(GenerateStarPackRequest(role = "Android Engineer", count = 5))

        val prompt = promptSlot.captured
        assertTrue(prompt.contains("5 STAR-format"))
        assertTrue(prompt.contains("MUST be a worked STAR answer"))
        STARCoachingPrompts.COMPONENTS.forEach { label ->
            assertTrue(prompt.contains("\"$label: <content>\""))
        }
    }

    @Test
    fun `AI path tolerates markdown fences`() = runTest {
        every { aiRepository.streamAnalyzeText(any(), any()) } returns flowOf(
            "Here are your questions:\n```json\n${aiJson()}\n```\nGood luck!"
        )

        val pack = useCase(GenerateStarPackRequest(role = "Android Engineer"))

        assertEquals(2, pack.size)
        assertEquals("LEADERSHIP", pack[1].category)
    }

    @Test
    fun `AI path respects requested count`() = runTest {
        val many = (1..8).joinToString(",") { i ->
            """{"text": "Question $i", "category": "BEHAVIORAL", "difficulty": "EASY", "expectedKeyPoints": ["S","T","A","R"]}"""
        }
        every { aiRepository.streamAnalyzeText(any(), any()) } returns flowOf("[$many]")

        val pack = useCase(GenerateStarPackRequest(role = "Designer", count = 3))

        assertEquals(3, pack.size)
    }

    @Test
    fun `falls back to template pack when stream is empty`() = runTest {
        every { aiRepository.streamAnalyzeText(any(), any()) } returns emptyFlow()

        val pack = useCase(GenerateStarPackRequest(role = "Android Engineer", count = 4))

        assertEquals(4, pack.size)
        assertTrue(pack.all { it.expectedKeyPoints.isNotEmpty() })
        assertTrue(pack.any { it.text.contains("Android Engineer") })
    }

    @Test
    fun `falls back to template pack when stream throws`() = runTest {
        every { aiRepository.streamAnalyzeText(any(), any()) } throws RuntimeException("provider down")

        val pack = useCase(GenerateStarPackRequest(role = "Backend Engineer"))

        assertEquals(5, pack.size)
        assertTrue(pack.all { it.idealAnswer?.isNotBlank() == true })
    }

    @Test
    fun `falls back to template pack when response is unparseable`() = runTest {
        every { aiRepository.streamAnalyzeText(any(), any()) } returns flowOf("I'm sorry, I cannot generate questions right now.")

        val pack = useCase(GenerateStarPackRequest(role = "Product Manager"))

        assertEquals(5, pack.size)
        assertTrue(pack.any { it.text.contains("Product Manager") })
    }

    @Test
    fun `blank role falls back to software engineer template`() = runTest {
        every { aiRepository.streamAnalyzeText(any(), any()) } returns emptyFlow()

        val pack = useCase(GenerateStarPackRequest(role = ""))

        assertTrue(pack.isNotEmpty())
        assertTrue(pack.any { it.text.contains("Software Engineer") })
    }

    @Test
    fun `template pack clamps count to available questions`() = runTest {
        every { aiRepository.streamAnalyzeText(any(), any()) } returns emptyFlow()

        val pack = useCase(GenerateStarPackRequest(role = "DevOps Engineer", count = 99))

        assertTrue(pack.isNotEmpty())
        assertTrue(pack.size <= 20)
        assertTrue(pack.size >= 6) // the full template pool
    }

    @Test
    fun `every template question carries the STAR key-points framework`() = runTest {
        val pack = STARPrepGenerator.generateStarPack("Data Engineer", count = 20)

        assertTrue(pack.isNotEmpty())
        pack.forEach { question ->
            assertTrue("STAR key points missing for: ${question.text}", question.expectedKeyPoints.isNotEmpty())
            assertTrue("idealAnswer missing for: ${question.text}", question.idealAnswer?.isNotBlank() == true)
            assertTrue(question.category in setOf("BEHAVIORAL", "TECHNICAL", "LEADERSHIP"))
        }
    }
}
