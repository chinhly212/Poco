package com.contentautomation.domain.drive

import android.content.Context
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream

/**
 * Đọc Service Account JSON từ assets/google-service-account.json.
 * YÊU CẦU: chia sẻ thư mục Drive cho email service account
 * (xxx@project.iam.gserviceaccount.com) với quyền Editor.
 */
class GoogleDriveService(private val context: Context) {

    private val drive: Drive by lazy {
        val credStream: InputStream = context.assets.open("google-service-account.json")
        val credential = GoogleCredential
            .fromStream(credStream)
            .createScoped(listOf(DriveScopes.DRIVE_FILE))
        Drive.Builder(
            NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
        ).setApplicationName("ContentAutomation").build()
    }

    suspend fun ensureFolder(parentId: String, name: String): String = withContext(Dispatchers.IO) {
        val query = "mimeType='application/vnd.google-apps.folder' and name='${name.replace("'", "\\'")}' and '$parentId' in parents and trashed=false"
        val list = drive.files().list().setQ(query).setFields("files(id,name)").execute()
        list.files?.firstOrNull()?.id ?: run {
            val metadata = File().apply {
                this.name = name
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(parentId)
            }
            drive.files().create(metadata).setFields("id").execute().id
        }
    }

    suspend fun upload(
        localPath: String, folderId: String, mimeType: String, name: String
    ): String = withContext(Dispatchers.IO) {
        val fileMetadata = File().apply {
            this.name = name
            parents = listOf(folderId)
        }
        val media = FileContent(mimeType, java.io.File(localPath))
        // Resumable upload — an toàn khi mạng chập chờn
        drive.files().create(fileMetadata, media)
            .setFields("id,size")
            .execute().id
    }

    suspend fun delete(fileId: String) = withContext(Dispatchers.IO) {
        runCatching { drive.files().delete(fileId).execute() }
        Unit
    }

    suspend fun download(fileId: String, destPath: String) = withContext(Dispatchers.IO) {
        val out = java.io.File(destPath).outputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(out)
        out.close()
    }
}
