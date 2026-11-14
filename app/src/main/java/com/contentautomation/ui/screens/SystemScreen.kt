package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentautomation.ui.MainViewModel

@Composable
fun SystemScreen(vm: MainViewModel) {
    val activeVideo by vm.currentProcessingVideo.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Tiến trình hệ thống", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (activeVideo == null) {
            // Không có video nào đang chạy
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(Color.Red, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Không phát hiện video mới", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            // Đang có video chạy
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(Color.Green, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Đang xử lý video mới!", color = Color.Green, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Tính toán tiến trình dựa trên trạng thái
            val (stepNumber, stepName) = getStepInfo(activeVideo?.status ?: "")
            val progress = stepNumber / 13f

            Text("Bước $stepNumber/13: $stepName", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(12.dp),
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Video ID: ${activeVideo?.externalVideoId}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

// Hàm ánh xạ trạng thái sang 13 bước
private fun getStepInfo(status: String): Pair<Int, String> {
    return when (status) {
        "NEW" -> 1 to "Phát hiện video mới từ kênh nguồn"
        "FETCHING_METADATA" -> 2 to "Đang copy link video"
        "DOWNLOADING" -> 3 to "Đang tải video không logo..."
        "UPLOADED_TO_DRIVE" -> 4 to "Đang chuyển hướng sang TikTok"
        "PROCESSING_VIDEO" -> 6 to "Đang ghép nhạc vào video..."
        "TRANSLATING" -> 7 to "Đang tạo mô tả video & hashtag"
        "READY_TO_PUBLISH" -> 8 to "Đang kiểm tra toàn bộ video..."
        "PUBLISHING" -> 9 to "Đang đăng lên TikTok..."
        "PUBLISHED" -> 13 to "Đã đăng thành công! Hoàn tất."
        "FAILED" -> 0 to "Gặp lỗi (Đang chờ thử lại)"
        else -> 1 to "Đang khởi tạo..."
    }
}
