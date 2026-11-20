package com.contentautomation.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class VideoRecordPayload(
    val telegramMessageId: Long,
    val fileName: String,
    val sizeBefore: Long,
    val sizeAfter: Long,
    val thumbnailUrl: String?
)

class NiChiApiClient(private val workerBaseUrl: String) {
    private val client = OkHttpClient()

    suspend fun saveVideoRecord(
        bearerToken: String,
        payload: VideoRecordPayload
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("telegram_message_id", payload.telegramMessageId)
                put("file_name", payload.fileName)
                put("size_before", payload.sizeBefore)
                put("size_after", payload.sizeAfter)
                put("thumbnail_url", payload.thumbnailUrl)
            }

            val request = Request.Builder()
                .url("${workerBaseUrl.trimEnd('/')}/api/videos")
                .addHeader("Authorization", "Bearer $bearerToken")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("API ${response.code}: $bodyStr"))
                }
                Result.success(JSONObject(bodyStr))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
