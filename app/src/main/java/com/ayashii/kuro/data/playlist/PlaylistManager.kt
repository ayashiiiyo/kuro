package com.ayashii.kuro.data.playlist

import android.content.Context
import android.content.SharedPreferences
import com.ayashii.kuro.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<Track> = emptyList()
)

class PlaylistManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        val raw = prefs.getString(KEY_PLAYLISTS, "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Playlist>()
            for (i in 0 until arr.length()) {
                val pObj = arr.getJSONObject(i)
                val id = pObj.optString("id", UUID.randomUUID().toString())
                val name = pObj.optString("name", "Playlist")
                val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())

                val tracksArr = pObj.optJSONArray("tracks") ?: JSONArray()
                val tracks = mutableListOf<Track>()
                for (j in 0 until tracksArr.length()) {
                    val tObj = tracksArr.getJSONObject(j)
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
                list.add(Playlist(id = id, name = name, createdAt = createdAt, tracks = tracks))
            }
            _playlists.value = list
        } catch (_: Exception) {
            _playlists.value = emptyList()
        }
    }

    private fun savePlaylists(list: List<Playlist>) {
        _playlists.value = list
        val arr = JSONArray()
        for (p in list) {
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("createdAt", p.createdAt)
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
        // Prevent duplicate track
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

        @Volatile
        private var INSTANCE: PlaylistManager? = null

        fun getInstance(context: Context): PlaylistManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaylistManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
