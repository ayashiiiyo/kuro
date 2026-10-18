package com.ayashii.kuro.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UserAccount(
    val name: String,
    val email: String,
    val channelHandle: String,
    val avatarUrl: String,
    val cookie: String
)

class AuthManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _authState = MutableStateFlow<UserAccount?>(loadStoredAccount())
    val authState: StateFlow<UserAccount?> = _authState.asStateFlow()

    private fun loadStoredAccount(): UserAccount? {
        val cookie = prefs.getString(KEY_COOKIE, null) ?: return null
        val name = prefs.getString(KEY_NAME, "YouTube User") ?: "YouTube User"
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val handle = prefs.getString(KEY_HANDLE, "") ?: ""
        val avatar = prefs.getString(KEY_AVATAR, "") ?: ""
        return UserAccount(name, email, handle, avatar, cookie)
    }

    fun isLoggedIn(): Boolean = _authState.value != null

    fun getCookie(): String? = _authState.value?.cookie

    fun parseCookies(cookieStr: String): Map<String, String> {
        return cookieStr.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate {
                val idx = it.indexOf("=")
                it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            }
    }

    fun getSapisidHash(origin: String = "https://music.youtube.com"): String? {
        val cookie = getCookie() ?: return null
        val map = parseCookies(cookie)
        val sapisid = map["SAPISID"] ?: map["__Secure-3PAPISID"] ?: map["__Secure-1PAPISID"] ?: return null
        val time = System.currentTimeMillis() / 1000
        val sha1 = sha1Hex("$time $sapisid $origin")
        return "SAPISIDHASH ${time}_$sha1"
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun handleLoginSuccess(cookie: String) {
        val initialAccount = UserAccount(
            name = prefs.getString(KEY_NAME, "YouTube User") ?: "YouTube User",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            channelHandle = prefs.getString(KEY_HANDLE, "") ?: "",
            avatarUrl = prefs.getString(KEY_AVATAR, "") ?: "",
            cookie = cookie
        )
        prefs.edit().putString(KEY_COOKIE, cookie).apply()
        _authState.value = initialAccount

        // Fetch detailed profile from InnerTube in background
        CoroutineScope(Dispatchers.IO).launch {
            fetchAccountInfo(cookie)
        }
    }

    suspend fun fetchAccountInfo(cookie: String) {
        try {
            val sapisidHash = getSapisidHash("https://music.youtube.com")
            val jsonBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            val reqBuilder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/account/account_menu")
                .header("Content-Type", "application/json")
                .header("Cookie", cookie)
                .header("Origin", "https://music.youtube.com")
                .header("X-Origin", "https://music.youtube.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

            if (sapisidHash != null) {
                reqBuilder.header("Authorization", sapisidHash)
            }

            val resp = httpClient.newCall(reqBuilder.build()).execute()
            val respString = resp.body?.string().orEmpty()
            if (resp.isSuccessful && respString.isNotBlank()) {
                val json = JSONObject(respString)
                val actions = json.optJSONArray("actions")
                val openPopup = actions?.optJSONObject(0)?.optJSONObject("openPopupAction")
                val multiPage = openPopup?.optJSONObject("popup")?.optJSONObject("multiPageMenuRenderer")
                val activeAccount = multiPage?.optJSONObject("header")?.optJSONObject("activeAccountHeaderRenderer")

                if (activeAccount != null) {
                    val name = activeAccount.optJSONObject("accountName")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "YouTube User"
                    val email = activeAccount.optJSONObject("email")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                    val handle = activeAccount.optJSONObject("channelHandle")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""

                    var avatarUrl = ""
                    val accountPhoto = activeAccount.optJSONObject("accountPhoto")
                    val thumbs = accountPhoto?.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        avatarUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
                    }

                    prefs.edit()
                        .putString(KEY_NAME, name)
                        .putString(KEY_EMAIL, email)
                        .putString(KEY_HANDLE, handle)
                        .putString(KEY_AVATAR, avatarUrl)
                        .apply()

                    _authState.value = UserAccount(name, email, handle, avatarUrl, cookie)
                    Log.d("KuroAuth", "Account updated: $name ($email)")
                }
            }
        } catch (e: Exception) {
            Log.e("KuroAuth", "Failed to fetch account info: ${e.message}", e)
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _authState.value = null
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        } catch (e: Exception) {
            Log.e("KuroAuth", "Failed to clear WebView cookies: ${e.message}")
        }
    }

    companion object {
        private const val PREF_NAME = "kuro_auth_prefs"
        private const val KEY_COOKIE = "yt_cookie"
        private const val KEY_NAME = "yt_name"
        private const val KEY_EMAIL = "yt_email"
        private const val KEY_HANDLE = "yt_handle"
        private const val KEY_AVATAR = "yt_avatar"

        @Volatile
        private var instance: AuthManager? = null

        fun init(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): AuthManager {
            return instance ?: throw IllegalStateException("AuthManager must be initialized in Application.onCreate")
        }
    }
}
