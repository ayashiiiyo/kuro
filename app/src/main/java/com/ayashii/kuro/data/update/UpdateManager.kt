package com.ayashii.kuro.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.ayashii.kuro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkFileName: String
)

class UpdateManager private constructor(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "KuroUpdateManager"
        private const val PREFS_NAME = "kuro_update_prefs"
        private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
        private const val MIN_CHECK_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes throttle

        @Volatile
        private var instance: UpdateManager? = null

        // In-memory flag: true if a check has already executed in this cold-start process
        @Volatile
        var hasCheckedThisSession = false

        fun getInstance(context: Context): UpdateManager {
            return instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Checks GitHub API for newer releases.
     * @param force If true, bypasses throttle check (used for manual check from Settings).
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()

        if (!force && (now - lastCheck < MIN_CHECK_INTERVAL_MS)) {
            Log.d(TAG, "Update check throttled. Skipping.")
            return@withContext null
        }

        try {
            prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()
            val request = Request.Builder()
                .url("https://api.github.com/repos/ayashiiiyo/kuro/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Kuro-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API returned ${response.code}")
                return@withContext null
            }

            val bodyString = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyString)
            val tagName = json.optString("tag_name", "").removePrefix("v").trim()
            val releaseNotes = json.optString("body", "").trim()

            if (tagName.isBlank()) return@withContext null

            val currentVersion = BuildConfig.VERSION_NAME
            if (isNewerVersion(currentVersion, tagName)) {
                // Find release apk asset
                val assets = json.optJSONArray("assets") ?: return@withContext null
                var downloadUrl: String? = null
                var fileName: String? = null

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith("-release.apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url")
                        fileName = name
                        break
                    }
                }

                // Fallback to any apk asset if release name pattern doesn't match
                if (downloadUrl == null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true) && !name.contains("debug", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url")
                            fileName = name
                            break
                        }
                    }
                }

                if (downloadUrl != null && fileName != null) {
                    return@withContext UpdateInfo(
                        versionName = tagName,
                        releaseNotes = cleanReleaseNotes(releaseNotes),
                        downloadUrl = downloadUrl,
                        apkFileName = fileName
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update: ${e.message}")
        }
        null
    }

    private fun cleanReleaseNotes(raw: String): String {
        return raw.lines()
            .filterNot { it.contains("Published signed release", ignoreCase = true) }
            .take(10)
            .joinToString("\n")
            .trim()
    }

    /**
     * Downloads the APK file using Android's native DownloadManager.
     * When completed, prompts the package installer immediately.
     */
    fun startDownloadAndInstall(updateInfo: UpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(updateInfo.downloadUrl)

        // Remove old download if existing
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destinationFile = File(downloadDir, updateInfo.apkFileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(uri)
            .setTitle("Kuro v${updateInfo.versionName}")
            .setDescription("Mengunduh pembaruan aplikasi...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, updateInfo.apkFileName)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = downloadManager.enqueue(request)

        // Register one-shot broadcast receiver for completion
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    try {
                        installApk(destinationFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch installer: ${e.message}")
                    } finally {
                        try {
                            ctxt.unregisterReceiver(this)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk(file: File) {
        if (!file.exists()) return

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }

    /**
     * Compares semantic versioning (e.g. "3.4.5" vs "3.5.0").
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(currParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val currVal = currParts.getOrElse(i) { 0 }
            val latestVal = latestParts.getOrElse(i) { 0 }
            if (latestVal > currVal) return true
            if (latestVal < currVal) return false
        }
        return false
    }
}
