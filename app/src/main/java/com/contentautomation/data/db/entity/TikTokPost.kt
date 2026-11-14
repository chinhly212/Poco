package com.contentautomation.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

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
