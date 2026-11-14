package com.contentautomation.ui

import com.contentautomation.data.prefs.AppPreferences
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.contentautomation.work.DownloadWorker
import com.contentautomation.work.ProcessWorker
import com.contentautomation.work.PublishWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardStats(
    val newVideos: Int = 0,
    val downloading: Int = 0,
    val processing: Int = 0,
    val readyToPublish: Int = 0,
    val published: Int = 0,
    val failed: Int = 0
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as ContentAutomationApp
    private val db = application.database
    private val prefs = application.prefs

    val accounts = db.sourceAccountDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val newVideos = db.sourceVideoDao().observeByStatus(VideoStatus.NEW.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readyVideos = db.processedVideoDao().observeByStatus(VideoStatus.READY_TO_PUBLISH.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = db.tiktokPostDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<DashboardStats> = combine(
        db.sourceVideoDao().countByStatus(VideoStatus.NEW.name),
        db.sourceVideoDao().countByStatus(VideoStatus.DOWNLOADING.name),
        db.sourceVideoDao().countByStatus(VideoStatus.PROCESSING_VIDEO.name),
        db.processedVideoDao().countByStatus(VideoStatus.READY_TO_PUBLISH.name),
        db.sourceVideoDao().countByStatus(VideoStatus.PUBLISHED.name),
        db.sourceVideoDao().countByStatus(VideoStatus.FAILED.name)
    ) { arr ->
        DashboardStats(
            newVideos = arr[0], downloading = arr[1], processing = arr[2],
            readyToPublish = arr[3], published = arr[4], failed = arr[5]
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    fun addAccount(platform: String, name: String, identifier: String) {
        viewModelScope.launch {
            db.sourceAccountDao().insert(SourceAccount(
                platform = platform, accountName = name, accountIdentifier = identifier
            ))
        }
    }

    fun toggleAccount(account: SourceAccount) {
        viewModelScope.launch {
            db.sourceAccountDao().update(account.copy(enabled = !account.enabled))
        }
    }

    fun deleteAccount(account: SourceAccount) {
        viewModelScope.launch { db.sourceAccountDao().delete(account) }
    }

    fun downloadNow(video: SourceVideo) = DownloadWorker.enqueue(application, video.id)
    fun processNow(video: SourceVideo) = ProcessWorker.enqueue(application, video.id)

    fun publishNow(processed: ProcessedVideo, privacy: String, title: String) =
        PublishWorker.enqueue(application, processed.id, privacy, title)

    fun updateProcessedDescription(processed: ProcessedVideo, description: String, hashtags: List<String>) {
        viewModelScope.launch {
            db.processedVideoDao().update(processed.copy(descriptionVi = description, hashtags = hashtags))
        }
    }

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
