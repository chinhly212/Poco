
package com.contentautomation

import android.app.Application
import android.util.Log
import com.contentautomation.data.db.AppDatabase
import com.contentautomation.data.prefs.AppPreferences
import com.contentautomation.domain.tiktok.TikTokClient
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ContentAutomationApp : Application() {

    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Database dùng chung toàn ứng dụng
    val database: AppDatabase by lazy {
        AppDatabase.get(applicationContext)
    }

    // Cài đặt và thông tin TikTok
    val prefs: AppPreferences by lazy {
        AppPreferences(applicationContext)
    }

    // TikTok API Client
    val tiktokClient: TikTokClient by lazy {
        TikTokClient(
            prefs = prefs,
            clientKeyProvider = {
                runBlocking {
                    prefs.tiktokClientKey.first()
                }
            },
            clientSecretProvider = {
                runBlocking {
                    prefs.tiktokClientSecret.first()
                }
            }
        )
    }

    companion object {

        @Volatile
        var isEngineInitialized: Boolean = false
            private set

        @Volatile
        var engineInitializationError: String? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Khởi tạo các thành phần dùng chung
        initializeAppComponents()

        // Khởi tạo YoutubeDL và FFmpeg ở luồng nền
        initMediaEngines()
    }

    private fun initializeAppComponents() {
        try {
            database
            prefs
            tiktokClient

            Log.d("MuChiApp", "App components initialized")
        } catch (e: Exception) {
            Log.e("MuChiApp", "App initialization error", e)
        }
    }

    private fun initMediaEngines() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                Log.d("MuChiEngine", "Initializing YoutubeDL...")

                YoutubeDL.getInstance()
                    .init(applicationContext)

                Log.d("MuChiEngine", "YoutubeDL initialized")

                Log.d("MuChiEngine", "Initializing FFmpeg...")

                FFmpeg.getInstance()
                    .init(applicationContext)

                isEngineInitialized = true
                engineInitializationError = null

                Log.d(
                    "MuChiEngine",
                    "YoutubeDL and FFmpeg initialized successfully"
                )

            } catch (e: Exception) {
                isEngineInitialized = false
                engineInitializationError = e.message

                Log.e(
                    "MuChiEngine",
                    "Media engine initialization failed",
                    e
                )
            }
        }
    }
}
