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
    val failedVideos by vm.failedVideos.collectAsStateWithLifecycle()
    
    // Lấy trạng thái bật/tắt từ bộ nhớ máy
    val isRunning by vm.isSystemRunning.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
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
                        vm.setSystemRunning(it)
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

        Text("Thông báo lỗi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (failedVideos.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(Color.Green, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Không có lỗi nào xảy ra", color = Color.Gray)
            }
        } else {
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
