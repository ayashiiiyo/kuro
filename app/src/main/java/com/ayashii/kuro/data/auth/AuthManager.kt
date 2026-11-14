package com.ayashii.kuro.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class UserAccount(
    val name: String,
    val email: String,
    val channelHandle: String,
    val avatarUrl: String,
    val cookie: String,
    val authUser: String = "0",
    val pageId: String = ""
)

class AuthManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val httpClient = com.ayashii.kuro.data.remote.HttpClientProvider.client
    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow<UserAccount?>(loadStoredAccount())
    val authState: StateFlow<UserAccount?> = _authState.asStateFlow()

    private fun loadStoredAccount(): UserAccount? {
        val rawCookie = prefs.getString(KEY_COOKIE, null) ?: return null
        val cookie = sanitizeCookieHeader(rawCookie)
        if (cookie.isBlank()) return null
        var name = prefs.getString(KEY_NAME, "YouTube User") ?: "YouTube User"
        var email = prefs.getString(KEY_EMAIL, "") ?: ""
        var handle = prefs.getString(KEY_HANDLE, "") ?: ""
        val rawAvatar = prefs.getString(KEY_AVATAR, "") ?: ""
        val avatar = normalizeAvatarUrl(rawAvatar)
        val authUser = prefs.getString(KEY_AUTH_USER, "0")?.takeIf { it.isNotBlank() } ?: "0"
        val pageId = prefs.getString(KEY_PAGE_ID, "") ?: ""

        if (name.startsWith("{") || name.contains("\"runs\"")) {
            name = parseRawJsonRuns(name).ifBlank { "YouTube User" }
        }
        if (email.startsWith("{") || email.contains("\"runs\"")) {
            email = parseRawJsonRuns(email)
        }
        if (handle.startsWith("{") || handle.contains("\"runs\"")) {
            handle = parseRawJsonRuns(handle)
        }

        return UserAccount(name, email, handle, avatar, cookie, authUser, pageId)
    }

    private fun sanitizeCookieHeader(raw: String): String {
        return raw.replace("\r", "").replace("\n", "").trim()
    }

    fun normalizeAvatarUrl(raw: String): String {
        if (raw.isBlank()) return ""
        var u = raw.trim().replace("\\/", "/").replace("\\u0026", "&").replace("\\u003d", "=")
        if (u.startsWith("//")) {
            u = "https:$u"
        }
        if (u.contains("googleusercontent.com") || u.contains("ggpht.com")) {
            u = u.replace(Regex("=s\\d+(-c)?[^&?\"']*"), "=s240-c")
            if (!u.contains("=s") && !u.contains("=w")) {
                u = "$u=s240-c"
            }
        }
        return u
    }

    private fun parseRawJsonRuns(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            extractTextFromAny(json)
        } catch (_: Exception) {
            jsonStr
        }
    }

    private fun extractTextFromAny(obj: Any?): String {
        if (obj == null) return ""
        return when (obj) {
            is String -> obj.trim()
            is JSONObject -> {
                val runs = obj.optJSONArray("runs")
                if (runs != null && runs.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until runs.length()) {
                        sb.append(runs.optJSONObject(i)?.optString("text", "").orEmpty())
                    }
                    val joined = sb.toString().trim()
                    if (joined.isNotBlank()) return joined
                }
                obj.optString("simpleText", "").trim()
            }
            else -> ""
        }
    }

    private fun extractBestThumbnailUrl(photoObj: JSONObject?): String {
        if (photoObj == null) return ""
        val directThumbs = photoObj.optJSONArray("thumbnails")
            ?: photoObj.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: photoObj.optJSONObject("avatar")?.optJSONArray("thumbnails")
        if (directThumbs != null && directThumbs.length() > 0) {
            val raw = directThumbs.optJSONObject(directThumbs.length() - 1)?.optString("url", "").orEmpty()
            return normalizeAvatarUrl(raw)
        }
        return ""
    }

    fun isLoggedIn(): Boolean = _authState.value != null

    fun getCookie(): String? = _authState.value?.cookie

    fun getAuthUser(): String = _authState.value?.authUser?.ifBlank { "0" } ?: "0"

    fun getPageId(): String = _authState.value?.pageId.orEmpty()

    fun parseCookies(cookieStr: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        cookieStr.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { part ->
                val idx = part.indexOf("=")
                val key = part.substring(0, idx).trim()
                val value = part.substring(idx + 1).trim()
                if (key.isNotEmpty() && (value.isNotEmpty() || !result.containsKey(key))) {
                    result[key] = value
                }
            }
        return result
    }

    fun getSapisidHash(origin: String = "https://music.youtube.com", cookieOverride: String? = null): String? {
        val cookie = cookieOverride ?: getCookie() ?: return null
        val map = parseCookies(cookie)
        val sapisid = map["SAPISID"]
            ?: map["__Secure-3PAPISID"]
            ?: map["__Secure-1PAPISID"]
            ?: return null
        if (sapisid.isBlank()) return null
        val time = System.currentTimeMillis() / 1000
        val sha1 = sha1Hex("$time $sapisid $origin")
        return "SAPISIDHASH ${time}_$sha1"
    }

    fun applyAuthToRequest(
        reqBuilder: Request.Builder,
        payload: JSONObject? = null,
        origin: String = "https://music.youtube.com"
    ) {
        val account = _authState.value ?: return
        val cookie = sanitizeCookieHeader(account.cookie)
        if (cookie.isBlank()) return
        reqBuilder.header("Cookie", cookie)
        val authUser = account.authUser.ifBlank { "0" }
        reqBuilder.header("X-Goog-AuthUser", authUser)
        if (account.pageId.isNotBlank()) {
            reqBuilder.header("X-Goog-PageId", account.pageId)
            payload?.optJSONObject("context")?.let { ctx ->
                val userObj = ctx.optJSONObject("user") ?: JSONObject().also { ctx.put("user", it) }
                userObj.put("onBehalfOfUser", account.pageId)
            }
        }
        getSapisidHash(origin, cookie)?.let { hash ->
            reqBuilder.header("Authorization", hash)
        }
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun handleLoginSuccess(
        cookie: String,
        initialName: String = "",
        initialAvatar: String = "",
        initialEmail: String = "",
        authUser: String = "0",
        pageId: String = ""
    ) {
        val cleanCookie = sanitizeCookieHeader(cookie)
        if (cleanCookie.isBlank()) return
        val cleanAvatar = normalizeAvatarUrl(initialAvatar)
        val validInitialName = initialName.trim().takeIf { it.isNotBlank() && it != "null" } ?: ""
        val savedName = if (validInitialName.isNotBlank()) {
            validInitialName
        } else {
            prefs.getString(KEY_NAME, "YouTube User") ?: "YouTube User"
        }
        val savedAvatar = if (cleanAvatar.isNotBlank()) {
            cleanAvatar
        } else {
            normalizeAvatarUrl(prefs.getString(KEY_AVATAR, "") ?: "")
        }
        val savedEmail = if (initialEmail.isNotBlank()) {
            initialEmail.trim()
        } else {
            prefs.getString(KEY_EMAIL, "") ?: ""
        }
        val resolvedAuthUser = authUser.trim().ifBlank { prefs.getString(KEY_AUTH_USER, "0") ?: "0" }
        val resolvedPageId = pageId.trim().ifBlank { prefs.getString(KEY_PAGE_ID, "") ?: "" }

        val initialAccount = UserAccount(
            name = savedName,
            email = savedEmail,
            channelHandle = prefs.getString(KEY_HANDLE, "") ?: "",
            avatarUrl = savedAvatar,
            cookie = cleanCookie,
            authUser = resolvedAuthUser,
            pageId = resolvedPageId
        )
        prefs.edit()
            .putString(KEY_COOKIE, cleanCookie)
            .putString(KEY_AUTH_USER, resolvedAuthUser)
            .apply {
                if (validInitialName.isNotBlank()) putString(KEY_NAME, validInitialName)
                if (cleanAvatar.isNotBlank()) putString(KEY_AVATAR, cleanAvatar)
                if (savedEmail.isNotBlank()) putString(KEY_EMAIL, savedEmail)
                if (resolvedPageId.isNotBlank()) putString(KEY_PAGE_ID, resolvedPageId)
            }
            .apply()

        _authState.value = initialAccount

        authScope.launch {
            fetchAccountInfo(cleanCookie)
        }
    }

    @Synchronized
    fun mergeResponseCookies(setCookieHeaders: List<String>) {
        val current = _authState.value ?: return
        if (setCookieHeaders.isEmpty()) return
        val merged = LinkedHashMap<String, String>(parseCookies(current.cookie))
        var changed = false
        for (rawHeader in setCookieHeaders) {
            val pair = rawHeader.substringBefore(";").trim()
            val eqIdx = pair.indexOf("=")
            if (eqIdx <= 0) continue
            val key = pair.substring(0, eqIdx).trim()
            val value = pair.substring(eqIdx + 1).trim()
            if (key.isEmpty()) continue
            if (value.isEmpty() || value.equals("deleted", ignoreCase = true)) {
                if (merged.remove(key) != null) changed = true
            } else if (merged[key] != value) {
                merged[key] = value
                changed = true
            }
        }
        if (changed) {
            val updatedCookie = merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
            prefs.edit()
                .putString(KEY_COOKIE, updatedCookie)
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()
            _authState.value = current.copy(cookie = updatedCookie)
        }
    }

    fun refreshAccountInfoIfNeeded(force: Boolean = false) {
        val current = _authState.value ?: return
        if (current.cookie.isBlank()) return
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        val isStale = (System.currentTimeMillis() - lastCheck) >= 12L * 60L * 60L * 1000L
        val needsRefresh = force ||
            isStale ||
            current.avatarUrl.isBlank() ||
            current.name.isBlank() ||
            current.name == "YouTube User"
        if (needsRefresh) {
            authScope.launch {
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                fetchAccountInfo(current.cookie)
            }
        }
    }

    fun updateProfile(
        name: String,
        email: String,
        avatarUrl: String,
        channelHandle: String = "",
        authUser: String = "",
        pageId: String = ""
    ) {
        val current = _authState.value ?: return
        val cleanName = name.trim().takeIf { it.isNotBlank() && it != "null" }
        val newName = if (!cleanName.isNullOrBlank()) cleanName else current.name
        val newEmail = if (email.isNotBlank() && email != "null") email.trim() else current.email
        val normAvatar = normalizeAvatarUrl(avatarUrl)
        val newAvatar = if (normAvatar.isNotBlank()) normAvatar else current.avatarUrl
        val newHandle = if (channelHandle.isNotBlank() && channelHandle != "null") channelHandle.trim() else current.channelHandle
        val newAuthUser = if (authUser.isNotBlank()) authUser.trim() else current.authUser
        val newPageId = if (pageId.isNotBlank()) pageId.trim() else current.pageId

        prefs.edit()
            .putString(KEY_NAME, newName)
            .putString(KEY_EMAIL, newEmail)
            .putString(KEY_AVATAR, newAvatar)
            .putString(KEY_HANDLE, newHandle)
            .putString(KEY_AUTH_USER, newAuthUser)
            .putString(KEY_PAGE_ID, newPageId)
            .apply()

        _authState.value = current.copy(
            name = newName,
            email = newEmail,
            channelHandle = newHandle,
            avatarUrl = newAvatar,
            authUser = newAuthUser,
            pageId = newPageId
        )
        Log.d("KuroAuth", "Profile updated: $newName ($newAvatar)")
    }

    suspend fun fetchAccountInfo(cookie: String) {
        if (tryFetchAccountMenu(cookie, isMusic = true)) return
        if (tryFetchAccountMenu(cookie, isMusic = false)) return
        if (tryFetchAccountsList(cookie)) return
        tryScrapeWebProfile(cookie)
    }

    private fun extractAccountDetailsFromJson(root: JSONObject): Boolean {
        var foundName = ""
        var foundEmail = ""
        var foundHandle = ""
        var foundAvatar = ""
        var foundPageId = ""

        fun inspectRenderer(obj: JSONObject, isHeader: Boolean) {
            val nameCandidate = extractTextFromAny(obj.opt("accountName"))
                .ifBlank { extractTextFromAny(obj.opt("name")) }
            val emailCandidate = extractTextFromAny(obj.opt("email"))
                .ifBlank {
                    val byline = extractTextFromAny(obj.opt("accountByline"))
                    if (byline.contains("@")) byline else ""
                }
            val handleCandidate = extractTextFromAny(obj.opt("channelHandle"))
            val photoCandidate = extractBestThumbnailUrl(obj.optJSONObject("accountPhoto"))
                .ifBlank { extractBestThumbnailUrl(obj.optJSONObject("avatar")) }

            if (nameCandidate.isNotBlank() && (foundName.isBlank() || isHeader)) {
                foundName = nameCandidate
            }
            if (emailCandidate.isNotBlank() && (foundEmail.isBlank() || isHeader)) {
                foundEmail = emailCandidate
            }
            if (handleCandidate.isNotBlank() && (foundHandle.isBlank() || isHeader)) {
                foundHandle = handleCandidate
            }
            if (photoCandidate.isNotBlank() && (foundAvatar.isBlank() || isHeader)) {
                foundAvatar = photoCandidate
            }

            val tokens = obj.optJSONObject("serviceEndpoint")
                ?.optJSONObject("selectActiveIdentityEndpoint")
                ?.optJSONArray("supportedTokens")
            if (tokens != null) {
                for (i in 0 until tokens.length()) {
                    val tok = tokens.optJSONObject(i) ?: continue
                    val pid = tok.optJSONObject("pageIdToken")?.optString("pageId", "").orEmpty()
                    if (pid.isNotBlank() && foundPageId.isBlank()) {
                        foundPageId = pid
                    }
                }
            }
        }

        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    node.optJSONObject("activeAccountHeaderRenderer")?.let {
                        inspectRenderer(it, isHeader = true)
                    }
                    node.optJSONObject("googleAccountHeaderRenderer")?.let {
                        inspectRenderer(it, isHeader = true)
                    }
                    node.optJSONObject("accountItem")?.let { item ->
                        val isSelected = item.optBoolean("isSelected", false)
                        if (isSelected || (foundName.isBlank() && foundAvatar.isBlank())) {
                            inspectRenderer(item, isHeader = isSelected)
                        }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        walk(node.opt(k))
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        walk(node.opt(i))
                    }
                }
            }
        }

        walk(root)

        if (foundName.isNotBlank() || foundAvatar.isNotBlank()) {
            updateProfile(
                name = foundName,
                email = foundEmail,
                avatarUrl = foundAvatar,
                channelHandle = foundHandle,
                pageId = foundPageId
            )
            return true
        }
        return false
    }

    private fun tryFetchAccountMenu(cookie: String, isMusic: Boolean): Boolean {
        try {
            val origin = if (isMusic) "https://music.youtube.com" else "https://www.youtube.com"
            val clientName = if (isMusic) "WEB_REMIX" else "WEB"
            val clientNum = if (isMusic) "67" else "1"
            val clientVer = if (isMusic) "1.20241028.01.00" else "2.20241028.01.00"
            val sapisidHash = getSapisidHash(origin, cookie)
            val authUser = getAuthUser()
            val pageId = getPageId()

            val jsonBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", clientName)
                        put("clientVersion", clientVer)
                        put("hl", "en")
                        put("gl", "US")
                    })
                    if (pageId.isNotBlank()) {
                        put("user", JSONObject().apply {
                            put("onBehalfOfUser", pageId)
                        })
                    }
                })
            }

            val reqBuilder = Request.Builder()
                .url("$origin/youtubei/v1/account/account_menu")
                .header("Content-Type", "application/json")
                .header("Cookie", sanitizeCookieHeader(cookie))
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-Goog-AuthUser", authUser)
                .header("X-YouTube-Client-Name", clientNum)
                .header("X-YouTube-Client-Version", clientVer)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

            if (pageId.isNotBlank()) {
                reqBuilder.header("X-Goog-PageId", pageId)
            }
            if (sapisidHash != null) {
                reqBuilder.header("Authorization", sapisidHash)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val respString = resp.body?.string().orEmpty()
                if (resp.isSuccessful && respString.isNotBlank()) {
                    val json = JSONObject(respString)
                    if (extractAccountDetailsFromJson(json)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("KuroAuth", "Account menu fetch error: ${e.message}")
        }
        return false
    }

    private fun tryFetchAccountsList(cookie: String): Boolean {
        try {
            val origin = "https://www.youtube.com"
            val sapisidHash = getSapisidHash(origin, cookie)
            val authUser = getAuthUser()
            val jsonBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20241028.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }
            val reqBuilder = Request.Builder()
                .url("$origin/youtubei/v1/account/accounts_list")
                .header("Content-Type", "application/json")
                .header("Cookie", sanitizeCookieHeader(cookie))
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-Goog-AuthUser", authUser)
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", "2.20241028.01.00")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

            if (sapisidHash != null) {
                reqBuilder.header("Authorization", sapisidHash)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val respString = resp.body?.string().orEmpty()
                if (resp.isSuccessful && respString.isNotBlank()) {
                    val json = JSONObject(respString)
                    if (extractAccountDetailsFromJson(json)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("KuroAuth", "Accounts list fetch error: ${e.message}")
        }
        return false
    }

    private fun tryScrapeWebProfile(cookie: String): Boolean {
        val urls = listOf("https://music.youtube.com/", "https://www.youtube.com/")
        val userRegex = Regex("\"(?:USER_NAME|ACCOUNT_NAME)\"\\s*:\\s*\"([^\"]+)\"")
        val delegatedRegex = Regex("\"DELEGATED_SESSION_ID\"\\s*:\\s*\"([^\"]+)\"")
        val sessionIdxRegex = Regex("\"SESSION_INDEX\"\\s*:\\s*\"?(\\d+)\"?")
        val avatarUrlRegex = Regex("https?:\\\\?/\\\\?/(?:yt3\\.ggpht\\.com|lh3\\.googleusercontent\\.com)[^\"'\\s\\\\]+")
        val simpleNameRegex = Regex("\"accountName\"\\s*:\\s*\\{\\s*\"simpleText\"\\s*:\\s*\"([^\"]+)\"")

        for (url in urls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Cookie", sanitizeCookieHeader(cookie))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful || html.isBlank()) return@use

                    var scrapedName = userRegex.find(html)?.groupValues?.getOrNull(1).orEmpty()
                    val delegatedSessionId = delegatedRegex.find(html)?.groupValues?.getOrNull(1).orEmpty()
                    val sessionIndex = sessionIdxRegex.find(html)?.groupValues?.getOrNull(1).orEmpty()
                    val avatarMatch = avatarUrlRegex.find(html)?.value.orEmpty()

                    if (scrapedName.isBlank()) {
                        scrapedName = simpleNameRegex.find(html)?.groupValues?.getOrNull(1).orEmpty()
                    }
                    val normAvatar = normalizeAvatarUrl(avatarMatch)
                    if (scrapedName.isNotBlank() || normAvatar.isNotBlank() || delegatedSessionId.isNotBlank()) {
                        updateProfile(
                            name = scrapedName,
                            email = "",
                            avatarUrl = normAvatar,
                            authUser = sessionIndex,
                            pageId = delegatedSessionId
                        )
                        if (scrapedName.isNotBlank() && normAvatar.isNotBlank()) {
                            return true
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return false
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
        private const val KEY_AUTH_USER = "yt_auth_user"
        private const val KEY_PAGE_ID = "yt_page_id"
        private const val KEY_LAST_CHECK = "yt_last_check"

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
