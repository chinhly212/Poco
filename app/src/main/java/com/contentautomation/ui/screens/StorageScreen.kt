package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ui.components.*
import com.contentautomation.ui.models.StorageInfo
import com.contentautomation.ui.DriveUiState
import com.contentautomation.data.remote.DriveFileItem
import java.util.Locale

private const val WARN_FRACTION = 0.9f
private const val WARN_FREE_GB = 10f

private fun gb(v: Float) = String.format(Locale.getDefault(), "%.1f GB", v)

/**
 * driveStatus: chuỗi trạng thái Google Drive lấy từ nơi bạn đang quản lý (ví dụ "Chưa kết nối").
 * onDriveClick: hành động của nút ở khu vực Google Drive.
 */
@Composable
fun StorageScreen(
    storage: StorageInfo,
    drive: DriveUiState,
    onConnectDrive: () -> Unit,
    onRefreshDrive: () -> Unit,
    onPickUpload: () -> Unit,
    onDeleteDriveFile: (String) -> Unit,
    onDownloadDriveFile: (String, String) -> Unit,
    onDisconnectDrive: () -> Unit
) {
    val low = storage.totalGb > 0f &&
        (storage.fraction >= WARN_FRACTION || storage.freeGb < WARN_FREE_GB)

    ScreenColumn {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GText("BỘ NHỚ ĐIỆN THOẠI", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
                GText(gb(storage.usedGb), 30.sp, weight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                GlassProgressBar(storage.fraction, color = if (low) GlassColors.Amber else GlassColors.Blue)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        GText("Tổng", 12.sp, color = GlassColors.InkSoft)
                        GText(gb(storage.totalGb), 14.sp, weight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        GText("Còn trống", 12.sp, color = GlassColors.InkSoft)
                        GText(gb(storage.freeGb), 14.sp, weight = FontWeight.Bold, color = GlassColors.Green)
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GText("VIDEO ĐÃ XỬ LÝ", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
                Spacer(Modifier.height(6.dp))
                GText("NiChi tự lưu video hoàn tất vào thư viện điện thoại, thư mục Movies/NiChi.", 13.sp)
                GText("Không cần đăng nhập hay kết nối bất kỳ mạng xã hội nào.", 11.sp, color = GlassColors.InkSoft)
            }
        }

        if (low) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    GText("Cảnh báo bộ nhớ", 14.sp, weight = FontWeight.Bold, color = GlassColors.Amber)
                    GText("Bộ nhớ sắp đầy, hãy giải phóng dung lượng để tiếp tục lưu video.",
                        12.sp, color = GlassColors.InkSoft)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GText("GOOGLE DRIVE RIÊNG CỦA BẠN", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft)
                Spacer(Modifier.height(6.dp))
                if (drive.connected && drive.account != null) {
                    GText(drive.account.email, 14.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val total = drive.account.totalBytes
                    val used = drive.account.usedBytes
                    val fraction = if (total > 0L) used.toFloat() / total.toFloat() else 0f
                    GlassProgressBar(fraction)
                    Spacer(Modifier.height(6.dp))
                    GText(
                        if (total > 0L) "Đã dùng ${formatBytes(used)} / ${formatBytes(total)}"
                        else "Google chưa cung cấp giới hạn dung lượng cho tài khoản này",
                        12.sp, color = GlassColors.InkSoft
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("Làm mới", onClick = onRefreshDrive, modifier = Modifier.weight(1f))
                        GlassButton("Tải tệp lên", onClick = onPickUpload, primary = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    GlassButton("Ngắt kết nối tài khoản này", onClick = onDisconnectDrive)
                } else {
                    GText("Chưa kết nối tài khoản Google Drive.", 13.sp, color = GlassColors.InkSoft)
                    GlassButton("Kết nối Google Drive", onClick = onConnectDrive, primary = true,
                        modifier = Modifier.fillMaxWidth())
                }
                if (drive.loading) {
                    Spacer(Modifier.height(8.dp))
                    GText("Đang đồng bộ...", 12.sp, color = GlassColors.Blue)
                }
                if (!drive.message.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    GText(drive.message, 12.sp, color = GlassColors.Amber)
                }
            }
        }

        if (drive.connected) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GText("TỆP TRÊN DRIVE", 10.sp, weight = FontWeight.Bold, color = GlassColors.InkSoft,
                            modifier = Modifier.weight(1f))
                        GText("${drive.files.size} tệp", 11.sp, color = GlassColors.InkSoft)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (drive.files.isEmpty()) {
                        GText("Chưa có tệp nào do NiChi quản lý hoặc được cấp quyền truy cập.", 12.sp,
                            color = GlassColors.InkSoft)
                    } else {
                        drive.files.forEach { file ->
                            DriveFileRow(file, onDeleteDriveFile, onDownloadDriveFile)
                            GlassDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveFileRow(file: DriveFileItem, onDelete: (String) -> Unit, onDownload: (String, String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            GText(file.name, 13.sp, weight = FontWeight.SemiBold)
            GText(formatBytes(file.sizeBytes), 11.sp, color = GlassColors.InkSoft)
        }
        Column(horizontalAlignment = Alignment.End) {
            GlassButton("Tải về", onClick = { onDownload(file.id, file.name) })
            Spacer(Modifier.height(4.dp))
            GlassButton("Thùng rác", onClick = { onDelete(file.id) })
        }
    }
}

private fun formatBytes(value: Long): String {
    if (value < 1024L) return "${value} B"
    val units = listOf("KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var index = -1
    do {
        amount /= 1024.0
        index++
    } while (amount >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", amount, units[index])
}
