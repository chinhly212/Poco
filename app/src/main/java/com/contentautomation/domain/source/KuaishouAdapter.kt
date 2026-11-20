
package com.contentautomation.domain.source

import org.json.JSONObject

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
            if (e is SourceUnavailableException) throw e

            throw SourceUnavailableException(
                "Kuaishou scan failed: ${e.message}",
                e
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
            if (e is SourceUnavailableException) throw e

            throw SourceUnavailableException(
                "Kuaishou metadata failed: ${e.message}",
                e
            )
        }
    }

    override suspend fun getDownloadUrl(
        videoId: String
    ): String? = null

    private fun parseFromHtml(
        html: String
    ): List<SourceVideoItem> {

        val jsonText = extractJsonObject(
            html,
            "__APOLLO_STATE__"
        ) ?: throw SourceUnavailableException(
            "Kuaishou không tìm thấy dữ liệu __APOLLO_STATE__."
        )

        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            throw SourceUnavailableException(
                "Kuaishou trả về JSON không hợp lệ: ${e.message}",
                e
            )
        }

        val result = mutableListOf<SourceVideoItem>()
        val keys = root.keys()

        while (keys.hasNext()) {
            val key = keys.next()

            if (!key.startsWith("VisionVideoDetail")) {
                continue
            }

            val obj = root.optJSONObject(key) ?: continue

            val id = obj.optString("photoId")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: continue

            val caption = obj.optString("caption")
                .takeIf { it.isNotBlank() && it != "null" }

            val timestamp = obj.optLong("timestamp")
                .takeIf { it > 0 }

            result.add(
                SourceVideoItem(
                    id = id,
                    url = "https://www.kuaishou.com/short-video/$id",
                    title = caption,
                    description = caption,
                    publishedAt = timestamp
                )
            )
        }

        return result.distinctBy { it.id }
    }

    private fun extractJsonObject(
        source: String,
        marker: String
    ): String? {

        val markerIndex = source.indexOf(marker)
        if (markerIndex < 0) return null

        val start = source.indexOf('{', markerIndex)
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until source.length) {
            val char = source[i]

            if (inString) {
                if (escaped) {
                    escaped = false
                } else {
                    when (char) {
                        '\\' -> escaped = true
                        '"' -> inString = false
                    }
                }
                continue
            }

            when (char) {
                '"' -> inString = true
                '{' -> depth++

                '}' -> {
                    depth--

                    if (depth == 0) {
                        return source.substring(start, i + 1)
                    }

                    if (depth < 0) return null
                }
            }
        }

        return null
    }
}
