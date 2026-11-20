package com.contentautomation.domain.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TranslationService(
    private val apiKeyProvider: () -> String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun translate(
        sourceLang: String,
        targetLang: String,
        text: String
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        require(targetLang.isNotBlank()) {
            "Ngôn ngữ đích không hợp lệ"
        }

        val key = apiKeyProvider().trim()
        if (key.isBlank()) {
            throw IllegalStateException("Translation API key chưa cấu hình")
        }

        val chunks = splitIntoChunks(text, 4000)
        val result = StringBuilder()

        for (chunk in chunks) {
            result.append(
                translateChunk(key, sourceLang, targetLang, chunk)
            )
        }

        result.toString()
    }

    private fun splitIntoChunks(
        text: String,
        maxCodePoints: Int
    ): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        var count = 0

        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            current.appendCodePoint(codePoint)
            count++

            index += Character.charCount(codePoint)

            if (count >= maxCodePoints) {
                chunks.add(current.toString())
                current.setLength(0)
                count = 0
            }
        }

        if (current.isNotEmpty()) {
            chunks.add(current.toString())
        }

        return chunks
    }

    private fun translateChunk(
        key: String,
        sourceLang: String,
        targetLang: String,
        text: String
    ): String {
        val body = JSONObject().apply {
            put("q", JSONArray().put(text))
            if (sourceLang.isNotBlank() && sourceLang != "auto") {
                put("source", sourceLang)
            }
            put("target", targetLang)
            put("format", "text")
        }.toString().toRequestBody("application/json".toMediaType())

        val url = "https://translation.googleapis.com/language/translate/v2"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", key)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Google Translate trả về dữ liệu rỗng")

            if (!response.isSuccessful) {
                throw IOException(
                    "Translate HTTP ${response.code}: ${extractApiError(responseBody)}"
                )
            }

            val json = try {
                JSONObject(responseBody)
            } catch (e: Exception) {
                throw IOException("Phản hồi Google Translate không hợp lệ", e)
            }

            if (json.has("error")) {
                throw IOException(extractApiError(responseBody))
            }

            val translations = json.optJSONObject("data")
                ?.optJSONArray("translations")
                ?: throw IOException("Không tìm thấy dữ liệu bản dịch")

            if (translations.length() == 0) {
                throw IOException("Google Translate không trả về bản dịch")
            }

            return translations.getJSONObject(0)
                .optString("translatedText")
                .takeIf { it.isNotBlank() }
                ?: throw IOException("Bản dịch trả về rỗng")
        }
    }

    private fun extractApiError(responseBody: String): String {
        return try {
            JSONObject(responseBody)
                .optJSONObject("error")
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: "Không xác định được lỗi dịch"
        } catch (_: Exception) {
            "Không thể xử lý yêu cầu dịch"
        }
    }
}
