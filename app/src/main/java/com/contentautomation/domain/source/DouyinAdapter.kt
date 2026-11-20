package com.contentautomation.domain.source

import kotlinx.coroutines.CancellationException

class DouyinAdapter : WebScrapingAdapter() {

    override suspend fun getLatestVideos(
        accountIdentifier: String
    ): List<SourceVideoItem> {
        if (accountIdentifier.isBlank()) {
            throw SourceUnavailableException("Douyin account ID không được để trống.")
        }

        return try {
            val html = httpGet(
                "https://www.douyin.com/user/$accountIdentifier"
            )
            parseVideos(html)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is SourceUnavailableException) throw e

            throw SourceUnavailableException(
                "Không thể lấy danh sách video Douyin: ${e.message}",
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
                "https://www.douyin.com/video/$videoId"
            )

            parseVideos(html).firstOrNull { it.id == videoId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is SourceUnavailableException) throw e

            throw SourceUnavailableException(
                "Không thể lấy metadata Douyin: ${e.message}",
                e
            )
        }
    }

    override suspend fun getDownloadUrl(
        videoId: String
    ): String? = null

    private fun parseVideos(html: String): List<SourceVideoItem> {
        val idRegex = Regex(
            "\"aweme_id\"\\s*:\\s*\"(\\d+)\""
        )
        val descRegex = Regex(
            "\"desc\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\""
        )
        val timeRegex = Regex(
            "\"create_time\"\\s*:\\s*(\\d+)"
        )

        val ids = idRegex.findAll(html).toList()

        if (ids.isEmpty()) {
            throw SourceUnavailableException(
                "Douyin không trả về danh sách aweme_id hợp lệ. " +
                    "Trang có thể yêu cầu đăng nhập hoặc đã thay đổi cấu trúc."
            )
        }

        val result = mutableListOf<SourceVideoItem>()

        for (match in ids.distinctBy { it.groupValues[1] }) {
            val id = match.groupValues[1]
            val start = match.range.first
            val end = minOf(html.length, start + 12000)
            val section = html.substring(start, end)

            val description = descRegex.find(section)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace("\\\"", "\"")
                ?.replace("\\n", "\n")
                ?.takeIf { it.isNotBlank() }

            val publishedAt = timeRegex.find(section)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?.let { if (it > 0) it * 1000L else null }

            result.add(
                SourceVideoItem(
                    id = id,
                    url = "https://www.douyin.com/video/$id",
                    title = description,
                    description = description,
                    publishedAt = publishedAt
                )
            )
        }

        return result
    }
}
