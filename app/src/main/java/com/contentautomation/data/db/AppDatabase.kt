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
        PipelineJob::class,
        StoredNotification::class
    ],
    version = 4,
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


    abstract fun pipelineJobDao(): PipelineJobDao

    abstract fun notificationDao(): NotificationDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS tiktok_posts")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stored_notifications (
                        id TEXT NOT NULL PRIMARY KEY,
                        kind TEXT NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        time TEXT NOT NULL,
                        action_label TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stored_notifications_created_at ON stored_notifications(created_at)")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}
