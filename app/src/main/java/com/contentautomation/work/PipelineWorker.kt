package com.contentautomation.work

import android.content.Context
import androidx.work.*
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
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

        private fun uniqueWorkName(jobId: String): String =
            "pipeline_$jobId"

        fun enqueue(context: Context, jobId: String) {

            val request =
                OneTimeWorkRequestBuilder<PipelineWorker>()
                    .setInputData(
                        workDataOf(KEY_JOB_ID to jobId)
                    )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(
                                NetworkType.CONNECTED
                            )
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

            val jobId =
                inputData.getString(KEY_JOB_ID)
                    ?: return@withContext Result.failure()

            val dao = db.pipelineJobDao()

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

                if (!ContentAutomationApp.isEngineInitialized) {
                    throw IllegalStateException(
                        ContentAutomationApp.engineInitializationError
                            ?: "Media engine chưa khởi tạo"
                    )
                }

                if (job.stage == PipelineStage.SPEECH_TO_TEXT) {

                    dao.update(
                        job.copy(
                            status = PipelineStatus.PAUSED,
                            error = "Chờ kết nối API nhận diện giọng nói",
                            updatedAt = System.currentTimeMillis()
                        )
                    )

                    return@withContext Result.success()
                }

                dao.update(
                    job.copy(
                        status = PipelineStatus.RUNNING,
                        error = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                when (job.stage) {

                    // =========================
                    // 1. DOWNLOAD
                    // =========================

                    PipelineStage.DOWNLOAD -> {

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

                        YoutubeDL.getInstance()
                            .execute(request)

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
                            downloaded.length() == 0L
                        ) {
                            throw IllegalStateException(
                                "Tải video thất bại: không tìm thấy file"
                            )
                        }

                        dao.update(
                            job.copy(
                                stage = PipelineStage.ANALYZE,
                                status = PipelineStatus.PENDING,
                                retries = 0,
                                error = null,
                                updatedAt = System.currentTimeMillis()
                            )
                        )

                        db.sourceVideoDao().updateStatus(
                            video.id,
                            VideoStatus.PROCESSING_VIDEO.name
                        )

                        // APPEND_OR_REPLACE cho phép nối
                        // công việc tiếp theo vào unique work hiện tại.
                        enqueue(
                            applicationContext,
                            job.id
                        )
                    }

                    // =========================
                    // 2. ANALYZE
                    // =========================

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
                                ?: throw IllegalStateException(
                                    "Không tìm thấy video nguồn"
                                )

                        val audioFile =
                            File(workDir, "audio.wav")

                        if (audioFile.exists()) {
                            audioFile.delete()
                        }

                        val command = """
                            -y
                            -i "${source.absolutePath}"
                            -vn
                            -ac 1
                            -ar 16000
                            -c:a pcm_s16le
                            "${audioFile.absolutePath}"
                        """.trimIndent()

                        val session =
                            FFmpegKit.execute(command)

                        val returnCode =
                            session.returnCode

                        if (!ReturnCode.isSuccess(returnCode)) {

                            throw IllegalStateException(
                                session.failStackTrace
                                    ?: session.output
                                    ?: "FFmpeg tách âm thanh thất bại"
                            )
                        }

                        if (
                            !audioFile.exists() ||
                            audioFile.length() <= 0L
                        ) {
                            throw IllegalStateException(
                                "Không tạo được audio.wav"
                            )
                        }

                        dao.update(
                            job.copy(
                                stage = PipelineStage.SPEECH_TO_TEXT,
                                status = PipelineStatus.PAUSED,
                                retries = 0,
                                error = "Đã tách âm thanh. Chờ API nhận diện giọng nói.",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    // =========================
                    // CÁC CÔNG ĐOẠN CHƯA TRIỂN KHAI
                    // =========================

                    else -> {

                        dao.update(
                            job.copy(
                                status = PipelineStatus.PAUSED,
                                error = "Công đoạn ${job.stage} chưa được triển khai",
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

                val retryCount =
                    job.retries + 1

                val failedPermanently =
                    retryCount >= 3

                dao.update(
                    job.copy(
                        status = if (failedPermanently)
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

