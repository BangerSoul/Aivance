package com.bangersoul.aivance.core.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "applications",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ResumeVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["resumeVersionId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AtsReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["atsReportId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CoverLetterVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["coverLetterVersionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["jobId"]),
        Index(value = ["resumeVersionId"]),
        Index(value = ["atsReportId"]),
        Index(value = ["coverLetterVersionId"])
    ]
)
data class ApplicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jobId: Long,
    val resumeVersionId: Long? = null,
    val atsReportId: Long? = null,
    val coverLetterVersionId: Long? = null,
    val currentStageId: String = "SAVED",
    val status: String = "ACTIVE", // ACTIVE, COMPLETED, ARCHIVED
    val dateApplied: Long? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val notes: String? = null,
    /** Free-form salary range, carried over from the retired `job_applications` table (R1). */
    val salaryRange: String? = null
)

@Entity(tableName = "application_stages")
data class ApplicationStageEntity(
    @PrimaryKey
    val id: String, // e.g., "SAVED", "APPLIED", "INTERVIEWING"
    val label: String,
    val order: Int,
    val isSystemStage: Boolean = true
)

@Entity(
    tableName = "application_timeline",
    foreignKeys = [
        ForeignKey(
            entity = ApplicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["applicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["applicationId"])]
)
data class ApplicationTimelineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val applicationId: Long,
    val eventType: String, // STAGE_CHANGE, NOTE_ADDED, DOCUMENT_LINKED, etc.
    val title: String,
    val description: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val metadataJson: String? = null
)

@Entity(
    tableName = "application_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ApplicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["applicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["applicationId"])]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val applicationId: Long,
    val title: String,
    val description: String?,
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val dueDate: Long? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val triggerType: String, // e.g., "STAGE_ENTERED", "ATS_SCORE_THRESHOLD"
    val triggerValue: String?, // e.g., "APPLIED" or "70"
    val actionType: String, // e.g., "CREATE_TASK", "SEND_REMINDER"
    val actionParamsJson: String,
    val isEnabled: Boolean = true
)
