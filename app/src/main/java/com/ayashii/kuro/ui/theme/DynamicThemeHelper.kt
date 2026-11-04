package com.ayashii.kuro.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dynamic theme helper that extracts ambient dominant colors from album artwork
 * using AndroidX Palette with fast 32x32 downsampling and in-memory LRU caching.
 */
object DynamicThemeHelper {
    private val colorCache = LruCache<String, Color>(64)

    fun getCachedColor(imageUrl: String?): Color? {
        if (imageUrl.isNullOrBlank()) return null
        return colorCache.get(imageUrl)
    }

    fun extractDominantColorFast(imageUrl: String?): Color? = getCachedColor(imageUrl)

    suspend fun extractDominantColor(context: Context, imageUrl: String?): Color = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) return@withContext DarkCard

        colorCache.get(imageUrl)?.let { return@withContext it }

        try {
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(32, 32)
                .allowHardware(false) // Mandatory: prevent Hardware Bitmap exception in Palette
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val rawBitmap = result.drawable.toBitmap()
                val scaledBitmap = if (rawBitmap.width > 32 || rawBitmap.height > 32) {
                    Bitmap.createScaledBitmap(rawBitmap, 32, 32, false)
                } else {
                    rawBitmap
                }

                val softwareBitmap = if (scaledBitmap.config == Bitmap.Config.HARDWARE) {
                    scaledBitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    scaledBitmap
                }

                val palette = Palette.from(softwareBitmap)
                    .maximumColorCount(16)
                    .generate()

                val swatch = palette.darkVibrantSwatch
                    ?: palette.dominantSwatch
                    ?: palette.vibrantSwatch
                    ?: palette.mutedSwatch

                val extractedColor = swatch?.let { Color(it.rgb) } ?: DarkCard
                colorCache.put(imageUrl, extractedColor)
                return@withContext extractedColor
            }
        } catch (_: Exception) {
            // Fallback gracefully on network, decoding or palette errors
        }

        DarkCard
    }
}

/**
 * Composable helper that remembers and animates the dominant album artwork color,
 * providing a smooth 450ms crossfade between track transitions.
 */
@Composable
fun rememberDynamicDominantColor(
    imageUrl: String?,
    defaultColor: Color = DarkCard
): Color {
    val context = LocalContext.current
    var dominantColor by remember(imageUrl) {
        mutableStateOf(DynamicThemeHelper.getCachedColor(imageUrl) ?: defaultColor)
    }

    LaunchedEffect(imageUrl) {
        if (!imageUrl.isNullOrBlank()) {
            dominantColor = DynamicThemeHelper.extractDominantColor(context, imageUrl)
        } else {
            dominantColor = defaultColor
        }
    }

    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "dynamic_ambient_color"
    )

    return animatedColor
}
