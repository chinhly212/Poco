package com.contentautomation.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class MediaStoreSaver(private val context: Context) {

    suspend fun saveVideoToGallery(sourceFile: File, displayName: String): Uri? =
        withContext(Dispatchers.IO) {
            if (!sourceFile.isFile || sourceFile.length() <= 0L) return@withContext null

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/MuChi"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            try {
                val copiedBytes = resolver.openOutputStream(uri, "w")?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out).also { out.flush() }
                    }
                } ?: throw IOException("Không mở được luồng ghi vào thư viện.")

                if (copiedBytes <= 0L) {
                    throw IOException("Tệp video không có dữ liệu.")
                }

                val publishValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                if (resolver.update(uri, publishValues, null, null) <= 0) {
                    throw IOException("Không thể hoàn tất lưu video vào thư viện.")
                }
                uri
            } catch (_: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        }
}
