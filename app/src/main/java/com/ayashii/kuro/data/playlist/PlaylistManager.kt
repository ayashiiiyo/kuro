package com.ayashii.kuro.data.playlist

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
import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<Track> = emptyList(),
    val remoteId: String? = null
)

class PlaylistManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val ioScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _watchLaterTracks = MutableStateFlow<List<Track>>(emptyList())
    val watchLaterTracks: StateFlow<List<Track>> = _watchLaterTracks.asStateFlow()

    init {
        ioScope.launch {
            loadPlaylists()
            loadWatchLater()
        }
    }

    @Synchronized
    private fun loadPlaylists() {
        val raw = prefs.getString(KEY_PLAYLISTS, "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Playlist>()
            for (i in 0 until arr.length()) {
                val pObj = arr.optJSONObject(i) ?: continue
                val id = pObj.optString("id", UUID.randomUUID().toString())
                val name = pObj.optString("name", "Playlist")
                val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())
                val remoteId = pObj.optString("remoteId", "").takeIf { it.isNotBlank() }

                val tracksArr = pObj.optJSONArray("tracks") ?: JSONArray()
                val tracks = mutableListOf<Track>()
                for (j in 0 until tracksArr.length()) {
                    val tObj = tracksArr.optJSONObject(j) ?: continue
                    tracks.add(
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
                list.add(Playlist(id = id, name = name, createdAt = createdAt, tracks = tracks, remoteId = remoteId))
            }
            if (!prefs.contains(KEY_WATCH_LATER)) {
                val legacyWatchLater = list.firstOrNull { it.name == "Putar Nanti" && it.remoteId == null }
                if (legacyWatchLater != null) {
                    _watchLaterTracks.value = legacyWatchLater.tracks
                    saveWatchLater(legacyWatchLater.tracks)
                    val migratedList = list.filter { it.id != legacyWatchLater.id }
                    _playlists.value = migratedList
                    savePlaylists(migratedList)
                    return
                }
            }
            _playlists.value = list
        } catch (_: Exception) {
            _playlists.value = emptyList()
        }
    }

    private fun savePlaylists(list: List<Playlist>) {
        _playlists.value = list
        val snapshot = ArrayList(list)
        ioScope.launch {
            try {
                val arr = JSONArray()
                for (p in snapshot) {
                    val pObj = JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("createdAt", p.createdAt)
                        if (!p.remoteId.isNullOrBlank()) {
                            put("remoteId", p.remoteId)
                        }
                        val tArr = JSONArray()
                        for (t in p.tracks) {
                            val tObj = JSONObject().apply {
                                put("id", t.id)
                                put("url", t.url)
                                put("title", t.title)
                                put("artist", t.artist)
                                put("thumbnailUrl", t.thumbnailUrl)
                                put("durationSeconds", t.durationSeconds)
                            }
                            tArr.put(tObj)
                        }
                        put("tracks", tArr)
                    }
                    arr.put(pObj)
                }
                prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun loadWatchLater() {
        val raw = prefs.getString(KEY_WATCH_LATER, null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val tObj = arr.optJSONObject(i) ?: continue
                list.add(
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
            _watchLaterTracks.value = list
        } catch (_: Exception) {
            _watchLaterTracks.value = emptyList()
        }
    }

    private fun saveWatchLater(list: List<Track>) {
        _watchLaterTracks.value = list
        val snapshot = ArrayList(list)
        ioScope.launch {
            try {
                val arr = JSONArray()
                for (t in snapshot) {
                    arr.put(
                        JSONObject().apply {
                            put("id", t.id)
                            put("url", t.url)
                            put("title", t.title)
                            put("artist", t.artist)
                            put("thumbnailUrl", t.thumbnailUrl)
                            put("durationSeconds", t.durationSeconds)
                        }
                    )
                }
                prefs.edit().putString(KEY_WATCH_LATER, arr.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun isTrackInWatchLater(trackId: String): Boolean {
        return _watchLaterTracks.value.any { it.id == trackId }
    }

    @Synchronized
    fun toggleWatchLater(track: Track): Boolean {
        val current = _watchLaterTracks.value.toMutableList()
        val exists = current.any { it.id == track.id }
        return if (exists) {
            current.removeAll { it.id == track.id }
            saveWatchLater(current)
            false
        } else {
            current.add(0, track)
            saveWatchLater(current)
            true
        }
    }

    @Synchronized
    fun removeFromWatchLater(trackId: String) {
        val current = _watchLaterTracks.value.filter { it.id != trackId }
        saveWatchLater(current)
    }

    @Synchronized
    fun upsertSyncedPlaylists(remotePlaylists: List<Triple<String, String, List<Track>>>) {
        if (remotePlaylists.isEmpty()) return
        val current = _playlists.value.toMutableList()
        for ((remoteId, title, tracks) in remotePlaylists) {
            val existingIdx = current.indexOfFirst { it.remoteId == remoteId }
            if (existingIdx != -1) {
                val existing = current[existingIdx]
                current[existingIdx] = existing.copy(
                    name = title.ifBlank { existing.name },
                    tracks = tracks
                )
            } else {
                current.add(
                    Playlist(
                        id = UUID.randomUUID().toString(),
                        name = title.ifBlank { "YouTube Music Playlist" },
                        createdAt = System.currentTimeMillis(),
                        tracks = tracks,
                        remoteId = remoteId
                    )
                )
            }
        }
        savePlaylists(current)
    }

    @Synchronized
    fun createPlaylist(name: String): Playlist {
        val trimmed = name.trim().ifBlank { "Playlist Baru" }
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            tracks = emptyList()
        )
        val current = _playlists.value.toMutableList()
        current.add(0, newPlaylist)
        savePlaylists(current)
        return newPlaylist
    }

    @Synchronized
    fun deletePlaylist(playlistId: String) {
        val current = _playlists.value.filter { it.id != playlistId }
        savePlaylists(current)
    }

    @Synchronized
    fun renamePlaylist(playlistId: String, newName: String) {
        val trimmed = newName.trim().ifBlank { "Playlist" }
        val current = _playlists.value.map {
            if (it.id == playlistId) it.copy(name = trimmed) else it
        }
        savePlaylists(current)
    }

    @Synchronized
    fun addTrackToPlaylist(playlistId: String, track: Track): Boolean {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return false

        val p = current[index]
        if (p.tracks.any { it.id == track.id }) return false

        val newTracks = p.tracks.toMutableList().apply { add(track) }
        current[index] = p.copy(tracks = newTracks)
        savePlaylists(current)
        return true
    }

    @Synchronized
    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return

        val p = current[index]
        val newTracks = p.tracks.filter { it.id != trackId }
        current[index] = p.copy(tracks = newTracks)
        savePlaylists(current)
    }

    companion object {
        private const val PREF_NAME = "kuro_playlists"
        private const val KEY_PLAYLISTS = "playlist_data"
        private const val KEY_WATCH_LATER = "watch_later_tracks"

        @Volatile
        private var INSTANCE: PlaylistManager? = null

        fun getInstance(context: Context): PlaylistManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaylistManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
