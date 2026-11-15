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

private const val SUPABASE_ACCOUNT_DOMAIN =
    "lkiigrddlancxisbczln.supabase.co"

private fun accountIdToAuthEmail(accountId: String): String =
    "${accountId.lowercase()}@$SUPABASE_ACCOUNT_DOMAIN"

@Composable
fun AuthRoot(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ContentAutomationApp
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }

    var isLoginMode by remember { mutableStateOf(true) }
    var accountId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize()) {
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
                                "Tạo tài khoản Poco mới",
                            size = 13.sp,
                            color = GlassColors.InkSoft
                        )

                        Spacer(Modifier.height(24.dp))

                        OutlinedTextField(
                            value = accountId,
                            onValueChange = { value ->
                                accountId = value.lowercase()
                                    .filter {
                                        it in 'a'..'z' ||
                                        it in '0'..'9' ||
                                        it == '_'
                                    }
                                    .take(24)
                            },
                            label = { Text("ID tài khoản") },
                            supportingText = {
                                Text("3–24 ký tự: chữ thường, số hoặc dấu _")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii
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

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Mật khẩu") },
                            visualTransformation =
                                PasswordVisualTransformation(),
                            singleLine = true,
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

                        AnimatedVisibility(errorMsg != null) {
                            Text(
                                text = errorMsg ?: "",
                                color = GlassColors.Red,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }

                        AnimatedVisibility(successMsg != null) {
                            Text(
                                text = successMsg ?: "",
                                color = GlassColors.Green,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                val normalizedId =
                                    accountId.trim().lowercase()

                                if (!normalizedId.matches(
                                    Regex("^[a-z0-9_]{3,24}$")
                                )) {
                                    errorMsg =
                                        "ID phải có 3–24 ký tự, chỉ gồm chữ thường, số và dấu _."
                                    successMsg = null
                                    return@Button
                                }

                                if (password.isBlank()) {
                                    errorMsg = "Vui lòng nhập mật khẩu."
                                    successMsg = null
                                    return@Button
                                }

                                if (!isLoginMode && password.length < 8) {
                                    errorMsg =
                                        "Mật khẩu đăng ký phải có ít nhất 8 ký tự."
                                    successMsg = null
                                    return@Button
                                }

                                isLoading = true
                                errorMsg = null
                                successMsg = null

                                scope.launch {
                                    try {
                                        val authEmail =
                                            accountIdToAuthEmail(normalizedId)

                                        if (isLoginMode) {
                                            app.supabase.auth.signInWith(Email) {
                                                this.email = authEmail
                                                this.password = password
                                            }
                                            onLoginSuccess()
                                        } else {
                                            app.supabase.auth.signUpWith(Email) {
                                                this.email = authEmail
                                                this.password = password
                                            }

                                            if (
                                                app.supabase.auth
                                                    .currentSessionOrNull() == null
                                            ) {
                                                errorMsg =
                                                    "Supabase vẫn yêu cầu xác minh email. Hãy tắt Confirm email trong Authentication → Providers → Email rồi thử lại."
                                            } else {
                                                onLoginSuccess()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        errorMsg = e.message
                                            ?: "Không thể xác thực tài khoản. Vui lòng thử lại."
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
