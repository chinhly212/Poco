package com.contentautomation.work

import android.content.Context
import androidx.work.*
import com.contentautomation.data.db.entity.PipelineJob
import com.contentautomation.data.db.entity.PipelineStage
import com.contentautomation.data.db.entity.PipelineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DownloadWorker(
    ctx: Context,
    params: WorkerParameters
) : BaseWorker(ctx, params) {

    companion object {
        const val KEY_VIDEO_ID = "videoId"

        fun enqueue(context: Context, videoId: String) {
            val request =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        workDataOf(KEY_VIDEO_ID to videoId)
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS
                    )
                    .build()

            WorkManager.getInstance(context)
                .beginUniqueWork(
                    "download-$videoId",
                    ExistingWorkPolicy.KEEP,
                    request
                )
                .enqueue()
        }
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {

            val videoId = inputData.getString(KEY_VIDEO_ID)
                ?: return@withContext Result.failure()

            val video = db.sourceVideoDao()
                .findById(videoId)
                ?: return@withContext Result.failure()

            try {
                val jobDao = db.pipelineJobDao()

                var job = jobDao.findById(videoId)

                if (job == null) {
                    val newJob = PipelineJob(
                        id = video.id,
                        sourceVideoId = video.id,
                        stage = PipelineStage.DOWNLOAD,
                        status = PipelineStatus.PENDING
                    )

                    jobDao.insert(newJob)

                    job = jobDao.findById(videoId)
                        ?: throw IllegalStateException(
                            "Không thể tạo PipelineJob"
                        )
                }

                if (
                    job.stage == PipelineStage.DOWNLOAD &&
                    job.status == PipelineStatus.FAILED
                ) {
                    job = job.copy(
                        status = PipelineStatus.PENDING,
                        retries = 0,
                        error = null,
                        updatedAt = System.currentTimeMillis()
                    )

                    jobDao.update(job)
                }

                if (
                    job.status == PipelineStatus.DONE ||
                    job.status == PipelineStatus.RUNNING
                ) {
                    return@withContext Result.success()
                }

                PipelineWorker.enqueue(
                    applicationContext,
                    job.id
                )

                Result.success()

            } catch (e: Exception) {
                db.sourceVideoDao().updateStatusError(
                    video.id,
                    com.contentautomation.data.db.entity.VideoStatus.RETRY_PENDING.name,
                    e.message
                )

                Result.retry()
            }
        }
}
