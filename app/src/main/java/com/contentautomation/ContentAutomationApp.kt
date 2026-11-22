package com.contentautomation

import android.app.Application
import android.util.Log
import com.contentautomation.data.db.AppDatabase
import com.contentautomation.data.prefs.AppPreferences
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class ContentAutomationApp : Application() {

    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mediaEnginesReady = CompletableDeferred<Boolean>()

    val database: AppDatabase by lazy {
        AppDatabase.get(applicationContext)
    }

    val prefs: AppPreferences by lazy {
        AppPreferences(applicationContext)
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
        initializeAppComponents()
        initMediaEngines()
    }

    suspend fun awaitMediaEngines(timeoutMillis: Long = 60_000L): Boolean {
        return withTimeoutOrNull(timeoutMillis) {
            mediaEnginesReady.await()
        } ?: false
    }

    private fun initializeAppComponents() {
        try {
            database
            prefs
            Log.d("MuChiApp", "App components initialized")
        } catch (e: Exception) {
            Log.e("MuChiApp", "App initialization error", e)
        }
    }

    private fun initMediaEngines() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                // Remove the previously cached yt-dlp executable. The library only copies
                // its bundled executable when the file is absent, so an older install can
                // otherwise keep running a binary that does not recognize --js-runtimes.
                val cachedYtDlp = File(
                    noBackupFilesDir,
                    "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}"
                )
                if (cachedYtDlp.exists() && !cachedYtDlp.delete()) {
                    throw IllegalStateException("Không thể cập nhật bộ yt-dlp cũ: ${cachedYtDlp.absolutePath}")
                }
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)

                isEngineInitialized = true
                engineInitializationError = null
                mediaEnginesReady.complete(true)
                Log.d("MuChiEngine", "YoutubeDL and FFmpeg initialized")
            } catch (e: Exception) {
                isEngineInitialized = false
                engineInitializationError = e.message
                if (!mediaEnginesReady.isCompleted) {
                    mediaEnginesReady.complete(false)
                }
                Log.e("MuChiEngine", "Media engine initialization failed", e)
            }
        }
    }
}
