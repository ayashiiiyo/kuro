package com.ayashii.kuro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ayashii.kuro.MainActivity
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.player.PlayerQueueManager
import com.ayashii.kuro.data.recommendation.TasteManager
import com.ayashii.kuro.data.remote.MusicRepository
import com.ayashii.kuro.data.settings.SettingsManager
import com.ayashii.kuro.widget.WIDGET_SOURCE_URL_KEY
import com.ayashii.kuro.widget.loadPersistedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : MediaSessionService() {

    private var player: Player? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var restoringSession = false
    private var pauseTimeoutJob: Job? = null

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
                        MusicRepository().evictStreamUrl(current.url)
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
                } else if (playbackState == Player.STATE_ENDED) {
                    val current = PlayerQueueManager.currentTrack.value
                    if (current != null) {
                        TasteManager.getInstance(this@PlaybackService).recordTrackPlayed(current)
                    }
                    val nextTrack = PlayerQueueManager.getNextTrack(userInitiated = false)
                    if (nextTrack != null) {
                        playTrack(nextTrack)
                    }
                }
                updatePauseTimeout(exoPlayer)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updatePauseTimeout(exoPlayer)
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
        serviceScope.launch {
            try {
                val quality = SettingsManager.getInstance().audioQuality.value
                val repository = MusicRepository()
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

                val bestThumbnail = withContext(Dispatchers.IO) {
                    runCatching { repository.resolveBestThumbnail(track) }.getOrDefault(track.thumbnailUrl)
                }

                val updatedTrack = track.copy(
                    streamUrl = resolved.url,
                    mimeType = resolved.mimeType,
                    thumbnailUrl = bestThumbnail
                )
                PlayerQueueManager.updateCurrentTrack(updatedTrack)
                PlayerQueueManager.recordPlaybackSuccess()

                val metadata = MediaMetadata.Builder()
                    .setTitle(updatedTrack.title)
                    .setArtist(updatedTrack.artist)
                    .setArtworkUri(Uri.parse(updatedTrack.highResThumbnailUrl))
                    .setDisplayTitle(updatedTrack.title)
                    .setExtras(android.os.Bundle().apply {
                        putString(WIDGET_SOURCE_URL_KEY, updatedTrack.url)
                    })
                    .build()

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
                    runCatching { MusicRepository().getAudioStream(track.url, quality) }.getOrNull()
                } ?: return@launch

                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(Uri.parse(track.highResThumbnailUrl))
                    .setDisplayTitle(track.title)
                    .setExtras(android.os.Bundle().apply {
                        putString(WIDGET_SOURCE_URL_KEY, track.url)
                    })
                    .build()

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
