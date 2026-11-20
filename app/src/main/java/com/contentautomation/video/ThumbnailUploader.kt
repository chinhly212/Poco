package com.contentautomation.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class ThumbnailUploader(
    private val context: Context,
    private val cloudName: String = "hihnixtt",
    private val uploadPreset: String = "nichi_thumbnails"
) {
    private val client = OkHttpClient()

    suspend fun extractAndUpload(videoUri: Uri, userId: String): String? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val frame: Bitmap? = retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime

            frame ?: return@withContext null

            val scaledWidth = 480
            val scaledHeight = ((frame.height.toFloat() / frame.width.toFloat()) * scaledWidth)
                .toInt()
                .coerceAtLeast(1)
            val resized = Bitmap.createScaledBitmap(frame, scaledWidth, scaledHeight, true)

            val stream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val bytes = stream.toByteArray()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "thumb_${System.currentTimeMillis()}.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaType())
                )
                .addFormDataPart("upload_preset", uploadPreset)
                .addFormDataPart("public_id", "${userId}_${System.currentTimeMillis()}")
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val raw = response.body?.string() ?: return@withContext null
                JSONObject(raw).optString("secure_url", null)
            }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
