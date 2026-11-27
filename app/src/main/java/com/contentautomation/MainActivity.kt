package com.contentautomation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contentautomation.ui.AppNavHost
import com.contentautomation.ui.AppUpdateDialog
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.UpdateUiState
import com.contentautomation.update.AppUpdateManager
import com.contentautomation.update.AvailableUpdate
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var pendingInstallFile: File? = null

    private val unknownSourcesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val file = pendingInstallFile
            pendingInstallFile = null
            if (file != null &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    packageManager.canRequestPackageInstalls())
            ) {
                installApk(file)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: MainViewModel = viewModel()
            val updateManager = remember { AppUpdateManager(applicationContext) }
            val scope = rememberCoroutineScope()
            var showSplash by remember { mutableStateOf(true) }
            var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
            var recheckAfterSkip by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay(2500)
                showSplash = false
            }

            // Lần mở app đầu tiên sau splash.
            LaunchedEffect(showSplash) {
                if (!showSplash) {
                    updateState = UpdateUiState.Checking
                    try {
                        val update = updateManager.checkForUpdate()
                        updateState = if (update != null) {
                            UpdateUiState.Available(update)
                        } else {
                            UpdateUiState.Idle
                        }
                    } catch (error: Exception) {
                        updateState = UpdateUiState.CheckFailed(
                            error.message ?: "Không thể kết nối máy chủ cập nhật. Hãy kiểm tra Internet rồi thử lại."
                        )
                    }
                }
            }

            // Nếu người dùng bỏ qua rồi đưa app xuống nền và mở lại, kiểm tra lại ngay.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (!showSplash && recheckAfterSkip) {
                    recheckAfterSkip = false
                    scope.launch {
                        updateState = UpdateUiState.Checking
                        try {
                            val update = updateManager.checkForUpdate()
                            updateState = if (update != null) {
                                UpdateUiState.Available(update)
                            } else {
                                UpdateUiState.Idle
                            }
                        } catch (error: Exception) {
                            updateState = UpdateUiState.CheckFailed(
                                error.message ?: "Không thể kết nối máy chủ cập nhật. Hãy kiểm tra Internet rồi thử lại."
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (showSplash) {
                    Image(
                        painter = painterResource(id = R.drawable.muchi_splash),
                        contentDescription = "Mu Chi",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AppNavHost(vm)
                }

                AppUpdateDialog(
                    state = updateState,
                    onSkip = {
                        updateState = UpdateUiState.Idle
                        recheckAfterSkip = true
                    },
                    onUpdate = { update ->
                        scope.launch {
                            updateState = UpdateUiState.Downloading(update)
                            try {
                                val apk = updateManager.downloadAndPrepare(update)
                                updateState = UpdateUiState.Idle
                                installOrRequestPermission(apk)
                            } catch (error: Exception) {
                                updateState = UpdateUiState.Failed(
                                    update,
                                    error.message ?: "Không thể hoàn tất cập nhật."
                                )
                            }
                        }
                    },
                    onRetry = { update ->
                        scope.launch {
                            updateState = UpdateUiState.Downloading(update)
                            try {
                                val apk = updateManager.downloadAndPrepare(update)
                                updateState = UpdateUiState.Idle
                                installOrRequestPermission(apk)
                            } catch (error: Exception) {
                                updateState = UpdateUiState.Failed(
                                    update,
                                    error.message ?: "Không thể hoàn tất cập nhật."
                                )
                            }
                        }
                    },
                    onCheckAgain = {
                        scope.launch {
                            updateState = UpdateUiState.Checking
                            try {
                                val update = updateManager.checkForUpdate()
                                updateState = if (update != null) {
                                    UpdateUiState.Available(update)
                                } else {
                                    UpdateUiState.Idle
                                }
                            } catch (error: Exception) {
                                updateState = UpdateUiState.CheckFailed(
                                    error.message ?: "Không thể kết nối máy chủ cập nhật. Hãy kiểm tra Internet rồi thử lại."
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    private fun installOrRequestPermission(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallFile = apk
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            unknownSourcesLauncher.launch(intent)
        } else {
            installApk(apk)
        }
    }

    private fun installApk(apk: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
