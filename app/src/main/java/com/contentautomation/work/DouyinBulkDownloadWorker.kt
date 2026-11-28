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
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
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
        setForeground(createForegroundInfo(videoId, "Đang chuẩn bị tải video Douyin…"))
        val workDir = File(applicationContext.cacheDir, "MuChi/DouyinArchive/$videoId").apply { mkdirs() }
        val target = File(workDir, "douyin-original.mp4")

        try {
            app.database.sourceVideoDao().updateStatus(video.id, VideoStatus.DOWNLOADING.name)
            target.delete()
            setForeground(createForegroundInfo(videoId, "Đang tải trực tiếp từ Douyin về điện thoại…"))
            setProgress(workDataOf("message" to "Đang phân giải video trên điện thoại…", "video_id" to video.id, "external_video_id" to video.externalVideoId))
            if (!app.awaitMediaEngines()) {
                throw IllegalStateException(
                    ContentAutomationApp.engineInitializationError
                        ?: "Bộ tải video trên điện thoại chưa sẵn sàng. Hãy thử lại."
                )
            }
            val cookieHeader = app.prefs.getSecret("platform_cookie_douyin").orEmpty()
            if (cookieHeader.isBlank()) throw IllegalStateException("Phiên Douyin đã hết hạn. Hãy đăng nhập lại trong Cài đặt.")
            val cookieFile = File(applicationContext.cacheDir, "douyin-${videoId}.cookies")
            try {
                val cookieLines = cookieHeader.split(";").mapNotNull { part ->
                    val pair = part.trim().split("=", limit = 2)
                    if (pair.size != 2 || pair[0].isBlank()) null
                    else ".douyin.com\tTRUE\t/\tTRUE\t0\t${pair[0].trim()}\t${pair[1].trim()}"
                }
                if (cookieLines.isEmpty()) throw IllegalStateException("Phiên Douyin không có cookie hợp lệ.")
                cookieFile.writeText("# Netscape HTTP Cookie File\n" + cookieLines.joinToString("\n"), Charsets.UTF_8)
                val request = YoutubeDLRequest(video.sourceUrl).apply {
                    addOption("--format", "best[ext=mp4]/best")
                    addOption("--merge-output-format", "mp4")
                    addOption("--remux-video", "mp4")
                    addOption("--output", target.absolutePath)
                    addOption("--no-playlist")
                    addOption("--force-ipv4")
                    addOption("--socket-timeout", "30")
                    addOption("--retries", "3")
                    addOption("--extractor-retries", "3")
                    addOption("--cookies", cookieFile.absolutePath)
                    addOption("--newline")
                }
                YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, _ ->
                    val percent = progress.toInt().coerceIn(0, 99)
                    val message = if (etaInSeconds > 0L) {
                        "Đang tải trực tiếp · $percent% · còn khoảng ${etaInSeconds}s"
                    } else {
                        "Đang tải trực tiếp · $percent%"
                    }
                    // yt-dlp writes to the final file or its temporary .part file.
                    // Report real bytes instead of incorrectly treating percent as bytes.
                    val partial = File(target.absolutePath + ".part")
                    val bytesOnDisk = maxOf(target.length(), partial.length())
                    setProgressAsync(workDataOf(
                        "message" to message,
                        "video_id" to video.id,
                        "external_video_id" to video.externalVideoId,
                        "bytes_downloaded" to bytesOnDisk,
                        "total_bytes" to -1L,
                        "progress_percent" to percent
                    ))
                    setForegroundAsync(createForegroundInfo(videoId, message))
                }
            } finally {
                cookieFile.delete()
            }
            val downloaded = target.takeIf { it.isFile && it.length() > 0L }
                ?: throw IllegalStateException("Douyin không trả file video hợp lệ về điện thoại.")
            val downloadedBytes = downloaded.length()
            setForeground(createForegroundInfo(videoId, "Đã tải trực tiếp · đang lưu vào Movies…"))
            setProgress(workDataOf("message" to "Đã tải trực tiếp · đang lưu vào Movies…", "video_id" to video.id, "external_video_id" to video.externalVideoId))

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
            setProgress(workDataOf(
                "message" to "Đã lưu video vào Movies · ${formatBytes(downloadedBytes)}",
                "video_id" to video.id,
                "external_video_id" to video.externalVideoId,
                "bytes_downloaded" to downloadedBytes,
                "total_bytes" to downloadedBytes,
                "progress_percent" to 100
            ))
            Result.success(workDataOf(
                "saved_uri" to savedUri.toString(),
                "display_name" to displayName,
                "video_id" to video.id,
                "external_video_id" to video.externalVideoId,
                "bytes_downloaded" to downloadedBytes,
                "total_bytes" to downloadedBytes,
                "progress_percent" to 100,
                "message" to "Đã lưu video vào Movies · ${formatBytes(downloadedBytes)}"
            ))
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
                .addTag("muchi-douyin-video:$externalVideoId")
                .build()
            // One unique chain serializes all selected downloads.
            WorkManager.getInstance(context)
                .beginUniqueWork(UNIQUE_QUEUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                .enqueue()
        }
    }
}
