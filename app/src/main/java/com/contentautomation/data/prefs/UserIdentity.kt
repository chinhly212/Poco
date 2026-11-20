
package com.contentautomation.data.prefs

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

object UserIdentity {

    private const val PREFS_NAME = "poco_user_identity"
    private const val KEY_USER_ID = "user_id"

    /**
     * Lấy ID đã lưu.
     * Nếu chưa có thì tạo ID từ Android ID.
     */
    @Synchronized
    fun getOrCreate(context: Context): String {
        val appContext = context.applicationContext

        val prefs = appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        // Ưu tiên ID đã lưu trong app.
        val savedId = prefs.getString(KEY_USER_ID, null)

        if (!savedId.isNullOrBlank()) {
            return savedId
        }

        // Lấy Android ID của thiết bị.
        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // Tạo ID ổn định từ Android ID.
        val userId = if (!androidId.isNullOrBlank()) {
            val digest = MessageDigest
                .getInstance("SHA-256")
                .digest(androidId.toByteArray(Charsets.UTF_8))

            val hash = digest.joinToString("") {
                "%02x".format(it)
            }

            "POCO-${hash.take(20).uppercase()}"
        } else {
            // Dự phòng nếu Android ID không đọc được.
            "POCO-${UUID.randomUUID().toString().replace("-", "").take(20).uppercase()}"
        }

        // Lưu ID vào bộ nhớ ứng dụng.
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .commit()

        return userId
    }
}
