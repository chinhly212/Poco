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

private fun vietnameseReleaseNotes(notes: String): String {
    val original = notes.trim()
    if (original.isBlank()) return "Sửa lỗi và cải thiện độ ổn định của ứng dụng."

    // Giữ nguyên nội dung tiếng Việt do quy trình phát hành tạo ra.
    if (
        original.contains("Khắc phục", ignoreCase = true) ||
        original.contains("Cải thiện", ignoreCase = true) ||
        original.contains("Sửa lỗi", ignoreCase = true)
    ) return original

    val normalized = original.lowercase()
    return when {
        normalized.contains("403") || normalized.contains("bad_jwt") ->
            "• Khắc phục lỗi HTTP 403 khi sử dụng Chat và đăng xuất tài khoản.\n" +
            "• Cải thiện xử lý phiên đăng nhập khi mã xác thực hết hạn.\n" +
            "• Đảm bảo có thể đăng xuất trên thiết bị ngay cả khi máy chủ từ chối yêu cầu."

        normalized.contains("updater") || normalized.contains("update") ->
            "• Cải thiện tính năng kiểm tra và cập nhật phiên bản trực tiếp trong ứng dụng.\n" +
            "• Tăng độ ổn định khi tải và xác minh tệp cập nhật."

        else -> "• Sửa lỗi và cải thiện độ ổn định của ứng dụng."
    }
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
                        Text(vietnameseReleaseNotes(state.update.manifest.releaseNotes))
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
