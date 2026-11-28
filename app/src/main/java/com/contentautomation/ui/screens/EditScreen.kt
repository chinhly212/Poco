package com.contentautomation.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.contentautomation.ui.components.GText
import com.contentautomation.ui.components.ScreenColumn
import com.contentautomation.videoeditor.domain.SrtCodec
import com.contentautomation.videoeditor.domain.SubtitleCue
import com.contentautomation.videoeditor.engine.AndroidDeviceResourceMonitor
import com.contentautomation.videoeditor.engine.MlKitVideoTextRecognizer
import com.contentautomation.videoeditor.engine.SherpaOnnxChineseModelManager
import com.contentautomation.videoeditor.engine.SherpaOnnxVideoSpeechRecognizer
import com.contentautomation.videoeditor.domain.ProcessingProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EditInk = Color(0xFF18181B)
private val EditMuted = Color(0xFF71717A)
private val EditBorder = Color(0xFFE4E4E7)
private val EditSurface = Color.White

@Composable
fun EditScreen(
    selectedVideoName: String?,
    selectedVideoUri: Uri?,
    onImportVideo: () -> Unit
) {
    val context = LocalContext.current
    val subtitles = remember { mutableStateListOf<SubtitleCue>() }
    var sourceLanguage by remember { mutableStateOf("Tự động nhận diện") }
    var targetLanguage by remember { mutableStateOf("Tiếng Việt") }
    var subtitleDraft by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Chọn video để bắt đầu dự án.") }
    val resourceMonitor = remember { AndroidDeviceResourceMonitor() }
    val resourceSnapshot = remember(context) { resourceMonitor.snapshot(context) }
    val recommendedProfile = remember(resourceSnapshot) { resourceMonitor.recommendedProfile(resourceSnapshot) }
    var selectedProfile by remember(recommendedProfile) {
        mutableStateOf(
            when (recommendedProfile) {
                com.contentautomation.videoeditor.domain.ProcessingProfile.LOW_MEMORY -> "Tiết kiệm"
                com.contentautomation.videoeditor.domain.ProcessingProfile.BALANCED -> "Cân bằng"
                com.contentautomation.videoeditor.domain.ProcessingProfile.PERFORMANCE -> "Hiệu năng"
            }
        )
    }

        val coroutineScope = rememberCoroutineScope()
    var isOcrRunning by remember { mutableStateOf(false) }
    var isSpeechModelDownloading by remember { mutableStateOf(false) }
    var isSpeechRunning by remember { mutableStateOf(false) }
    val speechModelManager = remember(context) { SherpaOnnxChineseModelManager(context) }
    var hasSpeechModel by remember(context) { mutableStateOf(speechModelManager.isInstalled()) }
    val processingProfile = when (selectedProfile) {
        "Tiết kiệm" -> ProcessingProfile.LOW_MEMORY
        "Hiệu năng" -> ProcessingProfile.PERFORMANCE
        else -> ProcessingProfile.BALANCED
    }

    val importSrtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Không đọc được tệp phụ đề.")
            }.onSuccess { raw ->
                val parsed = SrtCodec.parse(raw)
                subtitles.clear()
                subtitles.addAll(parsed)
                statusMessage = "Đã nhập ${parsed.size} đoạn phụ đề."
            }.onFailure { statusMessage = it.message ?: "Không thể nhập phụ đề." }
        }
    }
    val exportSrtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(SrtCodec.format(subtitles.toList(), useTranslation = true))
                } ?: error("Không thể tạo tệp phụ đề.")
            }.onSuccess { statusMessage = "Đã xuất tệp SRT." }
                .onFailure { statusMessage = it.message ?: "Xuất SRT thất bại." }
        }
    }

    ScreenColumn {
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            GText("MUCHI EDIT", 11.sp, weight = FontWeight.Bold, color = EditMuted)
            GText("Biên tập video", 22.sp, weight = FontWeight.Bold, color = EditInk)
            GText("Nhận diện • Dịch • Phụ đề • Lồng tiếng • Xuất video", 12.sp, color = EditMuted)
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GText("1. Video nguồn", 15.sp, weight = FontWeight.SemiBold, color = EditInk)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onImportVideo) { Text(if (selectedVideoName == null) "Chọn video" else "Đổi video") }
            }
            if (selectedVideoUri != null) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(9.dp)).background(Color.Black),
                    factory = { viewContext ->
                        VideoView(viewContext).apply {
                            setVideoURI(selectedVideoUri)
                            setMediaController(MediaController(viewContext).also { it.setAnchorView(this) })
                            setOnPreparedListener { player -> player.isLooping = false }
                        }
                    },
                    update = { view -> if (view.tag != selectedVideoUri.toString()) {
                        view.tag = selectedVideoUri.toString()
                        view.setVideoURI(selectedVideoUri)
                    } }
                )
                GText(selectedVideoName ?: "Video đã chọn", 12.sp, color = EditMuted)
            } else {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(9.dp)).background(Color(0xFFF4F4F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        androidx.compose.material3.Icon(Icons.Outlined.Movie, contentDescription = null, tint = EditMuted, modifier = Modifier.size(30.dp))
                        GText("Chưa có video", 13.sp, color = EditMuted)
                    }
                }
            }
        }

        SectionCard {
            SectionTitle("2. Nhận diện và dịch", Icons.Outlined.Language)
            OutlinedTextField(
                value = sourceLanguage,
                onValueChange = { sourceLanguage = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ngôn ngữ nguồn") },
                singleLine = true
            )
            OutlinedTextField(
                value = targetLanguage,
                onValueChange = { targetLanguage = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ngôn ngữ đích") },
                singleLine = true
            )
            WorkflowRow("Nhận diện lời nói (STT)", "Tạo văn bản kèm mốc thời gian", Icons.Outlined.Mic)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                WorkflowRow("Nhận diện chữ trong hình (OCR)", "ML Kit chạy trực tiếp trên điện thoại", Icons.Outlined.TextFields)
                Button(
                    onClick = {
                        val videoUri = selectedVideoUri
                        if (videoUri == null || isOcrRunning) return@Button
                        isOcrRunning = true
                        statusMessage = "Đang quét chữ trong video..."
                        coroutineScope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    MlKitVideoTextRecognizer().recognize(
                                        context = context,
                                        videoUri = videoUri,
                                        sampleIntervalMs = 1500L,
                                        profile = processingProfile,
                                        sourceLanguage = sourceLanguage
                                    )
                                }
                            }
                            result.onSuccess { found ->
                                val nextId = (subtitles.maxOfOrNull { it.id } ?: 0L) + 1L
                                val converted = found.mapIndexed { index, item ->
                                    SubtitleCue(
                                        id = nextId + index,
                                        startMs = item.startMs,
                                        endMs = item.endMs.coerceAtLeast(item.startMs + 500L),
                                        sourceText = item.text
                                    )
                                }
                                subtitles.addAll(converted)
                                statusMessage = if (converted.isEmpty()) {
                                    "OCR hoàn tất: không tìm thấy chữ trong video."
                                } else {
                                    "OCR hoàn tất: đã nhận diện ${converted.size} đoạn chữ."
                                }
                            }.onFailure {
                                statusMessage = "OCR thất bại: ${it.message ?: "Lỗi không xác định"}"
                            }
                            isOcrRunning = false
                        }
                    },
                    enabled = selectedVideoUri != null && !isOcrRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = EditInk),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isOcrRunning) "Đang nhận diện..." else "Quét chữ trong video")
                }
            }
            WorkflowRow("Dịch theo ngữ cảnh", "Giữ cách xưng hô và mạch hội thoại", Icons.Outlined.Language)
            GText("OCR offline: chế độ tự động chạy mô hình Latin và Trung; có thể chọn riêng Tiếng Trung hoặc Tiếng Việt. STT, dịch và lồng tiếng sẽ được kết nối tiếp.", 11.sp, color = EditMuted)
        }

        SectionCard {
            SectionTitle("3. Phụ đề", Icons.Outlined.ClosedCaption)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { importSrtLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) }) {
                    Text("Nhập SRT")
                }
                OutlinedButton(onClick = { exportSrtLauncher.launch("muchi-subtitles.srt") }, enabled = subtitles.isNotEmpty()) {
                    Text("Xuất SRT")
                }
            }
            OutlinedTextField(
                value = subtitleDraft,
                onValueChange = { subtitleDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Thêm nội dung phụ đề thủ công") },
                minLines = 2
            )
            Button(
                onClick = {
                    val nextId = (subtitles.maxOfOrNull { it.id } ?: 0L) + 1L
                    subtitles.add(SubtitleCue(nextId, (nextId - 1L) * 3000L, nextId * 3000L, subtitleDraft.trim()))
                    subtitleDraft = ""
                    statusMessage = "Đã thêm đoạn phụ đề thủ công."
                },
                enabled = subtitleDraft.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EditInk)
            ) { Text("Thêm đoạn phụ đề") }
            if (subtitles.isEmpty()) {
                GText("Chưa có đoạn phụ đề. Bạn có thể nhập tệp SRT hoặc thêm nội dung thủ công.", 12.sp, color = EditMuted)
            } else {
                subtitles.forEachIndexed { index, cue ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GText("#${index + 1}  ${SrtCodec.formatTimestamp(cue.startMs)}", 10.sp, weight = FontWeight.SemiBold, color = EditMuted)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Xóa",
                                modifier = Modifier.clickable { subtitles.remove(cue) }.padding(5.dp),
                                color = EditMuted,
                                fontSize = 11.sp
                            )
                        }
                        OutlinedTextField(
                            value = cue.sourceText,
                            onValueChange = { value -> subtitles[index] = cue.copy(sourceText = value) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Văn bản gốc") },
                            minLines = 1
                        )
                        OutlinedTextField(
                            value = cue.translatedText,
                            onValueChange = { value -> subtitles[index] = cue.copy(translatedText = value) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Bản dịch") },
                            minLines = 1
                        )
                    }
                    if (index != subtitles.lastIndex) Divider(color = EditBorder)
                }
            }
        }

        SectionCard {
            SectionTitle("4. Lồng tiếng và âm thanh", Icons.Outlined.AudioFile)
            WorkflowRow("Tạo giọng đọc từ bản dịch", "Chọn giọng, tốc độ và tạo từng đoạn", Icons.Outlined.Mic)
            WorkflowRow("Phối âm thanh", "Giọng đọc, âm thanh gốc và nhạc nền", Icons.Outlined.AudioFile)
            GText("Piper / TTS Android và bộ phối âm sẽ được kết nối ở phần engine.", 11.sp, color = EditMuted)
        }

        SectionCard {
            SectionTitle("5. Timeline và lớp biên tập", Icons.Outlined.OndemandVideo)
            TimelineTrack("Video", "Hình ảnh gốc")
            TimelineTrack("Phụ đề", "${subtitles.size} đoạn")
            TimelineTrack("Lồng tiếng", "Chưa có âm thanh")
            TimelineTrack("Văn bản / Logo / Blur / Mask", "Các lớp phủ")
        }

        SectionCard {
            SectionTitle("6. Cấu hình xử lý và xuất", Icons.Outlined.Download)
            GText("Mức sử dụng tài nguyên thiết bị", 12.sp, color = EditMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Tiết kiệm", "Cân bằng", "Hiệu năng").forEach { profile ->
                    val selected = selectedProfile == profile
                    Text(
                        profile,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (selected) EditInk else Color(0xFFF4F4F5))
                            .clickable { selectedProfile = profile }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        color = if (selected) Color.White else EditInk,
                        fontSize = 11.sp
                    )
                }
            }
            GText(
                "RAM còn trống: ${resourceSnapshot.availableMemoryBytes / 1_073_741_824.0} GB / ${resourceSnapshot.totalMemoryBytes / 1_073_741_824.0} GB • CPU: ${resourceSnapshot.cpuCoreCount} lõi",
                11.sp,
                color = EditMuted
            )
            GText(
                "Hồ sơ đề xuất: $selectedProfile • Nhiệt độ hệ thống mức ${resourceSnapshot.thermalStatus}. GPU/NPU chỉ được dùng khi engine xác nhận hỗ trợ.",
                11.sp,
                color = EditMuted
            )
            OutlinedButton(onClick = { statusMessage = "Chưa thể xuất video: pipeline nhận diện, lồng tiếng và render chưa được kết nối." }, modifier = Modifier.fillMaxWidth()) {
                Text("Xuất video (chờ tích hợp engine)")
            }
        }
        GText(statusMessage, 11.sp, color = EditMuted)
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(EditSurface).border(1.dp, EditBorder, RoundedCornerShape(12.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = EditInk, modifier = Modifier.size(19.dp))
        GText(title, 15.sp, weight = FontWeight.SemiBold, color = EditInk, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun WorkflowRow(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = EditMuted, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GText(title, 12.sp, weight = FontWeight.Medium, color = EditInk)
            GText(detail, 10.sp, color = EditMuted)
        }
        GText("Chưa nối", 10.sp, color = EditMuted)
    }
}

@Composable
private fun TimelineTrack(name: String, detail: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Color(0xFFF4F4F5)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        GText(name, 12.sp, weight = FontWeight.Medium, color = EditInk)
        Spacer(Modifier.weight(1f))
        GText(detail, 10.sp, color = EditMuted)
    }
}
