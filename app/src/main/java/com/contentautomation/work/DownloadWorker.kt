package com.contentautomation.work

import android.content.Context
import android.util.Log
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
                // Nếu gọi server lỗi, nó sẽ tự động thử lại sau 30s, 1 phút, 2 phút...
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .beginUniqueWork("download-$videoId", ExistingWorkPolicy.REPLACE, req)
                .enqueue()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val video = db.sourceVideoDao().findById(videoId) ?: return@withContext Result.failure()
        val account = db.sourceAccountDao().findById(video.sourceAccountId)
            ?: return@withContext Result.failure()

        try {
            // 1. Cập nhật trạng thái để App biết đang gửi lên Server
            db.sourceVideoDao().updateStatus(videoId, VideoStatus.DOWNLOADING.name)

            // 2. Gọi API Server (Server sẽ tự tải video, cắt ghép FFmpeg, dịch và đăng)
            val api = AutomationApi.create()
            val request = ProcessVideoRequest(
                videoUrl = video.sourceUrl,
                platform = account.platform
            )
            
            Log.d("DownloadWorker", "Đang gửi link lên Server: ${video.sourceUrl}")
            val response = api.processVideo(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                // 3. Server xử lý thành công, cập nhật trạng thái để thanh tiến trình nhảy sang bước tiếp theo
                Log.d("DownloadWorker", "Server đã nhận video và đang xử lý")
                db.sourceVideoDao().updateStatus(videoId, VideoStatus.PROCESSING_VIDEO.name)
                Result.success()
            } else {
                throw Exception("Lỗi từ Server: ${response.message()}")
            }

        } catch (e: Exception) {
            Log.e("DownloadWorker", "Lỗi gọi API Server: ${e.message}")
            db.sourceVideoDao().updateStatusError(videoId, VideoStatus.FAILED.name, e.message)
            
            // Cơ chế thử lại: Nếu chưa quá 5 lần thì báo WorkManager thử lại
            val retry = video.retryCount + 1
            if (retry < 5) {
                db.sourceVideoDao().updateStatus(videoId, VideoStatus.RETRY_PENDING.name)
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
