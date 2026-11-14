package com.contentautomation.domain.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Adapter cơ sở đọc HTML/JSON công khai từ trang profile.
 * KHÔNG bypass CAPTCHA, KHÔNG vượt rate-limit, KHÔNG giả mạo user agent để tránh chặn.
 * Người dùng phải tự đảm bảo tuân thủ ToS của nền tảng.
 */
abstract class WebScrapingAdapter : SourceAdapter {
    protected val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    protected suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code} for $url")
            resp.body?.string() ?: ""
        }
    }

    protected fun parseJsonArray(json: String, key: String): JSONArray {
        val obj = JSONObject(json)
        return obj.optJSONArray(key) ?: JSONArray()
    }
}
