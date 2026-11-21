package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.contentautomation.ui.MainViewModel
import com.contentautomation.video.AiVideoServerClient
import com.contentautomation.video.MediaStoreSaver
import kotlinx.coroutines.launch
import java.io.File

private data class AiChatLine(val text: String, val fromUser: Boolean)

@Composable
fun AiScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = remember { AiVideoServerClient() }
    val lines = remember { mutableStateListOf<AiChatLine>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var serverOnline by remember { mutableStateOf<Boolean?>(null) }
    var serverStatusLine by remember { mutableStateOf("Đang kết nối máy chủ…") }

    LaunchedEffect(Unit) {
        runCatching { server.getHealth() }
            .onSuccess { health ->
                serverOnline = true
                serverStatusLine = if (health.aiReady) {
                    "Đang hoạt động · mô hình: ${health.configuredProviders.joinToString()}"
                } else {
                    "Máy chủ đã kết nối · chưa cấu hình API mô hình AI"
                }
            }
            .onFailure { error ->
                serverOnline = false
                serverStatusLine = error.message ?: "Không kết nối được máy chủ"
            }
        if (lines.isEmpty()) {
            lines.add(
                AiChatLine(
                    "Xin chào! Tôi là trợ lý MuChi. Bạn có thể trò chuyện, hỏi AI hoặc gửi link video để tôi tự tìm nguồn tải và lưu vào điện thoại.",
                    false
                )
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("MuChi AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    serverStatusLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (serverOnline == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (line.fromUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        Text(
                            line.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (line.fromUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("MuChi AI đang xử lý…", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nhắn tin hoặc dán link video…") },
                maxLines = 4,
                enabled = !busy
            )
            IconButton(
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    val prompt = input.trim()
                    if (prompt.isNotEmpty()) {
                        lines.add(AiChatLine(prompt, true))
                        input = ""
                        busy = true
                        scope.launch {
                            try {
                                val response = server.sendChat(prompt)
                                if (response.type == "video" && !response.videoUrl.isNullOrBlank()) {
                                    lines.add(AiChatLine("Đã tìm thấy video qua ${response.provider ?: "máy chủ"}. Đang tải và lưu vào điện thoại…", false))
                                    val temp = File(context.cacheDir, "muchiai_${System.currentTimeMillis()}.mp4")
                                    try {
                                        val downloaded = server.downloadResolvedVideo(
                                            response.videoUrl,
                                            temp,
                                            response.provider,
                                            response.title
                                        )
                                        val saved = MediaStoreSaver(context).saveVideoToGallery(
                                            temp,
                                            "MuChi_${System.currentTimeMillis()}.mp4"
                                        )
                                        if (saved == null) throw IllegalStateException("Không lưu được video vào thư viện.")
                                        lines.add(
                                            AiChatLine(
                                                "Đã tải và lưu video vào Movies/MuChi. Nguồn: ${downloaded.provider}. Dung lượng: ${downloaded.bytesWritten / 1024} KB.",
                                                false
                                            )
                                        )
                                    } finally {
                                        temp.delete()
                                    }
                                } else {
                                    lines.add(AiChatLine(response.message, false))
                                }
                            } catch (error: Exception) {
                                lines.add(AiChatLine("Không xử lý được yêu cầu: ${error.message ?: "Lỗi không xác định"}", false))
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Gửi")
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
