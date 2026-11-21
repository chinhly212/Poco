package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.GText
import com.contentautomation.ui.components.GlassCard
import com.contentautomation.ui.components.ScreenColumn

@Composable
fun AiScreen(vm: MainViewModel) {
    var apiKey by remember { mutableStateOf("") }
    var sourceLanguage by remember { mutableStateOf("auto") }
    var targetLanguage by remember { mutableStateOf("vi") }
    var inputText by remember { mutableStateOf("") }
    val result by vm.translationResult.collectAsStateWithLifecycle()
    val loading by vm.translationLoading.collectAsStateWithLifecycle()
    val error by vm.translationError.collectAsStateWithLifecycle()

    ScreenColumn {
        GText("AI & DỊCH THUẬT", 19.sp)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                GText("CẤU HÌNH GOOGLE TRANSLATE", 11.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Google Cloud Translation API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveTranslationApiKey(apiKey) },
                    enabled = apiKey.isNotBlank()
                ) { Text("Lưu khóa mã hóa trên thiết bị") }
                Text(
                    "Cần bật Cloud Translation API và thanh toán/billing trong Google Cloud. Khóa được lưu trong vùng mã hóa của ứng dụng.",
                    fontSize = 12.sp
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                GText("DỊCH VĂN BẢN", 11.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sourceLanguage,
                    onValueChange = { sourceLanguage = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ngôn ngữ nguồn (auto, zh, en...)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = targetLanguage,
                    onValueChange = { targetLanguage = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ngôn ngữ đích (vi, zh, en...)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    label = { Text("Văn bản cần dịch") },
                    minLines = 4
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.translateText(sourceLanguage.trim(), targetLanguage.trim(), inputText) },
                    enabled = inputText.isNotBlank() && targetLanguage.isNotBlank() && !loading
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Dịch ngay")
                }
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, fontSize = 12.sp)
                }
                if (result.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    GText("KẾT QUẢ", 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(result, fontSize = 15.sp)
                }
            }
        }
    }
}
