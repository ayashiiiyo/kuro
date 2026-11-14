package com.ayashii.kuro.data.cache

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

class HistoryStore private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed.asStateFlow()

    private val _searchRecents = MutableStateFlow<List<Track>>(emptyList())
    val searchRecents: StateFlow<List<Track>> = _searchRecents.asStateFlow()

    private var lastRecordedId: String? = null
    private var lastRecordedAt: Long = 0L

    init {
        load()
    }

    @Synchronized
    fun recordTrackPlayed(track: Track) {
        val now = System.currentTimeMillis()
        if (track.id == lastRecordedId && (now - lastRecordedAt) < 20_000L) return
        lastRecordedId = track.id
        lastRecordedAt = now

        val nextRecent = (_recentlyPlayed.value.filterNot { it.id == track.id }).toMutableList().apply {
            add(0, track)
        }.take(MAX_ITEMS)
        _recentlyPlayed.value = nextRecent
        saveListAsync(KEY_RECENTS, nextRecent)

        val nextSearch = (_searchRecents.value.filterNot { it.id == track.id }).toMutableList().apply {
            add(0, track)
        }.take(MAX_ITEMS)
        _searchRecents.value = nextSearch
        saveListAsync(KEY_SEARCH_RECENTS, nextSearch)
    }

    fun removeFromRecents(trackId: String) {
        val updated = _searchRecents.value.filterNot { it.id == trackId }
        _searchRecents.value = updated
        saveListAsync(KEY_SEARCH_RECENTS, updated)
    }

    fun clearRecents() {
        _searchRecents.value = emptyList()
        scope.launch {
            runCatching { prefs.edit().putString(KEY_SEARCH_RECENTS, "[]").apply() }
        }
    }

    private fun saveListAsync(key: String, tracks: List<Track>) {
        val snapshot = ArrayList(tracks)
        scope.launch {
            runCatching {
                val arr = JSONArray()
                for (t in snapshot) {
                    arr.put(JSONObject().apply {
                        put("id", t.id)
                        put("url", t.url)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("thumbnailUrl", t.thumbnailUrl)
                        put("durationSeconds", t.durationSeconds)
                    })
                }
                prefs.edit().putString(key, arr.toString()).apply()
            }
        }
    }

    private fun load() {
        runCatching {
            val recents = parseTracks(prefs.getString(KEY_RECENTS, "[]") ?: "[]")
            _recentlyPlayed.value = recents
            val searchRaw = prefs.getString(KEY_SEARCH_RECENTS, null)
            _searchRecents.value = if (searchRaw != null) parseTracks(searchRaw) else recents
        }
    }

    private fun parseTracks(raw: String): List<Track> {
        val arr = JSONArray(raw)
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            out.add(
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
        return out
    }

    companion object {
        private const val PREF_NAME = "kuro_history_store"
        private const val KEY_RECENTS = "recents"
        private const val KEY_SEARCH_RECENTS = "search_recents"
        private const val MAX_ITEMS = 30

        @Volatile
        private var INSTANCE: HistoryStore? = null

        fun getInstance(context: Context): HistoryStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HistoryStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
