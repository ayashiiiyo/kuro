package com.ayashii.kuro.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.remote.MusicRepository
import com.ayashii.kuro.service.PlaybackService
import com.ayashii.kuro.widget.persist
import com.ayashii.kuro.widget.updateKuroWidgets
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLoadingTrack = MutableStateFlow(false)
    val isLoadingTrack = _isLoadingTrack.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded = _isPlayerExpanded.asStateFlow()

    private val _homeSections = MutableStateFlow<List<com.ayashii.kuro.data.model.HomeSection>>(emptyList())
    val homeSections = _homeSections.asStateFlow()

    private val _trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingTracks = _trendingTracks.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val favPrefs = getApplication<android.app.Application>().getSharedPreferences("kuro_favorites", android.content.Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<List<Track>>(loadStoredFavorites())
    val favorites = _favorites.asStateFlow()

    private fun loadStoredFavorites(): List<Track> {
        val jsonStr = favPrefs.getString("fav_list", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Track(
                        id = obj.getString("id"),
                        url = obj.getString("url"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        thumbnailUrl = obj.getString("thumbnailUrl"),
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        isFavorite = true
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveStoredFavorites(list: List<Track>) {
        try {
            val arr = org.json.JSONArray()
            list.forEach { t ->
                val obj = org.json.JSONObject().apply {
                    put("id", t.id)
                    put("url", t.url)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("thumbnailUrl", t.thumbnailUrl)
                    put("durationSeconds", t.durationSeconds)
                }
                arr.put(obj)
            }
            favPrefs.edit().putString("fav_list", arr.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("KuroVM", "Save favorites error: ${e.message}")
        }
    }

    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending = _isLoadingTrending.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    val authState = com.ayashii.kuro.data.auth.AuthManager.getInstance().authState

    val settingsManager = com.ayashii.kuro.data.settings.SettingsManager.getInstance()
    val themeMode = settingsManager.themeMode
    val audioQuality = settingsManager.audioQuality

    val audioOutputManager = com.ayashii.kuro.data.audio.AudioOutputManager.getInstance(getApplication())
    val audioDeviceState = audioOutputManager.deviceState
    fun openAudioOutputSettings() = audioOutputManager.openAudioSettings()

    val tasteManager = com.ayashii.kuro.data.recommendation.TasteManager.getInstance(getApplication())
    val recentlyPlayed = tasteManager.recentlyPlayed
    val onRepeatTracks = tasteManager.onRepeatTracks

    val playlistManager = com.ayashii.kuro.data.playlist.PlaylistManager.getInstance(getApplication())
    val playlists = playlistManager.playlists

    fun createPlaylist(name: String) = playlistManager.createPlaylist(name)
    fun deletePlaylist(id: String) = playlistManager.deletePlaylist(id)
    fun renamePlaylist(id: String, name: String) = playlistManager.renamePlaylist(id, name)
    fun addTrackToPlaylist(id: String, track: Track) = playlistManager.addTrackToPlaylist(id, track)
    fun removeTrackFromPlaylist(id: String, trackId: String) = playlistManager.removeTrackFromPlaylist(id, trackId)
    fun toggleWatchLater(track: Track) = playlistManager.toggleWatchLater(track)
    fun isTrackInWatchLater(trackId: String) = playlistManager.isTrackInWatchLater(trackId)

    fun playPlaylist(playlist: com.ayashii.kuro.data.playlist.Playlist, startTrack: Track? = null, shuffle: Boolean = false) {
        if (playlist.tracks.isEmpty()) return
        val list = if (shuffle) playlist.tracks.shuffled() else playlist.tracks
        val initialTrack = startTrack ?: list.first()
        playTrack(initialTrack, list)
    }

    val lyricsRepository = com.ayashii.kuro.data.lyrics.LyricsRepository(getApplication())

    private val _currentLyrics = MutableStateFlow<com.ayashii.kuro.data.lyrics.LyricsData?>(null)
    val currentLyrics = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics = _isLoadingLyrics.asStateFlow()

    private val playlistQueue = java.util.Collections.synchronizedList(mutableListOf<Track>())
    private var currentIndex = -1
    private var progressJob: Job? = null
    private var suggestJob: Job? = null
    private var playJob: Job? = null
    private var lyricsJob: Job? = null
    private var searchJob: Job? = null
    private var pendingTrackToPlay: Track? = null
    private var hasRetriedFallback = false
    private var trackStartTime = 0L

    private val mediaActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                PlaybackService.ACTION_NEXT -> skipNext()
                PlaybackService.ACTION_PREV -> skipPrevious()
                com.ayashii.kuro.widget.WIDGET_FAVORITES_CHANGED -> reloadFavoritesFromPrefs()
            }
        }
    }

    /** Keeps the in-app favorite state in sync when the home-screen widget toggles it. */
    private fun reloadFavoritesFromPrefs() {
        val latest = loadStoredFavorites()
        _favorites.value = latest
        val current = _currentTrack.value
        if (current != null) {
            val favorite = latest.any { it.id == current.id }
            if (current.isFavorite != favorite) _currentTrack.value = current.copy(isFavorite = favorite)
        }
    }

    /**
     * Player state must be read on the main thread, but building RemoteViews and the
     * AppWidgetManager binder call are moved off it.
     */
    private fun syncWidgetState() {
        val controller = mediaController ?: return
        persist(getApplication(), controller)
        viewModelScope.launch(Dispatchers.IO) { updateKuroWidgets(getApplication()) }
    }

    init {
        initializeMediaController()
        loadTrending()
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(PlaybackService.ACTION_NEXT)
                addAction(PlaybackService.ACTION_PREV)
                addAction(com.ayashii.kuro.widget.WIDGET_FAVORITES_CHANGED)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                getApplication(),
                mediaActionReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                val hadPendingTrack = pendingTrackToPlay != null
                pendingTrackToPlay?.let { pending ->
                    pendingTrackToPlay = null
                    playTrack(pending)
                }
                // Reopening the app while the service keeps playing used to show an empty
                // player; mirror the session state into the ViewModel instead.
                if (!hadPendingTrack) syncCurrentTrackFromController()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        syncWidgetState()
                        if (playing) {
                            _isLoadingTrack.value = false
                            startProgressTracking()
                        } else {
                            stopProgressTracking()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        syncWidgetState()
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _isLoadingTrack.value = true
                            }
                            Player.STATE_READY -> {
                                _isLoadingTrack.value = false
                                isSeeking = false
                                mediaController?.let { controller ->
                                    val d = controller.duration
                                    if (d > 0) {
                                        _duration.value = d
                                        val currentLrc = _currentLyrics.value
                                        val currentTrk = _currentTrack.value
                                        if (currentTrk != null && (currentLrc == null || !currentLrc.isSynced)) {
                                            val realDurSec = d / 1000L
                                            if (realDurSec > 0 && realDurSec != currentTrk.durationSeconds) {
                                                lyricsJob?.cancel()
                                                lyricsJob = viewModelScope.launch(Dispatchers.IO) {
                                                    val refreshed = lyricsRepository.getLyrics(
                                                        trackId = currentTrk.id,
                                                        title = currentTrk.title,
                                                        artist = currentTrk.artist,
                                                        durationSeconds = realDurSec
                                                    )
                                                    if (refreshed != null && refreshed.isSynced && _currentTrack.value?.id == currentTrk.id) {
                                                        _currentLyrics.value = refreshed
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Player.STATE_ENDED -> {
                                _isLoadingTrack.value = false
                                _currentTrack.value?.let { tasteManager.recordTrackPlayed(it) }
                                skipNext()
                            }
                            Player.STATE_IDLE -> {
                                _isLoadingTrack.value = false
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("KuroMusicViewModel", "Player error: ${error.message}", error)
                        val track = _currentTrack.value
                        if (track != null && track.streamUrl != null && !hasRetriedFallback) {
                            hasRetriedFallback = true
                            viewModelScope.launch(Dispatchers.IO) {
                                val fallbackUrl = repository.getFallbackStreamUrl(track.url)
                                if (fallbackUrl != null && fallbackUrl != track.streamUrl) {
                                    val fallbackTrack = track.copy(streamUrl = fallbackUrl, mimeType = "audio/webm")
                                    withContext(Dispatchers.Main) {
                                        _currentTrack.value = fallbackTrack
                                        mediaController?.let { ctrl ->
                                            val meta = MediaMetadata.Builder()
                                                .setTitle(fallbackTrack.title)
                                                .setArtist(fallbackTrack.artist)
                                                .setArtworkUri(Uri.parse(fallbackTrack.highResThumbnailUrl))
                                                .setDisplayTitle(fallbackTrack.title)
                                                .setExtras(android.os.Bundle().apply {
                                                    putString(com.ayashii.kuro.widget.WIDGET_SOURCE_URL_KEY, fallbackTrack.url)
                                                })
                                                .build()

                                            val item = MediaItem.Builder()
                                                .setMediaId(fallbackTrack.id)
                                                .setUri(fallbackUrl)
                                                .setMimeType("audio/webm")
                                                .setMediaMetadata(meta)
                                                .build()

                                            ctrl.setMediaItem(item)
                                            ctrl.prepare()
                                            ctrl.play()
                                        }
                                    }
                                    return@launch
                                }
                                withContext(Dispatchers.Main) {
                                    _isLoadingTrack.value = false
                                    _isPlaying.value = false
                                    skipNext()
                                }
                            }
                        } else {
                            hasRetriedFallback = false
                            _isLoadingTrack.value = false
                            _isPlaying.value = false
                            skipNext()
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        syncCurrentTrackFromController()
                        syncWidgetState()
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Mirrors the MediaSession's current item into [_currentTrack] without touching playback.
     * Used on first connection and on external transitions (widget/service restore).
     */
    private fun syncCurrentTrackFromController() {
        val controller = mediaController ?: return
        val item = controller.currentMediaItem ?: return
        val metadata = item.mediaMetadata
        val favorite = _favorites.value.any { it.id == item.mediaId }
        val existing = _currentTrack.value

        if (existing != null && existing.id == item.mediaId) {
            if (existing.isFavorite != favorite) {
                _currentTrack.value = existing.copy(isFavorite = favorite)
            }
            return
        }
        if (item.mediaId.isBlank() && metadata.title.isNullOrBlank()) return

        _currentTrack.value = Track(
            id = item.mediaId,
            url = metadata.extras?.getString(com.ayashii.kuro.widget.WIDGET_SOURCE_URL_KEY)
                ?: item.localConfiguration?.uri?.toString().orEmpty(),
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            thumbnailUrl = metadata.artworkUri?.toString().orEmpty(),
            durationSeconds = controller.duration.takeIf { it > 0 }?.div(1000L) ?: 0L,
            isFavorite = favorite
        )
        _isPlaying.value = controller.isPlaying
        if (controller.duration > 0) _duration.value = controller.duration
        _playbackPosition.value = controller.currentPosition.coerceAtLeast(0)
        if (controller.isPlaying) startProgressTracking()
    }

    private var isSeeking = false
    private var lastSeekTimestamp = 0L

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                if (isSeeking && (now - lastSeekTimestamp < 1200L)) {
                } else {
                    isSeeking = false
                    mediaController?.let { controller ->
                        val pos = controller.currentPosition.coerceAtLeast(0)
                        _playbackPosition.value = pos
                        val realDuration = controller.duration
                        if (realDuration > 0) {
                            _duration.value = realDuration
                        }
                    }
                }
                if (_isPlaying.value && now % 5_000L < 500L) {
                    syncWidgetState()
                }
                delay(if (_isPlaying.value) 500 else 1000)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
    }

    fun loadTrending() {
        viewModelScope.launch {
            _isLoadingTrending.value = true
            val rawShelves = repository.getHomeShelves().toMutableList()
            val onRepeat = tasteManager.onRepeatTracks.value
            if (onRepeat.isNotEmpty()) {
                rawShelves.add(0, com.ayashii.kuro.data.model.HomeSection(
                    title = "Lagi Sering Kamu Putar",
                    subtitle = "Berdasarkan selera musikmu",
                    tracks = onRepeat
                ))
            }
            _homeSections.value = rawShelves
            val tracks = if (rawShelves.isNotEmpty()) {
                rawShelves.flatMap { it.tracks }.distinctBy { it.id }
            } else {
                repository.getTrendingTracks()
            }
            _trendingTracks.value = tracks
            _isLoadingTrending.value = false

            launch(Dispatchers.IO) {
                delay(2000)
                tracks.take(2).forEach {
                    repository.getAudioStreamUrl(it.url, settingsManager.audioQuality.value)
                }
            }
        }
    }

    fun loadSuggestions(query: String) {
        suggestJob?.cancel()
        if (query.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(200)
            val list = repository.getSuggestions(query)
            _suggestions.value = list
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        _suggestions.value = emptyList()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val results = repository.searchTracks(query)
            _searchResults.value = results
            _isSearching.value = false

            launch(Dispatchers.IO) {
                delay(1500)
                results.take(2).forEach {
                    repository.getAudioStreamUrl(it.url, settingsManager.audioQuality.value)
                }
            }
        }
    }

    fun playTrackFromSearch(track: Track) {
        playlistQueue.clear()
        playlistQueue.add(track)
        currentIndex = 0
        playTrack(track, playlistQueue)
    }

    fun playTrack(track: Track, newQueue: List<Track> = emptyList()) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            _currentTrack.value = track
            _isLoadingTrack.value = true
            _playbackPosition.value = 0L
            _duration.value = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L

            if (newQueue.isNotEmpty() && newQueue !== playlistQueue) {
                playlistQueue.clear()
                playlistQueue.addAll(newQueue)
                currentIndex = playlistQueue.indexOfFirst { it.id == track.id }
            } else if (currentIndex == -1 || playlistQueue.getOrNull(currentIndex)?.id != track.id) {
                val idx = playlistQueue.indexOfFirst { it.id == track.id }
                if (idx != -1) {
                    currentIndex = idx
                } else if (playlistQueue.isEmpty()) {
                    playlistQueue.add(track)
                    currentIndex = 0
                }
            }

            if (playlistQueue.size <= 2) {
                launch(Dispatchers.IO) {
                    try {
                        val radioTracks = repository.getRadioTracks(track.id)
                        if (radioTracks.isNotEmpty()) {
                            val existingIds = playlistQueue.map { it.id }.toSet()
                            val freshTracks = radioTracks.filter { it.id !in existingIds }
                            playlistQueue.addAll(freshTracks)
                            if (playlistQueue.size > 1) {
                                repository.getAudioStreamUrl(playlistQueue[1].url, settingsManager.audioQuality.value)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("KuroVM", "Automix radio fetch: ${e.message}")
                    }
                }
            }

                lyricsJob?.cancel()
                lyricsJob = viewModelScope.launch(Dispatchers.IO) {
                    _isLoadingLyrics.value = true
                    _currentLyrics.value = null
                    try {
                        val data = lyricsRepository.getLyrics(
                            trackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            durationSeconds = track.durationSeconds,
                            autoTranslate = false
                        )
                        _currentLyrics.value = data
                        _isLoadingLyrics.value = false

                        if (data != null && data.lines.isNotEmpty() && settingsManager.autoTranslate.value) {
                            val translated = lyricsRepository.translateLyrics(data)
                            if (_currentTrack.value?.id == track.id) {
                                _currentLyrics.value = translated
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("KuroVM", "Lyrics load error: ${e.message}")
                        _isLoadingLyrics.value = false
                    }
                }

            hasRetriedFallback = false
            trackStartTime = System.currentTimeMillis()
            val resolvedStream = repository.getAudioStream(track.url, settingsManager.audioQuality.value)
            val bestThumbnail = repository.resolveBestThumbnail(track)
            if (resolvedStream != null) {
                val updatedTrack = track.copy(
                    streamUrl = resolvedStream.url,
                    mimeType = resolvedStream.mimeType,
                    thumbnailUrl = bestThumbnail
                )
                _currentTrack.value = updatedTrack

                val controller = mediaController
                if (controller != null) {
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(updatedTrack.title)
                        .setArtist(updatedTrack.artist)
                        .setArtworkUri(Uri.parse(updatedTrack.highResThumbnailUrl))
                        .setDisplayTitle(updatedTrack.title)
                        .setExtras(android.os.Bundle().apply {
                            putString(com.ayashii.kuro.widget.WIDGET_SOURCE_URL_KEY, updatedTrack.url)
                        })
                        .build()

                    val mediaItemBuilder = MediaItem.Builder()
                        .setMediaId(updatedTrack.id)
                        .setUri(resolvedStream.url)
                        .setMediaMetadata(mediaMetadata)

                    if (!resolvedStream.mimeType.isNullOrBlank()) {
                        mediaItemBuilder.setMimeType(resolvedStream.mimeType)
                    }

                    // Start PlaybackService normally (MediaSessionService manages foreground state automatically)
                    try {
                        val serviceIntent = Intent(getApplication(), PlaybackService::class.java)
                        getApplication<Application>().startService(serviceIntent)
                    } catch (_: Exception) {}

                    controller.setMediaItem(mediaItemBuilder.build())
                    controller.prepare()
                    controller.play()
                } else {
                    pendingTrackToPlay = updatedTrack
                }

                if (playlistQueue.isNotEmpty() && currentIndex + 1 < playlistQueue.size) {
                    val nextTrack = playlistQueue[currentIndex + 1]
                    launch(Dispatchers.IO) {
                        repository.getAudioStreamUrl(nextTrack.url, settingsManager.audioQuality.value)
                    }
                }
            } else {
                _isLoadingTrack.value = false
                Log.e("KuroMusicViewModel", "Failed to resolve stream URL for ${track.title}")
                if (playlistQueue.isNotEmpty() && currentIndex + 1 < playlistQueue.size) {
                    skipNext()
                }
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            // playWhenReady stays true while buffering; isPlaying would pause a
            // track that is still starting instead of resuming it.
            if (controller.playWhenReady) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun getProgress(): Float {
        val d = if (_duration.value > 0) _duration.value else ((_currentTrack.value?.durationSeconds ?: 0) * 1000L)
        return if (d > 0) (_playbackPosition.value.toFloat() / d.toFloat()).coerceIn(0f, 1f) else 0f
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        _playbackPosition.value = target
        lastSeekTimestamp = System.currentTimeMillis()
        isSeeking = true
        mediaController?.let { controller ->
            controller.seekTo(target)
            if (!controller.isPlaying) {
                controller.play()
            }
        }
    }

    fun getPositionMs(): Long = _playbackPosition.value

    fun skipNext() {
        if (playlistQueue.isEmpty()) return
        val current = _currentTrack.value
        if (current != null && trackStartTime > 0L) {
            val playedSec = (System.currentTimeMillis() - trackStartTime) / 1000L
            if (playedSec in 1..14) {
                tasteManager.recordTrackSkipped(current)
            } else if (playedSec >= 25) {
                tasteManager.recordTrackPlayed(current)
            }
        }
        if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
            _currentTrack.value?.let { playTrack(it) }
            return
        }
        val nextIndex = if (_isShuffle.value) {
            (0 until playlistQueue.size).random()
        } else if (currentIndex + 1 < playlistQueue.size) {
            currentIndex + 1
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
            0
        } else {
            -1
        }

        if (nextIndex in 0 until playlistQueue.size) {
            currentIndex = nextIndex
            playTrack(playlistQueue[nextIndex])

            if (playlistQueue.size - nextIndex <= 3 && playlistQueue.isNotEmpty()) {
                val seed = playlistQueue[nextIndex]
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val freshRadio = repository.getRadioTracks(seed.id)
                        val existingIds = playlistQueue.map { it.id }.toSet()
                        val toAdd = freshRadio.filter { it.id !in existingIds }
                        playlistQueue.addAll(toAdd)
                    } catch (_: Exception) {}
                }
            }
        } else {
            _isPlaying.value = false
            mediaController?.pause()
        }
    }

    fun skipPrevious() {
        if (playlistQueue.isEmpty()) return
        val current = _currentTrack.value
        if (current != null && trackStartTime > 0L) {
            val playedSec = (System.currentTimeMillis() - trackStartTime) / 1000L
            if (playedSec >= 25) {
                tasteManager.recordTrackPlayed(current)
            }
        }
        val prevIndex = if (currentIndex - 1 >= 0) {
            currentIndex - 1
        } else {
            playlistQueue.size - 1
        }
        currentIndex = prevIndex
        playTrack(playlistQueue[prevIndex])
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeat() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        mediaController?.repeatMode = if (next == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (next == Player.REPEAT_MODE_ONE) {
            _currentTrack.value?.let { tasteManager.recordTrackRepeated(it) }
        }
    }

    fun toggleFavorite(track: Track) {
        val currentFavs = _favorites.value.toMutableList()
        val exists = currentFavs.any { it.id == track.id }
        if (exists) {
            currentFavs.removeAll { it.id == track.id }
        } else {
            currentFavs.add(track.copy(isFavorite = true))
            tasteManager.recordTrackLiked(track)
        }
        _favorites.value = currentFavs
        saveStoredFavorites(currentFavs)
        syncWidgetState()

        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = _currentTrack.value?.copy(isFavorite = !exists)
        }
    }

    fun expandPlayer(expand: Boolean) {
        _isPlayerExpanded.value = expand
    }

    fun syncLikedSongs() {
        viewModelScope.launch {
            _isLoadingTrending.value = true
            val liked = repository.getLikedTracks()
            if (liked.isNotEmpty()) {
                val currentFavs = _favorites.value.toMutableList()
                liked.forEach { track ->
                    if (currentFavs.none { it.id == track.id }) {
                        currentFavs.add(track.copy(isFavorite = true))
                    }
                }
                _favorites.value = currentFavs
                saveStoredFavorites(currentFavs)
            }
            _isLoadingTrending.value = false
        }
    }

    fun logout() {
        com.ayashii.kuro.data.auth.AuthManager.getInstance().logout()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<android.app.Application>().unregisterReceiver(mediaActionReceiver)
        } catch (_: Exception) {}
        stopProgressTracking()
        suggestJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
