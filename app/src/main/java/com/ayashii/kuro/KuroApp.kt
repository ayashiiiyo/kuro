package com.ayashii.kuro

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.ayashii.kuro.data.auth.AuthManager
import com.ayashii.kuro.data.remote.KuroDownloader
import org.schabi.newpipe.extractor.NewPipe

class KuroApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        try {
            AuthManager.init(this)
        } catch (_: Throwable) {}
        try {
            val settings = com.ayashii.kuro.data.settings.SettingsManager.init(this)
            val savedLang = settings.language.value
            if (savedLang.code.isNotEmpty()) {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(savedLang.code)
                )
            }
        } catch (_: Throwable) {}
        try {
            val downloader = KuroDownloader.init(this)
            NewPipe.init(downloader)
        } catch (_: Throwable) {}
    }

    override fun newImageLoader(): ImageLoader {
        val is64BitRuntime = try {
            Process.is64Bit()
        } catch (_: Throwable) {
            false
        }
        val allowHardwareBitmaps = is64BitRuntime && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (is64BitRuntime) 0.08 else 0.06)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(40L * 1024 * 1024)
                    .build()
            }
            .allowHardware(allowHardwareBitmaps)
            .bitmapConfig(if (is64BitRuntime) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}
