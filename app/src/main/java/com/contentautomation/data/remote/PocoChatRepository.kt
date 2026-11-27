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

data class PocoChatProfile(val userId: String, val accountId: String, val displayName: String, val username: String)
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

class PocoChatRepository(context: Context, private val auth: SupabaseAuth = SupabaseAuth(context.applicationContext)) {
    companion object {
        private const val BASE = "https://odxgthsecijktkkubjwf.supabase.co"
        private const val KEY = "sb_publishable_PA474FOyjVttQPBnAfsGAA_nkEVbAdI"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient()

    private fun token(): String = auth.currentAccessToken()?.takeIf { it.isNotBlank() }
        ?: error("Phiên POCO đã hết hạn. Hãy đăng nhập lại.")

    private suspend fun request(path: String, method: String = "GET", body: String? = null,
                                prefer: String? = null): String = withContext(Dispatchers.IO) {
        fun buildRequest(accessToken: String): Request {
            val builder = Request.Builder().url(BASE + path)
                .header("apikey", KEY)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
            if (prefer != null) builder.header("Prefer", prefer)
            when (method) {
                "POST" -> builder.post((body ?: "{}").toRequestBody(JSON))
                "PATCH" -> builder.patch((body ?: "{}").toRequestBody(JSON))
                "DELETE" -> builder.delete((body ?: "").toRequestBody(JSON))
                else -> builder.get()
            }
            return builder.build()
        }

        var accessToken = token()
        var response = client.newCall(buildRequest(accessToken)).execute()
        var responseText = response.body?.string().orEmpty()

        // Supabase đôi khi trả 403 bad_jwt thay vì 401 khi access token cũ.
        // Chỉ refresh với 403 có dấu hiệu lỗi JWT; không refresh lỗi RLS/quyền truy cập.
        val authRejected = response.code == 401 ||
            (response.code == 403 && runCatching {
                val json = JSONObject(responseText)
                val detail = listOf(
                    json.optString("code"),
                    json.optString("error"),
                    json.optString("error_code"),
                    json.optString("message"),
                    json.optString("msg"),
                    json.optString("details")
                ).joinToString(" ").lowercase()
                detail.contains("jwt") || detail.contains("token") ||
                    detail.contains("bad_jwt") || detail.contains("invalid_credentials")
            }.getOrDefault(false))

        if (authRejected) {
            response.close()
            accessToken = auth.refreshSession().getOrElse { refreshError ->
                error("Phiên MuChi không còn hợp lệ (HTTP ${response.code}) và không thể làm mới: ${refreshError.message ?: "hãy đăng nhập lại"}.")
            }.accessToken
            response = client.newCall(buildRequest(accessToken)).execute()
            responseText = response.body?.string().orEmpty()
        }

        response.use {
            if (!it.isSuccessful) {
                val detail = runCatching {
                    val json = JSONObject(responseText)
                    listOf("message", "msg", "error_description", "details", "hint", "error", "code")
                        .firstNotNullOfOrNull { key -> json.optString(key).takeIf(String::isNotBlank) }
                }.getOrDefault("")
                error((detail ?: "").ifBlank { "Máy chủ POCO trả HTTP ${it.code}" })
            }
            responseText
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    suspend fun myUserId(): String {
        val user = JSONObject(request("/auth/v1/user"))
        return user.getString("id")
    }

    suspend fun myAccountId(): String {
        val userId = myUserId()
        val rows = JSONArray(request("/rest/v1/poco_profiles?select=account_id&user_id=eq.$userId&limit=1"))
        if (rows.length() == 0) error("Tài khoản chưa có ID MuChi. Hãy cập nhật cơ sở dữ liệu Supabase.")
        return rows.getJSONObject(0).getString("account_id")
    }

    suspend fun searchUsers(term: String): List<PocoChatProfile> {
        val input = term.trim()
        if (input.length < 2) return emptyList()
        val idPattern = Regex("^MC-A[0-9]{6}$", RegexOption.IGNORE_CASE)
        val path = if (idPattern.matches(input)) {
            "/rest/v1/poco_profiles?select=user_id,account_id,display_name,username&account_id=eq.${enc(input.uppercase())}&limit=25"
        } else {
            val safe = input.replace(Regex("[^\\p{L}\\p{N}_ ]"), " ").replace(Regex("\\s+"), " ").trim()
            if (safe.length < 2) return emptyList()
            val filter = "(display_name.ilike.*$safe*,username.ilike.*$safe*)"
            "/rest/v1/poco_profiles?select=user_id,account_id,display_name,username&or=${enc(filter)}&limit=25"
        }
        val rows = JSONArray(request(path))
        return (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            PocoChatProfile(
                o.getString("user_id"),
                o.getString("account_id"),
                o.optString("display_name"),
                o.optString("username")
            )
        }
    }

    suspend fun conversations(userId: String): List<PocoChatConversation> {
        val memberships = JSONArray(request("/rest/v1/poco_conversation_members?select=conversation_id&user_id=eq.$userId"))
        if (memberships.length() == 0) return emptyList()
        val ids = (0 until memberships.length()).map { memberships.getJSONObject(it).getString("conversation_id") }
        val inFilter = ids.joinToString(",", "(", ")")
        val rows = JSONArray(request("/rest/v1/poco_conversations?select=id,title,is_group,updated_at&id=in.${enc(inFilter)}&order=updated_at.desc&limit=100"))

        val members = JSONArray(request("/rest/v1/poco_conversation_members?select=conversation_id,user_id&conversation_id=in.${enc(inFilter)}"))
        val peerByConversation = mutableMapOf<String, String>()
        for (i in 0 until members.length()) {
            val member = members.getJSONObject(i)
            val conversationId = member.optString("conversation_id")
            val memberId = member.optString("user_id")
            if (memberId.isNotBlank() && memberId != userId) peerByConversation.putIfAbsent(conversationId, memberId)
        }

        val peerIds = peerByConversation.values.distinct()
        val profilesByUser = mutableMapOf<String, PocoChatProfile>()
        if (peerIds.isNotEmpty()) {
            val profileFilter = peerIds.joinToString(",", "(", ")")
            val profiles = JSONArray(request("/rest/v1/poco_profiles?select=user_id,account_id,display_name,username&user_id=in.${enc(profileFilter)}"))
            for (i in 0 until profiles.length()) {
                val p = profiles.getJSONObject(i)
                val profile = PocoChatProfile(p.getString("user_id"), p.optString("account_id"), p.optString("display_name"), p.optString("username"))
                profilesByUser[profile.userId] = profile
            }
        }

        val lastMessageByConversation = mutableMapOf<String, String>()
        val messageRows = JSONArray(request("/rest/v1/poco_messages?select=conversation_id,body&conversation_id=in.${enc(inFilter)}&deleted_at=is.null&order=created_at.desc&limit=100"))
        for (i in 0 until messageRows.length()) {
            val message = messageRows.getJSONObject(i)
            val conversationId = message.optString("conversation_id")
            if (conversationId !in lastMessageByConversation) {
                lastMessageByConversation[conversationId] = message.optString("body")
            }
        }

        return (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            val id = o.getString("id")
            val group = o.optBoolean("is_group")
            val peer = profilesByUser[peerByConversation[id]]
            val profileTitle = peer?.displayName?.takeIf { it.isNotBlank() }
                ?: peer?.accountId?.takeIf { it.isNotBlank() }
                ?: peer?.username?.takeIf { it.isNotBlank() }
                ?: peer?.userId
            val title = if (group) o.optString("title").ifBlank { "Nhóm POCO" }
                else profileTitle ?: o.optString("title").ifBlank { "Cuộc trò chuyện" }
            PocoChatConversation(id, title, group, o.optString("updated_at"), lastMessageByConversation[id].orEmpty())
        }
    }

    suspend fun conversationPeer(conversationId: String, myUserId: String): PocoChatProfile? {
        val members = JSONArray(request("/rest/v1/poco_conversation_members?select=user_id&conversation_id=eq.$conversationId"))
        val peerId = (0 until members.length())
            .map { members.getJSONObject(it).optString("user_id") }
            .firstOrNull { it.isNotBlank() && it != myUserId } ?: return null
        val rows = JSONArray(request("/rest/v1/poco_profiles?select=user_id,account_id,display_name,username&user_id=eq.$peerId&limit=1"))
        if (rows.length() == 0) return null
        val profile = rows.getJSONObject(0)
        return PocoChatProfile(
            profile.getString("user_id"),
            profile.optString("account_id"),
            profile.optString("display_name"),
            profile.optString("username")
        )
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
        return (org.json.JSONTokener(raw).nextValue() as? String)
            ?: error("Máy chủ không trả mã cuộc trò chuyện hợp lệ.")
    }

    suspend fun createGroup(title: String, memberIds: List<String>): String {
        val ids = JSONArray()
        memberIds.distinct().forEach(ids::put)
        val payload = JSONObject().put("group_title", title.trim()).put("member_user_ids", ids)
        val raw = request("/rest/v1/rpc/poco_create_group", "POST", payload.toString())
        return (org.json.JSONTokener(raw).nextValue() as? String)
            ?: error("Máy chủ không trả mã nhóm hợp lệ.")
    }
}
