package com.ayashii.kuro

import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.ayashii.kuro.data.auth.AuthManager
import com.ayashii.kuro.data.remote.KuroDownloader
import org.schabi.newpipe.extractor.NewPipe

class KuroApp : Application(), ImageLoaderFactory {

    private var customImageLoader: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        AuthManager.init(this)
        val settings = com.ayashii.kuro.data.settings.SettingsManager.init(this)
        try {
            val savedLang = settings.language.value
            if (savedLang.code.isNotEmpty()) {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(savedLang.code)
                )
            }
        } catch (_: Exception) {}
        val downloader = KuroDownloader.init(this)
        NewPipe.init(downloader)
    }

    override fun newImageLoader(): ImageLoader {
        return customImageLoader ?: synchronized(this) {
            customImageLoader ?: ImageLoader.Builder(this)
                // Cap memory cache to 8% to preserve heap for low-RAM devices (2-4GB)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.08)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(40L * 1024 * 1024)
                        .build()
                }
                // RGB_565 halves memory footprint per bitmap compared to ARGB_8888
                .bitmapConfig(Bitmap.Config.RGB_565)
                .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                .allowRgb565(true)
                .networkCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .respectCacheHeaders(false)
                .build()
                .also { customImageLoader = it }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                customImageLoader?.memoryCache?.clear()
            } else if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                customImageLoader?.memoryCache?.trimToSize(
                    (customImageLoader?.memoryCache?.size ?: 0) / 2
                )
            }
        } catch (_: Exception) {}
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            customImageLoader?.memoryCache?.clear()
        } catch (_: Exception) {}
    }
}
