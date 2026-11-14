package com.contentautomation.ui

import com.contentautomation.ui.screens.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.*

@Composable
fun AppNavHost(vm: MainViewModel) {
    val nav = rememberNavController()
    val tabs = listOf(
        Triple("dashboard", "Tổng quan", Icons.Default.Dashboard),
        Triple("new", "Video mới", Icons.Default.NewReleases),
        Triple("pending", "Chờ đăng", Icons.Default.Schedule),
        Triple("history", "Lịch sử", Icons.Default.History),
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
                        onClick = { nav.navigate(r) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(pad)) {
            composable("dashboard") { DashboardScreen(vm) }
            composable("new") { NewVideosScreen(vm) }
            composable("pending") { PendingVideosScreen(vm) }
            composable("history") { HistoryScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
