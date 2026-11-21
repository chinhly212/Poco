package com.contentautomation.domain.source

class DouyinAdapter : YtDlpProfileAdapter() {
    override fun profileUrl(identifier: String): String =
        if (identifier.startsWith("http://") || identifier.startsWith("https://")) identifier
        else "https://www.douyin.com/user/$identifier"

    override fun videoUrl(videoId: String): String =
        "https://www.douyin.com/video/$videoId"
}
