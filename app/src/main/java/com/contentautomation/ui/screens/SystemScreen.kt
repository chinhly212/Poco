package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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

private const val MAX_VISIBLE_JOBS = 100

private enum class JobFilter(val label: String) {
    ALL("Tất cả"),
    RUNNING("Đang chạy"),
    PENDING("Đang chờ"),
    DONE("Hoàn thành"),
    FAILED("Thất bại"),
    PAUSED("Tạm dừng")
}

@Composable
fun SystemScreen(
    vm: MainViewModel,
    jobs: List<PipelineJob>
) {
    var selectedFilter by remember { mutableStateOf(JobFilter.ALL) }

    val runningCount = jobs.count { it.status == PipelineStatus.RUNNING }
    val pendingCount = jobs.count { it.status == PipelineStatus.PENDING }
    val doneCount = jobs.count { it.status == PipelineStatus.DONE }
    val failedCount = jobs.count { it.status == PipelineStatus.FAILED }

    val filteredJobs = remember(jobs, selectedFilter) {
        val filtered = when (selectedFilter) {
            JobFilter.ALL -> jobs
            JobFilter.RUNNING -> jobs.filter { it.status == PipelineStatus.RUNNING }
            JobFilter.PENDING -> jobs.filter { it.status == PipelineStatus.PENDING }
            JobFilter.DONE -> jobs.filter { it.status == PipelineStatus.DONE }
            JobFilter.FAILED -> jobs.filter { it.status == PipelineStatus.FAILED }
            JobFilter.PAUSED -> jobs.filter { it.status == PipelineStatus.PAUSED }
        }
        filtered.take(MAX_VISIBLE_JOBS)
    }

    ScreenColumn(
        title = "Công việc",
        subtitle = "Theo dõi tiến trình xử lý video"
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GText(
                    "TỔNG QUAN PIPELINE",
                    11.sp,
                    color = GlassColors.InkSoft,
                    weight = FontWeight.Bold
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryItem("Tổng việc", jobs.size.toString(), GlassColors.Blue)
                    SummaryItem("Đang chạy", runningCount.toString(), GlassColors.Blue)
                    SummaryItem("Hoàn thành", doneCount.toString(), GlassColors.Green)
                    SummaryItem("Thất bại", failedCount.toString(), GlassColors.Red)
                }

                Spacer(Modifier.height(14.dp))

                GText(
                    "Đang chờ: $pendingCount công việc",
                    12.sp,
                    color = GlassColors.InkSoft
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        GText(
            "LỌC CÔNG VIỆC",
            12.sp,
            color = GlassColors.InkSoft,
            weight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            JobFilter.entries.take(3).forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { GText(filter.label, 11.sp) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            JobFilter.entries.drop(3).forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { GText(filter.label, 11.sp) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GText(
                "Danh sách công việc",
                16.sp,
                weight = FontWeight.SemiBold
            )

            GText(
                "${filteredJobs.size} mục",
                12.sp,
                color = GlassColors.InkSoft
            )
        }

        Spacer(Modifier.height(10.dp))

        if (filteredJobs.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusDot(GlassColors.InkFaint)

                    Spacer(Modifier.height(12.dp))

                    GText(
                        if (jobs.isEmpty()) {
                            "Chưa có công việc nào"
                        } else {
                            "Không có công việc phù hợp"
                        },
                        15.sp,
                        weight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(6.dp))

                    GText(
                        if (jobs.isEmpty()) {
                            "Công việc sẽ xuất hiện tại đây khi được tạo."
                        } else {
                            "Hãy chọn bộ lọc khác để xem công việc."
                        },
                        12.sp,
                        color = GlassColors.InkSoft
                    )
                }
            }
        } else {
            filteredJobs.forEach { job ->
                PipelineJobCard(job)
                Spacer(Modifier.height(9.dp))
            }

            if (jobs.size > MAX_VISIBLE_JOBS) {
                GText(
                    "Danh sách được giới hạn ở 100 công việc.",
                    11.sp,
                    color = GlassColors.InkSoft
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column {
        GText(
            value,
            21.sp,
            color = color,
            weight = FontWeight.Bold
        )
        GText(
            label,
            10.sp,
            color = GlassColors.InkSoft
        )
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
        Column(Modifier.padding(15.dp)) {
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

                    Spacer(Modifier.height(3.dp))

                    GText(
                        statusName(job.status),
                        12.sp,
                        color = statusColor,
                        weight = FontWeight.Medium
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small
                ) {
                    GText(
                        statusName(job.status),
                        10.sp,
                        color = statusColor,
                        weight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(13.dp))

            Surface(
                color = GlassColors.InkFaint.copy(alpha = 0.07f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(11.dp)
                ) {
                    GText(
                        "VIDEO ID",
                        10.sp,
                        color = GlassColors.InkSoft,
                        weight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    GText(
                        job.sourceVideoId,
                        12.sp,
                        color = GlassColors.Ink
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GText(
                    "Số lần thử",
                    11.sp,
                    color = GlassColors.InkSoft
                )

                GText(
                    job.retries.toString(),
                    11.sp,
                    weight = FontWeight.SemiBold
                )
            }

            if (!job.error.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))

                Surface(
                    color = GlassColors.Red.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(10.dp)) {
                        GText(
                            "CHI TIẾT LỖI",
                            10.sp,
                            color = GlassColors.Red,
                            weight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(4.dp))

                        GText(
                            job.error,
                            12.sp,
                            color = GlassColors.Red
                        )
                    }
                }
            }
        }
    }
}

private fun stageName(stage: PipelineStage): String =
    when (stage) {
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

private fun statusName(status: PipelineStatus): String =
    when (status) {
        PipelineStatus.PENDING -> "Đang chờ"
        PipelineStatus.RUNNING -> "Đang chạy"
        PipelineStatus.DONE -> "Hoàn thành"
        PipelineStatus.FAILED -> "Thất bại"
        PipelineStatus.PAUSED -> "Tạm dừng"
    }
    
