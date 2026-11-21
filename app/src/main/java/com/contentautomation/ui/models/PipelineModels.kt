
package com.contentautomation.ui.models

const val RETRY_DELAY_SECONDS = 15
const val MAX_RETRIES = 5

enum class ErrorKind {
    VideoUnavailable,
    StorageAlmostFull,
    NetworkLost
}

data class AppError(
    val id: String,
    val kind: ErrorKind,
    val title: String,
    val message: String,
    val time: String,
    val actionLabel: String
)

data class StepDef(
    val pending: String,
    val running: String,
    val done: String
)

/**
 * Quy trình xử lý video của Mu Chi.
 *
 * 1. Lấy video nguồn.
 * 2. Tải video không logo, không âm thanh.
 * 3. Xử lý video theo cấu hình.
 * 4. Tạo phụ đề khi đã cấu hình dịch vụ nhận diện.
 * 5. Lưu video hoàn tất trực tiếp vào điện thoại.
 */
object PipelineSteps {

    const val GET_VIDEO = 0
    const val DOWNLOAD_VIDEO = 1
    const val EXTRACT_AUDIO = 2
    const val PROCESS_VIDEO = 3
    const val GEMINI_LYRICS = 4
    const val MERGE_LYRICS = 5
    const val SAVE_TO_PHONE = 6

    const val TOTAL_STEPS = 7

    const val OUTPUT_DIRECTORY = "Movies/Mu Chi"

    val all = listOf(

        StepDef(
            pending = "Lấy video nguồn",
            running = "Đang lấy video nguồn",
            done = "Đã lấy video nguồn"
        ),

        StepDef(
            pending = "Tải video không logo, không âm thanh",
            running = "Đang tải video không logo, không âm thanh",
            done = "Đã tải video không logo, không âm thanh"
        ),

        StepDef(
            pending = "Tách âm thanh video gốc",
            running = "Đang tách âm thanh video gốc",
            done = "Đã tách âm thanh video gốc"
        ),

        StepDef(
            pending = "Xử lý video",
            running = "Đang xử lý video",
            done = "Đã xử lý video"
        ),

        StepDef(
            pending = "Gửi MP3 sang Gemini nhận diện lời bài hát",
            running = "Đang gửi MP3 sang Gemini API",
            done = "Gemini đã nhận diện lời bài hát"
        ),

        StepDef(
            pending = "Căn chỉnh và ghép lời bài hát vào video",
            running = "Đang căn chỉnh và ghép lời bài hát",
            done = "Đã ghép lời bài hát vào video"
        ),

        StepDef(
            pending = "Lưu video hoàn tất vào điện thoại",
            running = "Đang lưu video vào Movies/Mu Chi",
            done = "Đã lưu video vào điện thoại"
        )
    )
}

sealed interface PipelineState {

    data object Idle : PipelineState

    data class Running(
        val stepIndex: Int,
        val retry: Int = 0
    ) : PipelineState
}

data class StorageInfo(
    val usedGb: Float,
    val totalGb: Float
) {
    val freeGb: Float
        get() = (totalGb - usedGb).coerceAtLeast(0f)

    val fraction: Float
        get() = if (totalGb > 0f) {
            (usedGb / totalGb).coerceIn(0f, 1f)
        } else {
            0f
        }
}
