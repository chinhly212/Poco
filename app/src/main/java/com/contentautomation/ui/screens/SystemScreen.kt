package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.data.db.entity.PipelineJob
import com.contentautomation.data.db.entity.PipelineStage
import com.contentautomation.data.db.entity.PipelineStatus
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.*

@Composable
fun SystemScreen(
    vm: MainViewModel,
    jobs: List<PipelineJob>
) {
    ScreenColumn(
        title = "Hệ thống",
        subtitle = "Tiến trình xử lý video thực tế"
    ) {
        if (jobs.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusDot(GlassColors.InkFaint)

                    Spacer(Modifier.height(12.dp))

                    GText(
                        "Chưa có công việc Pipeline",
                        15.sp,
                        color = GlassColors.InkSoft
                    )

                    Spacer(Modifier.height(6.dp))

                    GText(
                        "Video mới được phát hiện sẽ xuất hiện tại đây.",
                        12.sp,
                        color = GlassColors.InkSoft
                    )
                }
            }
        } else {
            GText(
                "Danh sách công việc (${jobs.size})",
                16.sp,
                weight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            jobs.forEach { job ->
                PipelineJobCard(job)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PipelineJobCard(job: PipelineJob) {

    val statusColor = when (job.status) {
        PipelineStatus.RUNNING -> GlassColors.Blue
        PipelineStatus.DONE -> GlassColors.Green
        PipelineStatus.FAILED -> GlassColors.Red
        PipelineStatus.PAUSED -> GlassColors.Amber
        PipelineStatus.PENDING -> GlassColors.InkFaint
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(statusColor)

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    GText(
                        stageName(job.stage),
                        15.sp,
                        weight = FontWeight.SemiBold
                    )

                    GText(
                        statusName(job.status),
                        12.sp,
                        color = statusColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            GText(
                "Video ID: ${job.sourceVideoId}",
                12.sp,
                color = GlassColors.InkSoft
            )

            GText(
                "Số lần thử: ${job.retries}",
                12.sp,
                color = GlassColors.InkSoft
            )

            if (!job.error.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))

                GText(
                    "Lỗi: ${job.error}",
                    12.sp,
                    color = GlassColors.Red
                )
            }
        }
    }
}

private fun stageName(stage: PipelineStage): String {
    return when (stage) {
        PipelineStage.DOWNLOAD -> "Tải video"
        PipelineStage.ANALYZE -> "Phân tích video"
        PipelineStage.SPEECH_TO_TEXT -> "Nhận dạng giọng nói"
        PipelineStage.TRANSLATE -> "Dịch nội dung"
        PipelineStage.TTS -> "Tạo giọng đọc"
        PipelineStage.SUBTITLE -> "Tạo phụ đề"
        PipelineStage.MERGE -> "Ghép video và âm thanh"
        PipelineStage.EXPORT -> "Xuất video"
        PipelineStage.UPLOAD -> "Tải lên"
        PipelineStage.VERIFY -> "Kiểm tra kết quả"
    }
}

private fun statusName(status: PipelineStatus): String {
    return when (status) {
        PipelineStatus.PENDING -> "Đang chờ"
        PipelineStatus.RUNNING -> "Đang chạy"
        PipelineStatus.DONE -> "Hoàn thành"
        PipelineStatus.FAILED -> "Thất bại"
        PipelineStatus.PAUSED -> "Tạm dừng"
    }
}
