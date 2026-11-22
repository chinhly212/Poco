package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.contentautomation.data.db.entity.AiChatMessage
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
    val app = context.applicationContext as ContentAutomationApp
    val chatDao = remember { app.database.aiChatDao() }
    val lines = remember { mutableStateListOf<AiChatLine>() }

    suspend fun appendLine(line: AiChatLine) {
        chatDao.insert(AiChatMessage(message = line.text, fromUser = line.fromUser))
        lines.add(line)
    }

    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var serverOnline by remember { mutableStateOf<Boolean?>(null) }
    var serverStatusLine by remember { mutableStateOf("Đang kết nối…") }

    LaunchedEffect(Unit) {
        val savedMessages = runCatching { chatDao.getAll() }.getOrElse { emptyList() }
        lines.clear()
        lines.addAll(savedMessages.map { AiChatLine(it.message, it.fromUser) })

        runCatching { server.getHealth() }
            .onSuccess { health ->
                serverOnline = true
                serverStatusLine = if (health.aiReady || vm.getSavedApiKeys().isNotEmpty()) {
                    "Trợ lý sẵn sàng"
                } else {
                    "Chưa kết nối mô hình AI"
                }
            }
            .onFailure {
                serverOnline = false
                serverStatusLine = "Trợ lý đang gặp sự cố kết nối"
            }

        if (lines.isEmpty()) {
            appendLine(
                AiChatLine(
                    "Xin chào chủ nhân! Tôi là trợ lý riêng của bạn. Tôi sẽ cố gắng trả lời tự nhiên, ngắn gọn và đúng điều bạn cần. Bạn có thể trò chuyện hoặc gửi đường dẫn video để tôi tìm và tải giúp.",
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Đang suy nghĩ…", style = MaterialTheme.typography.labelSmall)
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
                        input = ""
                        busy = true
                        scope.launch {
                            appendLine(AiChatLine(prompt, true))
                            val sourceUrl = Regex("https?://[^\\s<>]+")
                                .find(prompt)?.value?.trimEnd('.', ',', ')', ']', '}')
                            try {
                                val response = runCatching { server.sendChat(prompt, vm.getSavedApiKeys()) }
                                val serverReply = response.getOrNull()
                                val resolvedUrl = serverReply?.videoUrl
                                    ?.takeIf { serverReply.type == "video" && it.isNotBlank() }

                                if (sourceUrl != null) {
                                    var saved = false
                                    var serverFileDownloaded = false
                                    var serverError = serverReply?.message
                                        ?: response.exceptionOrNull()?.message
                                        ?: "Không nhận được kết quả từ máy chủ."
                                    val providerErrors = serverReply?.providerErrors.orEmpty()

                                    if (resolvedUrl != null) {
                                        // Keep provider names and download infrastructure out of the chat.
                                        appendLine(AiChatLine("Tôi đã tìm thấy video.", false))
                                        val temp = File(context.cacheDir, "muchiai_server_${System.currentTimeMillis()}.mp4")
                                        try {
                                            server.downloadResolvedVideo(
                                                resolvedUrl, temp, serverReply?.provider, serverReply?.title, sourceUrl
                                            )
                                            serverFileDownloaded = true
                                            MediaStoreSaver(context).saveVideoToGallery(
                                                temp, "MuChi_${System.currentTimeMillis()}.mp4"
                                            )
                                            appendLine(AiChatLine("Tôi đã tải và lưu video vào thư viện cho chủ nhân.", false))
                                            saved = true
                                        } catch (error: Exception) {
                                            serverError = error.message ?: "Không thể tải hoặc lưu video."
                                        } finally {
                                            temp.delete()
                                        }
                                    }

                                    if (!saved && !serverFileDownloaded) {
                                        val fallbackDir = File(context.cacheDir, "muchiai_fallback_${System.currentTimeMillis()}")
                                        try {
                                            val localFile = withContext(Dispatchers.IO) {
                                                fallbackDir.mkdirs()
                                                val mediaApp = context.applicationContext as ContentAutomationApp
                                                if (!mediaApp.awaitMediaEngines()) {
                                                    throw IllegalStateException(
                                                        ContentAutomationApp.engineInitializationError
                                                            ?: "Bộ tải dự phòng chưa sẵn sàng."
                                                    )
                                                }
                                                val template = File(fallbackDir, "video.%(ext)s").absolutePath
                                                val request = YoutubeDLRequest(sourceUrl).apply {
                                                    addOption("--no-playlist")
                                                    addOption("-f", "best[ext=mp4]/best")
                                                    addOption("--merge-output-format", "mp4")
                                                    addOption("--remux-video", "mp4")
                                                    addOption("-o", template)
                                                }
                                                YoutubeDL.getInstance().execute(request)
                                                fallbackDir.listFiles()
                                                    ?.filter { it.isFile && !it.name.endsWith(".part") && it.length() > 0L }
                                                    ?.maxByOrNull { it.length() }
                                                    ?: throw IllegalStateException("Bộ tải không tạo được tệp video.")
                                            }
                                            MediaStoreSaver(context).saveVideoToGallery(
                                                localFile, "MuChi_${System.currentTimeMillis()}.mp4"
                                            )
                                            appendLine(AiChatLine("Tôi đã tải và lưu video vào thư viện cho chủ nhân.", false))
                                            saved = true
                                        } catch (localError: Exception) {
                                            val diagnostics = buildString {
                                                append("Đường dẫn: ").append(sourceUrl).append("\\n")
                                                append("Lỗi xử lý từ máy chủ: ").append(serverError).append("\\n")
                                                if (providerErrors.isNotEmpty()) {
                                                    append("Nguồn dự phòng: ").append(providerErrors.joinToString(" | ")).append("\\n")
                                                }
                                                append("Lỗi bộ tải trên điện thoại: ")
                                                    .append(localError.message ?: localError.javaClass.simpleName)
                                            }
                                            vm.reportAiIssue("AI không tải được video", diagnostics)
                                            appendLine(
                                                AiChatLine(
                                                    "Xin lỗi chủ nhân, lần này tôi chưa tải được video. Tôi đã ghi nhận sự cố trong thông báo hệ thống để kiểm tra.",
                                                    false
                                                )
                                            )
                                        } finally {
                                            fallbackDir.deleteRecursively()
                                        }
                                    } else if (!saved && serverFileDownloaded) {
                                        vm.reportAiIssue(
                                            "AI tìm thấy video nhưng không lưu được",
                                            "Đường dẫn: $sourceUrl\\nLỗi lưu thư viện: $serverError"
                                        )
                                        appendLine(
                                            AiChatLine(
                                                "Tôi đã tìm thấy video nhưng chưa thể lưu vào thư viện. Tôi đã ghi nhận sự cố trong thông báo hệ thống.",
                                                false
                                            )
                                        )
                                    }
                                } else {
                                    val reply = serverReply?.message
                                    if (reply.isNullOrBlank()) {
                                        val diagnostic = response.exceptionOrNull()?.message ?: "Máy chủ không trả lời."
                                        vm.reportAiIssue("MuChi AI chưa thể phản hồi", diagnostic)
                                        appendLine(AiChatLine("Xin lỗi chủ nhân, hiện tại tôi chưa thể trả lời. Chủ nhân thử lại sau nhé.", false))
                                    } else if (
                                        reply.contains("GEMINI_API_KEY", ignoreCase = true) ||
                                        reply.contains("OPENROUTER_API_KEY", ignoreCase = true) ||
                                        reply.contains("DEEPSEEK_API_KEY", ignoreCase = true) ||
                                        reply.contains("Chưa cấu hình API", ignoreCase = true)
                                    ) {
                                        vm.reportAiIssue("MuChi AI chưa được kết nối", reply)
                                        appendLine(AiChatLine("Tôi chưa được kết nối với mô hình AI. Chủ nhân kiểm tra phần API trong Cài đặt giúp tôi nhé.", false))
                                    } else {
                                        appendLine(AiChatLine(reply, false))
                                    }
                                }
                            } catch (error: Exception) {
                                vm.reportAiIssue(
                                    "MuChi AI gặp sự cố",
                                    error.message ?: error.javaClass.simpleName
                                )
                                appendLine(
                                    AiChatLine(
                                        "Xin lỗi chủ nhân, tôi vừa gặp sự cố khi xử lý yêu cầu. Tôi đã ghi nhận lỗi trong thông báo hệ thống.",
                                        false
                                    )
                                )
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
