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
            ?: return@withContext Result.failure()
        val account = app.database.sourceAccountDao().findById(video.sourceAccountId)
            ?: return@withContext Result.failure()

        if (!app.awaitMediaEngines()) return@withContext Result.retry()
        setForeground(createForegroundInfo(videoId, "Đang chuẩn bị tải video Douyin…"))
        val workDir = File(applicationContext.filesDir, "MuChi/DouyinArchive/$videoId").apply { mkdirs() }
        val target = File(workDir, "douyin-original.mp4")
        val outputTemplate = File(workDir, "douyin-source.%(ext)s").absolutePath

        try {
            app.database.sourceVideoDao().updateStatus(video.id, VideoStatus.DOWNLOADING.name)
            target.delete()

            val cookie = app.prefs.getSecret("platform_cookie_douyin").orEmpty()
            val cookieFile = File.createTempFile("muchi-douyin-download-", ".cookies", applicationContext.cacheDir)
            try {
                val cookieLines = cookie.split(";").mapNotNull { part ->
                    val pair = part.trim().split("=", limit = 2)
                    if (pair.size != 2 || pair[0].isBlank()) null
                    else ".douyin.com\tTRUE\t/\tTRUE\t0\t${pair[0].trim()}\t${pair[1].trim()}"
                }
                if (cookieLines.isNotEmpty()) {
                    cookieFile.writeText("# Netscape HTTP Cookie File\n" + cookieLines.joinToString("\n"), Charsets.UTF_8)
                }
                YoutubeDL.getInstance().execute(
                    YoutubeDLRequest(video.sourceUrl).apply {
                        if (cookieLines.isNotEmpty()) addOption("--cookies", cookieFile.absolutePath)
                        addOption("--no-playlist")
                        addOption("--force-ipv4")
                        addOption("--retries", "3")
                        addOption("--socket-timeout", "30")
                        addOption("--no-warnings")
                        addOption("-o", outputTemplate)
                        addOption("-f", "best")
                    }
                )
            } finally {
                cookieFile.delete()
            }

            val downloaded = if (target.exists() && target.length() > 0L) target else {
                workDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith("douyin-source.") && it.length() > 0L }
                    ?.maxByOrNull { it.lastModified() }
            } ?: throw IllegalStateException("Không tìm thấy tệp video sau khi tải.")

            val safeAccount = account.accountName
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .take(24)
                .ifBlank { "Douyin" }
            val displayName = "Douyin_${safeAccount}_${video.externalVideoId.take(18)}.mp4"
            val savedUri = MediaStoreSaver(applicationContext)
                .saveVideoToGallery(downloaded, displayName)

            DriveBackupWorker.enqueue(
                applicationContext,
                savedUri,
                displayName,
                "douyin_${video.id}_original"
            )
            app.database.sourceVideoDao().updateStatus(video.id, VideoStatus.SAVED_TO_PHONE.name)
            setProgress(workDataOf("message" to "Đã lưu video vào Movies", "video_id" to video.id))
            Result.success(workDataOf("saved_uri" to savedUri.toString(), "display_name" to displayName))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val permanent = runAttemptCount >= 2
            app.database.sourceVideoDao().updateStatusError(
                video.id,
                if (permanent) VideoStatus.FAILED.name else VideoStatus.RETRY_PENDING.name,
                error.message
            )
            if (permanent) Result.failure() else Result.retry()
        }
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
        private const val DOWNLOAD_LANES = 3

        fun enqueue(context: Context, videoId: String) {
            val request = OneTimeWorkRequestBuilder<DouyinBulkDownloadWorker>()
                .setInputData(workDataOf(KEY_VIDEO_ID to videoId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("muchi-douyin-archive-download")
                .build()
            val lane = Math.floorMod(videoId.hashCode(), DOWNLOAD_LANES)
            WorkManager.getInstance(context).enqueueUniqueWork(
                "muchi-douyin-download-lane-$lane",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
