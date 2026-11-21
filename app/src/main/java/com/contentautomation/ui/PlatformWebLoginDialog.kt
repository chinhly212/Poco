package com.contentautomation.ui

import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun PlatformWebLoginDialog(
    platform: String,
    onDismiss: () -> Unit,
    onSaveSession: (String) -> Unit
) {
    val context = LocalContext.current
    val normalized = platform.uppercase()
    val loginUrl = if (normalized == "KUAISHOU") {
        "https://www.kuaishou.com/account/login/"
    } else {
        "https://www.douyin.com/"
    }
    val cookieHosts = if (normalized == "KUAISHOU") {
        listOf("https://www.kuaishou.com", "https://passport.kuaishou.com")
    } else {
        listOf("https://www.douyin.com", "https://passport.douyin.com")
    }
    val webView = remember(platform) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl(loginUrl)
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.stopLoading() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đăng nhập $platform") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Đăng nhập trên trang chính thức. MuChi chỉ lưu cookie phiên trên điện thoại và không đọc mật khẩu.")
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxWidth().height(520.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                CookieManager.getInstance().flush()
                val cookieParts = cookieHosts.flatMap { host ->
                    CookieManager.getInstance().getCookie(host)
                        ?.split(";")
                        ?.map { it.trim() }
                        .orEmpty()
                }.filter { it.contains("=") }
                val authCookieNames = if (normalized == "KUAISHOU") {
                    setOf("kuaishou.server.web_st", "kuaishou.server.web_ph", "userId")
                } else {
                    setOf("sessionid", "sessionid_ss", "sid_tt")
                }
                val hasAuthCookie = cookieParts.any {
                    it.substringBefore("=").trim() in authCookieNames
                }
                val cookies = if (hasAuthCookie) cookieParts
                    .distinctBy { it.substringBefore("=") }
                    .joinToString("; ") else ""
                onSaveSession(cookies)
            }) {
                Text("Đã đăng nhập – lưu phiên")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}
