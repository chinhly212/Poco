package com.contentautomation.videoeditor.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.contentautomation.videoeditor.domain.ProcessingProfile
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max

class MlKitVideoTextRecognizer : VideoTextRecognizer {
    override suspend fun capability(context: Context) = EngineCapability(
        id = "mlkit-video-ocr-latin-chinese",
        displayName = "ML Kit OCR (Latin/Vietnamese + Chinese)",
        available = true,
        onDevice = true,
        requiresNetwork = false
    )

    override suspend fun recognize(
        context: Context,
        videoUri: Uri,
        sampleIntervalMs: Long,
        profile: ProcessingProfile,
        sourceLanguage: String
    ): List<OcrSegment> {
        require(sampleIntervalMs > 0L) { "Khoảng lấy mẫu phải lớn hơn 0." }
        val language = sourceLanguage.trim().lowercase()
        val chineseOnly = language.contains("中文") || language.contains("chinese") ||
            language == "zh" || language.startsWith("zh-")
        val latinOnly = language.contains("latin") || language.contains("việt") ||
            language.contains("vietnamese") || language == "vi" || language.startsWith("vi-")
        val recognizers = mutableListOf<TextRecognizer>()
        if (!chineseOnly) recognizers += TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        if (!latinOnly) recognizers += TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            if (durationMs <= 0L) return emptyList()

            val limits = when (profile) {
                ProcessingProfile.LOW_MEMORY -> 2500L to 900L
                ProcessingProfile.BALANCED -> 1500L to 1800L
                ProcessingProfile.PERFORMANCE -> 1000L to 3600L
            }
            val intervalMs = maxOf(sampleIntervalMs, limits.first, durationMs / limits.second)
            val segments = mutableListOf<OcrSegment>()
            var timeMs = 0L

            while (timeMs < durationMs) {
                val original = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (original != null) {
                    val bitmap = downscale(original, 1280)
                    try {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        recognizers.forEach { recognizer ->
                            val result = recognizer.process(image).await()
                            result.textBlocks.forEach { block ->
                                val text = block.text.trim()
                                val bounds = block.boundingBox
                                if (text.isNotEmpty() && bounds != null) {
                                    segments += OcrSegment(
                                        startMs = timeMs,
                                        endMs = (timeMs + intervalMs).coerceAtMost(durationMs),
                                        text = text,
                                        left = (bounds.left.toFloat() / bitmap.width).coerceIn(0f, 1f),
                                        top = (bounds.top.toFloat() / bitmap.height).coerceIn(0f, 1f),
                                        right = (bounds.right.toFloat() / bitmap.width).coerceIn(0f, 1f),
                                        bottom = (bounds.bottom.toFloat() / bitmap.height).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        }
                    } finally {
                        if (bitmap !== original) bitmap.recycle()
                        original.recycle()
                    }
                }
                timeMs += intervalMs
            }
            return mergeAdjacentDuplicates(segments.sortedBy { it.startMs })
        } finally {
            recognizers.forEach { it.close() }
            retriever.release()
        }
    }

    private fun downscale(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= maxDimension) return source
        val scale = maxDimension.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun mergeAdjacentDuplicates(input: List<OcrSegment>): List<OcrSegment> {
        val output = mutableListOf<OcrSegment>()
        input.forEach { current ->
            val previous = output.lastOrNull()
            if (previous != null && previous.text.equals(current.text, ignoreCase = true) &&
                current.startMs <= previous.endMs + 500L
            ) {
                output[output.lastIndex] = previous.copy(endMs = max(previous.endMs, current.endMs))
            } else output += current
        }
        return output
    }
}
