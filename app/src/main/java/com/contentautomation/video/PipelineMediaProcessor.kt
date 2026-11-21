package com.contentautomation.video

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Real translation, Vietnamese speech synthesis and video/audio muxing for MuChi.
 * Speech is generated from translated title/description metadata. It does not claim
 * to transcribe dialogue embedded in the original video.
 */
class PipelineMediaProcessor(private val context: Context) {
    private val api = AiVideoServerClient()
    private val transcriptionClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    /**
     * Extracts a small mono audio track and transcribes Chinese speech with Groq Whisper.
     * The API key is passed per request and is never written to logs or disk.
     */
    suspend fun transcribeChinese(video: File, groqApiKey: String): String =
        withContext(Dispatchers.IO) {
            require(groqApiKey.isNotBlank()) { "Chưa có Groq API key để nhận diện lời nói." }
            require(video.exists() && video.length() > 0L) { "Không tìm thấy video để nhận diện lời nói." }

            val audio = File(video.parentFile, "source-audio.m4a")
            if (audio.exists()) audio.delete()
            try {
                val escapedVideo = video.absolutePath.replace("'", "'\\''")
                val escapedAudio = audio.absolutePath.replace("'", "'\\''")
                val command = "-y -i '$escapedVideo' -vn -ac 1 -ar 16000 -c:a aac -b:a 32k -f ipod '$escapedAudio'"
                val extractSession = FFmpegKit.execute(command)
                if (!ReturnCode.isSuccess(extractSession.returnCode) ||
                    !audio.exists() || audio.length() <= 0L) {
                    throw IllegalStateException("Không tách được âm thanh để nhận diện: \${(extractSession.failStackTrace ?: "").take(300)}")
                }
                if (audio.length() > 24L * 1024L * 1024L) {
                    throw IllegalStateException("Âm thanh sau khi tách vẫn vượt 24 MB. Hãy dùng video ngắn hơn.")
                }

                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", "whisper-large-v3-turbo")
                    .addFormDataPart("language", "zh")
                    .addFormDataPart("response_format", "json")
                    .addFormDataPart("file", audio.name, audio.asRequestBody("audio/mp4".toMediaType()))
                    .build()
                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/audio/transcriptions")
                    .header("Authorization", "Bearer $groqApiKey")
                    .post(multipart)
                    .build()

                transcriptionClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }
                            .getOrNull()?.takeIf { it.isNotBlank() }
                        throw IllegalStateException("Groq Whisper HTTP \${response.code}: \${detail ?: body.take(300)}")
                    }
                    val transcript = JSONObject(body).optString("text").trim()
                    require(transcript.isNotBlank()) { "Groq Whisper không nhận diện được lời nói trong video." }
                    transcript
                }
            } finally {
                audio.delete()
            }
        }

    suspend fun translateToVietnamese(
        sourceText: String,
        apiKeys: Map<String, String>
    ): String {
        val input = sourceText.trim().take(7000)
        require(input.isNotBlank()) { "Video không có tiêu đề hoặc mô tả để dịch." }
        require(apiKeys.values.any { it.isNotBlank() }) {
            "Chưa có API AI đã lưu. Hãy vào Cài đặt → API, kết nối Gemini, OpenRouter hoặc Groq trước."
        }

        val prompt = """
            Dịch nội dung tiếng Trung sau sang tiếng Việt tự nhiên, đúng nghĩa.
            Chỉ trả về bản dịch tiếng Việt, không giải thích, không thêm lời dẫn.
            Giữ nguyên tên riêng và con số.
            
            Nội dung:
            $input
        """.trimIndent()

        val result = api.sendChat(prompt, apiKeys).message.trim()
        require(result.isNotBlank()) { "AI không trả về bản dịch." }
        require(!result.contains("Chưa cấu hình API mô hình AI", ignoreCase = true) &&
            !result.contains("Các mô hình AI hiện không phản hồi", ignoreCase = true)) {
            "Máy chủ AI không gọi được nhà cung cấp. Hãy kiểm tra API trong Cài đặt."
        }
        return result
    }

    suspend fun synthesizeVietnamese(text: String, output: File) {
        require(text.isNotBlank()) { "Nội dung giọng đọc đang trống." }
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { continuation ->
                var engine: TextToSpeech? = null
                var finished = false
                fun finish(error: Throwable?) {
                    if (finished) return
                    finished = true
                    engine?.stop()
                    engine?.shutdown()
                    if (continuation.isActive) {
                        if (error == null) continuation.resume(Unit)
                        else continuation.resumeWith(Result.failure(error))
                    }
                }

                engine = TextToSpeech(context) { status ->
                    val tts = engine
                    if (status != TextToSpeech.SUCCESS || tts == null) {
                        finish(IllegalStateException("Không khởi tạo được bộ đọc văn bản Android."))
                        return@TextToSpeech
                    }

                    val languageStatus = tts.setLanguage(Locale("vi", "VN"))
                    if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                        languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                        finish(IllegalStateException("Điện thoại chưa có giọng đọc tiếng Việt. Hãy cài dữ liệu Text-to-Speech tiếng Việt trong cài đặt Android."))
                        return@TextToSpeech
                    }

                    val utteranceId = "muchi-${UUID.randomUUID()}"
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) {
                            if (id == utteranceId) {
                                if (output.exists() && output.length() > 0L) finish(null)
                                else finish(IllegalStateException("Android TTS kết thúc nhưng không tạo được tệp âm thanh."))
                            }
                        }
                        override fun onError(id: String?) {
                            if (id == utteranceId) finish(IllegalStateException("Không tạo được giọng đọc tiếng Việt."))
                        }
                        override fun onError(id: String?, errorCode: Int) {
                            if (id == utteranceId) finish(IllegalStateException("TTS báo lỗi mã $errorCode."))
                        }
                    })

                    val params = Bundle()
                    val result = tts.synthesizeToFile(text, params, output, utteranceId)
                    if (result != TextToSpeech.SUCCESS) {
                        finish(IllegalStateException("Không thể bắt đầu tạo tệp giọng đọc (mã $result)."))
                    }
                }

                continuation.invokeOnCancellation {
                    engine?.stop()
                    engine?.shutdown()
                }
            }
        }
        require(output.exists() && output.length() > 0L) {
            "Không tìm thấy tệp giọng đọc sau khi tạo."
        }
    }

    suspend fun mergeVideoAndVoice(video: File, voice: File, output: File) =
        withContext(Dispatchers.IO) {
            require(video.exists() && video.length() > 0L) { "Không tìm thấy video đã tối ưu." }
            require(voice.exists() && voice.length() > 0L) { "Không tìm thấy âm thanh tiếng Việt." }
            output.parentFile?.mkdirs()
            if (output.exists()) output.delete()

            fun q(file: File): String = "'" + file.absolutePath.replace("'", "'\\''") + "'"
            val command = listOf(
                "-y",
                "-i ${q(video)}",
                "-i ${q(voice)}",
                "-map 0:v:0",
                "-map 1:a:0",
                "-c:v copy",
                "-c:a aac",
                "-b:a 192k",
                "-af apad",
                "-shortest",
                "-movflags +faststart",
                q(output)
            ).joinToString(" ")

            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode) ||
                !output.exists() || output.length() <= 0L) {
                output.delete()
                val detail = session.failStackTrace ?: session.allLogsAsString
                throw IllegalStateException("Ghép giọng Việt vào video thất bại: ${detail.take(500)}")
            }
        }
}
