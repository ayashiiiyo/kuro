package com.ayashii.kuro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ayashii.kuro.MainActivity
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.service.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val COMMAND_ACTION = "com.ayashii.kuro.widget.COMMAND"
private const val PREFS = "kuro_widget_state"
private const val KEY_TRACK = "track"
private const val KEY_PLAYING = "playing"
private const val KEY_POSITION = "position"
private const val KEY_DURATION = "duration"

private object WidgetRenderer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var artworkLoadedFor: String? = null
    @Volatile private var artworkLoadInProgressFor: String? = null
    @Volatile private var cachedArtworkBitmap: Bitmap? = null

    fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val compact = ComponentName(context, KuroCompactWidgetProvider::class.java)
        val square = ComponentName(context, KuroSquareWidgetProvider::class.java)
        val compactIds = manager.getAppWidgetIds(compact)
        val squareIds = manager.getAppWidgetIds(square)
        if (compactIds.isEmpty() && squareIds.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val track = prefs.getString(KEY_TRACK, null)?.let(::decodeTrack)
        compactIds.forEach { id ->
            render(context, manager, id, false, track, prefs.getBoolean(KEY_PLAYING, false), prefs.getLong(KEY_POSITION, 0), prefs.getLong(KEY_DURATION, 0))
        }
        squareIds.forEach { id ->
            render(context, manager, id, true, track, prefs.getBoolean(KEY_PLAYING, false), prefs.getLong(KEY_POSITION, 0), prefs.getLong(KEY_DURATION, 0))
        }
        track?.let {
            if (artworkLoadedFor != it.id) {
                cachedArtworkBitmap = null
            }
            if (artworkLoadedFor != it.id && artworkLoadInProgressFor != it.id) {
                artworkLoadInProgressFor = it.id
                loadArtwork(context, manager, compact, square, it)
            }
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int, square: Boolean, track: Track?, playing: Boolean, position: Long, duration: Long) {
        val views = RemoteViews(context.packageName, if (square) R.layout.widget_kuro_square else R.layout.widget_kuro_compact)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val title = track?.title?.takeIf(String::isNotBlank) ?: "Buka Kuro untuk memutar"
        val artist = track?.artist?.takeIf(String::isNotBlank) ?: "Musikmu, selalu dekat"
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        val currentCached = cachedArtworkBitmap
        if (currentCached != null && artworkLoadedFor == track?.id) {
            views.setImageViewBitmap(R.id.widget_art, currentCached)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_background)
        }
        views.setTextViewText(R.id.widget_play, if (playing) "Ⅱ" else "▶")
        views.setProgressBar(R.id.widget_progress, 1000, if (duration > 0) ((position * 1000) / duration).toInt().coerceIn(0, 1000) else 0, false)
        setAction(context, views, R.id.widget_play, KuroWidgetActionReceiver.ACTION_PLAY_PAUSE, id * 10 + 1)
        setAction(context, views, R.id.widget_next, KuroWidgetActionReceiver.ACTION_NEXT, id * 10 + 2)
        if (!square) setAction(context, views, R.id.widget_previous, KuroWidgetActionReceiver.ACTION_PREVIOUS, id * 10 + 3)
        if (square) {
            views.setTextViewText(R.id.widget_favorite, if (track?.isFavorite == true) "♥" else "♡")
            setAction(context, views, R.id.widget_previous, KuroWidgetActionReceiver.ACTION_PREVIOUS, id * 10 + 3)
            setAction(context, views, R.id.widget_favorite, KuroWidgetActionReceiver.ACTION_FAVORITE, id * 10 + 4)
        }
        manager.updateAppWidget(id, views)
    }

    private fun setAction(context: Context, views: RemoteViews, viewId: Int, action: String, requestCode: Int) {
        val intent = Intent(context, KuroWidgetActionReceiver::class.java).setAction(COMMAND_ACTION).putExtra("widget_command", action)
        views.setOnClickPendingIntent(viewId, PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    private fun loadArtwork(context: Context, manager: AppWidgetManager, compact: ComponentName, square: ComponentName, track: Track) {
        scope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context).data(track.thumbnailUrlForDisplay(256)).allowHardware(false).build()
                val result = Coil.imageLoader(context).execute(request) as? SuccessResult
                val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
                if (bitmap == null) {
                    artworkLoadInProgressFor = null
                    return@launch
                }
                val rounded = roundedBitmap(bitmap)
                cachedArtworkBitmap = rounded
                artworkLoadedFor = track.id
                val compactIds = manager.getAppWidgetIds(compact)
                val squareIds = manager.getAppWidgetIds(square)
                (compactIds + squareIds).forEach { id ->
                    if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TRACK, null)?.let(::decodeTrack)?.id != track.id) return@forEach
                    val isSquare = id in squareIds
                    val views = RemoteViews(context.packageName, if (isSquare) R.layout.widget_kuro_square else R.layout.widget_kuro_compact)
                    views.setImageViewBitmap(R.id.widget_art, rounded)
                    manager.updateAppWidget(id, views)
                }
                artworkLoadInProgressFor = null
            } catch (_: Exception) {
                artworkLoadInProgressFor = null
            }
        }
    }

    private fun roundedBitmap(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val shader = android.graphics.BitmapShader(source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        val scale = maxOf(160f / source.width, 160f / source.height)
        val matrix = android.graphics.Matrix().apply { setScale(scale, scale) }
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        canvas.drawRoundRect(RectF(0f, 0f, 160f, 160f), 24f, 24f, paint)
        return output
    }
}

class KuroCompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetRenderer.update(context)
    override fun onReceive(context: Context, intent: Intent) { super.onReceive(context, intent); WidgetRenderer.update(context) }
}

class KuroSquareWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetRenderer.update(context)
    override fun onReceive(context: Context, intent: Intent) { super.onReceive(context, intent); WidgetRenderer.update(context) }
}

class KuroWidgetActionReceiver : android.content.BroadcastReceiver() {
    companion object {
        const val ACTION_PLAY_PAUSE = "com.ayashii.kuro.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.ayashii.kuro.widget.NEXT"
        const val ACTION_PREVIOUS = "com.ayashii.kuro.widget.PREVIOUS"
        const val ACTION_FAVORITE = "com.ayashii.kuro.widget.FAVORITE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                when (intent.getStringExtra("widget_command") ?: intent.action) {
                    ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                    ACTION_NEXT -> { if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) controller.seekToNextMediaItem() else controller.seekToNext() }
                    ACTION_PREVIOUS -> { if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) controller.seekToPreviousMediaItem() else controller.seekToPrevious() }
                    ACTION_FAVORITE -> toggleFavorite(context, controller)
                }
                persist(context, controller)
                WidgetRenderer.update(context)
            } catch (_: Exception) {
                WidgetRenderer.update(context)
            } finally {
                MediaController.releaseFuture(future)
                pendingResult.finish()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun toggleFavorite(context: Context, controller: MediaController) {
        val metadata = controller.currentMediaItem?.mediaMetadata ?: return
        val prefs = context.getSharedPreferences("kuro_favorites", Context.MODE_PRIVATE)
        val array = try { org.json.JSONArray(prefs.getString("fav_list", "[]")) } catch (_: Exception) { org.json.JSONArray() }
        val id = controller.currentMediaItem?.mediaId ?: return
        val match = (0 until array.length()).firstOrNull { array.optJSONObject(it)?.optString("id") == id }
        if (match != null) {
            val next = org.json.JSONArray()
            for (index in 0 until array.length()) if (index != match) next.put(array.getJSONObject(index))
            prefs.edit().putString("fav_list", next.toString()).apply()
        } else {
            array.put(org.json.JSONObject().apply {
                put("id", id)
                put("url", controller.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty())
                put("title", metadata.title?.toString().orEmpty())
                put("artist", metadata.artist?.toString().orEmpty())
                put("thumbnailUrl", metadata.artworkUri?.toString().orEmpty())
                put("durationSeconds", (controller.duration.coerceAtLeast(0) / 1000L))
            })
            prefs.edit().putString("fav_list", array.toString()).apply()
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
            url = item.localConfiguration?.uri?.toString().orEmpty(),
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            thumbnailUrl = metadata.artworkUri?.toString().orEmpty(),
            durationSeconds = controller.duration.takeIf { it > 0 }?.div(1000L) ?: 0,
            isFavorite = isFavorite(context, item.mediaId)
        )
        prefs.edit().putString(KEY_TRACK, encodeTrack(track)).apply()
    }
    prefs.edit().putBoolean(KEY_PLAYING, controller.isPlaying)
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
