package com.bangersoul.aivance.core.domain.repository

import android.net.Uri
import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeSection
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.common.result.CoreResult
import kotlinx.coroutines.flow.Flow

interface ResumeRepository {
    fun getResumes(): Flow<CoreResult<List<Resume>>>
    fun getResumeById(id: Long): Flow<CoreResult<Resume>>
    suspend fun saveResume(resume: Resume): CoreResult<Long>
    suspend fun deleteResume(id: Long): CoreResult<Unit>

    // Versions
    fun getVersions(resumeId: Long): Flow<CoreResult<List<ResumeVersion>>>
    suspend fun saveVersion(version: ResumeVersion): CoreResult<Long>
    suspend fun deleteVersion(resumeId: Long, versionId: Long): CoreResult<Unit>

    // Import & Parsing
    suspend fun importResume(uri: Uri): CoreResult<Long>
    suspend fun parseResume(resumeId: Long): CoreResult<Unit>

    // Analysis
    suspend fun analyzeResume(resumeId: Long, versionId: Long, jobDescription: String): CoreResult<AtsReport>
}

