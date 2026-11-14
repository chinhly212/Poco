package com.contentautomation.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.contentautomation.data.db.dao.*
import com.contentautomation.data.db.entity.*

@Database(
    entities = [SourceAccount::class, SourceVideo::class, ProcessedVideo::class, TikTokPost::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceAccountDao(): SourceAccountDao
    abstract fun sourceVideoDao(): SourceVideoDao
    abstract fun processedVideoDao(): ProcessedVideoDao
    abstract fun tiktokPostDao(): TikTokPostDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "content_automation.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
