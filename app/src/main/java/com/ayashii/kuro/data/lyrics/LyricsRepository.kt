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
    private val NEGATIVE_CACHE_TTL = 7 * 24 * 60 * 60 * 1000L // 7 days
    private val diskCacheDir = File(context.cacheDir, "lyrics_cache_v3").apply { mkdirs() }

    suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        durationSeconds: Long = 0L,
        autoTranslate: Boolean = true
    ): LyricsData? = withContext(Dispatchers.IO) {
        // 1. Check in-memory negative cache (track previously confirmed to have no lyrics)
        synchronized(negativeCache) {
            val emptyAt = negativeCache.get(trackId)
            if (emptyAt != null && System.currentTimeMillis() - emptyAt < NEGATIVE_CACHE_TTL) {
                return@withContext null
            }
        }

        // 2. Check in-memory cache
        synchronized(memCache) {
            memCache.get(trackId)?.let { return@withContext it }
        }

        // 3. Check disk cache
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

        // 4. Clean up title and artist for maximum matching accuracy
        val cleanTitle = cleanSearchTerm(title)
        val cleanArtist = cleanSearchTerm(artist)

        // 5. Fetch from LRCLIB
        var lyrics = fetchFromLrcLib(trackId, title, artist, cleanTitle, cleanArtist, durationSeconds)

        // 6. If LRCLIB had no lyrics, fallback to YouTube Music Official Lyrics
        if (lyrics == null) {
            lyrics = fetchFromYouTubeMusic(trackId, title, artist, durationSeconds)
        }

        // 7. If lyrics found and translation requested:
        if (lyrics != null && autoTranslate && lyrics.lines.isNotEmpty()) {
            val translatedLines = translateLinesToIndonesian(lyrics.lines)
            lyrics = lyrics.copy(lines = translatedLines)
        }

        // 8. Save to cache
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
            // Negative cache: prevent repeated wasted requests for tracks without lyrics
            synchronized(negativeCache) { negativeCache.put(trackId, System.currentTimeMillis()) }
            try {
                emptyMarker.createNewFile()
            } catch (_: Exception) {}
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

    private fun fetchFromLrcLib(
        trackId: String,
        origTitle: String,
        origArtist: String,
        cleanTitle: String,
        cleanArtist: String,
        durationSeconds: Long
    ): LyricsData? {
        try {
            // Attempt 1: Exact search with duration
            if (durationSeconds > 0) {
                val exactUrl = "https://lrclib.net/api/get?track_name=${encode(cleanTitle)}&artist_name=${encode(cleanArtist)}&duration=$durationSeconds"
                val exactReq = Request.Builder()
                    .url(exactUrl)
                    .header("User-Agent", "KuroMusic/2.4.0 (Android)")
                    .build()

                val exactResp = httpClient.newCall(exactReq).execute()
                if (exactResp.isSuccessful) {
                    val json = JSONObject(exactResp.body?.string().orEmpty())
                    val synced = json.optString("syncedLyrics")
                    val plain = json.optString("plainLyrics")

                    if (synced.isNotBlank()) {
                        val lines = LrcParser.parse(synced)
                        if (lines.isNotEmpty()) {
                            return LyricsData(trackId, origTitle, origArtist, lines, isSynced = true)
                        }
                    } else if (plain.isNotBlank()) {
                        val lines = LrcParser.parsePlain(plain, durationSeconds * 1000L)
                        return LyricsData(trackId, origTitle, origArtist, lines, isSynced = false)
                    }
                }
            }

            // Attempt 2: Smart Search with Duration-Matching Filter
            val targetedUrl = "https://lrclib.net/api/search?track_name=${encode(cleanTitle)}&artist_name=${encode(cleanArtist)}"
            val targetedReq = Request.Builder()
                .url(targetedUrl)
                .header("User-Agent", "KuroMusic/2.4.0 (Android)")
                .build()

            val targetedResp = httpClient.newCall(targetedReq).execute()
            if (targetedResp.isSuccessful) {
                val arr = JSONArray(targetedResp.body?.string().orEmpty())
                val candidates = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) {
                    candidates.add(arr.getJSONObject(i))
                }

                // Filter synced lyrics first, sorted by closest duration!
                val syncedCandidates = candidates.filter { it.optString("syncedLyrics").isNotBlank() }
                if (syncedCandidates.isNotEmpty()) {
                    val best = if (durationSeconds > 0) {
                        // Priority: Within +/- 2.5 seconds
                        val closeMatch = syncedCandidates.filter {
                            val dur = it.optDouble("duration", 0.0)
                            kotlin.math.abs(dur - durationSeconds) <= 2.5
                        }.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }

                        closeMatch ?: syncedCandidates.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }
                    } else {
                        syncedCandidates.firstOrNull()
                    }

                    if (best != null) {
                        val lines = LrcParser.parse(best.getString("syncedLyrics"))
                        if (lines.isNotEmpty()) {
                            return LyricsData(trackId, origTitle, origArtist, lines, isSynced = true)
                        }
                    }
                }

                // Fallback to plain lyrics with closest duration
                val plainCandidates = candidates.filter { it.optString("plainLyrics").isNotBlank() }
                if (plainCandidates.isNotEmpty()) {
                    val bestPlain = if (durationSeconds > 0) {
                        plainCandidates.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }
                    } else plainCandidates.firstOrNull()

                    if (bestPlain != null) {
                        val lines = LrcParser.parsePlain(bestPlain.getString("plainLyrics"))
                        return LyricsData(trackId, origTitle, origArtist, lines, isSynced = false)
                    }
                }
            }

            // Attempt 3: General query search (Ensures we never miss synced lyrics!)
            val generalUrl = "https://lrclib.net/api/search?q=${encode("$cleanTitle $cleanArtist")}"
            val generalReq = Request.Builder()
                .url(generalUrl)
                .header("User-Agent", "KuroMusic/2.5.0 (Android)")
                .build()

            val generalResp = httpClient.newCall(generalReq).execute()
            if (generalResp.isSuccessful) {
                val arr = JSONArray(generalResp.body?.string().orEmpty())
                val candidates = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) {
                    candidates.add(arr.getJSONObject(i))
                }
                val syncedCandidates = candidates.filter { it.optString("syncedLyrics").isNotBlank() }
                if (syncedCandidates.isNotEmpty()) {
                    val best = if (durationSeconds > 0) {
                        val closeMatch = syncedCandidates.filter {
                            val dur = it.optDouble("duration", 0.0)
                            kotlin.math.abs(dur - durationSeconds) <= 2.5
                        }.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }
                        closeMatch ?: syncedCandidates.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }
                    } else {
                        syncedCandidates.firstOrNull()
                    }

                    if (best != null) {
                        val lines = LrcParser.parse(best.getString("syncedLyrics"))
                        if (lines.isNotEmpty()) {
                            return LyricsData(trackId, origTitle, origArtist, lines, isSynced = true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LyricsRepository", "LRCLIB fetch error: ${e.message}")
        }
        return null
    }

    private fun fetchFromYouTubeMusic(
        trackId: String,
        origTitle: String,
        origArtist: String,
        durationSeconds: Long
    ): LyricsData? {
        try {
            val nextPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", trackId)
            }

            val nextReq = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/next")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240101.01.00")
                .post(nextPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val nextResp = httpClient.newCall(nextReq).execute()
            if (!nextResp.isSuccessful) return null
            val nextJson = JSONObject(nextResp.body?.string().orEmpty())
            val tabs = nextJson.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return null

            var browseId: String? = null
            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer")
                val title = tab?.optString("title", "")
                if (title.equals("Lyrics", ignoreCase = true)) {
                    browseId = tab?.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                    break
                }
            }

            if (!browseId.isNullOrBlank()) {
                val browsePayload = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB_REMIX")
                            put("clientVersion", "1.20240101.01.00")
                            put("hl", "en")
                            put("gl", "US")
                        })
                    })
                    put("browseId", browseId)
                }

                val browseReq = Request.Builder()
                    .url("https://music.youtube.com/youtubei/v1/browse")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .header("X-YouTube-Client-Name", "67")
                    .header("X-YouTube-Client-Version", "1.20240101.01.00")
                    .post(browsePayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val browseResp = httpClient.newCall(browseReq).execute()
                if (browseResp.isSuccessful) {
                    val browseJson = JSONObject(browseResp.body?.string().orEmpty())
                    val contents = browseJson.optJSONObject("contents")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")
                    val shelf = contents?.optJSONObject(0)?.optJSONObject("musicDescriptionShelfRenderer")
                    val runs = shelf?.optJSONObject("description")?.optJSONArray("runs")
                    if (runs != null && runs.length() > 0) {
                        val sb = StringBuilder()
                        for (j in 0 until runs.length()) {
                            sb.append(runs.optJSONObject(j)?.optString("text", ""))
                        }
                        val rawText = sb.toString().trim()
                        if (isValidLyricsText(rawText)) {
                            val lines = LrcParser.parsePlain(rawText)
                            if (isValidLyricsLines(lines)) {
                                return LyricsData(trackId, origTitle, origArtist, lines, isSynced = false)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("LyricsRepository", "YTM Lyrics fetch error: ${e.message}")
        }
        return null
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

    private fun translateLinesToIndonesian(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines
        try {
            val textToTranslate = lines.joinToString("\n~~~\n") { it.text }
            val formBody = ("client=gtx&sl=auto&tl=id&dt=t&q=" + encode(textToTranslate))
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val req = Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .post(formBody)
                .build()

            val resp = httpClient.newCall(req).execute()
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
                    val translatedBlocks = if (fullTranslated.contains("~~~")) {
                        fullTranslated.split("~~~").map { it.trim() }
                    } else {
                        fullTranslated.lines().map { it.trim() }
                    }

                    return lines.mapIndexed { idx, line ->
                        val trans = translatedBlocks.getOrNull(idx)?.trim()
                        val validTrans = if (!trans.isNullOrBlank() && !trans.equals(line.text.trim(), ignoreCase = true) && !trans.equals("~~~")) trans else null
                        line.copy(translation = validTrans)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("LyricsRepository", "Translation error: ${e.message}")
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
                        translation = if (obj.has("translation")) obj.getString("translation") else null
                    )
                )
            }
            LyricsData(trackId, title, artist, lines, isSynced)
        } catch (e: Exception) {
            null
        }
    }
}
