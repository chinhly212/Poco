package com.contentautomation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contentautomation.ui.AppNavHost
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.auth.AuthRoot
import io.github.jan.supabase.auth.auth

class MainActivity : ComponentActivity() {
    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }

        setContent {
            // BIẾN LƯU LỖI
            var crashLog by remember { mutableStateOf<String?>(null) }

            // NẾU CÓ LỖI -> HIỆN LÊN MÀN HÌNH
            if (crashLog != null) {
                Column(
                    Modifier.fillMaxSize().background(Color.White).padding(16.dp).verticalScroll(rememberScrollState())
                ) {
                    Text("🚨 APP BỊ LỖI (CHỤP MÀN HÌNH NÀY GỬI ADMIN):", color = Color.Red, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(crashLog!!, color = Color.Black, fontSize = 12.sp)
                }
                return@setContent
            }

            // CHẠY APP BÌNH THƯỜNG
            try {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        val app = applicationContext as ContentAutomationApp
                        
                        var isLoggedIn by remember { 
                            mutableStateOf(
                                try {
                                    app.supabase.auth.currentSessionOrNull() != null
                                } catch (e: Exception) {
                                    false
                                }
                            ) 
                        }

                        if (!isLoggedIn) {
                            AuthRoot(onLoginSuccess = { isLoggedIn = true })
                        } else {
                            val vm: MainViewModel = viewModel()
                            AppNavHost(vm, onLogout = { isLoggedIn = false })
                        }
                    }
                }
            } catch (e: Exception) {
                crashLog = e.stackTraceToString()
            }
        }
    }
}
