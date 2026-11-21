package com.contentautomation.ui

import android.app.Application
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.contentautomation.data.prefs.AppPreferences
import com.contentautomation.data.remote.GoogleDriveClient
import com.contentautomation.data.remote.DriveAccountInfo
import com.contentautomation.data.remote.DriveFileItem
import com.contentautomation.domain.translate.TranslationService
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
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as ContentAutomationApp

    private val translationService = TranslationService {
        application.prefs.getTranslationApiKeySecure()
    }
    private val _translationResult = MutableStateFlow("")
    val translationResult = _translationResult.asStateFlow()
    private val _translationLoading = MutableStateFlow(false)
    val translationLoading = _translationLoading.asStateFlow()
    private val _translationError = MutableStateFlow<String?>(null)
    val translationError = _translationError.asStateFlow()

    fun saveTranslationApiKey(key: String) {
        application.prefs.setTranslationApiKeySecure(key.trim())
        _translationError.value = "Đã lưu khóa dịch trên thiết bị."
    }

    fun translateText(sourceLang: String, targetLang: String, text: String) {
        viewModelScope.launch {
            _translationLoading.value = true
            _translationError.value = null
            _translationResult.value = ""
            try {
                _translationResult.value = translationService.translate(sourceLang, targetLang, text)
            } catch (e: Exception) {
                _translationError.value = e.message ?: "Dịch thuật thất bại."
            } finally {
                _translationLoading.value = false
            }
        }
    }


    private val driveClient = GoogleDriveClient(app)
    private val _driveState = MutableStateFlow(
        DriveUiState(connected = driveClient.hasSavedToken())
    )
    val driveState = _driveState.asStateFlow()

    fun acceptDriveAccessToken(token: String) {
        driveClient.saveAccessToken(token)
        refreshDrive()
    }

    fun refreshDrive() {
        viewModelScope.launch {
            _driveState.update { it.copy(loading = true, message = null) }
            try {
                if (!driveClient.hasSavedToken()) {
                    _driveState.value = DriveUiState(
                        connected = false,
                        message = "Phiên Drive hết hạn. Hãy kết nối lại."
                    )
                    return@launch
                }
                val account = driveClient.accountInfo()
                val files = driveClient.listFiles()
                _driveState.value = DriveUiState(
                    connected = true,
                    loading = false,
                    account = account,
                    files = files
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
        driveClient.clearAccessToken()
        _driveState.value = DriveUiState(connected = false, message = "Đã ngắt kết nối Google Drive.")
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
    }

    fun clearErrors() {
        _uiErrors.value = emptyList()
    }

    // INITIALIZATION

    init {
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
        identifier: String
    ) {
        viewModelScope.launch {
            try {
                application.database.sourceAccountDao().insert(
                    SourceAccount(
                        platform = platform.trim(),
                        accountName = name.trim(),
                        accountIdentifier = identifier.trim()
                    )
                )

            } catch (e: Exception) {
                Log.e(
                    "MainViewModel",
                    "Lỗi thêm tài khoản nguồn",
                    e
                )

                recordError(
                    ErrorKind.VideoUnavailable,
                    "Không thể thêm tài khoản",
                    e.message ?: "Cơ sở dữ liệu từ chối thao tác.",
                    "Kiểm tra lại"
                )
            }
        }
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
            } catch (e: Exception) {
                recordError(
                    ErrorKind.NetworkLost,
                    "Không thể bắt đầu quét",
                    e.message ?: "Không thể tạo tác vụ quét.",
                    "Thử lại"
                )
            }
        }
    }

    fun resumeJob(jobId: String) {
        viewModelScope.launch {
            try {
                val dao = application.database.pipelineJobDao()
                val job = dao.findById(jobId) ?: return@launch
                if (job.status == PipelineStatus.DONE) return@launch
                if (job.stage != PipelineStage.DOWNLOAD) {
                    recordError(
                        ErrorKind.VideoUnavailable,
                        "Công đoạn chưa được tích hợp",
                        "Hiện tại chỉ có thể tiếp tục lại bước tải video. Công đoạn ${job.stage} cần được triển khai trước.",
                        "Đã hiểu"
                    )
                    return@launch
                }
                dao.update(
                    job.copy(
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
