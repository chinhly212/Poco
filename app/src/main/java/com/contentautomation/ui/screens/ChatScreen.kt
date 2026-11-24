package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ui.components.GlassBackground
import com.contentautomation.ui.components.GlassCard
import com.contentautomation.ui.components.GText
import com.contentautomation.ui.components.GlassColors

@Composable
fun ChatScreen() {
    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GlassCard(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        tint = GlassColors.Blue,
                        modifier = Modifier.height(56.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    GText("POCO Chat", 22.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    GText(
                        "Nhắn tin cá nhân và trò chuyện nhóm sẽ được đồng bộ bằng tài khoản POCO hiện tại.",
                        13.sp,
                        color = GlassColors.InkSoft
                    )
                    Spacer(Modifier.height(14.dp))
                    GText(
                        "Chưa có cuộc trò chuyện",
                        14.sp,
                        weight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Giao diện Chat đã được thêm vào POCO. Phần gửi, nhận tin nhắn và danh bạ sẽ được kết nối ở bước tiếp theo.",
                        fontSize = 12.sp,
                        color = GlassColors.InkSoft
                    )
                }
            }
        }
    }
}
