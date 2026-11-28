package com.contentautomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.contentautomation.data.db.entity.SourceAccount
import com.contentautomation.domain.source.SourceVideoItem
import com.contentautomation.data.remote.PocoChatProfile
import com.contentautomation.data.remote.PocoChatRepository
import com.contentautomation.data.remote.SupabaseAuth
import com.contentautomation.data.remote.UserSupportRepository
import com.contentautomation.data.remote.MuChiAccountStatus
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.Person
import com.contentautomation.ui.DriveUiState
import com.contentautomation.ui.DouyinChannelLibraryUiState
import com.contentautomation.ui.DouyinDownloadProgressUiState
import androidx.work.WorkInfo
import com.contentautomation.ui.models.StorageInfo
import java.util.Locale
import kotlinx.coroutines.launch

private val SettingsBg = Color(0xFFF4F4F5)
private val SettingsWhite = Color(0xFFFFFFFF)
private val SettingsBorder = Color(0xFFE4E4E7)
private val SettingsInk = Color(0xFF18181B)
private val SettingsMuted = Color(0xFF71717A)
private val SettingsGreen = Color(0xFF16A34A)
private val SettingsAmber = Color(0xFFD97706)
private val SettingsRed = Color(0xFFDC2626)

@Composable
fun SettingsScreen(
    auth: SupabaseAuth,
    deviceId: String,
    accounts: List<SourceAccount>,
    appVersion: String,
    storage: StorageInfo,
    drive: DriveUiState,
    onConnectDrive: () -> Unit,
    onRefreshDrive: () -> Unit,
    onPickUpload: () -> Unit,
    onDeleteDriveFile: (String) -> Unit,
    onDownloadDriveFile: (String, String) -> Unit,
    onDisconnectDrive: () -> Unit,
    onAutoBackupChanged: (Boolean) -> Unit,
    onCopyId: () -> Unit,
    onAddAccount: () -> Unit,
    onSetAccountEnabled: (String, Boolean) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onOpenApi: () -> Unit,
    onLogout: () -> Unit,
    notificationsGranted: Boolean,
    storagePermissionRequired: Boolean,
    storageGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestStorage: () -> Unit,
    douyinSession: Boolean,
    kuaishouSession: Boolean,
    onLoginDouyin: () -> Unit,
    onLoginKuaishou: () -> Unit,
    onLogoutDouyin: () -> Unit,
    onLogoutKuaishou: () -> Unit,
    douyinLibrary: DouyinChannelLibraryUiState,
    douyinDownloadProgress: List<DouyinDownloadProgressUiState>,
    onScanDouyinChannel: (String) -> Unit,
    onDownloadSelectedDouyinVideos: (Set<String>) -> Unit,
    onClearDouyinLibrary: () -> Unit
) {
    val context = LocalContext.current
    val profileRepo = remember(context, auth) { PocoChatRepository(context.applicationContext, auth) }
    var profile by remember { mutableStateOf<PocoChatProfile?>(null) }
    var showProfileEditor by remember { mutableStateOf(false) }
    val supportRepo = remember(context, auth) { UserSupportRepository(context.applicationContext, auth) }
    val scope = rememberCoroutineScope()
    var accountStatus by remember { mutableStateOf<MuChiAccountStatus?>(null) }
    var accountStatusMessage by remember { mutableStateOf("Đang đồng bộ trạng thái tài khoản…") }
    var showSupportDialog by remember { mutableStateOf(false) }
    var supportCategory by remember { mutableStateOf("support") }
    var supportSubject by remember { mutableStateOf("") }
    var supportMessage by remember { mutableStateOf("") }
    var supportBusy by remember { mutableStateOf(false) }
    var supportFeedback by remember { mutableStateOf("") }
    fun refreshAccountStatus() {
        scope.launch {
            accountStatusMessage = "Đang đồng bộ trạng thái tài khoản…"
            runCatching { supportRepo.accountStatus() }
                .onSuccess {
                    accountStatus = it
                    accountStatusMessage = if (it.isBanned) "Tài khoản đang bị khóa" else "Đã đồng bộ với máy chủ"
                }
                .onFailure { accountStatusMessage = it.message ?: "Không thể đồng bộ tài khoản" }
        }
    }
    LaunchedEffect(auth) {
        runCatching { profileRepo.myProfile() }.onSuccess { profile = it }
        refreshAccountStatus()
    }
    val douyin = accounts.count { it.platform.equals("douyin", true) }
    val kuaishou = accounts.count { it.platform.equals("kuaishou", true) }
    var confirmLogout by remember { mutableStateOf(false) }
    var selectedDouyinIds by remember(douyinLibrary.accountId) { mutableStateOf(emptySet<String>()) }
    val shortId = remember(deviceId) {
        if (deviceId.length > 18) "${deviceId.take(8)}...${deviceId.takeLast(7)}" else deviceId
    }

    Column(
        Modifier.fillMaxSize().background(SettingsBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SettingsWhite)
                .border(1.dp, SettingsBorder, RoundedCornerShape(14.dp))
                .clickable { showProfileEditor = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!profile?.avatarUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = profile?.avatarUrl,
                    contentDescription = "Ảnh đại diện",
                    modifier = Modifier.size(56.dp).clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(Modifier.size(56.dp).clip(CircleShape).background(SettingsBg), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = SettingsMuted, modifier = Modifier.size(27.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Thông tin tài khoản", fontSize = 11.sp, color = SettingsMuted, fontWeight = FontWeight.SemiBold)
                Text(profile?.displayName?.ifBlank { "Chưa đặt tên" } ?: "Đang tải hồ sơ…", fontSize = 15.sp, color = SettingsInk, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("Biệt danh: ${profile?.nickname?.takeIf { it.isNotBlank() } ?: "Chưa đặt"}", fontSize = 11.sp, color = SettingsMuted, maxLines = 1)
                Text("ID: ${profile?.accountId ?: "Đang tải…"}", fontSize = 11.sp, color = SettingsMuted, maxLines = 1)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Chỉnh sửa hồ sơ", tint = SettingsMuted)
        }

        SettingsGroup("Tài khoản & Nguồn dữ liệu") {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tổng: ${accounts.size}  •  Douyin: $douyin  •  Kuaishou: $kuaishou",
                    modifier = Modifier.weight(1f), fontSize = 12.sp, color = SettingsInk, fontWeight = FontWeight.Medium, maxLines = 1)
                SmallAction("+ Thêm", onAddAccount)
            }
            SettingsDivider()
            SessionRow("Douyin", douyinSession, onLoginDouyin, onLogoutDouyin)
            SettingsDivider()
            SessionRow("Kuaishou", kuaishouSession, onLoginKuaishou, onLogoutKuaishou)
            SettingsDivider()
            Row(
                Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onOpenApi),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cấu hình API mô hình AI", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk, fontWeight = FontWeight.Medium)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SettingsMuted)
            }
            if (accounts.isNotEmpty()) {
                SettingsDivider()
                Text("Tài khoản nguồn", modifier = Modifier.padding(top = 10.dp, bottom = 4.dp), fontSize = 11.sp, color = SettingsMuted, fontWeight = FontWeight.SemiBold)
                accounts.forEachIndexed { index, account ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(account.accountName.ifBlank { account.accountIdentifier }, fontSize = 13.sp, color = SettingsInk, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${account.platform.uppercase(Locale.ROOT)} · ${account.accountIdentifier}", fontSize = 11.sp, color = SettingsMuted, maxLines = 1)
                            val profileStats = buildList {
                                account.profileVideoCount?.let { add("Video: ${it}") }
                                account.profileFollowerCount?.let { add("Follower: ${formatSettingsCount(it)}") }
                                account.profileFollowingCount?.let { add("Following: ${formatSettingsCount(it)}") }
                            }
                            if (profileStats.isNotEmpty()) {
                                Text(profileStats.joinToString(" · "), fontSize = 10.sp, color = SettingsMuted, maxLines = 1)
                            }
                        }
                        if (account.platform.equals("DOUYIN", ignoreCase = true)) {
                            SmallAction("Quét video", { onScanDouyinChannel(account.id) })
                            Spacer(Modifier.width(6.dp))
                        }
                        Switch(checked = account.enabled, onCheckedChange = { onSetAccountEnabled(account.id, it) })
                        TextButton(onClick = { onDeleteAccount(account.id) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("Xóa", color = SettingsRed, fontSize = 12.sp)
                        }
                    }
                    if (index != accounts.lastIndex) SettingsDivider()
                }
            }
        }

        SettingsGroup("Gói dịch vụ & Hỗ trợ") {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (accountStatus?.isPro == true) "MuChi Pro" else "MuChi Free",
                        fontSize = 15.sp, color = if (accountStatus?.isPro == true) SettingsGreen else SettingsInk,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            accountStatus?.isBanned == true -> "Tài khoản bị khóa: ${accountStatus?.banReason ?: "Liên hệ hỗ trợ"}"
                            accountStatus?.proPermanent == true -> "Pro vĩnh viễn"
                            accountStatus?.proExpiresAt != null -> "Hết hạn: ${accountStatus?.proExpiresAt}"
                            accountStatus != null -> "Gói miễn phí"
                            else -> accountStatusMessage
                        },
                        fontSize = 11.sp, color = if (accountStatus?.isBanned == true) SettingsRed else SettingsMuted
                    )
                    Text(accountStatusMessage, fontSize = 10.sp, color = SettingsMuted)
                }
                TextButton(onClick = { refreshAccountStatus() }) { Text("Làm mới") }
            }
            SettingsDivider()
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trung tâm hỗ trợ", fontSize = 14.sp, color = SettingsInk, fontWeight = FontWeight.Medium)
                    Text("Gửi yêu cầu trực tiếp đến quản trị MuChi", fontSize = 11.sp, color = SettingsMuted)
                }
                TextButton(onClick = { showSupportDialog = true }) { Text("Gửi yêu cầu") }
            }
            supportFeedback.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 11.sp, color = SettingsGreen, modifier = Modifier.padding(bottom = 10.dp))
            }
        }

        SettingsGroup("Lưu trữ & Đám mây") {
            val used = String.format(Locale.getDefault(), "%.1f", storage.usedGb)
            val total = String.format(Locale.getDefault(), "%.1f", storage.totalGb)
            val free = String.format(Locale.getDefault(), "%.1f", storage.freeGb)
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bộ nhớ điện thoại", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk, fontWeight = FontWeight.SemiBold)
                    Text("$used / $total GB", fontSize = 13.sp, color = SettingsInk, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxWidth().padding(top = 9.dp).height(5.dp).clip(RoundedCornerShape(8.dp)).background(SettingsBorder)) {
                    Box(Modifier.fillMaxWidth(storage.fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(SettingsAmber))
                }
                Text("Còn trống $free GB • Tự lưu vào thư mục Movies", modifier = Modifier.padding(top = 7.dp), fontSize = 11.sp, color = SettingsMuted)
            }
            SettingsDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Google Drive", fontSize = 14.sp, color = SettingsInk, fontWeight = FontWeight.Medium)
                    Text(
                        if (drive.connected) drive.account?.email ?: "Đã kết nối" else "Phiên hết hạn",
                        fontSize = 11.sp, color = if (drive.connected) SettingsGreen else SettingsAmber, maxLines = 1
                    )
                }
                if (drive.connected) {
                    SmallAction("Làm mới", onRefreshDrive)
                    Spacer(Modifier.width(6.dp))
                    SmallAction("Tải lên", onPickUpload)
                    Spacer(Modifier.width(6.dp))
                    SmallAction("Ngắt", onDisconnectDrive)
                } else SmallAction("Kết nối lại", onConnectDrive)
            }
            if (drive.connected) {
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Tự động sao lưu video", fontSize = 13.sp, color = SettingsInk, fontWeight = FontWeight.Medium)
                        Text("Video tải về và video quét được sẽ tự lưu lên Drive", fontSize = 10.sp, color = SettingsMuted)
                    }
                    Switch(checked = drive.autoBackupEnabled, onCheckedChange = onAutoBackupChanged)
                }
            } else {
                Text("Kết nối Google Drive để bật sao lưu video tự động.", fontSize = 10.sp, color = SettingsMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (drive.loading) Text("Đang đồng bộ…", fontSize = 11.sp, color = SettingsMuted, modifier = Modifier.padding(bottom = 6.dp))
            drive.message?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = SettingsAmber, modifier = Modifier.padding(bottom = 6.dp)) }
            if (drive.connected) {
                SettingsDivider()
                Text("Tệp trên Drive · ${drive.files.size}", modifier = Modifier.padding(top = 10.dp, bottom = 4.dp), fontSize = 11.sp, color = SettingsMuted, fontWeight = FontWeight.SemiBold)
                if (drive.files.isEmpty()) Text("Chưa có tệp", fontSize = 12.sp, color = SettingsMuted, modifier = Modifier.padding(vertical = 8.dp))
                drive.files.forEach { file ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(file.name, fontSize = 12.sp, color = SettingsInk, maxLines = 1)
                            Text(formatSettingsBytes(file.sizeBytes), fontSize = 10.sp, color = SettingsMuted)
                        }
                        TextButton(onClick = { onDownloadDriveFile(file.id, file.name) }, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("Tải về", fontSize = 11.sp) }
                        TextButton(onClick = { onDeleteDriveFile(file.id) }, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("Xóa", fontSize = 11.sp, color = SettingsRed) }
                    }
                }
            }
        }

        SettingsGroup("Thiết bị & Quyền") {
            Row(Modifier.fillMaxWidth().height(50.dp).clickable(onClick = onRequestNotifications), verticalAlignment = Alignment.CenterVertically) {
                Text("Thông báo ứng dụng", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk)
                Text(if (notificationsGranted) "Đã bật" else "Chưa bật", fontSize = 12.sp, color = if (notificationsGranted) SettingsGreen else SettingsAmber, fontWeight = FontWeight.SemiBold)
            }
            if (storagePermissionRequired) {
                SettingsDivider()
                Row(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onRequestStorage), verticalAlignment = Alignment.CenterVertically) {
                    Text("Quyền lưu trữ", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk)
                    Text(if (storageGranted) "Đã bật" else "Cấp quyền", fontSize = 12.sp, color = if (storageGranted) SettingsGreen else SettingsAmber)
                }
            }
            SettingsDivider()
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ID Thiết bị", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk)
                Text("MC-${shortId.removePrefix("MC-")}", fontSize = 12.sp, color = SettingsMuted)
                IconButton(onClick = onCopyId, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Sao chép ID", tint = SettingsMuted, modifier = Modifier.size(17.dp))
                }
            }
        }

        SettingsGroup("Hệ thống") {
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Phiên bản ứng dụng", modifier = Modifier.weight(1f), fontSize = 14.sp, color = SettingsInk)
                Text(appVersion, fontSize = 13.sp, color = SettingsMuted, fontWeight = FontWeight.SemiBold)
            }
        }

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SettingsWhite)
                .border(1.dp, SettingsBorder, RoundedCornerShape(12.dp))
                .clickable { confirmLogout = true }.padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Đăng xuất tài khoản", fontSize = 14.sp, color = SettingsRed, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { if (!supportBusy) showSupportDialog = false },
            title = { Text("Gửi yêu cầu hỗ trợ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = supportCategory, onValueChange = { supportCategory = it },
                        label = { Text("Danh mục") }, singleLine = true)
                    OutlinedTextField(value = supportSubject, onValueChange = { supportSubject = it },
                        label = { Text("Tiêu đề") }, singleLine = true)
                    OutlinedTextField(value = supportMessage, onValueChange = { supportMessage = it },
                        label = { Text("Mô tả vấn đề") }, minLines = 3)
                    if (supportFeedback.isNotBlank()) Text(supportFeedback, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = !supportBusy && supportMessage.isNotBlank(), onClick = {
                    scope.launch {
                        supportBusy = true
                        supportFeedback = ""
                        runCatching {
                            supportRepo.createTicket(supportCategory.ifBlank { "support" },
                                supportSubject.ifBlank { "Yêu cầu hỗ trợ" }, supportMessage)
                        }.onSuccess {
                            supportFeedback = "Đã gửi yêu cầu hỗ trợ thành công."
                            supportMessage = ""
                            supportSubject = ""
                            showSupportDialog = false
                        }.onFailure {
                            supportFeedback = it.message ?: "Gửi yêu cầu thất bại."
                        }
                        supportBusy = false
                    }
                }) { Text(if (supportBusy) "Đang gửi…" else "Gửi") }
            },
            dismissButton = { TextButton(enabled = !supportBusy, onClick = { showSupportDialog = false }) { Text("Đóng") } }
        )
    }

    if (showProfileEditor) {
        AccountProfileDialog(
            auth = auth,
            initialProfile = profile,
            onDismiss = { showProfileEditor = false },
            onSaved = { profile = it; showProfileEditor = false }
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Đăng xuất MuChi?") },
            text = { Text("Bạn sẽ quay về màn hình đăng nhập. Dữ liệu tài khoản trên máy chủ không bị xóa.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Đăng xuất") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Hủy") } }
        )
    }
    if (douyinLibrary.accountId != null) {
        DouyinVideoPickerDialog(
            state = douyinLibrary,
            downloadProgress = douyinDownloadProgress,
            selectedIds = selectedDouyinIds,
            onSelectionChanged = { selectedDouyinIds = it },
            onScanAgain = { douyinLibrary.accountId?.let(onScanDouyinChannel) },
            onDownloadSelected = onDownloadSelectedDouyinVideos,
            onDismiss = onClearDouyinLibrary
        )
    }
}


@Composable
private fun DouyinVideoPickerDialog(
    state: DouyinChannelLibraryUiState,
    downloadProgress: List<DouyinDownloadProgressUiState>,
    selectedIds: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onScanAgain: () -> Unit,
    onDownloadSelected: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val progressByVideo = remember(downloadProgress) { downloadProgress.associateBy { it.videoId } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Video của kênh Douyin", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${state.accountName} · ${state.videos.size} video", fontSize = 11.sp, color = SettingsMuted)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.message?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = SettingsMuted) }
                if (state.videos.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedIds.size == state.videos.size && state.videos.isNotEmpty(),
                            onCheckedChange = { checked ->
                                onSelectionChanged(if (checked) state.videos.map { it.id }.toSet() else emptySet())
                            }
                        )
                        Text("Chọn tất cả (${state.videos.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("Đã chọn ${selectedIds.size}", fontSize = 11.sp, color = SettingsMuted)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(state.videos, key = { it.id }) { video ->
                            DouyinVideoSelectionTile(
                                video = video,
                                checked = video.id in selectedIds,
                                progress = progressByVideo[video.id],
                                onCheckedChange = { checked ->
                                    onSelectionChanged(if (checked) selectedIds + video.id else selectedIds - video.id)
                                }
                            )
                        }
                    }
                }
                val visibleProgress = downloadProgress.filter {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.BLOCKED || it.state == WorkInfo.State.FAILED ||
                        it.state == WorkInfo.State.SUCCEEDED
                }
                if (visibleProgress.isNotEmpty()) {
                    Text("Tiến trình tải", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SettingsInk)
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        visibleProgress.takeLast(30).forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${item.videoId} · ${item.message}", fontSize = 10.sp, color = SettingsMuted, maxLines = 2)
                                LinearProgressIndicator(progress = item.percent / 100f, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.loading && selectedIds.isNotEmpty(),
                onClick = { onDownloadSelected(selectedIds) }
            ) { Text(if (state.loading) "Đang xử lý…" else "Xác nhận tải (${selectedIds.size})") }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !state.loading, onClick = onScanAgain) { Text("Quét lại") }
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        }
    )
}

@Composable
private fun DouyinVideoSelectionTile(
    video: SourceVideoItem,
    checked: Boolean,
    progress: DouyinDownloadProgressUiState?,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
            .background(SettingsWhite)
            .border(1.dp, if (checked) SettingsInk else SettingsBorder, RoundedCornerShape(9.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(width = 108.dp, height = 76.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SettingsBg)
        ) {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = "Ảnh bìa video ${video.id}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Không có ảnh bìa", fontSize = 9.sp, color = SettingsMuted)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                video.title?.takeIf { it.isNotBlank() } ?: "Video ${video.id}",
                fontSize = 11.sp,
                color = SettingsInk,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            Text(video.id, fontSize = 9.sp, color = SettingsMuted, maxLines = 1)
            if (progress != null) {
                Text(progress.message, fontSize = 9.sp, color = SettingsMuted, maxLines = 2)
                LinearProgressIndicator(progress = progress.percent / 100f, modifier = Modifier.fillMaxWidth())
            }
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SettingsWhite)
            .border(1.dp, SettingsBorder, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp)
    ) {
        Text(title, modifier = Modifier.padding(top = 13.dp, bottom = 4.dp), fontSize = 11.sp, color = SettingsMuted, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SessionRow(
    platform: String,
    saved: Boolean,
    onLogin: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Phiên $platform", fontSize = 13.sp, color = SettingsInk, fontWeight = FontWeight.Medium)
            Text(if (saved) "Đã lưu phiên" else "Chưa đăng nhập", fontSize = 11.sp,
                color = if (saved) SettingsGreen else SettingsMuted)
        }
        if (saved) {
            TextButton(onClick = onLogin, contentPadding = PaddingValues(horizontal = 5.dp)) {
                Text("Đăng nhập lại", fontSize = 11.sp, color = SettingsInk)
            }
            TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 5.dp)) {
                Text("Xóa", fontSize = 11.sp, color = SettingsRed)
            }
        } else SmallAction("Đăng nhập", onLogin)
    }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsBorder))
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(SettingsBg).clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) { Text(label, fontSize = 11.sp, color = SettingsInk, fontWeight = FontWeight.SemiBold) }
}

private fun formatSettingsCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}

private fun formatSettingsBytes(value: Long): String {
    if (value < 1024L) return "$value B"
    val units = listOf("KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var index = -1
    do { amount /= 1024.0; index++ } while (amount >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", amount, units[index])
}
