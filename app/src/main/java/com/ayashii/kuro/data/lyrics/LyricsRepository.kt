package com.ayashii.kuro.data.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsRepository(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val memCache = LruCache<String, LyricsData>(50)
    private val diskCacheDir = File(context.cacheDir, "lyrics_cache").apply { mkdirs() }

    suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        durationSeconds: Long = 0L,
        autoTranslate: Boolean = true
    ): LyricsData? = withContext(Dispatchers.IO) {
        // 1. Check in-memory cache
        synchronized(memCache) {
            memCache.get(trackId)?.let { return@withContext it }
        }

        // 2. Check disk cache
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

        // 3. Clean up title and artist for maximum matching accuracy
        val cleanTitle = cleanSearchTerm(title)
        val cleanArtist = cleanSearchTerm(artist)

        // 4. Fetch from LRCLIB
        var lyrics = fetchFromLrcLib(trackId, title, artist, cleanTitle, cleanArtist, durationSeconds)

        // 5. If lyrics found and translation requested:
        if (lyrics != null && autoTranslate && lyrics.lines.isNotEmpty()) {
            val translatedLines = translateLinesToIndonesian(lyrics.lines)
            lyrics = lyrics.copy(lines = translatedLines)
        }

        // 6. Save to cache
        if (lyrics != null) {
            synchronized(memCache) { memCache.put(trackId, lyrics) }
            try {
                diskFile.writeText(serializeLyricsJson(lyrics))
            } catch (e: Exception) {
                Log.d("LyricsRepository", "Disk cache save error: ${e.message}")
            }
        }

        lyrics
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
            // Attempt 1: Exact search
            val exactUrl = "https://lrclib.net/api/get?track_name=${encode(cleanTitle)}&artist_name=${encode(cleanArtist)}" +
                    if (durationSeconds > 0) "&duration=$durationSeconds" else ""

            val exactReq = Request.Builder()
                .url(exactUrl)
                .header("User-Agent", "KuroMusic/1.6.0 (Android)")
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

            // Attempt 2: General query search
            val searchUrl = "https://lrclib.net/api/search?q=${encode("$cleanTitle $cleanArtist")}"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "KuroMusic/1.6.0 (Android)")
                .build()

            val searchResp = httpClient.newCall(searchReq).execute()
            if (searchResp.isSuccessful) {
                val arr = JSONArray(searchResp.body?.string().orEmpty())
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val synced = item.optString("syncedLyrics")
                    val plain = item.optString("plainLyrics")

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
        } catch (e: Exception) {
            Log.e("LyricsRepository", "LRCLIB fetch error: ${e.message}")
        }
        return null
    }

    private fun translateLinesToIndonesian(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines
        try {
            val textToTranslate = lines.joinToString("\n") { it.text }
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=id&dt=t&q=" + encode(textToTranslate)

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
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
                    val translatedLines = sb.toString().lines()
                    return lines.mapIndexed { idx, line ->
                        val trans = translatedLines.getOrNull(idx)?.trim()
                        line.copy(translation = if (!trans.isNullOrBlank() && trans != line.text) trans else null)
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
