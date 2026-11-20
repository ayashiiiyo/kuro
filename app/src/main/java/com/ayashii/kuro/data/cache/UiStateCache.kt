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
    private const val MAX_SECTIONS = 12
    private const val MAX_TRACKS_PER_SECTION = 20
    private const val MAX_TRENDING = 40

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
                        put("layoutType", sec.layoutType.name)
                        if (!sec.headerAvatarUrl.isNullOrBlank()) put("headerAvatarUrl", sec.headerAvatarUrl)
                        if (!sec.browseId.isNullOrBlank()) put("browseId", sec.browseId)
                        put("tracks", tracksToJson(sec.tracks.take(MAX_TRACKS_PER_SECTION)))
                    })
                }
                put("homeSections", secArr)
                put("trendingTracks", tracksToJson(snapshot.trendingTracks.take(MAX_TRENDING)))
            }
            val file = File(context.cacheDir, SNAPSHOT_FILE)
            val tmp = File(context.cacheDir, "$SNAPSHOT_FILE.tmp")
            synchronized(this) {
                tmp.writeText(root.toString())
                if (!tmp.renameTo(file)) {
                    file.writeText(root.toString())
                    tmp.delete()
                }
            }
        }
    }

    fun loadSnapshot(context: Context): UiSnapshot? {
        return runCatching {
            val file = File(context.cacheDir, SNAPSHOT_FILE)
            if (!file.exists()) return null
            val raw = synchronized(this) { file.readText() }
            val root = JSONObject(raw)
            val secArr = root.optJSONArray("homeSections") ?: JSONArray()
            val sections = ArrayList<HomeSection>(secArr.length())
            for (i in 0 until secArr.length()) {
                val obj = secArr.optJSONObject(i) ?: continue
                val layoutName = obj.optString("layoutType", "")
                val layoutType = runCatching {
                    com.ayashii.kuro.data.model.HomeSectionType.valueOf(layoutName)
                }.getOrDefault(com.ayashii.kuro.data.model.HomeSectionType.SQUARE_CAROUSEL)
                sections.add(
                    HomeSection(
                        title = obj.optString("title", ""),
                        subtitle = obj.optString("subtitle", ""),
                        tracks = jsonToTracks(obj.optJSONArray("tracks")),
                        layoutType = layoutType,
                        headerAvatarUrl = obj.optString("headerAvatarUrl", "").takeIf { it.isNotBlank() },
                        browseId = obj.optString("browseId", "").takeIf { it.isNotBlank() }
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
                if (!t.browseId.isNullOrBlank()) put("browseId", t.browseId)
                if (!t.playlistId.isNullOrBlank()) put("playlistId", t.playlistId)
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
                    durationSeconds = obj.optLong("durationSeconds", 0L),
                    browseId = obj.optString("browseId", "").takeIf { it.isNotBlank() },
                    playlistId = obj.optString("playlistId", "").takeIf { it.isNotBlank() }
                )
            )
        }
        return list
    }
}
