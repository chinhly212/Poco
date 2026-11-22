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
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

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
            var skippedNotToday = 0
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            if (accounts.isEmpty()) {
                setProgress(workDataOf("phase" to "completed", "progress" to 100, "message" to "Chưa có kênh nào được bật."))
                return@withContext Result.success(workDataOf("scanned_accounts" to 0, "discovered_videos" to 0, "queued_jobs" to 0, "failed_accounts" to 0))
            }

            setProgress(workDataOf("phase" to "scanning", "progress" to 0, "message" to "Bắt đầu kiểm tra video đăng hôm nay: $today"))

            for ((accountIndex, account) in accounts.withIndex()) {
                setProgress(workDataOf(
                    "phase" to "scanning",
                    "progress" to ((accountIndex * 100) / accounts.size),
                    "message" to "Đang kiểm tra kênh ${accountIndex + 1}/${accounts.size}: ${account.accountName}",
                    "current_account" to account.accountName,
                    "scanned_accounts" to scanned,
                    "discovered_videos" to discovered,
                    "queued_jobs" to queued
                ))
                try {
                    val adapter =
                        SourceAdapterFactory.create(account.platform)

                    val sessionCookie = prefs.getSecret("platform_cookie_${account.platform.lowercase()}")
                    if (sessionCookie.isNullOrBlank()) {
                        throw IllegalStateException("Chưa đăng nhập ${account.platform}. Hãy đăng nhập nền tảng trước khi quét.")
                    }
                    val items = adapter.getLatestVideos(
                        account.accountIdentifier,
                        sessionCookie
                    )

                    scanned++

                    for (item in items) {
                        val publishedAt = item.publishedAt
                        if (publishedAt == null || publishedAt < dayStart || publishedAt >= dayEnd) {
                            skippedNotToday++
                            continue
                        }

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
                                kind = "NetworkLost",
                                title = "Quét thất bại · ${account.accountName}",
                                message = reason,
                                time = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date()),
                                actionLabel = "Kiểm tra đăng nhập",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }.onFailure { Log.e("MuChiScan", "Không lưu được thông báo lỗi quét", it) }
                }

                setProgress(workDataOf(
                    "phase" to "scanning",
                    "progress" to (((accountIndex + 1) * 100) / accounts.size),
                    "message" to "Đã kiểm tra ${accountIndex + 1}/${accounts.size} kênh · ${discovered} video mới hôm nay",
                    "current_account" to account.accountName,
                    "scanned_accounts" to scanned,
                    "discovered_videos" to discovered,
                    "queued_jobs" to queued,
                    "failed_accounts" to failed
                ))
            }

            val summary = "Đã kiểm tra $scanned kênh; tìm thấy $discovered video đăng hôm nay; đã tạo $queued công việc; $failed kênh lỗi."
            setProgress(workDataOf(
                "phase" to "completed",
                "progress" to 100,
                "message" to summary,
                "scanned_accounts" to scanned,
                "discovered_videos" to discovered,
                "queued_jobs" to queued,
                "failed_accounts" to failed,
                "skipped_not_today" to skippedNotToday
            ))
            Result.success(
                workDataOf(
                    "scanned_accounts" to scanned,
                    "discovered_videos" to discovered,
                    "queued_jobs" to queued,
                    "failed_accounts" to failed,
                    "skipped_not_today" to skippedNotToday
                )
            )
        }
}
