package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
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
    onSetAccountEnabled: (String, Boolean) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onOpenApi: () -> Unit,
    notificationsGranted: Boolean,
    storagePermissionRequired: Boolean,
    storageGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestStorage: () -> Unit,
    douyinSession: Boolean,
    kuaishouSession: Boolean,
    onLoginDouyin: () -> Unit,
    onLoginKuaishou: () -> Unit,
    onLogoutDouyin: () -> Unit,
    onLogoutKuaishou: () -> Unit
) {
    val kuaishou = accounts.count { it.platform.equals("kuaishou", ignoreCase = true) }
    val douyin = accounts.count { it.platform.equals("douyin", ignoreCase = true) }

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

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GText("QUYỀN ỨNG DỤNG", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        GText("Thông báo Android", 13.sp, weight = FontWeight.SemiBold)
                        GText(if (notificationsGranted) "Đã cấp quyền" else "Chưa cấp quyền", 11.sp,
                            color = if (notificationsGranted) GlassColors.Green else GlassColors.Amber)
                    }
                    GlassButton(if (notificationsGranted) "Đã bật" else "Cấp quyền", onClick = onRequestNotifications)
                }
                GlassDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        GText("Lưu video và tệp", 13.sp, weight = FontWeight.SemiBold)
                        GText(
                            if (!storagePermissionRequired) "Android 10 trở lên: dùng bộ chọn/MediaStore, không cần quyền truy cập toàn bộ bộ nhớ."
                            else if (storageGranted) "Đã cấp quyền lưu trữ cho Android 9 trở xuống."
                            else "Cần quyền lưu trữ trên Android 9 trở xuống.",
                            11.sp, color = GlassColors.InkSoft
                        )
                    }
                    if (storagePermissionRequired) {
                        GlassButton(if (storageGranted) "Đã bật" else "Cấp quyền", onClick = onRequestStorage)
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GText("ĐĂNG NHẬP NỀN TẢNG", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        GText("Douyin", 13.sp, weight = FontWeight.SemiBold)
                        GText(if (douyinSession) "Đã lưu phiên đăng nhập" else "Chưa đăng nhập", 11.sp,
                            color = if (douyinSession) GlassColors.Green else GlassColors.Amber)
                    }
                    GlassButton(if (douyinSession) "Đăng nhập lại" else "Đăng nhập", onClick = onLoginDouyin)
                    if (douyinSession) TextButton(onClick = onLogoutDouyin) { GText("Thoát", 11.sp, color = GlassColors.Red) }
                }
                GlassDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        GText("Kuaishou", 13.sp, weight = FontWeight.SemiBold)
                        GText(if (kuaishouSession) "Đã lưu phiên đăng nhập" else "Chưa đăng nhập", 11.sp,
                            color = if (kuaishouSession) GlassColors.Green else GlassColors.Amber)
                    }
                    GlassButton(if (kuaishouSession) "Đăng nhập lại" else "Đăng nhập", onClick = onLoginKuaishou)
                    if (kuaishouSession) TextButton(onClick = onLogoutKuaishou) { GText("Thoát", 11.sp, color = GlassColors.Red) }
                }
            }
        }

        GlassButton("API mô hình AI", onClick = onOpenApi, primary = true, modifier = Modifier.fillMaxWidth())
        GlassButton("Thêm tài khoản", onClick = onAddAccount, modifier = Modifier.fillMaxWidth())

        if (accounts.isNotEmpty()) {
            GText("QUẢN LÝ TÀI KHOẢN", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
            accounts.forEach { account ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                GText(account.accountName.ifBlank { account.accountIdentifier }, 14.sp, weight = FontWeight.Bold)
                                GText(account.platform.uppercase(), 11.sp, color = GlassColors.Blue)
                            }
                            Switch(
                                checked = account.enabled,
                                onCheckedChange = { onSetAccountEnabled(account.id, it) }
                            )
                        }
                        GText(account.accountIdentifier, 12.sp, color = GlassColors.InkSoft)
                        if (account.profileVerifiedAt != null) {
                            account.profileAvatarUrl?.takeIf { it.startsWith("https://") }?.let { avatar ->
                                coil.compose.AsyncImage(
                                    model = avatar,
                                    contentDescription = "Ảnh đại diện tài khoản",
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                            account.profileDescription?.takeIf { it.isNotBlank() }?.let {
                                GText(it, 11.sp, color = GlassColors.InkSoft)
                            }
                            account.profileVideoCount?.let {
                                GText("Số video: $it", 11.sp, color = GlassColors.Blue)
                            }
                            GText("Đã xác minh hồ sơ", 10.sp, color = GlassColors.Green)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onDeleteAccount(account.id) }) {
                                GText("Xóa tài khoản", 12.sp, color = GlassColors.Red)
                            }
                        }
                    }
                }
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
