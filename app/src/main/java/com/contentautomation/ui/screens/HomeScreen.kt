package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ui.components.*
import com.contentautomation.ui.models.AppError
import com.contentautomation.ui.models.ErrorKind
import com.contentautomation.data.db.entity.PipelineJob
import com.contentautomation.data.db.entity.PipelineStage
import com.contentautomation.data.db.entity.PipelineStatus

@Composable
fun HomeScreen(
    systemOn: Boolean,
    errors: List<AppError>,
    jobs: List<PipelineJob>,
    onToggleSystem: () -> Unit,
    onOpenWork: () -> Unit,
    onErrorAction: (AppError) -> Unit,
    onClearErrors: () -> Unit
) {
    ScreenColumn {
        SystemCard(systemOn, onToggleSystem)
        PipelineCard(jobs, onOpenWork)
        if (errors.isNotEmpty()) {
            ErrorSection(errors, onErrorAction, onClearErrors)
        }
    }
}

@Composable
private fun SystemCard(systemOn: Boolean, onToggle: () -> Unit) {
    val accent = if (systemOn) GlassColors.Green else GlassColors.InkSoft

    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GText("TRẠNG THÁI HỆ THỐNG", 11.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
            Spacer(Modifier.height(5.dp))
            GText(
                if (systemOn) "Đang hoạt động" else "Đang tắt",
                21.sp, weight = FontWeight.Bold, color = accent
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.95f), accent.copy(alpha = 0.22f))
                        )
                    )
                    .toggleable(value = systemOn, role = Role.Switch, onValueChange = { onToggle() }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PowerSettingsNew,
                    contentDescription = "Bật hoặc tắt hệ thống",
                    tint = accent,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

@Composable
private fun PipelineCard(jobs: List<PipelineJob>, onOpenWork: () -> Unit) {
    val running = jobs.count { it.status == PipelineStatus.RUNNING }
    val pending = jobs.count { it.status == PipelineStatus.PENDING }
    val done = jobs.count { it.status == PipelineStatus.DONE }
    val failed = jobs.count { it.status == PipelineStatus.FAILED }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GText("TIẾN TRÌNH CÔNG VIỆC", 10.sp, weight = FontWeight.Bold,
                    color = GlassColors.InkSoft, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenWork) {
                    GText("Mở công việc", 12.sp, weight = FontWeight.Bold, color = GlassColors.Blue)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GText("Tổng: ${jobs.size}", 12.sp, weight = FontWeight.SemiBold)
                GText("Đang chạy: $running", 12.sp, color = GlassColors.Blue)
                GText("Chờ: $pending", 12.sp, color = GlassColors.InkSoft)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GText("Đã lưu: $done", 12.sp, color = GlassColors.Green)
                GText("Lỗi: $failed", 12.sp, color = GlassColors.Red)
            }
            Spacer(Modifier.height(8.dp))
            if (jobs.isEmpty()) {
                GText("Chưa có công việc. Thêm tài khoản nguồn rồi chọn Quét ngay.",
                    12.sp, color = GlassColors.InkSoft)
            } else {
                jobs.take(3).forEach { job ->
                    GlassDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            GText(stageTitle(job.stage), 12.sp, weight = FontWeight.SemiBold)
                            GText(job.sourceVideoId, 10.sp, color = GlassColors.InkSoft)
                        }
                        GText(statusTitle(job.status), 11.sp, weight = FontWeight.Bold,
                            color = when (job.status) {
                                PipelineStatus.RUNNING -> GlassColors.Blue
                                PipelineStatus.DONE -> GlassColors.Green
                                PipelineStatus.FAILED -> GlassColors.Red
                                PipelineStatus.PAUSED -> GlassColors.Amber
                                PipelineStatus.PENDING -> GlassColors.InkSoft
                            })
                    }
                }
            }
        }
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
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GText("Cảnh báo hệ thống", 17.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) {
                GText("Xóa hết", 12.sp, weight = FontWeight.Bold, color = GlassColors.Blue)
            }
        }
        errors.forEach { ErrorCard(it, onAction) }
    }
}

private fun ErrorKind.icon(): ImageVector = when (this) {
    ErrorKind.VideoUnavailable -> Icons.Rounded.VideocamOff
    ErrorKind.StorageAlmostFull -> Icons.Rounded.Storage
    ErrorKind.NetworkLost -> Icons.Rounded.WifiOff
}

@Composable
private fun ErrorCard(error: AppError, onAction: (AppError) -> Unit) {
    val tint = if (error.kind == ErrorKind.VideoUnavailable) GlassColors.Red else GlassColors.Amber

    GlassCard(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                IconTile(error.kind.icon(), tint, 26.dp)
                Column(Modifier.weight(1f)) {
                    GText(error.title, 14.sp, weight = FontWeight.Bold)
                    GText(error.time, 11.sp, color = GlassColors.InkSoft)
                }
            }
            GText(error.message, 12.sp, color = GlassColors.InkSoft)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GlassButton(error.actionLabel, onClick = { onAction(error) })
            }
        }
    }
}
