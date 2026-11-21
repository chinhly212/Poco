package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.contentautomation.ui.MainViewModel

@Composable
fun ApiSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val providers = listOf(
        "gemini" to "Google Gemini",
        "openrouter" to "OpenRouter",
        "groq" to "Groq"
    )
    val inputs = remember { mutableStateMapOf<String, String>() }
    var revealInfo by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cấu hình API", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Quay lại") }
        }
        Text("Nhập khóa API của từng nhà cung cấp. Ứng dụng sẽ gọi máy chủ để xác thực trước khi lưu. Khóa chỉ lưu mã hóa trên điện thoại.")
        providers.forEach { (id, title) ->
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(if (vm.hasSavedApiKey(id)) "Đã có khóa API được lưu mã hóa" else "Chưa kết nối")
                OutlinedTextField(
                    value = inputs[id].orEmpty(),
                    onValueChange = { inputs[id] = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(
                    onClick = { vm.validateAndSaveApi(id, inputs[id].orEmpty()) },
                    enabled = !inputs[id].isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Kiểm tra và lưu") }
                if (vm.hasSavedApiKey(id)) {
                    TextButton(onClick = { vm.removeApiKey(id) }) { Text("Xóa khóa đã lưu") }
                }
            }
        }
        Text("Khóa không được gửi vào GitHub hoặc ghi vào nhật ký ứng dụng. Khi trò chuyện, ứng dụng gửi khóa qua HTTPS tới máy chủ AI để gọi đúng nhà cung cấp.")
    }
}
