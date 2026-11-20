package com.contentautomation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object GlassColors {
    val Ink = Color(0xFF0B2A55)
    val InkSoft = Color(0xFF40607F)
    val InkFaint = Color(0xFF8AA3BF)
    val Blue = Color(0xFF0A66D9)
    val Green = Color(0xFF0A8A5C)
    val Red = Color(0xFFC0322B)
    val Amber = Color(0xFF9A5A00)
    val Divider = Color(0x290A66D9)
    val BgTop = Color(0xFFFFFFFF)
    val BgMid = Color(0xFFE6F3FF)
    val BgBottom = Color(0xFFC4E2FF)
}

/** Nền trắng chuyển xanh nhạt, có vệt sáng xanh để lớp kính có chiều sâu. */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GlassColors.BgTop, GlassColors.BgMid, GlassColors.BgBottom)
                )
            )
            .drawBehind {
                fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                    val c = Offset(size.width * cx, size.height * cy)
                    drawCircle(
                        brush = Brush.radialGradient(listOf(color, Color.Transparent), c, r),
                        radius = r,
                        center = c
                    )
                }
                blob(Color(0x8C5AAFFF), 0.88f, 0.06f, size.width * 0.75f)
                blob(Color(0x592882F0), 0.00f, 0.52f, size.width * 0.80f)
                blob(Color(0x8078C8FF), 0.80f, 0.92f, size.width * 0.70f)
            },
        content = content
    )
}

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = GlassColors.Blue.copy(alpha = 0.25f),
                spotColor = GlassColors.Blue.copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.34f))
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.4f)))
                ),
                shape
            ),
        content = content
    )
}

@Composable
fun GText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    color: Color = GlassColors.Ink,
    align: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = size,
        fontWeight = weight,
        color = color,
        textAlign = align
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (primary) GlassColors.Blue else Color.White.copy(alpha = 0.65f))
            .border(1.dp, GlassColors.Blue.copy(alpha = if (primary) 1f else 0.3f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        GText(
            text, 12.sp,
            weight = FontWeight.SemiBold,
            color = if (primary) Color.White else GlassColors.Blue
        )
    }
}

@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassButton(text = label, onClick = onClick, primary = selected)
}

/** Biểu tượng để trần, không đặt trong ô bo góc. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(GlassColors.Divider))
}

/** Thanh tiến độ phẳng, không bo góc. */
@Composable
fun GlassProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = GlassColors.Blue) {
    Box(modifier.fillMaxWidth().height(10.dp).background(GlassColors.Blue.copy(alpha = 0.14f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

data class BottomItem(val label: String, val icon: ImageVector)

@Composable
fun GlassBottomBar(
    items: List<BottomItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(Modifier.padding(6.dp)) {
            items.forEachIndexed { i, item ->
                val on = i == selectedIndex
                val tint = if (on) GlassColors.Blue else GlassColors.InkSoft
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (on) GlassColors.Blue.copy(alpha = 0.13f) else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(3.dp))
                    GText(item.label, 10.sp, weight = if (on) FontWeight.Bold else FontWeight.Normal, color = tint)
                }
            }
        }
    }
}
