package com.ayashii.kuro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ayashii.kuro.MainActivity
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.remote.MusicRepository
import com.ayashii.kuro.service.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val COMMAND_ACTION = "com.ayashii.kuro.widget.COMMAND"
const val WIDGET_SOURCE_URL_KEY = "kuro_source_url"
const val WIDGET_FAVORITES_CHANGED = "com.ayashii.kuro.widget.FAVORITES_CHANGED"
private const val PREFS = "kuro_widget_state"
private const val KEY_TRACK = "track"
private const val KEY_PLAYING = "playing"
private const val KEY_POSITION = "position"
private const val KEY_DURATION = "duration"

private object WidgetRenderer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var artworkLoadedFor: String? = null
    @Volatile private var artworkLoadInProgressFor: String? = null
    @Volatile private var artworkJob: Job? = null
    @Volatile private var cachedArtworkBitmap: Bitmap? = null
    @Volatile private var cachedAccentColor: Int = Color.parseColor("#1E1F24")

    fun update(context: Context) {
        try {
            updateInternal(context)
        } catch (_: Exception) {}
    }

    private fun updateInternal(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val compact = ComponentName(context, KuroCompactWidgetProvider::class.java)
        val square = ComponentName(context, KuroSquareWidgetProvider::class.java)
        val compactIds = manager.getAppWidgetIds(compact)
        val squareIds = manager.getAppWidgetIds(square)
        if (compactIds.isEmpty() && squareIds.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val track = prefs.getString(KEY_TRACK, null)?.let(::decodeTrack)
        if (track != null && artworkLoadedFor != track.id) {
            cachedArtworkBitmap = null
            cachedAccentColor = Color.parseColor("#1E1F24")
        }
        compactIds.forEach { id ->
            render(context, manager, id, false, track, prefs.getBoolean(KEY_PLAYING, false), prefs.getLong(KEY_POSITION, 0), prefs.getLong(KEY_DURATION, 0))
        }
        squareIds.forEach { id ->
            render(context, manager, id, true, track, prefs.getBoolean(KEY_PLAYING, false), prefs.getLong(KEY_POSITION, 0), prefs.getLong(KEY_DURATION, 0))
        }
        track?.let {
            if (artworkLoadedFor != it.id && artworkLoadInProgressFor != it.id) {
                artworkLoadInProgressFor = it.id
                loadArtwork(context, manager, compact, square, it)
            }
        }
    }

    private fun resolveWidgetDimensions(context: Context, manager: AppWidgetManager, id: Int, square: Boolean): Triple<Int, Int, Float> {
        val options = try { manager.getAppWidgetOptions(id) } catch (_: Exception) { null }
        val defaultW = if (square) 320 else 340
        val defaultH = if (square) 156 else 92
        val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: defaultW
        val maxW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)?.takeIf { it > 0 } ?: minW
        val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)?.takeIf { it > 0 } ?: defaultH
        val maxH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.takeIf { it > 0 } ?: minH
        val widthDp = ((minW + maxW) / 2f).coerceIn(140f, 520f)
        val heightDp = ((minH + maxH) / 2f).coerceIn(64f, 420f)
        val rawDensity = context.resources.displayMetrics.density.coerceIn(1.5f, 3.0f)
        val scale = rawDensity.coerceAtMost(2.25f)
        val widthPx = (widthDp * scale).toInt().coerceAtLeast(220)
        val heightPx = (heightDp * scale).toInt().coerceAtLeast(110)
        val cornerRadiusPx = 24f * scale
        return Triple(widthPx, heightPx, cornerRadiusPx)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int, square: Boolean, track: Track?, playing: Boolean, position: Long, duration: Long) {
        val views = RemoteViews(context.packageName, if (square) R.layout.widget_kuro_square else R.layout.widget_kuro_compact)
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
        }
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val title = track?.title?.takeIf(String::isNotBlank) ?: context.getString(R.string.widget_placeholder_title)
        val artist = track?.artist?.takeIf(String::isNotBlank) ?: context.getString(R.string.widget_placeholder_artist)
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        val currentCached = cachedArtworkBitmap
        val (wPx, hPx, radiusPx) = resolveWidgetDimensions(context, manager, id, square)
        if (currentCached != null && artworkLoadedFor == track?.id) {
            views.setInt(R.id.widget_art, "setBackgroundResource", 0)
            views.setImageViewBitmap(R.id.widget_art, currentCached)
            val backdrop = renderGradientBackdrop(wPx, hPx, radiusPx, cachedAccentColor)
            views.setImageViewBitmap(R.id.widget_bg_art, backdrop)
        } else {
            views.setInt(R.id.widget_art, "setBackgroundResource", R.drawable.widget_art_background)
            views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_background)
            val defaultBackdrop = renderGradientBackdrop(wPx, hPx, radiusPx, Color.parseColor("#18181D"))
            views.setImageViewBitmap(R.id.widget_bg_art, defaultBackdrop)
        }
        views.setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_kuro_pause else R.drawable.ic_kuro_play)
        views.setContentDescription(R.id.widget_play, context.getString(R.string.widget_play_desc))
        views.setContentDescription(R.id.widget_previous, context.getString(R.string.widget_previous_desc))
        views.setContentDescription(R.id.widget_next, context.getString(R.string.widget_next_desc))
        views.setProgressBar(R.id.widget_progress, 1000, if (duration > 0) ((position * 1000) / duration).toInt().coerceIn(0, 1000) else 0, false)
        setAction(context, views, R.id.widget_play, KuroWidgetActionReceiver.ACTION_PLAY_PAUSE, id * 10 + 1)
        setAction(context, views, R.id.widget_next, KuroWidgetActionReceiver.ACTION_NEXT, id * 10 + 2)
        if (!square) {
            val options = manager.getAppWidgetOptions(id)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val showPrevious = minWidth == 0 || minWidth >= 260
            views.setViewVisibility(R.id.widget_previous, if (showPrevious) View.VISIBLE else View.GONE)
            if (showPrevious) setAction(context, views, R.id.widget_previous, KuroWidgetActionReceiver.ACTION_PREVIOUS, id * 10 + 3)
        }
        if (square) {
            views.setImageViewResource(
                R.id.widget_favorite,
                if (track?.isFavorite == true) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart_outline
            )
            setAction(context, views, R.id.widget_previous, KuroWidgetActionReceiver.ACTION_PREVIOUS, id * 10 + 3)
            setAction(context, views, R.id.widget_favorite, KuroWidgetActionReceiver.ACTION_FAVORITE, id * 10 + 4)
        }
        manager.updateAppWidget(id, views)
    }

    private fun setAction(context: Context, views: RemoteViews, viewId: Int, action: String, requestCode: Int) {
        val intent = Intent(context, KuroWidgetActionReceiver::class.java).apply {
            this.action = COMMAND_ACTION
            putExtra("widget_command", action)
        }
        views.setOnClickPendingIntent(
            viewId,
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun loadArtwork(context: Context, manager: AppWidgetManager, compact: ComponentName, square: ComponentName, track: Track) {
        artworkJob?.cancel()
        artworkJob = scope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context).data(track.thumbnailUrlForDisplay(256)).allowHardware(false).build()
                val result = Coil.imageLoader(context).execute(request) as? SuccessResult
                val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
                if (bitmap == null) {
                    if (artworkLoadInProgressFor == track.id) artworkLoadInProgressFor = null
                    return@launch
                }
                val currentPersistedId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_TRACK, null)?.let(::decodeTrack)?.id
                if (currentPersistedId != null && currentPersistedId != track.id) {
                    if (artworkLoadInProgressFor == track.id) artworkLoadInProgressFor = null
                    return@launch
                }
                val rounded = roundedBitmap(bitmap)
                val accentColor = extractWidgetAccentColor(bitmap)
                cachedArtworkBitmap = rounded
                cachedAccentColor = accentColor
                artworkLoadedFor = track.id
                val compactIds = manager.getAppWidgetIds(compact)
                val squareIds = manager.getAppWidgetIds(square)
                (compactIds + squareIds).forEach { id ->
                    if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TRACK, null)?.let(::decodeTrack)?.id != track.id) return@forEach
                    val isSquare = id in squareIds
                    val (wPx, hPx, radiusPx) = resolveWidgetDimensions(context, manager, id, isSquare)
                    val views = RemoteViews(context.packageName, if (isSquare) R.layout.widget_kuro_square else R.layout.widget_kuro_compact)
                    views.setInt(R.id.widget_art, "setBackgroundResource", 0)
                    views.setImageViewBitmap(R.id.widget_art, rounded)
                    views.setImageViewBitmap(R.id.widget_bg_art, renderGradientBackdrop(wPx, hPx, radiusPx, accentColor))
                    manager.partiallyUpdateAppWidget(id, views)
                }
                if (artworkLoadInProgressFor == track.id) artworkLoadInProgressFor = null
            } catch (_: Exception) {
                if (artworkLoadInProgressFor == track.id) artworkLoadInProgressFor = null
            }
        }
    }

    private fun extractWidgetAccentColor(source: Bitmap): Int {
        return try {
            val palette = Palette.from(source).maximumColorCount(12).generate()
            palette.darkVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: Color.parseColor("#1E1F24")
        } catch (_: Exception) {
            Color.parseColor("#1E1F24")
        }
    }

    private fun renderGradientBackdrop(width: Int, height: Int, cornerRadius: Float, accentRgb: Int): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val r = (Color.red(accentRgb) * 0.44f).toInt().coerceIn(12, 115)
        val g = (Color.green(accentRgb) * 0.44f).toInt().coerceIn(12, 115)
        val b = (Color.blue(accentRgb) * 0.44f).toInt().coerceIn(14, 125)
        val startColor = Color.argb(248, r, g, b)
        val midColor = Color.argb(250, (r * 0.45f).toInt().coerceAtLeast(10), (g * 0.45f).toInt().coerceAtLeast(10), (b * 0.45f).toInt().coerceAtLeast(13))
        val endColor = Color.argb(252, 10, 10, 13)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(startColor, midColor, endColor),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        val strokeWidthPx = 2.2f
        val inset = strokeWidthPx * 0.5f
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = Color.argb(46, (r + 95).coerceAtMost(255), (g + 95).coerceAtMost(255), (b + 95).coerceAtMost(255))
        }
        canvas.drawRoundRect(
            RectF(inset, inset, width - inset, height - inset),
            (cornerRadius - inset).coerceAtLeast(0f),
            (cornerRadius - inset).coerceAtLeast(0f),
            strokePaint
        )
        return output
    }

    private fun roundedBitmap(source: Bitmap): Bitmap {
        val size = 192
        val radius = 38f
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val shader = android.graphics.BitmapShader(source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        val scale = maxOf(size.toFloat() / source.width, size.toFloat() / source.height)
        val dx = (size.toFloat() - source.width * scale) * 0.5f
        val dy = (size.toFloat() - source.height * scale) * 0.5f
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb(38, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(0.75f, 0.75f, size - 0.75f, size - 0.75f), radius - 0.75f, radius - 0.75f, borderPaint)
        return output
    }
}

class KuroCompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetRenderer.update(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) =
        WidgetRenderer.update(context)
}

class KuroSquareWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetRenderer.update(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) =
        WidgetRenderer.update(context)
}

class KuroWidgetActionReceiver : android.content.BroadcastReceiver() {
    companion object {
        private const val TAG = "KuroWidgetReceiver"
        const val ACTION_PLAY_PAUSE = "com.ayashii.kuro.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.ayashii.kuro.widget.NEXT"
        const val ACTION_PREVIOUS = "com.ayashii.kuro.widget.PREVIOUS"
        const val ACTION_FAVORITE = "com.ayashii.kuro.widget.FAVORITE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra("widget_command") ?: intent.action
        if (command == null) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)

        fun finishOnce() {
            if (!finished.compareAndSet(false, true)) return
            try {
                WidgetRenderer.update(appContext)
            } catch (_: Exception) {}
            try {
                pendingResult.finish()
            } catch (_: Exception) {}
        }

        handler.postDelayed({ finishOnce() }, 2500L)

        val future = try {
            val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            MediaController.Builder(appContext, token).buildAsync()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Unable to build MediaController: ${e.message}")
            handler.removeCallbacksAndMessages(null)
            finishOnce()
            return
        }

        future.addListener({
            try {
                val controller = future.get()
                when (command) {
                    ACTION_PLAY_PAUSE -> {
                        if (controller.playWhenReady) controller.pause() else controller.play()
                    }
                    ACTION_NEXT -> {
                        if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
                            controller.seekToNextMediaItem()
                        } else if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) {
                            controller.seekToNext()
                        }
                    }
                    ACTION_PREVIOUS -> {
                        if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
                            controller.seekToPreviousMediaItem()
                        } else if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) {
                            controller.seekToPrevious()
                        }
                    }
                    ACTION_FAVORITE -> toggleFavorite(appContext, controller)
                }
                persist(appContext, controller)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Widget command '$command' failed: ${e.message}")
            } finally {
                handler.postDelayed({
                    handler.removeCallbacksAndMessages(null)
                    try {
                        MediaController.releaseFuture(future)
                    } catch (_: Exception) {}
                    finishOnce()
                }, 350L)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun toggleFavorite(context: Context, controller: MediaController) {
        val metadata = controller.currentMediaItem?.mediaMetadata ?: return
        val prefs = context.getSharedPreferences("kuro_favorites", Context.MODE_PRIVATE)
        val array = try { org.json.JSONArray(prefs.getString("fav_list", "[]")) } catch (_: Exception) { org.json.JSONArray() }
        val id = controller.currentMediaItem?.mediaId ?: return
        val match = (0 until array.length()).firstOrNull { array.optJSONObject(it)?.optString("id") == id }
        val isNowLiked = match == null
        if (match != null) {
            val next = org.json.JSONArray()
            for (index in 0 until array.length()) if (index != match) next.put(array.getJSONObject(index))
            prefs.edit().putString("fav_list", next.toString()).apply()
        } else {
            array.put(org.json.JSONObject().apply {
                put("id", id)
                put("url", metadata.extras?.getString(WIDGET_SOURCE_URL_KEY)
                    ?: controller.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty())
                put("title", metadata.title?.toString().orEmpty())
                put("artist", metadata.artist?.toString().orEmpty())
                put("thumbnailUrl", metadata.artworkUri?.toString().orEmpty())
                put("durationSeconds", (controller.duration.coerceAtLeast(0) / 1000L))
            })
            prefs.edit().putString("fav_list", array.toString()).apply()
        }
        context.sendBroadcast(Intent(WIDGET_FAVORITES_CHANGED).setPackage(context.packageName))
        WidgetRenderer.scope.launch(Dispatchers.IO) {
            MusicRepository.getInstance().setTrackLikeStatus(id, isLiked = isNowLiked)
        }
    }
}

internal fun persist(context: Context, controller: Player) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val item = controller.currentMediaItem
    val metadata = item?.mediaMetadata
    if (item != null && metadata != null) {
        val track = Track(
            id = item.mediaId,
            url = metadata.extras?.getString(WIDGET_SOURCE_URL_KEY)
                ?: item.localConfiguration?.uri?.toString().orEmpty(),
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            thumbnailUrl = metadata.artworkUri?.toString().orEmpty(),
            durationSeconds = controller.duration.takeIf { it > 0 }?.div(1000L) ?: 0,
            isFavorite = isFavorite(context, item.mediaId)
        )
        prefs.edit().putString(KEY_TRACK, encodeTrack(track)).apply()
    }
    prefs.edit().putBoolean(KEY_PLAYING, controller.playWhenReady)
        .putLong(KEY_POSITION, controller.currentPosition.coerceAtLeast(0))
        .putLong(KEY_DURATION, controller.duration.coerceAtLeast(0)).apply()
}

private fun isFavorite(context: Context, id: String): Boolean {
    val raw = context.getSharedPreferences("kuro_favorites", Context.MODE_PRIVATE).getString("fav_list", "[]") ?: "[]"
    return try { val array = org.json.JSONArray(raw); (0 until array.length()).any { array.optJSONObject(it)?.optString("id") == id } } catch (_: Exception) { false }
}

private fun encodeTrack(track: Track) = org.json.JSONObject().apply {
    put("id", track.id); put("url", track.url); put("title", track.title); put("artist", track.artist)
    put("thumbnailUrl", track.thumbnailUrl); put("durationSeconds", track.durationSeconds); put("isFavorite", track.isFavorite)
}.toString()

private fun decodeTrack(raw: String): Track? = try {
    val data = org.json.JSONObject(raw)
    Track(data.getString("id"), data.optString("url"), data.optString("title"), data.optString("artist"), data.optString("thumbnailUrl"), data.optLong("durationSeconds"), isFavorite = data.optBoolean("isFavorite"))
} catch (_: Exception) { null }

fun updateKuroWidgets(context: Context) = WidgetRenderer.update(context)

fun loadPersistedTrack(context: Context): Track? =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_TRACK, null)
        ?.let(::decodeTrack)
