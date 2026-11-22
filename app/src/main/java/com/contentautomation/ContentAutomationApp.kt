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
                // Keep the cached executable so a downloaded yt-dlp update is not
                // overwritten by the older binary bundled inside the APK on next launch.
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)

                // Refresh yt-dlp from the official stable release. A network/update
                // failure must not prevent the rest of the app from starting.
                runCatching {
                    val updateStatus = YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                    Log.i("MuChiEngine", "yt-dlp update status: $updateStatus; version=${YoutubeDL.getInstance().version(applicationContext)}")
                }.onFailure { updateError ->
                    Log.w("MuChiEngine", "Could not update yt-dlp; keeping the bundled/cached version", updateError)
                }

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
