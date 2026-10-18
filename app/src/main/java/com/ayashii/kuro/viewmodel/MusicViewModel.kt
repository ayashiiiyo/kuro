package com.ayashii.kuro.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.remote.MusicRepository
import com.ayashii.kuro.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

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

    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    val favorites = _favorites.asStateFlow()

    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending = _isLoadingTrending.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val playlistQueue = mutableListOf<Track>()
    private var currentIndex = -1
    private var progressJob: Job? = null

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
                        if (playing) startProgressTracking() else stopProgressTracking()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            skipNext()
                        }
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
                    _duration.value = controller.duration.coerceAtLeast(0)
                }
                delay(500)
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
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val results = repository.searchTracks(query)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun playTrack(track: Track, queue: List<Track> = emptyList()) {
        viewModelScope.launch {
            _currentTrack.value = track
            if (queue.isNotEmpty()) {
                playlistQueue.clear()
                playlistQueue.addAll(queue)
                currentIndex = queue.indexOfFirst { it.id == track.id }
            }

            // Resolve direct audio stream URL via NewPipeExtractor
            val streamUrl = repository.getAudioStreamUrl(track.url)
            if (streamUrl != null) {
                val updatedTrack = track.copy(streamUrl = streamUrl)
                _currentTrack.value = updatedTrack

                mediaController?.let { controller ->
                    val mediaItem = MediaItem.fromUri(streamUrl)
                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    controller.play()
                }
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

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackPosition.value = positionMs
    }

    fun skipNext() {
        if (playlistQueue.isNotEmpty() && currentIndex + 1 < playlistQueue.size) {
            currentIndex++
            playTrack(playlistQueue[currentIndex], playlistQueue)
        }
    }

    fun skipPrevious() {
        if (playlistQueue.isNotEmpty() && currentIndex - 1 >= 0) {
            currentIndex--
            playTrack(playlistQueue[currentIndex], playlistQueue)
        }
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

    override fun onCleared() {
        super.onCleared()
        stopProgressTracking()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
