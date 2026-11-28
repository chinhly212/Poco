package com.contentautomation.videoeditor.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.contentautomation.videoeditor.domain.SubtitleCue
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generates one local audio file per translated subtitle using the Android
 * system TTS engine. No model or network download is required.
 */
class AndroidTextToSpeechDubbingEngine(private val context: Context) {

    suspend fun generateAll(
        cues: List<SubtitleCue>,
        language: String,
        speed: Float = 1f
    ): List<SubtitleCue> = withContext(Dispatchers.Main.immediate) {
        val pending = cues.filter { it.translatedText.isNotBlank() }
        require(pending.isNotEmpty()) { "Chưa có bản dịch để lồng tiếng." }

        val outputDir = File(context.filesDir, "muchi-dubbing").apply { mkdirs() }
        check(outputDir.isDirectory) { "Không tạo được thư mục âm thanh lồng tiếng." }

        val tts = createEngine()
        try {
            val locale = resolveLocale(language)
            val availability = tts.isLanguageAvailable(locale)
            check(
                availability == TextToSpeech.LANG_AVAILABLE ||
                    availability == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    availability == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            ) {
                "Thiết bị chưa cài giọng đọc cho $language. Hãy cài dữ liệu giọng nói trong cài đặt Text-to-Speech của Android."
            }
            check(tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) {
                "Không thể chọn giọng đọc $language."
            }
            tts.setSpeechRate(speed.coerceIn(0.7f, 1.3f))

            val generated = mutableMapOf<Long, String>()
            pending.forEachIndexed { index, cue ->
                val file = File(outputDir, "cue_${cue.id}_${UUID.randomUUID()}.wav")
                val utteranceId = "muchi_${cue.id}_${UUID.randomUUID()}"
                val completion = CompletableDeferred<Unit>()
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) {
                        if (id == utteranceId) completion.complete(Unit)
                    }
                    @Deprecated("Deprecated by Android")
                    override fun onError(id: String?) {
                        if (id == utteranceId) completion.completeExceptionally(
                            IllegalStateException("Android TTS không tạo được đoạn ${index + 1}.")
                        )
                    }
                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) completion.completeExceptionally(
                            IllegalStateException("Android TTS lỗi mã $errorCode ở đoạn ${index + 1}.")
                        )
                    }
                })

                val result = tts.synthesizeToFile(
                    cue.translatedText,
                    Bundle(),
                    file,
                    utteranceId
                )
                check(result == TextToSpeech.SUCCESS) {
                    "Không thể bắt đầu tạo âm thanh đoạn ${index + 1}."
                }
                try {
                    completion.await()
                    check(file.exists() && file.length() > 44L) {
                        "Tệp âm thanh đoạn ${index + 1} rỗng hoặc không hợp lệ."
                    }
                    generated[cue.id] = file.absolutePath
                } catch (error: Throwable) {
                    file.delete()
                    throw error
                }
            }

            cues.map { cue ->
                generated[cue.id]?.let { cue.copy(voiceAudioPath = it) } ?: cue
            }
        } finally {
            tts.stop()
            tts.shutdown()
        }
    }

    private suspend fun createEngine(): TextToSpeech {
        val initialized = CompletableDeferred<TextToSpeech>()
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // The instance is supplied below after construction.
            } else {
                initialized.completeExceptionally(
                    IllegalStateException("Không khởi tạo được Android Text-to-Speech.")
                )
            }
        }
        // Initialization callback can arrive after the constructor returns.
        // A short listener bridge avoids blocking the main thread.
        val ready = CompletableDeferred<Unit>()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?) = Unit
        })
        // TextToSpeech initialization is asynchronous; wait for its callback
        // using a second instance-free latch set by the callback below.
        engine.shutdown()
        return createInitializedEngine()
    }

    private suspend fun createInitializedEngine(): TextToSpeech {
        val result = CompletableDeferred<TextToSpeech>()
        lateinit var instance: TextToSpeech
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) result.complete(instance)
            else result.completeExceptionally(
                IllegalStateException("Không khởi tạo được Android Text-to-Speech.")
            )
        }
        return try {
            result.await()
        } catch (error: Throwable) {
            instance.shutdown()
            throw error
        }
    }

    private fun resolveLocale(language: String): Locale {
        val value = language.lowercase(Locale.ROOT)
        return when {
            "vi" in value || "việt" in value -> Locale.forLanguageTag("vi-VN")
            "zh" in value || "trung" in value -> Locale.SIMPLIFIED_CHINESE
            "ja" in value || "nhật" in value -> Locale.JAPANESE
            "ko" in value || "hàn" in value -> Locale.KOREAN
            "fr" in value || "pháp" in value -> Locale.FRENCH
            "es" in value || "tây ban nha" in value -> Locale("es", "ES")
            "de" in value || "đức" in value -> Locale.GERMAN
            else -> Locale.US
        }
    }
}
