package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentautomation.data.db.entity.PipelineJob
import com.contentautomation.data.db.entity.PipelineStatus
import com.contentautomation.data.db.entity.PipelineStage
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.GText
import com.contentautomation.ui.components.GlassColors
import com.contentautomation.ui.components.ScreenColumn
import com.contentautomation.ui.components.StatusDot

private const val MAX_VISIBLE_JOBS = 100

private val PageBg = Color(0xFFF4F4F5)
private val CardBg = Color(0xFFFFFFFF)
private val BorderColor = Color(0xFFE4E4E7)
private val Ink = Color(0xFF18181B)
private val Muted = Color(0xFF71717A)
private val Blue = Color(0xFF2563EB)
private val Green = Color(0xFF16A34A)
private val Red = Color(0xFFDC2626)
private val Amber = Color(0xFFD97706)

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
    val context = androidx.compose.ui.platform.LocalContext.current
    var filter by remember { mutableStateOf(JobFilter.ALL) }
    val scanProgress by vm.scanProgress.collectAsStateWithLifecycle()

    val counts = remember(jobs) {
        JobFilter.entries.associateWith { f ->
            f.status?.let { status -> jobs.count { it.status == status } } ?: jobs.size
        }
    }
    val shown = remember(jobs, filter) {
        (filter.status?.let { status -> jobs.filter { it.status == status } } ?: jobs)
            .take(MAX_VISIBLE_JOBS)
    }

    ScreenColumn {
        FlatCard {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GText(
                        "Tiến trình quét kênh",
                        14.sp,
                        weight = FontWeight.SemiBold,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    GText(
                        "${scanProgress.progress}%",
                        13.sp,
                        weight = FontWeight.Bold,
                        color = Ink
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFFE4E4E7))
                ) {
                    Box(
                        Modifier.fillMaxWidth((scanProgress.progress / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Ink)
                    )
                }
                GText(scanProgress.message, 11.sp, color = Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlatActionButton(
                        text = "Quét ngay",
                        onClick = onScanNow,
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                    FlatActionButton(
                        text = "Tiếp tục tất cả",
                        onClick = {
                            val resumable = jobs.filter {
                                (it.status == PipelineStatus.PAUSED || it.status == PipelineStatus.FAILED) &&
                                    it.stage in resumableStages
                            }
                            if (resumable.isEmpty()) {
                                android.widget.Toast.makeText(
                                    context,
                                    if (jobs.isEmpty()) "Chưa có công việc để tiếp tục."
                                    else "Không có công việc nào đang tạm dừng hoặc bị lỗi có thể tiếp tục.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                resumable.forEach { onResumeJob(it.id) }
                                android.widget.Toast.makeText(
                                    context,
                                    "Đã đưa ${resumable.size} công việc trở lại hàng đợi.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GText("Lọc công việc", 14.sp, weight = FontWeight.SemiBold, color = Ink)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                JobFilter.entries.chunked(3).forEach { rowFilters ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowFilters.forEach { item ->
                            FilterTile(
                                label = item.label,
                                count = counts[item] ?: 0,
                                selected = filter == item,
                                modifier = Modifier.weight(1f),
                                onClick = { filter = item }
                            )
                        }
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(38.dp)
                )
                GText(
                    if (jobs.isEmpty()) "Chưa có công việc" else "Không tìm thấy công việc",
                    15.sp,
                    weight = FontWeight.SemiBold,
                    color = Ink
                )
                GText(
                    if (jobs.isEmpty()) "Video được đưa vào xử lý sẽ xuất hiện tại đây"
                    else "Hãy chọn một trạng thái khác để xem công việc.",
                    12.sp,
                    color = Muted,
                    align = TextAlign.Center
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GText("Danh sách công việc", 14.sp, weight = FontWeight.SemiBold, color = Ink)
                shown.forEach { job -> JobCard(job, onPauseJob, onResumeJob) }
                if (jobs.size > MAX_VISIBLE_JOBS) {
                    GText("Đang hiển thị tối đa $MAX_VISIBLE_JOBS công việc.", 11.sp, color = Muted)
                }
            }
        }
    }
}

private val resumableStages = listOf(
    PipelineStage.DOWNLOAD,
    PipelineStage.ANALYZE,
    PipelineStage.SPEECH_TO_TEXT,
    PipelineStage.TRANSLATE,
    PipelineStage.TTS,
    PipelineStage.MERGE,
    PipelineStage.EXPORT,
    PipelineStage.VERIFY
)

@Composable
private fun FlatCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(1.dp, BorderColor, shape),
        content = content
    )
}

@Composable
private fun FlatActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.height(40.dp)
            .clip(shape)
            .background(if (primary) Ink else Color.White)
            .border(if (primary) 0.dp else 1.dp, if (primary) Ink else BorderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GText(
            text,
            12.sp,
            weight = FontWeight.SemiBold,
            color = if (primary) Color.White else Ink
        )
    }
}

@Composable
private fun FilterTile(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier.aspectRatio(1.12f)
            .clip(shape)
            .background(Color.White)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) Ink else BorderColor,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GText(count.toString(), 18.sp, weight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(3.dp))
        GText(label, 11.sp, color = Muted, align = TextAlign.Center)
    }
}

@Composable
private fun JobCard(job: PipelineJob, onPauseJob: (String) -> Unit, onResumeJob: (String) -> Unit) {
    val (color, text) = when (job.status) {
        PipelineStatus.RUNNING -> Blue to "Đang chạy"
        PipelineStatus.DONE -> Green to "Hoàn thành"
        PipelineStatus.FAILED -> Red to "Thất bại"
        PipelineStatus.PAUSED -> Amber to "Tạm dừng"
        PipelineStatus.PENDING -> Muted to "Đang chờ"
    }

    FlatCard {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(color, 8.dp)
                GText(stageLabel(job.stage), 13.sp, weight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                GText(text, 11.sp, weight = FontWeight.SemiBold, color = color)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GText(job.sourceVideoId, 11.sp, color = Muted, modifier = Modifier.weight(1f))
                GText("Thử lại: ${job.retries}", 11.sp, color = Muted)
            }
            val stageIndex = resumableStages.indexOf(job.stage).coerceAtLeast(0)
            val stageProgress = if (job.status == PipelineStatus.DONE) 1f else stageIndex.toFloat() / resumableStages.size
            GText(
                "Tiến độ công đoạn: ${if (job.status == PipelineStatus.DONE) resumableStages.size else stageIndex}/${resumableStages.size}",
                10.sp,
                color = Muted
            )
            LinearProgressIndicator(
                progress = stageProgress,
                modifier = Modifier.fillMaxWidth(),
                color = Ink,
                trackColor = BorderColor
            )
            if (!job.error.isNullOrBlank()) {
                GText(job.error!!, 11.sp, color = if (job.status == PipelineStatus.DONE) Green else Red)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (job.status == PipelineStatus.RUNNING || job.status == PipelineStatus.PENDING) {
                    FlatActionButton("Tạm dừng", onClick = { onPauseJob(job.id) })
                } else if ((job.status == PipelineStatus.PAUSED || job.status == PipelineStatus.FAILED) && job.stage in resumableStages) {
                    FlatActionButton("Tiếp tục", onClick = { onResumeJob(job.id) }, primary = true)
                } else if (job.status == PipelineStatus.PAUSED || job.status == PipelineStatus.FAILED) {
                    GText("Cần cập nhật quy trình", 11.sp, color = Amber)
                }
            }
        }
    }
}

private fun stageLabel(stage: PipelineStage): String = when (stage) {
    PipelineStage.DOWNLOAD -> "Tải video nguồn"
    PipelineStage.ANALYZE -> "Tối ưu video"
    PipelineStage.SPEECH_TO_TEXT -> "Nhận diện lời nói tiếng Trung"
    PipelineStage.TRANSLATE -> "Dịch tiêu đề và mô tả nguồn"
    PipelineStage.TTS -> "Tạo giọng đọc từ bản dịch"
    PipelineStage.SUBTITLE -> "Phụ đề (chưa bật)"
    PipelineStage.MERGE -> "Ghép giọng Việt vào video"
    PipelineStage.EXPORT -> "Lưu vào điện thoại"
    PipelineStage.UPLOAD -> "Sao lưu (chưa bật)"
    PipelineStage.VERIFY -> "Kiểm tra tệp"
}
