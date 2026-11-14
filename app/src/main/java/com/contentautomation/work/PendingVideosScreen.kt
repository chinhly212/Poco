package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.data.db.entity.ProcessedVideo
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.*

@Composable
fun PendingVideosScreen(vm: MainViewModel) {
    val videos by vm.readyVideos.collectAsState()

    ScreenColumn(title = "Chờ đăng", subtitle = "Chỉnh sửa nội dung trước khi đăng TikTok") {
        if (videos.isEmpty()) {
            GText("Không có video nào đang chờ", 15.sp, color = GlassColors.InkSoft)
        } else {
            videos.forEach { pv -> // Sửa lỗi: dùng pv thay vì index
                var desc by remember(pv.id) { mutableStateOf(pv.descriptionVi.orEmpty()) }
                var tagsText by remember(pv.id) { mutableStateOf(pv.hashtags.joinToString(" ")) }
                var privacy by remember(pv.id) { mutableStateOf("SELF_ONLY") }

                GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GText("ID: ${pv.sourceVideoId.take(8)}", 14.sp, weight = FontWeight.Bold)
                        
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
                            listOf("SELF_ONLY", "PUBLIC_TO_EVERYONE").forEach {
                                FilterChip(selected = privacy == it, onClick = { privacy = it }, label = { Text(it) })
                            }
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton("Lưu", onClick = {
                                vm.updateProcessedDescription(pv, desc, tagsText.split(" ").filter { it.isNotBlank() })
                            })
                            GlassButton("Đăng TikTok", onClick = {
                                vm.publishNow(pv, privacy, desc)
                            })
                        }
                    }
                }
            }
        }
    }
}
