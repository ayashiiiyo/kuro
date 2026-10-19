package com.ayashii.kuro.data.recommendation

import android.content.Context
import android.content.SharedPreferences
import com.ayashii.kuro.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class TasteManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed.asStateFlow()

    private val _onRepeatTracks = MutableStateFlow<List<Track>>(emptyList())
    val onRepeatTracks: StateFlow<List<Track>> = _onRepeatTracks.asStateFlow()

    init {
        loadData()
    }

    @Synchronized
    fun recordTrackPlayed(track: Track) {
        // +3 points for normal listening
        updateTrackScore(track, deltaScore = 3, isPlay = true)
        addToRecents(track)
        loadData()
    }

    @Synchronized
    fun recordTrackLiked(track: Track) {
        // +5 points for like
        updateTrackScore(track, deltaScore = 5, isPlay = false)
        loadData()
    }

    @Synchronized
    fun recordTrackRepeated(track: Track) {
        // +4 points for repeat loop
        updateTrackScore(track, deltaScore = 4, isPlay = true)
        loadData()
    }

    @Synchronized
    fun recordTrackSkipped(track: Track) {
        // -2 points for fast skip (<15s)
        updateTrackScore(track, deltaScore = -2, isPlay = false)
        loadData()
    }

    private fun updateTrackScore(track: Track, deltaScore: Int, isPlay: Boolean) {
        val scoresRaw = prefs.getString(KEY_SCORES, "{}") ?: "{}"
        val playsRaw = prefs.getString(KEY_PLAYS, "{}") ?: "{}"
        val trackDataRaw = prefs.getString(KEY_TRACK_DATA, "{}") ?: "{}"

        val scoresJson = JSONObject(scoresRaw)
        val playsJson = JSONObject(playsRaw)
        val trackDataJson = JSONObject(trackDataRaw)

        val currentScore = scoresJson.optInt(track.id, 0)
        scoresJson.put(track.id, maxOf(0, currentScore + deltaScore))

        if (isPlay) {
            val currentPlays = playsJson.optInt(track.id, 0)
            playsJson.put(track.id, currentPlays + 1)
        }

        // Store serialized track json for recommendation retrieval
        val tJson = JSONObject().apply {
            put("id", track.id)
            put("url", track.url)
            put("title", track.title)
            put("artist", track.artist)
            put("thumbnailUrl", track.thumbnailUrl)
            put("durationSeconds", track.durationSeconds)
        }
        trackDataJson.put(track.id, tJson)

        prefs.edit()
            .putString(KEY_SCORES, scoresJson.toString())
            .putString(KEY_PLAYS, playsJson.toString())
            .putString(KEY_TRACK_DATA, trackDataJson.toString())
            .apply()
    }

    private fun addToRecents(track: Track) {
        val recentsRaw = prefs.getString(KEY_RECENTS, "[]") ?: "[]"
        val arr = JSONArray(recentsRaw)
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            list.add(arr.getJSONObject(i))
        }

        // Remove if duplicate id exists
        list.removeAll { it.optString("id") == track.id }

        // Add to front
        val newObj = JSONObject().apply {
            put("id", track.id)
            put("url", track.url)
            put("title", track.title)
            put("artist", track.artist)
            put("thumbnailUrl", track.thumbnailUrl)
            put("durationSeconds", track.durationSeconds)
            put("timestamp", System.currentTimeMillis())
        }
        list.add(0, newObj)

        // Keep maximum 30 recents
        val trimmed = if (list.size > 30) list.take(30) else list
        val newArr = JSONArray()
        trimmed.forEach { newArr.put(it) }

        prefs.edit().putString(KEY_RECENTS, newArr.toString()).apply()
    }

    private fun loadData() {
        // Load recents
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

        // Load On-Repeat based on scores
        val scoresRaw = prefs.getString(KEY_SCORES, "{}") ?: "{}"
        val trackDataRaw = prefs.getString(KEY_TRACK_DATA, "{}") ?: "{}"
        val scoresJson = JSONObject(scoresRaw)
        val trackDataJson = JSONObject(trackDataRaw)

        val idScorePairs = mutableListOf<Pair<String, Int>>()
        val keys = scoresJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val score = scoresJson.optInt(key, 0)
            if (score > 3) {
                idScorePairs.add(Pair(key, score))
            }
        }
        idScorePairs.sortByDescending { it.second }

        val repeatList = mutableListOf<Track>()
        for ((id, _) in idScorePairs.take(15)) {
            val tObj = trackDataJson.optJSONObject(id)
            if (tObj != null) {
                repeatList.add(
                    Track(
                        id = tObj.optString("id"),
                        url = tObj.optString("url"),
                        title = tObj.optString("title"),
                        artist = tObj.optString("artist"),
                        thumbnailUrl = tObj.optString("thumbnailUrl"),
                        durationSeconds = tObj.optLong("durationSeconds", 0L)
                    )
                )
            }
        }
        _onRepeatTracks.value = repeatList
    }

    fun getTotalPlays(): Int {
        val playsRaw = prefs.getString(KEY_PLAYS, "{}") ?: "{}"
        val json = JSONObject(playsRaw)
        var total = 0
        val keys = json.keys()
        while (keys.hasNext()) {
            total += json.optInt(keys.next(), 0)
        }
        return total
    }

    fun getTopArtists(limit: Int = 5): List<Pair<String, Int>> {
        val trackDataRaw = prefs.getString(KEY_TRACK_DATA, "{}") ?: "{}"
        val playsRaw = prefs.getString(KEY_PLAYS, "{}") ?: "{}"
        val tJson = JSONObject(trackDataRaw)
        val pJson = JSONObject(playsRaw)

        val artistCounts = mutableMapOf<String, Int>()
        val keys = tJson.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val trackObj = tJson.optJSONObject(id)
            val artist = trackObj?.optString("artist")?.trim()
            val count = pJson.optInt(id, 0)
            if (!artist.isNullOrBlank() && count > 0) {
                artistCounts[artist] = (artistCounts[artist] ?: 0) + count
            }
        }
        return artistCounts.toList().sortedByDescending { it.second }.take(limit)
    }

    fun getUniqueArtistsCount(): Int {
        val trackDataRaw = prefs.getString(KEY_TRACK_DATA, "{}") ?: "{}"
        val tJson = JSONObject(trackDataRaw)
        val artists = mutableSetOf<String>()
        val keys = tJson.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val trackObj = tJson.optJSONObject(id)
            val artist = trackObj?.optString("artist")?.trim()
            if (!artist.isNullOrBlank()) {
                artists.add(artist)
            }
        }
        return artists.size
    }

    fun getTopTracks(limit: Int = 5): List<Pair<Track, Int>> {
        val trackDataRaw = prefs.getString(KEY_TRACK_DATA, "{}") ?: "{}"
        val playsRaw = prefs.getString(KEY_PLAYS, "{}") ?: "{}"
        val tJson = JSONObject(trackDataRaw)
        val pJson = JSONObject(playsRaw)

        val list = mutableListOf<Pair<Track, Int>>()
        val keys = tJson.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val trackObj = tJson.optJSONObject(id)
            val count = pJson.optInt(id, 0)
            if (trackObj != null && count > 0) {
                val track = Track(
                    id = trackObj.optString("id"),
                    url = trackObj.optString("url"),
                    title = trackObj.optString("title"),
                    artist = trackObj.optString("artist"),
                    thumbnailUrl = trackObj.optString("thumbnailUrl"),
                    durationSeconds = trackObj.optLong("durationSeconds", 0L)
                )
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

        @Volatile
        private var INSTANCE: TasteManager? = null

        fun getInstance(context: Context): TasteManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TasteManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
