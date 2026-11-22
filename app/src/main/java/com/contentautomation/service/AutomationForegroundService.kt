package com.contentautomation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager

import com.contentautomation.ContentAutomationApp
import com.contentautomation.MainActivity
import com.contentautomation.data.db.entity.PipelineStatus
import com.contentautomation.work.PipelineWorker
import com.contentautomation.work.ScanWorker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutomationForegroundService : Service() {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var scanJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        startForeground(
            NOTIF_ID,
            buildNotification("Đang chạy quét tự động...")
        )

        if (scanJob == null || scanJob?.isActive == false) {

            scanJob = scope.launch {

                val app =
                    applicationContext as ContentAutomationApp

                // Khôi phục các pipeline đang chờ sau khi Android dừng tiến trình.
                // Chỉ khôi phục PENDING; PAUSED là trạng thái người dùng chủ động
                // tạm dừng (ví dụ đang chờ chọn nhạc TikTok) nên tuyệt đối không tự chạy.
                restorePendingPipelines(app)

                while (isActive) {

                    try {
                        val request =
                            OneTimeWorkRequestBuilder<ScanWorker>()
                                .build()

                        WorkManager.getInstance(applicationContext)
                            .enqueueUniqueWork(
                                SCAN_WORK_NAME,
                                ExistingWorkPolicy.KEEP,
                                request
                            )

                        val intervalSec =
                            app.prefs.scanIntervalSeconds.first()

                        val safeInterval =
                            intervalSec.coerceAtLeast(60)

                        delay(safeInterval * 1000L)

                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi trong chu kỳ quét tự động", e)
                        delay(60_000L)
                    }
                }
            }
        }

        return START_STICKY
    }

    private suspend fun restorePendingPipelines(app: ContentAutomationApp) {
        try {
            val pendingJobs = app.database.pipelineJobDao()
                .observeAll()
                .first()
                .filter { it.status == PipelineStatus.PENDING }

            val workManager = WorkManager.getInstance(applicationContext)
            var restored = 0

            for (job in pendingJobs) {
                val uniqueName = "pipeline_${job.id}"
                val existingWork = workManager
                    .getWorkInfosForUniqueWork(uniqueName)
                    .get()

                val alreadyScheduled = existingWork.any { info ->
                    info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.BLOCKED
                }

                if (!alreadyScheduled) {
                    PipelineWorker.enqueue(applicationContext, job.id)
                    restored++
                }
            }

            Log.i(
                TAG,
                "Khôi phục pipeline: ${restored}/${pendingJobs.size} công việc đang chờ; không tự chạy công việc PAUSED."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Không thể khôi phục pipeline đang chờ", e)
        }
    }

    override fun onDestroy() {
        scanJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val manager =
                getSystemService(NotificationManager::class.java)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "MuChi Automation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }

            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MuChi")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {

        private const val TAG = "AutomationService"

        const val CHANNEL_ID = "automation_channel"
        const val NOTIF_ID = 0x101

        const val SCAN_WORK_NAME = "poco_automatic_scan"

        fun start(context: Context) {

            val intent =
                Intent(context, AutomationForegroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {

            context.stopService(
                Intent(context, AutomationForegroundService::class.java)
            )

            WorkManager.getInstance(context)
                .cancelUniqueWork(SCAN_WORK_NAME)
        }
    }
}
