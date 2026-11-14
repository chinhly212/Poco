package com.contentautomation.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

enum class VideoStatus {
    NEW, FETCHING_METADATA, DOWNLOADING, UPLOADED_TO_DRIVE,
    PROCESSING_VIDEO, TRANSLATING, READY_TO_PUBLISH,
    PUBLISHING, PUBLISHED, FAILED, RETRY_PENDING
}

enum class Platform { KUAISHOU, DOUYIN, TIKTOK }

enum class AudioMode { ORIGINAL, TIKTOK }

class Converters {
    private val gson = Gson()
    @TypeConverter
    fun listToString(list: List<String>?): String? =
        list?.let { gson.toJson(it) }
    @TypeConverter
    fun stringToList(value: String?): List<String>? =
        value?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(it, type)
        }
}

@Entity(tableName = "source_accounts")
data class SourceAccount(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val platform: String,
    @ColumnInfo(name = "account_name") val accountName: String,
    @ColumnInfo(name = "account_identifier") val accountIdentifier: String,
    val enabled: Boolean = true,
    @ColumnInfo(name = "last_scan_at") val lastScanAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "source_videos",
    foreignKeys = [ForeignKey(
        entity = SourceAccount::class,
        parentColumns = ["id"],
        childColumns = ["source_account_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("source_account_id"),
        Index(value = ["source_account_id", "external_video_id"], unique = true)
    ]
)
data class SourceVideo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "source_account_id") val sourceAccountId: String,
    @ColumnInfo(name = "external_video_id") val externalVideoId: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    val title: String? = null,
    @ColumnInfo(name = "description_zh") val descriptionZh: String? = null,
    @ColumnInfo(name = "published_at") val publishedAt: Long? = null,
    @ColumnInfo(name = "discovered_at") val discoveredAt: Long = System.currentTimeMillis(),
    val status: String = VideoStatus.NEW.name,
    @ColumnInfo(name = "content_hash") val contentHash: String? = null,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null
)

@Entity(
    tableName = "processed_videos",
    foreignKeys = [ForeignKey(
        entity = SourceVideo::class,
        parentColumns = ["id"],
        childColumns = ["source_video_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("source_video_id")]
)
data class ProcessedVideo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "source_video_id") val sourceVideoId: String,
    @ColumnInfo(name = "drive_original_file_id") val driveOriginalFileId: String? = null,
    @ColumnInfo(name = "drive_processed_file_id") val driveProcessedFileId: String? = null,
    @ColumnInfo(name = "original_path") val originalPath: String? = null,
    @ColumnInfo(name = "processed_path") val processedPath: String? = null,
    @ColumnInfo(name = "description_vi") val descriptionVi: String? = null,
    val hashtags: List<String> = emptyList(),
    @ColumnInfo(name = "audio_mode") val audioMode: String = AudioMode.ORIGINAL.name,
    val status: String = VideoStatus.READY_TO_PUBLISH.name,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tiktok_posts",
    foreignKeys = [ForeignKey(
        entity = ProcessedVideo::class,
        parentColumns = ["id"],
        childColumns = ["processed_video_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("processed_video_id")]
)
data class TikTokPost(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "processed_video_id") val processedVideoId: String,
    @ColumnInfo(name = "tiktok_account_id") val tiktokAccountId: String,
    @ColumnInfo(name = "publish_id") val publishId: String? = null,
    @ColumnInfo(name = "privacy_level") val privacyLevel: String? = null,
    val status: String = "PENDING",
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "published_at") val publishedAt: Long? = null
)
