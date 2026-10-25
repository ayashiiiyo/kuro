package com.ayashii.kuro.data.update

import android.app.ActivityManager
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.ayashii.kuro.BuildConfig
import com.ayashii.kuro.R
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
        private const val NOTIFICATION_CHANNEL_UPDATE = "kuro_app_update_channel"
        private const val NOTIFICATION_ID_UPDATE = 2002

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
                response.close()
                return@withContext null
            }

            // ResponseBody.string() closes the source; close the response too so a
            // failed connection cannot leak the underlying socket.
            val bodyString = try {
                response.body?.string()
            } finally {
                response.close()
            } ?: return@withContext null
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
     * When completed, verifies the download and prompts the package installer.
     */
    fun startDownloadAndInstall(updateInfo: UpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            Log.e(TAG, "DownloadManager unavailable")
            return
        }
        val uri = Uri.parse(updateInfo.downloadUrl)

        // App-specific external storage is always readable via FileProvider without any
        // storage permission, and stays valid under scoped storage on Android 10+.
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir == null) {
            Log.e(TAG, "External files dir unavailable; cannot download update")
            return
        }
        val destinationFile = File(downloadDir, updateInfo.apkFileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(uri)
            .setTitle(context.getString(R.string.update_download_title, updateInfo.versionName))
            .setDescription(context.getString(R.string.update_download_desc))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, updateInfo.apkFileName)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            // Invalid URL / storage unavailable: never crash the UI, fall back to the browser.
            Log.e(TAG, "Unable to enqueue update download: ${e.message}")
            openDownloadPage(updateInfo.downloadUrl)
            return
        }

        // Register one-shot broadcast receiver for completion (exported because DownloadManager is a system UID)
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                try {
                    // DownloadManager reports completion even for failed downloads, so the
                    // actual status must be checked before telling the user it is ready.
                    if (!isDownloadSuccessful(downloadManager, downloadId) ||
                        !destinationFile.exists() ||
                        destinationFile.length() <= 0L
                    ) {
                        Log.w(TAG, "Update download did not complete successfully")
                        showDownloadFailedNotification()
                        return
                    }
                    if (hasInstallPermission()) {
                        // Show the notification first, then try the direct prompt only when
                        // the app is in the foreground (background activity starts are blocked).
                        showInstallNotification(updateInfo, destinationFile)
                        if (isAppForeground()) installApk(destinationFile)
                    } else {
                        showInstallPermissionNotification(destinationFile)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch installer/notification: ${e.message}")
                } finally {
                    try {
                        ctxt.unregisterReceiver(this)
                    } catch (_: Exception) {}
                }
            }
        }

        try {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to register download receiver: ${e.message}")
        }
    }

    private fun isDownloadSuccessful(downloadManager: DownloadManager, downloadId: Long): Boolean {
        return try {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                cursor.moveToFirst() &&
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                    DownloadManager.STATUS_SUCCESSFUL
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Unable to query download status: ${e.message}")
            false
        }
    }

    private fun openDownloadPage(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open download page: ${e.message}")
        }
    }

    private fun ensureChannel(): NotificationManager? {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_UPDATE,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.update_channel_desc)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        return notificationManager
    }

    private fun showInstallNotification(updateInfo: UpdateInfo, file: File) {
        val notificationManager = ensureChannel() ?: return
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UPDATE,
            createInstallIntent(file),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_ready_title))
            .setContentText(context.getString(R.string.update_ready_text, updateInfo.versionName))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.update_ready_bigtext, updateInfo.versionName))
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.update_install_now), pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)
    }

    /** Android 8+ requires per-app permission before the package installer can be launched. */
    private fun showInstallPermissionNotification(file: File) {
        val notificationManager = ensureChannel() ?: return
        val installPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UPDATE,
            createInstallIntent(file),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val settingsPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UPDATE + 1,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_permission_title))
            .setContentText(context.getString(R.string.update_permission_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.update_permission_text))
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            // Tapping again after granting the permission opens the installer directly.
            .setContentIntent(installPendingIntent)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.update_permission_title),
                settingsPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)
    }

    private fun showDownloadFailedNotification() {
        val notificationManager = ensureChannel() ?: return
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_available))
            .setContentText(context.getString(R.string.update_download_failed))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)
    }

    private fun createInstallIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun installApk(file: File) {
        if (!file.exists()) return
        try {
            context.startActivity(createInstallIntent(file))
        } catch (e: Exception) {
            // Android 10+ blocks background activity starts; the notification remains the
            // reliable install path, so this is not fatal.
            Log.w(TAG, "Direct install prompt unavailable: ${e.message}")
        }
    }

    private fun hasInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }
    }

    private fun isAppForeground(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            val pid = Process.myPid()
            activityManager.runningAppProcesses?.any {
                it.pid == pid && it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            } == true
        } catch (_: Exception) {
            false
        }
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
