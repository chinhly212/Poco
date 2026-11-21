
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
    const val OPTIMIZE_VIDEO = 2
    const val SAVE_TO_PHONE = 3
    const val TOTAL_STEPS = 4
    const val OUTPUT_DIRECTORY = "Movies/NiChi"

    val all = listOf(
        StepDef("Lấy video nguồn", "Đang lấy video nguồn", "Đã lấy video nguồn"),
        StepDef("Tải video", "Đang tải video", "Đã tải video"),
        StepDef("Tối ưu video", "Đang tối ưu video", "Đã tối ưu video"),
        StepDef("Lưu vào điện thoại", "Đang lưu vào Movies/NiChi", "Đã lưu video vào điện thoại")
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
