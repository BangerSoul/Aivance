package com.bangersoul.aivance.core.domain.usecase.resume.jsonresume

import com.bangersoul.aivance.core.common.model.ResumeSection
import com.bangersoul.aivance.core.common.model.ResumeVersion
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonResumeConverterTest {

    private fun sampleVersion(): ResumeVersion = ResumeVersion(
        id = 0,
        resumeId = 42L,
        versionName = "v1",
        templateId = "compact",
        sections = listOf(
            ResumeSection(
                sectionType = "summary",
                title = "Summary",
                content = "Senior Android engineer with 8 years of experience.",
                sectionOrder = 0
            ),
            ResumeSection(
                sectionType = "experience",
                title = "Acme Corp",
                content = "Led the mobile team and shipped the flagship app.",
                sectionOrder = 1
            ),
            ResumeSection(
                sectionType = "education",
                title = "MIT",
                content = "BSc Computer Science",
                sectionOrder = 2
            ),
            ResumeSection(
                sectionType = "skills",
                title = "Android",
                content = "Kotlin, Compose, Coroutines",
                sectionOrder = 3
            )
        )
    )

    @Test
    fun `round trip preserves section order types and content`() {
        val json = JsonResumeConverter.exportToJsonResume(sampleVersion())
        val imported = JsonResumeConverter.importFromJsonResume(json, resumeId = 7L)

        assertEquals(4, imported.sections.size)
        assertEquals(
            listOf("summary", "experience", "education", "skills"),
            imported.sections.map { it.sectionType.lowercase() }
        )
        assertEquals(listOf("Summary", "Acme Corp", "MIT", "Android"), imported.sections.map { it.title })
        assertEquals(
            listOf(
                "Senior Android engineer with 8 years of experience.",
                "Led the mobile team and shipped the flagship app.",
                "BSc Computer Science",
                "Kotlin, Compose, Coroutines"
            ),
            imported.sections.map { it.content }
        )
        assertEquals(7L, imported.resumeId)
        assertEquals("compact", imported.templateId)
    }

    @Test
    fun `export carries user basics into the schema`() {
        val json = JsonResumeConverter.exportToJsonResume(
            version = sampleVersion(),
            userName = "Ada Lovelace",
            userEmail = "ada@example.com"
        )

        val schema = Json { ignoreUnknownKeys = true }.decodeFromString<JsonResumeSchema>(json)
        assertEquals("Ada Lovelace", schema.basics?.name)
        assertEquals("ada@example.com", schema.basics?.email)
        assertEquals("Senior Android engineer with 8 years of experience.", schema.basics?.summary)
    }

    @Test
    fun `export escapes quotes and newlines so JSON stays valid`() {
        val version = ResumeVersion(
            resumeId = 1L,
            versionName = "v1",
            sections = listOf(
                ResumeSection(
                    sectionType = "experience",
                    title = "Acme \"Rocket\" Corp",
                    content = "Line one.\nLine two with \"quotes\"."
                )
            )
        )

        val json = JsonResumeConverter.exportToJsonResume(version)
        val imported = JsonResumeConverter.importFromJsonResume(json, resumeId = 1L)

        assertEquals("Acme \"Rocket\" Corp", imported.sections.single().title)
        assertEquals("Line one.\nLine two with \"quotes\".", imported.sections.single().content)
    }

    @Test
    fun `import tolerates unknown standard JSON Resume sections`() {
        // Standard JSON Resume documents carry awards/volunteer/interests/etc.
        // that this app does not model — they must be ignored, not crash.
        val raw = """
            {
              "basics": { "name": "Grace Hopper", "summary": "Rear admiral and compiler pioneer" },
              "work": [
                { "name": "US Navy", "position": "Rear Admiral", "summary": "COBOL pioneer" }
              ],
              "skills": [ { "name": "Coding", "keywords": ["COBOL", "Flow-matic"] } ],
              "awards": [ { "title": "Presidential Medal of Freedom" } ],
              "volunteer": [ { "organization": "ACM", "position": "Member" } ],
              "interests": [ { "name": "Sailing" } ]
            }
        """.trimIndent()

        val imported = JsonResumeConverter.importFromJsonResume(raw, resumeId = 3L)

        assertEquals(3, imported.sections.size)
        assertEquals("Rear admiral and compiler pioneer", imported.sections[0].content)
        assertEquals("US Navy", imported.sections[1].title)
        assertEquals("Rear Admiral\nCOBOL pioneer", imported.sections[1].content)
        assertEquals("Coding", imported.sections[2].title)
        assertEquals("COBOL, Flow-matic", imported.sections[2].content)
    }

    @Test
    fun `import of a document with no sections returns empty version`() {
        val imported = JsonResumeConverter.importFromJsonResume("""{"basics": {"name": "Nobody"}}""", resumeId = 1L)
        assertTrue(imported.sections.isEmpty())
        assertEquals(1L, imported.resumeId)
    }

    @Test
    fun `import maps position and studyType when present`() {
        val raw = """
            {
              "work": [
                { "name": "Acme", "position": "Engineer", "summary": "Built things" }
              ],
              "education": [
                { "institution": "MIT", "studyType": "Bachelor", "area": "CS", "score": "3.9" }
              ]
            }
        """.trimIndent()

        val imported = JsonResumeConverter.importFromJsonResume(raw, resumeId = 1L)

        assertEquals("Engineer\nBuilt things", imported.sections[0].content)
        assertEquals("Bachelor - CS - 3.9", imported.sections[1].content)
    }
}
