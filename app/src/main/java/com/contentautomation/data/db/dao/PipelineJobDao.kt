package com.contentautomation.data.db.dao

import androidx.room.*
import com.contentautomation.data.db.entity.PipelineJob
import com.contentautomation.data.db.entity.PipelineStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineJobDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(job: PipelineJob): Long

    @Update
    suspend fun update(job: PipelineJob)

    @Query("SELECT * FROM pipeline_jobs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PipelineJob>>

    @Query("SELECT * FROM pipeline_jobs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PipelineJob?

    @Query("""
        SELECT * FROM pipeline_jobs
        WHERE status = 'PENDING'
        ORDER BY updatedAt ASC
        LIMIT 1
    """)
    suspend fun next(): PipelineJob?

    @Query("""
        UPDATE pipeline_jobs
        SET status = 'PENDING',
            updatedAt = :now
        WHERE status = 'RUNNING'
    """)
    suspend fun resetRunning(now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE pipeline_jobs
        SET status = 'PAUSED',
            updatedAt = :now
        WHERE status = 'RUNNING'
    """)
    suspend fun pauseRunning(now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE pipeline_jobs
        SET status = 'PENDING',
            error = NULL,
            updatedAt = :now
        WHERE status = 'PAUSED'
    """)
    suspend fun resumePaused(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM pipeline_jobs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM pipeline_jobs WHERE status = :status")
    suspend fun countByStatus(status: PipelineStatus): Int
}
