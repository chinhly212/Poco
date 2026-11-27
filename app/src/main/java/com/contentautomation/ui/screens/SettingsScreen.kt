package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.contentautomation.data.db.entity.SourceAccount
import com.contentautomation.ui.DriveUiState
import com.contentautomation.ui.models.StorageInfo
import java.util.Locale

private val SettingsBg = Color(0xFFF4F4F5)
private val SettingsWhite = Color(0xFFFFFFFF)
private val SettingsBorder = Color(0xFFE4E4E7)
private val SettingsInk = Color(0xFF18181B)
private val SettingsMuted = Color(0xFF71717A)
private val SettingsGreen = Color(0xFF16A34A)
private val SettingsAmber = Color(0xFFD97706)
private val SettingsRed = Color(0xFFDC2626)

@Composable
fun SettingsScreen(
    deviceId: String,
    accounts: List<SourceAccount>,
    appVersion: String,
    storage: StorageInfo,
    drive: DriveUiState,
    onConnectDrive: () -> Unit,
    onRefreshDrive: () -> Unit,
    onPickUpload: () -> Unit,
    onDeleteDriveFile: (String) -> Unit,
    onDownloadDriveFile: (String, String) -> Unit,
    onDisconnectDrive: () -> Unit,
    onCopyId: () -> Unit,
    onAddAccount: () -> Unit,
    onSetAccountEnabled: (String, Boolean) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onOpenApi: () -> Unit,
    onLogout: () -> Unit,
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
    val douyin = accounts.count { it.platform.equals("douyin", true) }
    val kuaishou = accounts.count { it.platform.equals("kuaishou", true) }
    var confirmLogout by remember { mutableStateOf(false) }
    val shortId = remember(deviceId) {
        if (deviceId.length > 18) "${deviceId.take(8)}...${deviceId.takeLast(7)}" else deviceId
    }

    Column(
        Modifier.fillMaxSize().background(SettingsBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsGroup("Tài khoản & Nguồn dữ liệu") {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tổng: ${accounts.size}  •  Douyin: $douyin  •  Kuaishou: $kuaishou",
                    modifier = Modifier.weight(1f), fontSize = 12.sp, color = SettingsInk, fontWeight = FontWeight.Medium, maxLines = 1)
                SmallAction("+ Thêm", onAddAccount)
            }
            SettingsDivider()
            SessionRow("Douyin", douyinSession, onLoginDouyin, onLogoutDouyin)
            SettingsDivider()
            SessionRow("Kuaishou", kuaishouSession, onLoginKuaishou, onLogoutKuaishou)
            SettingsDivider()
            Row(
                Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onOpenApi),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cấu hình API mô hình AI", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk, fontWeight = FontWeight.Medium)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SettingsMuted)
            }
            if (accounts.isNotEmpty()) {
                SettingsDivider()
                Text("Tài khoản nguồn", modifier = Modifier.padding(top = 10.dp, bottom = 4.dp), fontSize = 11.sp, color = SettingsMuted, fontWeight = FontWeight.SemiBold)
                accounts.forEachIndexed { index, account ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(account.accountName.ifBlank { account.accountIdentifier }, fontSize = 13.sp, color = SettingsInk, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${account.platform.uppercase(Locale.ROOT)} · ${account.accountIdentifier}", fontSize = 11.sp, color = SettingsMuted, maxLines = 1)
                        }
                        Switch(checked = account.enabled, onCheckedChange = { onSetAccountEnabled(account.id, it) })
                        TextButton(onClick = { onDeleteAccount(account.id) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("Xóa", color = SettingsRed, fontSize = 12.sp)
                        }
                    }
                    if (index != accounts.lastIndex) SettingsDivider()
                }
            }
        }

        SettingsGroup("Lưu trữ & Đám mây") {
            val used = String.format(Locale.getDefault(), "%.1f", storage.usedGb)
            val total = String.format(Locale.getDefault(), "%.1f", storage.totalGb)
            val free = String.format(Locale.getDefault(), "%.1f", storage.freeGb)
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bộ nhớ điện thoại", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk, fontWeight = FontWeight.SemiBold)
                    Text("$used / $total GB", fontSize = 13.sp, color = SettingsInk, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxWidth().padding(top = 9.dp).height(5.dp).clip(RoundedCornerShape(8.dp)).background(SettingsBorder)) {
                    Box(Modifier.fillMaxWidth(storage.fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(SettingsAmber))
                }
                Text("Còn trống $free GB • Tự lưu vào thư mục Movies", modifier = Modifier.padding(top = 7.dp), fontSize = 11.sp, color = SettingsMuted)
            }
            SettingsDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Google Drive", fontSize = 14.sp, color = SettingsInk, fontWeight = FontWeight.Medium)
                    Text(
                        if (drive.connected) drive.account?.email ?: "Đã kết nối" else "Phiên hết hạn",
                        fontSize = 11.sp, color = if (drive.connected) SettingsGreen else SettingsAmber, maxLines = 1
                    )
                }
                if (drive.connected) {
                    SmallAction("Làm mới", onRefreshDrive)
                    Spacer(Modifier.width(6.dp))
                    SmallAction("Tải lên", onPickUpload)
                    Spacer(Modifier.width(6.dp))
                    SmallAction("Ngắt", onDisconnectDrive)
                } else SmallAction("Kết nối lại", onConnectDrive)
            }
            if (drive.loading) Text("Đang đồng bộ…", fontSize = 11.sp, color = SettingsMuted, modifier = Modifier.padding(bottom = 6.dp))
            drive.message?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = SettingsAmber, modifier = Modifier.padding(bottom = 6.dp)) }
            if (drive.connected) {
                SettingsDivider()
                Text("Tệp trên Drive · ${drive.files.size}", modifier = Modifier.padding(top = 10.dp, bottom = 4.dp), fontSize = 11.sp, color = SettingsMuted, fontWeight = FontWeight.SemiBold)
                if (drive.files.isEmpty()) Text("Chưa có tệp", fontSize = 12.sp, color = SettingsMuted, modifier = Modifier.padding(vertical = 8.dp))
                drive.files.forEach { file ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(file.name, fontSize = 12.sp, color = SettingsInk, maxLines = 1)
                            Text(formatSettingsBytes(file.sizeBytes), fontSize = 10.sp, color = SettingsMuted)
                        }
                        TextButton(onClick = { onDownloadDriveFile(file.id, file.name) }, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("Tải về", fontSize = 11.sp) }
                        TextButton(onClick = { onDeleteDriveFile(file.id) }, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("Xóa", fontSize = 11.sp, color = SettingsRed) }
                    }
                }
            }
        }

        SettingsGroup("Thiết bị & Quyền") {
            Row(Modifier.fillMaxWidth().height(50.dp).clickable(onClick = onRequestNotifications), verticalAlignment = Alignment.CenterVertically) {
                Text("Thông báo ứng dụng", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk)
                Text(if (notificationsGranted) "Đã bật" else "Chưa bật", fontSize = 12.sp, color = if (notificationsGranted) SettingsGreen else SettingsAmber, fontWeight = FontWeight.SemiBold)
            }
            if (storagePermissionRequired) {
                SettingsDivider()
                Row(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onRequestStorage), verticalAlignment = Alignment.CenterVertically) {
                    Text("Quyền lưu trữ", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk)
                    Text(if (storageGranted) "Đã bật" else "Cấp quyền", fontSize = 12.sp, color = if (storageGranted) SettingsGreen else SettingsAmber)
                }
            }
            SettingsDivider()
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ID Thiết bị", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk)
                Text("MC-${shortId.removePrefix("MC-")}", fontSize = 12.sp, color = SettingsMuted)
                IconButton(onClick = onCopyId, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Sao chép ID", tint = SettingsMuted, modifier = Modifier.size(17.dp))
                }
            }
        }

        SettingsGroup("Hệ thống") {
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Phiên bản ứng dụng", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk)
                Text(appVersion, fontSize = 13.sp, color = SettingsMuted, fontWeight = FontWeight.SemiBold)
            }
        }

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SettingsWhite)
                .border(1.dp, SettingsBorder, RoundedCornerShape(12.dp))
                .clickable { confirmLogout = true }.padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Đăng xuất tài khoản", fontSize = 14.sp, color = SettingsRed, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Đăng xuất MuChi?") },
            text = { Text("Bạn sẽ quay về màn hình đăng nhập. Dữ liệu tài khoản trên máy chủ không bị xóa.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Đăng xuất") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Hủy") } }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SettingsWhite)
            .border(1.dp, SettingsBorder, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp)
    ) {
        Text(title, modifier = Modifier.padding(top = 13.dp, bottom = 4.dp), fontSize = 11.sp, color = SettingsMuted, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SessionRow(
    platform: String,
    saved: Boolean,
    onLogin: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Phiên $platform", fontSize = 13.sp, color = SettingsInk, fontWeight = FontWeight.Medium)
            Text(if (saved) "Đã lưu phiên" else "Chưa đăng nhập", fontSize = 11.sp,
                color = if (saved) SettingsGreen else SettingsMuted)
        }
        if (saved) {
            TextButton(onClick = onLogin, contentPadding = PaddingValues(horizontal = 5.dp)) {
                Text("Đăng nhập lại", fontSize = 11.sp, color = SettingsInk)
            }
            TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 5.dp)) {
                Text("Xóa", fontSize = 11.sp, color = SettingsRed)
            }
        } else SmallAction("Đăng nhập", onLogin)
    }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsBorder))
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(SettingsBg).clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) { Text(label, fontSize = 11.sp, color = SettingsInk, fontWeight = FontWeight.SemiBold) }
}

private fun formatSettingsBytes(value: Long): String {
    if (value < 1024L) return "$value B"
    val units = listOf("KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var index = -1
    do { amount /= 1024.0; index++ } while (amount >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", amount, units[index])
}
