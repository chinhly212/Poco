package com.contentautomation

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.contentautomation.data.db.AppDatabase
import com.contentautomation.data.prefs.AppPreferences
import com.contentautomation.domain.drive.GoogleDriveService
import com.contentautomation.domain.ffmpeg.VideoProcessor
import com.contentautomation.domain.tiktok.TikTokClient
import com.contentautomation.domain.translate.TranslationService
import com.contentautomation.work.WorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ContentAutomationApp : Application(), Configuration.Provider {

    val database by lazy { AppDatabase.get(this) }
    val prefs by lazy { AppPreferences(this) }
    val driveService by lazy { GoogleDriveService(this) }
    val videoProcessor by lazy { VideoProcessor() }
    val translationService by lazy {
        TranslationService { runBlocking { prefs.translationApiKey.first() } }
    }
    val tiktokClient by lazy {
        TikTokClient(
            prefs,
            clientKeyProvider = { runBlocking { prefs.tiktokClientKey.first() } },
            clientSecretProvider = { runBlocking { prefs.tiktokClientSecret.first() } }
        )
    }

    fun driveFolderId(): String = runBlocking { prefs.driveFolderId.first() }
    fun defaultHashtag(): String = runBlocking { prefs.defaultHashtag.first() }
    fun currentScanIntervalSeconds(): Int = runBlocking { prefs.scanIntervalSeconds.first() }

    override fun onCreate() {
        super.onCreate()
        WorkScheduler.schedulePeriodicScan(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
