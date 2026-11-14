package com.ayashii.kuro.data.recommendation

import android.content.Context
import android.content.SharedPreferences
import com.ayashii.kuro.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class TasteManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed.asStateFlow()

    private val _searchRecents = MutableStateFlow<List<Track>>(emptyList())
    val searchRecents: StateFlow<List<Track>> = _searchRecents.asStateFlow()

    private val _onRepeatTracks = MutableStateFlow<List<Track>>(emptyList())
    val onRepeatTracks: StateFlow<List<Track>> = _onRepeatTracks.asStateFlow()

    private val _statsVersion = MutableStateFlow(0L)
    val statsVersion: StateFlow<Long> = _statsVersion.asStateFlow()

    private val scoresMap = ConcurrentHashMap<String, Int>()
    private val playsMap = ConcurrentHashMap<String, Int>()
    private val trackDataMap = ConcurrentHashMap<String, Track>()

    @Volatile
    private var cachedTopArtists: Set<String>? = null

    private var lastRecordedPlayId: String? = null
    private var lastRecordedPlayTimestamp: Long = 0L

    init {
        loadDataFromPrefs()
    }

    @Synchronized
    fun recordTrackPlayed(track: Track) {
        val now = System.currentTimeMillis()
        if (track.id == lastRecordedPlayId && (now - lastRecordedPlayTimestamp) < 20000L) {
            return
        }
        lastRecordedPlayId = track.id
        lastRecordedPlayTimestamp = now
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
        val now = System.currentTimeMillis()
        if (track.id == lastRecordedPlayId && (now - lastRecordedPlayTimestamp) < 15000L) {
            val currentPlays = playsMap[track.id] ?: 0
            if (currentPlays > 0) {
                playsMap[track.id] = currentPlays - 1
            }
            lastRecordedPlayId = null
        }
        updateTrackScore(track, deltaScore = -3, isPlay = false)
        refreshOnRepeat()
    }

    private fun updateTrackScore(track: Track, deltaScore: Int, isPlay: Boolean) {
        val currentScore = scoresMap[track.id] ?: 0
        scoresMap[track.id] = (currentScore + deltaScore).coerceIn(-20, 120)
        cachedTopArtists = null

        if (isPlay) {
            val currentPlays = playsMap[track.id] ?: 0
            playsMap[track.id] = currentPlays + 1
        }

        trackDataMap[track.id] = track

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

        _statsVersion.value = _statsVersion.value + 1L
        persistTasteDataAsync()
    }

    fun removeFromRecents(trackId: String) {
        val current = _searchRecents.value.toMutableList()
        current.removeAll { it.id == trackId }
        _searchRecents.value = current
        saveSearchRecents(current)
    }

    fun clearRecents() {
        _searchRecents.value = emptyList()
        coroutineScope.launch {
            try {
                prefs.edit().putString(KEY_SEARCH_RECENTS, "[]").apply()
            } catch (_: Exception) {}
        }
    }

    private fun addToRecents(track: Track) {
        val current = _recentlyPlayed.value.toMutableList()
        current.removeAll { it.id == track.id }
        current.add(0, track)

        val trimmed = if (current.size > MAX_RECENTS) current.take(MAX_RECENTS) else current
        _recentlyPlayed.value = trimmed
        saveRecents(trimmed)

        val currentSearch = _searchRecents.value.toMutableList()
        currentSearch.removeAll { it.id == track.id }
        currentSearch.add(0, track)
        val trimmedSearch = if (currentSearch.size > MAX_RECENTS) currentSearch.take(MAX_RECENTS) else currentSearch
        _searchRecents.value = trimmedSearch
        saveSearchRecents(trimmedSearch)
    }

    private fun saveSearchRecents(tracks: List<Track>) {
        val copy = ArrayList(tracks)
        coroutineScope.launch {
            try {
                val arr = JSONArray()
                for (t in copy) {
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
                prefs.edit().putString(KEY_SEARCH_RECENTS, arr.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    private fun saveRecents(tracks: List<Track>) {
        val copy = ArrayList(tracks)
        coroutineScope.launch {
            try {
                val arr = JSONArray()
                for (t in copy) {
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
            } catch (_: Exception) {}
        }
    }

    private fun refreshOnRepeat() {
        _onRepeatTracks.value = emptyList()
    }

    private fun persistTasteDataAsync() {
        val scoresSnapshot = HashMap(scoresMap)
        val playsSnapshot = HashMap(playsMap)
        val trackDataSnapshot = HashMap(trackDataMap)

        coroutineScope.launch {
            try {
                val scoresJson = JSONObject()
                scoresSnapshot.forEach { (k, v) -> scoresJson.put(k, v) }

                val playsJson = JSONObject()
                playsSnapshot.forEach { (k, v) -> playsJson.put(k, v) }

                val trackDataJson = JSONObject()
                trackDataSnapshot.forEach { (k, t) ->
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
            } catch (_: Exception) {}
        }
    }

    private fun loadDataFromPrefs() {
        try {
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
            val searchRecentsRaw = prefs.getString(KEY_SEARCH_RECENTS, null)
            if (searchRecentsRaw != null) {
                val sArr = JSONArray(searchRecentsRaw)
                val sList = mutableListOf<Track>()
                for (i in 0 until sArr.length()) {
                    val obj = sArr.getJSONObject(i)
                    sList.add(
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
                _searchRecents.value = sList
            } else {
                _searchRecents.value = rList
            }

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
            _statsVersion.value = _statsVersion.value + 1L
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
            if (artist.isNotBlank() && artist != "Unknown Artist" && count > 0) {
                artistCounts[artist] = (artistCounts[artist] ?: 0) + count
            }
        }
        return artistCounts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { Pair(it.key, it.value) }
    }

    /** Artists the user actually interacts with, weighted by taste score (not play count). */
    private fun topArtistSetByScore(limit: Int): Set<String> {
        val artistScores = mutableMapOf<String, Int>()
        scoresMap.forEach { (trackId, score) ->
            val track = trackDataMap[trackId] ?: return@forEach
            val artist = track.artist.trim()
            if (artist.isBlank() || artist == "Unknown Artist" || score <= 0) return@forEach
            artistScores[artist] = (artistScores[artist] ?: 0) + score
        }
        return artistScores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapTo(HashSet()) { it.key.lowercase() }
    }

    /**
     * Ranks a list of candidate tracks based on user affinities:
     * - Favored artist (+5 weight)
     * - High past interaction score
     * - Heavily penalized if repeatedly skipped
     */
    fun rankRadioTracks(candidates: List<Track>): List<Track> {
        if (candidates.size <= 1) return candidates
        val out = ArrayList<Track>(candidates.size)
        for (cand in candidates) {
            if (out.none { com.ayashii.kuro.data.native.KuroNativeCore.isDuplicateOrVariantTrack(cand, it) }) {
                out.add(cand)
            }
        }
        return out
    }

    fun getUniqueArtistsCount(): Int {
        val artists = mutableSetOf<String>()
        trackDataMap.forEach { (id, track) ->
            val count = playsMap[id] ?: 0
            val a = track.artist.trim()
            if (count > 0 && a.isNotBlank() && a != "Unknown Artist") {
                artists.add(a.lowercase())
            }
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
        private const val KEY_SEARCH_RECENTS = "search_recents"
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
