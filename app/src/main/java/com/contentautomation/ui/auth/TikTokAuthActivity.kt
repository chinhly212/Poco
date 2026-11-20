package com.contentautomation.ui.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.contentautomation.ContentAutomationApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom

class TikTokAuthActivity : ComponentActivity() {

    private val callbackUri = "https://localhost/tiktok-callback"
    private var expectedState: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = applicationContext as ContentAutomationApp
        val clientKey = runBlocking {
            app.prefs.tiktokClientKey.first()
        }

        if (clientKey.isBlank()) {
            finishWithError("Thiếu TikTok Client Key")
            return
        }

        val state = ByteArray(32).also {
            SecureRandom().nextBytes(it)
        }.joinToString("") { "%02x".format(it) }

        expectedState = state

        val authUrl = buildString {
            append("https://www.tiktok.com/v2/auth/authorize/?")
            append("client_key=")
            append(URLEncoder.encode(clientKey, "UTF-8"))
            append("&scope=")
            append(
                URLEncoder.encode(
                    "user.info.basic,video.upload,video.publish",
                    "UTF-8"
                )
            )
            append("&response_type=code")
            append("&redirect_uri=")
            append(URLEncoder.encode(callbackUri, "UTF-8"))
            append("&state=")
            append(URLEncoder.encode(state, "UTF-8"))
        }

        setContent {
            Scaffold { padding ->
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    factory = { ctx ->
                        @SuppressLint("SetJavaScriptEnabled")
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    url: String?
                                ): Boolean {
                                    if (url.isNullOrBlank()) {
                                        return false
                                    }

                                    val uri = Uri.parse(url)

                                    if (
                                        uri.scheme == "https" &&
                                        uri.host == "localhost" &&
                                        uri.path == "/tiktok-callback"
                                    ) {
                                        handleCallback(uri)
                                        return true
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

    private fun handleCallback(uri: Uri) {
        val returnedState = uri.getQueryParameter("state")

        if (
            expectedState.isNullOrBlank() ||
            returnedState.isNullOrBlank() ||
            returnedState != expectedState
        ) {
            finishWithError("OAuth state không hợp lệ")
            return
        }

        expectedState = null

        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        if (code.isNullOrBlank()) {
            finishWithError(
                error ?: "TikTok không trả về authorization code"
            )
            return
        }

        val app = applicationContext as ContentAutomationApp

        Thread {
            try {
                val clientKey = runBlocking {
                    app.prefs.tiktokClientKey.first()
                }

                val clientSecret = runBlocking {
                    app.prefs.tiktokClientSecret.first()
                }

                if (clientKey.isBlank() || clientSecret.isBlank()) {
                    throw IllegalStateException(
                        "Thiếu TikTok Client Key hoặc Client Secret"
                    )
                }

                val body = FormBody.Builder()
                    .add("client_key", clientKey)
                    .add("client_secret", clientSecret)
                    .add("code", code)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", callbackUri)
                    .build()

                val request = Request.Builder()
                    .url("https://open.tiktokapis.com/v2/oauth/token/")
                    .post(body)
                    .build()

                OkHttpClient().newCall(request).execute().use { response ->
                    val responseText =
                        response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        throw RuntimeException(
                            "TikTok OAuth HTTP ${response.code}: $responseText"
                        )
                    }

                    val json = JSONObject(responseText)

                    val errorCode = json.optString("error")

                    if (
                        errorCode.isNotBlank() &&
                        errorCode != "ok"
                    ) {
                        throw RuntimeException(
                            json.optString(
                                "error_description",
                                errorCode
                            )
                        )
                    }

                    val accessToken =
                        json.optString("access_token")

                    val refreshToken =
                        json.optString("refresh_token")

                    if (
                        accessToken.isBlank() ||
                        refreshToken.isBlank()
                    ) {
                        throw RuntimeException(
                            "TikTok không trả về token hợp lệ"
                        )
                    }

                    val expiresIn =
                        json.optLong("expires_in", 0L)

                    if (expiresIn <= 0L) {
                        throw RuntimeException(
                            "Thời hạn access token không hợp lệ"
                        )
                    }

                    app.prefs.setAccessToken(accessToken)
                    app.prefs.setRefreshToken(refreshToken)

                    app.prefs.setTokenExpiresAt(
                        System.currentTimeMillis() +
                            expiresIn * 1000L
                    )

                    val openId = json.optString("open_id")

                    if (openId.isNotBlank()) {
                        app.prefs.setOpenId(openId)
                    }
                }

                runOnUiThread {
                    finish()
                }

            } catch (e: Exception) {
                finishWithError(
                    e.message ?: "Không thể xác thực TikTok"
                )
            }
        }.start()
    }

    private fun finishWithError(message: String) {
        runOnUiThread {
            android.widget.Toast.makeText(
                this,
                "TikTok auth lỗi: $message",
                android.widget.Toast.LENGTH_LONG
            ).show()

            finish()
        }
    }
}
