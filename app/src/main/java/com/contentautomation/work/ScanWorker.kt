
package com.contentautomation.work

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.contentautomation.data.db.entity.SourceVideo
import com.contentautomation.data.db.entity.VideoStatus
import com.contentautomation.data.db.entity.PipelineJob
import com.contentautomation.data.db.entity.PipelineStage
import com.contentautomation.data.db.entity.PipelineStatus
import com.contentautomation.domain.source.SourceAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanWorker(
    ctx: Context,
    params: WorkerParameters
) : BaseWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

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
                    adapter.getLatestVideos(account.accountIdentifier)

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

                    // Chỉ tiếp tục nếu video thực sự được lưu.
                    val insertedId = db.sourceVideoDao().insert(video)

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

                    db.pipelineJobDao().insert(pipelineJob)

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

            } catch (e: Exception) {
                failed++

                android.util.Log.e(
                    "MuChiScan",
                    "Lỗi quét ${account.accountName}",
                    e
                )
            }
        }

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
