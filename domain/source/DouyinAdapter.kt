package com.contentautomation.domain.source

class DouyinAdapter : WebScrapingAdapter() {

    override suspend fun getLatestVideos(accountIdentifier: String): List<SourceVideoItem> {
        return try {
            val html = httpGet("https://www.douyin.com/user/$accountIdentifier")
            parseFromHtml(html)
        } catch (e: Exception) {
            throw KuaishouAdapter.SourceUnavailableException("Douyin scan failed: ${e.message}")
        }
    }

    override suspend fun getVideoMetadata(videoId: String): SourceVideoItem? =
        runCatching { getLatestVideos(videoId).firstOrNull() }.getOrNull()

    override suspend fun getDownloadUrl(videoId: String): String? = null

    private fun parseFromHtml(html: String): List<SourceVideoItem> {
        val regex = Regex("_ROUTER_DATA\\s*=\\s*(\\{.*?});", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(html) ?: return emptyList()
        return runCatching {
            val json = org.json.JSONObject(match.groupValues[1])
            val result = mutableListOf<SourceVideoItem>()
            val walk = fun(any: Any?) {
                when (any) {
                    is org.json.JSONObject -> {
                        val id = any.optString("aweme_id").ifBlank { any.optString("awemeId") }
                        if (id.isNotBlank()) {
                            result += SourceVideoItem(
                                id = id,
                                url = "https://www.douyin.com/video/$id",
                                title = any.optString("desc"),
                                description = any.optString("desc"),
                                publishedAt = any.optLong("create_time").takeIf { it > 0 }?.times(1000)
                            )
                        }
                        any.keys().forEach { walk(any.opt(it)) }
                    }
                    is org.json.JSONArray -> for (i in 0 until any.length()) walk(any.opt(i))
                }
            }
            walk(json)
            result.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }
}
