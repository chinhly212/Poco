package com.contentautomation.work

import android.content.Context
import android.util.Log
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.contentautomation.data.db.entity.PipelineJob
import com.contentautomation.data.db.entity.PipelineStage
import com.contentautomation.data.db.entity.PipelineStatus
import com.contentautomation.data.db.entity.SourceVideo
import com.contentautomation.data.db.entity.VideoStatus
import com.contentautomation.domain.source.SourceAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanWorker(
    ctx: Context,
    params: WorkerParameters
) : BaseWorker(ctx, params) {

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {

            if (!app.awaitMediaEngines()) {
                return@withContext Result.retry()
            }

            val accounts = db.sourceAccountDao().getEnabled()

            var scanned = 0
            var discovered = 0
            var queued = 0
            var failed = 0

            for (account in accounts) {
                try {
                    val adapter =
                        SourceAdapterFactory.create(account.platform)

                    val items =
                        val sessionCookie = prefs.getSecret("platform_cookie_${account.platform.lowercase()}")
                        if (sessionCookie.isNullOrBlank()) {
                            throw IllegalStateException("Chưa đăng nhập ${account.platform}. Hãy đăng nhập nền tảng trước khi quét.")
                        }
                        adapter.getLatestVideos(
                            account.accountIdentifier,
                            sessionCookie
                        )

                    scanned++

                    for (item in items) {
                        if (db.sourceVideoDao().exists(
                                account.id,
                                item.id
                            ) != 0
                        ) {
                            continue
                        }

                        val video = SourceVideo(
                            sourceAccountId = account.id,
                            externalVideoId = item.id,
                            sourceUrl = item.url,
                            title = item.title,
                            descriptionZh = item.description,
                            publishedAt = item.publishedAt,
                            status = VideoStatus.NEW.name
                        )

                        val insertedId =
                            db.sourceVideoDao().insert(video)

                        if (insertedId == -1L) {
                            continue
                        }

                        discovered++

                        val pipelineJob = PipelineJob(
                            id = video.id,
                            sourceVideoId = video.id,
                            stage = PipelineStage.DOWNLOAD,
                            status = PipelineStatus.PENDING
                        )

                        val jobInserted =
                            db.pipelineJobDao().insert(pipelineJob)

                        if (jobInserted == -1L) {
                            Log.w(
                                "MuChiScan",
                                "Pipeline job đã tồn tại: ${video.id}"
                            )
                            continue
                        }

                        PipelineWorker.enqueue(
                            applicationContext,
                            pipelineJob.id
                        )

                        queued++
                    }

                    db.sourceAccountDao().updateLastScan(
                        account.id,
                        System.currentTimeMillis()
                    )

                } catch (e: CancellationException) {
                    throw e

                } catch (e: Exception) {
                    failed++

                    val reason = e.message ?: "Không xác định được nguyên nhân."
                    Log.e(
                        "MuChiScan",
                        "Lỗi quét tài khoản ${account.accountName}: $reason",
                        e
                    )
                    runCatching {
                        val bucket = System.currentTimeMillis() / 900_000L
                        db.notificationDao().insert(
                            com.contentautomation.data.db.entity.StoredNotification(
                                id = "scan_${account.id}_$bucket",
                                kind = "VideoUnavailable",
                                title = "Quét thất bại · ${account.accountName}",
                                message = reason,
                                time = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date()),
                                actionLabel = "Kiểm tra đăng nhập",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }.onFailure { Log.e("MuChiScan", "Không lưu được thông báo lỗi quét", it) }
                }
            }

            if (failed > 0) {
                Log.w(
                    "MuChiScan",
                    "Có $failed tài khoản quét thất bại. " +
                        "Yêu cầu WorkManager thử lại."
                )

                Result.retry()

            } else {
                Result.success(
                    workDataOf(
                        "scanned_accounts" to scanned,
                        "discovered_videos" to discovered,
                        "queued_jobs" to queued,
                        "failed_accounts" to failed
                    )
                )
            }
        }
}
