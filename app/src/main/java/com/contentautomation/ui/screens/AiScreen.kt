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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.contentautomation.video.AiVideoServerClient
import com.contentautomation.video.MediaStoreSaver
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AiScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = remember { AiVideoServerClient() }

    var serverStatus by remember { mutableStateOf("Đang kiểm tra máy chủ…") }
    var serverReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var videoUrl by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var chatReply by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    suspend fun refreshServer() {
        serverStatus = "Đang kiểm tra máy chủ…"
        try {
            serverStatus = server.checkHealth()
            serverReady = true
        } catch (error: Exception) {
            serverStatus = error.message ?: "Không kết nối được máy chủ."
            serverReady = false
        }
    }

    LaunchedEffect(Unit) { refreshServer() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("TRUNG TÂM AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Trò chuyện với trợ lý và tải video qua máy chủ. Các nguồn tải sẽ tự chuyển khi nguồn trước gặp lỗi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Máy chủ AI Video", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (serverReady) "Đã kết nối" else "Chưa kết nối",
                        color = if (serverReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Text(serverStatus, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Nguồn tải: GenDownload · WebDownloader · TikTok Worker · yt-dlp",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(enabled = !busy, onClick = { scope.launch { refreshServer() } }) {
                    Text("Kiểm tra lại máy chủ")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TẢI VIDEO", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Dán đường dẫn video") },
                    placeholder = { Text("https://…") },
                    minLines = 2,
                    maxLines = 4
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && videoUrl.trim().startsWith("http"),
                    onClick = {
                        scope.launch {
                            busy = true
                            result = "Đang yêu cầu máy chủ tìm nguồn tải…"
                            val temp = File(context.cacheDir, "muchiai_${System.currentTimeMillis()}.mp4")
                            try {
                                val downloaded = server.downloadVideo(videoUrl.trim(), temp)
                                val saved = MediaStoreSaver(context).saveVideoToGallery(
                                    temp,
                                    "MuChi_${System.currentTimeMillis()}.mp4"
                                )
                                if (saved == null) throw IllegalStateException("Tải được video nhưng không lưu được vào thư viện.")
                                result = "Đã tải và lưu video vào Movies/MuChi. Nguồn: ${downloaded.provider}. Dung lượng: ${downloaded.bytesWritten / 1024} KB."
                                videoUrl = ""
                            } catch (error: Exception) {
                                result = "Tải video thất bại: ${error.message ?: "Lỗi không xác định"}"
                            } finally {
                                temp.delete()
                                busy = false
                            }
                        }
                    }
                ) {
                    if (busy) CircularProgressIndicator() else Text("Tải video và lưu vào điện thoại")
                }
                if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TRỢ LÝ AI", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nhập câu hỏi") },
                    placeholder = { Text("Bạn cần trợ giúp điều gì?") },
                    minLines = 2,
                    maxLines = 5
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && message.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            chatReply = "Đang gửi yêu cầu…"
                            try {
                                chatReply = server.sendChat(message.trim()).message
                            } catch (error: Exception) {
                                chatReply = "Không thể kết nối AI: ${error.message ?: "Lỗi không xác định"}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Gửi cho AI") }
                if (chatReply.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(chatReply, modifier = Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
