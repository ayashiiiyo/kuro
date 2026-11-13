package com.ayashii.kuro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ayashii.kuro.MainActivity
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.player.PlayerQueueManager
import com.ayashii.kuro.data.recommendation.TasteManager
import com.ayashii.kuro.data.remote.MusicRepository
import com.ayashii.kuro.data.settings.SettingsManager
import com.ayashii.kuro.widget.WIDGET_SOURCE_URL_KEY
import com.ayashii.kuro.widget.loadPersistedTrack
import com.ayashii.kuro.widget.persist
import com.ayashii.kuro.widget.updateKuroWidgets
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
private class KuroNotificationBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope
) : BitmapLoader {

    private val bitmapCache = LruCache<String, Bitmap>(24)

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch(Dispatchers.IO) {
            try {
                val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bmp != null) {
                    future.set(bmp)
                } else {
                    future.setException(IllegalArgumentException("Failed to decode bitmap bytes"))
                }
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val url = uri.toString()
        if (url.isBlank()) {
            future.setException(IllegalArgumentException("Empty artwork URI"))
            return future
        }
        synchronized(bitmapCache) {
            bitmapCache.get(url)?.let { cached ->
                if (!cached.isRecycled) {
                    future.set(cached)
                    return future
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                val loaded = fetchBitmapWithFallback(url)
                if (loaded != null) {
                    synchronized(bitmapCache) {
                        bitmapCache.put(url, loaded)
                    }
                    future.set(loaded)
                } else {
                    future.setException(IllegalStateException("Artwork load returned null for $url"))
                }
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    private suspend fun fetchBitmapWithFallback(primaryUrl: String): Bitmap? {
        val loader = Coil.imageLoader(context)
        val candidates = mutableListOf(primaryUrl)
        if (primaryUrl.contains("maxresdefault")) {
            candidates.add(primaryUrl.replace("maxresdefault.jpg", "hqdefault.jpg").replace("maxresdefault.webp", "hqdefault.jpg"))
        } else if (primaryUrl.contains("=w1080-h1080")) {
            candidates.add(primaryUrl.replace("=w1080-h1080", "=w512-h512"))
        }

        for (candidate in candidates.distinct()) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(candidate)
                    .size(512, 512)
                    .allowHardware(false)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val res = loader.execute(req)
                val bmp = (res as? SuccessResult)?.let { (it.drawable as? BitmapDrawable)?.bitmap }
                if (bmp != null && !bmp.isRecycled) {
                    return bmp
                }
            } catch (_: Throwable) {}
        }
        return null
    }
}

class PlaybackService : MediaSessionService() {

    private var player: Player? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = MusicRepository.getInstance()

    @Volatile
    private var restoringSession = false
    private var pauseTimeoutJob: Job? = null
    private var servicePlayJob: Job? = null

    companion object {
        private const val TAG = "KuroPlaybackService"
        private const val PAUSE_TIMEOUT_MS = 5 * 60 * 1000L
        const val CHANNEL_ID = "kuro_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.ayashii.kuro.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.ayashii.kuro.ACTION_NEXT"
        const val ACTION_PREV = "com.ayashii.kuro.ACTION_PREV"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            .setFragmentedMp4ExtractorFlags(androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory, extractorsFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                30_000,
                500,
                1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer Error: ${error.message}", error)
                val current = PlayerQueueManager.currentTrack.value
                if (current != null) {
                    try {
                        repository.evictStreamUrl(current.url)
                    } catch (_: Throwable) {}
                }
                val failureCount = PlayerQueueManager.recordPlaybackFailure()
                if (failureCount < 3) {
                    val nextTrack = PlayerQueueManager.getNextTrack(userInitiated = false, isErrorRecovery = true)
                    if (nextTrack != null) {
                        playTrack(nextTrack)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    PlayerQueueManager.recordPlaybackSuccess()
                    syncNotificationAndWidgets()
                } else if (playbackState == Player.STATE_ENDED) {
                    val current = PlayerQueueManager.currentTrack.value
                    if (current != null) {
                        TasteManager.getInstance(this@PlaybackService).recordTrackPlayed(current)
                    }
                    if (PlayerQueueManager.consumeSleepTimerEndOfTrack()) {
                        exoPlayer.pause()
                        syncNotificationAndWidgets()
                        updatePauseTimeout(exoPlayer)
                        return
                    }
                    val nextTrack = PlayerQueueManager.getNextTrack(userInitiated = false)
                    if (nextTrack != null) {
                        playTrack(nextTrack)
                    }
                }
                updatePauseTimeout(exoPlayer)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                syncNotificationAndWidgets()
                updatePauseTimeout(exoPlayer)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncNotificationAndWidgets()
            }
        })

        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(exoPlayer) {
            override fun play() {
                if (mediaItemCount == 0) {
                    restoreLastSessionAndPlay()
                    return
                }
                if (playbackState == Player.STATE_IDLE) {
                    prepare()
                }
                super.play()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> PlayerQueueManager.hasNext()
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> PlayerQueueManager.hasPrevious()
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getAvailableCommands(): Player.Commands {
                val builder = super.getAvailableCommands().buildUpon()
                if (PlayerQueueManager.hasNext()) {
                    builder.add(Player.COMMAND_SEEK_TO_NEXT)
                    builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                } else {
                    builder.remove(Player.COMMAND_SEEK_TO_NEXT)
                    builder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
                if (PlayerQueueManager.hasPrevious()) {
                    builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                } else {
                    builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
                return builder.build()
            }

            override fun seekToNext() {
                val nextTrack = PlayerQueueManager.getNextTrack(userInitiated = true)
                if (nextTrack != null) {
                    playTrack(nextTrack)
                }
            }

            override fun seekToNextMediaItem() {
                seekToNext()
            }

            override fun seekToPrevious() {
                val prevTrack = PlayerQueueManager.getPreviousTrack()
                if (prevTrack != null) {
                    playTrack(prevTrack)
                }
            }

            override fun seekToPreviousMediaItem() {
                seekToPrevious()
            }
        }

        player = forwardingPlayer

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)

        setMediaNotificationProvider(notificationProvider)

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(KuroNotificationBitmapLoader(this, serviceScope))
            .build()
    }

    private fun syncNotificationAndWidgets() {
        val activePlayer = player ?: return
        persist(this@PlaybackService, activePlayer)
        serviceScope.launch(Dispatchers.IO) {
            updateKuroWidgets(this@PlaybackService)
        }
    }

    private fun buildTrackMediaMetadata(track: Track): MediaMetadata {
        val artworkUrl = track.thumbnailUrlForDisplay(512).ifBlank { track.highResThumbnailUrl }
        return MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.artist)
            .setArtworkUri(if (artworkUrl.isNotBlank()) Uri.parse(artworkUrl) else null)
            .setDisplayTitle(track.title)
            .setExtras(Bundle().apply {
                putString(WIDGET_SOURCE_URL_KEY, track.url)
                putString("track_id", track.id)
            })
            .build()
    }

    private fun updatePauseTimeout(targetPlayer: Player) {
        val isPausedOrEnded = !targetPlayer.playWhenReady ||
            targetPlayer.playbackState == Player.STATE_ENDED ||
            targetPlayer.playbackState == Player.STATE_IDLE
        if (isPausedOrEnded) {
            if (pauseTimeoutJob == null) {
                pauseTimeoutJob = serviceScope.launch {
                    delay(PAUSE_TIMEOUT_MS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(false)
                    }
                }
            }
        } else {
            pauseTimeoutJob?.cancel()
            pauseTimeoutJob = null
        }
    }

    private fun playTrack(track: Track) {
        servicePlayJob?.cancel()
        PlayerQueueManager.updateCurrentTrack(track)
        servicePlayJob = serviceScope.launch {
            try {
                val quality = SettingsManager.getInstance().audioQuality.value
                val resolved = withContext(Dispatchers.IO) {
                    runCatching { repository.getAudioStream(track.url, quality) }.getOrNull()
                }
                if (resolved == null) {
                    Log.e(TAG, "Failed to resolve stream for ${track.title}")
                    val errorCount = PlayerQueueManager.recordPlaybackFailure()
                    if (errorCount < 3) {
                        val nextTrack = PlayerQueueManager.getNextTrack(userInitiated = false, isErrorRecovery = true)
                        if (nextTrack != null) {
                            playTrack(nextTrack)
                        }
                    }
                    return@launch
                }

                if (PlayerQueueManager.currentTrack.value?.id != track.id) {
                    return@launch
                }

                val updatedTrack = track.copy(
                    streamUrl = resolved.url,
                    mimeType = resolved.mimeType
                )
                PlayerQueueManager.updateCurrentTrack(updatedTrack)
                PlayerQueueManager.recordPlaybackSuccess()

                val metadata = buildTrackMediaMetadata(updatedTrack)

                val builder = MediaItem.Builder()
                    .setMediaId(updatedTrack.id)
                    .setUri(resolved.url)
                    .setMediaMetadata(metadata)
                if (!resolved.mimeType.isNullOrBlank()) {
                    builder.setMimeType(resolved.mimeType)
                }

                player?.run {
                    setMediaItem(builder.build())
                    prepare()
                    playWhenReady = true
                }
                syncNotificationAndWidgets()

                launch(Dispatchers.IO) {
                    val bestThumbnail = runCatching { repository.resolveBestThumbnail(track) }.getOrDefault(track.thumbnailUrl)
                    if (bestThumbnail != track.thumbnailUrl && PlayerQueueManager.currentTrack.value?.id == track.id) {
                        val upgradedTrack = updatedTrack.copy(thumbnailUrl = bestThumbnail)
                        PlayerQueueManager.updateCurrentTrack(upgradedTrack)
                        withContext(Dispatchers.Main) {
                            val p = player
                            val curItem = p?.currentMediaItem
                            if (p != null && curItem != null && curItem.mediaId == track.id && p.mediaItemCount > 0) {
                                val newItem = curItem.buildUpon()
                                    .setMediaMetadata(buildTrackMediaMetadata(upgradedTrack))
                                    .build()
                                p.replaceMediaItem(p.currentMediaItemIndex, newItem)
                                syncNotificationAndWidgets()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing track: ${e.message}", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.playback_channel_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun restoreLastSessionAndPlay() {
        if (restoringSession) return
        restoringSession = true
        serviceScope.launch {
            try {
                val track = loadPersistedTrack(this@PlaybackService) ?: return@launch
                if (track.url.isBlank()) return@launch
                val quality = SettingsManager.getInstance().audioQuality.value
                val resolved = withContext(Dispatchers.IO) {
                    runCatching { repository.getAudioStream(track.url, quality) }.getOrNull()
                } ?: return@launch

                val metadata = buildTrackMediaMetadata(track)

                val builder = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(resolved.url)
                    .setMediaMetadata(metadata)
                if (!resolved.mimeType.isNullOrBlank()) {
                    builder.setMimeType(resolved.mimeType)
                }

                player?.run {
                    setMediaItem(builder.build())
                    prepare()
                    playWhenReady = true
                }
                syncNotificationAndWidgets()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to restore last session: ${e.message}")
            } finally {
                restoringSession = false
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = mediaSession?.player
        if (activePlayer == null || !activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        pauseTimeoutJob?.cancel()
        pauseTimeoutJob = null
        servicePlayJob?.cancel()
        servicePlayJob = null
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
