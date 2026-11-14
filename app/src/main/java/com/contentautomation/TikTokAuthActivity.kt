package com.contentautomation.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.contentautomation.ContentAutomationApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.URLEncoder

/**
 * TikTok yêu cầu HTTPS redirect URI. Trên Android, ta dùng WebView để bắt
 * redirect_uri = https://localhost/tiktok-callback (TikTok cho phép localhost
 * khi app chưa verify domain) rồi trích code từ query param.
 *
 * Nếu bạn có server riêng: đổi redirect_uri sang server đó và bỏ WebView,
 * chỉ cần mở Custom Tab và xử lý deep link callback.
 */
class TikTokAuthActivity : ComponentActivity() {

    private val callbackUri = "https://localhost/tiktok-callback"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext as ContentAutomationApp
        val clientKey = runBlocking { app.prefs.tiktokClientKey.first() }
        if (clientKey.isBlank()) {
            finish(); return
        }
        val state = System.currentTimeMillis().toString()
        val authUrl = buildString {
            append("https://www.tiktok.com/v2/auth/authorize/?")
            append("client_key=").append(URLEncoder.encode(clientKey, "UTF-8"))
            append("&scope=").append(URLEncoder.encode("user.info.basic,video.upload,video.publish", "UTF-8"))
            append("&response_type=code")
            append("&redirect_uri=").append(URLEncoder.encode(callbackUri, "UTF-8"))
            append("&state=").append(state)
        }

        setContent {
            Scaffold { padding ->
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    factory = { ctx ->
                        @SuppressLint("SetJavaScriptEnabled")
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?, url: String?
                                ): Boolean {
                                    if (url?.startsWith(callbackUri) == true) {
                                        handleCallback(url); return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(authUrl)
                        }
                    }
                )
            }
        }
    }

    private fun handleCallback(url: String) {
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val err = uri.getQueryParameter("error")
        if (code.isNullOrBlank()) {
            finishWithError(err ?: "no_code"); return
        }
        val app = applicationContext as ContentAutomationApp
        Thread {
            try {
                val clientKey = runBlocking { app.prefs.tiktokClientKey.first() }
                val clientSecret = runBlocking { app.prefs.tiktokClientSecret.first() }
                val client = okhttp3.OkHttpClient()
                val body = okhttp3.FormBody.Builder()
                    .add("client_key", clientKey)
                    .add("client_secret", clientSecret)
                    .add("code", code)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", callbackUri).build()
                val req = okhttp3.Request.Builder()
                    .url("https://open.tiktokapis.com/v2/oauth/token/")
                    .post(body).build()
                client.newCall(req).execute().use { resp ->
                    val json = JSONObject(resp.body!!.string())
                    if (json.has("error") && json.getString("error").isNotBlank() &&
                        json.getString("error") != "ok") {
                        finishWithError(json.toString()); return@use
                    }
                    app.prefs.setAccessToken(json.getString("access_token"))
                    app.prefs.setRefreshToken(json.optString("refresh_token"))
                    val expiresIn = json.optLong("expires_in", 86400)
                    app.prefs.setTokenExpiresAt(System.currentTimeMillis() + expiresIn * 1000)
                    app.prefs.setOpenId(json.optString("open_id", "default"))
                    runOnUiThread { finish() }
                }
            } catch (e: Exception) {
                finishWithError(e.message ?: "unknown")
            }
        }.start()
    }

    private fun finishWithError(msg: String) {
        runOnUiThread {
            android.widget.Toast.makeText(this, "TikTok auth lỗi: $msg", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
