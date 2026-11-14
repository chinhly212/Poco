package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentautomation.data.db.entity.TikTokPost
import com.contentautomation.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val posts by vm.history.collectAsStateWithLifecycle()
    if (posts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Chưa có bài đăng nào")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(posts) { HistoryRow(it) }
    }
}

@Composable
private fun HistoryRow(p: TikTokPost) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Publish ID: ${p.publishId ?: "-"}", style = MaterialTheme.typography.titleSmall)
            Text("Trạng thái: ${p.status}")
            Text("Quyền riêng tư: ${p.privacyLevel ?: "-"}")
            Text("Thời gian: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(p.createdAt))}")
            p.errorMessage?.let { Text("Lỗi: $it", color = MaterialTheme.colorScheme.error) }
        }
    }
}
