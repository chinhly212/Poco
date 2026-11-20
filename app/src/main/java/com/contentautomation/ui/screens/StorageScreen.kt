package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.*
import java.util.Locale

@Composable
fun StorageScreen(vm: MainViewModel) {
    val storage by vm.phoneStorage.collectAsState()

    val used = storage.usedGb.coerceAtLeast(0f)
    val total = storage.totalGb.coerceAtLeast(0f)
    val free = storage.freeGb.coerceAtLeast(0f)
    val fraction = storage.fraction.coerceIn(0f, 1f)

    val usedText = String.format(Locale.US, "%.1f", used)
    val totalText = String.format(Locale.US, "%.1f", total)
    val freeText = String.format(Locale.US, "%.1f", free)
    val percent = (fraction * 100).toInt()

    val progressColor = when {
        fraction >= 0.9f -> GlassColors.Red
        fraction >= 0.75f -> GlassColors.Amber
        else -> GlassColors.Blue
    }

    ScreenColumn(
        title = "Lưu trữ",
        subtitle = "Theo dõi dung lượng thiết bị"
    ) {
        // STORAGE OVERVIEW
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                GText(
                    "BỘ NHỚ ĐIỆN THOẠI",
                    11.sp,
                    color = GlassColors.InkSoft,
                    weight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.Bottom
                ) {
                    Column {
                        GText(
                            "$usedText GB",
                            29.sp,
                            color = GlassColors.Ink,
                            weight = FontWeight.Bold
                        )
                        GText(
                            "Dung lượng đã sử dụng",
                            11.sp,
                            color = GlassColors.InkSoft
                        )
                    }

                    GText(
                        "$percent%",
                        20.sp,
                        color = progressColor,
                        weight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = progressColor,
                    trackColor = GlassColors.InkFaint.copy(alpha = 0.2f)
                )

                Spacer(Modifier.height(17.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StorageValue("Đã dùng", "$usedText GB")
                    StorageValue("Còn trống", "$freeText GB")
                    StorageValue("Tổng cộng", "$totalText GB")
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // STORAGE STATUS
        GText(
            "Tình trạng bộ nhớ",
            16.sp,
            weight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(9.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    StatusDot(progressColor)

                    Spacer(Modifier.width(9.dp))

                    GText(
                        when {
                            total <= 0f -> "Chưa đọc được thông tin bộ nhớ"
                            fraction >= 0.9f -> "Bộ nhớ gần đầy"
                            fraction >= 0.75f -> "Bộ nhớ đang sử dụng nhiều"
                            else -> "Dung lượng còn đủ"
                        },
                        14.sp,
                        weight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(8.dp))

                GText(
                    when {
                        total <= 0f ->
                            "Không lấy được dữ liệu dung lượng thiết bị."
                        fraction >= 0.9f ->
                            "Nên giải phóng dung lượng trước khi xử lý video mới."
                        fraction >= 0.75f ->
                            "Hãy theo dõi dung lượng khi tải và xuất video."
                        else ->
                            "Bộ nhớ thiết bị hiện chưa ở mức cảnh báo."
                    },
                    12.sp,
                    color = GlassColors.InkSoft
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // GOOGLE DRIVE
        GText(
            "Lưu trữ đám mây",
            16.sp,
            weight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(9.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GText(
                    "Google Drive",
                    16.sp,
                    weight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    StatusDot(GlassColors.Amber)

                    Spacer(Modifier.width(8.dp))

                    GText(
                        "Chưa kết nối",
                        12.sp,
                        color = GlassColors.Amber,
                        weight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(9.dp))

                GText(
                    "Ứng dụng chưa kết nối Google Drive API. "
                        + "Chức năng sao lưu đám mây chưa được kích hoạt.",
                    12.sp,
                    color = GlassColors.InkSoft
                )
            }
        }
    }
}

@Composable
private fun StorageValue(
    label: String,
    value: String
) {
    Column {
        GText(
            value,
            13.sp,
            weight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(3.dp))

        GText(
            label,
            10.sp,
            color = GlassColors.InkSoft
        )
    }
}
