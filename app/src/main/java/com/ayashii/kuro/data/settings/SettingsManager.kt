package com.ayashii.kuro.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class AudioQuality(val title: String, val description: String) {
    HIGH("High Quality (M4A / 256 kbps)", "Crisp sound, best audio fidelity"),
    MEDIUM("Normal Quality (128 kbps)", "Balanced speed & data usage"),
    LOW("Data Saver (64 kbps)", "Fastest buffer, minimal data usage")
}

class SettingsManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _audioQuality = MutableStateFlow(loadAudioQuality())
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()

    private val _autoTranslate = MutableStateFlow(prefs.getBoolean(KEY_AUTO_TRANSLATE, true))
    val autoTranslate: StateFlow<Boolean> = _autoTranslate.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    private fun loadAudioQuality(): AudioQuality {
        val name = prefs.getString(KEY_AUDIO_QUALITY, AudioQuality.HIGH.name) ?: AudioQuality.HIGH.name
        return try {
            AudioQuality.valueOf(name)
        } catch (_: Exception) {
            AudioQuality.HIGH
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setAudioQuality(quality: AudioQuality) {
        _audioQuality.value = quality
        prefs.edit().putString(KEY_AUDIO_QUALITY, quality.name).apply()
    }

    fun setAutoTranslate(enabled: Boolean) {
        _autoTranslate.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply()
    }

    fun getCacheSizeBytes(): Long {
        var total = 0L
        try {
            val cacheDirs = listOf(
                File(context.cacheDir, "lyrics_cache_v3"),
                File(context.cacheDir, "lyrics_cache_v2"),
                File(context.cacheDir, "lyrics_cache"),
                File(context.cacheDir, "http_cache"),
                File(context.cacheDir, "image_cache")
            )
            cacheDirs.forEach { dir ->
                if (dir.exists()) {
                    total += calculateDirSize(dir)
                }
            }
        } catch (_: Exception) {}
        return total
    }

    private fun calculateDirSize(dir: File): Long {
        var bytes = 0L
        dir.listFiles()?.forEach { file ->
            bytes += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return bytes
    }

    suspend fun getFormattedCacheSizeAsync(): String = withContext(Dispatchers.IO) {
        val bytes = getCacheSizeBytes()
        if (bytes <= 0) return@withContext "0.0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        String.format("%.1f MB", mb)
    }

    fun getFormattedCacheSize(): String {
        val bytes = getCacheSizeBytes()
        if (bytes <= 0) return "0.0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }

    suspend fun clearCacheAsync(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheDirs = listOf(
                File(context.cacheDir, "lyrics_cache_v3"),
                File(context.cacheDir, "lyrics_cache_v2"),
                File(context.cacheDir, "lyrics_cache"),
                File(context.cacheDir, "image_cache"),
                File(context.cacheDir, "http_cache")
            )
            cacheDirs.forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.name != "journal" && !file.name.endsWith(".lock")) {
                            file.deleteRecursively()
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearCache(): Boolean {
        return try {
            val cacheDirs = listOf(
                File(context.cacheDir, "lyrics_cache_v3"),
                File(context.cacheDir, "lyrics_cache_v2"),
                File(context.cacheDir, "lyrics_cache"),
                File(context.cacheDir, "image_cache"),
                File(context.cacheDir, "http_cache")
            )
            cacheDirs.forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.name != "journal" && !file.name.endsWith(".lock")) {
                            file.deleteRecursively()
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val PREF_NAME = "kuro_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_AUTO_TRANSLATE = "lyrics_auto_translate"

        @Volatile
        private var instance: SettingsManager? = null

        fun init(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): SettingsManager {
            return checkNotNull(instance) { "SettingsManager must be initialized before use" }
        }
    }
}
