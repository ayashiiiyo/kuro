package com.ayashii.kuro

import android.app.Application
import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import android.os.Process
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.ayashii.kuro.data.auth.AuthManager
import com.ayashii.kuro.data.remote.KuroDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.util.Locale
import java.util.concurrent.TimeUnit

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
                val locale = settings.resolveEffectiveLocale(savedLang)
                Locale.setDefault(locale)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val tag = if (savedLang.code == "in") "id" else savedLang.code
                    getSystemService(LocaleManager::class.java)?.applicationLocales =
                        LocaleList.forLanguageTags(tag)
                }
            }
        } catch (_: Throwable) {}
        try {
            val downloader = KuroDownloader.init(this)
            NewPipe.init(downloader)
        } catch (_: Throwable) {}
        try {
            AuthManager.getInstance().refreshAccountInfoIfNeeded(force = false)
        } catch (_: Throwable) {}
    }

    override fun newImageLoader(): ImageLoader {
        val is64BitRuntime = try {
            Process.is64Bit()
        } catch (_: Throwable) {
            false
        }
        val allowHardwareBitmaps = is64BitRuntime && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val imageHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                    )
                    .build()
                chain.proceed(req)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageHttpClient)
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
