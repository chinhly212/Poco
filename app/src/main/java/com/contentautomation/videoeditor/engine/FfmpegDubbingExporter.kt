package com.contentautomation.videoeditor.engine

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.contentautomation.videoeditor.domain.SubtitleCue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Muxes generated TTS clips into an MP4 while keeping the source video stream.
 * The rendered audio track contains the dubbed voice; source audio is not mixed
 * in this first pass.
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
        val inputVideo = File(workDir, "source-${System.currentTimeMillis()}.mp4")
        val outputVideo = File(workDir, "dubbed-${System.currentTimeMillis()}.mp4")

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

            val args = mutableListOf<String>()
            args += listOf("-y", "-i", inputVideo.absolutePath)
            audioFiles.forEach { args += listOf("-i", it.absolutePath) }

            val filters = mutableListOf<String>()
            voiceCues.forEachIndexed { index, cue ->
                val delay = cue.startMs.coerceAtLeast(0L)
                filters += "[${index + 1}:a]adelay=$delay|$delay[dub$index]"
            }
            val mixInputs = voiceCues.indices.joinToString("") { "[dub$it]" }
            filters += "$mixInputs" +
                "amix=inputs=${voiceCues.size}:duration=longest:dropout_transition=0:normalize=0[aout]"

            args += listOf(
                "-filter_complex", filters.joinToString(";"),
                "-map", "0:v:0",
                "-map", "[aout]",
                "-c:v", "copy",
                "-c:a", "aac",
                "-b:a", "192k",
                "-movflags", "+faststart",
                "-f", "mp4",
                outputVideo.absolutePath
            )

            val session = FFmpegKit.executeWithArguments(args.toTypedArray())
            check(ReturnCode.isSuccess(session.returnCode)) {
                "FFmpeg không thể ghép giọng đọc: ${session.failStackTrace ?: session.allLogsAsString}"
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
        }
    }
}
