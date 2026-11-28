package com.contentautomation.work

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.contentautomation.ContentAutomationApp
import com.contentautomation.MainActivity
import com.contentautomation.data.db.entity.VideoStatus
import com.contentautomation.video.MediaStoreSaver
import com.contentautomation.video.AiVideoServerClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads one user-selected Douyin video as-is to the phone's Movies library.
 * It deliberately does not enter the AI dubbing/translation pipeline.
 */
class DouyinBulkDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as ContentAutomationApp
        val videoId = inputData.getString(KEY_VIDEO_ID)
            ?: return@withContext Result.failure()
        val video = app.database.sourceVideoDao().findById(videoId)
            ?: return@withContext Result.success(
                workDataOf(
                    "external_video_id" to inputData.getString(KEY_EXTERNAL_VIDEO_ID).orEmpty(),
                    "message" to "Video không còn trong danh sách tải.",
                    "download_failed" to true
                )
            )
        val account = app.database.sourceAccountDao().findById(video.sourceAccountId)
            ?: run {
                app.database.sourceVideoDao().updateStatusError(
                    video.id,
                    VideoStatus.FAILED.name,
                    "Không tìm thấy tài khoản Douyin."
                )
                return@withContext Result.success(
                    workDataOf(
                        "video_id" to video.id,
                        "external_video_id" to video.externalVideoId,
                        "message" to "Không tìm thấy tài khoản Douyin.",
                        "download_failed" to true
                    )
                )
            }

        setProgress(
            workDataOf(
                "message" to "Đang chuẩn bị tải video Douyin…",
                "video_id" to video.id,
                "external_video_id" to video.externalVideoId,
                "bytes_downloaded" to 0L,
                "total_bytes" to -1L,
                "progress_percent" to 0
            )
        )
        if (!app.awaitMediaEngines()) return@withContext Result.retry()
        setForeground(createForegroundInfo(videoId, "Đang chuẩn bị tải video Douyin…"))
        val workDir = File(applicationContext.cacheDir, "MuChi/DouyinArchive/$videoId").apply { mkdirs() }
        val target = File(workDir, "douyin-original.mp4")

        try {
            app.database.sourceVideoDao().updateStatus(video.id, VideoStatus.DOWNLOADING.name)
            target.delete()
            setForeground(createForegroundInfo(videoId, "Đang yêu cầu máy chủ phân giải video Douyin…"))
            setProgress(workDataOf("message" to "Đang phân giải video Douyin…", "video_id" to video.id, "external_video_id" to video.externalVideoId))
            val downloadResult = AiVideoServerClient().downloadVideo(video.sourceUrl, target) { received, total ->
                val percent = if (total != null && total > 0L) {
                    ((received * 100L) / total).toInt().coerceIn(0, 99)
                } else 0
                val receivedLabel = formatBytes(received)
                val totalLabel = total?.takeIf { it > 0L }?.let(::formatBytes)
                val message = if (totalLabel != null) {
                    "Đang tải video · $receivedLabel / $totalLabel"
                } else {
                    "Đang tải video · $receivedLabel"
                }
                setProgress(
                    workDataOf(
                        "message" to message,
                        "video_id" to video.id,
                        "external_video_id" to video.externalVideoId,
                        "bytes_downloaded" to received,
                        "total_bytes" to (total ?: -1L),
                        "progress_percent" to percent
                    )
                )
                setForeground(createForegroundInfo(videoId, message))
            }
            val downloaded = target.takeIf { it.isFile && it.length() > 0L }
                ?: throw IllegalStateException("Máy chủ không trả file video hợp lệ.")
            if (downloadResult.bytesWritten != downloaded.length()) {
                throw IllegalStateException("Kích thước video tải về không khớp dữ liệu đã nhận.")
            }
            setForeground(createForegroundInfo(videoId, "Đã nhận video · đang lưu vào Movies…"))
            setProgress(workDataOf("message" to "Đã nhận video · đang lưu vào Movies…", "video_id" to video.id, "external_video_id" to video.externalVideoId))

            val safeAccount = account.accountName
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .take(24)
                .ifBlank { "Douyin" }
            val displayName = "Douyin_${safeAccount}_${video.externalVideoId.take(18)}.mp4"
            val savedUri = MediaStoreSaver(applicationContext)
                .saveVideoToGallery(downloaded, displayName)
            if (savedUri.toString().isBlank()) throw IllegalStateException("Android chưa xác nhận vị trí video đã lưu.")
            downloaded.delete()

            DriveBackupWorker.enqueue(
                applicationContext,
                savedUri,
                displayName,
                "douyin_${video.id}_original"
            )
            app.database.sourceVideoDao().updateStatus(video.id, VideoStatus.SAVED_TO_PHONE.name)
            setForeground(createForegroundInfo(videoId, "Đã lưu video vào Movies"))
            setProgress(workDataOf("message" to "Đã lưu video vào Movies · ${downloadResult.bytesWritten} byte", "video_id" to video.id, "external_video_id" to video.externalVideoId, "progress_percent" to 100))
            Result.success(workDataOf("saved_uri" to savedUri.toString(), "display_name" to displayName, "video_id" to video.id, "external_video_id" to video.externalVideoId, "progress_percent" to 100))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            target.delete()
            val permanent = runAttemptCount >= 3
            app.database.sourceVideoDao().updateStatusError(
                video.id,
                if (permanent) VideoStatus.FAILED.name else VideoStatus.RETRY_PENDING.name,
                error.message
            )
            if (permanent) Result.success(workDataOf("video_id" to video.id, "external_video_id" to video.externalVideoId, "message" to (error.message ?: "Tải video thất bại."), "progress_percent" to 0, "download_failed" to true)) else Result.retry()
        }
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024L) return "$value B"
        val units = listOf("KB", "MB", "GB", "TB")
        var amount = value.toDouble()
        var index = -1
        do { amount /= 1024.0; index++ } while (amount >= 1024.0 && index < units.lastIndex)
        return String.format(java.util.Locale.getDefault(), "%.1f %s", amount, units[index])
    }

    private fun createForegroundInfo(videoId: String, message: String): ForegroundInfo {
        val channelId = "muchi_douyin_downloads"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Tải video Douyin", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("MuChi · Tải Douyin")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val notificationId = 0x200 + Math.floorMod(videoId.hashCode(), 100000)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        private const val KEY_VIDEO_ID = "video_id"
        private const val KEY_EXTERNAL_VIDEO_ID = "external_video_id"
        private const val UNIQUE_QUEUE_NAME = "muchi-douyin-sequential-download-queue"

        fun enqueue(context: Context, videoId: String, externalVideoId: String) {
            val request = OneTimeWorkRequestBuilder<DouyinBulkDownloadWorker>()
                .setInputData(workDataOf(KEY_VIDEO_ID to videoId, KEY_EXTERNAL_VIDEO_ID to externalVideoId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("muchi-douyin-archive-download")
                .build()
            // One unique chain serializes all selected downloads.
            WorkManager.getInstance(context)
                .beginUniqueWork(UNIQUE_QUEUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                .enqueue()
        }
    }
}
