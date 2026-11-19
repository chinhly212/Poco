package com.contentautomation.work

import android.content.Context
import androidx.work.*
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PipelineWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_JOB_ID = "pipeline_job_id"

        fun enqueue(context: Context, jobId: String) {
            val request =
                OneTimeWorkRequestBuilder<PipelineWorker>()
                    .setInputData(
                        workDataOf(KEY_JOB_ID to jobId)
                    )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "pipeline_$jobId",
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {

            val app =
                applicationContext as ContentAutomationApp

            val db = app.database

            val jobId =
                inputData.getString(KEY_JOB_ID)
                    ?: return@withContext Result.failure()

            val dao = db.pipelineJobDao()

            val job = dao.findById(jobId)
                ?: return@withContext Result.failure()

            val video = db.sourceVideoDao()
                .findById(job.sourceVideoId)
                ?: return@withContext Result.failure()

            try {

                if (!ContentAutomationApp.isEngineInitialized) {
                    throw IllegalStateException(
                        "Bộ tải video chưa khởi tạo"
                    )
                }

                // Hiện tại chỉ thực thi công đoạn DOWNLOAD
                if (job.stage != PipelineStage.DOWNLOAD) {
    android.util.Log.i(
        "MuChiPipeline",
        "Job ${job.id} đang chờ công đoạn ${job.stage}"
    )

    return@withContext Result.success()
                }

                dao.update(
    job.copy(
        stage = PipelineStage.ANALYZE,
        status = PipelineStatus.PENDING,
        error = null,
        updatedAt = System.currentTimeMillis()
    )
)

db.sourceVideoDao().updateStatus(
    video.id,
    VideoStatus.PROCESSING_VIDEO.name
)
                db.sourceVideoDao().updateStatus(
                    video.id,
                    VideoStatus.DOWNLOADING.name
                )

                val workDir = File(
                    applicationContext.filesDir,
                    "MuChi/${video.id}"
                )

                if (!workDir.exists()) {
                    workDir.mkdirs()
                }

                val outputTemplate =
                    File(workDir, "source.%(ext)s").absolutePath

                val request =
                    YoutubeDLRequest(video.sourceUrl).apply {

                        addOption("--no-playlist")
                        addOption("-o", outputTemplate)
                        addOption("-f", "best")
                    }

                YoutubeDL.getInstance().execute(request)

                val downloaded = workDir.listFiles()
                    ?.filter {
                        it.isFile &&
                        it.name.startsWith("source.")
                    }
                    ?.maxByOrNull { it.lastModified() }

                if (downloaded == null ||
                    downloaded.length() <= 0
                ) {
                    throw IllegalStateException(
                        "Không tìm thấy video tải xuống"
                    )
                }

                // Chuyển sang bước kế tiếp,
                // chưa giả lập hoàn thành toàn Pipeline
                dao.update(
                    job.copy(
                        stage = PipelineStage.ANALYZE,
                        status = PipelineStatus.PENDING,
                        error = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                db.sourceVideoDao().updateStatus(
                    video.id,
                    VideoStatus.PROCESSING_VIDEO.name
                )

                Result.success()

            } catch (e: Exception) {

                val retryCount = job.retries + 1

                dao.update(
                    job.copy(
                        status = if (retryCount >= 3)
                            PipelineStatus.FAILED
                        else
                            PipelineStatus.PENDING,

                        retries = retryCount,
                        error = e.message
                            ?: "Lỗi không xác định",

                        updatedAt = System.currentTimeMillis()
                    )
                )

                db.sourceVideoDao().updateStatusError(
                    video.id,
                    VideoStatus.FAILED.name,
                    e.message
                )

                if (retryCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
}
