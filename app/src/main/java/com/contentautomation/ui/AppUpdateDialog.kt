package com.contentautomation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.contentautomation.update.AvailableUpdate

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class CheckFailed(val message: String) : UpdateUiState
    data class Available(val update: AvailableUpdate) : UpdateUiState
    data class Downloading(val update: AvailableUpdate) : UpdateUiState
    data class Failed(val update: AvailableUpdate, val message: String) : UpdateUiState
}

@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    onSkip: () -> Unit,
    onUpdate: (AvailableUpdate) -> Unit,
    onRetry: (AvailableUpdate) -> Unit,
    onCheckAgain: () -> Unit
) {
    when (state) {
        is UpdateUiState.CheckFailed -> {
            AlertDialog(
                onDismissRequest = onSkip,
                title = { Text("Không kiểm tra được cập nhật") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = onCheckAgain) { Text("Kiểm tra lại") }
                },
                dismissButton = {
                    TextButton(onClick = onSkip) { Text("Đóng") }
                }
            )
        }
        is UpdateUiState.Available -> {
            AlertDialog(
                onDismissRequest = onSkip,
                title = { Text("Có bản cập nhật mới") },
                text = {
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        Text("Phiên bản ${state.update.manifest.versionName}")
                        Text(
                            state.update.manifest.releaseNotes.ifBlank {
                                "Sửa lỗi và cải thiện độ ổn định."
                            }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { onUpdate(state.update) }) {
                        Text("Cập nhật")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onSkip) {
                        Text("Bỏ qua")
                    }
                }
            )
        }
        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Đang cập nhật") },
                text = {
                    Text("Đang tải và kiểm tra bản cập nhật phiên bản ${state.update.manifest.versionName}. Vui lòng chờ.")
                },
                confirmButton = {}
            )
        }
        is UpdateUiState.Failed -> {
            AlertDialog(
                onDismissRequest = onSkip,
                title = { Text("Cập nhật chưa hoàn tất") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { onRetry(state.update) }) {
                        Text("Thử lại")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onSkip) {
                        Text("Đóng")
                    }
                }
            )
        }
        else -> Unit
    }
}
