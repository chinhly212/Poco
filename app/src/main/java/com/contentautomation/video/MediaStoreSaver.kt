package com.contentautomation.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class MediaStoreSaver(private val context: Context) {

    suspend fun saveVideoToGallery(sourceFile: File, displayName: String): Uri =
        withContext(Dispatchers.IO) {
            if (!sourceFile.isFile) {
                throw IOException("Không tìm thấy file video tạm để lưu.")
            }
            if (sourceFile.length() <= 0L) {
                throw IOException("File video tải về bị rỗng.")
            }

            val safeName = displayName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .let { if (it.endsWith(".mp4", ignoreCase = true)) it else "$it.mp4" }

            val resolver = context.contentResolver
            // Save directly in the standard Movies folder. Some Android/OriginOS
            // MediaStore providers reject creating app-specific subfolders.
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            // Try the primary external volume first, then the legacy video
            // collection, and finally the generic Files collection. Some OEM
            // MediaStore providers expose only one of these insert endpoints.
            val insertTargets = mutableListOf<Pair<Uri, ContentValues>>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                insertTargets += MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to ContentValues(values)
            }
            insertTargets += MediaStore.Video.Media.EXTERNAL_CONTENT_URI to ContentValues(values)
            val filesValues = ContentValues(values).apply {
                put(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            }
            insertTargets += MediaStore.Files.getContentUri("external") to filesValues

            val insertErrors = mutableListOf<String>()
            var createdUri: Uri? = null
            for ((collectionUri, insertValues) in insertTargets) {
                try {
                    createdUri = resolver.insert(collectionUri, insertValues)
                    if (createdUri != null) break
                    insertErrors += "$collectionUri: MediaStore trả URI rỗng"
                } catch (error: Exception) {
                    insertErrors += "$collectionUri: ${error.javaClass.simpleName}: ${error.message ?: "không có thông tin"}"
                }
            }
            val uri = createdUri ?: throw IOException(
                "Android từ chối tạo video trong Movies qua tất cả bộ sưu tập MediaStore. Chi tiết: ${insertErrors.joinToString(" | ")}"
            )

            try {
                val output = resolver.openOutputStream(uri, "w")
                    ?: throw IOException("Android không mở được luồng ghi vào thư viện.")
                val copiedBytes = output.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out).also { out.flush() }
                    }
                }

                if (copiedBytes <= 0L) {
                    throw IOException("Không ghi được dữ liệu video vào thư viện.")
                }
                if (copiedBytes != sourceFile.length()) {
                    throw IOException("Video ghi vào thư viện chưa đủ dữ liệu: ${copiedBytes}/${sourceFile.length()} byte.")
                }

                val publishValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                val updated = resolver.update(uri, publishValues, null, null)
                if (updated <= 0) {
                    throw IOException("Đã chép video nhưng Android không thể hoàn tất hiển thị trong thư viện.")
                }
                uri
            } catch (error: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                if (error is IOException) throw error
                throw IOException("Lỗi ghi video vào thư viện Movies: ${error.message ?: error.javaClass.simpleName}", error)
            }
        }
}
