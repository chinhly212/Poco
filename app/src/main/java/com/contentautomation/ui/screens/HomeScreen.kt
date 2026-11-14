package com.contentautomation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*

@Composable
fun AppNavHost(vm: MainViewModel) {
    val nav = rememberNavController()
    
    // Đã cập nhật thành 4 Tab theo yêu cầu
    val tabs = listOf(
        Triple("home", "Trang chủ", Icons.Default.Home),
        Triple("system", "Hệ thống", Icons.Default.Build),
        Triple("storage", "Lưu trữ", Icons.Default.Folder),
        Triple("settings", "Cài đặt", Icons.Default.Settings)
    )
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                tabs.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { 
                            nav.navigate(r) { 
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            } 
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { com.contentautomation.ui.screens.HomeScreen(vm) }
            composable("system") { com.contentautomation.ui.screens.SystemScreen(vm) }
            composable("storage") { com.contentautomation.ui.screens.StorageScreen(vm) }
            composable("settings") { com.contentautomation.ui.screens.SettingsScreen(vm) } // File này đã có sẵn từ trước
        }
    }
}
