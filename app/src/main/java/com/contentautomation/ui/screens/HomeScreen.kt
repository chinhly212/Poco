
package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Upload
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
    onClearErrors: () -> Unit
) {
    ScreenColumn(
        title = "Poco",
        subtitle = "Trung tâm điều khiển tự động hóa video"
    ) {
        PowerCard(
            on = systemOn,
            onToggle = onToggleSystem
        )

        OverviewSection(
            systemOn = systemOn,
            errorCount = errors.size
        )

        WorkflowSection()

        if (errors.isNotEmpty()) {
            ErrorSection(
                errors = errors,
                onAction = onErrorAction,
                onClear = onClearErrors
            )
        } else {
            NormalStatusCard(systemOn)
        }
    }
}

@Composable
private fun PowerCard(
    on: Boolean,
    onToggle: () -> Unit
) {
    val accent = if (on) {
        Color(0xFF16A36A)
    } else {
        Color(0xFF8B9AAF)
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        glow = if (on) Color(0x3322C985) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    GText(
                        "TRẠNG THÁI HỆ THỐNG",
                        11.sp,
                        weight = FontWeight.Bold,
                        color = GlassColors.InkSoft
                    )

                    Spacer(Modifier.height(6.dp))

                    GText(
                        if (on) "Đang hoạt động" else "Đang tắt",
                        21.sp,
                        weight = FontWeight.Bold,
                        color = accent
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    StatusDot(accent)
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(126.dp)
                    .shadowCircle(accent)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (on) {
                                listOf(
                                    Color(0xFFD8FBE9),
                                    Color(0xFF70DCA7),
                                    Color(0xFF22A66B)
                                )
                            } else {
                                listOf(
                                    Color(0xFFF2F5FA),
                                    Color(0xFFD5DFEA),
                                    Color(0xFF9EADC0)
                                )
                            }
                        )
                    )
                    .border(
                        2.dp,
                        Color.White.copy(alpha = 0.9f),
                        CircleShape
                    )
                    .toggleable(
                        value = on,
                        role = Role.Switch,
                        onValueChange = { onToggle() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = "Bật hoặc tắt hệ thống",
                    modifier = Modifier.size(52.dp),
                    tint = Color(0xFF123B34)
                )
            }

            Spacer(Modifier.height(18.dp))

            GText(
                if (on) {
                    "Poco đang chạy dịch vụ nền"
                } else {
                    "Hệ thống chưa được khởi động"
                },
                14.sp,
                weight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(5.dp))

            GText(
                if (on) {
                    "Chạm nút nguồn để dừng tự động hóa"
                } else {
                    "Chạm nút nguồn để bắt đầu tự động hóa"
                },
                12.sp,
                color = GlassColors.InkSoft
            )
        }
    }
}

private fun Modifier.shadowCircle(color: Color): Modifier =
    this.drawBehind {
        drawCircle(
            color = color.copy(alpha = 0.16f),
            radius = size.minDimension * 0.62f,
            center = Offset(
                size.width / 2f,
                size.height / 2f
            )
        )
    }

@Composable
private fun OverviewSection(
    systemOn: Boolean,
    errorCount: Int
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GText(
            "Tổng quan",
            17.sp,
            weight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OverviewCard(
                modifier = Modifier.weight(1f),
                title = "Dịch vụ",
                value = if (systemOn) "Đang chạy" else "Đang tắt",
                icon = Icons.Rounded.PowerSettingsNew,
                color = if (systemOn) {
                    GlassColors.Green
                } else {
                    GlassColors.InkSoft
                }
            )

            OverviewCard(
                modifier = Modifier.weight(1f),
                title = "Cảnh báo",
                value = errorCount.toString(),
                icon = Icons.Rounded.ErrorOutline,
                color = if (errorCount > 0) {
                    GlassColors.Red
                } else {
                    GlassColors.Green
                }
            )
        }
    }
}

@Composable
private fun OverviewCard(
    modifier: Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconTile(
                icon = icon,
                size = 38.dp,
                tint = color,
                background = color.copy(alpha = 0.10f),
                borderColor = color.copy(alpha = 0.15f)
            )

            GText(
                title,
                12.sp,
                color = GlassColors.InkSoft
            )

            GText(
                value,
                16.sp,
                weight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun WorkflowSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GText(
            "Quy trình video",
            17.sp,
            weight = FontWeight.Bold
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
            ) {
                WorkflowRow(
                    icon = Icons.Rounded.CloudDownload,
                    title = "Phát hiện và tải video",
                    subtitle = "Nguồn video Kuaishou / Douyin",
                    color = GlassColors.Blue
                )

                WorkflowDivider()

                WorkflowRow(
                    icon = Icons.Rounded.MusicNote,
                    title = "Xử lý âm thanh",
                    subtitle = "Quy trình lựa chọn và ghép nhạc",
                    color = Color(0xFF9465D8)
                )

                WorkflowDivider()

                WorkflowRow(
                    icon = Icons.Rounded.Subtitles,
                    title = "Phụ đề video",
                    subtitle = "Nhận dạng và tạo phụ đề",
                    color = Color(0xFFCC8B31)
                )

                WorkflowDivider()

                WorkflowRow(
                    icon = Icons.Rounded.Upload,
                    title = "Xuất bản video",
                    subtitle = "TikTok và lưu trữ kết quả",
                    color = GlassColors.Green
                )
            }
        }
    }
}

@Composable
private fun WorkflowRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(
            icon = icon,
            size = 43.dp,
            shape = RoundedCornerShape(15.dp),
            tint = color,
            background = color.copy(alpha = 0.10f),
            borderColor = color.copy(alpha = 0.14f)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GText(
                title,
                14.sp,
                weight = FontWeight.SemiBold
            )

            GText(
                subtitle,
                11.sp,
                color = GlassColors.InkSoft
            )
        }
    }
}

@Composable
private fun WorkflowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 55.dp)
            .height(1.dp)
            .background(GlassColors.Divider)
    )
}

@Composable
private fun NormalStatusCard(systemOn: Boolean) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(
                icon = Icons.Rounded.CheckCircle,
                tint = if (systemOn) {
                    GlassColors.Green
                } else {
                    GlassColors.InkSoft
                },
                background = GlassColors.Green.copy(alpha = 0.09f)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                GText(
                    if (systemOn) "Không có cảnh báo" else "Hệ thống đang chờ",
                    14.sp,
                    weight = FontWeight.SemiBold
                )

                GText(
                    if (systemOn) {
                        "Chưa ghi nhận lỗi trong phiên hiện tại."
                    } else {
                        "Bật hệ thống để bắt đầu dịch vụ tự động."
                    },
                    12.sp,
                    color = GlassColors.InkSoft
                )
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
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                GText(
                    "Cảnh báo hệ thống",
                    17.sp,
                    weight = FontWeight.Bold
                )

                GText(
                    "${errors.size} thông báo cần kiểm tra",
                    12.sp,
                    color = GlassColors.InkSoft
                )
            }

            TextButton(onClick = onClear) {
                GText(
                    "Xóa hết",
                    12.sp,
                    weight = FontWeight.Bold,
                    color = GlassColors.Blue
                )
            }
        }

        errors.forEach { error ->
            ErrorCard(
                error = error,
                onAction = onAction
            )
        }
    }
}

private fun ErrorKind.icon(): ImageVector =
    when (this) {
        ErrorKind.VideoUnavailable ->
            Icons.Rounded.VideocamOff

        ErrorKind.StorageAlmostFull ->
            Icons.Rounded.Storage

        ErrorKind.NetworkLost ->
            Icons.Rounded.WifiOff
    }

@Composable
private fun ErrorCard(
    error: AppError,
    onAction: (AppError) -> Unit
) {
    val critical =
        error.kind == ErrorKind.VideoUnavailable

    val tint = if (critical) {
        GlassColors.Red
    } else {
        GlassColors.Amber
    }

    val background = tint.copy(alpha = 0.10f)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                IconTile(
                    icon = error.kind.icon(),
                    size = 42.dp,
                    tint = tint,
                    background = background,
                    borderColor = tint.copy(alpha = 0.20f)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    GText(
                        error.title,
                        14.sp,
                        weight = FontWeight.Bold
                    )

                    GText(
                        error.time,
                        11.sp,
                        color = GlassColors.InkSoft
                    )
                }
            }

            GText(
                error.message,
                12.sp,
                color = GlassColors.InkSoft
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                GlassButton(
                    text = error.actionLabel,
                    onClick = { onAction(error) }
                )
            }
        }
    }
}
