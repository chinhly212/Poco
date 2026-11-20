
package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.*

@Composable
fun SettingsScreen(vm: MainViewModel) {
    var platform by remember { mutableStateOf("KUAISHOU") }
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }

    val accounts by vm.accounts.collectAsState()

    val normalizedName = name.trim()
    val normalizedIdentifier = identifier.trim()

    val isDuplicate = accounts.any {
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
        subtitle = "Quản lý tài khoản nguồn"
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GText(
                    "Thêm kênh nguồn mới",
                    16.sp,
                    weight = FontWeight.SemiBold
                )

                Row(
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
                    label = { Text("Tên hiển thị") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassColors.Ink,
                        unfocusedTextColor = GlassColors.Ink
                    )
                )

                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("ID tài khoản (VD: 3xabc...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassColors.Ink,
                        unfocusedTextColor = GlassColors.Ink
                    )
                )

                if (isDuplicate) {
                    Text(
                        text = "Tài khoản này đã được thêm trước đó.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassColors.Blue
                    )
                ) {
                    Text("Thêm tài khoản")
                }
            }
        }
    }
}
