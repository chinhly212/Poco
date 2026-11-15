package com.contentautomation.work

import android.content.Context
import androidx.work.*
import com.contentautomation.data.db.entity.VideoStatus
import com.contentautomation.data.network.AutomationApi
import com.contentautomation.data.network.ProcessVideoRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DownloadWorker(ctx: Context, params: WorkerParameters) : BaseWorker(ctx, params) {

    companion object {
        const val KEY_VIDEO_ID = "videoId"

        fun enqueue(context: Context, videoId: String) {
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_VIDEO_ID to videoId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).beginUniqueWork("download-$videoId", ExistingWorkPolicy.REPLACE, req).enqueue()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val video = db.sourceVideoDao().findById(videoId) ?: return@withContext Result.failure()
        val account = db.sourceAccountDao().findById(video.sourceAccountId) ?: return@withContext Result.failure()

        try {
            db.sourceVideoDao().updateStatus(videoId, VideoStatus.DOWNLOADING.name)
            
            val api = AutomationApi.create()
            val response = api.processVideo(ProcessVideoRequest(video.sourceUrl, account.platform))
            
            if (response.isSuccessful && response.body()?.success == true) {
                db.sourceVideoDao().updateStatus(videoId, VideoStatus.PROCESSING_VIDEO.name)
                Result.success()
            } else {
                throw Exception("Lỗi từ Server: ${response.message()}")
            }
        } catch (e: Exception) {
            db.sourceVideoDao().updateStatusError(videoId, VideoStatus.FAILED.name, e.message)
            if (video.retryCount < 5) {
                db.sourceVideoDao().updateStatus(videoId, VideoStatus.RETRY_PENDING.name)
                Result.retry()
            } else Result.failure()
        }
    }
}
