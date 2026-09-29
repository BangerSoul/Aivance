package com.bangersoul.aivance.core.domain.usecase.resume.jsonresume

import com.bangersoul.aivance.core.common.model.ResumeSection
import com.bangersoul.aivance.core.common.model.ResumeVersion
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object JsonResumeConverter {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun exportToJsonResume(version: ResumeVersion, userName: String? = null, userEmail: String? = null): String {
        val workList = mutableListOf<JsonResumeWork>()
        val eduList = mutableListOf<JsonResumeEducation>()
        val skillList = mutableListOf<JsonResumeSkill>()
        var summaryText = ""

        version.sections.forEach { section ->
            when (section.sectionType.lowercase()) {
                "experience", "work" -> {
                    workList.add(
                        JsonResumeWork(
                            name = section.title,
                            summary = section.content
                        )
                    )
                }
                "education" -> {
                    eduList.add(
                        JsonResumeEducation(
                            institution = section.title,
                            area = section.content
                        )
                    )
                }
                "skills" -> {
                    val keywords = section.content.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
                    skillList.add(
                        JsonResumeSkill(
                            name = section.title.ifBlank { "Skills" },
                            keywords = keywords
                        )
                    )
                }
                "summary" -> {
                    summaryText = section.content
                }
                else -> {
                    workList.add(
                        JsonResumeWork(
                            name = section.title,
                            summary = section.content
                        )
                    )
                }
            }
        }

        val schema = JsonResumeSchema(
            selectedTemplate = version.templateId,
            basics = JsonResumeBasics(
                name = userName ?: "Professional",
                email = userEmail,
                summary = summaryText
            ),
            work = workList,
            education = eduList,
            skills = skillList
        )

        return json.encodeToString(schema)
    }

    fun importFromJsonResume(jsonString: String, resumeId: Long): ResumeVersion {
        val schema = json.decodeFromString<JsonResumeSchema>(jsonString)
        val sections = mutableListOf<ResumeSection>()
        var order = 0

        schema.basics?.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            sections.add(
                ResumeSection(
                    versionId = 0,
                    title = "Summary",
                    content = summary,
                    sectionOrder = order++,
                    sectionType = "summary"
                )
            )
        }

        schema.work?.forEach { work ->
            sections.add(
                ResumeSection(
                    versionId = 0,
                    title = work.name ?: work.position ?: "Work Experience",
                    content = listOfNotNull(work.position, work.summary).joinToString("\n"),
                    sectionOrder = order++,
                    sectionType = "experience"
                )
            )
        }

        schema.education?.forEach { edu ->
            sections.add(
                ResumeSection(
                    versionId = 0,
                    title = edu.institution ?: "Education",
                    content = listOfNotNull(edu.studyType, edu.area, edu.score).joinToString(" - "),
                    sectionOrder = order++,
                    sectionType = "education"
                )
            )
        }

        schema.skills?.forEach { skill ->
            sections.add(
                ResumeSection(
                    versionId = 0,
                    title = skill.name ?: "Skills",
                    content = skill.keywords?.joinToString(", ") ?: "",
                    sectionOrder = order++,
                    sectionType = "skills"
                )
            )
        }

        return ResumeVersion(
            id = 0,
            resumeId = resumeId,
            versionName = "Imported JSON Resume",
            templateId = schema.selectedTemplate ?: "modern",
            sections = sections
        )
    }
}
