package com.ayashii.kuro.viewmodel

import android.app.Application
import android.content.ComponentName
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

    private val _trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingTracks = _trendingTracks.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    val favorites = _favorites.asStateFlow()

    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending = _isLoadingTrending.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    val authState = com.ayashii.kuro.data.auth.AuthManager.getInstance().authState

    private val playlistQueue = mutableListOf<Track>()
    private var currentIndex = -1
    private var progressJob: Job? = null
    private var suggestJob: Job? = null
    private var playJob: Job? = null

    init {
        initializeMediaController()
        loadTrending()
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
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            _isLoadingTrack.value = false
                            startProgressTracking()
                        } else {
                            stopProgressTracking()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _isLoadingTrack.value = true
                            }
                            Player.STATE_READY -> {
                                _isLoadingTrack.value = false
                                mediaController?.let { controller ->
                                    val d = controller.duration
                                    if (d > 0) _duration.value = d
                                }
                            }
                            Player.STATE_ENDED -> {
                                _isLoadingTrack.value = false
                                skipNext()
                            }
                            Player.STATE_IDLE -> {
                                _isLoadingTrack.value = false
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("KuroMusicViewModel", "Player error: ${error.message}", error)
                        _isLoadingTrack.value = false
                        _isPlaying.value = false
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaController?.let { controller ->
                    _playbackPosition.value = controller.currentPosition.coerceAtLeast(0)
                    val realDuration = controller.duration
                    if (realDuration > 0) {
                        _duration.value = realDuration
                    }
                }
                delay(300)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
    }

    fun loadTrending() {
        viewModelScope.launch {
            _isLoadingTrending.value = true
            val tracks = repository.getTrendingTracks()
            _trendingTracks.value = tracks
            _isLoadingTrending.value = false

            // Pre-warm audio URLs for top 2 tracks in background
            launch(Dispatchers.IO) {
                tracks.take(2).forEach {
                    repository.getAudioStreamUrl(it.url)
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
        _suggestions.value = emptyList()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val results = repository.searchTracks(query)
            _searchResults.value = results
            _isSearching.value = false

            // Pre-warm audio URLs for top 2 search results in background
            launch(Dispatchers.IO) {
                results.take(2).forEach {
                    repository.getAudioStreamUrl(it.url)
                }
            }
        }
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

            // Resolve direct audio stream URL via NewPipeExtractor
            val streamUrl = repository.getAudioStreamUrl(track.url)
            if (streamUrl != null) {
                val updatedTrack = track.copy(streamUrl = streamUrl)
                _currentTrack.value = updatedTrack

                mediaController?.let { controller ->
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(updatedTrack.title)
                        .setArtist(updatedTrack.artist)
                        .setArtworkUri(Uri.parse(updatedTrack.thumbnailUrl))
                        .setDisplayTitle(updatedTrack.title)
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setMediaId(updatedTrack.id)
                        .setUri(streamUrl)
                        .setMediaMetadata(mediaMetadata)
                        .build()

                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    controller.play()
                }

                // Pre-fetch next track in background for instantaneous next play!
                if (playlistQueue.isNotEmpty() && currentIndex + 1 < playlistQueue.size) {
                    val nextTrack = playlistQueue[currentIndex + 1]
                    launch(Dispatchers.IO) {
                        repository.getAudioStreamUrl(nextTrack.url)
                    }
                }
            } else {
                _isLoadingTrack.value = false
                Log.e("KuroMusicViewModel", "Failed to resolve stream URL for ${track.title}")
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
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
        mediaController?.seekTo(positionMs)
        _playbackPosition.value = positionMs
    }

    fun skipNext() {
        if (playlistQueue.isEmpty()) return
        if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
            _currentTrack.value?.let { playTrack(it) }
            return
        }
        val nextIndex = if (_isShuffle.value) {
            (0 until playlistQueue.size).random()
        } else if (currentIndex + 1 < playlistQueue.size) {
            currentIndex + 1
        } else {
            0
        }
        currentIndex = nextIndex
        playTrack(playlistQueue[nextIndex])
    }

    fun skipPrevious() {
        if (playlistQueue.isEmpty()) return
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
        mediaController?.repeatMode = next
    }

    fun toggleFavorite(track: Track) {
        val currentFavs = _favorites.value.toMutableList()
        val exists = currentFavs.any { it.id == track.id }
        if (exists) {
            currentFavs.removeAll { it.id == track.id }
        } else {
            currentFavs.add(track.copy(isFavorite = true))
        }
        _favorites.value = currentFavs

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
            }
            _isLoadingTrending.value = false
        }
    }

    fun logout() {
        com.ayashii.kuro.data.auth.AuthManager.getInstance().logout()
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressTracking()
        suggestJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
