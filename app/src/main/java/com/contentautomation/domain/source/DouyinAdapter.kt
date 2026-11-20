
package com.contentautomation.domain.pipeline

import com.contentautomation.data.db.AppDatabase
import com.contentautomation.data.db.entity.*
import kotlinx.coroutines.flow.Flow

class PipelineController(
    private val database: AppDatabase
) {

    private val jobDao = database.pipelineJobDao()
    private val videoDao = database.sourceVideoDao()

    fun observeJobs(): Flow<List<PipelineJob>> {
        return jobDao.observeAll()
    }

    suspend fun enqueueVideo(videoId: String) {
        val video = videoDao.findById(videoId)
            ?: throw IllegalArgumentException("Không tìm thấy video")

        val job = PipelineJob(
            id = video.id,
            sourceVideoId = video.id,
            stage = PipelineStage.DOWNLOAD,
            status = PipelineStatus.PENDING
        )

        val inserted = jobDao.insert(job)

        if (inserted == -1L) {
            throw IllegalStateException("Video đã có trong Pipeline")
        }
    }

    suspend fun enqueueNewVideos(): Int {
        val videos = videoDao.findByStatuses(
            listOf(VideoStatus.NEW.name)
        )

        var added = 0

        for (video in videos) {
            val job = PipelineJob(
                id = video.id,
                sourceVideoId = video.id,
                stage = PipelineStage.DOWNLOAD,
                status = PipelineStatus.PENDING
            )

            if (jobDao.insert(job) != -1L) {
                added++
            }
        }

        return added
    }

    suspend fun pause() {
        jobDao.pauseRunning()
    }

    suspend fun resume() {
        jobDao.resetRunning()
        jobDao.resumePaused()
    }

    suspend fun nextJob(): PipelineJob? {
        return jobDao.next()
    }

    suspend fun update(job: PipelineJob) {
        jobDao.update(
            job.copy(
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
