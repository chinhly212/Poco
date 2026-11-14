package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.contentautomation.ui.MainViewModel

@Composable
fun SystemScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Tab Hệ thống (Đang phát triển)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        // Giả lập chấm đỏ
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(Color.Red, androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Không phát hiện video mới", color = Color.Gray)
        }
    }
}
