package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.data.db.entity.SourceAccount
import com.contentautomation.ui.components.*

@Composable
fun SettingsScreen(
    deviceId: String,
    accounts: List<SourceAccount>,
    appVersion: String,
    onCopyId: () -> Unit,
    onAddAccount: () -> Unit,
    tiktokClientKey: String,
    tiktokClientSecret: String,
    tiktokConnected: Boolean,
    onSaveTikTokCredentials: (String, String) -> Unit,
    onConnectTikTok: () -> Unit,
    onDisconnectTikTok: () -> Unit
) {
    val kuaishou = accounts.count { it.platform.equals("kuaishou", ignoreCase = true) }
    val douyin = accounts.count { it.platform.equals("douyin", ignoreCase = true) }
    var clientKey by remember(tiktokClientKey) { mutableStateOf(tiktokClientKey) }
    var clientSecret by remember(tiktokClientSecret) { mutableStateOf(tiktokClientSecret) }

    ScreenColumn {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GText("ID THIẾT BỊ", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GText(deviceId, 14.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    GlassButton("Sao chép", onClick = onCopyId)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                GText("TÀI KHOẢN NGUỒN", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                InfoRow("Tổng số tài khoản", accounts.size.toString())
                GlassDivider()
                InfoRow("Kuaishou", kuaishou.toString())
                GlassDivider()
                InfoRow("Douyin", douyin.toString())
            }
        }

        GlassButton("Thêm tài khoản", onClick = onAddAccount, primary = true, modifier = Modifier.fillMaxWidth())

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GText("KẾT NỐI TIKTOK", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = clientKey,
                    onValueChange = { clientKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("TikTok Client Key") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("TikTok Client Secret") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(
                    onClick = { onSaveTikTokCredentials(clientKey, clientSecret) },
                    enabled = clientKey.isNotBlank() && clientSecret.isNotBlank()
                ) { Text("Lưu thông tin TikTok") }
                Spacer(Modifier.height(6.dp))
                GText(
                    if (tiktokConnected) "Đã có phiên đăng nhập TikTok" else "Chưa đăng nhập TikTok",
                    12.sp,
                    color = if (tiktokConnected) GlassColors.Green else GlassColors.InkSoft
                )
                if (tiktokConnected) {
                    GlassButton("Ngắt kết nối TikTok", onClick = onDisconnectTikTok)
                } else {
                    GlassButton("Đăng nhập TikTok", onClick = onConnectTikTok, primary = true,
                        modifier = Modifier.fillMaxWidth())
                }
                GText(
                    "Cần tạo ứng dụng tại TikTok for Developers, bật quyền video.publish và khai báo Redirect URI trùng với cấu hình ứng dụng.",
                    11.sp, color = GlassColors.InkSoft
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                GText("THÔNG TIN ỨNG DỤNG", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                InfoRow("Phiên bản", appVersion)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        GText(label, 13.sp, modifier = Modifier.weight(1f))
        GText(value, 13.sp, weight = FontWeight.Bold, color = GlassColors.Blue)
    }
}
