package com.contentautomation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Bảng màu giao diện kính lỏng – nền xanh trắng. */
object GlassColors {
    val Ink = Color(0xFF0B2447)
    val InkSoft = Color(0xB80B2447)      // 72%
    val InkFaint = Color(0x1F0B2447)     // 12%
    val Divider = Color(0x1A0B2447)      // 10%
    val Blue = Color(0xFF1456C8)
    val BlueIcon = Color(0xFF1F6FE0)
    val BlueBright = Color(0xFF2F7BE8)
    val Green = Color(0xFF17A34A)
    val Red = Color(0xFFE5383B)
    val Amber = Color(0xFFB45309)
    val ShadowBlue = Color(0x33288CFF)
}

/** HazeState dùng chung: làm mờ phần nền nằm phía sau các thẻ kính. */
val LocalHazeState = compositionLocalOf<HazeState> {
    error("Hãy bọc UI trong CompositionLocalProvider(LocalHazeState provides ...)")
}

private val GlassStyle = HazeStyle(
    backgroundColor = Color(0xFFEAF3FF),
    tint = HazeTint(Color.White.copy(alpha = 0.15f)),
    blurRadius = 24.dp,
    noiseFactor = 0f,
)

private val GlassFill = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = 0.78f),
        Color.White.copy(alpha = 0.22f),
        Color.White.copy(alpha = 0.40f),
    )
)

/** Nền gradient trắng–xanh + các mảng xanh mờ để lớp kính có thứ để khúc xạ. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF6FAFF), Color(0xFFD9EAFF), Color(0xFFF4F9FF))
                )
            )
            .drawBehind {
                val w = size.width
                val h = size.height
                fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                    val c = Offset(cx, cy)
                    drawCircle(
                        Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = r),
                        radius = r,
                        center = c,
                    )
                }
                blob(Color(0x8C3D95FF), w * 0.20f, h * 0.14f, w * 0.60f)
                blob(Color(0xBF8FD0FF), w * 0.87f, h * 0.52f, w * 0.55f)
                blob(Color(0x735A8BFF), w * 0.24f, h * 0.82f, w * 0.50f)
            }
    )
}

/** Thẻ kính lỏng: blur nền phía sau + gradient trắng trong suốt + viền sáng + bóng xanh. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    glow: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shadowColor = glow ?: GlassColors.ShadowBlue
    Box(
        modifier
            .shadow(14.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(shape)
            .hazeEffect(state = LocalHazeState.current, style = GlassStyle)
            .background(GlassFill)
            .border(1.dp, Color.White.copy(alpha = 0.9f), shape),
        content = content,
    )
}

@Composable
fun GText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    color: Color = GlassColors.Ink,
    align: TextAlign? = null,
) {
    Text(text, modifier, color = color, fontSize = size, fontWeight = weight, textAlign = align)
}

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .shadow(4.dp, shape, ambientColor = GlassColors.ShadowBlue, spotColor = GlassColors.ShadowBlue)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.7f))
            .border(1.dp, Color.White, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        GText(text, 12.5.sp, weight = FontWeight.SemiBold, color = GlassColors.Blue)
    }
}

@Composable
fun IconTile(
    icon: ImageVector,
    size: Dp = 36.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    tint: Color = GlassColors.BlueIcon,
    background: Color = Color.White.copy(alpha = 0.6f),
    borderColor: Color = Color.White.copy(alpha = 0.95f),
) {
    Box(
        Modifier.size(size).clip(shape).background(background).border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.55f), tint = tint)
    }
}

/** Một dòng trong danh sách (Cài đặt, Lưu trữ…). */
@Composable
fun GlassRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    big: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (big) 84.dp else 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (big) IconTile(icon, 52.dp, CircleShape) else IconTile(icon)
        Column(Modifier.weight(1f)) {
            GText(title, if (big) 17.sp else 15.sp, weight = FontWeight.SemiBold)
            GText(subtitle, 12.5.sp, color = GlassColors.InkSoft)
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = GlassColors.Ink.copy(alpha = 0.5f),
        )
    }
}

/** Chấm trạng thái phát sáng (xanh = đang chạy, đỏ = không phát hiện video mới). */
@Composable
fun StatusDot(color: Color) {
    Box(
        Modifier
            .size(14.dp)
            .shadow(8.dp, CircleShape, ambientColor = color, spotColor = color)
            .background(color, CircleShape)
    )
}

/** Khung chung cho mỗi tab: tiêu đề + nội dung cuộn, chừa chỗ cho thanh tab nổi. */
@Composable
fun ScreenColumn(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            GText(title, 30.sp, weight = FontWeight.Bold)
            if (subtitle != null) GText(subtitle, 13.sp, color = GlassColors.InkSoft)
        }
        content()
        Spacer(Modifier.height(100.dp))
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Trang chủ", Icons.Rounded.Home),
    System("Hệ thống", Icons.Rounded.GridView),
    Storage("Lưu trữ", Icons.Rounded.Storage),
    Settings("Cài đặt", Icons.Rounded.Settings),
}

/** Thanh 4 tab nổi dạng kính lỏng. */
@Composable
fun GlassBottomBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth().height(68.dp), RoundedCornerShape(34.dp)) {
        Row(
            Modifier.fillMaxSize().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                val shape = RoundedCornerShape(28.dp)
                val tint = if (on) GlassColors.Blue else GlassColors.InkSoft
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .then(
                            if (on) Modifier
                                .background(Color.White.copy(alpha = 0.85f))
                                .border(1.dp, Color.White, shape)
                            else Modifier
                        )
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Icon(tab.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
                    GText(
                        tab.label, 11.sp,
                        weight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint,
                    )
                }
            }
        }
    }
}
