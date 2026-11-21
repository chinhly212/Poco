package com.contentautomation.work

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.contentautomation.video.MediaStoreSaver
import com.contentautomation.video.PipelineMediaProcessor
import com.contentautomation.video.VideoCompressor
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

        private fun uniqueWorkName(jobId: String) = "pipeline_$jobId"

        fun enqueue(context: Context, jobId: String) {
            val request = OneTimeWorkRequestBuilder<PipelineWorker>()
                .setInputData(workDataOf(KEY_JOB_ID to jobId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(jobId),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as ContentAutomationApp
        val db = app.database
        val dao = db.pipelineJobDao()
        val jobId = inputData.getString(KEY_JOB_ID)
            ?: return@withContext Result.failure()
        val job = dao.findById(jobId) ?: return@withContext Result.failure()
        val video = db.sourceVideoDao().findById(job.sourceVideoId)
            ?: return@withContext Result.failure()

        val workDir = File(applicationContext.filesDir, "MuChi/${video.id}").apply { mkdirs() }
        val processor = PipelineMediaProcessor(applicationContext)

        suspend fun next(stage: PipelineStage, message: String) {
            dao.update(job.copy(
                stage = stage,
                status = PipelineStatus.PENDING,
                retries = 0,
                error = message,
                updatedAt = System.currentTimeMillis()
            ))
            enqueue(applicationContext, job.id)
        }

        try {
            if (!app.awaitMediaEngines()) {
                throw IllegalStateException(
                    ContentAutomationApp.engineInitializationError
                        ?: "Bộ máy tải và xử lý video chưa sẵn sàng."
                )
            }

            dao.update(job.copy(
                status = PipelineStatus.RUNNING,
                error = "Đang thực hiện: ${job.stage.name}",
                updatedAt = System.currentTimeMillis()
            ))

            when (job.stage) {
                PipelineStage.DOWNLOAD -> {
                    db.sourceVideoDao().updateStatus(video.id, VideoStatus.DOWNLOADING.name)
                    val serverFile = File(workDir, "server-source.mp4")
                    val outputTemplate = File(workDir, "source.%(ext)s").absolutePath

                    val downloadedByServer = try {
                        com.contentautomation.video.AiVideoServerClient()
                            .downloadVideo(video.sourceUrl, serverFile)
                        serverFile.exists() && serverFile.length() > 0L
                    } catch (_: Exception) {
                        serverFile.delete()
                        false
                    }

                    if (!downloadedByServer) {
                        YoutubeDL.getInstance().execute(
                            YoutubeDLRequest(video.sourceUrl).apply {
                                addOption("--no-playlist")
                                addOption("-o", outputTemplate)
                                addOption("-f", "best")
                            }
                        )
                    }

                    val downloaded = findOriginalVideo(workDir)
                        ?: throw IllegalStateException("Tải video thất bại: không tìm thấy tệp video hợp lệ.")
                    if (downloaded.length() <= 0L) {
                        throw IllegalStateException("Video tải về rỗng.")
                    }

                    db.sourceVideoDao().updateStatus(video.id, VideoStatus.PROCESSING_VIDEO.name)
                    next(PipelineStage.ANALYZE, "Đã tải video. Chờ tối ưu video.")
                }

                PipelineStage.ANALYZE -> {
                    val source = findOriginalVideo(workDir)
                        ?: throw IllegalStateException("Không tìm thấy video gốc đã tải.")
                    val optimized = File(workDir, "optimized-source.mp4")
                    val result = VideoCompressor(applicationContext)
                        .compressKeepQuality(Uri.fromFile(source), optimized)

                    if (!result.success || !optimized.exists() || optimized.length() <= 0L) {
                        throw IllegalStateException(result.errorMessage ?: "Không thể tối ưu video nguồn.")
                    }
                    next(PipelineStage.SPEECH_TO_TEXT, "Đã tối ưu video. Chờ nhận diện lời nói.")
                }

                PipelineStage.SPEECH_TO_TEXT -> {
                    val optimized = File(workDir, "optimized-source.mp4")
                    if (!optimized.exists() || optimized.length() <= 0L) {
                        throw IllegalStateException("Không tìm thấy video đã tối ưu để nhận diện lời nói.")
                    }
                    val groqKey = app.prefs.getSecret("ai_api_groq")
                    val metadataText = listOfNotNull(
                        video.title?.takeIf { it.isNotBlank() },
                        video.descriptionZh?.takeIf { it.isNotBlank() }
                    ).joinToString("\n")

                    val transcript = if (!groqKey.isNullOrBlank()) {
                        processor.transcribeChinese(optimized, groqKey)
                    } else {
                        metadataText
                    }
                    if (transcript.isBlank()) {
                        throw IllegalStateException("Chưa có Groq API để nhận diện lời nói và tài khoản nguồn cũng không có tiêu đề/mô tả. Hãy kết nối Groq trong Cài đặt → API.")
                    }
                    File(workDir, "transcript-zh.txt").writeText(transcript, Charsets.UTF_8)
                    val statusMessage = if (groqKey.isNullOrBlank()) {
                        "Chưa cấu hình Groq: dùng tiêu đề/mô tả nguồn thay cho lời thoại."
                    } else {
                        "Đã nhận diện lời nói bằng Groq Whisper."
                    }
                    next(PipelineStage.TRANSLATE, statusMessage)
                }

                PipelineStage.TRANSLATE -> {
                    db.sourceVideoDao().updateStatus(video.id, VideoStatus.TRANSLATING.name)
                    val transcriptFile = File(workDir, "transcript-zh.txt")
                    val sourceText = if (transcriptFile.exists()) {
                        transcriptFile.readText(Charsets.UTF_8).trim()
                    } else {
                        listOfNotNull(
                            video.title?.takeIf { it.isNotBlank() },
                            video.descriptionZh?.takeIf { it.isNotBlank() }
                        ).joinToString("\n")
                    }
                    if (sourceText.isBlank()) {
                        throw IllegalStateException("Không có lời thoại hoặc nội dung nguồn để dịch sang tiếng Việt.")
                    }

                    val keys = listOf("gemini", "openrouter", "groq").mapNotNull { provider ->
                        app.prefs.getSecret("ai_api_$provider")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { provider to it }
                    }.toMap()

                    val translated = processor.translateToVietnamese(sourceText, keys)
                    File(workDir, "translated-vi.txt").writeText(translated, Charsets.UTF_8)

                    val processedDao = db.processedVideoDao()
                    val existing = processedDao.findBySourceId(video.id)
                    val row = if (existing == null) {
                        ProcessedVideo(
                            sourceVideoId = video.id,
                            originalPath = findOriginalVideo(workDir)?.absolutePath,
                            descriptionVi = translated,
                            status = VideoStatus.TRANSLATING.name
                        )
                    } else {
                        existing.copy(descriptionVi = translated, status = VideoStatus.TRANSLATING.name)
                    }
                    processedDao.insert(row)
                    next(PipelineStage.TTS, "Đã dịch nội dung. Chờ tạo giọng đọc tiếng Việt.")
                }

                PipelineStage.TTS -> {
                    val translatedFile = File(workDir, "translated-vi.txt")
                    if (!translatedFile.exists() || translatedFile.readText().isBlank()) {
                        throw IllegalStateException("Không tìm thấy bản dịch tiếng Việt để tạo giọng đọc.")
                    }
                    processor.synthesizeVietnamese(
                        translatedFile.readText(Charsets.UTF_8),
                        File(workDir, "voice-vi.wav")
                    )
                    next(PipelineStage.MERGE, "Đã tạo giọng đọc tiếng Việt.")
                }

                PipelineStage.MERGE -> {
                    val optimized = File(workDir, "optimized-source.mp4")
                    val voice = File(workDir, "voice-vi.wav")
                    val dubbed = File(workDir, "muchi-vietnamese.mp4")
                    processor.mergeVideoAndVoice(optimized, voice, dubbed)
                    next(PipelineStage.EXPORT, "Đã ghép giọng đọc tiếng Việt vào video.")
                }

                PipelineStage.EXPORT -> {
                    val dubbed = File(workDir, "muchi-vietnamese.mp4")
                    if (!dubbed.exists() || dubbed.length() <= 0L) {
                        throw IllegalStateException("Không tìm thấy video đã ghép tiếng Việt để xuất.")
                    }

                    val savedUri = MediaStoreSaver(applicationContext)
                        .saveVideoToGallery(dubbed, "MuChi_VI_${video.id.take(8)}.mp4")
                        ?: throw IllegalStateException("Android không lưu được video vào thư viện.")

                    val processedDao = db.processedVideoDao()
                    val existing = processedDao.findBySourceId(video.id)
                    val translated = File(workDir, "translated-vi.txt")
                        .takeIf { it.exists() }?.readText(Charsets.UTF_8)
                    val row = if (existing == null) {
                        ProcessedVideo(
                            sourceVideoId = video.id,
                            originalPath = findOriginalVideo(workDir)?.absolutePath,
                            processedPath = dubbed.absolutePath,
                            descriptionVi = translated,
                            status = VideoStatus.SAVED_TO_PHONE.name
                        )
                    } else {
                        existing.copy(
                            processedPath = dubbed.absolutePath,
                            descriptionVi = translated ?: existing.descriptionVi,
                            status = VideoStatus.SAVED_TO_PHONE.name
                        )
                    }
                    processedDao.insert(row)
                    db.sourceVideoDao().updateStatus(video.id, VideoStatus.SAVED_TO_PHONE.name)
                    next(PipelineStage.VERIFY, "Đã xuất video vào thư viện: $savedUri")
                }

                PipelineStage.VERIFY -> {
                    val output = File(workDir, "muchi-vietnamese.mp4")
                    if (!output.exists() || output.length() <= 0L) {
                        throw IllegalStateException("Kiểm tra sau xuất thất bại: tệp đầu ra không tồn tại hoặc rỗng.")
                    }
                    dao.update(job.copy(
                        status = PipelineStatus.DONE,
                        retries = 0,
                        error = "Hoàn tất: video đã có bản dịch và giọng đọc tiếng Việt, đã ghép và lưu vào thư viện điện thoại.",
                        updatedAt = System.currentTimeMillis()
                    ))
                    db.sourceVideoDao().updateStatus(video.id, VideoStatus.SAVED_TO_PHONE.name)
                }

                PipelineStage.SUBTITLE, PipelineStage.UPLOAD -> {
                    throw IllegalStateException(
                        "Công đoạn ${job.stage} chưa được bật trong quy trình hiện tại; video không bị đánh dấu hoàn thành giả."
                    )
                }
            }

            Result.success()
        } catch (e: CancellationException) {
            dao.update(job.copy(
                status = PipelineStatus.PENDING,
                updatedAt = System.currentTimeMillis()
            ))
            throw e
        } catch (e: Exception) {
            val retryCount = job.retries + 1
            val failedPermanently = retryCount >= 3
            dao.update(job.copy(
                status = if (failedPermanently) PipelineStatus.FAILED else PipelineStatus.PENDING,
                retries = retryCount,
                error = e.message ?: "Lỗi không xác định.",
                updatedAt = System.currentTimeMillis()
            ))
            db.sourceVideoDao().updateStatusError(
                video.id,
                if (failedPermanently) VideoStatus.FAILED.name else VideoStatus.RETRY_PENDING.name,
                e.message
            )
            if (failedPermanently) Result.failure() else Result.retry()
        }
    }

    private fun findOriginalVideo(directory: File): File? =
        directory.listFiles()
            ?.filter {
                it.isFile &&
                    (it.name == "server-source.mp4" || it.name.startsWith("source.")) &&
                    !it.name.endsWith(".part") &&
                    it.length() > 0L
            }
            ?.maxByOrNull { it.lastModified() }
}
