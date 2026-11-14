package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentautomation.ui.MainViewModel

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tổng quan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        StatCard("Video mới", stats.newVideos)
        StatCard("Đang tải", stats.downloading)
        StatCard("Đang xử lý", stats.processing)
        StatCard("Chờ đăng", stats.readyToPublish)
        StatCard("Đã đăng", stats.published)
        StatCard("Lỗi", stats.failed)
        Spacer(Modifier.height(8.dp))
        Text("Nền tảng:", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("Kuaishou") })
            AssistChip(onClick = {}, label = { Text("Douyin") })
            AssistChip(onClick = {}, label = { Text("TikTok") })
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
