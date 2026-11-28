package com.contentautomation.videoeditor.domain

/**
 * Editable project state. The media URI remains a content URI so Android's
 * Storage Access Framework can grant access without broad storage permission.
 */
data class VideoEditorProject(
    val id: String,
    val title: String,
    val sourceUri: String,
    val sourceDisplayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "vi",
    val subtitles: List<SubtitleCue> = emptyList(),
    val audioTracks: List<EditorAudioTrack> = emptyList(),
    val overlays: List<EditorOverlay> = emptyList(),
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

data class EditorAudioTrack(
    val id: String,
    val uri: String,
    val displayName: String,
    val startMs: Long = 0L,
    val volume: Float = 1f,
    val muted: Boolean = false
)

enum class OverlayKind {
    TEXT,
    LOGO,
    BLUR,
    MASK
}

data class EditorOverlay(
    val id: String,
    val kind: OverlayKind,
    val startMs: Long,
    val endMs: Long,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String? = null,
    val assetUri: String? = null
)

/** Device-aware processing presets; the engine still checks real capabilities. */
enum class ProcessingProfile {
    LOW_MEMORY,
    BALANCED,
    PERFORMANCE
}

enum class EditorStage {
    IMPORT,
    AUDIO_EXTRACTION,
    SPEECH_RECOGNITION,
    OCR_RECOGNITION,
    TRANSLATION,
    SUBTITLE_REVIEW,
    VOICE_GENERATION,
    AUDIO_MIX,
    VIDEO_EXPORT
}
