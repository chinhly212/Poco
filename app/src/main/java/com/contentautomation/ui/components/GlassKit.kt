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
    val Ink = Color(0xFF18181B)
    val InkSoft = Color(0xFF71717A)
    val InkFaint = Color(0xFFA1A1AA)
    val Blue = Color(0xFF2563EB)
    val Green = Color(0xFF16A34A)
    val Red = Color(0xFFDC2626)
    val Amber = Color(0xFFD97706)
    val Divider = Color(0xFFE4E4E7)
    val BgTop = Color(0xFFF4F4F5)
    val BgMid = Color(0xFFF4F4F5)
    val BgBottom = Color(0xFFF4F4F5)
}

/** Nền xám phẳng theo giao diện Minimalist SaaS. */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F4F5)),
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .drawBehind {
                drawLine(
                    color = Color(0xFFE4E4E7),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 5.dp)
        ) {
            items.forEachIndexed { i, item ->
                val selected = i == selectedIndex
                val tint = if (selected) Color(0xFF18181B) else Color(0xFF71717A)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelect(i) }
                        .padding(top = 9.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    GText(
                        item.label,
                        10.sp,
                        weight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint
                    )
                }
            }
        }
    }
}
