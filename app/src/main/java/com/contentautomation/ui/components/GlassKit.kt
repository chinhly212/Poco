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

object GlassColors {
    val Ink = Color(0xFF10233F)
    val InkSoft = Color(0xFF64748B)
    val InkFaint = Color(0xFFB8C6D9)
    val Divider = Color(0x26738AA8)

    val Blue = Color(0xFF1769E8)
    val BlueIcon = Color(0xFF2879EA)
    val BlueBright = Color(0xFF4598FF)

    val Green = Color(0xFF16A36A)
    val Red = Color(0xFFE54855)
    val Amber = Color(0xFFD28A24)

    val ShadowBlue = Color(0x263D83D8)
    val Surface = Color(0xCCFFFFFF)
}

val LocalHazeState = compositionLocalOf<HazeState> {
    error("LocalHazeState chưa được khởi tạo")
}

private val GlassStyle = HazeStyle(
    backgroundColor = Color(0xFFEAF3FF),
    tint = HazeTint(Color.White.copy(alpha = 0.18f)),
    blurRadius = 22.dp,
    noiseFactor = 0f
)

private val GlassFill = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.88f),
        Color.White.copy(alpha = 0.58f),
        Color(0xFFEAF3FF).copy(alpha = 0.48f)
    )
)

@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF8FBFF),
                        Color(0xFFE8F2FF),
                        Color(0xFFF4F8FF)
                    )
                )
            )
            .drawBehind {
                val w = size.width
                val h = size.height

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x553B91FF),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.12f, h * 0.12f),
                        radius = w * 0.75f
                    ),
                    radius = w * 0.75f,
                    center = Offset(w * 0.12f, h * 0.12f)
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x4487BFFF),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.95f, h * 0.48f),
                        radius = w * 0.65f
                    ),
                    radius = w * 0.65f,
                    center = Offset(w * 0.95f, h * 0.48f)
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x335D8FFF),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.2f, h * 0.9f),
                        radius = w * 0.6f
                    ),
                    radius = w * 0.6f,
                    center = Offset(w * 0.2f, h * 0.9f)
                )
            }
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    glow: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shadowColor = glow ?: GlassColors.ShadowBlue

    Box(
        modifier
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .clip(shape)
            .hazeEffect(
                state = LocalHazeState.current,
                style = GlassStyle
            )
            .background(GlassFill)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.98f),
                        Color.White.copy(alpha = 0.48f),
                        Color(0xFFBBD7F7).copy(alpha = 0.35f)
                    )
                ),
                shape = shape
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
        color = color,
        fontSize = size,
        fontWeight = weight,
        textAlign = align,
        lineHeight = size * 1.35
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier
            .heightIn(min = 44.dp)
            .shadow(
                5.dp,
                shape,
                ambientColor = GlassColors.ShadowBlue,
                spotColor = GlassColors.ShadowBlue
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White,
                        Color(0xFFEAF3FF)
                    )
                )
            )
            .border(1.dp, Color.White, shape)
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        GText(
            text = text,
            size = 12.sp,
            weight = FontWeight.Bold,
            color = GlassColors.Blue
        )
    }
}

@Composable
fun IconTile(
    icon: ImageVector,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    tint: Color = GlassColors.BlueIcon,
    background: Color = Color.White.copy(alpha = 0.76f),
    borderColor: Color = Color.White
) {
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.52f),
            tint = tint
        )
    }
}

@Composable
fun GlassRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    big: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (big) 88.dp else 68.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        if (big) {
            IconTile(
                icon = icon,
                size = 52.dp,
                shape = RoundedCornerShape(17.dp)
            )
        } else {
            IconTile(icon = icon)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            GText(
                title,
                if (big) 16.sp else 14.sp,
                weight = FontWeight.SemiBold
            )

            GText(
                subtitle,
                12.sp,
                color = GlassColors.InkSoft
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = GlassColors.InkSoft.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun StatusDot(color: Color) {
    Box(
        Modifier
            .size(11.dp)
            .shadow(
                7.dp,
                CircleShape,
                ambientColor = color,
                spotColor = color
            )
            .background(color, CircleShape)
    )
}

@Composable
fun ScreenColumn(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(
                horizontal = 18.dp,
                vertical = 14.dp
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            GText(
                title,
                28.sp,
                weight = FontWeight.Bold
            )

            if (subtitle != null) {
                GText(
                    subtitle,
                    13.sp,
                    color = GlassColors.InkSoft
                )
            }
        }

        content()

        Spacer(Modifier.height(96.dp))
    }
}

enum class Tab(
    val label: String,
    val icon: ImageVector
) {
    Home("Trang chủ", Icons.Rounded.Home),
    System("Công việc", Icons.Rounded.GridView),
    Storage("Lưu trữ", Icons.Rounded.Storage),
    Settings("Cài đặt", Icons.Rounded.Settings)
}

@Composable
fun GlassBottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Tab.entries.forEach { tab ->
                val isSelected = tab == selected
                val tint = if (isSelected) {
                    GlassColors.Blue
                } else {
                    GlassColors.InkSoft
                }

                val itemShape = RoundedCornerShape(22.dp)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(itemShape)
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White,
                                                Color(0xFFEAF3FF)
                                            )
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        Color.White,
                                        itemShape
                                    )
                            } else {
                                Modifier
                            }
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelect(tab) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        3.dp,
                        Alignment.CenterVertically
                    )
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(21.dp),
                        tint = tint
                    )

                    GText(
                        text = tab.label,
                        size = 10.sp,
                        weight = if (isSelected) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                        color = tint
                    )
                }
            }
        }
    }
}
