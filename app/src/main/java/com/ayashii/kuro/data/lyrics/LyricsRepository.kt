package com.ayashii.kuro.data.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.ayashii.kuro.data.settings.LyricsLanguage
import com.ayashii.kuro.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

class LyricsRepository(private val context: Context) {

    private val httpClient = com.ayashii.kuro.data.remote.HttpClientProvider.client

    private val memCache = LruCache<String, LyricsData>(100)
    private val negativeCache = LruCache<String, Long>(200)
    private val NEGATIVE_CACHE_TTL = 7 * 24 * 60 * 60 * 1000L
    private val diskCacheDir = File(context.cacheDir, "lyrics_cache_v8").apply {
        mkdirs()
        try {
            val oldCache7 = File(context.cacheDir, "lyrics_cache_v7")
            if (oldCache7.exists()) oldCache7.deleteRecursively()
            val oldCache6 = File(context.cacheDir, "lyrics_cache_v6")
            if (oldCache6.exists()) oldCache6.deleteRecursively()
            val oldCache5 = File(context.cacheDir, "lyrics_cache_v5")
            if (oldCache5.exists()) oldCache5.deleteRecursively()
            val oldCache4 = File(context.cacheDir, "lyrics_cache_v4")
            if (oldCache4.exists()) oldCache4.deleteRecursively()
        } catch (_: Exception) {}
    }

    private sealed class LyricsFetchResult {
        data class Success(val data: LyricsData) : LyricsFetchResult()
        object Empty : LyricsFetchResult()
        object Error : LyricsFetchResult()
    }

    private fun resolveTargetLang(): LyricsLanguage {
        return try {
            SettingsManager.getInstance().lyricsLanguage.value
        } catch (_: Throwable) {
            LyricsLanguage.INDONESIAN
        }
    }

    suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        durationSeconds: Long = 0L,
        autoTranslate: Boolean = true
    ): LyricsData? = withContext(Dispatchers.IO) {
        val targetLang = resolveTargetLang()
        val cacheKey = "${trackId}_${targetLang.deeplCode}"

        synchronized(negativeCache) {
            val emptyAt = negativeCache.get(trackId)
            if (emptyAt != null && System.currentTimeMillis() - emptyAt < NEGATIVE_CACHE_TTL) {
                return@withContext null
            }
        }

        synchronized(memCache) {
            memCache.get(cacheKey)?.let { return@withContext it }
            if (!autoTranslate) {
                memCache.get(trackId)?.let { return@withContext it }
            }
        }

        val langDiskFile = File(diskCacheDir, "${trackId}_${targetLang.deeplCode}.json")
        val baseDiskFile = File(diskCacheDir, "$trackId.json")

        if (langDiskFile.exists()) {
            try {
                val cached = parseLyricsJson(langDiskFile.readText())
                if (cached != null) {
                    synchronized(memCache) {
                        memCache.put(cacheKey, cached)
                        memCache.put(trackId, cached)
                    }
                    return@withContext cached
                } else {
                    langDiskFile.delete()
                }
            } catch (e: Exception) {
                Log.d("LyricsRepository", "Disk cache read error: ${e.message}")
            }
        }

        if (baseDiskFile.exists()) {
            try {
                val cached = parseLyricsJson(baseDiskFile.readText())
                if (cached != null) {
                    if (!autoTranslate) {
                        synchronized(memCache) { memCache.put(trackId, cached) }
                        return@withContext cached
                    }
                    val translatedLines = translateLines(cached.lines, targetLang)
                    val localized = cached.copy(lines = translatedLines)
                    synchronized(memCache) {
                        memCache.put(cacheKey, localized)
                        memCache.put(trackId, localized)
                    }
                    try {
                        langDiskFile.writeText(serializeLyricsJson(localized))
                    } catch (_: Exception) {}
                    return@withContext localized
                } else {
                    baseDiskFile.delete()
                }
            } catch (e: Exception) {
                Log.d("LyricsRepository", "Base disk cache read error: ${e.message}")
            }
        }

        val emptyMarker = File(diskCacheDir, "$trackId.empty")
        if (emptyMarker.exists() && System.currentTimeMillis() - emptyMarker.lastModified() < NEGATIVE_CACHE_TTL) {
            synchronized(negativeCache) { negativeCache.put(trackId, emptyMarker.lastModified()) }
            return@withContext null
        }

        val cleanTitle = cleanSearchTerm(title)
        val cleanArtist = cleanSearchTerm(artist)

        val liriqoResult = fetchFromLiriqo(trackId, title, artist, cleanTitle, cleanArtist, durationSeconds)
        var lyrics: LyricsData? = null

        if (liriqoResult is LyricsFetchResult.Success) {
            val sanitizedLines = LrcParser.sanitizeLines(liriqoResult.data.lines)
            if (isValidLyricsLines(sanitizedLines)) {
                lyrics = liriqoResult.data.copy(lines = sanitizedLines)
            }
        }

        if (lyrics != null) {
            try {
                pruneDiskCacheIfNeeded()
                baseDiskFile.writeText(serializeLyricsJson(lyrics))
                if (emptyMarker.exists()) emptyMarker.delete()
            } catch (_: Exception) {}

            if (autoTranslate && lyrics.lines.isNotEmpty()) {
                val translatedLines = translateLines(lyrics.lines, targetLang)
                lyrics = lyrics.copy(lines = translatedLines)
                try {
                    langDiskFile.writeText(serializeLyricsJson(lyrics))
                } catch (_: Exception) {}
            }

            synchronized(memCache) {
                memCache.put(trackId, lyrics)
                if (autoTranslate) {
                    memCache.put(cacheKey, lyrics)
                }
            }
        } else {
            val liriqoVerifiedEmpty = liriqoResult is LyricsFetchResult.Empty || liriqoResult is LyricsFetchResult.Success
            val hadNetworkError = liriqoResult is LyricsFetchResult.Error

            if (!hadNetworkError && liriqoVerifiedEmpty) {
                synchronized(negativeCache) { negativeCache.put(trackId, System.currentTimeMillis()) }
                try {
                    emptyMarker.createNewFile()
                } catch (_: Exception) {}
            }
        }

        lyrics
    }

    private fun pruneDiskCacheIfNeeded() {
        try {
            val files = diskCacheDir.listFiles() ?: return
            if (files.size > 500) {
                files.sortedBy { it.lastModified() }.take(files.size - 400).forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    private fun cleanSearchTerm(term: String): String {
        return term
            .replace(Regex("""\((?:official|audio|video|lyric|lyrics|remastered|remaster|hd|4k|hq|visualizer|mv).*?\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(?:official|audio|video|lyric|lyrics|remastered|remaster|hd|4k|hq|visualizer|mv).*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""feat\..*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""ft\..*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private suspend fun fetchFromLiriqo(
        trackId: String,
        title: String,
        artist: String,
        cleanTitle: String,
        cleanArtist: String,
        durationSeconds: Long
    ): LyricsFetchResult = withContext(Dispatchers.IO) {
        val durationMs = if (durationSeconds > 0) durationSeconds * 1000L else 0L

        if (trackId.isNotBlank() && trackId.length in 10..12) {
            val url = "https://api.liriqo-alfarrizi.my.id/v1/lyrics?v=${URLEncoder.encode(trackId, "UTF-8")}"
            val res = queryLiriqoApi(url, trackId, title, artist, durationMs)
            if (res is LyricsFetchResult.Success) return@withContext res
        }

        val directUrl = buildLiriqoSearchUrl(title, artist, durationSeconds)
        val directRes = queryLiriqoApi(directUrl, trackId, title, artist, durationMs)
        if (directRes is LyricsFetchResult.Success) return@withContext directRes

        if (cleanTitle != title || cleanArtist != artist) {
            val cleanUrl = buildLiriqoSearchUrl(cleanTitle, cleanArtist, durationSeconds)
            val cleanRes = queryLiriqoApi(cleanUrl, trackId, title, artist, durationMs)
            if (cleanRes is LyricsFetchResult.Success) return@withContext cleanRes
        }

        if (directRes is LyricsFetchResult.Error) {
            LyricsFetchResult.Error
        } else {
            LyricsFetchResult.Empty
        }
    }

    private fun buildLiriqoSearchUrl(t: String, a: String, dSec: Long): String {
        val sb = StringBuilder("https://api.liriqo-alfarrizi.my.id/v1/lyrics?title=")
            .append(URLEncoder.encode(t, "UTF-8"))
            .append("&artist=")
            .append(URLEncoder.encode(a, "UTF-8"))
        if (dSec > 0) {
            sb.append("&duration=").append(dSec)
        }
        return sb.toString()
    }

    private fun queryLiriqoApi(
        url: String,
        trackId: String,
        title: String,
        artist: String,
        durationMs: Long
    ): LyricsFetchResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .get()
                .build()

            val (code, body) = httpClient.newCall(request).execute().use { resp ->
                resp.code to resp.body?.string()
            }

            if (code == 200 && !body.isNullOrBlank()) {
                val lyricsData = com.ayashii.kuro.data.native.KuroNativeCore.parseLiriqo(
                    rawJson = body,
                    durationMs = durationMs,
                    trackId = trackId,
                    title = title,
                    artist = artist
                )
                if (lyricsData != null && isValidLyricsLines(lyricsData.lines)) {
                    LyricsFetchResult.Success(lyricsData)
                } else {
                    LyricsFetchResult.Empty
                }
            } else if (code == 404 || code == 400) {
                LyricsFetchResult.Empty
            } else {
                LyricsFetchResult.Error
            }
        } catch (_: Exception) {
            LyricsFetchResult.Error
        }
    }

    fun isValidLyricsLines(lines: List<LyricLine>): Boolean {
        val sanitized = LrcParser.sanitizeLines(lines)
        if (sanitized.isEmpty()) return false
        val nonBlank = sanitized.filter { it.text.isNotBlank() && !isIntroToken(it.text) }
        if (nonBlank.isEmpty()) return false
        if (nonBlank.size <= 2) {
            val combined = nonBlank.joinToString(" ") { it.text.lowercase().trim() }
            if (combined.contains("null") || combined.contains("batal") || combined.contains("cancel")) {
                return false
            }
        }
        return true
    }

    suspend fun translateLyrics(
        lyrics: LyricsData,
        targetLang: LyricsLanguage = resolveTargetLang()
    ): LyricsData = withContext(Dispatchers.IO) {
        if (lyrics.lines.isEmpty()) return@withContext lyrics
        val cacheKey = "${lyrics.trackId}_${targetLang.deeplCode}"
        val langDiskFile = File(diskCacheDir, "${lyrics.trackId}_${targetLang.deeplCode}.json")
        if (langDiskFile.exists()) {
            try {
                val cached = parseLyricsJson(langDiskFile.readText())
                if (cached != null && cached.lines.any { !it.translation.isNullOrBlank() }) {
                    synchronized(memCache) {
                        memCache.put(cacheKey, cached)
                        memCache.put(lyrics.trackId, cached)
                    }
                    return@withContext cached
                }
            } catch (_: Exception) {}
        }

        val translatedLines = translateLines(lyrics.lines, targetLang)
        val result = lyrics.copy(lines = translatedLines)
        synchronized(memCache) {
            memCache.put(cacheKey, result)
            memCache.put(lyrics.trackId, result)
        }
        try {
            langDiskFile.writeText(serializeLyricsJson(result))
        } catch (_: Exception) {}
        result
    }

    private val DEEPL_ENDPOINTS = listOf(
        "https://oneshot.fal.free.deepl.com/v1/translate",
        "https://oneshot.kef.free.deepl.com/v1/translate"
    )
    private val DEEPL_USER_AGENT = "DeepL/26.41.0 (com.deepl.mobiletranslator; build:264100; Android 14)"

    private fun translateLinesToIndonesian(lines: List<LyricLine>): List<LyricLine> {
        return translateLines(lines, resolveTargetLang())
    }

    private fun translateLines(lines: List<LyricLine>, targetLang: LyricsLanguage): List<LyricLine> {
        val cleanInput = LrcParser.sanitizeLines(lines)
        if (cleanInput.isEmpty()) return emptyList()

        val rawTexts = cleanInput.map { it.text }
        val payload = com.ayashii.kuro.data.native.KuroNativeCore.buildDeepLRequestPayload(rawTexts, targetLang.deeplCode)

        for (endpoint in DEEPL_ENDPOINTS) {
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", DEEPL_USER_AGENT)
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val respBody = resp.body?.string().orEmpty()
                        val translatedTexts = com.ayashii.kuro.data.native.KuroNativeCore.parseDeepLResponseTranslations(respBody)
                        if (translatedTexts.isNotEmpty()) {
                            return cleanInput.mapIndexed { idx, line ->
                                val trans = translatedTexts.getOrNull(idx)?.trim()
                                val validTrans = if (!trans.isNullOrBlank() && !trans.equals(line.text.trim(), ignoreCase = true) && !LrcParser.isJunkOrCreditLine(trans)) trans else null
                                line.copy(translation = validTrans)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("LyricsRepository", "DeepL endpoint $endpoint error: ${e.message}")
            }
        }

        try {
            val textToTranslate = cleanInput.mapIndexed { idx, line ->
                "[[[$idx]]] ${line.text}"
            }.joinToString("\n")

            val formBody = ("client=gtx&sl=auto&tl=${targetLang.googleCode}&dt=t&q=" + encode(textToTranslate))
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val req = Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .post(formBody)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonArr = JSONArray(resp.body?.string().orEmpty())
                    val segments = jsonArr.optJSONArray(0)
                    if (segments != null) {
                        val sb = StringBuilder()
                        for (i in 0 until segments.length()) {
                            val seg = segments.optJSONArray(i)
                            if (seg != null) {
                                sb.append(seg.optString(0))
                            }
                        }
                        val fullTranslated = sb.toString()

                        val tagRegex = Regex("""\[\s*\[\s*\[\s*(\d+)\s*\]\s*\]\s*\]\s*([\s\S]*?)(?=(?:\[\s*\[\s*\[\s*\d+\s*\]\s*\]\s*\]|\Z))""")
                        val tagMatches = tagRegex.findAll(fullTranslated).toList()
                        val translatedMap = mutableMapOf<Int, String>()
                        for (m in tagMatches) {
                            val idx = m.groupValues[1].toIntOrNull()
                            val text = m.groupValues[2].trim()
                            if (idx != null) {
                                translatedMap[idx] = text
                            }
                        }

                        if (translatedMap.isNotEmpty()) {
                            return cleanInput.mapIndexed { idx, line ->
                                val trans = translatedMap[idx]
                                val validTrans = if (!trans.isNullOrBlank() && !trans.equals(line.text.trim(), ignoreCase = true) && !LrcParser.isJunkOrCreditLine(trans)) trans else null
                                line.copy(translation = validTrans)
                            }
                        }

                        val delimiterRegex = Regex("""(?:\r?\n)?\s*(?:~[\s~]+~|\[\s*\[\s*\[\s*\d+\s*\]\s*\]\s*\])\s*(?:\r?\n)?""")
                        val translatedBlocks = if (delimiterRegex.containsMatchIn(fullTranslated)) {
                            fullTranslated.split(delimiterRegex).map { it.trim() }.filter { it.isNotEmpty() }
                        } else {
                            fullTranslated.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        }

                        return cleanInput.mapIndexed { idx, line ->
                            val trans = translatedBlocks.getOrNull(idx)?.trim()
                            val validTrans = if (!trans.isNullOrBlank() && !trans.equals(line.text.trim(), ignoreCase = true) && !trans.matches(Regex("""^\[\s*\[\s*\[.*\]\s*\]\s*\]$""")) && !LrcParser.isJunkOrCreditLine(trans)) trans else null
                            line.copy(translation = validTrans)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("LyricsRepository", "Fallback translation error: ${e.message}")
        }
        return cleanInput
    }

    private fun encode(str: String): String = URLEncoder.encode(str, "UTF-8")

    private fun serializeLyricsJson(data: LyricsData): String {
        val root = JSONObject().apply {
            put("trackId", data.trackId)
            put("title", data.title)
            put("artist", data.artist)
            put("isSynced", data.isSynced)
            val linesArr = JSONArray()
            data.lines.forEach { line ->
                val lineObj = JSONObject().apply {
                    put("timeMs", line.timeMs)
                    put("text", line.text)
                    if (line.translation != null) put("translation", line.translation)
                    if (line.endTimeMs != null) put("endTimeMs", line.endTimeMs)
                }
                linesArr.put(lineObj)
            }
            put("lines", linesArr)
        }
        return root.toString()
    }

    private fun parseLyricsJson(jsonStr: String): LyricsData? {
        return try {
            val root = JSONObject(jsonStr)
            val trackId = root.getString("trackId")
            val title = root.getString("title")
            val artist = root.getString("artist")
            val isSynced = root.optBoolean("isSynced", true)
            val linesArr = root.getJSONArray("lines")
            val rawLines = mutableListOf<LyricLine>()
            for (i in 0 until linesArr.length()) {
                val obj = linesArr.getJSONObject(i)
                rawLines.add(
                    LyricLine(
                        timeMs = obj.getLong("timeMs"),
                        text = obj.getString("text"),
                        translation = if (obj.has("translation")) obj.getString("translation") else null,
                        endTimeMs = if (obj.has("endTimeMs") && !obj.isNull("endTimeMs")) obj.getLong("endTimeMs") else null
                    )
                )
            }
            val sanitizedLines = LrcParser.sanitizeLines(rawLines)
            if (!isValidLyricsLines(sanitizedLines)) return null
            LyricsData(trackId, title, artist, sanitizedLines, isSynced)
        } catch (e: Exception) {
            null
        }
    }
}
