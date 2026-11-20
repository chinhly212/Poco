
package com.contentautomation.domain.source

class DouyinAdapter : WebScrapingAdapter() {

    override suspend fun getLatestVideos(
        accountIdentifier: String
    ): List<SourceVideoItem> {
        return try {
            val html = httpGet(
                "https://www.douyin.com/user/$accountIdentifier"
            )
            parseFromHtml(html)
        } catch (e: Exception) {
            throw SourceUnavailableException(
                "Douyin scan failed: ${e.message}"
            )
        }
    }

    override suspend fun getVideoMetadata(
        videoId: String
    ): SourceVideoItem? {
        if (videoId.isBlank()) return null

        return try {
            val html = httpGet(
                "https://www.douyin.com/video/$videoId"
            )

            parseFromHtml(html)
                .firstOrNull { it.id == videoId }
                ?: parseFromHtml(html).firstOrNull()

        } catch (e: Exception) {
            throw SourceUnavailableException(
                "Douyin metadata failed: ${e.message}"
            )
        }
    }

    override suspend fun getDownloadUrl(
        videoId: String
    ): String? = null

    private fun parseFromHtml(
        html: String
    ): List<SourceVideoItem> {

        val regex = Regex(
            "_ROUTER_DATA\\s*=\\s*(\\{.*?});",
            RegexOption.DOT_MATCHES_ALL
        )

        val match = regex.find(html)
            ?: throw SourceUnavailableException(
                "Douyin không tìm thấy dữ liệu _ROUTER_DATA. Có thể cấu trúc trang đã thay đổi hoặc nền tảng chặn truy cập."
            )

        val json = try {
            org.json.JSONObject(match.groupValues[1])
        } catch (e: Exception) {
            throw SourceUnavailableException(
                "Douyin trả về dữ liệu JSON không hợp lệ: ${e.message}"
            )
        }

        val result = mutableListOf<SourceVideoItem>()

        fun walk(any: Any?) {
            when (any) {
                is org.json.JSONObject -> {
                    val id = any.optString("aweme_id")
                        .ifBlank {
                            any.optString("awemeId")
                        }

                    if (id.isNotBlank() && id != "null") {
                        result.add(
                            SourceVideoItem(
                                id = id,
                                url = "https://www.douyin.com/video/$id",
                                title = any.optString("desc")
                                    .takeIf { it.isNotBlank() },
                                description = any.optString("desc")
                                    .takeIf { it.isNotBlank() },
                                publishedAt = any.optLong(
                                    "create_time"
                                ).takeIf { it > 0 }
                                    ?.times(1000)
                            )
                        )
                    }

                    val keys = any.keys()

                    while (keys.hasNext()) {
                        walk(any.opt(keys.next()))
                    }
                }

                is org.json.JSONArray -> {
                    for (i in 0 until any.length()) {
                        walk(any.opt(i))
                    }
                }
            }
        }

        walk(json)

        return result.distinctBy { it.id }
    }
}
