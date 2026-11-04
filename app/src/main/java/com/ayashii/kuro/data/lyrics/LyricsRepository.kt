package com.ayashii.kuro.data.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsRepository(private val context: Context) {

    private val httpClient = com.ayashii.kuro.data.remote.HttpClientProvider.client

    private val memCache = LruCache<String, LyricsData>(100)
    private val negativeCache = LruCache<String, Long>(200)
    private val NEGATIVE_CACHE_TTL = 7 * 24 * 60 * 60 * 1000L
    private val diskCacheDir = File(context.cacheDir, "lyrics_cache_v5").apply {
        mkdirs()
        try {
            val oldCache = File(context.cacheDir, "lyrics_cache_v4")
            if (oldCache.exists()) oldCache.deleteRecursively()
        } catch (_: Exception) {}
    }

    private sealed class LyricsFetchResult {
        data class Success(val data: LyricsData) : LyricsFetchResult()
        object Empty : LyricsFetchResult()
        object Error : LyricsFetchResult()
    }

    companion object {
        private const val YTM_CLIENT_NAME = "WEB_REMIX"
        private const val YTM_CLIENT_VERSION = "1.20241028.01.00"
        private const val YTM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        private const val YTM_ORIGIN = "https://music.youtube.com"
    }

    suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        durationSeconds: Long = 0L,
        autoTranslate: Boolean = true
    ): LyricsData? = withContext(Dispatchers.IO) {
        synchronized(negativeCache) {
            val emptyAt = negativeCache.get(trackId)
            if (emptyAt != null && System.currentTimeMillis() - emptyAt < NEGATIVE_CACHE_TTL) {
                return@withContext null
            }
        }

        synchronized(memCache) {
            memCache.get(trackId)?.let { return@withContext it }
        }

        val diskFile = File(diskCacheDir, "$trackId.json")
        if (diskFile.exists()) {
            try {
                val cached = parseLyricsJson(diskFile.readText())
                if (cached != null) {
                    synchronized(memCache) { memCache.put(trackId, cached) }
                    return@withContext cached
                }
            } catch (e: Exception) {
                Log.d("LyricsRepository", "Disk cache read error: ${e.message}")
            }
        }

        val emptyMarker = File(diskCacheDir, "$trackId.empty")
        if (emptyMarker.exists() && System.currentTimeMillis() - emptyMarker.lastModified() < NEGATIVE_CACHE_TTL) {
            synchronized(negativeCache) { negativeCache.put(trackId, emptyMarker.lastModified()) }
            return@withContext null
        }

        val cleanTitle = cleanSearchTerm(title)
        val cleanArtist = cleanSearchTerm(artist)

        val ytmResult = fetchFromYouTubeMusic(trackId, title, artist, durationSeconds)
        var lyrics: LyricsData? = null
        var lrcLibResult: LyricsFetchResult = LyricsFetchResult.Empty

        if (ytmResult is LyricsFetchResult.Success && ytmResult.data.isSynced) {
            lyrics = ytmResult.data
        } else {
            lrcLibResult = fetchFromLrcLib(trackId, title, artist, cleanTitle, cleanArtist, durationSeconds)
            if (lrcLibResult is LyricsFetchResult.Success && lrcLibResult.data.isSynced) {
                lyrics = compensateLrcOffset(lrcLibResult.data)
            } else {
                val ytmStatic = if (ytmResult is LyricsFetchResult.Success && !ytmResult.data.isSynced) ytmResult.data else null
                val lrcStatic = if (lrcLibResult is LyricsFetchResult.Success && !lrcLibResult.data.isSynced) lrcLibResult.data else null
                lyrics = ytmStatic ?: lrcStatic
            }
        }

        if (lyrics != null && autoTranslate && lyrics.lines.isNotEmpty()) {
            val translatedLines = translateLinesToIndonesian(lyrics.lines)
            lyrics = lyrics.copy(lines = translatedLines)
        }

        if (lyrics != null) {
            synchronized(memCache) { memCache.put(trackId, lyrics) }
            try {
                pruneDiskCacheIfNeeded()
                diskFile.writeText(serializeLyricsJson(lyrics))
                if (emptyMarker.exists()) emptyMarker.delete()
            } catch (e: Exception) {
                Log.d("LyricsRepository", "Disk cache save error: ${e.message}")
            }
        } else {
            val ytmVerifiedEmpty = ytmResult is LyricsFetchResult.Empty
            val lrcVerifiedEmpty = lrcLibResult is LyricsFetchResult.Empty
            val hadNetworkError = ytmResult is LyricsFetchResult.Error || lrcLibResult is LyricsFetchResult.Error

            if (!hadNetworkError && (ytmVerifiedEmpty || lrcVerifiedEmpty)) {
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

    private fun isWatermarkLine(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.startsWith("by ") || lower.contains("sync by") || lower.contains("synced by") ||
                lower.contains("lyrics by") || lower.contains("arranged by") || lower.contains("translated by") ||
                lower.contains("med el baraka")
    }

    private fun fetchFromLrcLib(
        trackId: String,
        origTitle: String,
        origArtist: String,
        cleanTitle: String,
        cleanArtist: String,
        durationSeconds: Long
    ): LyricsFetchResult {
        val candidates = mutableListOf<JSONObject>()
        var hadNetworkError = false
        var hadVerifiedResponse = false

        try {
            val targetedUrl = "https://lrclib.net/api/search?track_name=${encode(cleanTitle)}&artist_name=${encode(cleanArtist)}"
            val targetedReq = Request.Builder()
                .url(targetedUrl)
                .header("User-Agent", "KuroMusic/3.0.0 (Android)")
                .build()

            httpClient.newCall(targetedReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    hadVerifiedResponse = true
                    val arr = JSONArray(resp.body?.string().orEmpty())
                    for (i in 0 until arr.length()) {
                        candidates.add(arr.getJSONObject(i))
                    }
                } else if (resp.code == 404) {
                    hadVerifiedResponse = true
                } else {
                    hadNetworkError = true
                }
            }
        } catch (e: Exception) {
            hadNetworkError = true
            Log.d("LyricsRepository", "LRCLIB targeted search error: ${e.message}")
        }

        try {
            val generalUrl = "https://lrclib.net/api/search?q=${encode("$cleanTitle $cleanArtist")}"
            val generalReq = Request.Builder()
                .url(generalUrl)
                .header("User-Agent", "KuroMusic/3.0.0 (Android)")
                .build()

            httpClient.newCall(generalReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    hadVerifiedResponse = true
                    val arr = JSONArray(resp.body?.string().orEmpty())
                    for (i in 0 until arr.length()) {
                        candidates.add(arr.getJSONObject(i))
                    }
                } else if (resp.code == 404) {
                    hadVerifiedResponse = true
                } else {
                    hadNetworkError = true
                }
            }
        } catch (e: Exception) {
            hadNetworkError = true
            Log.d("LyricsRepository", "LRCLIB general search error: ${e.message}")
        }

        try {
            val exactUrl = if (durationSeconds > 0) {
                "https://lrclib.net/api/get?track_name=${encode(cleanTitle)}&artist_name=${encode(cleanArtist)}&duration=$durationSeconds"
            } else {
                "https://lrclib.net/api/get?track_name=${encode(cleanTitle)}&artist_name=${encode(cleanArtist)}"
            }
            val exactReq = Request.Builder()
                .url(exactUrl)
                .header("User-Agent", "KuroMusic/3.0.0 (Android)")
                .build()

            httpClient.newCall(exactReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    hadVerifiedResponse = true
                    val obj = JSONObject(resp.body?.string().orEmpty())
                    if (obj.has("id")) {
                        candidates.add(obj)
                    }
                } else if (resp.code == 404) {
                    hadVerifiedResponse = true
                } else {
                    hadNetworkError = true
                }
            }
        } catch (e: Exception) {
            hadNetworkError = true
            Log.d("LyricsRepository", "LRCLIB exact get error: ${e.message}")
        }

        if (candidates.isEmpty()) {
            return if (!hadNetworkError && hadVerifiedResponse) LyricsFetchResult.Empty else LyricsFetchResult.Error
        }

        val uniqueCandidates = candidates.distinctBy { it.optLong("id", -1L) }

        data class ScoredCandidate(
            val lines: List<LyricLine>,
            val score: Double
        )

        val scoredSynced = mutableListOf<ScoredCandidate>()

        for (cand in uniqueCandidates) {
            val synced = cand.optString("syncedLyrics")
            if (synced.isBlank()) continue

            val rawLines = LrcParser.parse(synced)
            if (rawLines.isEmpty()) continue

            val lines = rawLines.toMutableList()
            while (lines.size > 1 && lines[0].timeMs == 0L && (isIntroToken(lines[0].text) || isWatermarkLine(lines[0].text))) {
                lines.removeAt(0)
            }

            var score = 1000.0
            val candDur = cand.optDouble("duration", 0.0)
            if (durationSeconds > 0 && candDur > 0) {
                val durDiff = kotlin.math.abs(candDur - durationSeconds)
                score -= durDiff * 20.0
            }

            if (lines.size > 1) {
                val t0 = lines[0].timeMs
                val t1 = lines[1].timeMs
                if (t0 < 15000L && (t1 - t0) > 15000L && !isIntroToken(lines[0].text)) {
                    score -= 5000.0
                }
            }

            if (lines.size < 8) {
                score -= 400.0
            } else if (lines.size in 25..85) {
                score += 50.0
            }

            scoredSynced.add(ScoredCandidate(lines, score))
        }

        val bestSynced = scoredSynced.maxByOrNull { it.score }
        if (bestSynced != null && bestSynced.score > 0) {
            return LyricsFetchResult.Success(LyricsData(trackId, origTitle, origArtist, bestSynced.lines, isSynced = true))
        }

        val plainCandidates = uniqueCandidates.filter { it.optString("plainLyrics").isNotBlank() }
        if (plainCandidates.isNotEmpty()) {
            val bestPlainObj = if (durationSeconds > 0) {
                plainCandidates.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }
            } else plainCandidates.firstOrNull()

            if (bestPlainObj != null) {
                val lines = LrcParser.parsePlain(bestPlainObj.getString("plainLyrics"), durationSeconds * 1000L)
                return LyricsFetchResult.Success(LyricsData(trackId, origTitle, origArtist, lines, isSynced = false))
            }
        }

        return if (!hadNetworkError && hadVerifiedResponse) LyricsFetchResult.Empty else LyricsFetchResult.Error
    }

    private fun buildInnerTubeRequest(endpoint: String, payload: JSONObject): Request {
        val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
        val cookie = auth.getCookie().orEmpty()
        val sapisidHash = auth.getSapisidHash(YTM_ORIGIN)

        val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url("$YTM_ORIGIN/youtubei/v1/$endpoint")
            .header("Content-Type", "application/json")
            .header("User-Agent", YTM_USER_AGENT)
            .header("Origin", YTM_ORIGIN)
            .header("Referer", "$YTM_ORIGIN/")
            .header("X-Origin", YTM_ORIGIN)
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)
            .post(reqBody)

        if (cookie.isNotBlank()) {
            builder.header("Cookie", cookie)
        }
        if (!sapisidHash.isNullOrBlank()) {
            builder.header("Authorization", sapisidHash)
            builder.header("X-Goog-AuthUser", "0")
        }
        return builder.build()
    }

    private fun createInnerTubeContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", YTM_CLIENT_NAME)
                put("clientVersion", YTM_CLIENT_VERSION)
                put("hl", "en")
                put("gl", "US")
            })
        }
    }

    private fun fetchFromYouTubeMusic(
        trackId: String,
        origTitle: String,
        origArtist: String,
        durationSeconds: Long
    ): LyricsFetchResult {
        try {
            val nextPayload = JSONObject().apply {
                put("context", createInnerTubeContext())
                put("videoId", trackId)
            }
            val nextReq = buildInnerTubeRequest("next", nextPayload)
            val (nextSuccess, nextCode, nextBody) = httpClient.newCall(nextReq).execute().use { resp ->
                Triple(resp.isSuccessful, resp.code, resp.body?.string().orEmpty())
            }
            if (!nextSuccess) {
                return if (nextCode == 404) LyricsFetchResult.Empty else LyricsFetchResult.Error
            }

            val nextJson = JSONObject(nextBody)
            val tabs = nextJson.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return LyricsFetchResult.Empty

            var browseId: String? = null
            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                val endpoint = tab.optJSONObject("endpoint") ?: tab.optJSONObject("unselectedEndpoint")
                val bId = endpoint?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                val title = tab.optString("title", "")
                if (title.equals("Lyrics", ignoreCase = true) || title.equals("Lirik", ignoreCase = true) || bId.startsWith("MPLY")) {
                    browseId = bId
                    break
                }
            }

            if (browseId.isNullOrBlank()) return LyricsFetchResult.Empty

            val browsePayload = JSONObject().apply {
                put("context", createInnerTubeContext())
                put("browseId", browseId)
            }
            val browseReq = buildInnerTubeRequest("browse", browsePayload)
            val (browseSuccess, browseCode, browseBody) = httpClient.newCall(browseReq).execute().use { resp ->
                Triple(resp.isSuccessful, resp.code, resp.body?.string().orEmpty())
            }
            if (!browseSuccess) {
                return if (browseCode == 404) LyricsFetchResult.Empty else LyricsFetchResult.Error
            }

            val browseJson = JSONObject(browseBody)
            val contents = browseJson.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return LyricsFetchResult.Empty

            var timedLyricsRenderer: JSONObject? = null
            var staticShelfRenderer: JSONObject? = null

            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i) ?: continue
                if (timedLyricsRenderer == null) {
                    timedLyricsRenderer = item.optJSONObject("musicTimedLyricsRenderer")
                }
                if (staticShelfRenderer == null) {
                    staticShelfRenderer = item.optJSONObject("musicDescriptionShelfRenderer")
                }
            }

            if (timedLyricsRenderer != null) {
                val timedData = timedLyricsRenderer.optJSONArray("timedLyricsData")
                if (timedData != null && timedData.length() > 0) {
                    val lines = mutableListOf<LyricLine>()
                    for (i in 0 until timedData.length()) {
                        val lineObj = timedData.optJSONObject(i) ?: continue
                        val text = extractLyricText(lineObj)
                        if (text.isBlank()) continue

                        val cueRange = lineObj.optJSONObject("cueRange")
                        val rawTime = cueRange?.opt("startTimeMilliseconds")
                            ?: lineObj.opt("cueRangeStartMillis")
                            ?: lineObj.opt("startTimeMs")

                        val timeMs = when (rawTime) {
                            is Number -> rawTime.toLong()
                            is String -> rawTime.toLongOrNull() ?: 0L
                            else -> 0L
                        }

                        val rawEndTime = cueRange?.opt("endTimeMilliseconds")
                            ?: lineObj.opt("cueRangeEndMillis")
                            ?: lineObj.opt("endTimeMs")

                        val endTimeMs = when (rawEndTime) {
                            is Number -> rawEndTime.toLong()
                            is String -> rawEndTime.toLongOrNull()
                            else -> null
                        }
                        lines.add(LyricLine(timeMs = timeMs, text = text, endTimeMs = endTimeMs))
                    }
                    if (lines.isNotEmpty() && isValidLyricsLines(lines)) {
                        return LyricsFetchResult.Success(
                            LyricsData(
                                trackId = trackId,
                                title = origTitle,
                                artist = origArtist,
                                lines = lines.sortedBy { it.timeMs },
                                isSynced = true
                            )
                        )
                    }
                }
            }

            if (staticShelfRenderer != null) {
                val runs = staticShelfRenderer.optJSONObject("description")?.optJSONArray("runs")
                if (runs != null && runs.length() > 0) {
                    val sb = StringBuilder()
                    for (j in 0 until runs.length()) {
                        sb.append(runs.optJSONObject(j)?.optString("text", "").orEmpty())
                    }
                    val rawText = sb.toString().trim()
                    if (isValidLyricsText(rawText)) {
                        val lines = LrcParser.parsePlain(rawText)
                        if (isValidLyricsLines(lines)) {
                            return LyricsFetchResult.Success(
                                LyricsData(
                                    trackId = trackId,
                                    title = origTitle,
                                    artist = origArtist,
                                    lines = lines,
                                    isSynced = false
                                )
                            )
                        }
                    }
                }
            }

            return LyricsFetchResult.Empty
        } catch (e: Exception) {
            Log.d("LyricsRepository", "YTM Lyrics fetch error: ${e.message}")
            return LyricsFetchResult.Error
        }
    }

    private fun extractLyricText(lineObj: JSONObject): String {
        val lyricLine = lineObj.opt("lyricLine")
        return when (lyricLine) {
            is String -> lyricLine.trim()
            is JSONObject -> {
                val runs = lyricLine.optJSONArray("runs")
                if (runs != null) {
                    val sb = StringBuilder()
                    for (r in 0 until runs.length()) {
                        sb.append(runs.optJSONObject(r)?.optString("text", "").orEmpty())
                    }
                    sb.toString().trim()
                } else lyricLine.optString("text", "").trim()
            }
            else -> {
                val runs = lineObj.optJSONArray("runs")
                if (runs != null) {
                    val sb = StringBuilder()
                    for (r in 0 until runs.length()) {
                        sb.append(runs.optJSONObject(r)?.optString("text", "").orEmpty())
                    }
                    sb.toString().trim()
                } else {
                    lineObj.optString("text", "").trim()
                }
            }
        }
    }

    private fun compensateLrcOffset(lrcData: LyricsData): LyricsData {
        if (lrcData.lines.isEmpty() || !lrcData.isSynced) return lrcData
        val originalLines = lrcData.lines.sortedBy { it.timeMs }

        val cleanedLines = originalLines.toMutableList()
        while (cleanedLines.size > 1 && cleanedLines[0].timeMs == 0L && isIntroToken(cleanedLines[0].text)) {
            cleanedLines.removeAt(0)
        }

        return lrcData.copy(lines = cleanedLines)
    }

    private fun isValidLyricsText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 25) return false
        val lower = trimmed.lowercase()
        if (lower == "null" || lower == "cancel" || lower == "batal") return false
        val lines = lower.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.all { it in listOf("null", "cancel", "batal", "ok", "error") }) return false
        return true
    }

    fun isValidLyricsLines(lines: List<LyricLine>): Boolean {
        if (lines.isEmpty()) return false
        val nonBlank = lines.filter { it.text.isNotBlank() }
        if (nonBlank.isEmpty()) return false
        if (nonBlank.size <= 2) {
            val combined = nonBlank.joinToString(" ") { it.text.lowercase().trim() }
            if (combined.contains("null") || combined.contains("batal") || combined.contains("cancel")) {
                return false
            }
        }
        return true
    }

    suspend fun translateLyrics(lyrics: LyricsData): LyricsData = withContext(Dispatchers.IO) {
        if (lyrics.lines.isEmpty()) return@withContext lyrics
        val translatedLines = translateLinesToIndonesian(lyrics.lines)
        val result = lyrics.copy(lines = translatedLines)
        synchronized(memCache) { memCache.put(lyrics.trackId, result) }
        try {
            val diskFile = File(diskCacheDir, "${lyrics.trackId}.json")
            diskFile.writeText(serializeLyricsJson(result))
        } catch (_: Exception) {}
        result
    }

    private val DEEPL_ENDPOINTS = listOf(
        "https://oneshot.fal.free.deepl.com/v1/translate",
        "https://oneshot.kef.free.deepl.com/v1/translate"
    )
    private val DEEPL_USER_AGENT = "DeepL/26.41.0 (com.deepl.mobiletranslator; build:264100; Android 14)"

    private fun translateLinesToIndonesian(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines

        val rawTexts = lines.map { it.text }
        val payload = com.ayashii.kuro.data.native.KuroNativeCore.buildDeepLRequestPayload(rawTexts, "id")

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
                            return lines.mapIndexed { idx, line ->
                                val trans = translatedTexts.getOrNull(idx)?.trim()
                                val validTrans = if (!trans.isNullOrBlank() && !trans.equals(line.text.trim(), ignoreCase = true)) trans else null
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
            val textToTranslate = lines.mapIndexed { idx, line ->
                "[[[$idx]]] ${line.text}"
            }.joinToString("\n")

            val formBody = ("client=gtx&sl=auto&tl=id&dt=t&q=" + encode(textToTranslate))
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
                            return lines.mapIndexed { idx, line ->
                                val trans = translatedMap[idx]
                                val validTrans = if (!trans.isNullOrBlank() && !trans.equals(line.text.trim(), ignoreCase = true)) trans else null
                                line.copy(translation = validTrans)
                            }
                        }

                        val delimiterRegex = Regex("""(?:\r?\n)?\s*(?:~[\s~]+~|\[\s*\[\s*\[\s*\d+\s*\]\s*\]\s*\])\s*(?:\r?\n)?""")
                        val translatedBlocks = if (delimiterRegex.containsMatchIn(fullTranslated)) {
                            fullTranslated.split(delimiterRegex).map { it.trim() }.filter { it.isNotEmpty() }
                        } else {
                            fullTranslated.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        }

                        return lines.mapIndexed { idx, line ->
                            val trans = translatedBlocks.getOrNull(idx)?.trim()
                            val validTrans = if (!trans.isNullOrBlank() && !trans.equals(line.text.trim(), ignoreCase = true) && !trans.matches(Regex("""^\[\s*\[\s*\[.*\]\s*\]\s*\]$"""))) trans else null
                            line.copy(translation = validTrans)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("LyricsRepository", "Fallback translation error: ${e.message}")
        }
        return lines
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
            val lines = mutableListOf<LyricLine>()
            for (i in 0 until linesArr.length()) {
                val obj = linesArr.getJSONObject(i)
                lines.add(
                    LyricLine(
                        timeMs = obj.getLong("timeMs"),
                        text = obj.getString("text"),
                        translation = if (obj.has("translation")) obj.getString("translation") else null,
                        endTimeMs = if (obj.has("endTimeMs") && !obj.isNull("endTimeMs")) obj.getLong("endTimeMs") else null
                    )
                )
            }
            LyricsData(trackId, title, artist, lines, isSynced)
        } catch (e: Exception) {
            null
        }
    }
}
