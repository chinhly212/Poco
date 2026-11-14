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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contentautomation.ui.AppNavHost
import com.contentautomation.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Đề nghị bỏ tối ưu pin (tuỳ chọn, giúp service chạy ổn định)
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")))
            }
        }

        setContent {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val app = applicationContext as ContentAutomationApp
            // Kiểm tra xem đã đăng nhập Supabase chưa
            var isLoggedIn by remember { mutableStateOf(app.supabase.auth.currentSessionOrNull() != null) }

            if (!isLoggedIn) {
                com.contentautomation.ui.auth.AuthRoot(
                    onLoginSuccess = { isLoggedIn = true }
                )
            } else {
                val vm: com.contentautomation.ui.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                com.contentautomation.ui.AppNavHost(vm)
            }
        }
    }
        }
