package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentautomation.data.db.entity.ProcessedVideo
import com.contentautomation.ui.MainViewModel

@Composable
fun PendingVideosScreen(vm: MainViewModel) {
    val videos by vm.readyVideos.collectAsStateWithLifecycle()
    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Không có video chờ đăng")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(videos) { pv -> PendingRow(vm, pv) }
    }
}

@Composable
private fun PendingRow(vm: MainViewModel, pv: ProcessedVideo) {
    var desc by remember(pv.id) { mutableStateOf(pv.descriptionVi.orEmpty()) }
    var tagsText by remember(pv.id) { mutableStateOf(pv.hashtags.joinToString(" ")) }
    var privacy by remember(pv.id) { mutableStateOf("SELF_ONLY") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Video ID: ${pv.sourceVideoId.take(8)}…", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = desc, onValueChange = { desc = it },
                label = { Text("Mô tả tiếng Việt") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tagsText, onValueChange = { tagsText = it },
                label = { Text("Hashtags") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SELF_ONLY", "MUTUAL_FOLLOW_FRIENDS", "PUBLIC_TO_EVERYONE").forEach {
                    FilterChip(selected = privacy == it, onClick = { privacy = it }, label = { Text(it) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    vm.updateProcessedDescription(pv, desc, tagsText.split(" ").filter { it.isNotBlank() })
                }) { Text("Lưu") }
                Button(onClick = {
                    vm.publishNow(pv, privacy, desc)
                }) { Text("Đăng TikTok") }
            }
        }
    }
}
