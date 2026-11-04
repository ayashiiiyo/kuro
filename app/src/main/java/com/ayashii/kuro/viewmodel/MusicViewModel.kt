package com.ayashii.kuro.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
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
import com.ayashii.kuro.data.model.HomeSection
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.player.PlayerQueueManager
import com.ayashii.kuro.data.remote.MusicRepository
import com.ayashii.kuro.service.PlaybackService
import com.ayashii.kuro.widget.WIDGET_FAVORITES_CHANGED
import com.ayashii.kuro.widget.WIDGET_SOURCE_URL_KEY
import com.ayashii.kuro.widget.persist
import com.ayashii.kuro.widget.updateKuroWidgets
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.ByteArrayOutputStream
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository()
    private val queueManager = PlayerQueueManager.getInstance()
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

    private val _homeSections = MutableStateFlow<List<HomeSection>>(emptyList())
    val homeSections = _homeSections.asStateFlow()

    private val _trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingTracks = _trendingTracks.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _genreTracks = MutableStateFlow<List<Track>>(emptyList())
    val genreTracks: StateFlow<List<Track>> = _genreTracks.asStateFlow()

    private val _isLoadingGenre = MutableStateFlow(false)
    val isLoadingGenre: StateFlow<Boolean> = _isLoadingGenre.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val favPrefs = getApplication<Application>().getSharedPreferences("kuro_favorites", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<List<Track>>(loadStoredFavorites())
    val favorites = _favorites.asStateFlow()

    private fun loadStoredFavorites(): List<Track> {
        val jsonStr = favPrefs.getString("fav_list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                list.forEach { t ->
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
                favPrefs.edit().putString("fav_list", arr.toString()).apply()
            } catch (e: Exception) {
                Log.e("KuroVM", "Save favorites error: ${e.message}")
            }
        }
    }

    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending = _isLoadingTrending.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    val isShuffle: StateFlow<Boolean> = queueManager.isShuffle
    val repeatMode: StateFlow<Int> = queueManager.repeatMode
    val consecutiveErrors: StateFlow<Int> = queueManager.consecutiveErrors

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

    fun removeFromRecents(trackId: String) = tasteManager.removeFromRecents(trackId)
    fun clearRecentSearches() = tasteManager.clearRecents()

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

    private val playlistQueue = Collections.synchronizedList(mutableListOf<Track>())
    private var currentIndex = -1

    val queueState: StateFlow<List<Track>> = queueManager.queueState

    fun updateQueueState() {
        val current = queueManager.queueState.value
        synchronized(playlistQueue) {
            playlistQueue.clear()
            playlistQueue.addAll(current)
        }
        currentIndex = queueManager.currentIndex.value
    }

    fun getCurrentQueueIndex(): Int = queueManager.currentIndex.value

    fun addToQueue(track: Track) {
        queueManager.addToQueue(track)
        updateQueueState()
    }

    fun removeFromQueue(index: Int): Boolean {
        val result = queueManager.removeFromQueue(index)
        updateQueueState()
        return result
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int): Boolean {
        val result = queueManager.moveQueueItem(fromIndex, toIndex)
        updateQueueState()
        return result
    }

    fun clearUpcomingQueue() {
        queueManager.clearUpcomingQueue()
        updateQueueState()
    }

    private fun appendRadioTracks(candidates: List<Track>) {
        if (candidates.isEmpty()) return
        val fresh = synchronized(playlistQueue) {
            val existingIds = playlistQueue.mapTo(HashSet()) { it.id }
            tasteManager.rankRadioTracks(candidates.filter { it.id !in existingIds })
        }
        if (fresh.isNotEmpty()) {
            fresh.forEach { queueManager.addToQueue(it) }
            updateQueueState()
        }
    }

    private var progressJob: Job? = null
    private var suggestJob: Job? = null
    private var playJob: Job? = null
    private var lyricsJob: Job? = null
    private var searchJob: Job? = null
    private var genreJob: Job? = null
    private var pendingTrackToPlay: Track? = null
    private var hasRetriedFallback = false
    private var trackStartTime = 0L

    private val mediaActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PlaybackService.ACTION_NEXT -> skipNext(userInitiated = true)
                PlaybackService.ACTION_PREV -> skipPrevious()
                WIDGET_FAVORITES_CHANGED -> reloadFavoritesFromPrefs()
            }
        }
    }

    private fun reloadFavoritesFromPrefs() {
        val latest = loadStoredFavorites()
        _favorites.value = latest
        val current = _currentTrack.value
        if (current != null) {
            val favorite = latest.any { it.id == current.id }
            if (current.isFavorite != favorite) {
                val updated = current.copy(isFavorite = favorite)
                _currentTrack.value = updated
                queueManager.updateCurrentTrack(updated)
            }
        }
    }

    private fun syncWidgetState() {
        val controller = mediaController ?: return
        persist(getApplication(), controller)
        viewModelScope.launch(Dispatchers.IO) { updateKuroWidgets(getApplication()) }
    }

    init {
        initializeMediaController()
        loadTrending()
        viewModelScope.launch {
            queueManager.queueState.collect { tracks ->
                synchronized(playlistQueue) {
                    playlistQueue.clear()
                    playlistQueue.addAll(tracks)
                }
            }
        }
        viewModelScope.launch {
            queueManager.currentIndex.collect { idx ->
                currentIndex = idx
            }
        }
        viewModelScope.launch {
            queueManager.currentTrack.collect { trk ->
                if (trk == null && queueManager.queueState.value.isEmpty()) {
                    _currentTrack.value = null
                } else if (trk != null && _currentTrack.value?.id != trk.id) {
                    _currentTrack.value = trk
                }
            }
        }
        try {
            val filter = IntentFilter().apply {
                addAction(PlaybackService.ACTION_NEXT)
                addAction(PlaybackService.ACTION_PREV)
                addAction(WIDGET_FAVORITES_CHANGED)
            }
            ContextCompat.registerReceiver(
                getApplication(),
                mediaActionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
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
                                mediaController?.let { ctrl ->
                                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                                    anchorPosition = pos
                                    anchorTimestamp = SystemClock.elapsedRealtime()
                                    _playbackPosition.value = pos
                                }
                            }
                            Player.STATE_READY -> {
                                queueManager.recordPlaybackSuccess()
                                _isLoadingTrack.value = false
                                isSeeking = false
                                mediaController?.let { controller ->
                                    val pos = controller.currentPosition.coerceAtLeast(0L)
                                    anchorPosition = pos
                                    anchorTimestamp = SystemClock.elapsedRealtime()
                                    lastGroundTruthSync = anchorTimestamp
                                    _playbackPosition.value = pos
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
                                                    try {
                                                        _isLoadingLyrics.value = true
                                                        val refreshed = lyricsRepository.getLyrics(
                                                            trackId = currentTrk.id,
                                                            title = currentTrk.title,
                                                            artist = currentTrk.artist,
                                                            durationSeconds = realDurSec
                                                        )
                                                        if (refreshed != null && refreshed.isSynced && _currentTrack.value?.id == currentTrk.id) {
                                                            _currentLyrics.value = refreshed
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("KuroVM", "Lyrics duration resync error: ${e.message}")
                                                    } finally {
                                                        _isLoadingLyrics.value = false
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
                                skipNext(userInitiated = false, isErrorRecovery = false)
                            }
                            Player.STATE_IDLE -> {
                                _isLoadingTrack.value = false
                            }
                        }
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                            isSeeking = false
                            mediaController?.let { ctrl ->
                                val pos = ctrl.currentPosition.coerceAtLeast(0L)
                                anchorPosition = pos
                                val now = SystemClock.elapsedRealtime()
                                anchorTimestamp = now
                                lastGroundTruthSync = now
                                _playbackPosition.value = pos
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("KuroMusicViewModel", "Player error: ${error.message}", error)
                        val track = _currentTrack.value
                        if (track != null) {
                            try {
                                repository.evictStreamUrl(track.url)
                            } catch (_: Throwable) {}
                        }
                        val failureCount = queueManager.recordPlaybackFailure()
                        if (failureCount >= 3) {
                            Log.e("KuroMusicViewModel", "Consecutive playback errors reached limit: $failureCount")
                            _isLoadingTrack.value = false
                            _isPlaying.value = false
                            return
                        }
                        if (track != null && track.streamUrl != null && !hasRetriedFallback) {
                            hasRetriedFallback = true
                            viewModelScope.launch(Dispatchers.IO) {
                                val fallbackUrl = repository.getFallbackStreamUrl(track.url)
                                if (fallbackUrl != null && fallbackUrl != track.streamUrl) {
                                    val fallbackTrack = track.copy(streamUrl = fallbackUrl, mimeType = "audio/webm")
                                    withContext(Dispatchers.Main) {
                                        _currentTrack.value = fallbackTrack
                                        queueManager.updateCurrentTrack(fallbackTrack)
                                        mediaController?.let { ctrl ->
                                            val meta = MediaMetadata.Builder()
                                                .setTitle(fallbackTrack.title)
                                                .setArtist(fallbackTrack.artist)
                                                .setArtworkUri(Uri.parse(fallbackTrack.highResThumbnailUrl))
                                                .setDisplayTitle(fallbackTrack.title)
                                                .setExtras(Bundle().apply {
                                                    putString(WIDGET_SOURCE_URL_KEY, fallbackTrack.url)
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
                                    skipNext(userInitiated = false, isErrorRecovery = true)
                                }
                            }
                        } else {
                            hasRetriedFallback = false
                            _isLoadingTrack.value = false
                            _isPlaying.value = false
                            skipNext(userInitiated = false, isErrorRecovery = true)
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        anchorPosition = 0L
                        val now = SystemClock.elapsedRealtime()
                        anchorTimestamp = now
                        lastGroundTruthSync = now
                        _playbackPosition.value = 0L
                        syncCurrentTrackFromController()
                        syncWidgetState()
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun syncCurrentTrackFromController() {
        val controller = mediaController ?: return
        val item = controller.currentMediaItem ?: return
        val metadata = item.mediaMetadata
        val favorite = _favorites.value.any { it.id == item.mediaId }
        val existing = _currentTrack.value

        if (existing != null && existing.id == item.mediaId) {
            if (existing.isFavorite != favorite) {
                val updated = existing.copy(isFavorite = favorite)
                _currentTrack.value = updated
                queueManager.updateCurrentTrack(updated)
            }
            return
        }
        if (item.mediaId.isBlank() && metadata.title.isNullOrBlank()) return

        val resolved = Track(
            id = item.mediaId,
            url = metadata.extras?.getString(WIDGET_SOURCE_URL_KEY)
                ?: item.localConfiguration?.uri?.toString().orEmpty(),
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            thumbnailUrl = metadata.artworkUri?.toString().orEmpty(),
            durationSeconds = controller.duration.takeIf { it > 0 }?.div(1000L) ?: 0L,
            isFavorite = favorite
        )
        _currentTrack.value = resolved
        queueManager.updateCurrentTrack(resolved)
        _isPlaying.value = controller.isPlaying
        if (controller.duration > 0) _duration.value = controller.duration
        _playbackPosition.value = controller.currentPosition.coerceAtLeast(0)
        anchorPosition = _playbackPosition.value
        val now = SystemClock.elapsedRealtime()
        anchorTimestamp = now
        lastGroundTruthSync = now
        if (controller.isPlaying) startProgressTracking()
    }

    private var anchorPosition = 0L
    private var anchorTimestamp = 0L
    private var lastGroundTruthSync = 0L
    private var lastWidgetSync = 0L
    private var isSeeking = false
    private var lastSeekTimestamp = 0L

    private fun startProgressTracking() {
        progressJob?.cancel()
        val controller = mediaController
        val initialPos = controller?.currentPosition?.coerceAtLeast(0L) ?: _playbackPosition.value
        anchorPosition = initialPos
        anchorTimestamp = SystemClock.elapsedRealtime()
        lastGroundTruthSync = anchorTimestamp
        _playbackPosition.value = initialPos

        progressJob = viewModelScope.launch {
            while (true) {
                val now = SystemClock.elapsedRealtime()

                if (isSeeking && (now - lastSeekTimestamp >= 1200L)) {
                    isSeeking = false
                }

                if (!isSeeking && (now - lastGroundTruthSync >= 500L)) {
                    mediaController?.let { ctrl ->
                        val actualPos = ctrl.currentPosition.coerceAtLeast(0L)
                        val realDuration = ctrl.duration
                        if (realDuration > 0) {
                            _duration.value = realDuration
                        }
                        anchorPosition = actualPos
                        anchorTimestamp = now
                        lastGroundTruthSync = now
                    }
                }

                if (!isSeeking) {
                    val ctrl = mediaController
                    val isBuffering = ctrl?.playbackState == Player.STATE_BUFFERING || _isLoadingTrack.value
                    val isPlayingNow = _isPlaying.value && (ctrl?.isPlaying == true) && !isBuffering

                    val pos = if (isPlayingNow) {
                        val elapsed = now - anchorTimestamp
                        val dur = _duration.value
                        val calcPos = if (dur > 0) (anchorPosition + elapsed).coerceAtMost(dur)
                        else anchorPosition + elapsed

                        val currentPos = _playbackPosition.value
                        if (currentPos > 0 && calcPos < currentPos) {
                            currentPos
                        } else {
                            calcPos
                        }
                    } else {
                        if (isBuffering) {
                            anchorTimestamp = now
                        }
                        anchorPosition
                    }
                    _playbackPosition.value = pos.coerceAtLeast(0L)
                }

                if (_isPlaying.value && (now - lastWidgetSync >= 5000L)) {
                    lastWidgetSync = now
                    syncWidgetState()
                }

                delay(33)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        mediaController?.let { ctrl ->
            val pos = ctrl.currentPosition.coerceAtLeast(0L)
            anchorPosition = pos
            anchorTimestamp = SystemClock.elapsedRealtime()
            _playbackPosition.value = pos
        }
    }

    fun loadTrending() {
        viewModelScope.launch {
            _isLoadingTrending.value = true
            val rawShelves = repository.getHomeShelves().toMutableList()
            val onRepeat = tasteManager.onRepeatTracks.value
            if (onRepeat.isNotEmpty()) {
                rawShelves.add(0, HomeSection(
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

    fun loadGenreTracks(genre: String) {
        genreJob?.cancel()
        if (genre.isBlank() || genre == "Semua") {
            _genreTracks.value = emptyList()
            _isLoadingGenre.value = false
            return
        }
        genreJob = viewModelScope.launch {
            _isLoadingGenre.value = true
            try {
                val results = repository.searchTracks("$genre music hits")
                _genreTracks.value = results
            } catch (e: Exception) {
                Log.e("KuroMusicViewModel", "Load genre tracks error: ${e.message}")
                _genreTracks.value = emptyList()
            } finally {
                _isLoadingGenre.value = false
            }
        }
    }

    fun playTrackFromSearch(track: Track) {
        playTrack(track, listOf(track))
    }

    fun playTrack(track: Track, newQueue: List<Track> = emptyList()) {
        synchronized(playlistQueue) {
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
                } else {
                    val insertIdx = (currentIndex + 1).coerceIn(0, playlistQueue.size)
                    playlistQueue.add(insertIdx, track)
                    currentIndex = insertIdx
                }
            }
        }
        queueManager.playTrack(track, newQueue)
        updateQueueState()
        playTrackExecution(track)
    }

    fun playTrackAtIndex(index: Int) {
        val targetTrack = synchronized(playlistQueue) {
            playlistQueue.getOrNull(index)
        } ?: queueManager.queueState.value.getOrNull(index) ?: return

        queueManager.playTrackAtIndex(index)
        currentIndex = index
        updateQueueState()
        playTrackExecution(targetTrack)
    }

    private fun playTrackExecution(track: Track) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            _currentTrack.value = track
            _isLoadingTrack.value = true
            _playbackPosition.value = 0L
            anchorPosition = 0L
            val now = SystemClock.elapsedRealtime()
            anchorTimestamp = now
            lastGroundTruthSync = now
            _duration.value = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L

            if (queueManager.queueState.value.size <= 2) {
                launch(Dispatchers.IO) {
                    try {
                        val radioTracks = repository.getRadioTracks(track.id)
                        if (radioTracks.isNotEmpty()) {
                            appendRadioTracks(radioTracks)
                            val currentList = queueManager.queueState.value
                            if (currentList.size > 1) {
                                repository.getAudioStreamUrl(currentList[1].url, settingsManager.audioQuality.value)
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

                    if (data != null && data.lines.isNotEmpty() && settingsManager.autoTranslate.value) {
                        val translated = lyricsRepository.translateLyrics(data)
                        if (_currentTrack.value?.id == track.id) {
                            _currentLyrics.value = translated
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KuroVM", "Lyrics load error: ${e.message}")
                } finally {
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
                queueManager.updateCurrentTrack(updatedTrack)

                val controller = mediaController
                if (controller != null) {
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(updatedTrack.title)
                        .setArtist(updatedTrack.artist)
                        .setArtworkUri(Uri.parse(updatedTrack.highResThumbnailUrl))
                        .setDisplayTitle(updatedTrack.title)
                        .setExtras(Bundle().apply {
                            putString(WIDGET_SOURCE_URL_KEY, updatedTrack.url)
                        })
                        .build()

                    val mediaItemBuilder = MediaItem.Builder()
                        .setMediaId(updatedTrack.id)
                        .setUri(resolvedStream.url)
                        .setMediaMetadata(mediaMetadata)

                    if (!resolvedStream.mimeType.isNullOrBlank()) {
                        mediaItemBuilder.setMimeType(resolvedStream.mimeType)
                    }

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

                val currentList = queueManager.queueState.value
                val curIdx = queueManager.currentIndex.value
                if (currentList.isNotEmpty() && curIdx + 1 < currentList.size) {
                    val nextTrack = currentList[curIdx + 1]
                    launch(Dispatchers.IO) {
                        repository.getAudioStreamUrl(nextTrack.url, settingsManager.audioQuality.value)
                    }
                }
            } else {
                _isLoadingTrack.value = false
                Log.e("KuroMusicViewModel", "Failed to resolve stream URL for ${track.title}")
                try {
                    repository.evictStreamUrl(track.url)
                } catch (_: Throwable) {}
                val failureCount = queueManager.recordPlaybackFailure()
                if (failureCount < 3 && queueManager.hasNext()) {
                    skipNext(userInitiated = false, isErrorRecovery = true)
                } else {
                    _isPlaying.value = false
                }
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
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
        anchorPosition = target
        val now = SystemClock.elapsedRealtime()
        anchorTimestamp = now
        lastGroundTruthSync = now
        lastSeekTimestamp = now
        isSeeking = true
        mediaController?.seekTo(target)
    }

    fun getPositionMs(): Long = _playbackPosition.value

    fun skipNext(userInitiated: Boolean = true, isErrorRecovery: Boolean = false) {
        val currentQueue = queueManager.queueState.value
        if (currentQueue.isEmpty()) return
        val current = _currentTrack.value
        if (current != null && trackStartTime > 0L) {
            val playedSec = (System.currentTimeMillis() - trackStartTime) / 1000L
            if (playedSec in 1..14) {
                tasteManager.recordTrackSkipped(current)
            } else if (playedSec >= 25) {
                tasteManager.recordTrackPlayed(current)
            }
        }
        if (repeatMode.value == Player.REPEAT_MODE_ONE && !userInitiated && !isErrorRecovery) {
            _currentTrack.value?.let { playTrack(it) }
            return
        }
        val nextTrack = queueManager.getNextTrack(userInitiated = userInitiated, isErrorRecovery = isErrorRecovery)
        if (nextTrack != null) {
            currentIndex = queueManager.currentIndex.value
            updateQueueState()
            playTrackExecution(nextTrack)
            val remaining = queueManager.queueState.value.size - currentIndex
            if (remaining <= 3) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val freshRadio = repository.getRadioTracks(nextTrack.id)
                        appendRadioTracks(freshRadio)
                    } catch (_: Exception) {}
                }
            }
        } else {
            _isPlaying.value = false
            mediaController?.pause()
        }
    }

    fun skipPrevious() {
        val currentQueue = queueManager.queueState.value
        if (currentQueue.isEmpty()) return
        val current = _currentTrack.value
        if (current != null && trackStartTime > 0L) {
            val playedSec = (System.currentTimeMillis() - trackStartTime) / 1000L
            if (playedSec >= 25) {
                tasteManager.recordTrackPlayed(current)
            }
        }
        val prevTrack = queueManager.getPreviousTrack()
        if (prevTrack != null) {
            currentIndex = queueManager.currentIndex.value
            updateQueueState()
            playTrackExecution(prevTrack)
        }
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
    }

    fun toggleRepeat() {
        queueManager.toggleRepeat()
        val next = queueManager.repeatMode.value
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
            val updated = _currentTrack.value?.copy(isFavorite = !exists)
            _currentTrack.value = updated
            if (updated != null) {
                queueManager.updateCurrentTrack(updated)
            }
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
            getApplication<Application>().unregisterReceiver(mediaActionReceiver)
        } catch (_: Exception) {}
        stopProgressTracking()
        suggestJob?.cancel()
        genreJob?.cancel()
        searchJob?.cancel()
        playJob?.cancel()
        lyricsJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
