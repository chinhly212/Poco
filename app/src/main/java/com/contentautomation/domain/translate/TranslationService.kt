package com.contentautomation.domain.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Google Cloud Translation v2 (Simple API key).
 * Người dùng cấu hình TRANSLATION_API_KEY trong Settings.
 */
class TranslationService(
    private val apiKeyProvider: () -> String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    suspend fun translate(sourceLang: String, targetLang: String, text: String): String =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext ""
            val key = apiKeyProvider()
            if (key.isBlank()) throw IllegalStateException("Translation API key chưa cấu hình")
            // Cắt text dài thành 4000 ký tự/chunk
            val chunks = text.chunked(4000)
            chunks.joinToString("\n") { chunk ->
                translateChunk(key, sourceLang, targetLang, chunk)
            }
        }

    private fun translateChunk(key: String, src: String, tgt: String, text: String): String {
        val body = JSONObject().apply {
            put("q", JSONArray().put(text))
            put("source", src)
            put("target", tgt)
            put("format", "text")
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://translation.googleapis.com/language/translate/v2?key=$key")
            .post(body).build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Translate HTTP ${resp.code}")
            val json = JSONObject(resp.body!!.string())
            val translations = json.getJSONObject("data").getJSONArray("translations")
            return translations.getJSONObject(0).getString("translatedText")
        }
    }
}
