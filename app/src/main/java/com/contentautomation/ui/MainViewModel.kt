package com.contentautomation.ui

import android.app.Application
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.lifecycle.viewModelScope
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.contentautomation.data.prefs.AppPreferences
import com.contentautomation.data.remote.GoogleDriveClient
import com.contentautomation.data.remote.UserSupportRepository
import com.contentautomation.data.remote.DriveAccountInfo
import com.contentautomation.data.remote.DriveFileItem
import com.contentautomation.domain.source.SourceVideoItem
import android.net.Uri
import com.contentautomation.service.AutomationForegroundService
import com.contentautomation.ui.models.*
import com.contentautomation.work.DownloadWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


data class DriveUiState(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val account: DriveAccountInfo? = null,
    val files: List<DriveFileItem> = emptyList(),
    val message: String? = null,
    val autoBackupEnabled: Boolean = true
)

data class ApiConnectionStatus(
    val checking: Boolean = false,
    val connected: Boolean = false,
    val message: String = ""
)

data class ScanProgressUiState(
    val active: Boolean = false,
    val progress: Int = 0,
    val message: String = "Chưa chạy quét kênh."
)

data class DouyinChannelLibraryUiState(
    val accountId: String? = null,
    val accountName: String = "",
    val loading: Boolean = false,
    val videos: List<SourceVideoItem> = emptyList(),
    val message: String? = null
)

data class DouyinDownloadProgressUiState(
    val videoId: String,
    val state: WorkInfo.State,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val percent: Int = 0,
    val message: String = "Đang chờ tải…"
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as ContentAutomationApp
    private val userSupportRepository by lazy { UserSupportRepository(app.applicationContext) }

    private val _scanProgress = MutableStateFlow(ScanProgressUiState())
    val scanProgress = _scanProgress.asStateFlow()

    private val _douyinLibrary = MutableStateFlow(DouyinChannelLibraryUiState())
    val douyinLibrary = _douyinLibrary.asStateFlow()

    private val _douyinDownloadProgress = MutableStateFlow<List<DouyinDownloadProgressUiState>>(emptyList())
    val douyinDownloadProgress = _douyinDownloadProgress.asStateFlow()

    private val douyinDownloadObserver = Observer<List<WorkInfo>> { infos ->
        _douyinDownloadProgress.value = infos
            .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            .mapNotNull { info ->
                val videoId = info.progress.getString("video_id") ?: info.id.toString()
                DouyinDownloadProgressUiState(
                    videoId = videoId,
                    state = info.state,
                    downloadedBytes = info.progress.getLong("bytes_downloaded", 0L),
                    totalBytes = info.progress.getLong("total_bytes", -1L),
                    percent = info.progress.getInt("progress_percent", 0).coerceIn(0, 100),
                    message = info.progress.getString("message")
                        ?: if (info.state == WorkInfo.State.RUNNING) "Đang chuẩn bị tải…" else "Đang chờ lượt tải…"
                )
            }
    }

    private val scanWorkObserver = Observer<List<WorkInfo>> { infos ->
        val info = infos.lastOrNull()
        if (info == null) {
            _scanProgress.value = ScanProgressUiState()
        } else {
            val progress = info.progress.getInt("progress", 0).coerceIn(0, 100)
            val output = info.outputData
            val message = when (info.state) {
                WorkInfo.State.RUNNING -> info.progress.getString("message") ?: "Đang quét các kênh..."
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Đang chờ bắt đầu quét..."
                WorkInfo.State.SUCCEEDED -> output.getString("summary") ?: "Hoàn tất: ${output.getInt("scanned_accounts", 0)} kênh · ${output.getInt("discovered_videos", 0)} video mới · ${output.getInt("queued_jobs", 0)} công việc."
                WorkInfo.State.FAILED -> "Lượt quét thất bại. Xem thông báo hệ thống để biết nguyên nhân."
                WorkInfo.State.CANCELLED -> "Lượt quét đã bị hủy."
            }
            _scanProgress.value = ScanProgressUiState(
                active = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.BLOCKED,
                progress = if (info.state == WorkInfo.State.SUCCEEDED) 100 else progress,
                message = message
            )
        }
    }

    init {
        WorkManager.getInstance(app)
            .getWorkInfosForUniqueWorkLiveData(AutomationForegroundService.SCAN_WORK_NAME)
            .observeForever(scanWorkObserver)
        WorkManager.getInstance(app)
            .getWorkInfosByTagLiveData("muchi-douyin-archive-download")
            .observeForever(douyinDownloadObserver)
    }

    override fun onCleared() {
        WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkLiveData(AutomationForegroundService.SCAN_WORK_NAME)
            .removeObserver(scanWorkObserver)
        WorkManager.getInstance(application)
            .getWorkInfosByTagLiveData("muchi-douyin-archive-download")
            .removeObserver(douyinDownloadObserver)
        super.onCleared()
    }

    private val driveClient = GoogleDriveClient(app)
    private val _driveState = MutableStateFlow(
        DriveUiState(
            connected = driveClient.hasSavedToken(),
            autoBackupEnabled = driveClient.isAutoBackupEnabled()
        )
    )
    val driveState = _driveState.asStateFlow()

    fun acceptDriveAccessToken(token: String) {
        driveClient.saveAccessToken(token)
        refreshDrive()
    }

    fun setDriveAutoBackupEnabled(enabled: Boolean) {
        driveClient.setAutoBackupEnabled(enabled)
        _driveState.update { it.copy(autoBackupEnabled = enabled) }
    }

    fun refreshDrive() {
        viewModelScope.launch {
            _driveState.update { it.copy(loading = true, message = null) }
            try {
                if (!driveClient.hasSavedToken()) {
                    _driveState.value = DriveUiState(
                        connected = false,
                        message = "Chưa kết nối Google Drive.",
                        autoBackupEnabled = driveClient.isAutoBackupEnabled()
                    )
                    return@launch
                }
                val account = driveClient.accountInfo()
                val files = driveClient.listFiles()
                _driveState.value = DriveUiState(
                    connected = true,
                    loading = false,
                    account = account,
                    files = files,
                    autoBackupEnabled = driveClient.isAutoBackupEnabled()
                )
            } catch (e: Exception) {
                _driveState.update {
                    it.copy(
                        connected = driveClient.hasSavedToken(),
                        loading = false,
                        message = e.message ?: "Không thể tải dữ liệu Google Drive."
                    )
                }
            }
        }
    }

    fun uploadDriveFile(uri: Uri, name: String, mimeType: String) {
        viewModelScope.launch {
            _driveState.update { it.copy(loading = true, message = null) }
            try {
                driveClient.upload(uri, name, mimeType)
                refreshDrive()
            } catch (e: Exception) {
                _driveState.update {
                    it.copy(loading = false, message = e.message ?: "Tải tệp lên thất bại.")
                }
            }
        }
    }

    fun deleteDriveFile(fileId: String) {
        viewModelScope.launch {
            _driveState.update { it.copy(loading = true, message = null) }
            try {
                driveClient.delete(fileId)
                refreshDrive()
            } catch (e: Exception) {
                _driveState.update {
                    it.copy(loading = false, message = e.message ?: "Xóa tệp thất bại.")
                }
            }
        }
    }

    fun downloadDriveFile(fileId: String, name: String) {
        viewModelScope.launch {
            _driveState.update { it.copy(loading = true, message = null) }
            try {
                val file = driveClient.download(fileId, name)
                _driveState.update {
                    it.copy(loading = false, message = "Đã tải về: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                _driveState.update {
                    it.copy(loading = false, message = e.message ?: "Tải tệp xuống thất bại.")
                }
            }
        }
    }

    fun disconnectDrive() {
        driveClient.setAutoBackupEnabled(false)
        driveClient.clearAccessToken()
        _driveState.value = DriveUiState(
            connected = false,
            message = "Đã ngắt kết nối Google Drive. Sao lưu tự động đã tắt.",
            autoBackupEnabled = false
        )
    }


    // SYSTEM STATUS

    private val _isSystemRunning = MutableStateFlow(false)

    val isSystemRunning = _isSystemRunning.asStateFlow()

    // PIPELINE STATE

    private val _pipelineState =
        MutableStateFlow<PipelineState>(PipelineState.Idle)

    val pipelineState = _pipelineState.asStateFlow()

    val pipelineJobs =
        application.database.pipelineJobDao()
            .observeAll()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    // ERROR MANAGEMENT

    private val _uiErrors =
        MutableStateFlow<List<AppError>>(emptyList())

    val uiErrors = _uiErrors.asStateFlow()

    private fun recordError(
        kind: ErrorKind,
        title: String,
        message: String,
        actionLabel: String
    ) {
        val error = AppError(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = title,
            message = message,
            time = SimpleDateFormat(
                "HH:mm dd/MM/yyyy",
                Locale.getDefault()
            ).format(Date()),
            actionLabel = actionLabel
        )

        _uiErrors.update { current ->
            (listOf(error) + current).take(50)
        }
        // Chỉ gửi lỗi thật lên máy chủ; recordError cũng được dùng cho một số thông báo thành công.
        val isSuccessNotice = title.startsWith("Kết nối thành công") ||
            title.startsWith("Đã kết nối") ||
            title.startsWith("Đã xóa API") ||
            title.startsWith("Đã ngắt phiên") ||
            title.startsWith("Đã lưu phiên")
        if (!isSuccessNotice) {
            viewModelScope.launch {
                runCatching {
                    userSupportRepository.reportError(
                        message = "$title: $message",
                        errorCode = kind.name,
                        source = "android"
                    )
                }.onFailure { Log.w("MainViewModel", "Không thể gửi error log lên máy chủ", it) }
            }
        }

        viewModelScope.launch {
            runCatching {
                application.database.notificationDao().insert(
                    com.contentautomation.data.db.entity.StoredNotification(
                        id = error.id,
                        kind = error.kind.name,
                        title = error.title,
                        message = error.message,
                        time = error.time,
                        actionLabel = error.actionLabel,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }.onFailure { Log.e("MainViewModel", "Không thể lưu thông báo", it) }
        }
    }

    fun reportAiIssue(title: String, diagnostic: String) {
        val safeDiagnostic = diagnostic.take(2500).ifBlank { "Không có thông tin chi tiết." }
        recordError(
            ErrorKind.VideoUnavailable,
            title,
            safeDiagnostic,
            "Đã hiểu"
        )
    }

    fun clearErrors() {
        _uiErrors.value = emptyList()
        viewModelScope.launch {
            runCatching { application.database.notificationDao().deleteAll() }
                .onFailure { Log.e("MainViewModel", "Không thể xóa thông báo", it) }
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            runCatching { application.database.notificationDao().deleteById(id) }
                .onFailure { Log.e("MainViewModel", "Không thể xóa thông báo", it) }
        }
    }

    // API keys are stored only in EncryptedSharedPreferences.
    private val apiClient = com.contentautomation.video.AiVideoServerClient()
    private val _apiConnectionStates = MutableStateFlow<Map<String, ApiConnectionStatus>>(emptyMap())
    val apiConnectionStates = _apiConnectionStates.asStateFlow()

    fun getSavedApiKeys(): Map<String, String> = listOf("gemini", "openrouter", "deepseek")
        .mapNotNull { provider ->
            application.prefs.getSecret("ai_api_$provider")?.takeIf { it.isNotBlank() }
                ?.let { provider to it }
        }.toMap()

    fun hasSavedApiKey(provider: String): Boolean =
        !application.prefs.getSecret("ai_api_${provider.lowercase()}").isNullOrBlank()

    fun validateAndSaveApi(provider: String, apiKey: String) {
        val normalized = provider.lowercase()
        val label = when (normalized) {
            "gemini" -> "Google Gemini"
            "openrouter" -> "OpenRouter"
            "deepseek" -> "DeepSeek"
            else -> normalized
        }
        viewModelScope.launch {
            _apiConnectionStates.update {
                it + (normalized to ApiConnectionStatus(checking = true, message = "Đang kiểm tra trực tiếp với nhà cung cấp…"))
            }
            try {
                require(normalized in setOf("gemini", "openrouter", "deepseek")) {
                    "Nhà cung cấp API chưa được hỗ trợ."
                }
                require(apiKey.isNotBlank()) { "API key không được để trống." }
                val result = apiClient.validateProvider(normalized, apiKey.trim())
                application.prefs.setSecret("ai_api_$normalized", apiKey.trim())
                _apiConnectionStates.update {
                    it + (normalized to ApiConnectionStatus(connected = true, message = result))
                }
                recordError(
                    ErrorKind.NetworkLost,
                    "Kết nối thành công · $label",
                    "Khóa API đã được xác thực với nhà cung cấp và lưu mã hóa trên điện thoại.",
                    "Đã hiểu"
                )
            } catch (e: Exception) {
                val reason = e.message ?: "Không xác định được nguyên nhân. Hãy kiểm tra mạng và API key."
                _apiConnectionStates.update {
                    it + (normalized to ApiConnectionStatus(connected = false, message = reason))
                }
                recordError(
                    ErrorKind.NetworkLost,
                    "Kết nối thất bại · $label",
                    reason,
                    "Kiểm tra API"
                )
            }
        }
    }

    fun removeApiKey(provider: String) {
        application.prefs.removeSecret("ai_api_${provider.lowercase()}")
        recordError(
            ErrorKind.NetworkLost,
            "Đã xóa API · ${provider.replaceFirstChar { it.uppercase() }}",
            "Khóa API đã được xóa khỏi vùng lưu trữ mã hóa trên điện thoại.",
            "Đã hiểu"
        )
    }

    // INITIALIZATION

    init {
        viewModelScope.launch {
            application.database.notificationDao().observeAll().collect { stored ->
                _uiErrors.value = stored.mapNotNull { item ->
                    val kind = runCatching { ErrorKind.valueOf(item.kind) }.getOrNull() ?: return@mapNotNull null
                    AppError(item.id, kind, item.title, item.message, item.time, item.actionLabel)
                }
            }
        }
        viewModelScope.launch {
            try {
                val savedState =
                    application.prefs.foregroundEnabled.first()

                if (savedState) {
                    AutomationForegroundService.start(application)
                }

                _isSystemRunning.value = savedState

            } catch (e: Exception) {
                Log.e(
                    "MainViewModel",
                    "Không thể khôi phục trạng thái hệ thống",
                    e
                )

                _isSystemRunning.value = false

                try {
                    application.prefs.set(
                        AppPreferences.KEY_FOREGROUND_ENABLED,
                        false
                    )
                } catch (saveError: Exception) {
                    Log.e(
                        "MainViewModel",
                        "Không thể lưu trạng thái hệ thống",
                        saveError
                    )
                }
            }
        }
    }

    // SYSTEM CONTROL

    fun setSystemRunning(isRunning: Boolean) {
        viewModelScope.launch {
            val previousState = _isSystemRunning.value

            try {
                if (isRunning) {
                    application.prefs.set(
                        AppPreferences.KEY_FOREGROUND_ENABLED,
                        true
                    )

                    AutomationForegroundService.start(application)

                } else {
                    AutomationForegroundService.stop(application)

                    application.prefs.set(
                        AppPreferences.KEY_FOREGROUND_ENABLED,
                        false
                    )
                }

                _isSystemRunning.value = isRunning

            } catch (e: Exception) {
                Log.e(
                    "MainViewModel",
                    "Không thể thay đổi trạng thái hệ thống",
                    e
                )

                _isSystemRunning.value = previousState

                recordError(
                    ErrorKind.NetworkLost,
                    "Không thể thay đổi hệ thống",
                    e.message ?: "Không thể bật hoặc tắt dịch vụ nền.",
                    "Thử lại"
                )

                try {
                    application.prefs.set(
                        AppPreferences.KEY_FOREGROUND_ENABLED,
                        previousState
                    )
                } catch (saveError: Exception) {
                    Log.e(
                        "MainViewModel",
                        "Không thể khôi phục trạng thái đã lưu",
                        saveError
                    )
                }
            }
        }
    }

    // PHONE STORAGE

    val phoneStorage: StateFlow<StorageInfo> =
        flow {
            try {
                val stat = StatFs(
                    Environment.getDataDirectory().path
                )

                val total =
                    stat.blockCountLong * stat.blockSizeLong

                val available =
                    stat.availableBlocksLong * stat.blockSizeLong

                val used = total - available

                emit(
                    StorageInfo(
                        usedGb = used /
                            (1024f * 1024f * 1024f),
                        totalGb = total /
                            (1024f * 1024f * 1024f)
                    )
                )

            } catch (e: Exception) {
                Log.e(
                    "MainViewModel",
                    "Lỗi đọc bộ nhớ điện thoại",
                    e
                )

                emit(StorageInfo(0f, 0f))
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            StorageInfo(0f, 0f)
        )

    // PLATFORM WEB SESSIONS: encrypted locally, one login per platform.
    fun hasPlatformSession(platform: String): Boolean =
        !application.prefs.getSecret("platform_cookie_${platform.lowercase()}").isNullOrBlank()

    fun savePlatformSession(platform: String, cookieHeader: String) {
        require(platform.uppercase() in setOf("DOUYIN", "KUAISHOU")) { "Nền tảng không hợp lệ." }
        require(cookieHeader.isNotBlank()) { "Không lấy được phiên đăng nhập từ trang web. Hãy đăng nhập hoàn tất rồi thử lại." }
        application.prefs.setSecret("platform_cookie_${platform.lowercase()}", cookieHeader)
        recordError(
            ErrorKind.NetworkLost,
            "Đã lưu phiên ${platform}",
            "Phiên đăng nhập đã được lưu mã hóa trên điện thoại.",
            "Đã hiểu"
        )
    }

    fun clearPlatformSession(platform: String) {
        application.prefs.removeSecret("platform_cookie_${platform.lowercase()}")
        recordError(ErrorKind.NetworkLost, "Đã ngắt phiên ${platform}", "Phiên đăng nhập đã được xóa khỏi điện thoại.", "Đã hiểu")
    }

    // SOURCE ACCOUNTS

    val accounts =
        application.database.sourceAccountDao()
            .observeAll()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    fun addAccount(
        platform: String,
        name: String,
        identifier: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val normalizedPlatform = platform.trim().uppercase()
                require(normalizedPlatform in setOf("DOUYIN", "KUAISHOU")) {
                    "Chỉ hỗ trợ tài khoản Douyin hoặc Kuaishou."
                }
                val cookie = application.prefs.getSecret("platform_cookie_${normalizedPlatform.lowercase()}")
                require(!cookie.isNullOrBlank()) {
                    "Bạn chưa đăng nhập ${normalizedPlatform}. Hãy đăng nhập ${normalizedPlatform} trước khi thêm tài khoản theo dõi."
                }
                val rawIdentifier = identifier.trim()
                require(rawIdentifier.isNotBlank()) { "ID tài khoản không được để trống." }
                val normalizedIdentifier = normalizeSourceIdentifier(normalizedPlatform, rawIdentifier)
                require(normalizedIdentifier.isNotBlank()) { "Không đọc được ID từ đường dẫn tài khoản." }

                val adapter = com.contentautomation.domain.source.SourceAdapterFactory.create(normalizedPlatform, application.filesDir)
                val profile = (adapter as? com.contentautomation.domain.source.YtDlpProfileAdapter)
                    ?.verifyProfile(normalizedIdentifier, cookie)
                    ?: throw IllegalStateException("Bộ đọc hồ sơ của nền tảng chưa được hỗ trợ.")

                application.database.sourceAccountDao().insert(
                    SourceAccount(
                        platform = normalizedPlatform,
                        accountName = profile.displayName.ifBlank { name.trim().ifBlank { normalizedIdentifier } },
                        accountIdentifier = profile.identifier.ifBlank { normalizedIdentifier },
                        profileDescription = profile.description,
                        profileAvatarUrl = profile.avatarUrl,
                        profileVideoCount = profile.videoCount,
                        profileVerifiedAt = System.currentTimeMillis(),
                        profileFollowerCount = profile.followerCount,
                        profileFollowingCount = profile.followingCount
                    )
                )
                recordError(
                    ErrorKind.NetworkLost,
                    "Đã kết nối tài khoản ${profile.displayName}",
                    "Đã xác minh hồ sơ ${profile.displayName} trên ${normalizedPlatform}.",
                    "Đã hiểu"
                )
                onResult(true, "Đã kết nối thành công: ${profile.displayName}")
            } catch (e: Exception) {
                val reason = e.message ?: "Không xác định được nguyên nhân."
                recordError(ErrorKind.NetworkLost, "Không thể kết nối tài khoản", reason, "Kiểm tra đăng nhập")
                onResult(false, reason)
            }
        }
    }

    fun setAccountEnabled(accountId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val dao = application.database.sourceAccountDao()
                val account = dao.findById(accountId)
                    ?: throw IllegalArgumentException("Không tìm thấy tài khoản nguồn.")
                dao.update(account.copy(enabled = enabled))
            } catch (e: Exception) {
                Log.e("MainViewModel", "Không thể cập nhật tài khoản nguồn", e)
                recordError(
                    ErrorKind.VideoUnavailable,
                    "Không thể cập nhật tài khoản",
                    e.message ?: "Không thể thay đổi trạng thái tài khoản.",
                    "Thử lại"
                )
            }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                val dao = application.database.sourceAccountDao()
                val account = dao.findById(accountId)
                    ?: throw IllegalArgumentException("Không tìm thấy tài khoản nguồn.")
                dao.delete(account)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Không thể xóa tài khoản nguồn", e)
                recordError(
                    ErrorKind.VideoUnavailable,
                    "Không thể xóa tài khoản",
                    e.message ?: "Không thể xóa tài khoản nguồn.",
                    "Thử lại"
                )
            }
        }
    }

    private fun normalizeSourceIdentifier(platform: String, input: String): String {
        val value = input.trim()
        val uri = runCatching { android.net.Uri.parse(value) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host?.lowercase().orEmpty()
        require(scheme in setOf("http", "https")) {
            "Hãy dán liên kết trang cá nhân đầy đủ, bắt đầu bằng https://"
        }
        val allowed = when (platform) {
            "DOUYIN" -> host == "douyin.com" || host.endsWith(".douyin.com") || host == "iesdouyin.com" || host.endsWith(".iesdouyin.com")
            "KUAISHOU" -> host == "kuaishou.com" || host.endsWith(".kuaishou.com") || host == "gifshow.com" || host.endsWith(".gifshow.com")
            else -> false
        }
        require(allowed) { "Liên kết này không thuộc nền tảng $platform đã chọn." }
        return value.substringBefore("#")
    }


    // DOUYIN FULL-CHANNEL LIBRARY: scan every available page, then let the user choose downloads.

    fun scanDouyinChannel(accountId: String) {
        viewModelScope.launch {
            _douyinLibrary.value = DouyinChannelLibraryUiState(
                accountId = accountId,
                loading = true,
                message = "Đang quét Douyin trực tiếp trên điện thoại…"
            )
            try {
                val account = application.database.sourceAccountDao().findById(accountId)
                    ?: throw IllegalArgumentException("Không tìm thấy kênh Douyin.")
                require(account.platform.equals("DOUYIN", ignoreCase = true)) {
                    "Chức năng này chỉ dành cho kênh Douyin."
                }
                val cookie = application.prefs.getSecret("platform_cookie_douyin")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Phiên Douyin chưa kết nối. Hãy đăng nhập Douyin trong Cài đặt.")

                if (!application.awaitMediaEngines()) {
                    throw IllegalStateException("Bộ tải video trên điện thoại chưa khởi động xong. Hãy thử lại sau ít phút.")
                }
                val adapter = com.contentautomation.domain.source.DouyinAdapter(application.filesDir)
                val videos = adapter.getLatestVideos(account.accountIdentifier, cookie).distinctBy { it.id }
                val dao = application.database.sourceVideoDao()
                val visibleVideos = mutableListOf<SourceVideoItem>()
                var savedToDatabase = 0

                videos.chunked(20).forEachIndexed { _, batch ->
                    for (item in batch) {
                        val existing = dao.findByAccountAndExternalId(account.id, item.id)
                        if (existing == null) {
                            val inserted = dao.insert(
                                SourceVideo(
                                    sourceAccountId = account.id,
                                    externalVideoId = item.id,
                                    sourceUrl = item.url,
                                    title = item.title,
                                    descriptionZh = item.description,
                                    publishedAt = item.publishedAt,
                                    status = VideoStatus.NEW.name
                                )
                            )
                            if (inserted != -1L) savedToDatabase++
                        }
                        visibleVideos += item
                    }
                    _douyinLibrary.value = DouyinChannelLibraryUiState(
                        accountId = account.id,
                        accountName = account.accountName,
                        loading = true,
                        videos = visibleVideos.toList(),
                        message = "Đã ghi ${visibleVideos.size}/${videos.size} video vào bộ nhớ điện thoại…"
                    )
                }

                application.database.sourceAccountDao().updateLastScan(account.id, System.currentTimeMillis())
                _douyinLibrary.value = DouyinChannelLibraryUiState(
                    accountId = account.id,
                    accountName = account.accountName,
                    loading = false,
                    videos = visibleVideos.toList(),
                    message = "Đã quét ${videos.size} video · lưu mới ${savedToDatabase} video vào cơ sở dữ liệu trên máy. Hãy chọn video muốn tải."
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _douyinLibrary.value = DouyinChannelLibraryUiState(
                    accountId = accountId,
                    loading = false,
                    message = e.message ?: "Không thể quét kênh Douyin."
                )
            }
        }
    }

    fun downloadSelectedDouyinVideos(videoIds: Set<String>) {
        val snapshot = _douyinLibrary.value
        val accountId = snapshot.accountId ?: return
        val selected = snapshot.videos.filter { it.id in videoIds }
        if (selected.isEmpty()) {
            _douyinLibrary.update { it.copy(message = "Bạn chưa chọn video nào.") }
            return
        }
        viewModelScope.launch {
            _douyinLibrary.update { it.copy(loading = true, message = "Đang đưa video đã chọn vào hàng đợi tải…") }
            try {
                val account = application.database.sourceAccountDao().findById(accountId)
                    ?: throw IllegalArgumentException("Không tìm thấy kênh Douyin.")
                val dao = application.database.sourceVideoDao()
                var queued = 0
                var skipped = 0
                for (item in selected) {
                    val existing = dao.findByAccountAndExternalId(account.id, item.id)
                    val videoId = if (existing != null) {
                        if (existing.status == VideoStatus.SAVED_TO_PHONE.name ||
                            existing.status == VideoStatus.DOWNLOADING.name
                        ) {
                            skipped++
                            continue
                        }
                        existing.id
                    } else {
                        val video = SourceVideo(
                            sourceAccountId = account.id,
                            externalVideoId = item.id,
                            sourceUrl = item.url,
                            title = item.title,
                            descriptionZh = item.description,
                            publishedAt = item.publishedAt,
                            status = VideoStatus.NEW.name
                        )
                        val inserted = dao.insert(video)
                        if (inserted == -1L) {
                            skipped++
                            continue
                        }
                        video.id
                    }
                    dao.updateStatus(videoId, VideoStatus.DOWNLOADING.name)
                    com.contentautomation.work.DouyinBulkDownloadWorker.enqueue(application, videoId)
                    queued++
                }
                _douyinLibrary.update {
                    it.copy(
                        loading = false,
                        message = "Đã xác nhận $queued video để tải trực tiếp về điện thoại${if (skipped > 0) " · $skipped video đang tải hoặc đã lưu nên được bỏ qua" else ""}."
                    )
                }
            } catch (e: Exception) {
                _douyinLibrary.update {
                    it.copy(loading = false, message = e.message ?: "Không thể tạo hàng đợi tải video.")
                }
            }
        }
    }

    fun clearDouyinLibrary() {
        _douyinLibrary.value = DouyinChannelLibraryUiState()
    }

    // RETRY FAILED DOWNLOADS

    fun retryFailedDownloads() {
        viewModelScope.launch {
            try {
                val dao = application.database.sourceVideoDao()

                val failedVideos = dao.findByStatuses(
                    listOf(
                        VideoStatus.FAILED.name,
                        VideoStatus.RETRY_PENDING.name
                    )
                )

                failedVideos.forEach { video ->
                    DownloadWorker.enqueue(
                        application,
                        video.id
                    )
                }

            } catch (e: Exception) {
                Log.e(
                    "MainViewModel",
                    "Không thể thử lại video thất bại",
                    e
                )

                recordError(
                    ErrorKind.VideoUnavailable,
                    "Thử tải lại thất bại",
                    e.message ?: "Không thể đưa video vào hàng đợi.",
                    "Thử lại"
                )
            }
        }
    }

    // VIDEO DOWNLOAD

    fun downloadNow(video: SourceVideo) {
        try {
            DownloadWorker.enqueue(
                application,
                video.id
            )

        } catch (e: Exception) {
            Log.e(
                "MainViewModel",
                "Lỗi khởi chạy tải video",
                e
            )

            recordError(
                ErrorKind.VideoUnavailable,
                "Không thể tải video",
                e.message ?: "Không thể đưa video vào hàng đợi.",
                "Thử lại"
            )
        }
    }

    // APP-WIDE JOB ORCHESTRATION: all screens call the same ViewModel actions.

    fun scanNow() {
        viewModelScope.launch {
            try {
                val enabledAccounts = application.database.sourceAccountDao().getEnabled()
                if (enabledAccounts.isEmpty()) {
                    android.widget.Toast.makeText(
                        application,
                        "Chưa có nguồn đang bật. Hãy thêm tài khoản tại Cài đặt trước khi quét.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val request = androidx.work.OneTimeWorkRequestBuilder<com.contentautomation.work.ScanWorker>()
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                androidx.work.WorkManager.getInstance(application)
                    .enqueueUniqueWork(
                        com.contentautomation.service.AutomationForegroundService.SCAN_WORK_NAME,
                        androidx.work.ExistingWorkPolicy.KEEP,
                        request
                    )
                android.widget.Toast.makeText(
                    application,
                    "Đã gửi lệnh quét ${enabledAccounts.size} nguồn. Kết quả sẽ xuất hiện trong Công việc.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                recordError(
                    ErrorKind.NetworkLost,
                    "Không thể bắt đầu quét",
                    e.message ?: "Không thể tạo tác vụ quét.",
                    "Thử lại"
                )
                android.widget.Toast.makeText(
                    application,
                    "Không thể bắt đầu quét: " + (e.message ?: "lỗi không xác định"),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun resumeJob(jobId: String) {
        viewModelScope.launch {
            try {
                val dao = application.database.pipelineJobDao()
                val job = dao.findById(jobId) ?: return@launch
                if (job.status == PipelineStatus.DONE) return@launch
                if (job.stage !in listOf(
                        PipelineStage.DOWNLOAD,
                        PipelineStage.ANALYZE,
                        PipelineStage.SPEECH_TO_TEXT,
                        PipelineStage.TRANSLATE,
                        PipelineStage.TTS,
                        PipelineStage.MERGE,
                        PipelineStage.EXPORT,
                        PipelineStage.VERIFY
                    )) {
                    recordError(
                        ErrorKind.VideoUnavailable,
                        "Không thể tiếp tục công đoạn này",
                        "Công đoạn ${job.stage} chưa được hỗ trợ tiếp tục tự động.",
                        "Đã hiểu"
                    )
                    return@launch
                }
                val resumeStage = job.stage
                dao.update(
                    job.copy(
                        stage = resumeStage,
                        status = PipelineStatus.PENDING,
                        retries = 0,
                        error = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                com.contentautomation.work.PipelineWorker.enqueue(application, jobId)
            } catch (e: Exception) {
                recordError(
                    ErrorKind.VideoUnavailable,
                    "Không thể tiếp tục công việc",
                    e.message ?: "Không thể đưa công việc trở lại hàng đợi.",
                    "Thử lại"
                )
            }
        }
    }

    fun pauseJob(jobId: String) {
        viewModelScope.launch {
            try {
                androidx.work.WorkManager.getInstance(application)
                    .cancelUniqueWork("pipeline_$jobId")
                val dao = application.database.pipelineJobDao()
                dao.findById(jobId)?.let { job ->
                    dao.update(
                        job.copy(
                            status = PipelineStatus.PAUSED,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                recordError(
                    ErrorKind.VideoUnavailable,
                    "Không thể tạm dừng",
                    e.message ?: "Không thể dừng công việc.",
                    "Thử lại"
                )
            }
        }
    }

    fun retryJob(jobId: String) = resumeJob(jobId)
}
