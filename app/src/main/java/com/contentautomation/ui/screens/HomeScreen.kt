package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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

@Composable
fun HomeScreen(
    systemOn: Boolean,
    errors: List<AppError>,
    onToggleSystem: () -> Unit,
    onErrorAction: (AppError) -> Unit,
    onClearErrors: () -> Unit,
) {
    ScreenColumn(title = "Trang chủ") {
        PowerCard(systemOn, onToggleSystem)

        if (errors.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GText("Thông báo lỗi", 17.sp, weight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                CountBadge(errors.size)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearErrors) {
                    GText("Xóa hết", 13.sp, weight = FontWeight.SemiBold, color = GlassColors.Blue)
                }
            }
            errors.forEach { ErrorCard(it, onErrorAction) }
        }
    }
}

@Composable
private fun PowerCard(on: Boolean, onToggle: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(32.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassCard(
                Modifier.size(136.dp),
                CircleShape,
                glow = if (on) Color(0xFF22C55E) else null,
            ) {
                val colors = if (on) {
                    listOf(Color(0xFFB6F7D2), Color(0xFF1FB36B))
                } else {
                    listOf(Color(0xFFE6EEF8), Color(0xFF9DB2CC))
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(100.dp)
                        .clip(CircleShape)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    colors,
                                    center = Offset(size.width * 0.3f, size.height * 0.25f),
                                    radius = size.width * 0.9f,
                                )
                            )
                        }
                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                        .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Bật/tắt toàn bộ hệ thống",
                        modifier = Modifier.size(46.dp),
                        tint = Color(0xFF05352A),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GText(
                if (on) "Toàn bộ hệ thống đang chạy" else "Toàn bộ hệ thống đang tắt",
                18.sp, weight = FontWeight.SemiBold,
            )
            GText(
                if (on) "Chạm để tắt toàn bộ hệ thống" else "Chạm để bật toàn bộ hệ thống",
                13.sp, color = GlassColors.InkSoft,
            )
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(GlassColors.Red.copy(alpha = 0.15f))
            .border(1.dp, GlassColors.Red.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        GText("$count", 12.sp, weight = FontWeight.SemiBold, color = Color(0xFFB4232A))
    }
}

private fun ErrorKind.icon(): ImageVector = when (this) {
    ErrorKind.VideoUnavailable -> Icons.Rounded.VideocamOff
    ErrorKind.StorageAlmostFull -> Icons.Rounded.Storage
    ErrorKind.NetworkLost -> Icons.Rounded.WifiOff
}

@Composable
private fun ErrorCard(error: AppError, onAction: (AppError) -> Unit) {
    val critical = error.kind == ErrorKind.VideoUnavailable
    val tint = if (critical) Color(0xFFC42A2E) else GlassColors.Amber
    val bg = if (critical) GlassColors.Red.copy(alpha = 0.14f) else Color(0xFFF59E0B).copy(alpha = 0.20f)
    val edge = if (critical) GlassColors.Red.copy(alpha = 0.40f) else GlassColors.Amber.copy(alpha = 0.45f)

    GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp)) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(error.kind.icon(), size = 40.dp, shape = RoundedCornerShape(14.dp), tint = tint, background = bg, borderColor = edge)
            Column(Modifier.weight(1f)) {
                GText(error.title, 15.sp, weight = FontWeight.SemiBold)
                GText(error.message, 12.5.sp, Modifier.padding(top = 2.dp), color = GlassColors.InkSoft)
                GText(error.time, 11.sp, Modifier.padding(top = 3.dp), color = GlassColors.InkSoft)
            }
            GlassButton(error.actionLabel, onClick = { onAction(error) })
        }
    }
}
