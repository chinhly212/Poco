package com.contentautomation.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.data.db.entity.PipelineJob
import com.contentautomation.data.db.entity.PipelineStatus
import com.contentautomation.data.db.entity.PipelineStage
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.*

private const val MAX_VISIBLE_JOBS = 100

private enum class JobFilter(val label: String, val status: PipelineStatus?) {
    ALL("Tất cả", null),
    RUNNING("Đang chạy", PipelineStatus.RUNNING),
    PENDING("Đang chờ", PipelineStatus.PENDING),
    DONE("Hoàn thành", PipelineStatus.DONE),
    FAILED("Thất bại", PipelineStatus.FAILED),
    PAUSED("Tạm dừng", PipelineStatus.PAUSED)
}

@Composable
fun SystemScreen(
    vm: MainViewModel,
    jobs: List<PipelineJob>,
    onScanNow: () -> Unit,
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit
) {
    var filter by remember { mutableStateOf(JobFilter.ALL) }

    val shown = remember(jobs, filter) {
        (filter.status?.let { s -> jobs.filter { it.status == s } } ?: jobs).take(MAX_VISIBLE_JOBS)
    }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Quét ngay", onClick = onScanNow, primary = true, modifier = Modifier.weight(1f))
            GlassButton("Tiếp tục tất cả", onClick = {
                jobs.filter { (it.status == PipelineStatus.PAUSED || it.status == PipelineStatus.FAILED) && it.stage in listOf(PipelineStage.DOWNLOAD, PipelineStage.ANALYZE, PipelineStage.SPEECH_TO_TEXT) }
                    .forEach { onResumeJob(it.id) }
            }, modifier = Modifier.weight(1f))
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GText("Tổng số công việc", 12.sp, color = GlassColors.InkSoft)
                GText(jobs.size.toString(), 30.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    Count("Đang chạy", jobs, PipelineStatus.RUNNING, GlassColors.Blue)
                    Count("Đang chờ", jobs, PipelineStatus.PENDING, GlassColors.InkSoft)
                    Count("Xong", jobs, PipelineStatus.DONE, GlassColors.Green)
                    Count("Lỗi", jobs, PipelineStatus.FAILED, GlassColors.Red)
                    Count("Dừng", jobs, PipelineStatus.PAUSED, GlassColors.Amber)
                }
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JobFilter.entries.forEach { f ->
                GlassChip(f.label, selected = filter == f, onClick = { filter = f })
            }
        }

        if (shown.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(25.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GText(
                        if (jobs.isEmpty()) "Chưa có công việc" else "Không tìm thấy công việc",
                        15.sp, weight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(5.dp))
                    GText(
                        if (jobs.isEmpty()) "Video được đưa vào xử lý sẽ xuất hiện tại đây."
                        else "Hãy chọn một bộ lọc khác.",
                        12.sp, color = GlassColors.InkSoft, align = TextAlign.Center
                    )
                }
            }
        } else {
            shown.forEach { job -> JobCard(job, onPauseJob, onResumeJob) }
            if (jobs.size > MAX_VISIBLE_JOBS) {
                GText("Đang hiển thị tối đa $MAX_VISIBLE_JOBS công việc.", 11.sp, color = GlassColors.InkSoft)
            }
        }
    }
}

@Composable
private fun RowScope.Count(label: String, jobs: List<PipelineJob>, status: PipelineStatus, color: Color) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        GText(jobs.count { it.status == status }.toString(), 20.sp, weight = FontWeight.Bold, color = color)
        GText(label, 10.sp, color = GlassColors.InkSoft)
    }
}

@Composable
private fun JobCard(job: PipelineJob, onPauseJob: (String) -> Unit, onResumeJob: (String) -> Unit) {
    val (color, text) = when (job.status) {
        PipelineStatus.RUNNING -> GlassColors.Blue to "Đang chạy"
        PipelineStatus.DONE -> GlassColors.Green to "Hoàn thành"
        PipelineStatus.FAILED -> GlassColors.Red to "Thất bại"
        PipelineStatus.PAUSED -> GlassColors.Amber to "Tạm dừng"
        PipelineStatus.PENDING -> GlassColors.InkSoft to "Đang chờ"
    }

    GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatusDot(color)
                GText(stageLabel(job.stage), 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                GText(text, 11.sp, weight = FontWeight.Bold, color = color)
            }
            GlassDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GText(job.sourceVideoId, 12.sp, color = GlassColors.InkSoft, modifier = Modifier.weight(1f))
                GText("Thử lại: ${job.retries}", 12.sp, color = GlassColors.InkSoft)
            }
            if (!job.error.isNullOrBlank()) {
                GText(job.error!!, 12.sp, color = if (job.status == PipelineStatus.DONE) GlassColors.Green else GlassColors.Red)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (job.status == PipelineStatus.RUNNING || job.status == PipelineStatus.PENDING) {
                    GlassButton("Tạm dừng", onClick = { onPauseJob(job.id) })
                } else if ((job.status == PipelineStatus.PAUSED || job.status == PipelineStatus.FAILED) && job.stage in listOf(PipelineStage.DOWNLOAD, PipelineStage.ANALYZE, PipelineStage.SPEECH_TO_TEXT)) {
                    GlassButton("Tiếp tục", onClick = { onResumeJob(job.id) }, primary = true)
                } else if (job.status == PipelineStatus.PAUSED || job.status == PipelineStatus.FAILED) {
                    GText("Cần cập nhật quy trình", 11.sp, color = GlassColors.Amber)
                }
            }
        }
    }
}

private fun stageLabel(stage: PipelineStage): String = when (stage) {
    PipelineStage.DOWNLOAD -> "Tải video nguồn"
    PipelineStage.ANALYZE -> "Tối ưu video"
    PipelineStage.SPEECH_TO_TEXT -> "Nhận diện lời nói"
    PipelineStage.TRANSLATE -> "Dịch nội dung"
    PipelineStage.TTS -> "Tạo giọng đọc"
    PipelineStage.SUBTITLE -> "Tạo phụ đề"
    PipelineStage.MERGE -> "Ghép nội dung"
    PipelineStage.EXPORT -> "Lưu vào điện thoại"
    PipelineStage.UPLOAD -> "Sao lưu"
    PipelineStage.VERIFY -> "Kiểm tra tệp"
}
