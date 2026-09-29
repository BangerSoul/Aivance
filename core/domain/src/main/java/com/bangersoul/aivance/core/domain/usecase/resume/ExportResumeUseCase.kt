package com.bangersoul.aivance.core.domain.usecase.resume

import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.ValidationError
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.usecase.resume.jsonresume.JsonResumeConverter
import com.bangersoul.aivance.core.domain.usecase.UseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

enum class ExportFormat {
    TXT,
    MARKDOWN,
    JSON
}

data class ExportResumeRequest(
    val resumeId: Long,
    val versionId: Long,
    val format: ExportFormat = ExportFormat.TXT
)

/**
 * Exports a specific resume version.
 */
class ExportResumeUseCase @Inject constructor(
    private val resumeRepository: ResumeRepository
) : UseCase<ExportResumeRequest, CoreResult<String>>() {

    override suspend operator fun invoke(input: ExportResumeRequest): CoreResult<String> {
        return runCatchingCore {
            val versionsResult = resumeRepository.getVersions(input.resumeId).firstOrNull()
            val versions = when (versionsResult) {
                is Result.Success -> versionsResult.data
                is Result.Failure -> throw Exception(versionsResult.error.message)
                null -> throw Exception("Versions not found.")
            }

            val version = versions.find { it.id == input.versionId }
                ?: throw Exception("Version ${input.versionId} not found.")

            when (input.format) {
                ExportFormat.TXT -> exportAsText(version)
                ExportFormat.MARKDOWN -> exportAsMarkdown(version)
                ExportFormat.JSON -> exportAsJson(version)
            }
        }
    }

    private fun exportAsText(version: ResumeVersion): String {
        return buildString {
            appendLine("=== ${version.versionName} ===")
            version.sections.forEach { section ->
                appendLine()
                appendLine("${section.title}:")
                appendLine(section.content)
            }
        }
    }

    private fun exportAsMarkdown(version: ResumeVersion): String {
        return buildString {
            appendLine("# ${version.versionName}")
            appendLine()
            version.sections.forEach { section ->
                appendLine("## ${section.title}")
                appendLine()
                appendLine(section.content)
                appendLine()
            }
        }
    }

    private fun exportAsJson(version: ResumeVersion): String {
        // Standard JSON Resume schema — the same format the Resume Engine
        // imports, so an exported file round-trips back into the app. Escaping
        // and pretty-printing are handled by the serializer.
        return JsonResumeConverter.exportToJsonResume(version = version)
    }
}
