package com.contentautomation.videoeditor.engine

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.contentautomation.videoeditor.domain.SubtitleCue
import com.contentautomation.videoeditor.domain.SrtCodec
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the source video, mixes its original audio quietly with generated
 * voice clips, and places each voice clip at its subtitle cue start time.
 * Videos without an audio stream are exported with the generated voice only.
 */
class FfmpegDubbingExporter(private val context: Context) {

    suspend fun export(
        videoUri: Uri,
        cues: List<SubtitleCue>,
        outputUri: Uri
    ): Unit = withContext(Dispatchers.IO) {
        val voiceCues = cues.filter { !it.voiceAudioPath.isNullOrBlank() }
            .sortedBy { it.startMs }
        require(voiceCues.isNotEmpty()) { "Chưa có âm thanh lồng tiếng để xuất." }

        val workDir = File(context.cacheDir, "muchi-export").apply { mkdirs() }
        check(workDir.isDirectory) { "Không tạo được thư mục xuất video." }
        val stamp = System.currentTimeMillis()
        val inputVideo = File(workDir, "source-$stamp.mp4")
        val outputVideo = File(workDir, "dubbed-$stamp.mp4")
        val subtitleFile = File(workDir, "subtitles-$stamp.srt")
        val hasSubtitles = cues.any { it.displayText(useTranslation = true).isNotBlank() }

        try {
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                inputVideo.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Không đọc được video nguồn.")

            val audioFiles = voiceCues.mapIndexed { index, cue ->
                val source = File(requireNotNull(cue.voiceAudioPath))
                check(source.isFile && source.length() > 44L) {
                    "Không tìm thấy tệp giọng đọc của đoạn ${index + 1}."
                }
                source
            }

            val baseArgs = mutableListOf<String>()
            baseArgs += listOf("-y", "-i", inputVideo.absolutePath)
            audioFiles.forEach { baseArgs += listOf("-i", it.absolutePath) }
            if (hasSubtitles) {
                subtitleFile.writeText(SrtCodec.format(cues, useTranslation = true), Charsets.UTF_8)
                baseArgs += listOf("-i", subtitleFile.absolutePath)
            }

            fun buildArguments(includeOriginalAudio: Boolean): List<String> {
                val filters = mutableListOf<String>()
                voiceCues.forEachIndexed { index, cue ->
                    val delay = cue.startMs.coerceAtLeast(0L)
                    filters += "[${index + 1}:a]adelay=$delay|$delay[dub$index]"
                }

                val dubbedInputs = voiceCues.indices.joinToString("") { "[dub$it]" }
                if (includeOriginalAudio) {
                    filters += "[0:a]volume=0.22[original]"
                    filters += "[original]$dubbedInputs" +
                        "amix=inputs=${voiceCues.size + 1}:duration=longest:" +
                        "dropout_transition=0:normalize=0[aout]"
                } else {
                    filters += dubbedInputs +
                        "amix=inputs=${voiceCues.size}:duration=longest:" +
                        "dropout_transition=0:normalize=0[aout]"
                }

                val outputArgs = mutableListOf(
                    "-filter_complex", filters.joinToString(";"),
                    "-map", "0:v:0",
                    "-map", "[aout]",
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-b:a", "192k"
                )
                if (hasSubtitles) {
                    outputArgs += listOf("-map", "${audioFiles.size + 1}:s:0", "-c:s", "mov_text")
                }
                outputArgs += listOf(
                    "-movflags", "+faststart",
                    "-f", "mp4",
                    outputVideo.absolutePath
                )
                return baseArgs + outputArgs
            }

            var session = FFmpegKit.executeWithArguments(buildArguments(includeOriginalAudio = true).toTypedArray())
            if (!ReturnCode.isSuccess(session.returnCode)) {
                val logs = session.allLogsAsString.orEmpty()
                val noSourceAudio = logs.contains("matches no streams", ignoreCase = true) ||
                    logs.contains("Stream specifier ':a' in filtergraph", ignoreCase = true)
                if (!noSourceAudio) {
                    error("FFmpeg không thể ghép giọng đọc: ${session.failStackTrace ?: logs}")
                }
                outputVideo.delete()
                session = FFmpegKit.executeWithArguments(buildArguments(includeOriginalAudio = false).toTypedArray())
            }
            check(ReturnCode.isSuccess(session.returnCode)) {
                "FFmpeg không thể xuất video: ${session.failStackTrace ?: session.allLogsAsString}"
            }
            check(outputVideo.isFile && outputVideo.length() > 1024L) {
                "Tệp video xuất ra không hợp lệ."
            }

            context.contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                outputVideo.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Không thể ghi video vào vị trí đã chọn.")
        } finally {
            inputVideo.delete()
            outputVideo.delete()
            subtitleFile.delete()
        }
    }
}
