package com.contentautomation.work

import android.content.Context
import androidx.work.*
import com.contentautomation.data.db.entity.AudioMode
import com.contentautomation.data.db.entity.ProcessedVideo
import com.contentautomation.data.db.entity.VideoStatus
import com.contentautomation.domain.source.SourceAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadWorker(ctx: Context, params: WorkerParameters) : BaseWorker(ctx, params) {

    companion object {
        const val KEY_VIDEO_ID = "videoId"

        fun enqueue(context: Context, videoId: String) {
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_VIDEO_ID to videoId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .beginUniqueWork("download-$videoId", ExistingWorkPolicy.REPLACE, req)
                .enqueue()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val video = db.sourceVideoDao().findById(videoId) ?: return@withContext Result.failure()
        val account = db.sourceAccountDao().findById(video.sourceAccountId)
            ?: return@withContext Result.failure()

        try {
            db.sourceVideoDao().updateStatus(videoId, VideoStatus.DOWNLOADING.name)

            val adapter = SourceAdapterFactory.create(account.platform)
            val url = adapter.getDownloadUrl(video.externalVideoId)
                ?: throw IllegalStateException("Không lấy được URL tải (platform có thể chặn)")

            val dir = File(applicationContext.filesDir, "videos").apply { mkdirs() }
            val outFile = File(dir, "${video.externalVideoId}.mp4")

            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url).build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                    resp.body?.byteStream()?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw RuntimeException("Empty body")
                }

            // Upload lên Drive (nếu được cấu hình) — nếu không, chỉ lưu local
            var driveFileId: String? = null
            try {
                val folderId = app.driveFolderId()
                if (folderId.isNotBlank()) {
                    val folderName = "${account.platform.lowercase()}-${account.accountName}"
                    val sub = app.driveService.ensureFolder(folderId, folderName)
                    val orig = app.driveService.ensureFolder(sub, "original")
                    driveFileId = app.driveService.upload(
                        outFile.absolutePath, orig, "video/mp4",
                        "${video.externalVideoId}.mp4"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("DownloadWorker", "Drive upload failed: ${e.message}")
            }

            val existing = db.processedVideoDao().findBySourceId(videoId)
            val pv = (existing ?: ProcessedVideo(sourceVideoId = videoId)).copy(
                driveOriginalFileId = driveFileId,
                originalPath = outFile.absolutePath,
                audioMode = AudioMode.ORIGINAL.name,
                status = if (driveFileId != null) VideoStatus.UPLOADED_TO_DRIVE.name else VideoStatus.PROCESSING_VIDEO.name
            )
            db.processedVideoDao().insert(pv)
            db.sourceVideoDao().updateStatus(videoId, VideoStatus.UPLOADED_TO_DRIVE.name)

            ProcessWorker.enqueue(applicationContext, videoId)
            Result.success()
        } catch (e: Exception) {
            db.sourceVideoDao().updateStatusError(videoId, VideoStatus.FAILED.name, e.message)
            val retry = video.retryCount + 1
            if (retry < 5) {
                db.sourceVideoDao().updateStatus(videoId, VideoStatus.RETRY_PENDING.name)
                Result.retry()
            } else Result.failure()
        }
    }
}
