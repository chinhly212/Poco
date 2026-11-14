package com.contentautomation.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ContentAutomationApp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

@Composable
fun AuthRoot(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ContentAutomationApp
    val scope = rememberCoroutineScope()

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF6FAFF), Color(0xFFD9EAFF))))
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (isLoginMode) "Đăng Nhập" else "Đăng Ký", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B2447))
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Mật khẩu") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMsg != null) {
                Text(errorMsg!!, color = Color.Red, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMsg = "Vui lòng nhập đủ Email và Mật khẩu"
                        return@Button
                    }
                    isLoading = true
                    errorMsg = null
                    scope.launch {
                        try {
                            if (isLoginMode) {
                                app.supabase.auth.signInWith(Email) {
                                    this.email = email
                                    this.password = password
                                }
                            } else {
                                app.supabase.auth.signUpWith(Email) {
                                    this.email = email
                                    this.password = password
                                }
                            }
                            onLoginSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Có lỗi xảy ra"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text(if (isLoginMode) "Đăng Nhập" else "Tạo Tài Khoản", fontSize = 16.sp)
            }

            TextButton(onClick = { isLoginMode = !isLoginMode; errorMsg = null }) {
                Text(if (isLoginMode) "Chưa có tài khoản? Đăng ký ngay" else "Đã có tài khoản? Đăng nhập", color = Color(0xFF1E7BFF))
            }
        }
    }
}
