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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
        listOf("https://www.kuaishou.com", "https://kuaishou.com", "https://passport.kuaishou.com")
    } else {
        listOf(
            "https://www.douyin.com/",
            "https://douyin.com/",
            "https://passport.douyin.com/",
            "https://creator.douyin.com/"
        )
    }
    var sessionMessage by remember(platform) { mutableStateOf<String?>(null) }
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
        onDispose { webView.stopLoading(); webView.destroy() }
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
                sessionMessage?.let {
                    Text(it, color = androidx.compose.ui.graphics.Color(0xFFB45309))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val manager = CookieManager.getInstance()
                manager.flush()
                val cookieMap = linkedMapOf<String, String>()
                cookieHosts.forEach { host ->
                    manager.getCookie(host).orEmpty()
                        .split(";")
                        .map { it.trim() }
                        .filter { it.contains("=") }
                        .forEach { part ->
                            val name = part.substringBefore("=").trim()
                            val value = part.substringAfter("=", "").trim()
                            if (name.isBlank() || value.isBlank()) return@forEach
                            val existing = cookieMap[name]
                            // Prefer canonical UIFID over a temporary or empty duplicate.
                            if (existing == null || name.equals("UIFID", true)) {
                                cookieMap[name] = value
                            }
                        }
                }
                val hasUifid = cookieMap.keys.any {
                    it.equals("UIFID", true) || it.equals("UIFID_TEMP", true)
                }
                if (normalized != "KUAISHOU" && !hasUifid) {
                    sessionMessage = "Chưa lấy được UIFID từ phiên Douyin. Hãy tải lại trang, chờ đăng nhập hoàn tất rồi bấm lưu phiên lại. Không thể quét kênh nếu thiếu cookie này."
                    return@Button
                }
                val cookies = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                if (cookies.isBlank()) {
                    sessionMessage = "Không đọc được cookie. Hãy đăng nhập lại rồi thử lưu phiên."
                    return@Button
                }
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
