package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.data.db.entity.SourceVideo
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.*

@Composable
fun NewVideosScreen(vm: MainViewModel) {
    val videos by vm.newVideos.collectAsState()

    ScreenColumn(title = "Video mới", subtitle = "Danh sách video vừa quét được") {
        if (videos.isEmpty()) {
            GText("Không có video mới nào", 15.sp, color = GlassColors.InkSoft)
        } else {
            videos.forEach { video ->  // Sửa lỗi ở đây: dùng video thay vì index
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        GText(video.title ?: video.externalVideoId, 15.sp, weight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        GText(video.descriptionZh ?: "Không có mô tả", 13.sp, color = GlassColors.InkSoft, maxLines = 2)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton("Tải ngay", onClick = { vm.downloadNow(video) })
                        }
                    }
                }
            }
        }
    }
}
