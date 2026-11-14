package com.contentautomation.data.db.dao

import androidx.room.*
import com.contentautomation.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: SourceAccount): Long
    @Update suspend fun update(account: SourceAccount)
    @Delete suspend fun delete(account: SourceAccount)
    @Query("SELECT * FROM source_accounts ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SourceAccount>>
    @Query("SELECT * FROM source_accounts WHERE enabled = 1")
    suspend fun getEnabled(): List<SourceAccount>
    @Query("SELECT * FROM source_accounts WHERE id = :id")
    suspend fun findById(id: String): SourceAccount?
    @Query("UPDATE source_accounts SET last_scan_at = :ts WHERE id = :id")
    suspend fun updateLastScan(id: String, ts: Long)
}

@Dao
interface SourceVideoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(video: SourceVideo): Long
    @Update suspend fun update(video: SourceVideo)
    @Query("SELECT COUNT(*) FROM source_videos WHERE source_account_id = :accountId AND external_video_id = :extId")
    suspend fun exists(accountId: String, extId: String): Int
    @Query("SELECT * FROM source_videos WHERE id = :id")
    suspend fun findById(id: String): SourceVideo?
    @Query("SELECT * FROM source_videos WHERE status IN (:statuses) ORDER BY discovered_at DESC")
    suspend fun findByStatuses(statuses: List<String>): List<SourceVideo>
    @Query("SELECT * FROM source_videos ORDER BY discovered_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SourceVideo>>
    @Query("UPDATE source_videos SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
    @Query("UPDATE source_videos SET status = :status, last_error = :err, retry_count = retry_count + 1 WHERE id = :id")
    suspend fun updateStatusError(id: String, status: String, err: String?)
    @Query("SELECT COUNT(*) FROM source_videos WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>
    @Query("SELECT * FROM source_videos WHERE status = :status ORDER BY discovered_at DESC")
    fun observeByStatus(status: String): Flow<List<SourceVideo>>
}

@Dao
interface ProcessedVideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: ProcessedVideo): Long
    @Update suspend fun update(video: ProcessedVideo)
    @Query("SELECT * FROM processed_videos WHERE id = :id")
    suspend fun findById(id: String): ProcessedVideo?
    @Query("SELECT * FROM processed_videos WHERE source_video_id = :sourceId")
    suspend fun findBySourceId(sourceId: String): ProcessedVideo?
    @Query("SELECT * FROM processed_videos WHERE status = :status ORDER BY created_at DESC")
    fun observeByStatus(status: String): Flow<List<ProcessedVideo>>
    @Query("SELECT * FROM processed_videos ORDER BY created_at DESC LIMIT 200")
    fun observeAll(): Flow<List<ProcessedVideo>>
    @Query("SELECT COUNT(*) FROM processed_videos WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>
}

@Dao
interface TikTokPostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: TikTokPost): Long
    @Update suspend fun update(post: TikTokPost)
    @Query("SELECT * FROM tiktok_posts ORDER BY created_at DESC LIMIT 200")
    fun observeAll(): Flow<List<TikTokPost>>
    @Query("SELECT * FROM tiktok_posts WHERE id = :id")
    suspend fun findById(id: String): TikTokPost?
    @Query("SELECT * FROM tiktok_posts WHERE publish_id = :publishId LIMIT 1")
    suspend fun findByPublishId(publishId: String): TikTokPost?
    @Query("SELECT * FROM tiktok_posts WHERE status IN ('PENDING','PROCESSING_UPLOAD','PROCESSING_DOWNLOAD')")
    suspend fun findInFlight(): List<TikTokPost>
    @Query("SELECT * FROM tiktok_posts WHERE status = :status ORDER BY created_at DESC")
    fun observeByStatus(status: String): Flow<List<TikTokPost>>
    @Query("SELECT COUNT(*) FROM tiktok_posts WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>
}
