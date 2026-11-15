
package com.contentautomation

import android.app.Application
import androidx.work.Configuration
import com.contentautomation.data.db.AppDatabase
import com.contentautomation.data.prefs.AppPreferences
import com.contentautomation.domain.tiktok.TikTokClient
import com.contentautomation.domain.translate.TranslationService
import com.contentautomation.work.WorkScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ContentAutomationApp : Application(), Configuration.Provider {

    val database by lazy { AppDatabase.get(this) }
    val prefs by lazy { AppPreferences(this) }

    val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    val translationService by lazy {
        TranslationService {
            runBlocking { prefs.translationApiKey.first() }
        }
    }

    val tiktokClient by lazy {
        TikTokClient(
            prefs,
            clientKeyProvider = {
                runBlocking { prefs.tiktokClientKey.first() }
            },
            clientSecretProvider = {
                runBlocking { prefs.tiktokClientSecret.first() }
            }
        )
    }

    fun defaultHashtag(): String =
        runBlocking { prefs.defaultHashtag.first() }

    fun currentScanIntervalSeconds(): Int =
        runBlocking { prefs.scanIntervalSeconds.first() }

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        try {
            WorkScheduler.schedulePeriodicScan(this)
        } catch (e: Exception) {
            android.util.Log.e(
                "AppInit",
                "Lỗi WorkManager: ${e.message}"
            )
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
