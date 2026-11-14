package com.ayashii.kuro.data.cache

import android.content.Context
import com.ayashii.kuro.data.model.HomeSection
import com.ayashii.kuro.data.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class UiSnapshot(
    val lastPage: Int = 0,
    val isPlayerExpanded: Boolean = false,
    val homeSections: List<HomeSection> = emptyList(),
    val trendingTracks: List<Track> = emptyList(),
    val savedAtMs: Long = 0L
)

object UiStateCache {

    private const val SNAPSHOT_FILE = "kuro_ui_snapshot.json"
    private const val MAX_SECTIONS = 4
    private const val MAX_TRACKS_PER_SECTION = 12
    private const val MAX_TRENDING = 15

    fun saveSnapshot(context: Context, snapshot: UiSnapshot) {
        runCatching {
            val root = JSONObject().apply {
                put("lastPage", snapshot.lastPage)
                put("isPlayerExpanded", snapshot.isPlayerExpanded)
                put("savedAtMs", System.currentTimeMillis())

                val secArr = JSONArray()
                for (sec in snapshot.homeSections.take(MAX_SECTIONS)) {
                    secArr.put(JSONObject().apply {
                        put("title", sec.title)
                        put("subtitle", sec.subtitle)
                        put("tracks", tracksToJson(sec.tracks.take(MAX_TRACKS_PER_SECTION)))
                    })
                }
                put("homeSections", secArr)
                put("trendingTracks", tracksToJson(snapshot.trendingTracks.take(MAX_TRENDING)))
            }
            val file = File(context.cacheDir, SNAPSHOT_FILE)
            file.writeText(root.toString())
        }
    }

    fun loadSnapshot(context: Context): UiSnapshot? {
        return runCatching {
            val file = File(context.cacheDir, SNAPSHOT_FILE)
            if (!file.exists()) return null
            val root = JSONObject(file.readText())
            val secArr = root.optJSONArray("homeSections") ?: JSONArray()
            val sections = ArrayList<HomeSection>(secArr.length())
            for (i in 0 until secArr.length()) {
                val obj = secArr.optJSONObject(i) ?: continue
                sections.add(
                    HomeSection(
                        title = obj.optString("title", ""),
                        subtitle = obj.optString("subtitle", ""),
                        tracks = jsonToTracks(obj.optJSONArray("tracks"))
                    )
                )
            }
            val trending = jsonToTracks(root.optJSONArray("trendingTracks"))
            UiSnapshot(
                lastPage = root.optInt("lastPage", 0),
                isPlayerExpanded = root.optBoolean("isPlayerExpanded", false),
                homeSections = sections,
                trendingTracks = trending,
                savedAtMs = root.optLong("savedAtMs", 0L)
            )
        }.getOrNull()
    }

    private fun tracksToJson(tracks: List<Track>): JSONArray {
        val arr = JSONArray()
        for (t in tracks) {
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("url", t.url)
                put("title", t.title)
                put("artist", t.artist)
                put("thumbnailUrl", t.thumbnailUrl)
                put("durationSeconds", t.durationSeconds)
            })
        }
        return arr
    }

    private fun jsonToTracks(arr: JSONArray?): List<Track> {
        if (arr == null) return emptyList()
        val list = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                Track(
                    id = obj.optString("id"),
                    url = obj.optString("url"),
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    thumbnailUrl = obj.optString("thumbnailUrl"),
                    durationSeconds = obj.optLong("durationSeconds", 0L)
                )
            )
        }
        return list
    }
}
