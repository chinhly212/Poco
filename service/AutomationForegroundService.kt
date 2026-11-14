package com.contentautomation.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.contentautomation.MainActivity
import com.contentautomation.R
import com.contentautomation.ContentAutomationApp
import com.contentautomation.work.ScanWorker
import kotlinx.coroutines.*

class AutomationForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Đang chạy quét 24/7..."))
        if (scanJob == null) {
            scanJob = scope.launch {
                val app = applicationContext as ContentAutomationApp
                while (isActive) {
                    // Enqueue scan qua WorkManager để đảm bảo isolation + retry
                    val req = androidx.work.OneTimeWorkRequestBuilder<ScanWorker>().build()
                    androidx.work.WorkManager.getInstance(this@AutomationForegroundService).enqueue(req)
                    val intervalSec = app.currentScanIntervalSeconds()
                    delay(intervalSec.coerceAtLeast(60) * 1000L)
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
            val mgr = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID, "Content Automation", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Content Automation")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "automation_channel"
        const val NOTIF_ID = 0x101

        fun start(context: Context) {
            val intent = Intent(context, AutomationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutomationForegroundService::class.java))
        }
    }
}
