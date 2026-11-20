package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ui.components.*
import com.contentautomation.ui.models.StorageInfo
import java.util.Locale

private const val WARN_FRACTION = 0.9f
private const val WARN_FREE_GB = 10f

private fun gb(v: Float) = String.format(Locale.getDefault(), "%.1f GB", v)

/**
 * driveStatus: chuỗi trạng thái Google Drive lấy từ nơi bạn đang quản lý (ví dụ "Chưa kết nối").
 * onDriveClick: hành động của nút ở khu vực Google Drive.
 */
@Composable
fun StorageScreen(
    storage: StorageInfo,
    driveStatus: String,
    onDriveClick: () -> Unit
) {
    val low = storage.totalGb > 0f &&
        (storage.fraction >= WARN_FRACTION || storage.freeGb < WARN_FREE_GB)

    ScreenColumn {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GText("ĐÃ SỬ DỤNG", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
                GText(gb(storage.usedGb), 30.sp, weight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                GlassProgressBar(storage.fraction, color = if (low) GlassColors.Amber else GlassColors.Blue)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        GText("Tổng", 12.sp, color = GlassColors.InkSoft)
                        GText(gb(storage.totalGb), 14.sp, weight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        GText("Còn trống", 12.sp, color = GlassColors.InkSoft)
                        GText(gb(storage.freeGb), 14.sp, weight = FontWeight.Bold, color = GlassColors.Green)
                    }
                }
            }
        }

        if (low) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    GText("Cảnh báo bộ nhớ", 14.sp, weight = FontWeight.Bold, color = GlassColors.Amber)
                    Spacer(Modifier.height(3.dp))
                    GText("Bộ nhớ sắp đầy, hãy giải phóng dung lượng để tiếp tục lưu video.",
                        12.sp, color = GlassColors.InkSoft)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    GText("GOOGLE DRIVE", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
                    GText(driveStatus, 14.sp, weight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp))
                }
                GlassButton("Mở", onClick = onDriveClick)
            }
        }
    }
}
