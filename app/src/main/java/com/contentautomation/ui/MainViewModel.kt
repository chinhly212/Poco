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
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

// Định nghĩa cấu trúc dữ liệu Supabase trả về
@Serializable
data class SystemStatus(val id: Int = 1, val is_running: Boolean = false)

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

    // --- TRẠNG THÁI HỆ THỐNG TỪ SUPABASE ---
    private val _isSystemRunning = MutableStateFlow(false)
    val isSystemRunning = _isSystemRunning.asStateFlow()

    init {
        // Tự động đồng bộ trạng thái nút nguồn từ Supabase mỗi 3 giây
        viewModelScope.launch {
            while (true) {
                try {
                    val status = supabase.from("system_status").select().decodeSingleOrNull<SystemStatus>()
                    if (status != null) {
                        _isSystemRunning.value = status.is_running
                    }
                } catch (e: Exception) {
                    // Bỏ qua lỗi mạng, thử lại sau
                }
                delay(3000)
            }
        }
    }

    fun setSystemRunning(isRunning: Boolean) {
        _isSystemRunning.value = isRunning // Cập nhật UI ngay lập tức
        viewModelScope.launch {
            try {
                // Ghi trạng thái lên Supabase
                supabase.from("system_status").update({
                    set("is_running", isRunning)
                }) {
                    filter { eq("id", 1) }
                }
            } catch (e: Exception) {
                android.util.Log.e("ViewModel", "Lỗi cập nhật trạng thái: ${e.message}")
            }
        }
    }

    // --- TIẾN TRÌNH 13 BƯỚC TỪ SUPABASE ---
    private val _pipelineState = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val pipelineState = _pipelineState.asStateFlow()

    init {
        // Liên tục hỏi Supabase xem có video nào đang chạy không
        viewModelScope.launch {
            while (true) {
                try {
                    // Lấy video mới nhất đang ở trạng thái PENDING hoặc PROCESSING
                    val list = supabase.from("video_queue")
                        .select {
                            filter {
                                isIn("status", listOf("PENDING", "PROCESSING"))
                            }
                            order("created_at", Order.DESCENDING)
                            limit(1)
                        }.decodeList<VideoQueue>()

                    if (list.isEmpty()) {
                        _pipelineState.value = PipelineState.Idle
                    } else {
                        val video = list.first()
                        // Map current_step (1-13) sang stepIndex (0-12) cho giao diện
                        _pipelineState.value = PipelineState.Running(
                            stepIndex = (video.current_step - 1).coerceIn(0, 12),
                            retry = 0
                        )
                    }
                } catch (e: Exception) {
                     // Bỏ qua lỗi mạng
                }
                delay(3000) // Cập nhật mỗi 3 giây
            }
        }
    }

    // --- LỖI TỪ SUPABASE ---
    private val _uiErrors = MutableStateFlow<List<AppError>>(emptyList())
    val uiErrors = _uiErrors.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                try {
                    val list = supabase.from("video_queue")
                        .select {
                            filter { eq("status", "FAILED") }
                            order("created_at", Order.DESCENDING)
                            limit(5)
                        }.decodeList<VideoQueue>()

                    _uiErrors.value = list.map { video ->
                        AppError(
                            id = video.id,
                            kind = ErrorKind.VideoUnavailable,
                            title = "Lỗi xử lý video",
                            message = video.error_message ?: "Không rõ nguyên nhân",
                            time = "Gần đây",
                            actionLabel = "Thử lại"
                        )
                    }
                } catch (e: Exception) {}
                delay(5000)
            }
        }
    }

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

    // --- CÁC HÀM XỬ LÝ CŨ ---
    val accounts = application.database.sourceAccountDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAccount(platform: String, name: String, identifier: String) {
        viewModelScope.launch {
            application.database.sourceAccountDao().insert(SourceAccount(platform = platform, accountName = name, accountIdentifier = identifier))
        }
    }

    fun downloadNow(video: SourceVideo) = DownloadWorker.enqueue(application, video.id)
    fun publishNow(processed: ProcessedVideo, privacy: String, title: String) = PublishWorker.enqueue(application, processed.id, privacy, title)
}
