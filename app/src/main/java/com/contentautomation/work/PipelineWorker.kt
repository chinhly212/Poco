package com.contentautomation.work

import android.content.Context
import androidx.work.*
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PipelineWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {

        const val KEY_JOB_ID = "pipeline_job_id"

        private fun uniqueWorkName(jobId: String) =
            "pipeline_$jobId"

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
                    uniqueWorkName(jobId),
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    request
                )
        }
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {

            val app =
                applicationContext as ContentAutomationApp

            val db = app.database
            val dao = db.pipelineJobDao()

            val jobId =
                inputData.getString(KEY_JOB_ID)
                    ?: return@withContext Result.failure()

            val job = dao.findById(jobId)
                ?: return@withContext Result.failure()

            val video = db.sourceVideoDao()
                .findById(job.sourceVideoId)
                ?: return@withContext Result.failure()

            val workDir = File(
                applicationContext.filesDir,
                "MuChi/${video.id}"
            )

            workDir.mkdirs()

            try {
                when (job.stage) {

                    PipelineStage.DOWNLOAD -> {
                        dao.update(
                            job.copy(
                                status = PipelineStatus.RUNNING,
                                error = null,
                                updatedAt = System.currentTimeMillis()
                            )
                        )

                        db.sourceVideoDao().updateStatus(
                            video.id,
                            VideoStatus.DOWNLOADING.name
                        )

                        val outputTemplate =
                            File(
                                workDir,
                                "source.%(ext)s"
                            ).absolutePath

                        val request =
                            YoutubeDLRequest(video.sourceUrl).apply {
                                addOption("--no-playlist")
                                addOption("-o", outputTemplate)
                                addOption("-f", "best")
                            }

                        YoutubeDL.getInstance().execute(request)

                        val downloaded =
                            workDir.listFiles()
                                ?.filter {
                                    it.isFile &&
                                        it.name.startsWith("source.") &&
                                        !it.name.endsWith(".part")
                                }
                                ?.maxByOrNull {
                                    it.lastModified()
                                }

                        if (
                            downloaded == null ||
                            downloaded.length() <= 0L
                        ) {
                            throw IllegalStateException(
                                "Tải video thất bại: không tìm thấy file"
                            )
                        }

                        db.sourceVideoDao().updateStatus(
                            video.id,
                            VideoStatus.PROCESSING_VIDEO.name
                        )

                        dao.update(
                            job.copy(
                                stage = PipelineStage.ANALYZE,
                                status = PipelineStatus.PENDING,
                                retries = 0,
                                error = null,
                                updatedAt = System.currentTimeMillis()
                            )
                        )

                        enqueue(
                            applicationContext,
                            job.id
                        )
                    }

                    PipelineStage.ANALYZE -> {
                        val source =
                            workDir.listFiles()
                                ?.filter {
                                    it.isFile &&
                                        it.name.startsWith("source.") &&
                                        !it.name.endsWith(".part")
                                }
                                ?.maxByOrNull {
                                    it.lastModified()
                                }

                        if (
                            source == null ||
                            source.length() <= 0L
                        ) {
                            throw IllegalStateException(
                                "Không tìm thấy video gốc đã tải"
                            )
                        }

                        // Không tách âm thanh từ video gốc.
                        // Chờ chọn nhạc từ thư viện TikTok.
                        dao.update(
                            job.copy(
                                stage = PipelineStage.SPEECH_TO_TEXT,
                                status = PipelineStatus.PAUSED,
                                retries = 0,
                                error = "Đã tải video gốc. Chờ chọn nhạc TikTok và nhận diện lời bài hát.",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    PipelineStage.SPEECH_TO_TEXT -> {
                        dao.update(
                            job.copy(
                                status = PipelineStatus.PAUSED,
                                error = "Chờ audio nhạc TikTok để nhận diện lời bài hát và tạo phụ đề.",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    else -> {
                        dao.update(
                            job.copy(
                                status = PipelineStatus.PAUSED,
                                error = "Công đoạn ${job.stage} chưa được triển khai.",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                Result.success()

            } catch (e: CancellationException) {
                dao.update(
                    job.copy(
                        status = PipelineStatus.PENDING,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                throw e

            } catch (e: Exception) {
                val retryCount = job.retries + 1
                val failedPermanently = retryCount >= 3

                dao.update(
                    job.copy(
                        status = if (failedPermanently)
                            PipelineStatus.FAILED
                        else
                            PipelineStatus.PENDING,
                        retries = retryCount,
                        error = e.message ?: "Lỗi không xác định",
                        updatedAt = System.currentTimeMillis()
                    )
                )

                db.sourceVideoDao().updateStatusError(
                    video.id,
                    if (failedPermanently)
                        VideoStatus.FAILED.name
                    else
                        VideoStatus.RETRY_PENDING.name,
                    e.message
                )

                if (failedPermanently) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        }
}
