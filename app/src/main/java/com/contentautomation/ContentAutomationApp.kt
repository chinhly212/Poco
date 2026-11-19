package com.contentautomation

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ContentAutomationApp : Application() {

    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {

        @Volatile
        var isEngineInitialized = false
            private set

        @Volatile
        var engineInitializationError: String? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()

        initMediaEngines()
    }

    private fun initMediaEngines() {

        applicationScope.launch(Dispatchers.IO) {

            try {

                Log.d("MuChiEngine", "Đang khởi tạo YoutubeDL...")

                YoutubeDL.getInstance()
                    .init(this@ContentAutomationApp)

                Log.d("MuChiEngine", "YoutubeDL khởi tạo thành công")

                Log.d("MuChiEngine", "Đang khởi tạo FFmpeg...")

                FFmpeg.getInstance()
                    .init(this@ContentAutomationApp)

                isEngineInitialized = true
                engineInitializationError = null

                Log.d(
                    "MuChiEngine",
                    "Khởi tạo YoutubeDL và FFmpeg thành công"
                )

            } catch (e: Exception) {

                isEngineInitialized = false
                engineInitializationError = e.message

                Log.e(
                    "MuChiEngine",
                    "Khởi tạo Media Engine thất bại",
                    e
                )
            }
        }
    }
}
