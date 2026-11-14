package com.contentautomation.work

import android.content.Context
import androidx.work.*
import com.contentautomation.data.db.entity.VideoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class ProcessWorker(ctx: Context, params: WorkerParameters) : BaseWorker(ctx, params) {

    companion object {
        const val KEY_VIDEO_ID = "videoId"
        fun enqueue(context: Context, videoId: String) {
            val req = OneTimeWorkRequestBuilder<ProcessWorker>()
                .setInputData(workDataOf(KEY_VIDEO_ID to videoId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .beginUniqueWork("process-$videoId", ExistingWorkPolicy.REPLACE, req)
                .enqueue()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val video = db.sourceVideoDao().findById(videoId) ?: return@withContext Result.failure()
        val pv = db.processedVideoDao().findBySourceId(videoId) ?: return@withContext Result.failure()
        val input = pv.originalPath ?: return@withContext Result.failure()

        try {
            db.sourceVideoDao().updateStatus(videoId, VideoStatus.PROCESSING_VIDEO.name)
            val dir = File(applicationContext.filesDir, "videos").apply { mkdirs() }
            val tmpAudio = File(dir, "${video.externalVideoId}_audio.mp4")
            val out = File(dir, "${video.externalVideoId}_tiktok.mp4")

            app.videoProcessor.ensureAudioTrack(input, tmpAudio.absolutePath)
            app.videoProcessor.processToTikTok(tmpAudio.absolutePath, out.absolutePath)

            // Upload processed lên Drive
            var processedDriveId: String? = null
            runCatching {
                val folderId = app.driveFolderId()
                if (folderId.isNotBlank()) {
                    val account = db.sourceAccountDao().findById(video.sourceAccountId)
                    val folderName = "${account?.platform?.lowercase()}-${account?.accountName}"
                    val sub = app.driveService.ensureFolder(folderId, folderName)
                    val proc = app.driveService.ensureFolder(sub, "processed")
                    processedDriveId = app.driveService.upload(
                        out.absolutePath, proc, "video/mp4", "${video.externalVideoId}_tiktok.mp4"
                    )
                }
            }

            db.processedVideoDao().insert(
                pv.copy(processedPath = out.absolutePath, driveProcessedFileId = processedDriveId)
            )
            db.sourceVideoDao().updateStatus(videoId, VideoStatus.TRANSLATING.name)

            // Dịch mô tả
            val descVi = try {
                app.translationService.translate("zh", "vi", video.descriptionZh ?: "")
            } catch (e: Exception) {
                android.util.Log.w("ProcessWorker", "Translate failed: ${e.message}")
                ""
            }

            // Hashtags
            val hashtags = extractHashtags(video.descriptionZh ?: "")
            val defaultTag = app.defaultHashtag()
            val allTags = (listOf(defaultTag) + hashtags).distinct()

            db.processedVideoDao().insert(
                pv.copy(
                    processedPath = out.absolutePath,
                    driveProcessedFileId = processedDriveId,
                    descriptionVi = descVi,
                    hashtags = allTags,
                    status = VideoStatus.READY_TO_PUBLISH.name
                )
            )
            db.sourceVideoDao().updateStatus(videoId, VideoStatus.READY_TO_PUBLISH.name)

            // KHÔNG auto-publish — user bấm nút trong UI (đúng spec mục 13)
            Result.success()
        } catch (e: Exception) {
            db.sourceVideoDao().updateStatusError(videoId, VideoStatus.FAILED.name, e.message)
            Result.retry()
        }
    }

    private fun extractHashtags(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return Regex("#[\\p{L}0-9_]+").findAll(text).map { it.value }.take(10).toList()
    }
}
