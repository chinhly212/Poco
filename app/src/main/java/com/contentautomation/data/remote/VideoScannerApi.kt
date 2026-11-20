package com.contentautomation.data.remote

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ScannedVideoItem(
    val videoId: String,
    val title: String,
    val webpageUrl: String
)

class VideoScannerApi(context: Context) {

    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchChannelVideos(
        apiUrl: String,
        limit: Int = 5
    ): Result<List<ScannedVideoItem>> = withContext(Dispatchers.IO) {
        try {
            require(apiUrl.startsWith("https://")) {
                "API phải sử dụng HTTPS"
            }

            require(limit in 1..100) {
                "Limit phải từ 1 đến 100"
            }

            val request = Request.Builder()
                .url(apiUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP ${response.code}: Không thể lấy danh sách video"
                    )
                }

                val body = response.body?.string()
                    ?: throw IOException("API trả về dữ liệu rỗng")

                val json = JSONObject(body)

                val entries = json.optJSONArray("entries")
                    ?: json.optJSONArray("videos")
                    ?: json.optJSONArray("items")
                    ?: json.optJSONObject("data")?.optJSONArray("videos")
                    ?: json.optJSONObject("data")?.optJSONArray("items")
                    ?: JSONArray()

                val result = mutableListOf<ScannedVideoItem>()

                for (i in 0 until minOf(entries.length(), limit)) {
                    val item = entries.optJSONObject(i) ?: continue

                    val id = item.optString("id")
                        .ifBlank { item.optString("video_id") }

                    val url = item.optString("webpage_url")
                        .ifBlank { item.optString("url") }
                        .ifBlank { item.optString("link") }

                    val title = item.optString("title")
                        .ifBlank { "Video_$id" }

                    if (id.isNotBlank() && url.startsWith("https://")) {
                        result.add(
                            ScannedVideoItem(
                                videoId = id,
                                title = title,
                                webpageUrl = url
                            )
                        )
                    }
                }

                Result.success(result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadVideo(
        videoId: String,
        videoUrl: String
    ): Result<File> = withContext(Dispatchers.IO) {

        var temporaryFile: File? = null

        try {
            require(videoId.isNotBlank()) {
                "Video ID không hợp lệ"
            }

            require(videoUrl.startsWith("https://")) {
                "URL video phải sử dụng HTTPS"
            }

            val safeId = videoId.replace(
                Regex("[^a-zA-Z0-9_-]"),
                "_"
            )

            val rawDir = File(
                appContext.getExternalFilesDir(null),
                "raw_videos"
            )

            if (!rawDir.exists() && !rawDir.mkdirs()) {
                throw IOException("Không thể tạo thư mục raw_videos")
            }

            val targetFile = File(rawDir, "$safeId.mp4")
            temporaryFile = File(rawDir, "$safeId.tmp")

            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }

            val request = Request.Builder()
                .url(videoUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP ${response.code}: Tải video thất bại"
                    )
                }

                val contentType = response.header("Content-Type").orEmpty()

                if (
                    contentType.contains("text/html", ignoreCase = true) ||
                    contentType.contains("application/json", ignoreCase = true)
                ) {
                    throw IOException("URL không trả về dữ liệu video")
                }

                val body = response.body
                    ?: throw IOException("Không có dữ liệu tải xuống")

                body.byteStream().use { input ->
                    temporaryFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (
                !temporaryFile.exists() ||
                temporaryFile.length() < 10_240L
            ) {
                throw IOException("File video rỗng hoặc không hợp lệ")
            }

            if (targetFile.exists() && !targetFile.delete()) {
                throw IOException("Không thể thay thế file video cũ")
            }

            if (!temporaryFile.renameTo(targetFile)) {
                temporaryFile.copyTo(targetFile, overwrite = true)
                if (!temporaryFile.delete()) {
                    temporaryFile.deleteOnExit()
                }
            }

            Result.success(targetFile)

        } catch (e: CancellationException) {
            temporaryFile?.let {
                if (it.exists()) it.delete()
            }
            throw e
        } catch (e: Exception) {
            temporaryFile?.let {
                if (it.exists()) it.delete()
            }
            Result.failure(e)
        }
    }
}
