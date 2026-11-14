package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentautomation.service.AutomationForegroundService
import com.contentautomation.ui.MainViewModel

@Composable
fun HomeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    // Lấy danh sách các video bị lỗi từ Database
    val failedVideos by vm.failedVideos.collectAsStateWithLifecycle()
    
    // Trạng thái bật tắt (giả lập trạng thái ban đầu là false)
    var isRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // THẺ BẬT TẮT HỆ THỐNG 24/7
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hệ thống 24/7", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Tự động quét và đăng video", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Switch(
                    checked = isRunning,
                    onCheckedChange = {
                        isRunning = it
                        if (it) {
                            AutomationForegroundService.start(context)
                        } else {
                            AutomationForegroundService.stop(context)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // KHU VỰC THÔNG BÁO LỖI
        Text("Thông báo hệ thống", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (failedVideos.isEmpty()) {
            // Không có lỗi
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(Color.Green, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hệ thống đang hoạt động bình thường", color = Color.Gray)
            }
        } else {
            // Có lỗi, hiển thị danh sách
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(failedVideos) { video ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Lỗi: ${video.lastError ?: "Không rõ nguyên nhân"}", color = Color.Red, fontWeight = FontWeight.Bold)
                            Text("Video ID: ${video.externalVideoId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
