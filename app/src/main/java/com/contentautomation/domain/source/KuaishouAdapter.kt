package com.contentautomation.domain.source

class KuaishouAdapter : YtDlpProfileAdapter() {
    override fun profileUrl(identifier: String): String =
        if (identifier.startsWith("http://") || identifier.startsWith("https://")) identifier
        else "https://www.kuaishou.com/profile/$identifier"

    override fun videoUrl(videoId: String): String =
        "https://www.kuaishou.com/short-video/$videoId"
}
