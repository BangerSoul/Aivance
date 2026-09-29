package com.bangersoul.aivance.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.bangersoul.aivance.core.database.model.JobEntity
import com.bangersoul.aivance.core.database.model.JobWithDetails
import com.bangersoul.aivance.core.database.model.SavedJobEntity
import com.bangersoul.aivance.core.database.model.ViewedJobEntity
import com.bangersoul.aivance.core.database.model.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Transaction
    @Query("SELECT * FROM jobs ORDER BY postedDate DESC")
    fun getJobsPagingSource(): PagingSource<Int, JobWithDetails>

    @Transaction
    @Query("SELECT * FROM jobs ORDER BY postedDate DESC")
    fun getJobsWithDetails(): Flow<List<JobWithDetails>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getJobById(id: Long): JobEntity?

    @Transaction
    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getJobWithDetailsById(id: Long): JobWithDetails?

    /** Looks up a cached job row by its provider URL — used to dedupe caches. */
    @Query("SELECT * FROM jobs WHERE url = :url LIMIT 1")
    suspend fun getJobByUrl(url: String): JobEntity?

    // @Upsert (not REPLACE): the jobs table is a parent for saved_jobs,
    // viewed_jobs, and applications (all ON DELETE CASCADE). INSERT OR REPLACE
    // resolves a primary-key conflict by DELETING the existing job row and
    // re-inserting it, which fires those cascades and silently wipes the user's
    // bookmark, viewed-history, and tracked application (plus its timeline and
    // tasks) every time a job already in the DB is re-cached at the same id.
    // @Upsert emits ON CONFLICT DO UPDATE — the row is updated in place, so no
    // delete fires and the children survive. Callers that consume the returned
    // rowid must resolve a stable id themselves (see JobRepositoryImpl.cacheJob),
    // because @Upsert returns -1 on the update path.
    @Upsert
    suspend fun insertJob(job: JobEntity): Long

    @Upsert
    suspend fun insertJobs(jobs: List<JobEntity>)

    @Query("DELETE FROM jobs WHERE postedDate < :beforeTimestamp")
    suspend fun deleteJobsOlderThan(beforeTimestamp: Long): Int

    @Query("DELETE FROM jobs")
    suspend fun deleteAllJobs()

    // Saved Jobs (Bookmarks)
    @Query("SELECT jobId FROM saved_jobs ORDER BY dateSaved DESC")
    fun getSavedJobIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedJob(savedJob: SavedJobEntity)

    @Delete
    suspend fun deleteSavedJob(savedJob: SavedJobEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_jobs WHERE jobId = :jobId)")
    suspend fun isJobSaved(jobId: Long): Boolean

    // Viewed History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertViewedJob(viewedJob: ViewedJobEntity)

    @Transaction
    @Query("SELECT j.* FROM jobs j INNER JOIN viewed_jobs v ON j.id = v.jobId ORDER BY v.lastViewed DESC LIMIT :limit")
    fun getRecentlyViewedJobs(limit: Int): Flow<List<JobWithDetails>>

    // Search History
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getSearchHistory(limit: Int): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(search: SearchHistoryEntity)
}
