package com.contentautomation.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ContentAutomationApp
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.auth.TikTokAuthActivity
import com.contentautomation.ui.components.*
import kotlinx.coroutines.flow.first

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ContentAutomationApp
    
    var scanInterval by remember { mutableStateOf("") }
    var defaultHashtag by remember { mutableStateOf("") }

    // Chỉ load các cấu hình cơ bản, bỏ các API Key vì đã chuyển lên Server
    LaunchedEffect(Unit) {
        scanInterval = app.prefs.scanIntervalSeconds.first().toString()
        defaultHashtag = app.prefs.defaultHashtag.first()
    }

    ScreenColumn(title = "Cài đặt", subtitle = "Cấu hình hệ thống & Tài khoản") {
        
        // --- CẤU HÌNH CHUNG ---
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GText("Cấu hình chung", 16.sp, weight = FontWeight.SemiBold)
                
                OutlinedTextField(
                    value = scanInterval, onValueChange = { scanInterval = it },
                    label = { Text("Chu kỳ quét (giây)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassColors.Ink,
                        unfocusedTextColor = GlassColors.Ink,
                        focusedBorderColor = GlassColors.Blue,
                        unfocusedBorderColor = GlassColors.InkSoft
                    )
                )
                
                OutlinedTextField(
                    value = defaultHashtag, onValueChange = { defaultHashtag = it },
                    label = { Text("Hashtag mặc định") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassColors.Ink,
                        unfocusedTextColor = GlassColors.Ink,
                        focusedBorderColor = GlassColors.Blue,
                        unfocusedBorderColor = GlassColors.InkSoft
                    )
                )

                GlassButton("Lưu cài đặt", onClick = {
                    vm.saveSetting("scan_interval", scanInterval)
                    vm.saveSetting("default_hashtag", defaultHashtag)
                }, modifier = Modifier.fillMaxWidth())
            }
        }

        // --- TÀI KHOẢN TIKTOK ---
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GText("Kết nối TikTok", 16.sp, weight = FontWeight.SemiBold)
                GText("Đăng nhập để cấp quyền đăng video tự động", 13.sp, color = GlassColors.InkSoft)
                GlassButton("Đăng nhập TikTok", onClick = {
                    ctx.startActivity(Intent(ctx, TikTokAuthActivity::class.java))
                }, modifier = Modifier.fillMaxWidth())
            }
        }

        // --- QUẢN LÝ TÀI KHOẢN NGUỒN ---
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GText("Thêm kênh nguồn", 16.sp, weight = FontWeight.SemiBold)
                AddAccountRow(onAdd = { platform, name, id -> vm.addAccount(platform, name, id) })
            }
        }
    }
}

@Composable
private fun AddAccountRow(onAdd: (String, String, String) -> Unit) {
    var platform by remember { mutableStateOf("KUAISHOU") }
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("KUAISHOU", "DOUYIN").forEach {
                FilterChip(
                    selected = platform == it, 
                    onClick = { platform = it }, 
                    label = { Text(it) }
                )
            }
        }
        
        OutlinedTextField(
            value = name, onValueChange = { name = it }, 
            label = { Text("Tên hiển thị") }, 
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GlassColors.Ink,
                unfocusedTextColor = GlassColors.Ink,
                focusedBorderColor = GlassColors.Blue,
                unfocusedBorderColor = GlassColors.InkSoft
            )
        )
        
        OutlinedTextField(
            value = identifier, onValueChange = { identifier = it }, 
            label = { Text("ID tài khoản nguồn") }, 
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GlassColors.Ink,
                unfocusedTextColor = GlassColors.Ink,
                focusedBorderColor = GlassColors.Blue,
                unfocusedBorderColor = GlassColors.InkSoft
            )
        )
        
        GlassButton("Thêm tài khoản", onClick = { 
            if (name.isNotBlank() && identifier.isNotBlank()) { 
                onAdd(platform, name, identifier)
                name = ""; identifier = "" 
            } 
        }, modifier = Modifier.fillMaxWidth())
    }
}
