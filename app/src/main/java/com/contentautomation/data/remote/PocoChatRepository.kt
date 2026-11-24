package com.contentautomation.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PocoChatProfile(val userId: String, val displayName: String, val username: String)
data class PocoChatConversation(
    val id: String,
    val title: String,
    val isGroup: Boolean,
    val updatedAt: String,
    val lastMessage: String = ""
)
data class PocoChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAt: String
)

class PocoChatRepository(context: Context) {
    companion object {
        private const val BASE = "https://odxgthsecijktkkubjwf.supabase.co"
        private const val KEY = "sb_publishable_PA474FOyjVttQPBnAfsGAA_nkEVbAdI"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val auth = SupabaseAuth(context.applicationContext)
    private val client = OkHttpClient()

    private fun token(): String = auth.currentAccessToken()?.takeIf { it.isNotBlank() }
        ?: error("Phiên POCO đã hết hạn. Hãy đăng nhập lại.")

    private suspend fun request(path: String, method: String = "GET", body: String? = null,
                                prefer: String? = null): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(BASE + path)
            .header("apikey", KEY)
            .header("Authorization", "Bearer ${token()}")
            .header("Accept", "application/json")
        if (prefer != null) builder.header("Prefer", prefer)
        when (method) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(JSON))
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody(JSON))
            "DELETE" -> builder.delete((body ?: "").toRequestBody(JSON))
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("details") }
                }.getOrDefault("")
                error(detail.ifBlank { "Máy chủ POCO trả HTTP ${response.code}" })
            }
            text
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    suspend fun myUserId(): String {
        val user = JSONObject(request("/auth/v1/user"))
        return user.getString("id")
    }

    suspend fun searchUsers(term: String): List<PocoChatProfile> {
        val safe = term.trim().replace("%", "").replace(",", " ")
        if (safe.length < 2) return emptyList()
        val filter = "(display_name.ilike.*$safe*,username.ilike.*$safe*)"
        val rows = JSONArray(request("/rest/v1/poco_profiles?select=user_id,display_name,username&or=${enc(filter)}&limit=25"))
        return (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            PocoChatProfile(o.getString("user_id"), o.optString("display_name"), o.optString("username"))
        }
    }

    suspend fun conversations(userId: String): List<PocoChatConversation> {
        val memberships = JSONArray(request("/rest/v1/poco_conversation_members?select=conversation_id&user_id=eq.$userId"))
        if (memberships.length() == 0) return emptyList()
        val ids = (0 until memberships.length()).map { memberships.getJSONObject(it).getString("conversation_id") }
        val inFilter = ids.joinToString(",", "(", ")")
        val rows = JSONArray(request("/rest/v1/poco_conversations?select=id,title,is_group,updated_at&id=in.${enc(inFilter)}&order=updated_at.desc&limit=100"))
        (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            val id = o.getString("id")
            val group = o.optBoolean("is_group")
            val title = o.optString("title").ifBlank {
                if (group) "Nhóm POCO" else "Cuộc trò chuyện"
            }
            PocoChatConversation(id, title, group, o.optString("updated_at"))
        }
    }

    suspend fun messages(conversationId: String): List<PocoChatMessage> {
        val rows = JSONArray(request("/rest/v1/poco_messages?select=id,conversation_id,sender_id,body,created_at&conversation_id=eq.$conversationId&deleted_at=is.null&order=created_at.asc&limit=300"))
        return (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            PocoChatMessage(o.getString("id"), o.getString("conversation_id"),
                o.getString("sender_id"), o.getString("body"), o.getString("created_at"))
        }
    }

    suspend fun sendMessage(conversationId: String, userId: String, body: String) {
        val text = body.trim()
        require(text.isNotEmpty() && text.length <= 10000) { "Tin nhắn phải có từ 1 đến 10.000 ký tự." }
        val payload = JSONObject().put("conversation_id", conversationId)
            .put("sender_id", userId).put("body", text)
        request("/rest/v1/poco_messages", "POST", payload.toString(), "return=minimal")
    }

    suspend fun createDirect(otherUserId: String): String {
        val raw = request("/rest/v1/rpc/poco_create_direct_conversation", "POST",
            JSONObject().put("other_user_id", otherUserId).toString())
        (org.json.JSONTokener(raw).nextValue() as? String)
            ?: error("Máy chủ không trả mã cuộc trò chuyện hợp lệ.")
    }

    suspend fun createGroup(title: String, memberIds: List<String>): String {
        val ids = JSONArray()
        memberIds.distinct().forEach(ids::put)
        val payload = JSONObject().put("group_title", title.trim()).put("member_user_ids", ids)
        val raw = request("/rest/v1/rpc/poco_create_group", "POST", payload.toString())
        (org.json.JSONTokener(raw).nextValue() as? String)
            ?: error("Máy chủ không trả mã nhóm hợp lệ.")
    }
}
