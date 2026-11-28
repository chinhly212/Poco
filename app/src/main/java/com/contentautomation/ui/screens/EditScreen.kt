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
import com.contentautomation.videoeditor.engine.AiSubtitleTranslator
import com.contentautomation.videoeditor.engine.AndroidTextToSpeechDubbingEngine
import com.contentautomation.videoeditor.engine.FfmpegDubbingExporter
import com.contentautomation.ui.MainViewModel
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
    vm: MainViewModel,
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
    var isTranslationRunning by remember { mutableStateOf(false) }
    var isDubbingRunning by remember { mutableStateOf(false) }
    var isExportRunning by remember { mutableStateOf(false) }
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

    val exportVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        if (uri != null) {
            val videoUri = selectedVideoUri
            if (videoUri == null) {
                statusMessage = "Hãy chọn video nguồn trước khi xuất."
            } else if (!isExportRunning) {
                isExportRunning = true
                statusMessage = "Đang ghép giọng đọc và đóng gói video MP4..."
                val exportCues = subtitles.toList()
                coroutineScope.launch {
                    val result = runCatching {
                        FfmpegDubbingExporter(context).export(videoUri, exportCues, uri)
                    }
                    result.onSuccess {
                        statusMessage = "Đã xuất video MP4 có giọng lồng tiếng."
                    }.onFailure {
                        statusMessage = "Xuất video thất bại: ${it.message ?: "Lỗi không xác định"}"
                    }
                    isExportRunning = false
                }
            }
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
            Button(
                onClick = {
                    if (isSpeechModelDownloading || hasSpeechModel) return@Button
                    isSpeechModelDownloading = true
                    statusMessage = "Đang tải mô hình STT tiếng Trung/Anh (~82 MB)..."
                    coroutineScope.launch {
                        val result = runCatching { speechModelManager.download() }
                        hasSpeechModel = speechModelManager.isInstalled()
                        statusMessage = if (result.isSuccess && hasSpeechModel) {
                            "Đã cài mô hình STT offline tiếng Trung/Anh."
                        } else {
                            "Tải mô hình STT thất bại: " + (result.exceptionOrNull()?.message ?: "Không xác minh được mô hình")
                        }
                        isSpeechModelDownloading = false
                    }
                },
                enabled = !hasSpeechModel && !isSpeechModelDownloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        hasSpeechModel -> "Đã cài mô hình STT tiếng Trung/Anh"
                        isSpeechModelDownloading -> "Đang tải mô hình STT..."
                        else -> "Tải mô hình STT tiếng Trung/Anh (~82 MB)"
                    }
                )
            }
            Button(
                onClick = {
                    val videoUri = selectedVideoUri ?: return@Button
                    if (isSpeechRunning) return@Button
                    isSpeechRunning = true
                    statusMessage = "Đang tách âm thanh và nhận diện lời nói..."
                    coroutineScope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                SherpaOnnxVideoSpeechRecognizer(speechModelManager).recognize(
                                    context = context,
                                    videoUri = videoUri,
                                    profile = processingProfile,
                                    sourceLanguage = sourceLanguage
                                )
                            }
                        }
                        result.onSuccess { found ->
                            val nextId = (subtitles.maxOfOrNull { it.id } ?: 0L) + 1L
                            subtitles.addAll(found.mapIndexed { index, item ->
                                SubtitleCue(
                                    id = nextId + index,
                                    startMs = item.startMs,
                                    endMs = item.endMs.coerceAtLeast(item.startMs + 500L),
                                    sourceText = item.text
                                )
                            })
                            statusMessage = if (found.isEmpty()) {
                                "STT hoàn tất: không nhận diện được lời nói."
                            } else {
                                "STT hoàn tất: " + found.size + " đoạn. Mốc thời gian hiện chia theo cửa sổ 10 giây."
                            }
                        }.onFailure {
                            statusMessage = "STT thất bại: " + (it.message ?: "Lỗi không xác định")
                        }
                        isSpeechRunning = false
                    }
                },
                enabled = selectedVideoUri != null && hasSpeechModel && !isSpeechRunning,
                colors = ButtonDefaults.buttonColors(containerColor = EditInk),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSpeechRunning) "Đang nhận diện lời nói..." else "Nhận diện lời nói trong video")
            }
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
            WorkflowRow("Dịch theo ngữ cảnh", "Dùng API AI đã lưu trong Cài đặt > API", Icons.Outlined.Language, status = "AI API")
            Button(
                onClick = {
                    if (isTranslationRunning || subtitles.isEmpty()) return@Button
                    isTranslationRunning = true
                    statusMessage = "Đang dịch phụ đề theo từng nhóm..."
                    val sourceCues = subtitles.toList()
                    coroutineScope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                AiSubtitleTranslator().translate(
                                    cues = sourceCues,
                                    sourceLanguage = sourceLanguage,
                                    targetLanguage = targetLanguage,
                                    apiKeys = vm.getSavedApiKeys()
                                )
                            }
                        }
                        result.onSuccess { translated ->
                            subtitles.clear()
                            subtitles.addAll(translated)
                            statusMessage = "Đã dịch ${translated.size} đoạn phụ đề sang ${targetLanguage}."
                        }.onFailure {
                            statusMessage = "Dịch phụ đề thất bại: ${it.message ?: "Lỗi không xác định"}"
                        }
                        isTranslationRunning = false
                    }
                },
                enabled = subtitles.isNotEmpty() && !isTranslationRunning,
                colors = ButtonDefaults.buttonColors(containerColor = EditInk),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isTranslationRunning) "Đang dịch phụ đề..." else "Dịch toàn bộ ${subtitles.size} đoạn")
            }
            GText("OCR offline hỗ trợ Latin/Việt và Trung. STT hiện dùng mô hình offline tiếng Trung/Anh; mốc thời gian chia theo cửa sổ 10 giây. Dịch cần API AI đã cấu hình.", 11.sp, color = EditMuted)
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
            WorkflowRow(
                "Tạo giọng đọc từ bản dịch",
                "Dùng giọng TTS đã cài trên Android, không cần tải mô hình",
                Icons.Outlined.Mic,
                status = if (subtitles.any { !it.voiceAudioPath.isNullOrBlank() }) "Đã tạo" else "Android TTS"
            )
            Button(
                onClick = {
                    if (isDubbingRunning || subtitles.none { it.translatedText.isNotBlank() }) return@Button
                    isDubbingRunning = true
                    statusMessage = "Đang tạo từng đoạn giọng đọc bằng Android TTS..."
                    val sourceCues = subtitles.toList()
                    coroutineScope.launch {
                        val result = runCatching {
                            AndroidTextToSpeechDubbingEngine(context).generateAll(
                                cues = sourceCues,
                                language = targetLanguage,
                                speed = 1f
                            )
                        }
                        result.onSuccess { dubbed ->
                            subtitles.clear()
                            subtitles.addAll(dubbed)
                            val count = dubbed.count { !it.voiceAudioPath.isNullOrBlank() }
                            statusMessage = "Đã tạo $count tệp giọng đọc. Âm thanh đang lưu trong bộ nhớ ứng dụng."
                        }.onFailure {
                            statusMessage = "Lồng tiếng thất bại: ${it.message ?: "Lỗi không xác định"}"
                        }
                        isDubbingRunning = false
                    }
                },
                enabled = subtitles.any { it.translatedText.isNotBlank() } && !isDubbingRunning,
                colors = ButtonDefaults.buttonColors(containerColor = EditInk),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isDubbingRunning) "Đang tạo giọng đọc..." else "Tạo giọng đọc cho phụ đề đã dịch")
            }
            WorkflowRow("Phối âm thanh", "Ghép giọng đọc với video và nhạc nền", Icons.Outlined.AudioFile)
            GText(
                "Mỗi câu được tạo thành một tệp WAV riêng. Hiện đã tạo giọng đọc; ghép vào video và xuất thành phẩm là bước engine tiếp theo.",
                11.sp,
                color = EditMuted
            )
        }

        SectionCard {
            SectionTitle("5. Timeline và lớp biên tập", Icons.Outlined.OndemandVideo)
            TimelineTrack("Video", "Hình ảnh gốc")
            TimelineTrack("Phụ đề", "${subtitles.size} đoạn")
            TimelineTrack("Lồng tiếng", "${subtitles.count { !it.voiceAudioPath.isNullOrBlank() }} tệp âm thanh")
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
            Button(
                onClick = { exportVideoLauncher.launch("muchi-dubbed.mp4") },
                enabled = selectedVideoUri != null &&
                    subtitles.any { !it.voiceAudioPath.isNullOrBlank() } &&
                    !isExportRunning,
                colors = ButtonDefaults.buttonColors(containerColor = EditInk),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isExportRunning) "Đang xuất video..." else "Xuất video MP4 có lồng tiếng")
            }
            GText(
                "Bản xuất giữ nguyên hình ảnh video, giảm âm thanh gốc xuống 22% và phối trộn giọng đọc theo mốc thời gian. Nếu video không có tiếng gốc, chỉ xuất track lồng tiếng.",
                11.sp,
                color = EditMuted
            )
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
private fun WorkflowRow(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, status: String = "Chưa nối") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = EditMuted, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GText(title, 12.sp, weight = FontWeight.Medium, color = EditInk)
            GText(detail, 10.sp, color = EditMuted)
        }
        GText(status, 10.sp, color = EditMuted)
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
