package com.contentautomation.video

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class MediaStoreSaver(private val context: Context) {

    suspend fun saveVideoToGallery(sourceFile: File, displayName: String): Uri {
        if (!sourceFile.isFile) throw IOException("Không tìm thấy video cần lưu.")
        if (sourceFile.length() <= 0L) throw IOException("Video tải về bị rỗng.")
        val safeName = normalizeDisplayName(displayName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findExistingVideo(safeName, sourceFile.length())?.let { return it }
        }
        return saveVideoFromStream(FileInputStream(sourceFile), safeName, sourceFile.length())
    }

    private suspend fun findExistingVideo(displayName: String, expectedBytes: Long): Uri? =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.SIZE)
            val selection = "\${MediaStore.Video.Media.DISPLAY_NAME}=? AND \${MediaStore.Video.Media.RELATIVE_PATH}=?"
            val args = arrayOf(displayName, "\${Environment.DIRECTORY_MOVIES}/")
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "\${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                while (cursor.moveToNext()) {
                    if (cursor.getLong(sizeColumn) == expectedBytes) {
                        return@withContext Uri.withAppendedPath(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idColumn).toString()
                        )
                    }
                }
            }
            null
        }

    /**
     * Saves a network stream straight into the public Movies library. No
     * user-visible intermediate download file is created.
     */
    suspend fun saveVideoFromStream(
        inputStream: InputStream,
        displayName: String,
        expectedBytes: Long? = null
    ): Uri = withContext(Dispatchers.IO) {
        val safeName = normalizeDisplayName(displayName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            saveLegacyFile(inputStream, safeName, expectedBytes)
        } else {
            saveMediaStoreStream(inputStream, safeName, expectedBytes)
        }
    }

    private fun normalizeDisplayName(displayName: String): String {
        val clean = displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("""[/:*?"<>|]"""), "_")
            .trim()
            .take(110)
            .ifBlank { "MuChi_${System.currentTimeMillis()}" }
        return if (clean.endsWith(".mp4", ignoreCase = true)) clean else "$clean.mp4"
    }

    private fun saveLegacyFile(
        inputStream: InputStream,
        safeName: String,
        expectedBytes: Long?
    ): Uri {
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            inputStream.close()
            throw IOException("Android cần quyền lưu trữ để ghi video vào thư mục Movies.")
        }
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        if (!moviesDir.exists() && !moviesDir.mkdirs()) {
            inputStream.close()
            throw IOException("Không tạo được thư mục Movies trên điện thoại.")
        }
        val outputFile = File(moviesDir, safeName).let { candidate ->
            if (!candidate.exists()) candidate else File(
                moviesDir,
                "${candidate.nameWithoutExtension}_${System.currentTimeMillis()}.mp4"
            )
        }
        return try {
            val copied = FileOutputStream(outputFile).use { output ->
                inputStream.use { source -> source.copyTo(output).also { output.flush() } }
            }
            verifyCopiedBytes(copied, expectedBytes)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(outputFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )
            Uri.fromFile(outputFile)
        } catch (error: Exception) {
            inputStream.close()
            outputFile.delete()
            if (error is IOException) throw error
            throw IOException("Không thể lưu video vào thư mục Movies: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    private fun saveMediaStoreStream(
        inputStream: InputStream,
        safeName: String,
        expectedBytes: Long?
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val targets = mutableListOf<Pair<Uri, ContentValues>>()
        targets += MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to ContentValues(values)
        targets += MediaStore.Video.Media.EXTERNAL_CONTENT_URI to ContentValues(values)
        val genericValues = ContentValues(values).apply {
            put(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        }
        targets += MediaStore.Files.getContentUri("external") to genericValues

        val insertErrors = mutableListOf<String>()
        var createdUri: Uri? = null
        for ((collection, insertValues) in targets) {
            try {
                createdUri = resolver.insert(collection, insertValues)
                if (createdUri != null) break
                insertErrors += "$collection: MediaStore trả URI rỗng"
            } catch (error: Exception) {
                insertErrors += "$collection: ${error.javaClass.simpleName}: ${error.message ?: "không có thông tin"}"
            }
        }
        val uri = createdUri ?: run {
            inputStream.close()
            throw IOException("Android không tạo được mục video trong thư viện. ${insertErrors.joinToString(" | ")}")
        }

        return try {
            val output = resolver.openOutputStream(uri, "w")
                ?: throw IOException("Android không mở được luồng ghi vào thư viện.")
            val copied = output.use { destination ->
                inputStream.use { source -> source.copyTo(destination).also { destination.flush() } }
            }
            verifyCopiedBytes(copied, expectedBytes)

            val published = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            val updated = resolver.update(uri, published, null, null)
            if (updated <= 0) {
                val visible = resolver.query(
                    uri,
                    arrayOf(MediaStore.Video.Media.IS_PENDING),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    cursor.moveToFirst() &&
                        cursor.getInt(0) == 0
                } ?: false
                if (!visible) throw IOException("Đã chép video nhưng Android chưa xác nhận hiển thị trong thư viện.")
            }
            uri
        } catch (error: Exception) {
            inputStream.close()
            runCatching { resolver.delete(uri, null, null) }
            if (error is IOException) throw error
            throw IOException("Không thể lưu video vào thư viện Movies: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    private fun verifyCopiedBytes(copied: Long, expectedBytes: Long?) {
        if (copied <= 0L) throw IOException("Không nhận được dữ liệu video để lưu.")
        if (expectedBytes != null && expectedBytes > 0L && copied != expectedBytes) {
            throw IOException("Video chưa tải đủ dữ liệu: ${copied}/${expectedBytes} byte.")
        }
    }
}
