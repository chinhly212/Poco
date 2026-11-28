package com.contentautomation.videoeditor.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.contentautomation.videoeditor.domain.ProcessingProfile

/** Reads real Android memory, thermal and ABI information before model loading. */
class AndroidDeviceResourceMonitor : DeviceResourceMonitor {
    override fun snapshot(context: Context): DeviceResourceSnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }

        return DeviceResourceSnapshot(
            availableMemoryBytes = memory.availMem,
            totalMemoryBytes = memory.totalMem,
            lowMemory = memory.lowMemory,
            thermalStatus = thermal,
            cpuCoreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            supportedAbis = Build.SUPPORTED_ABIS.toList()
        )
    }

    override fun recommendedProfile(snapshot: DeviceResourceSnapshot): ProcessingProfile {
        val availableGb = snapshot.availableMemoryBytes / 1_073_741_824.0
        val totalGb = snapshot.totalMemoryBytes / 1_073_741_824.0
        val thermalHot = snapshot.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE

        return when {
            snapshot.lowMemory || availableGb < 1.2 || totalGb < 4.0 ||
                snapshot.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE ->
                ProcessingProfile.LOW_MEMORY
            thermalHot || availableGb < 2.5 || totalGb < 8.0 ->
                ProcessingProfile.BALANCED
            else -> ProcessingProfile.PERFORMANCE
        }
    }
}
