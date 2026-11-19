
package com.contentautomation.ui

import android.app.Application
import android.os.Environment
import android.os.StatFs
import android.util.Log

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.contentautomation.ui.models.*
import com.contentautomation.work.DownloadWorker
import com.contentautomation.work.PublishWorker

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as ContentAutomationApp

    // ======================================
    // SYSTEM STATUS
    // ======================================

    private val _isSystemRunning = MutableStateFlow(false)

    val isSystemRunning = _isSystemRunning.asStateFlow()

    fun setSystemRunning(isRunning: Boolean) {
        _isSystemRunning.value = isRunning
    }

    // ======================================
    // VIDEO PIPELINE
    // ======================================

    private val _pipelineState =
        MutableStateFlow<PipelineState>(PipelineState.Idle)

    val pipelineState = _pipelineState.asStateFlow()
    // ======================================
// PIPELINE JOBS - DATABASE THỰC
// ======================================

val pipelineJobs =
    application.database
        .pipelineJobDao()
        .observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ======================================
    // ERROR LIST
    // ======================================

    private val _uiErrors =
        MutableStateFlow<List<AppError>>(emptyList())

    val uiErrors = _uiErrors.asStateFlow()

    // ======================================
    // PHONE STORAGE
    // ======================================

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
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StorageInfo(0f, 0f)
        )

    // ======================================
    // SOURCE ACCOUNTS
    // ======================================

    val accounts =
        application.database
            .sourceAccountDao()
            .observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun addAccount(
        platform: String,
        name: String,
        identifier: String
    ) {
        viewModelScope.launch {
            try {
                application.database
                    .sourceAccountDao()
                    .insert(
                        SourceAccount(
                            platform = platform,
                            accountName = name,
                            accountIdentifier = identifier
                        )
                    )
            } catch (e: Exception) {
                Log.e(
                    "MainViewModel",
                    "Lỗi thêm tài khoản nguồn",
                    e
                )
            }
        }
    }

    // ======================================
    // VIDEO ACTIONS
    // ======================================

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
        }
    }

    fun publishNow(
        processed: ProcessedVideo,
        privacy: String,
        title: String
    ) {
        try {
            PublishWorker.enqueue(
                application,
                processed.id,
                privacy,
                title
            )
        } catch (e: Exception) {
            Log.e(
                "MainViewModel",
                "Lỗi khởi chạy đăng video",
                e
            )
        }
    }
}
