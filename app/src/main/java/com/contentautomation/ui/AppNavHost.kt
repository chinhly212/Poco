
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
import com.contentautomation.ui.models.ErrorKind
import com.contentautomation.ui.screens.*
import dev.chrisbanes.haze.HazeState

@Composable
fun AppNavHost(vm: MainViewModel) {
    val nav = rememberNavController()
    val hazeState = remember { HazeState() }
    var currentTab by remember { mutableStateOf(Tab.Home) }

    val isSystemRunning by vm.isSystemRunning.collectAsStateWithLifecycle()
    val uiErrors by vm.uiErrors.collectAsStateWithLifecycle()
    val pipelineJobs by vm.pipelineJobs.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize()) {
            GlassBackground(Modifier.fillMaxSize())

            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    Box(
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        GlassBottomBar(
                            selected = currentTab,
                            onSelect = {
                                currentTab = it
                                nav.navigate(it.name) {
                                    popUpTo(nav.graph.startDestinationId) {
                                        saveState = true
                                    }
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
                    composable(Tab.Home.name) {
                        HomeScreen(
                            systemOn = isSystemRunning,
                            errors = uiErrors,
                            onToggleSystem = {
                                vm.setSystemRunning(!isSystemRunning)
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
                                        nav.navigate(Tab.Storage.name) {
                                            launchSingleTop = true
                                        }
                                        currentTab = Tab.Storage
                                    }
                                }
                            },
                            onClearErrors = {
                                vm.clearErrors()
                            }
                        )
                    }

                    composable(Tab.System.name) {
                        SystemScreen(vm, pipelineJobs)
                    }

                    composable(Tab.Storage.name) {
                        StorageScreen(vm)
                    }

                    composable(Tab.Settings.name) {
                        SettingsScreen(vm)
                    }
                }
            }
        }
    }
}
