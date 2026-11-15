package com.contentautomation.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ContentAutomationApp
import com.contentautomation.ui.components.*
import dev.chrisbanes.haze.HazeState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

@Composable
fun AuthRoot(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ContentAutomationApp
    val scope = rememberCoroutineScope()

    // FIX: Khởi tạo HazeState cho màn hình đăng nhập
    val hazeState = remember { HazeState() }

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // FIX: Cung cấp LocalHazeState trước khi sử dụng GlassCard
    CompositionLocalProvider(LocalHazeState provides hazeState) {

        Box(Modifier.fillMaxSize()) {

            // 1. Nền kính mờ
            GlassBackground(Modifier.fillMaxSize())

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .systemBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // 2. Thẻ kính chứa Form
                GlassCard(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        GText(
                            text = if (isLoginMode) "Đăng Nhập" else "Đăng Ký",
                            size = 28.sp,
                            weight = FontWeight.Bold,
                            color = GlassColors.Ink
                        )

                        Spacer(Modifier.height(8.dp))

                        GText(
                            text = if (isLoginMode)
                                "Chào mừng trở lại hệ thống"
                            else
                                "Tạo tài khoản quản trị mới",
                            size = 13.sp,
                            color = GlassColors.InkSoft
                        )

                        Spacer(Modifier.height(24.dp))

                        // Ô nhập Email
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Địa chỉ Gmail") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GlassColors.Ink,
                                unfocusedTextColor = GlassColors.Ink,
                                focusedBorderColor = GlassColors.Blue,
                                unfocusedBorderColor = GlassColors.InkSoft,
                                focusedLabelColor = GlassColors.Blue
                            )
                        )

                        Spacer(Modifier.height(16.dp))

                        // Ô nhập Mật khẩu
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Mật khẩu") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GlassColors.Ink,
                                unfocusedTextColor = GlassColors.Ink,
                                focusedBorderColor = GlassColors.Blue,
                                unfocusedBorderColor = GlassColors.InkSoft,
                                focusedLabelColor = GlassColors.Blue
                            )
                        )

                        // Thông báo lỗi
                        AnimatedVisibility(visible = errorMsg != null) {
                            Text(
                                text = errorMsg ?: "",
                                color = GlassColors.Red,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }

                        // Thông báo thành công
                        AnimatedVisibility(visible = successMsg != null) {
                            Text(
                                text = successMsg ?: "",
                                color = GlassColors.Green,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        // Nút Đăng nhập / Đăng ký
                        Button(
                            onClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMsg = "Vui lòng nhập đầy đủ Email và Mật khẩu"
                                    successMsg = null
                                    return@Button
                                }

                                isLoading = true
                                errorMsg = null
                                successMsg = null

                                scope.launch {
                                    try {
                                        if (isLoginMode) {
                                            // ĐĂNG NHẬP
                                            app.supabase.auth.signInWith(Email) {
                                                this.email = email.trim()
                                                this.password = password
                                            }

                                            onLoginSuccess()

                                        } else {
                                            // ĐĂNG KÝ
                                            app.supabase.auth.signUpWith(Email) {
                                                this.email = email.trim()
                                                this.password = password
                                            }

                                            if (
                                                app.supabase.auth.currentSessionOrNull() == null
                                            ) {
                                                successMsg =
                                                    "Đăng ký thành công! Vui lòng kiểm tra Gmail để xác nhận."

                                                isLoginMode = true
                                            } else {
                                                onLoginSuccess()
                                            }
                                        }

                                    } catch (e: Exception) {
                                        errorMsg = e.message
                                            ?: "Đã có lỗi xảy ra, vui lòng thử lại."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !isLoading,
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GlassColors.Blue
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = if (isLoginMode)
                                        "Đăng Nhập"
                                    else
                                        "Tạo Tài Khoản",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Chuyển đổi Đăng nhập / Đăng ký
                        TextButton(
                            onClick = {
                                isLoginMode = !isLoginMode
                                errorMsg = null
                                successMsg = null
                            },
                            enabled = !isLoading
                        ) {
                            Text(
                                text = if (isLoginMode)
                                    "Chưa có tài khoản? Đăng ký ngay"
                                else
                                    "Đã có tài khoản? Đăng nhập",
                                color = GlassColors.Blue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
