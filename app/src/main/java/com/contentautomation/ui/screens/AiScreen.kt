package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.AiChatMessage
import com.contentautomation.ui.MainViewModel
import com.contentautomation.video.AiVideoServerClient
import com.contentautomation.video.AiChatTurn
import com.contentautomation.video.MediaStoreSaver
import kotlinx.coroutines.launch

private data class AiChatLine(val text: String, val fromUser: Boolean)

private val AiBg = Color(0xFFF9FAFB)
private val AiInk = Color(0xFF18181B)
private val AiMuted = Color(0xFF71717A)
private val AiLine = Color(0xFFE4E4E7)

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

    LaunchedEffect(Unit) {
        val savedMessages = runCatching { chatDao.getAll() }.getOrElse { emptyList() }
        lines.clear()
        lines.addAll(savedMessages.map { AiChatLine(it.message, it.fromUser) })
        runCatching { server.getHealth() }
    }

    fun submitPrompt() {
        val prompt = input.trim()
        if (prompt.isEmpty() || busy) return
        input = ""
        busy = true
        scope.launch {
                            appendLine(AiChatLine(prompt, true))
                            val sourceUrls = Regex("""https?://[^\s<>]+""")
                                .findAll(prompt)
                                .map { it.value.trimEnd('.', ',', ')', ']', '}') }
                                .toList()
                            val sourceUrl = sourceUrls.firstOrNull()
                            try {
                                if (sourceUrls.isNotEmpty() &&
                                    (sourceUrls.size != 1 || sourceUrl == null || !isSupportedVideoLink(sourceUrl))
                                ) {
                                    appendLine(
                                        AiChatLine(
                                            "Đường dẫn này không được hỗ trợ. MuChi chỉ nhận một link video hợp lệ từ các nền tảng được hỗ trợ.",
                                            false
                                        )
                                    )
                                    return@launch
                                }
                                val chatHistory = lines.dropLast(1).takeLast(12).map {
                                    AiChatTurn(if (it.fromUser) "user" else "assistant", it.text)
                                }
                                val response = runCatching {
                                    server.sendChat(prompt, vm.getSavedApiKeys(), chatHistory)
                                }
                                val serverReply = response.getOrNull()
                                val resolvedUrl = serverReply?.videoUrl
                                    ?.takeIf { serverReply.type == "video" && it.isNotBlank() }

                                if (sourceUrl != null && serverReply?.type == "unsupported_link") {
                                    appendLine(AiChatLine(serverReply.message, false))
                                } else if (sourceUrl != null) {
                                    var saved = false
                                    var serverError = serverReply?.message
                                        ?: response.exceptionOrNull()?.message
                                        ?: "Không nhận được kết quả từ máy chủ."
                                    val providerErrors = serverReply?.providerErrors.orEmpty()

                                    if (resolvedUrl != null) {
                                        appendLine(AiChatLine("Tôi đã tìm thấy video.", false))
                                        try {
                                            server.downloadResolvedVideoToGallery(
                                                resolvedUrl,
                                                MediaStoreSaver(context),
                                                "MuChi_${System.currentTimeMillis()}.mp4",
                                                serverReply?.provider,
                                                serverReply?.title,
                                                sourceUrl
                                            )
                                            appendLine(AiChatLine("Tôi đã tải và lưu video vào thư viện cho chủ nhân.", false))
                                            saved = true
                                        } catch (error: Exception) {
                                            serverError = error.message ?: "Không thể tải hoặc lưu video."
                                        }
                                    }

                                    if (!saved) {
                                        val diagnostics = buildString {
                                            append("Đường dẫn: ").append(sourceUrl).append("\n")
                                            append("Lỗi xử lý từ máy chủ: ").append(serverError).append("\n")
                                            if (providerErrors.isNotEmpty()) {
                                                append("Nguồn dự phòng: ").append(providerErrors.joinToString(" | ")).append("\n")
                                            }
                                            append("Không sử dụng tải tạm trên điện thoại; video chưa được lưu vào thư viện.")
                                        }
                                        vm.reportAiIssue("AI không tải được video", diagnostics)
                                        appendLine(
                                            AiChatLine(
                                                "Xin lỗi chủ nhân, lần này tôi chưa tải được video. Tôi đã ghi nhận sự cố trong thông báo hệ thống để kiểm tra.",
                                                false
                                            )
                                        )
                                    }
                                } else {
                                    val reply = serverReply?.message
                                    if (serverReply?.type == "error" || reply.isNullOrBlank()) {
                                        val diagnostic = reply
                                            ?: response.exceptionOrNull()?.message
                                            ?: "Trợ lý không trả lời."
                                        vm.reportAiIssue("MuChi AI chưa thể phản hồi", diagnostic)
                                        appendLine(AiChatLine("Xin lỗi chủ nhân, hiện tại tôi chưa thể trả lời. Tôi đã ghi nhận sự cố trong thông báo hệ thống.", false))
                                    } else if (
                                        reply.contains("GEMINI_API_KEY", ignoreCase = true) ||
                                        reply.contains("OPENROUTER_API_KEY", ignoreCase = true) ||
                                        reply.contains("DEEPSEEK_API_KEY", ignoreCase = true) ||
                                        reply.contains("Chưa cấu hình API", ignoreCase = true)
                                    ) {
                                        vm.reportAiIssue("MuChi AI chưa được kết nối", reply)
                                        appendLine(AiChatLine("Xin lỗi chủ nhân, trợ lý đang gặp sự cố kết nối. Tôi đã ghi nhận trong thông báo hệ thống.", false))
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

    Column(
        modifier = Modifier.fillMaxSize().background(AiBg),
        verticalArrangement = Arrangement.Top
    ) {
        // Header 52dp, trắng, đường phân cách mảnh.
        Box(
            modifier = Modifier.fillMaxWidth().height(52.dp)
                .background(Color.White)
                .border(width = 0.dp, color = Color.Transparent)
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter).background(AiLine))
            Text(
                text = "MuChi AI",
                modifier = Modifier.align(Alignment.Center),
                color = AiInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    if (!busy) scope.launch {
                        runCatching { chatDao.deleteAll() }
                            .onSuccess { lines.clear() }
                            .onFailure {
                                vm.reportAiIssue("Không xóa được lịch sử chat", it.message ?: it.javaClass.simpleName)
                            }
                    }
                },
                enabled = !busy,
                modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Xóa lịch sử chat",
                    tint = AiMuted,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        if (lines.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(54.dp).clip(CircleShape)
                        .background(Color.White).border(1.dp, AiLine, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = AiInk, modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Tôi có thể giúp gì cho bạn hôm nay?",
                    color = AiInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                listOf("Tải video từ đường dẫn", "Kiểm tra trạng thái quét", "Giúp tôi sử dụng MuChi").forEach { suggestion ->
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, AiLine, RoundedCornerShape(8.dp))
                            .clickable(enabled = !busy) {
                                input = when (suggestion) {
                                    "Tải video từ đường dẫn" -> "Tôi muốn tải video từ đường dẫn: "
                                    "Kiểm tra trạng thái quét" -> "Kiểm tra trạng thái quét hiện tại"
                                    else -> "Hướng dẫn tôi sử dụng MuChi"
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(suggestion, color = AiInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                lines.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(if (line.fromUser) 0.88f else 0.94f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (line.fromUser) Color(0xFFF1F1F3) else Color.White)
                                .border(1.dp, AiLine, RoundedCornerShape(14.dp))
                                .padding(horizontal = 13.dp, vertical = 11.dp)
                        ) {
                            Text(line.text, color = AiInk, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
                if (busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = AiMuted)
                        Text("Đang suy nghĩ…", color = AiMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        // Thanh nhập liệu tích hợp nút gửi bên trong.
        Row(
            modifier = Modifier.fillMaxWidth().background(AiBg)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFD4D4D8), RoundedCornerShape(24.dp))
                    .padding(start = 16.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (input.isEmpty()) {
                        Text("Nhắn tin hoặc dán link...", color = Color(0xFFA1A1AA), fontSize = 13.sp, maxLines = 1)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = AiInk, fontSize = 14.sp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                        .background(if (input.isNotBlank() && !busy) AiInk else Color(0xFFA1A1AA))
                        .clickable(enabled = input.isNotBlank() && !busy) { submitPrompt() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Gửi tin nhắn", tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

private fun isSupportedVideoLink(rawUrl: String): Boolean {
    val uri = runCatching { android.net.Uri.parse(rawUrl) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme != "http" && scheme != "https") return false
    val host = uri.host?.lowercase()?.removeSuffix(".") ?: return false
    val path = uri.path.orEmpty().lowercase()
    val query = uri.encodedQuery.orEmpty().lowercase()
    fun hostIs(domain: String) = host == domain || host.endsWith(".$domain")

    return when {
        hostIs("tiktok.com") -> if (host == "vm.tiktok.com" || host == "vt.tiktok.com") {
            path.isNotBlank() && path != "/"
        } else {
            Regex("""/@[^/]+/video/[^/?]+|/t/[^/]+|/v/[^/]+""").containsMatchIn(path)
        }
        hostIs("douyin.com") || hostIs("iesdouyin.com") -> if (host == "v.douyin.com") {
            path.isNotBlank() && path != "/"
        } else {
            Regex("""/video/[^/?]+|/share/video/[^/?]+""").containsMatchIn(path)
        }
        hostIs("kuaishou.com") || hostIs("gifshow.com") -> if (host == "v.kuaishou.com" || host == "v.gifshow.com") {
            path.isNotBlank() && path != "/"
        } else {
            Regex("""/short-video/[^/?]+|/fw/photo/[^/?]+|/photo/[^/?]+|/s/[^/?]+|/share/[^/?]+""").containsMatchIn(path)
        }
        host == "youtu.be" || host == "fb.watch" || host == "redd.it" || host == "b23.tv" ->
            path.isNotBlank() && path != "/"
        hostIs("youtube.com") ->
            (path == "/watch" && query.split("&").any { it.startsWith("v=") }) ||
                Regex("""/(shorts|live|embed)/[^/?]+""").containsMatchIn(path)
        hostIs("instagram.com") -> Regex("""/(reel|p|tv)/[^/?]+""").containsMatchIn(path)
        hostIs("facebook.com") -> Regex("""/(watch|videos|reel|share/v|share/r)(/|$)""").containsMatchIn(path)
        hostIs("threads.net") -> Regex("""/@[^/]+/post/[^/?]+""").containsMatchIn(path)
        hostIs("x.com") || hostIs("twitter.com") -> Regex("""/[^/]+/status/[^/?]+""").containsMatchIn(path)
        hostIs("reddit.com") -> Regex("""/comments/[^/?]+""").containsMatchIn(path)
        hostIs("bilibili.com") -> Regex("""/video/(bv[0-9a-z]+|av[0-9]+)""").containsMatchIn(path)
        else -> false
    }
}
