package com.contentautomation.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.SourceVideo
import com.contentautomation.data.db.entity.VideoStatus
import com.contentautomation.domain.source.SourceAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as ContentAutomationApp
        val accounts = app.database.sourceAccountDao().getEnabled()
        
        for (account in accounts) {
            try {
                val adapter = SourceAdapterFactory.create(account.platform)
                val items = adapter.getLatestVideos(account.accountIdentifier)
                for (item in items) {
                    if (app.database.sourceVideoDao().exists(account.id, item.id) > 0) continue
                    val newVideo = SourceVideo(
                        sourceAccountId = account.id,
                        externalVideoId = item.id,
                        sourceUrl = item.url,
                        title = item.title,
                        descriptionZh = item.description,
                        publishedAt = item.publishedAt,
                        status = VideoStatus.NEW.name
                    )
                    app.database.sourceVideoDao().insert(newVideo)
                    DownloadWorker.enqueue(applicationContext, newVideo.id)
                }
                app.database.sourceAccountDao().updateLastScan(account.id, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w("ScanWorker", "Scan failed for ${account.accountName}: ${e.message}")
            }
        }
        Result.success(workDataOf("scanned_accounts" to accounts.size))
    }
}
