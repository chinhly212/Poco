package com.contentautomation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.contentautomation.ui.components.*
import com.contentautomation.ui.screens.*
import dev.chrisbanes.haze.HazeState

@Composable
fun AppNavHost(vm: MainViewModel) {
    val nav = rememberNavController()
    val hazeState = remember { HazeState() }
    var currentTab by remember { mutableStateOf(Tab.Home) }

    // Thu thập dữ liệu thật từ ViewModel
    val isSystemRunning by vm.isSystemRunning.collectAsStateWithLifecycle()
    val uiErrors by vm.uiErrors.collectAsStateWithLifecycle()
    val pipelineState by vm.pipelineState.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize()) {
            // Nền Kính mờ cho toàn bộ App
            GlassBackground(Modifier.fillMaxSize())

            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    // Thanh điều hướng nổi dạng kính
                    Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                        GlassBottomBar(
                            selected = currentTab,
                            onSelect = { 
                                currentTab = it
                                nav.navigate(it.name) { 
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = Tab.Home.name,
                    modifier = Modifier.padding(padding)
                ) {
                    // Màn hình Trang chủ
                    composable(Tab.Home.name) {
                        HomeScreen(
                            systemOn = isSystemRunning,
                            errors = uiErrors,
                            onToggleSystem = { vm.setSystemRunning(!isSystemRunning) },
                            onErrorAction = { error ->
                                // Nút "Thử lại" trên thẻ lỗi
                                // (Có thể xử lý logic tải lại video ở đây nếu cần)
                            },
                            onClearErrors = { /* Xoá lỗi nếu cần */ }
                        )
                    }
                    
                    // Màn hình Hệ thống (Tiến trình 13 bước)
                    composable(Tab.System.name) { 
                        SystemScreen(vm, pipelineState) 
                    }
                    
                    // Màn hình Lưu trữ
                    composable(Tab.Storage.name) { 
                        StorageScreen(vm) 
                    }
                    
                    // Màn hình Cài đặt
                    composable(Tab.Settings.name) { 
                        SettingsScreen(vm) 
                    }
                }
            }
        }
    }
}
