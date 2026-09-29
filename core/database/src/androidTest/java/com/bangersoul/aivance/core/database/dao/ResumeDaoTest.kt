package com.bangersoul.aivance.core.database.dao

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.bangersoul.aivance.core.database.AivanceDatabase
import com.bangersoul.aivance.core.database.buildTestDatabase
import com.bangersoul.aivance.core.database.model.ResumeEntity
import com.bangersoul.aivance.core.database.model.ResumeSectionEntity
import com.bangersoul.aivance.core.database.model.ResumeVersionEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the resume upsert cascade bug: re-saving an existing
 * resume row used OnConflictStrategy.REPLACE, which deletes the old row and
 * re-inserts it — firing the ON DELETE CASCADE that wiped every child
 * resume_versions row. The Resume Engine then lost its freshly parsed version
 * and the in-wizard ATS scan failed with "Version not found".
 */
@RunWith(AndroidJUnit4::class)
class ResumeDaoTest {

    private lateinit var db: AivanceDatabase
    private lateinit var resumeDao: ResumeDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = buildTestDatabase(context)
        resumeDao = db.resumeDao()
    }

    @After
    fun cleanup() {
        db.close()
    }

    @Test
    fun reSavingExistingResumeKeepsItsVersionsAndSections() = runTest {
        val now = System.currentTimeMillis()
        val resumeId = resumeDao.insertResume(
            ResumeEntity(
                name = "DummyResume",
                fileName = "DummyResume.pdf",
                dateCreated = now,
                lastModified = now
            )
        )

        // parseResume() creates the "Original Import" version with sections.
        val versionId = resumeDao.insertVersion(
            ResumeVersionEntity(resumeId = resumeId, versionName = "Original Import")
        )
        resumeDao.insertSections(
            listOf(
                ResumeSectionEntity(
                    versionId = versionId,
                    sectionType = "summary",
                    title = "Summary",
                    content = "Alex Johnson — Senior Android Engineer",
                    sectionOrder = 0
                )
            )
        )

        // Repository then writes the primary-version id back onto the SAME
        // resume row. With REPLACE this deleted the version (CASCADE); with
        // @Upsert it must survive as an in-place UPDATE.
        resumeDao.insertResume(
            ResumeEntity(
                id = resumeId,
                name = "DummyResume",
                fileName = "DummyResume.pdf",
                primaryVersionId = versionId,
                dateCreated = now,
                lastModified = now
            )
        )

        resumeDao.getVersionsForResume(resumeId).test {
            val versions = awaitItem()
            assertThat(versions).hasSize(1)
            assertThat(versions[0].id).isEqualTo(versionId)
            assertThat(versions[0].versionName).isEqualTo("Original Import")
        }

        resumeDao.getSectionsForVersion(versionId).test {
            val sections = awaitItem()
            assertThat(sections).hasSize(1)
            assertThat(sections[0].content).contains("Senior Android Engineer")
        }
    }
}
