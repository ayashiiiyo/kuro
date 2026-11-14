package com.ayashii.kuro.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayashii.kuro.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ayashii.kuro.data.auth.AuthManager
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography

private fun isTrustedGoogleAuthHost(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = try {
        Uri.parse(url).host?.lowercase() ?: return false
    } catch (_: Exception) {
        return false
    }
    return host == "google.com" ||
        host.endsWith(".google.com") ||
        host == "youtube.com" ||
        host.endsWith(".youtube.com") ||
        host.endsWith(".gstatic.com") ||
        host.endsWith(".googleapis.com") ||
        host.endsWith(".googleusercontent.com")
}

private fun isInitialSignInEntryUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return true
    val lower = url.lowercase()
    return lower.contains("accounts.google.com/servicelogin") ||
        lower.contains("accounts.google.com/v3/signin/identifier") ||
        lower.contains("accounts.google.com/signin/v2/identifier")
}

private fun mergeCookieStrings(vararg cookieParts: String?): String {
    val map = LinkedHashMap<String, String>()
    for (part in cookieParts) {
        if (part.isNullOrBlank()) continue
        part.split(";").forEach { raw ->
            val trimmed = raw.trim()
            val idx = trimmed.indexOf("=")
            if (idx > 0) {
                val k = trimmed.substring(0, idx).trim()
                val v = trimmed.substring(idx + 1).trim()
                if (k.isNotEmpty() && v.isNotEmpty()) {
                    map[k] = v
                }
            }
        }
    }
    return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginWebViewDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canNavigateBack by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var hasCompletedLogin by remember { mutableStateOf(false) }
    val colors = KuroAppTheme.colors

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.removeJavascriptInterface("KuroBridge")
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
        BackHandler {
            val view = webViewInstance
            val currentUrl = view?.url
            if (view != null && canNavigateBack && view.canGoBack() && !isInitialSignInEntryUrl(currentUrl)) {
                view.goBack()
                canNavigateBack = view.canGoBack() && !isInitialSignInEntryUrl(view.url)
            } else {
                onDismiss()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(colors.card)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.login_youtube_title),
                        style = Typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.login_youtube_sub),
                        style = Typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
                IconButton(onClick = { webViewInstance?.reload() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = colors.textSecondary
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { pageProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = colors.textPrimary,
                    trackColor = colors.border
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

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
                            cookieManager.removeAllCookies(null)
                            cookieManager.flush()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
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

                            fun extractAndFinalizeLogin(view: WebView?, currentUrl: String) {
                                if (hasCompletedLogin) return
                                cookieManager.flush()
                                val cookies = mergeCookieStrings(
                                    cookieManager.getCookie("https://music.youtube.com"),
                                    cookieManager.getCookie("https://www.youtube.com"),
                                    cookieManager.getCookie("https://m.youtube.com"),
                                    cookieManager.getCookie(currentUrl)
                                )
                                val hasAuth = cookies.contains("SAPISID=") ||
                                    cookies.contains("__Secure-3PAPISID=") ||
                                    cookies.contains("__Secure-1PAPISID=")

                                if (!hasAuth || !currentUrl.contains("music.youtube.com")) {
                                    return
                                }

                                view?.evaluateJavascript(
                                    """
                                    (() => {
                                        try {
                                            let avatar = '';
                                            let name = '';
                                            let email = '';
                                            let authUser = '0';
                                            let pageId = '';
                                            const imgs = document.querySelectorAll("#avatar-btn img, ytmusic-settings-button img, img.yt-spec-avatar-shape__image, img.yt-img-shadow, img#img, img[src*='googleusercontent.com'], img[src*='ggpht.com']");
                                            for (let i = 0; i < imgs.length; i++) {
                                                const src = imgs[i].src || imgs[i].getAttribute('src') || '';
                                                if (src && (src.includes('googleusercontent.com') || src.includes('ggpht.com'))) {
                                                    avatar = src;
                                                    break;
                                                }
                                            }
                                            if (window.ytcfg && typeof window.ytcfg.get === 'function') {
                                                name = window.ytcfg.get('USER_NAME') || window.ytcfg.get('ACCOUNT_NAME') || '';
                                                const idx = window.ytcfg.get('SESSION_INDEX');
                                                if (idx !== undefined && idx !== null && String(idx).length > 0) {
                                                    authUser = String(idx);
                                                }
                                                pageId = window.ytcfg.get('DELEGATED_SESSION_ID') || '';
                                            }
                                            if (!name) {
                                                const nameEl = document.querySelector("div[aria-label*='Google Account:'], button[aria-label*='Google Account:'], [aria-label*='Akun Google:']");
                                                if (nameEl) {
                                                    const rawLabel = (nameEl.getAttribute('aria-label') || '')
                                                        .replace('Google Account:', '')
                                                        .replace('Akun Google:', '')
                                                        .trim();
                                                    const parenIdx = rawLabel.indexOf('(');
                                                    if (parenIdx > 0) {
                                                        name = rawLabel.substring(0, parenIdx).trim();
                                                        const closeIdx = rawLabel.indexOf(')', parenIdx);
                                                        if (closeIdx > parenIdx) {
                                                            email = rawLabel.substring(parenIdx + 1, closeIdx).trim();
                                                        }
                                                    } else {
                                                        name = rawLabel;
                                                    }
                                                }
                                            }
                                            if (window.KuroBridge) {
                                                window.KuroBridge.onUserData(name, email, avatar);
                                            }
                                            return JSON.stringify({
                                                avatar: avatar || '',
                                                name: name || '',
                                                email: email || '',
                                                authUser: authUser || '0',
                                                pageId: pageId || ''
                                            });
                                        } catch(e) {
                                            return '{}';
                                        }
                                    })()
                                    """.trimIndent()
                                ) { evalResult ->
                                    if (hasCompletedLogin) return@evaluateJavascript
                                    hasCompletedLogin = true
                                    var extractedName = ""
                                    var extractedAvatar = ""
                                    var extractedEmail = ""
                                    var extractedAuthUser = "0"
                                    var extractedPageId = ""
                                    try {
                                        if (!evalResult.isNullOrBlank() && evalResult != "null") {
                                            val unescaped = org.json.JSONTokener(evalResult).nextValue().toString()
                                            val json = org.json.JSONObject(unescaped)
                                            extractedName = json.optString("name", "")
                                            extractedAvatar = json.optString("avatar", "")
                                            extractedEmail = json.optString("email", "")
                                            extractedAuthUser = json.optString("authUser", "0")
                                            extractedPageId = json.optString("pageId", "")
                                        }
                                    } catch (_: Exception) {}

                                    AuthManager.getInstance().handleLoginSuccess(
                                        cookie = cookies,
                                        initialName = extractedName,
                                        initialAvatar = extractedAvatar,
                                        initialEmail = extractedEmail,
                                        authUser = extractedAuthUser,
                                        pageId = extractedPageId
                                    )
                                    onLoginSuccess()
                                    onDismiss()
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString()
                                    return !isTrustedGoogleAuthHost(targetUrl)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    canNavigateBack = view?.canGoBack() == true && !isInitialSignInEntryUrl(url)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    canNavigateBack = view?.canGoBack() == true && !isInitialSignInEntryUrl(url)
                                    val currentUrl = url ?: ""
                                    if (!isTrustedGoogleAuthHost(currentUrl)) return
                                    extractAndFinalizeLogin(view, currentUrl)
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    val currentUrl = url ?: ""
                                    canNavigateBack = view?.canGoBack() == true && !isInitialSignInEntryUrl(currentUrl)
                                    if (!isTrustedGoogleAuthHost(currentUrl)) return
                                    if (currentUrl.contains("music.youtube.com")) {
                                        view?.postDelayed({
                                            if (!hasCompletedLogin) {
                                                extractAndFinalizeLogin(view, currentUrl)
                                            }
                                        }, 650L)
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
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}
