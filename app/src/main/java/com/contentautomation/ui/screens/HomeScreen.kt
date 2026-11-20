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
import com.contentautomation.ui.models.PipelineSteps

@Composable
fun HomeScreen(
    systemOn: Boolean,
    errors: List<AppError>,
    onToggleSystem: () -> Unit,
    onErrorAction: (AppError) -> Unit,
    onClearErrors: () -> Unit
) {
    ScreenColumn {
        SystemCard(systemOn, onToggleSystem)
        PipelineCard()
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
private fun PipelineCard() {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            GText(
                "QUY TRÌNH XỬ LÝ VIDEO", 10.sp,
                weight = FontWeight.Bold, color = GlassColors.InkSoft,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            PipelineSteps.all.forEachIndexed { i, step ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GText("${i + 1}", 13.sp, weight = FontWeight.Bold, color = GlassColors.Blue,
                        modifier = Modifier.width(24.dp))
                    GText(step.pending, 13.sp, modifier = Modifier.weight(1f))
                }
                if (i != PipelineSteps.all.lastIndex) GlassDivider()
            }
        }
    }
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
