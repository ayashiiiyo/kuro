package com.ayashii.kuro.data.recommendation

import android.content.Context
import android.content.SharedPreferences
import com.ayashii.kuro.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class TasteManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed.asStateFlow()

    private val _onRepeatTracks = MutableStateFlow<List<Track>>(emptyList())
    val onRepeatTracks: StateFlow<List<Track>> = _onRepeatTracks.asStateFlow()

    // In-memory caches to eliminate repeated JSON serialization/deserialization
    private val scoresMap = ConcurrentHashMap<String, Int>()
    private val playsMap = ConcurrentHashMap<String, Int>()
    private val trackDataMap = ConcurrentHashMap<String, Track>()

    init {
        loadDataFromPrefs()
    }

    @Synchronized
    fun recordTrackPlayed(track: Track) {
        updateTrackScore(track, deltaScore = 3, isPlay = true)
        addToRecents(track)
        refreshOnRepeat()
    }

    @Synchronized
    fun recordTrackLiked(track: Track) {
        updateTrackScore(track, deltaScore = 5, isPlay = false)
        refreshOnRepeat()
    }

    @Synchronized
    fun recordTrackRepeated(track: Track) {
        updateTrackScore(track, deltaScore = 4, isPlay = true)
        refreshOnRepeat()
    }

    @Synchronized
    fun recordTrackSkipped(track: Track) {
        updateTrackScore(track, deltaScore = -2, isPlay = false)
        refreshOnRepeat()
    }

    private fun updateTrackScore(track: Track, deltaScore: Int, isPlay: Boolean) {
        val currentScore = scoresMap[track.id] ?: 0
        scoresMap[track.id] = maxOf(0, currentScore + deltaScore)

        if (isPlay) {
            val currentPlays = playsMap[track.id] ?: 0
            playsMap[track.id] = currentPlays + 1
        }

        trackDataMap[track.id] = track

        // Prune entries if exceeding max limit to prevent unbounded memory/disk growth
        if (trackDataMap.size > MAX_TRACK_ENTRIES) {
            val sortedByScore = scoresMap.entries.sortedBy { it.value }
            val toRemoveCount = trackDataMap.size - MAX_TRACK_ENTRIES
            for (i in 0 until toRemoveCount) {
                val entry = sortedByScore.getOrNull(i) ?: break
                scoresMap.remove(entry.key)
                playsMap.remove(entry.key)
                trackDataMap.remove(entry.key)
            }
        }

        persistTasteDataAsync()
    }

    private fun addToRecents(track: Track) {
        val current = _recentlyPlayed.value.toMutableList()
        current.removeAll { it.id == track.id }
        current.add(0, track)

        val trimmed = if (current.size > MAX_RECENTS) current.take(MAX_RECENTS) else current
        _recentlyPlayed.value = trimmed

        // Persist recents
        val arr = JSONArray()
        for (t in trimmed) {
            val obj = JSONObject().apply {
                put("id", t.id)
                put("url", t.url)
                put("title", t.title)
                put("artist", t.artist)
                put("thumbnailUrl", t.thumbnailUrl)
                put("durationSeconds", t.durationSeconds)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    private fun refreshOnRepeat() {
        val sorted = scoresMap.entries
            .filter { it.value > 3 }
            .sortedByDescending { it.value }
            .take(15)

        val list = sorted.mapNotNull { trackDataMap[it.key] }
        _onRepeatTracks.value = list
    }

    private fun persistTasteDataAsync() {
        val scoresJson = JSONObject()
        scoresMap.forEach { (k, v) -> scoresJson.put(k, v) }

        val playsJson = JSONObject()
        playsMap.forEach { (k, v) -> playsJson.put(k, v) }

        val trackDataJson = JSONObject()
        trackDataMap.forEach { (k, t) ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("url", t.url)
                put("title", t.title)
                put("artist", t.artist)
                put("thumbnailUrl", t.thumbnailUrl)
                put("durationSeconds", t.durationSeconds)
            }
            trackDataJson.put(k, obj)
        }

        prefs.edit()
            .putString(KEY_SCORES, scoresJson.toString())
            .putString(KEY_PLAYS, playsJson.toString())
            .putString(KEY_TRACK_DATA, trackDataJson.toString())
            .apply()
    }

    private fun loadDataFromPrefs() {
        try {
            // 1. Load recents
            val recentsRaw = prefs.getString(KEY_RECENTS, "[]") ?: "[]"
            val recentsArr = JSONArray(recentsRaw)
            val rList = mutableListOf<Track>()
            for (i in 0 until recentsArr.length()) {
                val obj = recentsArr.getJSONObject(i)
                rList.add(
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
            _recentlyPlayed.value = rList

            // 2. Load taste maps
            val scoresJson = JSONObject(prefs.getString(KEY_SCORES, "{}") ?: "{}")
            val playsJson = JSONObject(prefs.getString(KEY_PLAYS, "{}") ?: "{}")
            val trackDataJson = JSONObject(prefs.getString(KEY_TRACK_DATA, "{}") ?: "{}")

            val sKeys = scoresJson.keys()
            while (sKeys.hasNext()) {
                val k = sKeys.next()
                scoresMap[k] = scoresJson.optInt(k, 0)
            }

            val pKeys = playsJson.keys()
            while (pKeys.hasNext()) {
                val k = pKeys.next()
                playsMap[k] = playsJson.optInt(k, 0)
            }

            val tKeys = trackDataJson.keys()
            while (tKeys.hasNext()) {
                val k = tKeys.next()
                val obj = trackDataJson.optJSONObject(k)
                if (obj != null) {
                    trackDataMap[k] = Track(
                        id = obj.optString("id"),
                        url = obj.optString("url"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        thumbnailUrl = obj.optString("thumbnailUrl"),
                        durationSeconds = obj.optLong("durationSeconds", 0L)
                    )
                }
            }

            refreshOnRepeat()
        } catch (_: Exception) {}
    }

    fun getTotalPlays(): Int {
        var total = 0
        playsMap.values.forEach { total += it }
        return total
    }

    fun getTopArtists(limit: Int = 5): List<Pair<String, Int>> {
        val artistCounts = mutableMapOf<String, Int>()
        trackDataMap.forEach { (id, track) ->
            val count = playsMap[id] ?: 0
            val artist = track.artist.trim()
            if (artist.isNotBlank() && count > 0) {
                artistCounts[artist] = (artistCounts[artist] ?: 0) + count
            }
        }
        return artistCounts.toList().sortedByDescending { it.second }.take(limit)
    }

    fun getUniqueArtistsCount(): Int {
        val artists = mutableSetOf<String>()
        trackDataMap.values.forEach {
            val a = it.artist.trim()
            if (a.isNotBlank()) artists.add(a)
        }
        return artists.size
    }

    fun getTopTracks(limit: Int = 5): List<Pair<Track, Int>> {
        val list = mutableListOf<Pair<Track, Int>>()
        trackDataMap.forEach { (id, track) ->
            val count = playsMap[id] ?: 0
            if (count > 0) {
                list.add(Pair(track, count))
            }
        }
        return list.sortedByDescending { it.second }.take(limit)
    }

    companion object {
        private const val PREF_NAME = "kuro_taste_profile"
        private const val KEY_SCORES = "scores"
        private const val KEY_PLAYS = "plays"
        private const val KEY_TRACK_DATA = "track_data"
        private const val KEY_RECENTS = "recents"
        private const val MAX_RECENTS = 30
        private const val MAX_TRACK_ENTRIES = 100

        @Volatile
        private var INSTANCE: TasteManager? = null

        fun getInstance(context: Context): TasteManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TasteManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
