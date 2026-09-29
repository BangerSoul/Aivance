package com.bangersoul.aivance.core.database.dao

import androidx.room.Room
import com.bangersoul.aivance.core.database.AivanceDatabase
import com.bangersoul.aivance.core.database.converter.EncryptedTypeConverters
import com.bangersoul.aivance.core.database.model.CompanyEntity
import com.bangersoul.aivance.core.database.model.ApplicationEntity
import com.bangersoul.aivance.core.database.model.JobEntity
import com.bangersoul.aivance.core.database.model.SavedJobEntity
import com.bangersoul.aivance.core.database.model.ViewedJobEntity
import com.bangersoul.aivance.core.database.security.EncryptionService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Regression coverage for the jobs-table cascade-delete defect.
 *
 * `jobs` is the parent of `saved_jobs`, `viewed_jobs`, and `applications`
 * (all ON DELETE CASCADE). [JobDao.insertJob] used to be annotated
 * `@Insert(onConflict = REPLACE)`, which resolves a primary-key conflict by
 * DELETING the existing row and re-inserting it. Re-caching a job already in the
 * DB — JobRepositoryImpl.searchJobs / cacheJob re-insert at the existing id —
 * therefore fired those cascades and silently wiped the user's bookmark, viewed
 * history, and tracked application. The fix switches [JobDao.insertJob] to
 * `@Upsert` (ON CONFLICT DO UPDATE — update in place, no delete, no cascade).
 *
 * Runs under Robolectric so it executes in `testDebugUnitTest` rather than
 * requiring an emulator like the androidTest DAO suites.
 */
@RunWith(RobolectricTestRunner::class)
class JobDaoUpsertCascadeTest {

    private lateinit var db: AivanceDatabase
    private lateinit var jobDao: JobDao
    private lateinit var companyDao: CompanyDao
    private lateinit var workflowDao: WorkflowDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        // Mirrors buildTestDatabase() (androidTest source set): the schema maps
        // EncryptedString columns through EncryptedTypeConverters, so the builder
        // must register them or Room fails type-converter validation at open time.
        db = Room.inMemoryDatabaseBuilder(context, AivanceDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(EncryptedTypeConverters(EncryptionService(context)))
            .build()
        jobDao = db.jobDao()
        companyDao = db.companyDao()
        workflowDao = db.workflowDao()
    }

    @After
    fun cleanup() = db.close()

    /** Seeds one company + one job that has a bookmark, a viewed record, and a
     *  tracked application, then returns the job's stable primary-key id. */
    private suspend fun seedJobWithChildren(): Long {
        val companyId = companyDao.insertCompany(
            CompanyEntity(
                name = "Google", domain = null, logoUrl = null, website = null,
                industry = null, headquarters = null
            )
        )
        val jobId = jobDao.insertJob(
            JobEntity(
                companyId = companyId,
                title = "Android Engineer",
                location = "Remote",
                type = "FULL_TIME",
                remoteType = null,
                experienceLevel = null,
                salaryMin = null,
                salaryMax = null,
                currency = null,
                description = "Build things",
                descriptionHtml = null,
                url = "https://jobs.google.com/42",
                sourceProviderId = "GREENHOUSE",
                postedDate = 1_000L
            )
        )
        jobDao.insertSavedJob(SavedJobEntity(jobId = jobId))
        jobDao.insertViewedJob(ViewedJobEntity(jobId = jobId))
        workflowDao.insertApplication(
            ApplicationEntity(
                jobId = jobId,
                currentStageId = "APPLIED",
                status = "ACTIVE",
                dateApplied = 2_000L,
                notes = "Referred",
                lastModified = 2_000L
            )
        )
        return jobId
    }

    /**
     * Control: proves FK enforcement + CASCADE are actually active in this test
     * environment. Without this guard the regression test below could pass
     * vacuously — children would "survive" simply because cascades never fire.
     */
    @Test
    fun deletingParentJobCascadesToChildren() = runTest {
        val jobId = seedJobWithChildren()
        assertThat(jobDao.isJobSaved(jobId)).isTrue()
        assertThat(workflowDao.getAllApplications().first()).hasSize(1)

        jobDao.deleteAllJobs()

        assertThat(jobDao.isJobSaved(jobId)).isFalse()
        assertThat(workflowDao.getAllApplications().first()).isEmpty()
    }

    /**
     * Regression: re-caching a job already in the DB (same primary key) must
     * update the row in place and KEEP its children. RED under the old
     * `@Insert(REPLACE)` (the cascade wiped them); GREEN under `@Upsert`.
     */
    @Test
    fun reCachingExistingJobPreservesBookmarkViewedAndApplication() = runTest {
        val jobId = seedJobWithChildren()

        // Simulate JobRepositoryImpl re-caching the same job at its existing id.
        jobDao.insertJob(
            JobEntity(
                id = jobId,
                companyId = 1,
                title = "Android Engineer (updated)",
                location = "Remote",
                type = "FULL_TIME",
                remoteType = null,
                experienceLevel = null,
                salaryMin = null,
                salaryMax = null,
                currency = null,
                description = "Build things — refreshed",
                descriptionHtml = null,
                url = "https://jobs.google.com/42",
                sourceProviderId = "GREENHOUSE",
                postedDate = 1_000L
            )
        )

        assertThat(jobDao.isJobSaved(jobId)).isTrue()
        assertThat(jobDao.getRecentlyViewedJobs(10).first().map { it.job.id })
            .contains(jobId)
        assertThat(workflowDao.getAllApplications().first()).hasSize(1)
        // Success path: the row was updated in place, not deleted or duplicated.
        assertThat(jobDao.getJobById(jobId)?.title).isEqualTo("Android Engineer (updated)")
        assertThat(jobDao.getJobsWithDetails().first()).hasSize(1)
    }

    /**
     * Success path: a fresh insert (auto-generated id) still returns a usable,
     * positive rowid. `cacheJob` and the tracker stub rely on this value as the
     * application / cover-letter foreign key.
     */
    @Test
    fun insertingNewJobReturnsPositiveRowId() = runTest {
        val companyId = companyDao.insertCompany(
            CompanyEntity(
                name = "Meta", domain = null, logoUrl = null, website = null,
                industry = null, headquarters = null
            )
        )
        val newId = jobDao.insertJob(
            JobEntity(
                companyId = companyId,
                title = "Backend Engineer",
                location = null, type = null, remoteType = null,
                experienceLevel = null, salaryMin = null, salaryMax = null,
                currency = null, description = null, descriptionHtml = null,
                url = "https://jobs.meta.com/7", sourceProviderId = "LEVER",
                postedDate = 3_000L
            )
        )
        assertThat(newId).isGreaterThan(0L)
        assertThat(jobDao.getJobById(newId)).isNotNull()
    }
}
