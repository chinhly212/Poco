package com.contentautomation.ui.models

const val RETRY_DELAY_SECONDS = 15
const val MAX_RETRIES = 5

enum class ErrorKind { VideoUnavailable, StorageAlmostFull, NetworkLost }

data class AppError(
    val id: String, // Đã đổi thành String để khớp với ID Database
    val kind: ErrorKind,
    val title: String,
    val message: String,
    val time: String,
    val actionLabel: String,
)

data class StepDef(val pending: String, val running: String, val done: String)

object PipelineSteps {
    val all = listOf(
        StepDef("Phát hiện video mới từ kênh nguồn", "Phát hiện video mới từ kênh nguồn", "Phát hiện video mới từ kênh nguồn"),
        StepDef("Copy link video", "Đang copy link video", "Đã copy link video"),
        StepDef("Tải video không logo", "Đang chuyển sang tải video không logo", "Tải video không logo thành công"),
        StepDef("Chuyển hướng sang TikTok", "Đang chuyển hướng sang TikTok", "Đã chuyển hướng sang TikTok"),
        StepDef("Tìm nhạc phù hợp theo chủ đề", "Đang tìm kiếm nhạc phù hợp theo chủ đề", "Đã tìm thấy nhạc phù hợp"),
        StepDef("Ghép nhạc vào video", "Đang ghép nhạc vào video", "Ghép nhạc vào video thành công"),
        StepDef("Tạo mô tả video & viết #", "Đang tạo mô tả video & viết #", "Đã tạo mô tả & hashtag"),
        StepDef("Kiểm tra toàn bộ video", "Đang kiểm tra toàn bộ video", "Không có lỗi"),
        StepDef("Đăng lên TikTok", "Đang đăng lên TikTok", "Đăng thành công!"),
        StepDef("Chuyển hướng đến Google Drive", "Đang chuyển hướng đến Google Drive", "Đã chuyển hướng đến Google Drive"),
        StepDef("Sao lưu video lên Google Drive", "Đang sao lưu video", "Đã sao lưu video"),
        StepDef("Sao lưu thành công", "Đang xác nhận sao lưu", "Sao lưu thành công"),
        StepDef("Hoàn tất", "Đang hoàn tất", "Hoàn tất"),
    )
}

sealed interface PipelineState {
    data object Idle : PipelineState
    data class Running(val stepIndex: Int, val retry: Int = 0) : PipelineState
}

data class StorageInfo(val usedGb: Float, val totalGb: Float) {
    val freeGb: Float get() = totalGb - usedGb
    val fraction: Float get() = if (totalGb > 0) (usedGb / totalGb).coerceIn(0f, 1f) else 0f
}
