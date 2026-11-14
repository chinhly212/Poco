package com.contentautomation.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.contentautomation.ContentAutomationApp
import com.contentautomation.service.AutomationForegroundService
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.auth.TikTokAuthActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ContentAutomationApp
    var scanInterval by remember { mutableStateOf("") }
    var defaultHashtag by remember { mutableStateOf("") }
    var translationKey by remember { mutableStateOf("") }
    var driveFolderId by remember { mutableStateOf("") }
    var tiktokClientKey by remember { mutableStateOf("") }
    var tiktokClientSecret by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scanInterval = app.prefs.scanIntervalSeconds.first().toString()
        defaultHashtag = app.prefs.defaultHashtag.first()
        translationKey = app.prefs.translationApiKey.first()
        driveFolderId = app.prefs.driveFolderId.first()
        tiktokClientKey = app.prefs.tiktokClientKey.first()
        tiktokClientSecret = app.prefs.tiktokClientSecret.first()
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cài đặt", style = MaterialTheme.typography.headlineMedium)
        SettingsField("Chu kỳ quét (giây)", scanInterval) { scanInterval = it }
        SettingsField("Hashtag mặc định", defaultHashtag) { defaultHashtag = it }
        SettingsField("Translation API Key", translationKey) { translationKey = it }
        SettingsField("Google Drive Folder ID", driveFolderId) { driveFolderId = it }
        SettingsField("TikTok Client Key", tiktokClientKey) { tiktokClientKey = it }
        SettingsField("TikTok Client Secret", tiktokClientSecret) { tiktokClientSecret = it }

        Button(onClick = {
            vm.saveSetting("scan_interval", scanInterval)
            vm.saveSetting("default_hashtag", defaultHashtag)
            vm.saveSetting("translation_api_key", translationKey)
            vm.saveSetting("drive_folder_id", driveFolderId)
            vm.saveSetting("tiktok_client_key", tiktokClientKey)
            vm.saveSetting("tiktok_client_secret", tiktokClientSecret)
        }, modifier = Modifier.fillMaxWidth()) { Text("Lưu cài đặt") }

        HorizontalDivider()
        Button(onClick = {
            ctx.startActivity(Intent(ctx, TikTokAuthActivity::class.java))
        }, modifier = Modifier.fillMaxWidth()) { Text("Đăng nhập TikTok") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { AutomationForegroundService.start(ctx) }) { Text("Bật quét 24/7") }
            OutlinedButton(onClick = { AutomationForegroundService.stop(ctx) }) { Text("Tắt quét 24/7") }
        }

        HorizontalDivider()
        Text("Quản lý tài khoản nguồn", style = MaterialTheme.typography.titleMedium)
        AddAccountRow(onAdd = { platform, name, id -> vm.addAccount(platform, name, id) })
    }
}

@Composable
private fun SettingsField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AddAccountRow(onAdd: (String, String, String) -> Unit) {
    var platform by remember { mutableStateOf("KUAISHOU") }
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("KUAISHOU", "DOUYIN").forEach {
                FilterChip(selected = platform == it, onClick = { platform = it }, label = { Text(it) })
            }
        }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên hiển thị") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = identifier, onValueChange = { identifier = it }, label = { Text("ID tài khoản nguồn") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (name.isNotBlank() && identifier.isNotBlank()) { onAdd(platform, name, identifier); name = ""; identifier = "" } }, modifier = Modifier.fillMaxWidth()) {
            Text("Thêm tài khoản")
        }
    }
}
