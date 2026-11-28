package com.contentautomation.videoeditor.engine

import android.content.Context
import android.net.Uri
import com.contentautomation.videoeditor.domain.EditorStage
import com.contentautomation.videoeditor.domain.ProcessingProfile
import com.contentautomation.videoeditor.domain.SubtitleCue
import kotlinx.coroutines.flow.Flow

/**
 * Contracts for the on-device video localization pipeline.
 *
 * Implementations must report honest capability/availability and must never
 * return fabricated recognition, translation, voice or export results.
 */
data class EngineCapability(
    val id: String,
    val displayName: String,
    val available: Boolean,
    val onDevice: Boolean,
    val requiresNetwork: Boolean = false,
    val modelSizeBytes: Long? = null,
    val reasonUnavailable: String? = null
)

data class EngineProgress(
    val stage: EditorStage,
    val fraction: Float,
    val message: String
)

data class SpeechSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val speaker: String? = null
)

data class OcrSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class TranslationRequest(
    val sourceLanguage: String,
    val targetLanguage: String,
    val previousContext: List<String>,
    val nextContext: List<String>,
    val segments: List<SubtitleCue>
)

data class VoiceRequest(
    val text: String,
    val language: String,
    val voiceId: String,
    val speed: Float = 1f
)

data class ExportRequest(
    val videoUri: Uri,
    val subtitles: List<SubtitleCue>,
    val dubbedAudio: List<Uri>,
    val outputUri: Uri,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Int? = null
)

interface VideoSpeechRecognizer {
    suspend fun capability(context: Context): EngineCapability
    suspend fun recognize(videoUri: Uri, profile: ProcessingProfile): List<SpeechSegment>
}

interface VideoTextRecognizer {
    suspend fun capability(context: Context): EngineCapability
    suspend fun recognize(context: Context, videoUri: Uri, sampleIntervalMs: Long, profile: ProcessingProfile): List<OcrSegment>
}

interface ContextualTranslator {
    suspend fun capability(context: Context): EngineCapability
    suspend fun translate(request: TranslationRequest): List<SubtitleCue>
}

interface OnDeviceVoiceGenerator {
    suspend fun capability(context: Context): EngineCapability
    suspend fun voices(language: String): List<String>
    suspend fun generate(request: VoiceRequest, outputUri: Uri): Uri
}

interface VideoRenderer {
    suspend fun capability(context: Context): EngineCapability
    fun export(request: ExportRequest): Flow<EngineProgress>
}

data class DeviceResourceSnapshot(
    val availableMemoryBytes: Long,
    val totalMemoryBytes: Long,
    val lowMemory: Boolean,
    val thermalStatus: Int,
    val cpuCoreCount: Int,
    val supportedAbis: List<String>
)

interface DeviceResourceMonitor {
    fun snapshot(context: Context): DeviceResourceSnapshot
    fun recommendedProfile(snapshot: DeviceResourceSnapshot): ProcessingProfile
}
