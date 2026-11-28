package com.contentautomation.videoeditor.engine

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.contentautomation.videoeditor.domain.ProcessingProfile
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * Offline Chinese/English STT. Paraformer does not provide timestamps, so
 * transcript timing is assigned to bounded 30-second audio windows.
 */
class SherpaOnnxVideoSpeechRecognizer(
    private val modelManager: SherpaOnnxChineseModelManager
) : VideoSpeechRecognizer {

    override suspend fun capability(context: Context): EngineCapability {
        val installed = modelManager.isInstalled()
        return EngineCapability(
            id = "sherpa-onnx-paraformer-zh-small",
            displayName = "Sherpa-ONNX Paraformer (Chinese/English)",
            available = installed,
            onDevice = true,
            requiresNetwork = false,
            modelSizeBytes = 81_800_000L,
            reasonUnavailable = if (installed) null else "Cần tải mô hình STT khoảng 82 MB trước khi nhận diện."
        )
    }

    override suspend fun recognize(
        context: Context,
        videoUri: Uri,
        profile: ProcessingProfile,
        sourceLanguage: String
    ): List<SpeechSegment> = withContext(Dispatchers.IO) {
        require(modelManager.isInstalled()) { "Chưa có mô hình STT. Hãy tải mô hình tiếng Trung trước." }
        val normalizedLanguage = sourceLanguage.trim().lowercase()
        require(
            normalizedLanguage.isBlank() ||
                normalizedLanguage.contains("tự động") ||
                normalizedLanguage.contains("auto") ||
                normalizedLanguage.contains("中文") ||
                normalizedLanguage.contains("trung") ||
                normalizedLanguage.contains("chinese") ||
                normalizedLanguage == "zh" ||
                normalizedLanguage.startsWith("zh-") ||
                normalizedLanguage.contains("english") ||
                normalizedLanguage == "en" ||
                normalizedLanguage.startsWith("en-")
        ) {
            "Mô hình STT hiện tại hỗ trợ tiếng Trung và tiếng Anh; chưa hỗ trợ nhận diện tiếng Việt."
        }

        val input = File.createTempFile("muchi-stt-source-", ".video", context.cacheDir)
        val pcm = File.createTempFile("muchi-stt-audio-", ".pcm", context.cacheDir)
        try {
            context.contentResolver.openInputStream(videoUri)?.use { source ->
                input.outputStream().use { target -> source.copyTo(target) }
            } ?: error("Không mở được video nguồn.")

            val command = "-y -i ${quote(input.absolutePath)} -vn -ac 1 -ar 16000 -f s16le ${quote(pcm.absolutePath)}"
            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                error("Không tách được âm thanh: ${session.failStackTrace ?: session.returnCode}")
            }
            require(pcm.length() >= 2L) { "Video không có luồng âm thanh hợp lệ." }

            val threads = when (profile) {
                ProcessingProfile.LOW_MEMORY -> 1
                ProcessingProfile.BALANCED -> 2
                ProcessingProfile.PERFORMANCE -> 4
            }
            val modelConfig = OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(model = modelManager.modelPath()),
                tokens = modelManager.tokensPath(),
                modelType = "paraformer",
                numThreads = threads,
                provider = "cpu"
            )
            val recognizer = OfflineRecognizer(
                config = OfflineRecognizerConfig(modelConfig = modelConfig)
            )
            try {
                decodeInChunks(recognizer, pcm)
            } finally {
                recognizer.release()
            }
        } finally {
            input.delete()
            pcm.delete()
        }
    }

    private fun decodeInChunks(
        recognizer: OfflineRecognizer,
        pcmFile: File
    ): List<SpeechSegment> {
        val sampleRate = 16_000
        val chunkSamples = sampleRate * 30
        val bytesPerChunk = chunkSamples * 2
        val output = mutableListOf<SpeechSegment>()
        RandomAccessFile(pcmFile, "r").use { audio ->
            val totalSamples = pcmFile.length() / 2L
            var startSample = 0L
            val bytes = ByteArray(bytesPerChunk)
            while (startSample < totalSamples) {
                val wanted = min(bytesPerChunk.toLong(), (totalSamples - startSample) * 2L).toInt()
                audio.readFully(bytes, 0, wanted)
                val sampleCount = wanted / 2
                val samples = FloatArray(sampleCount) { index ->
                    val low = bytes[index * 2].toInt() and 0xff
                    val high = bytes[index * 2 + 1].toInt()
                    (((high shl 8) or low).toShort().toInt() / 32768.0f)
                }
                if (samples.isNotEmpty()) {
                    val stream = recognizer.createStream()
                    try {
                        stream.acceptWaveform(samples, sampleRate)
                        recognizer.decode(stream)
                        val text = recognizer.getResult(stream).text.trim()
                        if (text.isNotEmpty()) {
                            val startMs = startSample * 1000L / sampleRate
                            val endMs = (startSample + sampleCount) * 1000L / sampleRate
                            output += SpeechSegment(startMs, endMs, text)
                        }
                    } finally {
                        stream.release()
                    }
                }
                startSample += sampleCount
            }
        }
        return output
    }

    private fun quote(path: String): String =
        "'" + path.replace("'", "'\\''") + "'"
}
