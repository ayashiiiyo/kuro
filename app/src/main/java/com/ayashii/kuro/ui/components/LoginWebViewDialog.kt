package com.ayashii.kuro.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ayashii.kuro.data.auth.AuthManager
import com.ayashii.kuro.ui.theme.DarkBorder
import com.ayashii.kuro.ui.theme.DarkCard
import com.ayashii.kuro.ui.theme.MutedText
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.PureWhite
import com.ayashii.kuro.ui.theme.SubtleGray
import com.ayashii.kuro.ui.theme.Typography

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginWebViewDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        BackHandler(enabled = webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(DarkCard)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = PureWhite
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Login to YouTube",
                        style = Typography.titleMedium,
                        color = PureWhite
                    )
                    Text(
                        text = "Sign in via official Google login",
                        style = Typography.bodyMedium,
                        color = MutedText
                    )
                }
                IconButton(onClick = { webViewInstance?.reload() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = SubtleGray
                    )
                }
            }

            // Progress bar
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { pageProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = PureWhite,
                    trackColor = DarkBorder
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // WebView
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                // Clean standard modern Chrome mobile user agent with real device model (prevents 'disallowed_useragent' Google error)
                                val deviceModel = android.os.Build.MODEL.ifBlank { "Android Device" }
                                val osVersion = android.os.Build.VERSION.RELEASE.ifBlank { "14" }
                                userAgentString = "Mozilla/5.0 (Linux; Android $osVersion; $deviceModel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            }

                            addJavascriptInterface(object {
                                @android.webkit.JavascriptInterface
                                fun onUserData(name: String?, email: String?, avatarUrl: String?) {
                                    if (!name.isNullOrBlank() || !avatarUrl.isNullOrBlank()) {
                                        AuthManager.getInstance().updateProfile(name ?: "", email ?: "", avatarUrl ?: "")
                                    }
                                }
                            }, "KuroBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false

                                    val currentUrl = url ?: ""
                                    val cookies = cookieManager.getCookie("https://music.youtube.com")
                                        ?: cookieManager.getCookie("https://www.youtube.com")
                                        ?: cookieManager.getCookie(currentUrl)
                                        ?: ""

                                    val hasAuth = cookies.contains("SAPISID") ||
                                            cookies.contains("LOGIN_INFO") ||
                                            cookies.contains("__Secure-3PAPISID")

                                    // Extract Google avatar and name directly from DOM!
                                    view?.evaluateJavascript(
                                        """
                                        (() => {
                                            try {
                                                let avatar = '';
                                                let name = '';
                                                let email = '';
                                                const img = document.querySelector("#avatar-btn img, ytmusic-settings-button img, img.yt-img-shadow, img#img, img[src*='googleusercontent.com']");
                                                if (img && img.src && img.src.startsWith('http')) {
                                                    avatar = img.src;
                                                }
                                                if (window.ytcfg && typeof window.ytcfg.get === 'function') {
                                                    name = window.ytcfg.get('USER_NAME') || window.ytcfg.get('ACCOUNT_NAME') || '';
                                                }
                                                if (!name) {
                                                    const nameEl = document.querySelector("div[aria-label*='Google Account:']");
                                                    if (nameEl) {
                                                        name = (nameEl.getAttribute('aria-label') || '').replace('Google Account:', '').trim();
                                                    }
                                                }
                                                if (window.KuroBridge) {
                                                    window.KuroBridge.onUserData(name, email, avatar);
                                                }
                                                return JSON.stringify({ avatar: avatar, name: name });
                                            } catch(e) {
                                                return '{}';
                                            }
                                        })()
                                        """.trimIndent()
                                    ) { evalResult ->
                                        var extractedName = ""
                                        var extractedAvatar = ""
                                        try {
                                            if (!evalResult.isNullOrBlank() && evalResult != "null") {
                                                val unescaped = org.json.JSONTokener(evalResult).nextValue().toString()
                                                val json = org.json.JSONObject(unescaped)
                                                extractedName = json.optString("name", "")
                                                extractedAvatar = json.optString("avatar", "")
                                            }
                                        } catch (_: Exception) {}

                                        if (hasAuth && (currentUrl.contains("music.youtube.com") || currentUrl.contains("youtube.com"))) {
                                            AuthManager.getInstance().handleLoginSuccess(cookies, extractedName, extractedAvatar)
                                            onLoginSuccess()
                                            onDismiss()
                                        }
                                    }
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    val currentUrl = url ?: ""
                                    val cookies = cookieManager.getCookie(currentUrl) ?: ""
                                    if ((cookies.contains("SAPISID") || cookies.contains("LOGIN_INFO")) &&
                                        (currentUrl.contains("music.youtube.com") || currentUrl.contains("youtube.com"))) {
                                        AuthManager.getInstance().handleLoginSuccess(cookies)
                                        onLoginSuccess()
                                        onDismiss()
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress / 100f
                                }
                            }

                            webViewInstance = this
                            loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F")
                        }
                    }
                )

                if (isLoading && pageProgress < 0.2f) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                        color = PureWhite
                    )
                }
            }
        }
    }
}
