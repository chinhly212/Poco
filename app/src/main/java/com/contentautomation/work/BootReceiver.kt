package com.contentautomation.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.contentautomation.ContentAutomationApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as ContentAutomationApp
            val enabled = runBlocking { app.prefs.foregroundEnabled.first() }
            if (enabled) AutomationForegroundService.start(context)
        }
    }
}
