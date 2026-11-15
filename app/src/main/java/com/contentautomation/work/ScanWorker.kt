package com.contentautomation.work

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.contentautomation.data.db.entity.SourceVideo
import com.contentautomation.data.db.entity.VideoStatus
import com.contentautomation.domain.source.SourceAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanWorker(ctx: Context, params: WorkerParameters) : BaseWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val accounts = db.sourceAccountDao().getEnabled()
        
        for (account in accounts) {
            try {
                val adapter = SourceAdapterFactory.create(account.platform)
                val items = adapter.getLatestVideos(account.accountIdentifier)
                
                items.forEach { item ->
                    if (db.sourceVideoDao().exists(account.id, item.id) == 0) {
                        val newVideo = SourceVideo(
                            sourceAccountId = account.id,
                            externalVideoId = item.id,
                            sourceUrl = item.url,
                            title = item.title,
                            descriptionZh = item.description,
                            publishedAt = item.publishedAt,
                            status = VideoStatus.NEW.name
                        )
                        db.sourceVideoDao().insert(newVideo)
                        DownloadWorker.enqueue(applicationContext, newVideo.id)
                    }
                }
                db.sourceAccountDao().updateLastScan(account.id, System.currentTimeMillis())
            } catch (e: Exception) {
                android.util.Log.w("ScanWorker", "Scan failed for ${account.accountName}: ${e.message}")
            }
        }
        Result.success(workDataOf("scanned_accounts" to accounts.size))
    }
}
