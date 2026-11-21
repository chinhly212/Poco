package com.contentautomation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.contentautomation.data.remote.SupabaseAuth
import kotlinx.coroutines.launch

@Composable
fun SupabaseLoginScreen(
    auth: SupabaseAuth,
    onSignedIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (isSignUp) "Tạo tài khoản Mu Chi" else "Đăng nhập Mu Chi")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mật khẩu") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    message = ""
                    val result = if (isSignUp) auth.signUp(email.trim(), password)
                    else auth.signIn(email.trim(), password)
                    busy = false
                    result.onSuccess { session ->
                        if (session != null) onSignedIn()
                        else message = "Đã tạo tài khoản. Hãy kiểm tra email để xác nhận, rồi đăng nhập."
                    }.onFailure {
                        message = it.message ?: "Có lỗi xác thực."
                    }
                }
            },
            enabled = !busy && email.contains("@") && password.length >= 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator()
            else Text(if (isSignUp) "Đăng ký" else "Đăng nhập")
        }
        TextButton(onClick = {
            isSignUp = !isSignUp
            message = ""
        }) {
            Text(if (isSignUp) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký")
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message)
        }
    }
}
