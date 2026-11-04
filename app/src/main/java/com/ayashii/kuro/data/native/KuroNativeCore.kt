package com.ayashii.kuro.data.native

import android.util.Log
import com.ayashii.kuro.data.model.Track
import org.json.JSONArray
import org.json.JSONObject

object KuroNativeCore {

    private const val TAG = "KuroNativeCore"

    val isLoaded: Boolean = try {
        System.loadLibrary("kuro_core")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "Native kuro_core library not available, using Kotlin fallback: ${e.message}")
        false
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load kuro_core library: ${e.message}")
        false
    }

    private external fun cleanTrackTitle(rawTitle: String): String
    private external fun rankCandidates(candidatesJson: String, profileJson: String): String
    private external fun parseAutomixTracks(rawJson: String, currentVideoId: String): String
    private external fun buildDeepLPayload(linesJson: String, targetLang: String): String
    private external fun parseDeepLTranslations(responseJson: String): String

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
            .replace(Regex("""(?i)\s*[\(\[](?:official\s*(?:music\s*)?video|official\s*audio|music\s*video|lyric\s*video|lyrics|audio|visualizer|remastered|remaster|4k|hd|hq|mv|clip\s*officiel|video\s*oficial)[\)\]]"""), "")
            .replace(Regex("""(?i)\s*\|\s*(?:official\s*(?:music\s*)?video|official\s*audio|visualizer).*"""), "")
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
}
