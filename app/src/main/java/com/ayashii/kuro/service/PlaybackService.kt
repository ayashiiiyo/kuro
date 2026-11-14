package com.ayashii.kuro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.ayashii.kuro.widget.WIDGET_FAVORITES_CHANGED
import com.google.common.util.concurrent.Futures
import org.json.JSONArray
import org.json.JSONObject
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ayashii.kuro.MainActivity
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.player.PlayerQueueManager
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.ayashii.kuro.data.auth.AuthManager
import com.ayashii.kuro.data.cache.HistoryStore
import com.ayashii.kuro.data.offline.OfflineMusicManager
import java.io.File
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
    private val inFlightRequests = HashMap<String, ListenableFuture<Bitmap>>()

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch(Dispatchers.IO) {
            try {
                val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bmp != null) {
                    future.set(badgeNotificationArtwork(bmp))
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
            inFlightRequests[url]?.let { existing ->
                if (!existing.isDone) {
                    return existing
                }
            }
            inFlightRequests[url] = future
        }
        scope.launch(Dispatchers.IO) {
            try {
                val loaded = fetchBitmapWithFallback(url)
                if (loaded != null) {
                    val badged = badgeNotificationArtwork(loaded)
                    synchronized(bitmapCache) {
                        bitmapCache.put(url, badged)
                        inFlightRequests.remove(url)
                    }
                    future.set(badged)
                } else {
                    synchronized(bitmapCache) {
                        inFlightRequests.remove(url)
                    }
                    future.setException(IllegalStateException("Artwork load returned null for $url"))
                }
            } catch (t: Throwable) {
                synchronized(bitmapCache) {
                    inFlightRequests.remove(url)
                }
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

    /**
     * Composites a sleek Kuro logo badge onto the notification artwork thumbnail
     * (similar to Spotify's branded badge on media notification cards).
     */
    private fun badgeNotificationArtwork(source: Bitmap): Bitmap {
        return try {
            val size = 512
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val scale = maxOf(size.toFloat() / source.width, size.toFloat() / source.height)
            val dx = (size.toFloat() - source.width * scale) * 0.5f
            val dy = (size.toFloat() - source.height * scale) * 0.5f
            val matrix = android.graphics.Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            canvas.drawBitmap(source, matrix, imgPaint)

            val badgeRadius = 38f
            val cx = size - 54f
            val cy = size - 54f

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(222, 9, 9, 11)
            }
            canvas.drawCircle(cx, cy, badgeRadius, bgPaint)

            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
                color = Color.argb(95, 255, 255, 255)
            }
            canvas.drawCircle(cx, cy, badgeRadius - 1.1f, ringPaint)

            canvas.save()
            val logoScale = (badgeRadius * 1.48f) / 68f
            canvas.translate(cx - 34f * logoScale, cy - 34f * logoScale)
            canvas.scale(logoScale, logoScale)
            canvas.translate(-21f, -20f)

            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3.2f
                strokeCap = Paint.Cap.ROUND
                color = Color.argb(185, 212, 212, 216)
            }
            canvas.drawArc(RectF(30f, 28f, 82f, 80f), -90f, 180f, false, arcPaint)

            val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5.4f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Color.argb(240, 228, 228, 231)
            }
            val wavePath = Path().apply {
                moveTo(29f, 56f)
                cubicTo(33f, 56f, 35f, 48f, 39f, 48f)
                cubicTo(43f, 48f, 44f, 64f, 48f, 64f)
                cubicTo(51f, 64f, 53f, 54f, 57f, 54f)
                lineTo(71f, 69f)
            }
            canvas.drawPath(wavePath, wavePaint)

            val kPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5.8f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Color.WHITE
            }
            val kPath = Path().apply {
                moveTo(48f, 37f)
                lineTo(48f, 71f)
                moveTo(52f, 55f)
                cubicTo(57f, 49f, 63f, 43f, 71f, 38f)
            }
            canvas.drawPath(kPath, kPaint)

            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }
            canvas.drawCircle(71f, 38f, 3.0f, dotPaint)
            canvas.restore()

            out
        } catch (_: Throwable) {
            source
        }
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
    private val favSyncReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WIDGET_FAVORITES_CHANGED) {
                updateNotificationCustomLayout()
            }
        }
    }

    companion object {
        private const val TAG = "KuroPlaybackService"
        private const val PAUSE_TIMEOUT_MS = 5 * 60 * 1000L
        const val CHANNEL_ID = "kuro_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.ayashii.kuro.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.ayashii.kuro.ACTION_NEXT"
        const val ACTION_PREV = "com.ayashii.kuro.ACTION_PREV"
        const val CUSTOM_ACTION_FAVORITE = "com.ayashii.kuro.CUSTOM_ACTION_FAVORITE"
        const val CUSTOM_ACTION_REPEAT = "com.ayashii.kuro.CUSTOM_ACTION_REPEAT"
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

        val streamCacheDir = File(cacheDir, "exo_stream_cache").apply { mkdirs() }
        val streamCache = SimpleCache(
            streamCacheDir,
            LeastRecentlyUsedCacheEvictor(128L * 1024L * 1024L),
            StandaloneDatabaseProvider(this)
        )
        streamSimpleCache = streamCache
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory, extractorsFactory)

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
                val failedTrackId = current?.id
                servicePlayJob?.cancel()
                servicePlayJob = serviceScope.launch {
                    delay(650L)
                    if (PlayerQueueManager.currentTrack.value?.id == failedTrackId &&
                        exoPlayer.playbackState == Player.STATE_IDLE && !exoPlayer.isPlaying
                    ) {
                        val failureCount = PlayerQueueManager.recordPlaybackFailure()
                        if (failureCount < 3) {
                            val nextTrack = PlayerQueueManager.getNextTrack(userInitiated = false, isErrorRecovery = true)
                            if (nextTrack != null) {
                                playTrack(nextTrack)
                            }
                        }
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
                        HistoryStore.getInstance(this@PlaybackService).recordTrackPlayed(current)
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
                    } else {
                        advanceToNextTrack(userInitiated = false)
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
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> PlayerQueueManager.hasNext() || mediaItemCount > 0
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> PlayerQueueManager.hasPrevious() || mediaItemCount > 0
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun hasNextMediaItem(): Boolean = true

            override fun hasPreviousMediaItem(): Boolean = true

            override fun seekToNext() {
                advanceToNextTrack(userInitiated = true)
            }

            override fun seekToNextMediaItem() {
                seekToNext()
            }

            override fun seekToPrevious() {
                handlePreviousOrRestart()
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
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(CUSTOM_ACTION_FAVORITE, Bundle.EMPTY))
                        .add(SessionCommand(CUSTOM_ACTION_REPEAT, Bundle.EMPTY))
                        .build()
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .setAvailablePlayerCommands(playerCommands)
                        .setCustomLayout(buildNotificationCustomLayout())
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CUSTOM_ACTION_FAVORITE -> {
                            toggleCurrentTrackFavoriteFromNotification()
                            updateNotificationCustomLayout()
                        }
                        CUSTOM_ACTION_REPEAT -> {
                            PlayerQueueManager.toggleRepeat()
                            val nextRepeat = PlayerQueueManager.repeatMode.value
                            player?.repeatMode = if (nextRepeat == Player.REPEAT_MODE_ONE) {
                                Player.REPEAT_MODE_ONE
                            } else {
                                Player.REPEAT_MODE_OFF
                            }
                            updateNotificationCustomLayout()
                            syncNotificationAndWidgets()
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
        updateNotificationCustomLayout()

        serviceScope.launch {
            PlayerQueueManager.repeatMode.collect {
                updateNotificationCustomLayout()
            }
        }
        serviceScope.launch {
            PlayerQueueManager.currentTrack.collect {
                updateNotificationCustomLayout()
            }
        }
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                favSyncReceiver,
                android.content.IntentFilter(WIDGET_FAVORITES_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    private fun isCurrentTrackFavorite(): Boolean {
        val current = PlayerQueueManager.currentTrack.value
        val mediaId = current?.id?.takeIf { it.isNotBlank() }
            ?: player?.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
            ?: return false
        val prefs = getSharedPreferences("kuro_favorites", Context.MODE_PRIVATE)
        val raw = prefs.getString("fav_list", null) ?: return current?.isFavorite == true
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).any { arr.optJSONObject(it)?.optString("id") == mediaId }
        } catch (_: Exception) {
            current?.isFavorite == true
        }
    }

    private fun buildNotificationCustomLayout(): List<CommandButton> {
        val isFav = isCurrentTrackFavorite()
        val favButton = CommandButton.Builder()
            .setDisplayName(getString(if (isFav) R.string.player_saved else R.string.player_save))
            .setIconResId(if (isFav) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart_outline)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_FAVORITE, Bundle.EMPTY))
            .build()

        val repeatMode = PlayerQueueManager.repeatMode.value
        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_notif_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_notif_repeat_all
            else -> R.drawable.ic_notif_repeat_off
        }
        val repeatButton = CommandButton.Builder()
            .setDisplayName("Repeat")
            .setIconResId(repeatIcon)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_REPEAT, Bundle.EMPTY))
            .build()

        return listOf(favButton, repeatButton)
    }

    private fun updateNotificationCustomLayout() {
        val session = mediaSession ?: return
        session.setCustomLayout(buildNotificationCustomLayout())
    }

    private fun toggleCurrentTrackFavoriteFromNotification() {
        val activePlayer = player
        val current = PlayerQueueManager.currentTrack.value
        val persisted = loadPersistedTrack(this)
        val metadata = activePlayer?.currentMediaItem?.mediaMetadata
        val id = current?.id?.takeIf { it.isNotBlank() }
            ?: activePlayer?.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
            ?: persisted?.id
            ?: return

        val prefs = getSharedPreferences("kuro_favorites", Context.MODE_PRIVATE)
        val array = try { JSONArray(prefs.getString("fav_list", "[]")) } catch (_: Exception) { JSONArray() }
        val match = (0 until array.length()).firstOrNull { array.optJSONObject(it)?.optString("id") == id }
        val isNowLiked = match == null
        if (match != null) {
            val next = JSONArray()
            for (index in 0 until array.length()) {
                if (index != match) next.put(array.getJSONObject(index))
            }
            prefs.edit().putString("fav_list", next.toString()).apply()
        } else {
            val newEntry = JSONObject().apply {
                put("id", id)
                put("url", current?.url?.takeIf { it.isNotBlank() }
                    ?: metadata?.extras?.getString(WIDGET_SOURCE_URL_KEY)
                    ?: activePlayer?.currentMediaItem?.localConfiguration?.uri?.toString()
                    ?: persisted?.url.orEmpty())
                put("title", current?.title?.takeIf { it.isNotBlank() }
                    ?: metadata?.title?.toString()
                    ?: persisted?.title.orEmpty())
                put("artist", current?.artist?.takeIf { it.isNotBlank() }
                    ?: metadata?.artist?.toString()
                    ?: persisted?.artist.orEmpty())
                put("thumbnailUrl", current?.thumbnailUrl?.takeIf { it.isNotBlank() }
                    ?: metadata?.artworkUri?.toString()
                    ?: persisted?.thumbnailUrl.orEmpty())
                put("durationSeconds", current?.durationSeconds?.takeIf { it > 0L }
                    ?: (activePlayer?.duration?.takeIf { it > 0 }?.div(1000L))
                    ?: persisted?.durationSeconds ?: 0L)
            }
            val next = JSONArray()
            next.put(newEntry)
            for (index in 0 until array.length()) {
                next.put(array.getJSONObject(index))
            }
            prefs.edit().putString("fav_list", next.toString()).apply()
        }

        if (current != null && current.id == id) {
            PlayerQueueManager.updateCurrentTrack(current.copy(isFavorite = isNowLiked))
        }
        sendBroadcast(Intent(WIDGET_FAVORITES_CHANGED).setPackage(packageName))
        syncNotificationAndWidgets()
        serviceScope.launch(Dispatchers.IO) {
            repository.setTrackLikeStatus(id, isLiked = isNowLiked)
        }
    }

    private fun syncNotificationAndWidgets() {
        val activePlayer = player ?: return
        updateNotificationCustomLayout()
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

    private var lastPreviousTapWallMs = 0L

    private fun handlePreviousOrRestart() {
        val activePlayer = player ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val isDoubleTap = (now - lastPreviousTapWallMs) <= 3500L
        lastPreviousTapWallMs = now

        if (!isDoubleTap && activePlayer.currentPosition > 3500L) {
            activePlayer.seekTo(0L)
            syncNotificationAndWidgets()
            return
        }

        val prevTrack = PlayerQueueManager.getPreviousTrack()
        if (prevTrack != null) {
            playTrack(prevTrack)
        } else {
            activePlayer.seekTo(0L)
            if (!activePlayer.playWhenReady) {
                activePlayer.play()
            }
            syncNotificationAndWidgets()
        }
    }

    private fun advanceToNextTrack(userInitiated: Boolean, isErrorRecovery: Boolean = false) {
        val nextTrack = PlayerQueueManager.getNextTrack(userInitiated = userInitiated, isErrorRecovery = isErrorRecovery)
        if (nextTrack != null) {
            playTrack(nextTrack)
            return
        }
        val current = PlayerQueueManager.currentTrack.value ?: return
        servicePlayJob?.cancel()
        servicePlayJob = serviceScope.launch {
            try {
                val radioTracks = withContext(Dispatchers.IO) {
                    repository.getRadioTracks(current.id)
                }.filter { it.id != current.id }
                if (radioTracks.isNotEmpty()) {
                    val ranked = radioTracks
                    ranked.forEach { PlayerQueueManager.addToQueue(it) }
                    val fetchedNext = PlayerQueueManager.getNextTrack(userInitiated = userInitiated, isErrorRecovery = isErrorRecovery)
                    if (fetchedNext != null) {
                        playTrack(fetchedNext)
                        return@launch
                    }
                }
                val fallbackPool = withContext(Dispatchers.IO) {
                    repository.getTrendingTracks()
                }.filter { it.id != current.id }
                if (fallbackPool.isNotEmpty()) {
                    fallbackPool.forEach { PlayerQueueManager.addToQueue(it) }
                    val fallbackNext = PlayerQueueManager.getNextTrack(userInitiated = userInitiated, isErrorRecovery = isErrorRecovery)
                    if (fallbackNext != null) {
                        playTrack(fallbackNext)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error advancing to next radio track: ${e.message}")
            }
        }
    }

    private fun playTrack(track: Track) {
        servicePlayJob?.cancel()
        PlayerQueueManager.updateCurrentTrack(track)
        servicePlayJob = serviceScope.launch {
            try {
                val quality = SettingsManager.getInstance().audioQuality.value
                val offlineUri = OfflineMusicManager.getInstance(this@PlaybackService).getLocalAudioUri(track.id)
                val resolved = if (offlineUri != null) {
                    com.ayashii.kuro.data.remote.ResolvedStream(url = offlineUri, mimeType = null)
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching { repository.getAudioStream(track.url, quality) }.getOrNull()
                    }
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
                HistoryStore.getInstance(this@PlaybackService).recordTrackPlayed(updatedTrack)
                if (offlineUri == null && AuthManager.getInstance().isLoggedIn()) {
                    launch(Dispatchers.IO) {
                        runCatching { repository.reportPlaybackToYouTubeMusic(updatedTrack.id) }
                    }
                }

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

                launch(Dispatchers.IO) {
                    try {
                        val queueSnapshot = PlayerQueueManager.queueState.value
                        val curIdx = PlayerQueueManager.currentIndex.value
                        val remaining = queueSnapshot.size - curIdx
                        if (queueSnapshot.size <= 2 || remaining <= 3) {
                            val radioTracks = repository.getRadioTracks(track.id)
                            if (radioTracks.isNotEmpty()) {
                                val currentQ = PlayerQueueManager.queueState.value
                                val existingIds = currentQ.mapTo(HashSet()) { it.id }
                                val existingTitles = currentQ.mapNotNullTo(HashSet()) {
                                    com.ayashii.kuro.data.native.KuroNativeCore.normalizeSongTitleKey(it.title).takeIf(String::isNotEmpty)
                                }
                                val uniqueRadio = radioTracks.filter { cand ->
                                    if (cand.id in existingIds) false
                                    else {
                                        val tk = com.ayashii.kuro.data.native.KuroNativeCore.normalizeSongTitleKey(cand.title)
                                        tk.isEmpty() || existingTitles.add(tk)
                                    }
                                }
                                uniqueRadio.forEach { PlayerQueueManager.addToQueue(it) }
                            }
                        }
                        val updatedQueue = PlayerQueueManager.queueState.value
                        val nextIdx = PlayerQueueManager.currentIndex.value + 1
                        if (nextIdx in updatedQueue.indices) {
                            repository.getAudioStreamUrl(updatedQueue[nextIdx].url, quality)
                        }
                    } catch (_: Exception) {}
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
                if (PlayerQueueManager.queueState.value.isEmpty()) {
                    PlayerQueueManager.playTrack(track, listOf(track))
                }
                val quality = SettingsManager.getInstance().audioQuality.value
                val offlineUri = OfflineMusicManager.getInstance(this@PlaybackService).getLocalAudioUri(track.id)
                val resolved = if (offlineUri != null) {
                    com.ayashii.kuro.data.remote.ResolvedStream(url = offlineUri, mimeType = null)
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching { repository.getAudioStream(track.url, quality) }.getOrNull()
                    }
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
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> player?.let { if (it.playWhenReady) it.pause() else it.play() }
            ACTION_NEXT -> player?.seekToNext()
            ACTION_PREV -> player?.seekToPrevious()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = mediaSession?.player
        if (activePlayer == null || !activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(favSyncReceiver)
        } catch (_: Exception) {}
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
        runCatching { streamSimpleCache?.release() }
        streamSimpleCache = null
        super.onDestroy()
    }
}
