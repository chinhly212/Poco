package com.contentautomation.videoeditor.engine

import com.contentautomation.video.AiVideoServerClient
import com.contentautomation.videoeditor.domain.SubtitleCue
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Uses the user's already-configured MuChi AI provider keys to translate subtitle
 * batches. The result is accepted only when every requested cue has a translation.
 */
class AiSubtitleTranslator(
    private val client: AiVideoServerClient = AiVideoServerClient()
) {
    suspend fun translate(
        cues: List<SubtitleCue>,
        sourceLanguage: String,
        targetLanguage: String,
        apiKeys: Map<String, String>
    ): List<SubtitleCue> {
        require(cues.isNotEmpty()) { "Chưa có phụ đề để dịch." }
        require(apiKeys.values.any { it.isNotBlank() }) {
            "Chưa cấu hình API AI. Hãy thêm Gemini, OpenRouter hoặc DeepSeek trong Cài đặt > API."
        }

        val translatedById = linkedMapOf<Long, String>()
        cues.sortedBy { it.startMs }.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            val payload = JSONArray()
            batch.forEach { cue ->
                payload.put(
                    JSONObject()
                        .put("id", cue.id)
                        .put("text", cue.sourceText)
                )
            }
            val prompt = buildString {
                appendLine("Bạn là biên dịch viên phụ đề video chuyên nghiệp.")
                appendLine("Dịch từ ngôn ngữ: $sourceLanguage sang: $targetLanguage.")
                appendLine("Giữ nguyên ý nghĩa, sắc thái, tên riêng và cách xưng hô theo ngữ cảnh.")
                appendLine("Không giải thích, không thêm lời dẫn, không bỏ dòng.")
                appendLine("Trả về duy nhất JSON hợp lệ theo mẫu: {\"translations\":[{\"id\":1,\"text\":\"bản dịch\"}]}")
                appendLine("Phải giữ nguyên id cho từng mục và trả đủ tất cả mục.")
                appendLine("Dữ liệu phụ đề:")
                append(payload.toString())
            }
            val response = client.sendChat(prompt, apiKeys).message
            val jsonText = response.substringAfter('{', "").let { "{" + it.substringBeforeLast('}', "") + "}" }
            val parsed = runCatching { JSONObject(jsonText) }
                .getOrElse { throw IOException("AI trả dữ liệu dịch không phải JSON hợp lệ (nhóm ${batchIndex + 1}).", it) }
            val translations = parsed.optJSONArray("translations")
                ?: throw IOException("AI không trả danh sách bản dịch (nhóm ${batchIndex + 1}).")
            for (index in 0 until translations.length()) {
                val item = translations.optJSONObject(index) ?: continue
                val id = item.optLong("id", Long.MIN_VALUE)
                val text = item.optString("text").trim()
                if (id != Long.MIN_VALUE && text.isNotBlank()) translatedById[id] = text
            }
            val missing = batch.filter { translatedById[it.id].isNullOrBlank() }
            if (missing.isNotEmpty()) {
                throw IOException("AI chưa dịch đủ ${missing.size} đoạn ở nhóm ${batchIndex + 1}; chưa áp dụng kết quả để tránh mất phụ đề.")
            }
        }

        return cues.map { cue ->
            cue.copy(translatedText = translatedById[cue.id]
                ?: throw IOException("Thiếu bản dịch cho phụ đề #${cue.id}."))
        }
    }

    private companion object {
        const val BATCH_SIZE = 12
    }
}
