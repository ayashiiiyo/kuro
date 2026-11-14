package com.ayashii.kuro.data.native

import android.util.Log
import com.ayashii.kuro.data.lyrics.LrcParser
import com.ayashii.kuro.data.model.Track
import org.json.JSONArray
import org.json.JSONObject

object KuroNativeCore {

    private const val TAG = "KuroNativeCore"
    private val CLEAN_NOISE_REGEX = Regex("""(?i)\s*[\(\[](?:official\s*(?:music\s*)?video|official\s*audio|music\s*video|lyric\s*video|lyrics|audio|visualizer|remastered|remaster|4k|hd|hq|mv|clip\s*officiel|video\s*oficial)[\)\]]""")
    private val CLEAN_PIPE_REGEX = Regex("""(?i)\s*\|\s*(?:official\s*(?:music\s*)?video|official\s*audio|visualizer).*""")
    private val LRC_TAG_REGEX = Regex("""\[(\d{1,2}):(\d{2}(?:\.\d{1,3})?)\]""")

    val isLoaded: Boolean = try {
        System.loadLibrary("kuro_core")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "Native kuro_core library not available, using Kotlin fallback: ${e.message}")
        false
    } catch (e: Throwable) {
        Log.w(TAG, "Failed to load kuro_core library: ${e.message}")
        false
    }

    private external fun cleanTrackTitle(rawTitle: String): String
    private external fun rankCandidates(candidatesJson: String, profileJson: String): String
    private external fun parseAutomixTracks(rawJson: String, currentVideoId: String): String
    private external fun buildDeepLPayload(linesJson: String, targetLang: String): String
    private external fun parseDeepLTranslations(responseJson: String): String
    private external fun parseLiriqoLyrics(rawJson: String, durationMs: Long): String

    fun sanitizeTitle(rawTitle: String): String {
        if (!isLoaded) return fallbackCleanTitle(rawTitle)
        return try {
            cleanTrackTitle(rawTitle)
        } catch (e: Throwable) {
            fallbackCleanTitle(rawTitle)
        }
    }

    fun rankRadioTracks(
        candidates: List<Track>,
        scores: Map<String, Int>,
        topArtists: List<String>
    ): List<Track> {
        if (candidates.isEmpty()) return candidates
        if (!isLoaded) return fallbackRankRadio(candidates, scores, topArtists)

        return try {
            val candArr = JSONArray()
            for (t in candidates) {
                candArr.put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("url", t.url)
                    put("thumbnailUrl", t.thumbnailUrl)
                    put("durationSeconds", t.durationSeconds)
                })
            }

            val profObj = JSONObject().apply {
                val scoresObj = JSONObject()
                scores.forEach { (k, v) -> scoresObj.put(k, v) }
                put("scores", scoresObj)

                val artArr = JSONArray()
                topArtists.forEach { artArr.put(it) }
                put("top_artists", artArr)
            }

            val outJson = rankCandidates(candArr.toString(), profObj.toString())
            val resArr = JSONArray(outJson)
            val ranked = mutableListOf<Track>()
            val candMap = candidates.associateBy { it.id }

            for (i in 0 until resArr.length()) {
                val obj = resArr.getJSONObject(i)
                val id = obj.optString("id")
                val orig = candMap[id]
                if (orig != null) {
                    ranked.add(orig)
                }
            }
            if (ranked.isNotEmpty()) ranked else candidates
        } catch (e: Throwable) {
            Log.e(TAG, "Native rank failed, using fallback: ${e.message}")
            fallbackRankRadio(candidates, scores, topArtists)
        }
    }

    fun parseAutomix(rawJson: String, currentVideoId: String): List<Track>? {
        if (!isLoaded) return null
        return try {
            val out = parseAutomixTracks(rawJson, currentVideoId)
            val arr = JSONArray(out)
            if (arr.length() == 0) return null

            val list = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val vid = obj.optString("id")
                if (vid.isBlank()) continue
                list.add(
                    Track(
                        id = vid,
                        url = obj.optString("url", "https://www.youtube.com/watch?v=$vid"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        thumbnailUrl = obj.optString("thumbnailUrl"),
                        durationSeconds = obj.optLong("durationSeconds", 0L)
                    )
                )
            }
            if (list.isNotEmpty()) list else null
        } catch (e: Throwable) {
            Log.e(TAG, "Native parseAutomix failed: ${e.message}")
            null
        }
    }

    fun buildDeepLRequestPayload(lines: List<String>, targetLang: String): String {
        if (!isLoaded) {
            val arr = JSONArray()
            lines.forEach { arr.put(it) }
            val obj = JSONObject()
            obj.put("text", arr)
            obj.put("target_lang", targetLang)
            return obj.toString()
        }
        return try {
            val linesJson = JSONArray(lines).toString()
            buildDeepLPayload(linesJson, targetLang)
        } catch (e: Throwable) {
            val arr = JSONArray()
            lines.forEach { arr.put(it) }
            val obj = JSONObject()
            obj.put("text", arr)
            obj.put("target_lang", targetLang)
            obj.toString()
        }
    }

    fun parseDeepLResponseTranslations(responseJson: String): List<String> {
        if (!isLoaded) {
            return fallbackParseDeepL(responseJson)
        }
        return try {
            val parsed = parseDeepLTranslations(responseJson)
            val arr = JSONArray(parsed)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Throwable) {
            fallbackParseDeepL(responseJson)
        }
    }

    private fun fallbackParseDeepL(responseJson: String): List<String> {
        return try {
            val root = JSONObject(responseJson)
            val transArr = root.optJSONArray("translations") ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until transArr.length()) {
                val item = transArr.optJSONObject(i)
                list.add(item?.optString("text").orEmpty())
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fallbackCleanTitle(raw: String): String {
        return raw
            .replace(CLEAN_NOISE_REGEX, "")
            .replace(CLEAN_PIPE_REGEX, "")
            .trim()
    }

    private fun fallbackRankRadio(
        candidates: List<Track>,
        scores: Map<String, Int>,
        topArtists: List<String>
    ): List<Track> {
        val topSet = topArtists.map { it.lowercase() }.toSet()
        return candidates.sortedByDescending { track ->
            var s = scores[track.id] ?: 0
            if (s < 0) s = 0
            if (track.artist.lowercase() in topSet) s += 5
            s
        }
    }

    private fun isJunkLyricLine(text: String): Boolean {
        return LrcParser.isJunkOrCreditLine(text)
    }

    fun parseLiriqo(
        rawJson: String,
        durationMs: Long,
        trackId: String,
        title: String,
        artist: String
    ): com.ayashii.kuro.data.lyrics.LyricsData? {
        if (rawJson.isBlank()) return null
        val jsonResult = if (isLoaded) {
            try {
                parseLiriqoLyrics(rawJson, durationMs)
            } catch (_: Throwable) {
                fallbackParseLiriqo(rawJson, durationMs)
            }
        } else {
            fallbackParseLiriqo(rawJson, durationMs)
        }

        if (jsonResult.isBlank()) return null
        return try {
            val root = JSONObject(jsonResult)
            val isSynced = root.optBoolean("is_synced", true)
            val linesArr = root.optJSONArray("lines") ?: return null
            val rawLines = mutableListOf<com.ayashii.kuro.data.lyrics.LyricLine>()
            for (i in 0 until linesArr.length()) {
                val obj = linesArr.getJSONObject(i)
                val timeMs = obj.getLong("time_ms")
                val text = obj.getString("text")
                val endMs = if (obj.has("end_time_ms") && !obj.isNull("end_time_ms")) obj.getLong("end_time_ms") else null
                rawLines.add(com.ayashii.kuro.data.lyrics.LyricLine(timeMs, text, endTimeMs = endMs))
            }
            val lines = LrcParser.sanitizeLines(rawLines)
            if (lines.isEmpty()) return null
            com.ayashii.kuro.data.lyrics.LyricsData(
                trackId = trackId,
                title = title,
                artist = artist,
                lines = lines,
                isSynced = isSynced
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackParseLiriqo(rawJson: String, durationMs: Long): String {
        return try {
            val root = JSONObject(rawJson)
            val tracks = root.optJSONArray("tracks") ?: return ""
            if (tracks.length() == 0) return ""

            val ranks = listOf(
                "apple_music_liriqo",
                "musixmatch",
                "unison_community",
                "netease",
                "kugou_legato",
                "qq_portato",
                "lrclib",
                "ytm_line"
            )

            var bestProvider = "liriqo"
            var bestSynced = true
            var bestScore = -1
            var bestItems: List<JSONObject> = emptyList()

            for (i in 0 until tracks.length()) {
                val t = tracks.optJSONObject(i) ?: continue
                val provider = t.optString("provider", "liriqo")
                val pRank = ranks.indexOf(provider).let { if (it >= 0) it else 99 }
                val syncLevel = t.optString("syncLevel", "")
                val isWordOrSyllable = syncLevel == "syllable" || syncLevel == "word"

                val timed = t.optJSONArray("timed")
                if (timed != null && timed.length() > 0) {
                    val items = mutableListOf<JSONObject>()
                    for (j in 0 until timed.length()) {
                        val line = timed.optJSONObject(j) ?: continue
                        if (line.optBoolean("isInstrumental", false) || line.optBoolean("isCue", false)) continue
                        val rawText = line.optString("text", "").trim()
                        val cleanText = LrcParser.sanitizeLyricText(rawText) ?: continue
                        val start = line.optLong("start", -1L)
                        val end = line.optLong("end", -1L)
                        if (start < 0 || end <= start) continue
                        if (durationMs > 0 && start > durationMs + 10000) continue
                        val item = JSONObject()
                        item.put("time_ms", start)
                        item.put("text", cleanText)
                        item.put("end_time_ms", end)
                        items.add(item)
                    }
                    if (items.isNotEmpty()) {
                        items.sortBy { it.optLong("time_ms", 0L) }
                        val score = if (isWordOrSyllable) 1000 - pRank else 500 - pRank
                        if (score > bestScore) {
                            bestScore = score
                            bestProvider = provider
                            bestSynced = true
                            bestItems = items
                        }
                        continue
                    }
                }

                val lrc = t.optString("lrc", "")
                if (lrc.isNotBlank()) {
                    val items = mutableListOf<JSONObject>()
                    for (rawLine in lrc.lines()) {
                        val trimmed = rawLine.trim()
                        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                            val unwrapped = LrcParser.unwrapNetEaseJsonText(trimmed)
                            val jsonTime = unwrapped?.first
                            val cleanText = unwrapped?.second?.let { LrcParser.sanitizeLyricText(it) }
                            if (jsonTime != null && !cleanText.isNullOrBlank() && (durationMs <= 0 || jsonTime <= durationMs + 10000)) {
                                val item = JSONObject()
                                item.put("time_ms", jsonTime)
                                item.put("text", cleanText)
                                items.add(item)
                            }
                            continue
                        }
                        val match = LRC_TAG_REGEX.find(trimmed) ?: continue
                        val min = match.groupValues[1].toLongOrNull() ?: continue
                        val secFloat = match.groupValues[2].toDoubleOrNull() ?: continue
                        val timeMs = (min * 60_000L + (secFloat * 1000L).toLong())
                        val rawText = trimmed.substring(match.range.last + 1).trim()
                        val cleanText = LrcParser.sanitizeLyricText(rawText) ?: continue
                        if (durationMs > 0 && timeMs > durationMs + 10000) continue
                        val item = JSONObject()
                        item.put("time_ms", timeMs)
                        item.put("text", cleanText)
                        items.add(item)
                    }
                    if (items.isNotEmpty()) {
                        items.sortBy { it.optLong("time_ms", 0L) }
                        val score = 300 - pRank
                        if (score > bestScore) {
                            bestScore = score
                            bestProvider = provider
                            bestSynced = true
                            bestItems = items
                        }
                        continue
                    }
                }

                val plain = t.optString("plain", "")
                if (plain.isNotBlank()) {
                    val items = mutableListOf<JSONObject>()
                    for (line in plain.lines()) {
                        val cleanText = LrcParser.sanitizeLyricText(line) ?: continue
                        val item = JSONObject()
                        item.put("time_ms", 0L)
                        item.put("text", cleanText)
                        items.add(item)
                    }
                    if (items.isNotEmpty()) {
                        val score = 100 - pRank
                        if (score > bestScore) {
                            bestScore = score
                            bestProvider = provider
                            bestSynced = false
                            bestItems = items
                        }
                    }
                }
            }

            if (bestItems.isEmpty()) return ""
            val resultObj = JSONObject()
            resultObj.put("provider", bestProvider)
            resultObj.put("is_synced", bestSynced)
            val linesArr = JSONArray()
            bestItems.forEach { linesArr.put(it) }
            resultObj.put("lines", linesArr)
            resultObj.toString()
        } catch (_: Exception) {
            ""
        }
    }
}
