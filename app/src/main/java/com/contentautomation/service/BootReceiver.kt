
package com.contentautomation.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.contentautomation.ContentAutomationApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = appContext as ContentAutomationApp
                val enabled = app.prefs.foregroundEnabled.first()

                if (enabled) {
                    AutomationForegroundService.start(appContext)
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Không thể khởi động dịch vụ sau khi bật máy",
                    e
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
