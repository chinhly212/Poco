package com.contentautomation.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class MuChiAccountStatus(
    val plan: String,
    val proExpiresAt: String?,
    val proPermanent: Boolean,
    val status: String,
    val banReason: String?,
    val dailyUsed: Int
) {
    val isPro: Boolean get() = plan.equals("pro", true)
    val isBanned: Boolean get() = status.equals("banned", true)
}

class UserSupportRepository(
    context: Context,
    private val auth: SupabaseAuth = SupabaseAuth(context.applicationContext)
) {
    companion object {
        private const val BASE = "https://odxgthsecijktkkubjwf.supabase.co"
        private const val KEY = "sb_publishable_PA474FOyjVttQPBnAfsGAA_nkEVbAdI"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient()

    private suspend fun post(payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val token = auth.currentAccessToken()?.takeIf { it.isNotBlank() }
            ?: error("Phiên MuChi đã hết hạn. Hãy đăng nhập lại.")
        val request = Request.Builder()
            .url("$BASE/functions/v1/user-support")
            .header("apikey", KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val result = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val reason = result.optString("error").ifBlank { "HTTP ${response.code}" }
                error("Yêu cầu máy chủ thất bại: $reason")
            }
            result
        }
    }

    suspend fun accountStatus(): MuChiAccountStatus {
        val json = post(JSONObject().put("action", "account_status"))
        return MuChiAccountStatus(
            plan = json.optString("plan", "free"),
            proExpiresAt = json.optString("proExpiresAt").takeIf { it.isNotBlank() && it != "null" },
            proPermanent = json.optBoolean("proPermanent"),
            status = json.optString("status", "active"),
            banReason = json.optString("banReason").takeIf { it.isNotBlank() && it != "null" },
            dailyUsed = json.optInt("dailyUsed", 0)
        )
    }

    suspend fun reportError(
        message: String,
        httpStatus: Int? = null,
        errorCode: String? = null,
        source: String = "android"
    ): String {
        require(message.isNotBlank()) { "Nội dung lỗi không được để trống." }
        val result = post(JSONObject()
            .put("action", "report_error")
            .put("message", message.take(2000))
            .put("httpStatus", httpStatus ?: JSONObject.NULL)
            .put("errorCode", errorCode ?: JSONObject.NULL)
            .put("source", source.take(80)))
        return result.optString("id")
    }

    suspend fun createTicket(category: String, subject: String, message: String): String {
        require(message.isNotBlank()) { "Nội dung hỗ trợ không được để trống." }
        val result = post(JSONObject()
            .put("action", "create_ticket")
            .put("category", category.take(60))
            .put("subject", subject.take(200))
            .put("message", message.take(5000)))
        return result.optString("id")
    }
}
