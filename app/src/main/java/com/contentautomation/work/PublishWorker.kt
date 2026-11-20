
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

        private const val STATUS_COMPLETE = "PUBLISH_COMPLETE"
        private const val STATUS_FAILED = "FAILED"

        fun enqueue(
            context: Context,
            processedId: String,
            privacy: String,
            title: String
        ) {
            val request = OneTimeWorkRequestBuilder<PublishWorker>()
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
                    request
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

            val file = File(
                pv.processedPath
                    ?: return@withContext Result.failure()
            )

            if (!file.exists() || file.length() <= 0L) {
                return@withContext Result.failure()
            }

            val video = db.sourceVideoDao()
                .findById(pv.sourceVideoId)
                ?: return@withContext Result.failure()

            val postDao = db.tiktokPostDao()

            try {
                db.sourceVideoDao().updateStatus(
                    video.id,
                    VideoStatus.PUBLISHING.name
                )

                var post = postDao.findLatestByProcessedVideoId(
                    processedId
                )

                // Nếu đã có publish_id thì tuyệt đối không tạo bài mới.
                if (post != null) {
                    val existingPublishId = post.publishId
                        ?: throw IllegalStateException(
                            "Bài đăng cũ không có publish_id. Dừng để tránh đăng trùng."
                        )

                    if (post.status == STATUS_COMPLETE) {
                        db.sourceVideoDao().updateStatus(
                            video.id,
                            VideoStatus.PUBLISHED.name
                        )
                        return@withContext Result.success()
                    }

                    if (post.status == STATUS_FAILED) {
                        db.sourceVideoDao().updateStatusError(
                            video.id,
                            VideoStatus.FAILED.name,
                            post.errorMessage
                        )
                        return@withContext Result.failure()
                    }

                    // Tiếp tục kiểm tra bài đăng cũ trên TikTok.
                    val remoteStatus =
                        app.tiktokClient.fetchStatus(existingPublishId)

                    post = post.copy(
                        status = remoteStatus.status,
                        errorMessage = remoteStatus.failReason,
                        publishedAt =
                            if (remoteStatus.status == STATUS_COMPLETE) {
                                System.currentTimeMillis()
                            } else {
                                post.publishedAt
                            }
                    )

                    postDao.update(post)

                } else {
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

                    post = TikTokPost(
                        processedVideoId = processedId,
                        tiktokAccountId =
                            app.prefs.getOpenId() ?: "default",
                        publishId = init.publishId,
                        privacyLevel = privacy,
                        status = "PROCESSING_UPLOAD"
                    )

                    // Lưu publish_id trước khi tải video lên.
                    postDao.insert(post)

                    app.tiktokClient.uploadFile(
                        init.uploadUrl,
                        file,
                        init.publishId
                    )
                }

                var finalStatus = post.status
                var failReason = post.errorMessage

                // Theo dõi đúng publish_id đã lưu.
                for (i in 0 until 30) {
                    if (
                        finalStatus == STATUS_COMPLETE ||
                        finalStatus == STATUS_FAILED
                    ) {
                        break
                    }

                    delay(10_000L)

                    val status = app.tiktokClient.fetchStatus(
                        post.publishId
                            ?: throw IllegalStateException(
                                "Thiếu publish_id"
                            )
                    )

                    finalStatus = status.status
                    failReason = status.failReason

                    post = post.copy(
                        status = finalStatus,
                        errorMessage = failReason,
                        publishedAt =
                            if (finalStatus == STATUS_COMPLETE) {
                                System.currentTimeMillis()
                            } else {
                                null
                            }
                    )

                    postDao.update(post)
                }

                if (finalStatus == STATUS_COMPLETE) {
                    db.sourceVideoDao().updateStatus(
                        video.id,
                        VideoStatus.PUBLISHED.name
                    )

                    Result.success()

                } else if (finalStatus == STATUS_FAILED) {
                    db.sourceVideoDao().updateStatusError(
                        video.id,
                        VideoStatus.FAILED.name,
                        failReason
                    )

                    Result.failure()

                } else {
                    db.sourceVideoDao().updateStatusError(
                        video.id,
                        VideoStatus.RETRY_PENDING.name,
                        "TikTok vẫn đang xử lý bài đăng"
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
