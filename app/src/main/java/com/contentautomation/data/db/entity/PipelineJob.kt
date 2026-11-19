package com.contentautomation.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverter

enum class PipelineStage {
    DOWNLOAD,
    ANALYZE,
    SPEECH_TO_TEXT,
    TRANSLATE,
    TTS,
    SUBTITLE,
    MERGE,
    EXPORT,
    UPLOAD,
    VERIFY
}

enum class PipelineStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    PAUSED
}

@Entity(
    tableName = "pipeline_jobs",
    foreignKeys = [
        ForeignKey(
            entity = SourceVideo::class,
            parentColumns = ["id"],
            childColumns = ["sourceVideoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceVideoId"], unique = true),
        Index(value = ["status"])
    ]
)
data class PipelineJob(
    @PrimaryKey
    val id: String,

    val sourceVideoId: String,

    val stage: PipelineStage = PipelineStage.DOWNLOAD,

    val status: PipelineStatus = PipelineStatus.PENDING,

    val retries: Int = 0,

    val error: String? = null,

    val updatedAt: Long = System.currentTimeMillis()
)

class PipelineConverters {

    @TypeConverter
    fun stageToString(value: PipelineStage): String =
        value.name

    @TypeConverter
    fun stringToStage(value: String): PipelineStage =
        PipelineStage.valueOf(value)

    @TypeConverter
    fun statusToString(value: PipelineStatus): String =
        value.name

    @TypeConverter
    fun stringToStatus(value: String): PipelineStatus =
        PipelineStatus.valueOf(value)
}
