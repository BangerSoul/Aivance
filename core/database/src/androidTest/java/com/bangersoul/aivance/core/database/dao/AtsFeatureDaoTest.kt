package com.bangersoul.aivance.core.database.dao

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.bangersoul.aivance.core.database.AivanceDatabase
import com.bangersoul.aivance.core.database.buildTestDatabase
import com.bangersoul.aivance.core.database.model.AtsReportEntity
import com.bangersoul.aivance.core.database.model.JobDescriptionEntity
import com.bangersoul.aivance.core.database.model.ResumeEntity
import com.bangersoul.aivance.core.database.model.ResumeSectionEntity
import com.bangersoul.aivance.core.database.model.ResumeVersionEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtsFeatureDaoTest {

    private lateinit var db: AivanceDatabase
    private lateinit var atsDao: AtsDao
    private lateinit var resumeDao: ResumeDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = buildTestDatabase(context)
        atsDao = db.atsDao()
        resumeDao = db.resumeDao()
    }

    @After
    fun cleanup() {
        db.close()
    }

    private suspend fun insertResumeWithVersion(): Long {
        val resume = ResumeEntity(
            id = 1,
            name = "John_Doe_Resume.pdf",
            dateCreated = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
        resumeDao.insertResume(resume)
        resumeDao.insertVersion(
            ResumeVersionEntity(
                id = 1,
                resumeId = 1,
                versionName = "Main Version",
                templateId = "modern"
            )
        )
        val sections = listOf(
            ResumeSectionEntity(versionId = 1, title = "Experience", content = "...", sectionOrder = 0, sectionType = "EXPERIENCE"),
            ResumeSectionEntity(versionId = 1, title = "Skills", content = "...", sectionOrder = 1, sectionType = "SKILLS")
        )
        resumeDao.insertSections(sections)
        return 1L
    }

    @Test
    fun resumeAndAtsReportIntegration() = runTest {
        insertResumeWithVersion()

        // 2. Save the job description — ats_reports.jobDescriptionId is an
        //    enforced FK, so the JD row must exist first.
        val jdId = atsDao.insertJobDescription(
            JobDescriptionEntity(
                companyName = null, jobTitle = null, rawText = "Senior Android Engineer",
                sourceUrl = null, extractedSkills = null
            )
        )

        // 3. Perform ATS analysis and save the report
        val report = AtsReportEntity(
            id = 1,
            resumeVersionId = 1,
            jobDescriptionId = jdId,
            overallScore = 92,
            matchPercentage = 92,
            matchedKeywords = "Kotlin, Coroutines, Room",
            missingKeywords = "KMP",
            sectionScores = "{}",
            optimizationTips = "[]",
            dateGenerated = System.currentTimeMillis()
        )
        atsDao.insertReport(report)

        // 4. Verify data
        val savedResume = resumeDao.getResumeById(1)
        assertThat(savedResume?.name).isEqualTo("John_Doe_Resume.pdf")

        resumeDao.getSectionsForVersion(1).test {
            val savedSections = awaitItem()
            assertThat(savedSections).hasSize(2)
            assertThat(savedSections[0].title).isEqualTo("Experience")
        }

        atsDao.getAllReports().test {
            val savedReports = awaitItem()
            assertThat(savedReports).hasSize(1)
            assertThat(savedReports[0].overallScore).isEqualTo(92)
            assertThat(savedReports[0].resumeVersionId).isEqualTo(1)
            assertThat(savedReports[0].jobDescriptionId).isEqualTo(jdId)
        }

        atsDao.getReportsForVersion(1).test {
            val versionReports = awaitItem()
            assertThat(versionReports).hasSize(1)
        }
    }

    @Test
    fun deleteResume_cascadesToAtsReports() = runTest {
        val resume = ResumeEntity(id = 1, name = "test.pdf", dateCreated = 0, lastModified = 0)
        resumeDao.insertResume(resume)
        resumeDao.insertVersion(
            ResumeVersionEntity(id = 1, resumeId = 1, versionName = "Main", templateId = "modern")
        )
        val jdId = atsDao.insertJobDescription(
            JobDescriptionEntity(companyName = null, jobTitle = null, rawText = "Job", sourceUrl = null, extractedSkills = null)
        )

        val report = AtsReportEntity(
            id = 1,
            resumeVersionId = 1,
            jobDescriptionId = jdId,
            overallScore = 80,
            matchPercentage = 80,
            matchedKeywords = "",
            missingKeywords = "",
            sectionScores = "{}",
            optimizationTips = "[]",
            dateGenerated = 1000L
        )
        atsDao.insertReport(report)

        // Delete the resume — the version and its reports should cascade away.
        resumeDao.deleteResume(resume)

        atsDao.getAllReports().test {
            assertThat(awaitItem()).isEmpty()
        }
    }
}
