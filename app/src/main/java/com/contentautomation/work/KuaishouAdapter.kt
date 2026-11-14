package com.contentautomation.domain.source

/**
 * Kuaishou adapter đọc JSON từ endpoint công khai / profile.
 * Nếu endpoint không khả dụng, ném SourceUnavailableException để worker
 * đánh dấu RETRY_PENDING (không crash app).
 */
class KuaishouAdapter : WebScrapingAdapter() {

    override suspend fun getLatestVideos(accountIdentifier: String): List<SourceVideoItem> {
        return try {
            val url = "https://www.kuaishou.com/graphql"
            // GraphQL công khai cần POST; minh hoạ GET fallback
            val body = httpGet("https://www.kuaishou.com/profile/$accountIdentifier")
            parseFromHtml(body)
        } catch (e: Exception) {
            throw SourceUnavailableException("Kuaishou scan failed: ${e.message}")
        }
    }

    override suspend fun getVideoMetadata(videoId: String): SourceVideoItem? =
        runCatching { getLatestVideos(videoId).firstOrNull() }.getOrNull()

    override suspend fun getDownloadUrl(videoId: String): String? = null

    private fun parseFromHtml(html: String): List<SourceVideoItem> {
        // Trích xuất JSON nhúng <script>window.__APOLLO_STATE__ = {...}</script>
        // Chỉ trả về metadata công khai, KHÔNG có URL video trực tiếp (bị chặn bởi ToS).
        val regex = Regex("window\\.__APOLLO_STATE__\\s*=\\s*(\\{.*?\\});", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(html) ?: return emptyList()
        return runCatching {
            val root = org.json.JSONObject(match.groupValues[1])
            val result = mutableListOf<SourceVideoItem>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("VisionVideoDetail")) {
                    val obj = root.optJSONObject(key) ?: continue
                    val id = obj.optString("photoId").ifBlank { continue }
                    result += SourceVideoItem(
                        id = id,
                        url = "https://www.kuaishou.com/short-video/$id",
                        title = obj.optString("caption"),
                        description = obj.optString("caption"),
                        publishedAt = obj.optLong("timestamp").takeIf { it > 0 }
                    )
                }
            }
            result
        }.getOrDefault(emptyList())
    }

    class SourceUnavailableException(msg: String) : Exception(msg)
}
