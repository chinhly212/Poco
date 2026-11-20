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

import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

import com.contentautomation.ContentAutomationApp
import com.contentautomation.MainActivity
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

                while (isActive) {

                    try {
                        // Tạo yêu cầu quét
                        val request =
                            OneTimeWorkRequestBuilder<ScanWorker>()
                                .build()

                        // Không tạo nhiều công việc quét trùng nhau
                        WorkManager.getInstance(applicationContext)
                            .enqueueUniqueWork(
                                SCAN_WORK_NAME,
                                ExistingWorkPolicy.KEEP,
                                request
                            )

                        // Đọc chu kỳ quét từ DataStore
                        val intervalSec =
                            app.prefs.scanIntervalSeconds.first()

                        // Tối thiểu 60 giây
                        val safeInterval =
                            intervalSec.coerceAtLeast(60)

                        delay(safeInterval * 1000L)

                    } catch (e: Exception) {

                        delay(60_000L)
                    }
                }
            }
        }

        return START_STICKY
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
