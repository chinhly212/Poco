package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentautomation.data.db.entity.SourceVideo
import com.contentautomation.ui.MainViewModel

@Composable
fun NewVideosScreen(vm: MainViewModel) {
    val videos by vm.newVideos.collectAsStateWithLifecycle()
    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Không có video mới")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(videos) { v -> NewVideoRow(v, onDownload = { vm.downloadNow(v) }) }
    }
}

@Composable
private fun NewVideoRow(v: SourceVideo, onDownload: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(v.title ?: v.externalVideoId, style = MaterialTheme.typography.titleMedium)
            if (!v.descriptionZh.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(v.descriptionZh, style = MaterialTheme.typography.bodySmall,
                    maxLines = 3)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(v.status) })
                Button(onClick = onDownload) { Text("Tải ngay") }
            }
        }
    }
}
