package com.contentautomation.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.contentautomation.data.db.dao.*
import com.contentautomation.data.db.entity.*

@Database(
    entities = [
        SourceAccount::class,
        SourceVideo::class,
        ProcessedVideo::class,
        TikTokPost::class,
        PipelineJob::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(
    Converters::class,
    PipelineConverters::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sourceAccountDao(): SourceAccountDao

    abstract fun sourceVideoDao(): SourceVideoDao

    abstract fun processedVideoDao(): ProcessedVideoDao

    abstract fun tiktokPostDao(): TikTokPostDao

    abstract fun pipelineJobDao(): PipelineJobDao

    companion object {

        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pipeline_jobs (
                        id TEXT NOT NULL PRIMARY KEY,
                        sourceVideoId TEXT NOT NULL,
                        stage TEXT NOT NULL,
                        status TEXT NOT NULL,
                        retries INTEGER NOT NULL,
                        error TEXT,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(sourceVideoId)
                            REFERENCES source_videos(id)
                            ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_pipeline_jobs_sourceVideoId
                    ON pipeline_jobs(sourceVideoId)
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS
                    index_pipeline_jobs_status
                    ON pipeline_jobs(status)
                """.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "content_automation.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}
