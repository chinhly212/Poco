
package com.contentautomation.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.data.prefs.UserIdentity
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.*

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current

    // Lấy lại ID cũ hoặc tạo ID mới khi chạy lần đầu.
    val userId = remember {
        UserIdentity.getOrCreate(context)
    }

    var platform by remember { mutableStateOf("KUAISHOU") }
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }

    val accounts by vm.accounts.collectAsState()

    val normalizedName = name.trim()
    val normalizedIdentifier = identifier.trim()

    val isDuplicate = normalizedIdentifier.isNotEmpty() &&
        accounts.any {
            it.platform.equals(platform, ignoreCase = true) &&
                it.accountIdentifier.trim().equals(
                    normalizedIdentifier,
                    ignoreCase = true
                )
        }

    val isValid = normalizedName.isNotEmpty() &&
        normalizedIdentifier.isNotEmpty() &&
        !isDuplicate

    ScreenColumn(
        title = "Cài đặt",
        subtitle = "Quản lý tài khoản và cấu hình ứng dụng"
    ) {

        // USER ACCOUNT INFORMATION
        GlassCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(17.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GText(
                    "THÔNG TIN TÀI KHOẢN",
                    11.sp,
                    color = GlassColors.InkSoft,
                    weight = FontWeight.Bold
                )

                GText(
                    "ID người dùng",
                    13.sp,
                    weight = FontWeight.SemiBold
                )

                GText(
                    userId,
                    16.sp,
                    color = GlassColors.Blue,
                    weight = FontWeight.Bold
                )

                GlassButton(
                    text = "Sao chép ID người dùng",
                    onClick = {
                        val clipboard = context.getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager

                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "Poco User ID",
                                userId
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                GText(
                    "ID được tạo từ Android ID của thiết bị.",
                    11.sp,
                    color = GlassColors.InkSoft
                )
            }
        }

        // ACCOUNT SUMMARY
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(17.dp)) {
                GText(
                    "QUẢN LÝ TÀI KHOẢN",
                    11.sp,
                    color = GlassColors.InkSoft,
                    weight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        GText(
                            accounts.size.toString(),
                            28.sp,
                            color = GlassColors.Blue,
                            weight = FontWeight.Bold
                        )
                        GText(
                            "Tài khoản đã lưu",
                            12.sp,
                            color = GlassColors.InkSoft
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        GText(
                            accounts.count {
                                it.platform.equals("KUAISHOU", true)
                            }.toString(),
                            19.sp,
                            weight = FontWeight.SemiBold
                        )
                        GText(
                            "Kuaishou",
                            11.sp,
                            color = GlassColors.InkSoft
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        GText(
                            accounts.count {
                                it.platform.equals("DOUYIN", true)
                            }.toString(),
                            19.sp,
                            weight = FontWeight.SemiBold
                        )
                        GText(
                            "Douyin",
                            11.sp,
                            color = GlassColors.InkSoft
                        )
                    }
                }
            }
        }

        // ADD ACCOUNT
        GText(
            "Thêm tài khoản mới",
            17.sp,
            weight = FontWeight.SemiBold
        )

        GlassCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GText(
                    "NỀN TẢNG",
                    11.sp,
                    color = GlassColors.InkSoft,
                    weight = FontWeight.Bold
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = platform == "KUAISHOU",
                        onClick = { platform = "KUAISHOU" },
                        label = { Text("Kuaishou") }
                    )

                    FilterChip(
                        selected = platform == "DOUYIN",
                        onClick = { platform = "DOUYIN" },
                        label = { Text("Douyin") }
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên hiển thị") },
                    placeholder = { Text("Nhập tên tài khoản") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassColors.Ink,
                        unfocusedTextColor = GlassColors.Ink,
                        focusedBorderColor = GlassColors.Blue
                    )
                )

                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ID tài khoản") },
                    placeholder = { Text("Nhập ID tài khoản") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassColors.Ink,
                        unfocusedTextColor = GlassColors.Ink,
                        focusedBorderColor = GlassColors.Blue
                    )
                )

                if (isDuplicate) {
                    GText(
                        "ID này đã tồn tại trên nền tảng đã chọn.",
                        12.sp,
                        color = GlassColors.Red
                    )
                }

                Button(
                    onClick = {
                        if (isValid) {
                            vm.addAccount(
                                platform,
                                normalizedName,
                                normalizedIdentifier
                            )

                            name = ""
                            identifier = ""
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(49.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassColors.Blue
                    )
                ) {
                    Text(
                        "Thêm tài khoản",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // SAVED ACCOUNTS
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GText(
                "Tài khoản đã lưu",
                17.sp,
                weight = FontWeight.SemiBold
            )

            GText(
                "${accounts.size} tài khoản",
                11.sp,
                color = GlassColors.InkSoft
            )
        }

        if (accounts.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GText(
                        "Chưa có tài khoản",
                        14.sp,
                        weight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(5.dp))

                    GText(
                        "Thêm tài khoản nguồn để bắt đầu quản lý.",
                        12.sp,
                        color = GlassColors.InkSoft
                    )
                }
            }
        } else {
            accounts.forEach { account ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusDot(GlassColors.Green)

                            Spacer(Modifier.width(9.dp))

                            Column(Modifier.weight(1f)) {
                                GText(
                                    account.accountName,
                                    14.sp,
                                    weight = FontWeight.SemiBold
                                )

                                GText(
                                    account.platform,
                                    11.sp,
                                    color = GlassColors.Blue
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        GText(
                            "ID tài khoản",
                            10.sp,
                            color = GlassColors.InkSoft
                        )

                        Spacer(Modifier.height(3.dp))

                        GText(
                            account.accountIdentifier,
                            12.sp,
                            color = GlassColors.Ink
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }

        // APP INFORMATION
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                GText(
                    "THÔNG TIN ỨNG DỤNG",
                    11.sp,
                    color = GlassColors.InkSoft,
                    weight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                InfoRow("Tên ứng dụng", "Poco")
                InfoRow("Phiên bản giao diện", "Liquid Glass")
                InfoRow("Nền tảng", "Android")
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        GText(
            label,
            12.sp,
            color = GlassColors.InkSoft
        )

        GText(
            value,
            12.sp,
            weight = FontWeight.Medium
        )
    }
}
