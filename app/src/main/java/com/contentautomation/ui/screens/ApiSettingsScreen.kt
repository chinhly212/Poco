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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentautomation.ui.MainViewModel

@Composable
fun ApiSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val providers = listOf(
        "gemini" to "Google Gemini",
        "openrouter" to "OpenRouter",
        "deepseek" to "DeepSeek"
    )
    val inputs = remember { mutableStateMapOf<String, String>() }
    val connectionStates by vm.apiConnectionStates.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cấu hình API", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Quay lại") }
        }
        Text(
            "Nhập API key riêng cho từng nhà cung cấp. MuChi sẽ kiểm tra trực tiếp với máy chủ của nhà cung cấp trước khi lưu. Khóa được mã hóa trên điện thoại."
        )
        providers.forEach { (id, title) ->
            val state = connectionStates[id]
            val saved = vm.hasSavedApiKey(id)
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(if (saved) "Đã lưu khóa API mã hóa trên thiết bị" else "Chưa lưu API key")
                OutlinedTextField(
                    value = inputs[id].orEmpty(),
                    onValueChange = { inputs[id] = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = state?.checking != true
                )
                Button(
                    onClick = { vm.validateAndSaveApi(id, inputs[id].orEmpty()) },
                    enabled = !inputs[id].isNullOrBlank() && state?.checking != true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state?.checking == true) {
                        CircularProgressIndicator()
                    } else {
                        Text(if (saved) "Kiểm tra và cập nhật" else "Kiểm tra và lưu")
                    }
                }
                if (state?.message?.isNotBlank() == true) {
                    Text(
                        text = (if (state.connected) "KẾT NỐI THÀNH CÔNG: " else if (state.checking) "" else "KẾT NỐI THẤT BẠI: ") + state.message,
                        color = if (state.connected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
                if (saved) {
                    TextButton(onClick = { vm.removeApiKey(id) }) { Text("Xóa khóa đã lưu") }
                }
            }
        }
        Text(
            "Khi gửi tin nhắn trong tab AI, MuChi sẽ dùng các khóa đã lưu để gọi Gemini, OpenRouter hoặc DeepSeek qua HTTPS. Không đưa API key vào GitHub."
        )
    }
}
