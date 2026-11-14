package com.contentautomation.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.contentautomation.ContentAutomationApp

abstract class BaseWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    protected val app: ContentAutomationApp get() = applicationContext as ContentAutomationApp
    protected val db get() = app.database
    protected val prefs get() = app.prefs
}
