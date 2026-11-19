package com.contentautomation.ui

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.contentautomation.ContentAutomationApp
import com.contentautomation.data.db.entity.*
import com.contentautomation.ui.models.*
import com.contentautomation.work.DownloadWorker
import com.contentautomation.work.PublishWorker
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.*
import io.github.jan.supabase.postgrest.query.filter.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class SystemStatus(
    val id: Int = 1,
    val is_running: Boolean = false
)

@Serializable
data class VideoQueue(
    val id: String,
    val platform: String,
    val video_url: String,
    val status: String,
    val current_step: Int,
    val step_name: String,
    val error_message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as ContentAutomationApp
    private val prefs = application.prefs
    private val supabase = application.supabase

    // ==============================
    // SYSTEM STATUS
    // ==============================

    private val _isSystemRunning = MutableStateFlow(false)
    val isSystemRunning = _isSystemRunning.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                try {
                    val status = supabase
                        .from("system_status")
                        .select()
                        .decodeSingleOrNull<SystemStatus>()

                    if (status != null) {
                        _isSystemRunning.value = status.is_running
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "Supabase",
                        "Lỗi lấy trạng thái hệ thống",
                        e
                    )
                }

                delay(3000)
            }
        }
    }

    fun setSystemRunning(isRunning: Boolean) {
        _isSystemRunning.value = isRunning

        viewModelScope.launch {
            try {
                supabase.from("system_status").update(
                    {
                        set("is_running", isRunning)
                    }
                ) {
                    filter {
                        eq("id", 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "ViewModel",
                    "Lỗi cập nhật trạng thái",
                    e
                )
            }
        }
    }

    // ==============================
    // VIDEO PIPELINE
    // ==============================

    private val _pipelineState =
        MutableStateFlow<PipelineState>(PipelineState.Idle)

    val pipelineState = _pipelineState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                try {
                    val videos = supabase
                        .from("video_queue")
                        .select {
                            filter {
                                isIn(
                                    "status",
                                    listOf("PENDING", "PROCESSING")
                                )
                            }

                            order(
                                column = "created_at",
                                order = Order.DESCENDING
                            )

                            limit(count = 1)
                        }
                        .decodeList<VideoQueue>()

                    if (videos.isEmpty()) {
                        _pipelineState.value = PipelineState.Idle
                    } else {
                        val video = videos.first()

                        _pipelineState.value = PipelineState.Running(
                            stepIndex = (video.current_step - 1)
                                .coerceIn(0, 12),
                            retry = 0
                        )
                    }

                } catch (e: Exception) {
                    android.util.Log.e(
                        "Supabase",
                        "Lỗi lấy tiến trình video",
                        e
                    )
                }

                delay(3000)
            }
        }
    }

    // ==============================
    // ERROR LIST
    // ==============================

    private val _uiErrors =
        MutableStateFlow<List<AppError>>(emptyList())

    val uiErrors = _uiErrors.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                try {
                    val failedVideos = supabase
                        .from("video_queue")
                        .select {
                            filter {
                                eq("status", "FAILED")
                            }

                            order(
                                column = "created_at",
                                order = Order.DESCENDING
                            )

                            limit(count = 5)
                        }
                        .decodeList<VideoQueue>()

                    val errors: List<AppError> =
                        failedVideos.map { video ->
                            AppError(
                                id = video.id,
                                kind = ErrorKind.VideoUnavailable,
                                title = "Lỗi xử lý video",
                                message = video.error_message
                                    ?: "Không rõ nguyên nhân",
                                time = "Gần đây",
                                actionLabel = "Thử lại"
                            )
                        }

                    _uiErrors.value = errors

                } catch (e: Exception) {
                    android.util.Log.e(
                        "Supabase",
                        "Lỗi lấy danh sách lỗi video",
                        e
                    )
                }

                delay(5000)
            }
        }
    }

    // ==============================
    // PHONE STORAGE
    // ==============================

    val phoneStorage: StateFlow<StorageInfo> = flow {

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
                usedGb = used / (1024f * 1024f * 1024f),
                totalGb = total / (1024f * 1024f * 1024f)
            )
        )

    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        StorageInfo(0f, 0f)
    )

    // ==============================
    // SOURCE ACCOUNTS
    // ==============================

    val accounts = application.database
        .sourceAccountDao()
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
            application.database
                .sourceAccountDao()
                .insert(
                    SourceAccount(
                        platform = platform,
                        accountName = name,
                        accountIdentifier = identifier
                    )
                )
        }
    }

    // ==============================
    // VIDEO ACTIONS
    // ==============================

    fun downloadNow(video: SourceVideo) {
        DownloadWorker.enqueue(application, video.id)
    }

    fun publishNow(
        processed: ProcessedVideo,
        privacy: String,
        title: String
    ) {
        PublishWorker.enqueue(
            application,
            processed.id,
            privacy,
            title
        )
    }
}
