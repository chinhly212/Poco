package com.contentautomation.work

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val PERIODIC_SCAN = "periodic-scan"

    /** WorkManager min period là 15 phút. Spec yêu cầu 10 phút → ForegroundService làm chính xác 10p. */
    fun schedulePeriodicScan(context: Context) {
        val req = PeriodicWorkRequestBuilder<ScanWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SCAN, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }

    fun cancelPeriodicScan(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_SCAN)
    }
}
