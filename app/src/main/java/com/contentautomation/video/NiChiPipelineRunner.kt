package com.contentautomation.video

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class PipelineStep {
    object Compressing : PipelineStep()
    object UploadingThumbnail : PipelineStep()
    object SavingMetadata : PipelineStep()
    object SavingToGallery : PipelineStep()
    data class Completed(val galleryUri: Uri, val thumbnailUrl: String?) : PipelineStep()
    data class Failed(val step: String, val reason: String) : PipelineStep()
}

class NiChiPipelineRunner(
    private val context: Context,
    private val workerBaseUrl: String
) {
    private val compressor = VideoCompressor(context)
    private val thumbnailUploader = ThumbnailUploader(
        context = context,
        cloudName = "hihnixtt",
        uploadPreset = "nichi_thumbnails"
    )
    private val apiClient = NiChiApiClient(workerBaseUrl)
    private val gallerySaver = MediaStoreSaver(context)

    private fun queryFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                ?.takeIf { it > 0 } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Chạy từ nén video -> up thumbnail -> gửi Worker (sau khi đã có telegramMessageId) -> lưu Thư viện.
     * Nếu upload Telegram thực hiện trước hoặc sau bước lưu metadata, truyền telegramMessageId tương ứng.
     */
    suspend fun runPipeline(
        inputUri: Uri,
        displayName: String,
        userId: String,
        bearerToken: String,
        telegramMessageId: Long,
        onStepChanged: (PipelineStep) -> Unit
    ): PipelineStep {
        val sizeBefore = queryFileSize(inputUri)
        val tempOutput = File(context.cacheDir, "nichi_out_${System.currentTimeMillis()}.mp4")

        return try {
            // 1. Nén video bằng Media3 Transformer
            withContext(Dispatchers.Main) { onStepChanged(PipelineStep.Compressing) }
            val compressResult = withContext(Dispatchers.Main) {
                compressor.compressKeepQuality(inputUri = inputUri, outputFile = tempOutput)
            }
            if (!compressResult.success || !tempOutput.exists() || tempOutput.length() <= 0L) {
                val err = PipelineStep.Failed(
                    step = "compress",
                    reason = compressResult.errorMessage ?: "File nén rỗng hoặc lỗi"
                )
                onStepChanged(err)
                return err
            }
            val sizeAfter = tempOutput.length()

            // 2. Cắt thumbnail & đẩy lên Cloudinary (hihnixtt / nichi_thumbnails)
            withContext(Dispatchers.Main) { onStepChanged(PipelineStep.UploadingThumbnail) }
            val thumbUrl = thumbnailUploader.extractAndUpload(videoUri = inputUri, userId = userId)

            // 3. Ghi metadata qua Cloudflare Worker -> Supabase (gắn user_id)
            withContext(Dispatchers.Main) { onStepChanged(PipelineStep.SavingMetadata) }
            val apiResult = apiClient.saveVideoRecord(
                bearerToken = bearerToken,
                payload = VideoRecordPayload(
                    telegramMessageId = telegramMessageId,
                    fileName = displayName,
                    sizeBefore = sizeBefore,
                    sizeAfter = sizeAfter,
                    thumbnailUrl = thumbUrl
                )
            )
            if (apiResult.isFailure) {
                val err = PipelineStep.Failed(
                    step = "worker_api",
                    reason = apiResult.exceptionOrNull()?.message ?: "Lỗi lưu metadata"
                )
                onStepChanged(err)
                return err
            }

            // 4. Lưu video vào Thư viện thiết bị
            withContext(Dispatchers.Main) { onStepChanged(PipelineStep.SavingToGallery) }
            val savedUri = gallerySaver.saveVideoToGallery(
                sourceFile = tempOutput,
                displayName = displayName
            )
            if (savedUri == null) {
                val err = PipelineStep.Failed(
                    step = "gallery",
                    reason = "Không lưu được vào Thư viện"
                )
                onStepChanged(err)
                return err
            }

            val done = PipelineStep.Completed(galleryUri = savedUri, thumbnailUrl = thumbUrl)
            onStepChanged(done)
            done
        } finally {
            if (tempOutput.exists()) {
                tempOutput.delete()
            }
        }
    }
}
