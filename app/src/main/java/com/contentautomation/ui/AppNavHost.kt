package com.contentautomation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.contentautomation.ui.components.GlassBackground
import com.contentautomation.ui.components.GlassBottomBar
import com.contentautomation.ui.components.LocalHazeState
import com.contentautomation.ui.components.Tab
import com.contentautomation.ui.models.ErrorKind
import com.contentautomation.ui.screens.HomeScreen
import com.contentautomation.ui.screens.SettingsScreen
import com.contentautomation.ui.screens.StorageScreen
import com.contentautomation.ui.screens.SystemScreen
import dev.chrisbanes.haze.HazeState

@Composable
fun AppNavHost(vm: MainViewModel) {
    val navController = rememberNavController()
    val hazeState = androidx.compose.runtime.remember { HazeState() }

    val isSystemRunning by
        vm.isSystemRunning.collectAsStateWithLifecycle()

    val uiErrors by
        vm.uiErrors.collectAsStateWithLifecycle()

    val pipelineJobs by
        vm.pipelineJobs.collectAsStateWithLifecycle()

    val backStackEntry by
        navController.currentBackStackEntryAsState()

    val currentRoute = backStackEntry?.destination?.route

    val selectedTab = Tab.entries.firstOrNull {
        it.name == currentRoute
    } ?: Tab.Home

    fun navigateTo(tab: Tab) {
        navController.navigate(tab.name) {
            popUpTo(
                navController.graph.findStartDestination().id
            ) {
                saveState = true
            }

            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(
        LocalHazeState provides hazeState
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            GlassBackground(
                modifier = Modifier.fillMaxSize()
            )

            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(
                                horizontal = 18.dp,
                                vertical = 12.dp
                            )
                    ) {
                        GlassBottomBar(
                            selected = selectedTab,
                            onSelect = ::navigateTo
                        )
                    }
                }
            ) { innerPadding ->

                NavHost(
                    navController = navController,
                    startDestination = Tab.Home.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {

                    composable(Tab.Home.name) {
                        HomeScreen(
                            systemOn = isSystemRunning,
                            errors = uiErrors,

                            onToggleSystem = {
                                vm.setSystemRunning(
                                    !isSystemRunning
                                )
                            },

                            onErrorAction = { error ->
                                when (error.kind) {

                                    ErrorKind.VideoUnavailable -> {
                                        vm.retryFailedDownloads()
                                    }

                                    ErrorKind.NetworkLost -> {
                                        vm.setSystemRunning(true)
                                    }

                                    ErrorKind.StorageAlmostFull -> {
                                        navigateTo(Tab.Storage)
                                    }
                                }
                            },

                            onClearErrors = {
                                vm.clearErrors()
                            }
                        )
                    }

                    composable(Tab.System.name) {
                        SystemScreen(
                            vm = vm,
                            jobs = pipelineJobs
                        )
                    }

                    composable(Tab.Storage.name) {
                        StorageScreen(
                            vm = vm
                        )
                    }

                    composable(Tab.Settings.name) {
                        SettingsScreen(
                            vm = vm
                        )
                    }
                }
            }
        }
    }
}
