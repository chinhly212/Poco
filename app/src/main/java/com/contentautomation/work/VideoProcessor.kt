package com.contentautomation.domain.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

data class VideoInfo(
    val duration: Long,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean,
    val sizeBytes: Long
)

class VideoProcessor {

    suspend fun probe(path: String): VideoInfo = withContext(Dispatchers.IO) {
        val session = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(path)
        val info = session.mediaInformation
            ?: throw IllegalStateException("FFprobe failed: ${session.failStackTrace}")
        val duration = info.duration?.toDoubleOrNull()?.times(1000)?.toLong() ?: 0L
        val stream = info.streams.firstOrNull { it.type == "video" }
        
        // Sửa lỗi ép kiểu Long thành Int
        val width = stream?.width?.toInt() ?: 0
        val height = stream?.height?.toInt() ?: 0
        val hasAudio = info.streams.any { it.type == "audio" }
        
        VideoInfo(duration, width, height, hasAudio, File(path).length())
    }

    suspend fun processToTikTok(input: String, output: String): String {
        // Sửa lỗi: Truyền nguyên 1 chuỗi String vào executeAsync thay vì Array
        val cmd = "-y -i \"$input\" -vf \"scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2\" -c:v libx264 -preset medium -crf 23 -c:a aac -b:a 128k -movflags +faststart \"$output\""
        
        val session = suspendCancellableCoroutine<FFmpegSession> { cont ->
            FFmpegKit.executeAsync(cmd) { s -> cont.resume(s) }
        }
        if (!ReturnCode.isSuccess(session.returnCode)) {
            throw RuntimeException("FFmpeg failed: ${session.failStackTrace}")
        }
        return output
    }

    suspend fun ensureAudioTrack(input: String, output: String) {
        val info = probe(input)
        if (info.hasAudio) {
            File(input).copyTo(File(output), overwrite = true)
            return
        }
        val cmd = "-y -i \"$input\" -f lavfi -i anullsrc=channel_layout=stereo:sample_rate=44100 -shortest -c:v copy -c:a aac \"$output\""
        
        val session = suspendCancellableCoroutine<FFmpegSession> { cont ->
            FFmpegKit.executeAsync(cmd) { s -> cont.resume(s) }
        }
        if (!ReturnCode.isSuccess(session.returnCode)) {
            throw RuntimeException("FFmpeg audio pad failed: ${session.failStackTrace}")
        }
    }
}
