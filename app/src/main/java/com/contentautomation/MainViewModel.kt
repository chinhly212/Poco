package com.contentautomation.ui

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.contentautomation.data.prefs.AppPreferences
import com.contentautomation.ui.models.*
import com.contentautomation.work.DownloadWorker
import com.contentautomation.work.PublishWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as ContentAutomationApp
    private val db = application.database
    private val prefs = application.prefs

    // --- TRẠNG THÁI HỆ THỐNG ---
    val isSystemRunning = prefs.foregroundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSystemRunning(isRunning: Boolean) {
        viewModelScope.launch { prefs.set(AppPreferences.KEY_FOREGROUND_ENABLED, isRunning) }
    }

    // --- LỖI THẬT TỪ DATABASE ---
    val uiErrors: StateFlow<List<AppError>> = db.sourceVideoDao().observeByStatus(VideoStatus.FAILED.name)
        .map { videos ->
            videos.map { video ->
                AppError(
                    id = video.id,
                    kind = if (video.lastError?.contains("mạng", ignoreCase = true) == true) ErrorKind.NetworkLost else ErrorKind.VideoUnavailable,
                    title = "Lỗi xử lý video",
                    message = video.lastError ?: "Không thể lấy link video",
                    time = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(video.discoveredAt)),
                    actionLabel = "Thử lại"
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- TIẾN TRÌNH 13 BƯỚC THẬT TỪ DATABASE ---
    val pipelineState: StateFlow<PipelineState> = db.sourceVideoDao().observeRecent(5).map { videos ->
        val active = videos.firstOrNull { 
            it.status != VideoStatus.NEW.name && it.status != VideoStatus.FAILED.name && it.status != VideoStatus.PUBLISHED.name 
        }
        
        if (active == null) {
            PipelineState.Idle
        } else {
            val stepIndex = when (active.status) {
                VideoStatus.FETCHING_METADATA.name -> 1
                VideoStatus.DOWNLOADING.name -> 2
                VideoStatus.UPLOADED_TO_DRIVE.name -> 3
                VideoStatus.PROCESSING_VIDEO.name -> 5
                VideoStatus.TRANSLATING.name -> 6
                VideoStatus.READY_TO_PUBLISH.name -> 7
                VideoStatus.PUBLISHING.name -> 8
                else -> 0
            }
            PipelineState.Running(stepIndex = stepIndex, retry = active.retryCount)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PipelineState.Idle)

    // --- DUNG LƯỢNG BỘ NHỚ THẬT ---
    val phoneStorage: StateFlow<StorageInfo> = flow {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val avail = stat.availableBlocksLong * stat.blockSizeLong
        val used = total - avail
        emit(StorageInfo(
            usedGb = used / (1024f * 1024f * 1024f),
            totalGb = total / (1024f * 1024f * 1024f)
        ))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageInfo(0f, 0f))


    // --- CÁC HÀM XỬ LÝ (ĐÃ BỔ SUNG ĐẦY ĐỦ) ---
    val accounts = db.sourceAccountDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bổ sung lại để sửa lỗi PendingVideosScreen
    val readyVideos = db.processedVideoDao().observeByStatus(VideoStatus.READY_TO_PUBLISH.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bổ sung lại hàm cập nhật mô tả
    fun updateProcessedDescription(processed: ProcessedVideo, description: String, hashtags: List<String>) {
        viewModelScope.launch {
            db.processedVideoDao().update(processed.copy(descriptionVi = description, hashtags = hashtags))
        }
    }

    fun addAccount(platform: String, name: String, identifier: String) {
        viewModelScope.launch {
            db.sourceAccountDao().insert(SourceAccount(platform = platform, accountName = name, accountIdentifier = identifier))
        }
    }

    fun deleteAccount(account: SourceAccount) {
        viewModelScope.launch { db.sourceAccountDao().delete(account) }
    }

    fun downloadNow(video: SourceVideo) = DownloadWorker.enqueue(application, video.id)
    
    // Đã xóa hàm processNow vì đã chuyển xử lý sang Server

    fun publishNow(processed: ProcessedVideo, privacy: String, title: String) = PublishWorker.enqueue(application, processed.id, privacy, title)

    suspend fun getSourceVideo(id: String) = db.sourceVideoDao().findById(id)
    suspend fun getProcessedVideo(id: String) = db.processedVideoDao().findById(id)

    fun saveSetting(key: String, value: String) {
        viewModelScope.launch {
            when (key) {
                "scan_interval" -> value.toIntOrNull()?.let { prefs.set(AppPreferences.KEY_SCAN_INTERVAL, it) }
                "max_retry" -> value.toIntOrNull()?.let { prefs.set(AppPreferences.KEY_MAX_RETRY, it) }
                "default_hashtag" -> prefs.set(AppPreferences.KEY_DEFAULT_HASHTAG, value)
                "translation_api_key" -> prefs.set(AppPreferences.KEY_TRANSLATION_API_KEY, value)
                "drive_folder_id" -> prefs.set(AppPreferences.KEY_DRIVE_FOLDER_ID, value)
                "tiktok_client_key" -> prefs.set(AppPreferences.KEY_TIKTOK_CLIENT_KEY, value)
                "tiktok_client_secret" -> prefs.set(AppPreferences.KEY_TIKTOK_CLIENT_SECRET, value)
            }
        }
    }
}
