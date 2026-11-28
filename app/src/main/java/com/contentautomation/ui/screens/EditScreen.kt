package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ui.components.GText
import com.contentautomation.ui.components.ScreenColumn

private val EditInk = Color(0xFF18181B)
private val EditMuted = Color(0xFF71717A)
private val EditBorder = Color(0xFFE4E4E7)

@Composable
fun EditScreen(
    selectedVideoName: String?,
    onImportVideo: () -> Unit
) {
    ScreenColumn {
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            GText("MUCHI EDIT", 11.sp, weight = FontWeight.Bold, color = EditMuted)
            GText("Biên tập video", 22.sp, weight = FontWeight.Bold, color = EditInk)
            GText(
                "Nhận diện lời nói, dịch, tạo phụ đề và lồng tiếng ngay trên điện thoại.",
                13.sp,
                color = EditMuted
            )
        }

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, EditBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onImportVideo)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF4F4F5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = EditInk)
            }
            GText(
                if (selectedVideoName == null) "Chọn video từ điện thoại" else "Chọn video khác",
                15.sp,
                weight = FontWeight.SemiBold,
                color = EditInk
            )
            GText(
                selectedVideoName ?: "MP4, MOV và các định dạng Android hỗ trợ",
                11.sp,
                color = EditMuted
            )
        }

        if (selectedVideoName != null) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, EditBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = EditInk)
                    GText("Video đã chọn", 13.sp, weight = FontWeight.SemiBold, color = EditInk,
                        modifier = Modifier.padding(start = 8.dp))
                }
                GText(selectedVideoName, 12.sp, color = EditMuted)
                GText(
                    "Bước tiếp theo: kết nối bộ nhận diện giọng nói/OCR, dịch phụ đề, lồng tiếng và timeline.",
                    11.sp,
                    color = EditMuted
                )
            }
        }
    }
}
