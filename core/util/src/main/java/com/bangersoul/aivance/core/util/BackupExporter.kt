package com.bangersoul.aivance.core.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.database.AivanceDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AivanceBackupPayload(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val resumes: List<BackupResume> = emptyList(),
    val jobs: List<BackupJob> = emptyList(),
    val coverLetters: List<BackupCoverLetter> = emptyList(),
    val applications: List<BackupApplication> = emptyList(),
    val profiles: List<BackupUserProfile> = emptyList()
)

@Serializable
data class BackupResume(
    val id: Long,
    val name: String,
    val text: String = "",
    val versions: List<BackupResumeVersion> = emptyList()
)

@Serializable
data class BackupResumeVersion(
    val id: Long,
    val resumeId: Long,
    val versionName: String,
    val sections: List<BackupResumeSection> = emptyList()
)

@Serializable
data class BackupResumeSection(
    val title: String,
    val content: String,
    val sectionOrder: Int = 0
)

@Serializable
data class BackupJob(
    val id: Long,
    val title: String,
    val companyId: Long = 1,
    val location: String? = null,
    val description: String? = null,
    val url: String = ""
)

@Serializable
data class BackupCoverLetter(
    val id: Long,
    val company: String,
    val role: String,
    val dateCreated: Long
)

/**
 * Backup representation of a job application (R1: sourced from the canonical
 * `applications` table).
 *
 * [status] is the canonical lifecycle value (ACTIVE/COMPLETED/ARCHIVED) and
 * [stageId] the pipeline stage. Payloads written before R1 predate [stageId] and
 * carried the legacy application-status string in [status] instead — on import a
 * null [stageId] is treated as that legacy layout.
 */
@Serializable
data class BackupApplication(
    val id: Long,
    val jobId: Long,
    val status: String,
    val dateApplied: Long = System.currentTimeMillis(),
    val stageId: String? = null,
    val salaryRange: String? = null,
    val notes: String? = null
)

@Serializable
data class BackupUserProfile(
    val id: String,
    val name: String,
    val email: String,
    val currentRole: String? = null,
    val targetRole: String? = null,
    val skills: List<String> = emptyList()
)

@Singleton
class BackupExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AivanceDatabase,
    private val json: Json,
    private val backupSecurity: BackupSecurity
) {
    suspend fun exportBackup(passphrase: String? = null): CoreResult<Uri> = withContext(Dispatchers.IO) {
        try {
            // Device-bound secret when the caller doesn't supply one — never a
            // hardcoded value (audit S-04).
            val effectivePassphrase = passphrase ?: backupSecurity.devicePassphrase()
            val resumesEntities = database.resumeDao().getResumes().firstOrNull() ?: emptyList()
            val backupResumes = resumesEntities.map { r ->
                val versions = database.resumeDao().getVersionsForResume(r.id).firstOrNull() ?: emptyList()
                val backupVersions = versions.map { v ->
                    val sections = database.resumeDao().getSectionsForVersion(v.id).firstOrNull() ?: emptyList()
                    BackupResumeVersion(
                        id = v.id,
                        resumeId = v.resumeId,
                        versionName = v.versionName,
                        sections = sections.map { BackupResumeSection(it.title, it.content, it.sectionOrder) }
                    )
                }
                BackupResume(id = r.id, name = r.name, text = r.rawText?.value ?: "", versions = backupVersions)
            }

            val jobsWithDetails = database.jobDao().getJobsWithDetails().firstOrNull() ?: emptyList()
            val backupJobs = jobsWithDetails.map { j ->
                BackupJob(id = j.job.id, title = j.job.title, companyId = j.job.companyId, location = j.job.location, description = j.job.description, url = j.job.url)
            }

            val coverLetterEntities = database.coverLetterDao().getCoverLetters().firstOrNull() ?: emptyList()
            val backupCoverLetters = coverLetterEntities.map { c ->
                BackupCoverLetter(id = c.id, company = c.company, role = c.role, dateCreated = c.dateCreated)
            }

            val appEntities = database.workflowDao().getAllApplications().firstOrNull() ?: emptyList()
            val backupApps = appEntities.map { a ->
                BackupApplication(
                    id = a.id,
                    jobId = a.jobId,
                    status = a.status,
                    // The canonical table keeps dateApplied nullable (a SAVED application was
                    // never submitted). Fall back to lastModified so the payload stays non-null
                    // and a restore does not anchor the follow-up window at epoch 0.
                    dateApplied = a.dateApplied ?: a.lastModified,
                    stageId = a.currentStageId,
                    salaryRange = a.salaryRange,
                    notes = a.notes
                )
            }

            val profileEntity = database.profileDao().getUserProfile().firstOrNull()
            val backupProfiles = if (profileEntity != null) {
                listOf(BackupUserProfile(id = profileEntity.id, name = profileEntity.name, email = profileEntity.email, currentRole = profileEntity.currentRole, targetRole = profileEntity.targetRole, skills = profileEntity.skills))
            } else emptyList()

            val payload = AivanceBackupPayload(
                resumes = backupResumes,
                jobs = backupJobs,
                coverLetters = backupCoverLetters,
                applications = backupApps,
                profiles = backupProfiles
            )

            val jsonString = json.encodeToString(payload)
            val encryptedBytes = BackupSecurity.encryptString(jsonString, effectivePassphrase)

            val file = File(context.cacheDir, "aivance_backup_${System.currentTimeMillis()}.aivance_backup")
            FileOutputStream(file).use { it.write(encryptedBytes) }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Result.Success(uri)
        } catch (e: Exception) {
            Timber.e(e, "Backup export failed")
            Result.Failure(DomainError(e.message ?: "Backup export failed", e))
        }
    }

    companion object {
        // Re-exported stateless helpers (delegate to BackupSecurity) so existing
        // callers and tests keep compiling. The KDF lives in BackupSecurity.

        fun encryptString(plainText: String, passphrase: String): ByteArray =
            BackupSecurity.encryptString(plainText, passphrase)

        fun decryptBytes(encryptedData: ByteArray, passphrase: String): String =
            BackupSecurity.decryptBytes(encryptedData, passphrase)
    }
}
