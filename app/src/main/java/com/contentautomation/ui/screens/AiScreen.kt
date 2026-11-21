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
import com.contentautomation.ContentAutomationApp
import com.contentautomation.ui.MainViewModel
import com.contentautomation.video.AiVideoServerClient
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.contentautomation.video.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                            val sourceUrl = Regex("https?://[^\\s<>]+")
                                .find(prompt)?.value?.trimEnd('.', ',', ')', ']', '}')
                            try {
                                val response = runCatching { server.sendChat(prompt) }
                                val serverReply = response.getOrNull()
                                val resolvedUrl = serverReply?.videoUrl
                                    ?.takeIf { serverReply.type == "video" && it.isNotBlank() }

                                if (sourceUrl != null) {
                                    var saved = false
                                    var serverError = serverReply?.message
                                        ?: response.exceptionOrNull()?.message
                                        ?: "Máy chủ chưa trả đường dẫn video."
                                    if (resolvedUrl != null) {
                                        lines.add(AiChatLine("Máy chủ đã tìm thấy video qua ${serverReply?.provider ?: "API"}. Đang tải…", false))
                                        val temp = File(context.cacheDir, "muchiai_server_${System.currentTimeMillis()}.mp4")
                                        try {
                                            val downloaded = server.downloadResolvedVideo(
                                                resolvedUrl, temp, serverReply?.provider, serverReply?.title
                                            )
                                            val uri = MediaStoreSaver(context).saveVideoToGallery(
                                                temp, "MuChi_${System.currentTimeMillis()}.mp4"
                                            )
                                            if (uri == null) throw IllegalStateException("Không lưu được video vào thư viện.")
                                            lines.add(AiChatLine(
                                                "Đã tải và lưu video vào Movies/MuChi. Nguồn: ${downloaded.provider}; ${downloaded.bytesWritten / 1024} KB.",
                                                false
                                            ))
                                            saved = true
                                        } catch (error: Exception) {
                                            serverError = error.message ?: "Không tải được file từ API."
                                        } finally {
                                            temp.delete()
                                        }
                                    }

                                    if (!saved) {
                                        lines.add(AiChatLine("API máy chủ chưa tải được. Đang thử bộ tải dự phòng trên điện thoại…", false))
                                        val fallbackDir = File(context.cacheDir, "muchiai_fallback_${System.currentTimeMillis()}")
                                        try {
                                            val localFile = withContext(Dispatchers.IO) {
                                                fallbackDir.mkdirs()
                                                val app = context.applicationContext as ContentAutomationApp
                                                if (!app.awaitMediaEngines()) {
                                                    throw IllegalStateException(
                                                        ContentAutomationApp.engineInitializationError
                                                            ?: "Bộ tải trên điện thoại chưa sẵn sàng."
                                                    )
                                                }
                                                val template = File(fallbackDir, "video.%(ext)s").absolutePath
                                                val request = YoutubeDLRequest(sourceUrl).apply {
                                                    addOption("--no-playlist")
                                                    addOption("-f", "best")
                                                    addOption("-o", template)
                                                }
                                                YoutubeDL.getInstance().execute(request)
                                                fallbackDir.listFiles()
                                                    ?.filter { it.isFile && !it.name.endsWith(".part") && it.length() > 0L }
                                                    ?.maxByOrNull { it.length() }
                                                    ?: throw IllegalStateException("yt-dlp không tạo được file video.")
                                            }
                                            val uri = MediaStoreSaver(context).saveVideoToGallery(
                                                localFile, "MuChi_${System.currentTimeMillis()}.mp4"
                                            )
                                            if (uri == null) throw IllegalStateException("Tải xong nhưng không lưu được vào thư viện.")
                                            lines.add(AiChatLine(
                                                "Đã tải và lưu video vào Movies/MuChi bằng bộ tải dự phòng trên điện thoại.",
                                                false
                                            ))
                                        } catch (localError: Exception) {
                                            lines.add(AiChatLine(
                                                "Chưa tải được video. Lỗi máy chủ: $serverError. Lỗi bộ tải trên điện thoại: ${localError.message ?: "không xác định"}.",
                                                false
                                            ))
                                        } finally {
                                            fallbackDir.deleteRecursively()
                                        }
                                    }
                                } else {
                                    val reply = serverReply?.message
                                        ?: "Không thể kết nối máy chủ AI: ${response.exceptionOrNull()?.message ?: "lỗi không xác định"}"
                                    lines.add(AiChatLine(reply, false))
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
