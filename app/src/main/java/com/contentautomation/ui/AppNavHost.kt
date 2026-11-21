package com.contentautomation.ui

import android.app.Activity
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
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
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.contentautomation.ui.screens.AiScreen
import com.contentautomation.ui.screens.SettingsScreen
import com.contentautomation.ui.screens.SystemScreen
import com.contentautomation.ui.screens.StorageScreen
import com.contentautomation.ui.screens.ApiSettingsScreen

private object MuChiRoutes {
    const val HOME = "home"
    const val WORK = "work"
    const val AI = "ai"
    const val STORAGE = "storage"
    const val SETTINGS = "settings"
    const val API = "api"
}

@Composable
fun AppNavHost(vm: MainViewModel) {

    val context = LocalContext.current
    val auth = remember(context) { SupabaseAuth(context.applicationContext) }
    var authenticated by remember { mutableStateOf(!auth.currentAccessToken().isNullOrBlank()) }

    val driveAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val authorization = Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(result.data)
                val token = authorization.accessToken
                if (!token.isNullOrBlank()) vm.acceptDriveAccessToken(token)
                else Toast.makeText(context, "Google không trả access token.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Không nhận được quyền Google Drive.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(context, if (granted) "Đã cấp quyền." else "Bạn chưa cấp quyền. Có thể bật lại trong Cài đặt Android.", Toast.LENGTH_LONG).show()
    }

    val driveUploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            } ?: "nichi-upload"
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            vm.uploadDriveFile(uri, name, mime)
        }
    }

    fun connectDrive() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(DriveScopes.DRIVE_FILE),
                    Scope("openid"),
                    Scope("email"),
                    Scope("profile")
                )
            )
            .build()
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { authorization ->
                if (authorization.hasResolution()) {
                    val pending = authorization.pendingIntent
                    if (pending != null) {
                        driveAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pending.intentSender).build()
                        )
                    } else {
                        Toast.makeText(context, "Google không trả yêu cầu cấp quyền.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val token = authorization.accessToken
                    if (!token.isNullOrBlank()) vm.acceptDriveAccessToken(token)
                    else Toast.makeText(context, "Google không trả access token.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(context, error.message ?: "Không thể kết nối Google Drive.", Toast.LENGTH_LONG).show()
            }
    }
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

    val phoneStorage by
        vm.phoneStorage.collectAsStateWithLifecycle()

    val driveState by vm.driveState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshDrive() }

    val backStackEntry by
        navController.currentBackStackEntryAsState()

    val currentRoute =
        backStackEntry?.destination?.route

    val selectedIndex = when (currentRoute) {
        MuChiRoutes.HOME -> 0
        MuChiRoutes.WORK -> 1
        MuChiRoutes.AI -> 2
        MuChiRoutes.STORAGE -> 3
        MuChiRoutes.SETTINGS, MuChiRoutes.API -> 4
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
                        BottomItem(label = "Công việc", icon = Icons.Rounded.Work),
                        BottomItem(label = "AI", icon = Icons.Rounded.AutoAwesome),
                        BottomItem(label = "Dung lượng", icon = Icons.Rounded.Storage),
                        BottomItem(label = "Cài đặt", icon = Icons.Rounded.Settings)
                    ),

                    selectedIndex = selectedIndex,

                    onSelect = { index ->
                        when (index) {
                            0 -> navigateTo(MuChiRoutes.HOME)
                            1 -> navigateTo(MuChiRoutes.WORK)
                            2 -> navigateTo(MuChiRoutes.AI)
                            3 -> navigateTo(MuChiRoutes.STORAGE)
                            4 -> navigateTo(MuChiRoutes.SETTINGS)
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
                        jobs = pipelineJobs,
                        onOpenWork = { navigateTo(MuChiRoutes.WORK) },

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
                                    if (!error.title.startsWith("Kết nối") && !error.title.startsWith("Đã xóa API")) {
                                        vm.setSystemRunning(true)
                                    }
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

                composable(MuChiRoutes.WORK) {
                    SystemScreen(
                        vm = vm,
                        jobs = pipelineJobs,
                        onScanNow = { vm.scanNow() },
                        onPauseJob = { vm.pauseJob(it) },
                        onResumeJob = { vm.resumeJob(it) }
                    )
                }

                composable(MuChiRoutes.AI) {
                    AiScreen(vm)
                }

                composable(MuChiRoutes.STORAGE) {
                    StorageScreen(
                        storage = phoneStorage,
                        drive = driveState,
                        onConnectDrive = { connectDrive() },
                        onRefreshDrive = { vm.refreshDrive() },
                        onPickUpload = { driveUploadLauncher.launch(arrayOf("*/*")) },
                        onDeleteDriveFile = { vm.deleteDriveFile(it) },
                        onDownloadDriveFile = { id, name -> vm.downloadDriveFile(id, name) },
                        onDisconnectDrive = { vm.disconnectDrive() }
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
                        },
                        onSetAccountEnabled = { id, enabled ->
                            vm.setAccountEnabled(id, enabled)
                        },
                        onDeleteAccount = { id ->
                            vm.deleteAccount(id)
                        },
                        onOpenApi = { navigateTo(MuChiRoutes.API) },
                        notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                        storagePermissionRequired = Build.VERSION.SDK_INT <= 28,
                        storageGranted = Build.VERSION.SDK_INT > 28 ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED,
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onRequestStorage = {
                            if (Build.VERSION.SDK_INT <= 28) permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    )
                }

                composable(MuChiRoutes.API) {
                    ApiSettingsScreen(vm = vm, onBack = { navController.popBackStack() })
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    var platformMenuExpanded by remember { mutableStateOf(false) }

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

                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = platformMenuExpanded,
                    onExpandedChange = { platformMenuExpanded = !platformMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = platform,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Nền tảng") },
                        modifier = Modifier.menuAnchor(),
                        singleLine = true
                    )
                    DropdownMenu(
                        expanded = platformMenuExpanded,
                        onDismissRequest = { platformMenuExpanded = false }
                    ) {
                        listOf("Douyin", "Kuaishou").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    platform = option
                                    platformMenuExpanded = false
                                }
                            )
                        }
                    }
                }

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
