
package com.contentautomation.domain.source

class KuaishouAdapter : WebScrapingAdapter() {

    override suspend fun getLatestVideos(
        accountIdentifier: String
    ): List<SourceVideoItem> {
        return try {
            val html = httpGet(
                "https://www.kuaishou.com/profile/$accountIdentifier"
            )

            parseFromHtml(html)

        } catch (e: Exception) {
            throw SourceUnavailableException(
                "Kuaishou scan failed: ${e.message}"
            )
        }
    }

    override suspend fun getVideoMetadata(
        videoId: String
    ): SourceVideoItem? {
        if (videoId.isBlank()) return null

        return try {
            val html = httpGet(
                "https://www.kuaishou.com/short-video/$videoId"
            )

            parseFromHtml(html)
                .firstOrNull { it.id == videoId }

        } catch (e: Exception) {
            throw SourceUnavailableException(
                "Kuaishou metadata failed: ${e.message}"
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
            "window\\.__APOLLO_STATE__\\s*=\\s*(\\{.*?\\});",
            RegexOption.DOT_MATCHES_ALL
        )

        val match = regex.find(html)
            ?: return emptyList()

        return runCatching {
            val root = org.json.JSONObject(
                match.groupValues[1]
            )

            val result = mutableListOf<SourceVideoItem>()
            val keys = root.keys()

            while (keys.hasNext()) {
                val key = keys.next()

                if (!key.startsWith("VisionVideoDetail")) {
                    continue
                }

                val obj = root.optJSONObject(key)
                    ?: continue

                val id = obj.optString("photoId")

                if (id.isBlank()) {
                    continue
                }

                result.add(
                    SourceVideoItem(
                        id = id,
                        url = "https://www.kuaishou.com/short-video/$id",
                        title = obj.optString("caption"),
                        description = obj.optString("caption"),
                        publishedAt = obj.optLong("timestamp")
                            .takeIf { it > 0 }
                    )
                )
            }

            result.distinctBy { it.id }

        }.getOrDefault(emptyList())
    }
}
