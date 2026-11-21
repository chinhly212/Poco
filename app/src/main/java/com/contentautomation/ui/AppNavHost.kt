package com.contentautomation.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.contentautomation.data.prefs.UserIdentity
import com.contentautomation.data.remote.SupabaseAuth
import com.contentautomation.ui.components.BottomItem
import com.contentautomation.ui.components.GlassBackground
import com.contentautomation.ui.components.GlassBottomBar
import com.contentautomation.ui.models.ErrorKind
import com.contentautomation.ui.screens.HomeScreen
import com.contentautomation.ui.screens.SettingsScreen
import com.contentautomation.ui.screens.SystemScreen

private object MuChiRoutes {
    const val HOME = "home"
    const val AI = "ai"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(vm: MainViewModel) {

    val context = LocalContext.current
    val auth = remember(context) { SupabaseAuth(context.applicationContext) }
    var authenticated by remember { mutableStateOf(!auth.currentAccessToken().isNullOrBlank()) }
    if (!authenticated) {
        SupabaseLoginScreen(
            auth = auth,
            onSignedIn = { authenticated = true }
        )
        return
    }
    val navController = rememberNavController()

    val isSystemRunning by
        vm.isSystemRunning.collectAsStateWithLifecycle()

    val uiErrors by
        vm.uiErrors.collectAsStateWithLifecycle()

    val pipelineJobs by
        vm.pipelineJobs.collectAsStateWithLifecycle()

    val accounts by
        vm.accounts.collectAsStateWithLifecycle()

    val backStackEntry by
        navController.currentBackStackEntryAsState()

    val currentRoute =
        backStackEntry?.destination?.route

    val selectedIndex = when (currentRoute) {
        MuChiRoutes.HOME -> 0
        MuChiRoutes.AI -> 1
        MuChiRoutes.SETTINGS -> 2
        else -> 0
    }

    val deviceId = remember {
        UserIdentity.getOrCreate(context)
    }

    val appVersion = remember {
        try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    var showAddAccountDialog by remember {
        mutableStateOf(false)
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(
                navController.graph.findStartDestination().id
            ) {
                saveState = true
            }

            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        GlassBackground(
            modifier = Modifier.fillMaxSize()
        ) {}

        Scaffold(
            containerColor = Color.Transparent,

            bottomBar = {
                GlassBottomBar(
                    items = listOf(
                        BottomItem(
                            label = "Trang chủ",
                            icon = Icons.Rounded.Home
                        ),
                        BottomItem(
                            label = "AI",
                            icon = Icons.Rounded.AutoAwesome
                        ),
                        BottomItem(
                            label = "Cài đặt",
                            icon = Icons.Rounded.Settings
                        )
                    ),

                    selectedIndex = selectedIndex,

                    onSelect = { index ->
                        when (index) {
                            0 -> navigateTo(MuChiRoutes.HOME)
                            1 -> navigateTo(MuChiRoutes.AI)
                            2 -> navigateTo(MuChiRoutes.SETTINGS)
                        }
                    }
                )
            }
        ) { innerPadding ->

            NavHost(
                navController = navController,
                startDestination = MuChiRoutes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                composable(MuChiRoutes.HOME) {

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
                                    navigateTo(
                                        MuChiRoutes.SETTINGS
                                    )
                                }
                            }
                        },

                        onClearErrors = {
                            vm.clearErrors()
                        }
                    )
                }

                composable(MuChiRoutes.AI) {

                    SystemScreen(
                        vm = vm,
                        jobs = pipelineJobs
                    )
                }

                composable(MuChiRoutes.SETTINGS) {

                    SettingsScreen(
                        deviceId = deviceId,
                        accounts = accounts,
                        appVersion = appVersion,

                        onCopyId = {
                            val clipboard =
                                context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager

                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "MuChi ID",
                                    deviceId
                                )
                            )
                        },

                        onAddAccount = {
                            showAddAccountDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showAddAccountDialog) {

        AddAccountDialog(
            onDismiss = {
                showAddAccountDialog = false
            },

            onAdd = { platform, name, identifier ->

                vm.addAccount(
                    platform = platform,
                    name = name,
                    identifier = identifier
                )

                showAddAccountDialog = false
            }
        )
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onAdd: (
        String,
        String,
        String
    ) -> Unit
) {

    var platform by remember {
        mutableStateOf("Douyin")
    }

    var accountName by remember {
        mutableStateOf("")
    }

    var identifier by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text("Thêm tài khoản nguồn")
        },

        text = {
            Column {

                OutlinedTextField(
                    value = platform,
                    onValueChange = {
                        platform = it
                    },
                    label = {
                        Text("Nền tảng")
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = accountName,
                    onValueChange = {
                        accountName = it
                    },
                    label = {
                        Text("Tên tài khoản")
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = identifier,
                    onValueChange = {
                        identifier = it
                    },
                    label = {
                        Text("ID hoặc đường dẫn")
                    },
                    singleLine = true
                )
            }
        },

        confirmButton = {
            TextButton(
                enabled = platform.isNotBlank()
                    && identifier.isNotBlank(),

                onClick = {
                    onAdd(
                        platform.trim(),
                        accountName.trim(),
                        identifier.trim()
                    )
                }
            ) {
                Text("Thêm")
            }
        },

        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Hủy")
            }
        }
    )
}
