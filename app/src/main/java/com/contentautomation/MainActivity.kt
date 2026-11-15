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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as ContentAutomationApp
    private val prefs = application.prefs
    private val firestore: FirebaseFirestore = application.firestore

    private val listeners = mutableListOf<ListenerRegistration>()

    // TRẠNG THÁI HỆ THỐNG
    private val _isSystemRunning = MutableStateFlow(false)
    val isSystemRunning = _isSystemRunning.asStateFlow()

    // TIẾN TRÌNH VIDEO
    private val _pipelineState =
        MutableStateFlow<PipelineState>(PipelineState.Idle)
    val pipelineState = _pipelineState.asStateFlow()

    // DANH SÁCH LỖI
    private val _uiErrors = MutableStateFlow<List<AppError>>(emptyList())
    val uiErrors = _uiErrors.asStateFlow()

    init {
        observeSystemStatus()
        observeVideoQueue()
        observeVideoErrors()
    }

    // Theo dõi document system_status/1
    private fun observeSystemStatus() {
        val listener = firestore
            .collection("system_status")
            .document("1")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    android.util.Log.e(
                        "Firestore",
                        "Lỗi đọc trạng thái: ${error.message}"
                    )
                    return@addSnapshotListener
                }

                _isSystemRunning.value =
                    snapshot?.getBoolean("is_running") ?: false
            }

        listeners.add(listener)
    }

    // Cập nhật trạng thái hệ thống
    fun setSystemRunning(isRunning: Boolean) {
        _isSystemRunning.value = isRunning

        firestore.collection("system_status")
            .document("1")
            .set(
                mapOf("is_running" to isRunning),
                SetOptions.merge()
            )
            .addOnFailureListener { error ->
                android.util.Log.e(
                    "Firestore",
                    "Lỗi cập nhật trạng thái: ${error.message}"
                )
            }
    }

    // Theo dõi video đang xử lý
    private fun observeVideoQueue() {
        val listener = firestore
            .collection("video_queue")
            .whereIn("status", listOf("PENDING", "PROCESSING"))
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    android.util.Log.e(
                        "Firestore",
                        "Lỗi đọc hàng đợi: ${error.message}"
                    )
                    return@addSnapshotListener
                }

                val video = snapshot?.documents?.firstOrNull()

                if (video == null) {
                    _pipelineState.value = PipelineState.Idle
                    return@addSnapshotListener
                }

                val currentStep =
                    (video.getLong("current_step") ?: 1L).toInt()

                _pipelineState.value = PipelineState.Running(
                    stepIndex = (currentStep - 1).coerceIn(0, 12),
                    retry = 0
                )
            }

        listeners.add(listener)
    }

    // Theo dõi video bị lỗi
    private fun observeVideoErrors() {
        val listener = firestore
            .collection("video_queue")
            .whereEqualTo("status", "FAILED")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    android.util.Log.e(
                        "Firestore",
                        "Lỗi đọc danh sách lỗi: ${error.message}"
                    )
                    return@addSnapshotListener
                }

                _uiErrors.value = snapshot?.documents?.map { video ->
                    AppError(
                        id = video.id,
                        kind = ErrorKind.VideoUnavailable,
                        title = "Lỗi xử lý video",
                        message = video.getString("error_message")
                            ?: "Không rõ nguyên nhân",
                        time = "Gần đây",
                        actionLabel = "Thử lại"
                    )
                } ?: emptyList()
            }

        listeners.add(listener)
    }

    // DUNG LƯỢNG BỘ NHỚ ĐIỆN THOẠI
    val phoneStorage: StateFlow<StorageInfo> = flow {
        val stat = StatFs(Environment.getDataDirectory().path)

        val total = stat.blockCountLong * stat.blockSizeLong
        val avail = stat.availableBlocksLong * stat.blockSizeLong
        val used = total - avail

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

    // TÀI KHOẢN NGUỒN
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
            application.database.sourceAccountDao().insert(
                SourceAccount(
                    platform = platform,
                    accountName = name,
                    accountIdentifier = identifier
                )
            )
        }
    }

    // TẢI VIDEO
    fun downloadNow(video: SourceVideo) {
        DownloadWorker.enqueue(application, video.id)
    }

    // ĐĂNG VIDEO
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

    override fun onCleared() {
        listeners.forEach { it.remove() }
        listeners.clear()
        super.onCleared()
    }
}
