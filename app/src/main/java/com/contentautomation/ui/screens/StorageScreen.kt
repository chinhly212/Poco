package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.*
import java.util.Locale

@Composable
fun StorageScreen(vm: MainViewModel) {
    val phoneStorage by vm.phoneStorage.collectAsState()

    ScreenColumn(title = "Lưu trữ", subtitle = "Thông tin bộ nhớ thiết bị") {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GText("Bộ nhớ điện thoại", 16.sp, weight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GText("Đã dùng: ${String.format(Locale.US, "%.1f", phoneStorage.usedGb)} GB", 14.sp, color = GlassColors.InkSoft)
                    GText("Tổng: ${String.format(Locale.US, "%.1f", phoneStorage.totalGb)} GB", 14.sp, color = GlassColors.InkSoft)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { phoneStorage.fraction },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = if (phoneStorage.fraction > 0.9f) GlassColors.Red else GlassColors.Blue,
                    trackColor = GlassColors.InkFaint
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GText("Google Drive", 16.sp, weight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                GText("Chưa kết nối API (Vào Cài đặt để nhập)", 13.sp, color = GlassColors.InkSoft)
            }
        }
    }
}
