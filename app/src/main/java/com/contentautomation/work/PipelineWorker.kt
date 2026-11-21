package com.contentautomation.work

import android.content.Context
import androidx.work.*
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import android.net.Uri
import com.contentautomation.video.VideoCompressor
import com.contentautomation.video.MediaStoreSaver
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
                // Workers may start before Application's asynchronous native-engine setup finishes.
                if (!app.awaitMediaEngines()) {
                    throw IllegalStateException(
                        ContentAutomationApp.engineInitializationError
                            ?: "Bộ máy tải và xử lý video chưa sẵn sàng. Hệ thống sẽ tự thử lại."
                    )
                }

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

                        // First use the centralized server: it automatically fails over
                        // GenDownload -> public fallbacks -> server-side yt-dlp.
                        // If the server is asleep/unavailable, fall back to on-device yt-dlp.
                        val serverDownloaded = try {
                            com.contentautomation.video.AiVideoServerClient()
                                .downloadVideo(video.sourceUrl, File(workDir, "server-source.mp4"))
                            true
                        } catch (serverError: Exception) {
                            File(workDir, "server-source.mp4").delete()
                            false
                        }

                        if (!serverDownloaded) {
                            val request =
                                YoutubeDLRequest(video.sourceUrl).apply {
                                    addOption("--no-playlist")
                                    addOption("-o", outputTemplate)
                                    addOption("-f", "best")
                                }
                            YoutubeDL.getInstance().execute(request)
                        }

                        val downloaded =
                            workDir.listFiles()
                                ?.filter {
                                    it.isFile &&
                                        (it.name.startsWith("source.") || it.name == "server-source.mp4") &&
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
                        val source = workDir.listFiles()
                            ?.filter { it.isFile && it.name.startsWith("source.") && !it.name.endsWith(".part") }
                            ?.maxByOrNull { it.lastModified() }
                            ?: throw IllegalStateException("Không tìm thấy video gốc đã tải")

                        if (source.length() <= 0L) {
                            throw IllegalStateException("Video gốc rỗng, không thể xử lý")
                        }

                        dao.update(job.copy(
                            status = PipelineStatus.RUNNING,
                            error = "Đang tối ưu video và lưu vào điện thoại...",
                            updatedAt = System.currentTimeMillis()
                        ))

                        val processedFile = File(workDir, "nichi-processed.mp4")
                        val compressed = VideoCompressor(applicationContext)
                            .compressKeepQuality(Uri.fromFile(source), processedFile)

                        if (!compressed.success || compressed.outputFile == null ||
                            !compressed.outputFile.exists() || compressed.outputFile.length() <= 0L) {
                            throw IllegalStateException(compressed.errorMessage ?: "Không thể xử lý video")
                        }

                        val savedUri = MediaStoreSaver(applicationContext)
                            .saveVideoToGallery(compressed.outputFile, "MuChi_${video.id.take(8)}.mp4")
                            ?: throw IllegalStateException("Không thể lưu video vào thư viện điện thoại")

                        val processedDao = db.processedVideoDao()
                        val existing = processedDao.findBySourceId(video.id)
                        val processed = if (existing == null) {
                            ProcessedVideo(
                                sourceVideoId = video.id,
                                originalPath = source.absolutePath,
                                processedPath = compressed.outputFile.absolutePath,
                                status = VideoStatus.SAVED_TO_PHONE.name
                            )
                        } else {
                            existing.copy(
                                originalPath = source.absolutePath,
                                processedPath = compressed.outputFile.absolutePath,
                                status = VideoStatus.SAVED_TO_PHONE.name
                            )
                        }
                        processedDao.insert(processed)

                        db.sourceVideoDao().updateStatus(video.id, VideoStatus.SAVED_TO_PHONE.name)
                        dao.update(job.copy(
                            stage = PipelineStage.EXPORT,
                            status = PipelineStatus.DONE,
                            retries = 0,
                            error = "Đã xử lý và lưu vào Movies/MuChi trên điện thoại.",
                            updatedAt = System.currentTimeMillis()
                        ))
                    }

                    PipelineStage.SPEECH_TO_TEXT -> {
                        dao.update(
                            job.copy(
                                status = PipelineStatus.PAUSED,
                                error = "Chờ cấu hình dịch vụ nhận diện lời bài hát và tạo phụ đề.",
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
