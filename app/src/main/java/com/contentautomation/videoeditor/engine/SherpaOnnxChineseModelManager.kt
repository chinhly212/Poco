package com.contentautomation.videoeditor.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads the optional 82 MB Chinese/English Paraformer model only when the
 * user asks for speech recognition. Model weights are never bundled in the APK.
 */
class SherpaOnnxChineseModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val modelDirectory = File(
        appContext.filesDir,
        "speech-models/sherpa-onnx-paraformer-zh-small-2024-03-09"
    )

    private val modelFile get() = File(modelDirectory, "model.int8.onnx")
    private val tokensFile get() = File(modelDirectory, "tokens.txt")

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    fun isInstalled(): Boolean =
        modelFile.isFile && modelFile.length() > 70_000_000L &&
            tokensFile.isFile && tokensFile.length() > 10_000L

    fun modelPath(): String = modelFile.absolutePath
    fun tokensPath(): String = tokensFile.absolutePath

    suspend fun download(onProgress: (Long, Long) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) {
            if (isInstalled()) return@withContext
            if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
                error("Không tạo được thư mục lưu mô hình STT.")
            }

            val modelUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-paraformer-zh-small-2024-03-09/resolve/main/model.int8.onnx"
            val tokensUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-paraformer-zh-small-2024-03-09/resolve/main/tokens.txt"
            val modelTemp = File(modelDirectory, "model.int8.onnx.part")
            val tokensTemp = File(modelDirectory, "tokens.txt.part")

            try {
                downloadFile(modelUrl, modelTemp, onProgress)
                val actualSha = sha256(modelTemp)
                require(actualSha == EXPECTED_MODEL_SHA256) {
                    "Kiểm tra SHA-256 mô hình không khớp; tệp tải xuống bị lỗi."
                }
                downloadFile(tokensUrl, tokensTemp) { _, _ -> }
                require(tokensTemp.length() > 10_000L) { "Tệp tokens.txt không hợp lệ." }

                replaceAtomically(modelTemp, modelFile)
                replaceAtomically(tokensTemp, tokensFile)
                require(isInstalled()) { "Không xác minh được mô hình sau khi tải." }
            } catch (error: Throwable) {
                modelTemp.delete()
                tokensTemp.delete()
                throw error
            }
        }

    private fun downloadFile(
        url: String,
        destination: File,
        onProgress: (Long, Long) -> Unit
    ) {
        val request = Request.Builder().url(url).header("User-Agent", "MUCHI-Android").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Tải mô hình thất bại: HTTP ${response.code}")
            val body = response.body ?: error("Máy chủ không trả dữ liệu mô hình.")
            val total = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                    output.fd.sync()
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun replaceAtomically(source: File, destination: File) {
        if (destination.exists() && !destination.delete()) {
            error("Không thay thế được tệp mô hình cũ.")
        }
        if (!source.renameTo(destination)) {
            source.copyTo(destination, overwrite = true)
            if (!source.delete()) source.deleteOnExit()
        }
    }

    companion object {
        private const val EXPECTED_MODEL_SHA256 =
            "3ef6c19369b912f7caf3cef8e545c5ccd1a33d9d7ec792a46668dc41c4b229ec"
    }
}
