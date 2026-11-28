package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.data.db.entity.PipelineJob
import com.contentautomation.data.db.entity.PipelineStage
import com.contentautomation.data.db.entity.PipelineStatus
import com.contentautomation.ui.components.GText
import com.contentautomation.ui.components.ScreenColumn
import com.contentautomation.ui.models.AppError
import com.contentautomation.ui.models.ErrorKind
import com.contentautomation.ui.MainViewModel

private val PageBg = Color(0xFFF4F4F5)
private val CardBg = Color(0xFFFFFFFF)
private val Line = Color(0xFFE4E4E7)
private val Ink = Color(0xFF18181B)
private val Muted = Color(0xFF71717A)
private val ErrorRed = Color(0xFFDC2626)
private val SuccessGreen = Color(0xFF16A34A)
private val Blue = Color(0xFF2563EB)

@Composable
fun HomeScreen(
    systemOn: Boolean,
    errors: List<AppError>,
    jobs: List<PipelineJob>,
    vm: MainViewModel,
    onScanNow: () -> Unit,
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onToggleSystem: () -> Unit,
    onErrorAction: (AppError) -> Unit,
    onDeleteError: (String) -> Unit,
    onClearErrors: () -> Unit
) {
    ScreenColumn {
        SystemCard(systemOn, onToggleSystem)
        SystemScreen(vm = vm, jobs = jobs, onScanNow = onScanNow, onPauseJob = onPauseJob, onResumeJob = onResumeJob)
        if (errors.isNotEmpty()) {
            ErrorSection(errors, onErrorAction, onDeleteError, onClearErrors)
        }
    }
}

@Composable
private fun SystemCard(systemOn: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(1.dp, Line, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (systemOn) SuccessGreen else Color(0xFFA1A1AA))
            )
            Column {
                GText(
                    if (systemOn) "Hệ thống đang hoạt động" else "Hệ thống đang tắt",
                    14.sp,
                    weight = FontWeight.SemiBold,
                    color = Ink
                )
                GText("TRẠNG THÁI HỆ THỐNG", 10.sp, color = Muted)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF18181B))
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            GText(
                if (systemOn) "Tắt hệ thống" else "Bật hệ thống",
                12.sp,
                weight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun PipelineCard(jobs: List<PipelineJob>, onOpenWork: () -> Unit) {
    val running = jobs.count { it.status == PipelineStatus.RUNNING }
    val pending = jobs.count { it.status == PipelineStatus.PENDING || it.status == PipelineStatus.PAUSED }
    val done = jobs.count { it.status == PipelineStatus.DONE }
    val failed = jobs.count { it.status == PipelineStatus.FAILED }
    val activeIndex = when {
        running > 0 -> 1
        pending > 0 -> 2
        done > 0 -> 3
        failed > 0 -> 4
        else -> 0
    }
    val progress by animateFloatAsState(
        targetValue = activeIndex / 4f,
        animationSpec = tween(durationMillis = 450),
        label = "home_status_progress"
    )
    val shape = RoundedCornerShape(10.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(1.dp, Line, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GText(
                "Tiến trình công việc",
                14.sp,
                weight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            GText(
                "Mở công việc",
                12.sp,
                weight = FontWeight.SemiBold,
                color = Blue,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenWork)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Box(
            Modifier.fillMaxWidth().height(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0xFFE4E4E7))
        ) {
            Box(
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        when (activeIndex) {
                            1 -> Blue
                            2 -> Muted
                            3 -> SuccessGreen
                            4 -> ErrorRed
                            else -> Color(0xFFE4E4E7)
                        }
                    )
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            HomeStatusItem("Tổng", jobs.size, Ink, activeIndex == 0 || jobs.isNotEmpty(), Modifier.weight(1f))
            HomeStatusItem("Đang chạy", running, Blue, activeIndex >= 1 && running > 0, Modifier.weight(1f))
            HomeStatusItem("Chờ", pending, Muted, activeIndex >= 2 && pending > 0, Modifier.weight(1f))
            HomeStatusItem("Đã lưu", done, SuccessGreen, activeIndex >= 3 && done > 0, Modifier.weight(1f))
            HomeStatusItem("Lỗi", failed, ErrorRed, activeIndex >= 4 && failed > 0, Modifier.weight(1f))
        }

        if (jobs.isEmpty()) {
            GText(
                "Chưa có công việc. Thêm tài khoản nguồn rồi chọn Quét ngay.",
                12.sp,
                color = Muted
            )
        } else {
            jobs.take(3).forEach { job ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        GText(stageTitle(job.stage), 12.sp, weight = FontWeight.SemiBold, color = Ink)
                        GText(job.sourceVideoId, 10.sp, color = Muted)
                    }
                    GText(
                        statusTitle(job.status),
                        11.sp,
                        weight = FontWeight.SemiBold,
                        color = when (job.status) {
                            PipelineStatus.RUNNING -> Blue
                            PipelineStatus.DONE -> SuccessGreen
                            PipelineStatus.FAILED -> ErrorRed
                            PipelineStatus.PAUSED -> Color(0xFFD97706)
                            PipelineStatus.PENDING -> Muted
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeStatusItem(
    label: String,
    count: Int,
    color: Color,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        GText(
            count.toString(),
            14.sp,
            weight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasized) color else Muted
        )
        GText(
            label,
            9.sp,
            weight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) color else Muted
        )
    }
}

private fun stageTitle(stage: PipelineStage): String = when (stage) {
    PipelineStage.DOWNLOAD -> "Tải video"
    PipelineStage.ANALYZE -> "Tối ưu và lưu video"
    PipelineStage.SPEECH_TO_TEXT -> "Nhận diện giọng nói"
    PipelineStage.TRANSLATE -> "Dịch nội dung"
    PipelineStage.TTS -> "Tạo giọng đọc"
    PipelineStage.SUBTITLE -> "Tạo phụ đề"
    PipelineStage.MERGE -> "Ghép video"
    PipelineStage.EXPORT -> "Xuất video"
    PipelineStage.UPLOAD -> "Tải lên máy chủ"
    PipelineStage.VERIFY -> "Kiểm tra kết quả"
}

private fun statusTitle(status: PipelineStatus): String = when (status) {
    PipelineStatus.RUNNING -> "Đang chạy"
    PipelineStatus.PENDING -> "Đang chờ"
    PipelineStatus.DONE -> "Hoàn thành"
    PipelineStatus.FAILED -> "Thất bại"
    PipelineStatus.PAUSED -> "Tạm dừng"
}

@Composable
private fun ErrorSection(
    errors: List<AppError>,
    onAction: (AppError) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GText("Thông báo hệ thống", 15.sp, weight = FontWeight.SemiBold, color = Ink,
                modifier = Modifier.weight(1f))
            GText(
                "Xóa hết",
                12.sp,
                weight = FontWeight.Medium,
                color = Muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        errors.forEach { ErrorCard(it, onAction, onDelete) }
    }
}

private fun ErrorKind.icon(): ImageVector = when (this) {
    ErrorKind.VideoUnavailable -> Icons.Outlined.VideocamOff
    ErrorKind.StorageAlmostFull -> Icons.Outlined.Storage
    ErrorKind.NetworkLost -> Icons.Outlined.WifiOff
}

@Composable
private fun ErrorCard(error: AppError, onAction: (AppError) -> Unit, onDelete: (String) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val is502 = error.title.contains("HTTP 502", ignoreCase = true) ||
        error.message.contains("HTTP 502", ignoreCase = true)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(1.dp, Line, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(error.kind.icon(), contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    GText(error.title, 13.sp, weight = FontWeight.SemiBold, color = Ink,
                        modifier = Modifier.weight(1f, fill = false))
                    if (is502) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFFEE2E2))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            GText("HTTP 502", 10.sp, weight = FontWeight.Bold, color = Color(0xFFB91C1C))
                        }
                    }
                }
                GText(error.time, 10.sp, color = Muted)
            }
        }
        GText(error.message, 11.sp, color = Muted)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RectActionButton(
                text = "Xóa",
                onClick = { onDelete(error.id) },
                textColor = ErrorRed,
                modifier = Modifier.padding(end = 7.dp)
            )
            RectActionButton(
                text = error.actionLabel,
                onClick = { onAction(error) },
                filled = true
            )
        }
    }
}

@Composable
private fun RectActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Ink,
    filled: Boolean = false
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (filled) Color(0xFFF4F4F5) else Color.White)
            .border(1.dp, Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        GText(text, 11.sp, weight = FontWeight.Medium, color = textColor)
    }
}
