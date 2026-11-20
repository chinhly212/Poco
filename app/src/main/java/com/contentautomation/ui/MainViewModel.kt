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
import com.contentautomation.service.AutomationForegroundService
import com.contentautomation.ui.models.*
import com.contentautomation.work.DownloadWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as ContentAutomationApp

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
}
