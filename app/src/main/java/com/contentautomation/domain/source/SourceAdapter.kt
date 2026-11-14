package com.contentautomation.domain.source

data class SourceVideoItem(
    val id: String,
    val url: String,
    val title: String?,
    val description: String?,
    val publishedAt: Long?
)

interface SourceAdapter {
    suspend fun getLatestVideos(accountIdentifier: String): List<SourceVideoItem>
    suspend fun getVideoMetadata(videoId: String): SourceVideoItem?
    suspend fun getDownloadUrl(videoId: String): String?
}

object SourceAdapterFactory {
    fun create(platform: String): SourceAdapter = when (platform.uppercase()) {
        "KUAISHOU" -> KuaishouAdapter()
        "DOUYIN" -> DouyinAdapter()
        else -> throw IllegalArgumentException("Unsupported platform: $platform")
    }
}
