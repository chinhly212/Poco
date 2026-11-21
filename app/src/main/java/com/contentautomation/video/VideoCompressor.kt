package com.contentautomation.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

data class CompressResult(
    val success: Boolean,
    val outputFile: File? = null,
    val errorMessage: String? = null
)

class VideoCompressor(private val context: Context) {

    suspend fun compressKeepQuality(
        inputUri: Uri,
        outputFile: File,
        targetBitrate: Int = 3_500_000,
        maxHeight: Int = 1080
    ): CompressResult = withContext(Dispatchers.Main.immediate) {
        if (outputFile.exists()) outputFile.delete()

        suspendCancellableCoroutine { cont ->
            val videoEffects = listOf(Presentation.createForHeight(maxHeight))
            val encoderSettings = VideoEncoderSettings.Builder()
                .setBitrate(targetBitrate)
                .build()
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(encoderSettings)
                .build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        if (cont.isActive) {
                            cont.resume(
                                if (outputFile.exists() && outputFile.length() > 0L) {
                                    CompressResult(success = true, outputFile = outputFile)
                                } else {
                                    CompressResult(
                                        success = false,
                                        errorMessage = "Bộ mã hóa kết thúc nhưng tệp đầu ra rỗng."
                                    )
                                }
                            )
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) {
                            cont.resume(
                                CompressResult(
                                    success = false,
                                    errorMessage = exportException.message ?: "Lỗi nén video"
                                )
                            )
                        }
                    }
                })
                .build()

            cont.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post {
                    transformer.cancel()
                }
            }

            try {
                val mediaItem = MediaItem.fromUri(inputUri)
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(
                        CompressResult(
                            success = false,
                            errorMessage = e.message ?: "Không thể bắt đầu nén video"
                        )
                    )
                }
            }
        }
    }
}
