package com.contentautomation.work

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.contentautomation.data.remote.GoogleDriveClient
import java.util.concurrent.TimeUnit

/** Uploads a video already saved in MediaStore; WorkManager persists and retries it. */
class DriveBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val drive = GoogleDriveClient(applicationContext)
        if (!drive.isAutoBackupEnabled()) return Result.success()

        val uriText = inputData.getString(KEY_URI) ?: return Result.failure()
        val displayName = inputData.getString(KEY_NAME) ?: return Result.failure()
        val backupKey = inputData.getString(KEY_BACKUP_KEY) ?: return Result.failure()
        val uri = Uri.parse(uriText)

        return try {
            setProgress(workDataOf("phase" to "uploading", "name" to displayName))
            val uploaded = drive.upload(uri, displayName, "video/mp4", backupKey)
            Result.success(
                workDataOf(
                    "drive_file_id" to uploaded.id,
                    "drive_file_name" to uploaded.name,
                    "backup_key" to backupKey
                )
            )
        } catch (error: Exception) {
            setProgress(
                workDataOf(
                    "phase" to "waiting_retry",
                    "name" to displayName,
                    "error" to (error.message ?: "Không thể sao lưu video lên Google Drive.")
                )
            )
            Result.retry()
        }
    }

    companion object {
        private const val KEY_URI = "media_uri"
        private const val KEY_NAME = "display_name"
        private const val KEY_BACKUP_KEY = "backup_key"

        fun enqueue(context: Context, uri: Uri, displayName: String, backupKey: String) {
            val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setInputData(
                    workDataOf(
                        KEY_URI to uri.toString(),
                        KEY_NAME to displayName,
                        KEY_BACKUP_KEY to backupKey
                    )
                )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "drive-backup-$backupKey",
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        const val TAG = "muchi-drive-backup"
    }
}
