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
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/MuChi"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Android từ chối tạo mục video trong thư viện. Hãy kiểm tra dung lượng trống.")

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
                throw IOException("Lỗi ghi video vào Movies/MuChi: ${error.message ?: error.javaClass.simpleName}", error)
            }
        }
}
