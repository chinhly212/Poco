package com.contentautomation.domain.pipeline

import com.contentautomation.data.db.AppDatabase
import com.contentautomation.data.db.entity.*
import kotlinx.coroutines.flow.Flow

class PipelineController(
    private val database: AppDatabase
) {

    private val jobDao = database.pipelineJobDao()
    private val videoDao = database.sourceVideoDao()

    // Theo dõi danh sách công việc thật
    fun observeJobs(): Flow<List<PipelineJob>> {
        return jobDao.observeAll()
    }

    // Đưa một video vào Pipeline
    suspend fun enqueueVideo(videoId: String) {

        val video = videoDao.findById(videoId)
            ?: throw IllegalArgumentException("Không tìm thấy video")

        val job = PipelineJob(
            id = video.id,
            sourceVideoId = video.id,
            stage = PipelineStage.DOWNLOAD,
            status = PipelineStatus.PENDING
        )

        jobDao.insert(job)
    }

    // Đưa tất cả video mới vào Pipeline
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

            jobDao.insert(job)
            added++
        }

        return added
    }

    // Tạm dừng những công việc đang chạy
    suspend fun pause() {
        jobDao.pauseRunning()
    }

    // Tiếp tục các công việc đã tạm dừng
    suspend fun resume() {

        val jobs = jobDao.observeAll()

        // Việc cập nhật sẽ được thực hiện thông qua DAO
        jobDao.resetRunning()
    }

    // Lấy công việc tiếp theo
    suspend fun nextJob(): PipelineJob? {
        return jobDao.next()
    }

    // Cập nhật trạng thái
    suspend fun update(job: PipelineJob) {
        jobDao.update(
            job.copy(
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
