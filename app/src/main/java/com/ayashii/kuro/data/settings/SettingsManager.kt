package com.ayashii.kuro.data.settings

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.ayashii.kuro.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class AppLanguage(val code: String, val displayName: String, val hlCode: String, val glCode: String) {
    SYSTEM("", "System Default", "", ""),
    INDONESIAN("in", "Bahasa Indonesia", "id", "ID"),
    ENGLISH("en", "English", "en", "US"),
    JAPANESE("ja", "日本語 (Japanese)", "ja", "JP"),
    KOREAN("ko", "한국어 (Korean)", "ko", "KR"),
    CHINESE("zh", "中文 (Chinese)", "zh-CN", "SG"),
    SPANISH("es", "Español (Spanish)", "es", "ES"),
    FRENCH("fr", "Français (French)", "fr", "FR"),
    GERMAN("de", "Deutsch (German)", "de", "DE"),
    PORTUGUESE("pt", "Português (Portuguese)", "pt", "BR"),
    RUSSIAN("ru", "Русский (Russian)", "ru", "RU"),
    ARABIC("ar", "العربية (Arabic)", "ar", "SA")
}

enum class LyricsLanguage(val deeplCode: String, val googleCode: String, val displayName: String) {
    INDONESIAN("id", "id", "Bahasa Indonesia"),
    ENGLISH("en", "en", "English"),
    JAPANESE("ja", "ja", "日本語 (Japanese)"),
    KOREAN("ko", "ko", "한국어 (Korean)"),
    CHINESE("zh", "zh-CN", "中文 (Chinese)"),
    SPANISH("es", "es", "Español (Spanish)"),
    FRENCH("fr", "fr", "Français (French)"),
    GERMAN("de", "de", "Deutsch (German)"),
    PORTUGUESE("pt", "pt", "Português (Portuguese)"),
    RUSSIAN("ru", "ru", "Русский (Russian)"),
    ARABIC("ar", "ar", "العربية (Arabic)"),
    TURKISH("tr", "tr", "Türkçe (Turkish)"),
    ITALIAN("it", "it", "Italiano (Italian)"),
    DUTCH("nl", "nl", "Nederlands (Dutch)"),
    POLISH("pl", "pl", "Polski (Polish)"),
    SWEDISH("sv", "sv", "Svenska (Swedish)"),
    UKRAINIAN("uk", "uk", "Українська (Ukrainian)"),
    VIETNAMESE("vi", "vi", "Tiếng Việt (Vietnamese)"),
    THAI("th", "th", "ไทย (Thai)")
}

enum class AudioQuality(
    val title: String,
    val description: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int
) {
    HIGH(
        "High Quality (M4A / 256 kbps)",
        "Crisp sound, best audio fidelity",
        R.string.settings_quality_high_title,
        R.string.settings_quality_high_desc
    ),
    MEDIUM(
        "Normal Quality (128 kbps)",
        "Balanced speed & data usage",
        R.string.settings_quality_med_title,
        R.string.settings_quality_med_desc
    ),
    LOW(
        "Data Saver (64 kbps)",
        "Fastest buffer, minimal data usage",
        R.string.settings_quality_low_title,
        R.string.settings_quality_low_desc
    )
}

class SettingsManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _lyricsLanguage = MutableStateFlow(loadLyricsLanguage())
    val lyricsLanguage: StateFlow<LyricsLanguage> = _lyricsLanguage.asStateFlow()

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

    private fun loadLanguage(): AppLanguage {
        val code = prefs.getString(KEY_LANGUAGE, "") ?: ""
        if (code == "id") return AppLanguage.INDONESIAN
        return AppLanguage.values().firstOrNull { it.code == code } ?: AppLanguage.SYSTEM
    }

    private fun loadLyricsLanguage(): LyricsLanguage {
        val code = prefs.getString(KEY_LYRICS_LANGUAGE, "id") ?: "id"
        return LyricsLanguage.values().firstOrNull { it.deeplCode.equals(code, ignoreCase = true) }
            ?: LyricsLanguage.INDONESIAN
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

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        prefs.edit().putString(KEY_LANGUAGE, lang.code).commit()
        try {
            val locale = resolveEffectiveLocale(lang)
            Locale.setDefault(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (lang.code.isEmpty()) {
                    localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
                } else {
                    val tag = if (lang.code == "in") "id" else lang.code
                    localeManager?.applicationLocales = LocaleList.forLanguageTags(tag)
                }
            }
        } catch (_: Throwable) {}
    }

    fun setLyricsLanguage(lang: LyricsLanguage) {
        _lyricsLanguage.value = lang
        prefs.edit().putString(KEY_LYRICS_LANGUAGE, lang.deeplCode).apply()
    }

    fun setAudioQuality(quality: AudioQuality) {
        _audioQuality.value = quality
        prefs.edit().putString(KEY_AUDIO_QUALITY, quality.name).apply()
    }

    fun setAutoTranslate(enabled: Boolean) {
        _autoTranslate.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply()
    }

    fun resolveEffectiveLocale(lang: AppLanguage = _language.value): Locale {
        if (lang == AppLanguage.SYSTEM || lang.code.isEmpty()) {
            val sysLoc = android.content.res.Resources.getSystem().configuration.locales.get(0)
            return sysLoc ?: Locale.getDefault()
        }
        return if (lang.code == "in" || lang.code == "id") {
            Locale("in", "ID")
        } else {
            Locale(lang.code)
        }
    }

    fun createLocalizedContext(base: Context, lang: AppLanguage = _language.value): Context {
        return try {
            val locale = resolveEffectiveLocale(lang)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            base.createConfigurationContext(config)
        } catch (_: Throwable) {
            base
        }
    }

    fun getLocalizedString(@StringRes resId: Int, vararg formatArgs: Any): String {
        val localizedCtx = createLocalizedContext(context)
        return try {
            if (formatArgs.isEmpty()) {
                localizedCtx.getString(resId)
            } else {
                localizedCtx.getString(resId, *formatArgs)
            }
        } catch (_: Throwable) {
            context.getString(resId)
        }
    }

    fun getEffectiveHlAndGl(): Pair<String, String> {
        val lang = _language.value
        if (lang != AppLanguage.SYSTEM && lang.hlCode.isNotEmpty()) {
            return Pair(lang.hlCode, lang.glCode)
        }
        val sys = resolveEffectiveLocale(AppLanguage.SYSTEM)
        val l = sys.language.lowercase()
        return when (l) {
            "in", "id" -> Pair("id", "ID")
            "ja" -> Pair("ja", "JP")
            "ko" -> Pair("ko", "KR")
            "zh" -> Pair("zh-CN", "SG")
            "es" -> Pair("es", "ES")
            "fr" -> Pair("fr", "FR")
            "de" -> Pair("de", "DE")
            "pt" -> Pair("pt", "BR")
            "ru" -> Pair("ru", "RU")
            "ar" -> Pair("ar", "SA")
            else -> Pair("en", "US")
        }
    }

    fun getCacheSizeBytes(): Long {
        var total = 0L
        try {
            val cacheDirs = listOf(
                File(context.cacheDir, "lyrics_cache_v8"),
                File(context.cacheDir, "lyrics_cache_v7"),
                File(context.cacheDir, "lyrics_cache_v6"),
                File(context.cacheDir, "lyrics_cache_v5"),
                File(context.cacheDir, "lyrics_cache_v4"),
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
                File(context.cacheDir, "lyrics_cache_v8"),
                File(context.cacheDir, "lyrics_cache_v7"),
                File(context.cacheDir, "lyrics_cache_v6"),
                File(context.cacheDir, "lyrics_cache_v5"),
                File(context.cacheDir, "lyrics_cache_v4"),
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
                File(context.cacheDir, "lyrics_cache_v8"),
                File(context.cacheDir, "lyrics_cache_v7"),
                File(context.cacheDir, "lyrics_cache_v6"),
                File(context.cacheDir, "lyrics_cache_v5"),
                File(context.cacheDir, "lyrics_cache_v4"),
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
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_LYRICS_LANGUAGE = "lyrics_target_language"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_AUTO_TRANSLATE = "lyrics_auto_translate"

        @Volatile
        private var instance: SettingsManager? = null

        fun init(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                val safeCtx = context.applicationContext ?: context
                instance ?: SettingsManager(safeCtx).also { instance = it }
            }
        }

        fun getInstance(): SettingsManager {
            return checkNotNull(instance) { "SettingsManager must be initialized before use" }
        }
    }
}
