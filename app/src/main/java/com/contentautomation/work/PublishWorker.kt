
package com.contentautomation.work

import android.content.Context
import androidx.work.*
import com.contentautomation.data.db.entity.TikTokPost
import com.contentautomation.data.db.entity.VideoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class PublishWorker(
    ctx: Context,
    params: WorkerParameters
) : BaseWorker(ctx, params) {

    companion object {
        const val KEY_PROCESSED_ID = "processedId"
        const val KEY_PRIVACY = "privacy"
        const val KEY_TITLE = "title"

        fun enqueue(
            context: Context,
            processedId: String,
            privacy: String,
            title: String
        ) {
            val req =
                OneTimeWorkRequestBuilder<PublishWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_PROCESSED_ID to processedId,
                            KEY_PRIVACY to privacy,
                            KEY_TITLE to title
                        )
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        60,
                        TimeUnit.SECONDS
                    )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

            WorkManager.getInstance(context)
                .beginUniqueWork(
                    "publish-$processedId",
                    ExistingWorkPolicy.KEEP,
                    req
                )
                .enqueue()
        }
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {

            val processedId =
                inputData.getString(KEY_PROCESSED_ID)
                    ?: return@withContext Result.failure()

            val privacy =
                inputData.getString(KEY_PRIVACY) ?: "SELF_ONLY"

            val title =
                inputData.getString(KEY_TITLE) ?: ""

            val pv = db.processedVideoDao()
                .findById(processedId)
                ?: return@withContext Result.failure()

            val path = pv.processedPath
                ?: return@withContext Result.failure()

            val file = File(path)

            if (!file.exists() || file.length() <= 0L) {
                return@withContext Result.failure()
            }

            val video = db.sourceVideoDao()
                .findById(pv.sourceVideoId)
                ?: return@withContext Result.failure()

            try {
                db.sourceVideoDao().updateStatus(
                    video.id,
                    VideoStatus.PUBLISHING.name
                )

                val caption = buildString {
                    append(pv.descriptionVi?.trim().orEmpty())

                    if (pv.hashtags.isNotEmpty()) {
                        append("\n\n")
                        append(pv.hashtags.joinToString(" "))
                    }
                }.ifBlank {
                    title
                }.take(2200)

                val init = app.tiktokClient.initializePost(
                    caption,
                    privacy,
                    file.length()
                )

                val post = TikTokPost(
                    processedVideoId = processedId,
                    tiktokAccountId =
                        app.prefs.getOpenId() ?: "default",
                    publishId = init.publishId,
                    privacyLevel = privacy,
                    status = "PROCESSING_UPLOAD"
                )

                db.tiktokPostDao().insert(post)

                app.tiktokClient.uploadFile(
                    init.uploadUrl,
                    file,
                    init.publishId
                )

                var finalStatus = "PROCESSING_UPLOAD"
                var postId: String? = null
                var failReason: String? = null

                for (i in 0 until 30) {
                    delay(10_000L)

                    val status =
                        app.tiktokClient.fetchStatus(init.publishId)

                    finalStatus = status.status
                    postId = status.postId
                    failReason = status.failReason

                    if (
                        finalStatus == "PUBLISH_COMPLETE" ||
                        finalStatus == "FAILED"
                    ) {
                        break
                    }
                }

                db.tiktokPostDao().insert(
                    post.copy(
                        status = finalStatus,
                        errorMessage = failReason,
                        publishedAt =
                            if (finalStatus == "PUBLISH_COMPLETE") {
                                System.currentTimeMillis()
                            } else {
                                null
                            }
                    )
                )

                if (finalStatus == "PUBLISH_COMPLETE") {
                    db.sourceVideoDao().updateStatus(
                        video.id,
                        VideoStatus.PUBLISHED.name
                    )

                    Result.success()
                } else {
                    db.sourceVideoDao().updateStatusError(
                        video.id,
                        VideoStatus.RETRY_PENDING.name,
                        failReason
                    )

                    Result.retry()
                }

            } catch (e: Exception) {
                db.sourceVideoDao().updateStatusError(
                    video.id,
                    VideoStatus.RETRY_PENDING.name,
                    e.message
                )

                Result.retry()
            }
        }
}
