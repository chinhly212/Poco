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
    onOpenApi: () -> Unit
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
