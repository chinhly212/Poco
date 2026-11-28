package com.contentautomation.videoeditor.domain

/**
 * Android-native subtitle cue. Times are stored in milliseconds to avoid
 * floating-point drift while editing and exporting subtitle timelines.
 */
data class SubtitleCue(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val sourceText: String,
    val translatedText: String = "",
    val speaker: String? = null,
    val voiceAudioPath: String? = null
) {
    init {
        require(startMs >= 0L) { "Subtitle start time cannot be negative." }
        require(endMs > startMs) { "Subtitle end time must be after its start time." }
    }

    fun displayText(useTranslation: Boolean = true): String =
        if (useTranslation && translatedText.isNotBlank()) translatedText else sourceText
}

/** SRT import/export shared by recognition, translation, dubbing and editor screens. */
object SrtCodec {
    private val timestampPattern =
        Regex("""^(\d{2,}):(\d{2}):(\d{2})[,.](\d{3})$""")

    fun parse(content: String): List<SubtitleCue> {
        if (content.isBlank()) return emptyList()

        val normalized = content.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val blocks = Regex("""\n\s*\n""").split(normalized.trim())
        val cues = mutableListOf<SubtitleCue>()

        blocks.forEachIndexed { index, block ->
            val lines = block.lines()
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex < 0) return@forEachIndexed

            val timing = lines[timingIndex].split("-->", limit = 2)
            if (timing.size != 2) return@forEachIndexed
            val start = parseTimestamp(timing[0].trim()) ?: return@forEachIndexed
            val end = parseTimestamp(timing[1].trim().substringBefore(' ').trim())
                ?: return@forEachIndexed
            if (end <= start) return@forEachIndexed

            val text = lines.drop(timingIndex + 1).joinToString("\n").trim()
            if (text.isBlank()) return@forEachIndexed

            cues += SubtitleCue(
                id = index.toLong() + 1L,
                startMs = start,
                endMs = end,
                sourceText = text
            )
        }
        return cues.sortedWith(compareBy<SubtitleCue> { it.startMs }.thenBy { it.endMs })
    }

    fun format(cues: List<SubtitleCue>, useTranslation: Boolean = false): String =
        cues.sortedWith(compareBy<SubtitleCue> { it.startMs }.thenBy { it.endMs })
            .mapIndexedNotNull { index, cue ->
                val text = cue.displayText(useTranslation).trim()
                if (text.isBlank()) return@mapIndexedNotNull null
                buildString {
                    append(index + 1)
                    append('\n')
                    append(formatTimestamp(cue.startMs))
                    append(" --> ")
                    append(formatTimestamp(cue.endMs))
                    append('\n')
                    append(text)
                }
            }
            .joinToString("\n\n")
            .let { if (it.isEmpty()) "" else "$it\n" }

    fun formatTimestamp(milliseconds: Long): String {
        val value = milliseconds.coerceAtLeast(0L)
        val hours = value / 3_600_000L
        val minutes = (value % 3_600_000L) / 60_000L
        val seconds = (value % 60_000L) / 1_000L
        val millis = value % 1_000L
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }

    private fun parseTimestamp(value: String): Long? {
        val match = timestampPattern.matchEntire(value) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val millis = match.groupValues[4].toLongOrNull() ?: return null
        if (minutes > 59L || seconds > 59L) return null
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis
    }
}
