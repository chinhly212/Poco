package com.contentautomation.work

import android.content.Context
import androidx.work.*
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.TikTokPost
import com.contentautomation.data.db.entity.VideoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class PublishWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_PROCESSED_ID = "processedId"
        const val KEY_PRIVACY = "privacy"
        const val KEY_TITLE = "title"

        fun enqueue(context: Context, processedId: String, privacy: String, title: String) {
            val req = OneTimeWorkRequestBuilder<PublishWorker>()
                .setInputData(workDataOf(
                    KEY_PROCESSED_ID to processedId,
                    KEY_PRIVACY to privacy,
                    KEY_TITLE to title
                ))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .beginUniqueWork("publish-$processedId", ExistingWorkPolicy.REPLACE, req)
                .enqueue()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as ContentAutomationApp
        val processedId = inputData.getString(KEY_PROCESSED_ID) ?: return@withContext Result.failure()
        val privacy = inputData.getString(KEY_PRIVACY) ?: "SELF_ONLY"
        val title = inputData.getString(KEY_TITLE) ?: ""

        val pv = app.database.processedVideoDao().findById(processedId) ?: return@withContext Result.failure()
        val path = pv.processedPath ?: return@withContext Result.failure()
        
        val file = File(path)
        if (!file.exists()) return@withContext Result.failure()

        val video = app.database.sourceVideoDao().findById(pv.sourceVideoId) ?: return@withContext Result.failure()

        try {
            app.database.sourceVideoDao().updateStatus(video.id, VideoStatus.PUBLISHING.name)
            
            val caption = buildString {
                append(pv.descriptionVi?.trim().orEmpty())
                if (pv.hashtags.isNotEmpty()) {
                    append("\n\n")
                    append(pv.hashtags.joinToString(" "))
                }
            }.ifBlank { title }.take(2200)

            val init = app.tiktokClient.initializePost(caption, privacy, file.length())
            val post = TikTokPost(
                processedVideoId = processedId,
                tiktokAccountId = app.prefs.getOpenId() ?: "default",
                publishId = init.publishId,
                privacyLevel = privacy,
                status = "PROCESSING_UPLOAD"
            )
            app.database.tiktokPostDao().insert(post)

            app.tiktokClient.uploadFile(init.uploadUrl, file, init.publishId)

            var finalStatus = "PROCESSING_UPLOAD"
            var postId: String? = null
            var failReason: String? = null
            
            for (i in 0 until 30) {
                delay(10_000L)
                val st = app.tiktokClient.fetchStatus(init.publishId)
                finalStatus = st.status
                postId = st.postId
                failReason = st.failReason
                if (st.status == "PUBLISH_COMPLETE" || st.status == "FAILED") break
            }

            val updated = post.copy(
                status = finalStatus,
                errorMessage = failReason,
                publishedAt = if (finalStatus == "PUBLISH_COMPLETE") System.currentTimeMillis() else null
            )
            app.database.tiktokPostDao().insert(updated)

            if (finalStatus == "PUBLISH_COMPLETE") {
                app.database.sourceVideoDao().updateStatus(video.id, VideoStatus.PUBLISHED.name)
                Result.success()
            } else {
                app.database.sourceVideoDao().updateStatusError(video.id, VideoStatus.FAILED.name, failReason)
                Result.retry()
            }
        } catch (e: Exception) {
            app.database.sourceVideoDao().updateStatusError(video.id, VideoStatus.FAILED.name, e.message)
            Result.retry()
        }
    }
}
