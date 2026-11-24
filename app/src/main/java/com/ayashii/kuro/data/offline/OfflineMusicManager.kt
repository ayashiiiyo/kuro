package com.ayashii.kuro.data.offline

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.remote.HttpClientProvider
import com.ayashii.kuro.data.remote.MusicRepository
import com.ayashii.kuro.data.settings.AudioSubQuality
import com.ayashii.kuro.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream

data class OfflineTrack(
    val track: Track,
    val localUri: String,
    val qualityBadge: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val downloadedAtMs: Long,
    val localThumbnailUri: String? = null
)

class OfflineMusicManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val indexMutex = Mutex()

    private val _offlineTracks = MutableStateFlow<List<OfflineTrack>>(emptyList())
    val offlineTracks: StateFlow<List<OfflineTrack>> = _offlineTracks.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    init {
        scope.launch {
            indexMutex.withLock {
                loadIndex()
            }
        }
    }

    fun isDownloaded(trackId: String): Boolean {
        return getLocalAudioUri(trackId) != null
    }

    fun getLocalAudioInfo(trackId: String): Pair<String, String>? {
        val entry = _offlineTracks.value.firstOrNull { it.track.id == trackId } ?: return null
        val uriStr = entry.localUri
        val exists = if (uriStr.startsWith("content://")) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(uriStr), "r")?.use { true } ?: false
            }.getOrDefault(false)
        } else {
            val path = uriStr.removePrefix("file://")
            File(path).exists()
        }
        return if (exists) Pair(uriStr, entry.mimeType) else null
    }

    fun getLocalAudioUri(trackId: String): String? {
        return getLocalAudioInfo(trackId)?.first
    }

    fun getLocalThumbnailUri(trackId: String): String? {
        val entry = _offlineTracks.value.firstOrNull { it.track.id == trackId }
        val stored = entry?.localThumbnailUri
        if (!stored.isNullOrBlank()) {
            val file = File(stored.removePrefix("file://"))
            if (file.exists() && file.length() > 0L) return stored
        }
        val fallbackFile = File(File(context.filesDir, "offline_thumbs"), "$trackId.jpg")
        return if (fallbackFile.exists() && fallbackFile.length() > 0L) {
            Uri.fromFile(fallbackFile).toString()
        } else {
            null
        }
    }

    private fun resolveStreamUserAgent(streamUrl: String): String {
        return when {
            streamUrl.contains("c=IOS") ->
                "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"
            streamUrl.contains("c=ANDROID_VR") ->
                "com.google.android.apps.youtube.vr.oculus/1.56.21 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            streamUrl.contains("c=ANDROID_MUSIC") ->
                "com.google.android.apps.youtube.music/7.21.50 (Linux; U; Android 14) gzip"
            streamUrl.contains("c=ANDROID") ->
                "com.google.android.youtube/19.44.38 (Linux; U; Android 14) gzip"
            else ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        }
    }

    private fun downloadLocalThumbnail(track: Track): String? {
        val remoteUrl = track.thumbnailUrlForDisplay(512)
        if (remoteUrl.isBlank()) return null
        if (remoteUrl.startsWith("file://") || remoteUrl.startsWith("content://")) return remoteUrl
        return runCatching {
            val safeId = sanitizeIdForFilename(track.id)
            val thumbDir = File(context.filesDir, "offline_thumbs").apply { mkdirs() }
            val thumbFile = File(thumbDir, "$safeId.jpg")
            val tempFile = File(thumbDir, "$safeId.jpg.tmp")
            val req = Request.Builder()
                .url(remoteUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                )
                .build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val bytes = resp.body?.bytes() ?: return@runCatching null
                if (bytes.isEmpty()) return@runCatching null
                tempFile.writeBytes(bytes)
                if (thumbFile.exists()) thumbFile.delete()
                if (!tempFile.renameTo(thumbFile)) {
                    thumbFile.writeBytes(bytes)
                    tempFile.delete()
                }
                Uri.fromFile(thumbFile).toString()
            }
        }.getOrNull()
    }

    private fun deleteTargetUri(uriStr: String?) {
        if (uriStr.isNullOrBlank()) return
        runCatching {
            if (uriStr.startsWith("content://")) {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uriStr))
            } else {
                File(uriStr.removePrefix("file://")).delete()
            }
        }
    }

    fun downloadTrack(
        track: Track,
        subQuality: AudioSubQuality = SettingsManager.getInstance().downloadSubQuality.value
    ) {
        var alreadyRunning = false
        _downloadProgress.update { current ->
            if (current.containsKey(track.id)) {
                alreadyRunning = true
                current
            } else {
                current + (track.id to 0.02f)
            }
        }
        if (alreadyRunning) return

        scope.launch {
            var savedUriString: String? = null
            var completed = false
            try {
                val repo = MusicRepository.getInstance()
                val streamUrl = repo.getAudioStreamUrl(
                    url = track.url,
                    quality = subQuality.parent,
                    subQualityOverride = subQuality
                )
                if (streamUrl.isNullOrBlank()) {
                    clearProgress(track.id)
                    return@launch
                }

                val req = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", resolveStreamUserAgent(streamUrl))
                    .build()

                val customTreeUri = SettingsManager.getInstance().downloadTreeUri.value
                var totalBytes = 0L
                var resolvedMimeType = if (subQuality.preferOpus || streamUrl.contains("audio%2Fwebm") || streamUrl.contains("itag=251")) {
                    "audio/webm"
                } else {
                    "audio/mp4"
                }

                HttpClientProvider.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        clearProgress(track.id)
                        return@launch
                    }
                    val contentTypeHeader = resp.header("Content-Type")?.substringBefore(";")?.trim()?.lowercase()
                    if (!contentTypeHeader.isNullOrBlank() && contentTypeHeader.startsWith("audio/")) {
                        resolvedMimeType = contentTypeHeader
                    }
                    val isWebm = resolvedMimeType.contains("webm") || resolvedMimeType.contains("opus")
                    val ext = if (isWebm) "webm" else "m4a"
                    val safeName = "${track.artist} - ${track.title}"
                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        .trim()
                        .take(80)
                        .ifBlank { track.id }
                    val fileName = "$safeName.$ext"

                    val body = resp.body ?: run {
                        clearProgress(track.id)
                        return@launch
                    }
                    val contentLength = body.contentLength()
                    val target = openOutputTarget(customTreeUri, fileName, resolvedMimeType, track.id, ext)
                    if (target == null) {
                        clearProgress(track.id)
                        return@launch
                    }
                    savedUriString = target.first
                    target.second.use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(16 * 1024)
                            var read: Int
                            var lastEmittedProgress = 0.02f
                            var lastEmitTimeMs = android.os.SystemClock.elapsedRealtime()
                            while (input.read(buf).also { read = it } != -1) {
                                out.write(buf, 0, read)
                                totalBytes += read
                                if (contentLength > 0) {
                                    val pct = (totalBytes.toFloat() / contentLength.toFloat()).coerceIn(0.05f, 0.96f)
                                    val nowMs = android.os.SystemClock.elapsedRealtime()
                                    if (pct - lastEmittedProgress >= 0.02f || nowMs - lastEmitTimeMs >= 200L) {
                                        lastEmittedProgress = pct
                                        lastEmitTimeMs = nowMs
                                        updateProgress(track.id, pct)
                                    }
                                }
                            }
                            out.flush()
                        }
                    }
                    if (contentLength > 0L && totalBytes < contentLength) {
                        throw java.io.IOException("Incomplete download: $totalBytes / $contentLength")
                    }
                }
                val finalUri = promoteTempFileIfNeeded(savedUriString)
                savedUriString = finalUri
                if (!finalUri.isNullOrBlank() && totalBytes > 0L) {
                    val localThumbUri = downloadLocalThumbnail(track)
                    val offlineTrackModel = if (!localThumbUri.isNullOrBlank()) {
                        track.copy(thumbnailUrl = localThumbUri)
                    } else {
                        track
                    }
                    val offlineItem = OfflineTrack(
                        track = offlineTrackModel,
                        localUri = finalUri,
                        qualityBadge = subQuality.badge,
                        mimeType = resolvedMimeType,
                        fileSizeBytes = totalBytes,
                        downloadedAtMs = System.currentTimeMillis(),
                        localThumbnailUri = localThumbUri
                    )
                    var previousUri: String? = null
                    indexMutex.withLock {
                        previousUri = _offlineTracks.value.firstOrNull { it.track.id == track.id }?.localUri
                        val updated = _offlineTracks.value.filterNot { it.track.id == track.id }.toMutableList().apply {
                            add(0, offlineItem)
                        }
                        _offlineTracks.value = updated
                        saveIndex(updated)
                    }
                    if (!previousUri.isNullOrBlank() && previousUri != finalUri) {
                        deleteTargetUri(previousUri)
                    }
                    completed = true
                }
            } catch (e: Exception) {
                Log.e("OfflineMusicManager", "Download failed for ${track.title}: ${e.message}", e)
            } finally {
                if (!completed && !savedUriString.isNullOrBlank()) {
                    deleteTargetUri(savedUriString)
                }
                clearProgress(track.id)
            }
        }
    }

    fun deleteOfflineTrack(trackId: String) {
        scope.launch {
            indexMutex.withLock {
                val entry = _offlineTracks.value.firstOrNull { it.track.id == trackId }
                if (entry != null) {
                    deleteTargetUri(entry.localUri)
                    deleteTargetUri(entry.localThumbnailUri)
                    runCatching {
                        File(File(context.filesDir, "offline_thumbs"), "$trackId.jpg").delete()
                    }
                    val updated = _offlineTracks.value.filterNot { it.track.id == trackId }
                    _offlineTracks.value = updated
                    saveIndex(updated)
                }
            }
        }
    }

    private fun openOutputTarget(
        customTreeUriStr: String?,
        displayFileName: String,
        mimeType: String,
        trackId: String,
        ext: String
    ): Pair<String, OutputStream>? {
        if (!customTreeUriStr.isNullOrBlank()) {
            val created = runCatching {
                val treeUri = Uri.parse(customTreeUriStr)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                val docUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    mimeType,
                    displayFileName
                ) ?: return@runCatching null
                val stream = context.contentResolver.openOutputStream(docUri)
                if (stream == null) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }
                    return@runCatching null
                }
                Pair(docUri.toString(), stream)
            }.getOrNull()
            if (created != null) return created
        }

        val safeId = sanitizeIdForFilename(trackId)
        val dir = File(context.filesDir, "offline_music").apply { mkdirs() }
        val tempFile = File(dir, "$safeId.$ext.tmp")
        return Pair(Uri.fromFile(tempFile).toString(), tempFile.outputStream())
    }

    private fun sanitizeIdForFilename(rawId: String): String {
        val cleaned = rawId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
        return cleaned.ifBlank { "track_${System.currentTimeMillis()}" }
    }

    private fun promoteTempFileIfNeeded(uriString: String?): String? {
        if (uriString.isNullOrBlank() || !uriString.startsWith("file://")) return uriString
        val path = Uri.parse(uriString).path ?: return uriString
        if (!path.endsWith(".tmp")) return uriString
        val tempFile = File(path)
        val finalFile = File(path.removeSuffix(".tmp"))
        if (finalFile.exists()) finalFile.delete()
        return if (tempFile.renameTo(finalFile)) {
            Uri.fromFile(finalFile).toString()
        } else {
            uriString
        }
    }

    private fun updateProgress(trackId: String, value: Float) {
        _downloadProgress.update { it + (trackId to value) }
    }

    private fun clearProgress(trackId: String) {
        _downloadProgress.update { it - trackId }
    }

    private fun loadIndex() {
        runCatching {
            val raw = prefs.getString(KEY_INDEX, "[]") ?: "[]"
            val arr = JSONArray(raw)
            val list = ArrayList<OfflineTrack>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val tObj = obj.optJSONObject("track") ?: continue
                val trackId = tObj.optString("id")
                val storedThumb = obj.optString("localThumbnailUri", "").takeIf { it.isNotBlank() }
                val verifiedThumb = storedThumb?.takeIf { File(it.removePrefix("file://")).exists() }
                    ?: File(File(context.filesDir, "offline_thumbs"), "$trackId.jpg")
                        .takeIf { it.exists() && it.length() > 0L }
                        ?.let { Uri.fromFile(it).toString() }
                val rawThumb = tObj.optString("thumbnailUrl")
                list.add(
                    OfflineTrack(
                        track = Track(
                            id = trackId,
                            url = tObj.optString("url"),
                            title = tObj.optString("title"),
                            artist = tObj.optString("artist"),
                            thumbnailUrl = verifiedThumb ?: rawThumb,
                            durationSeconds = tObj.optLong("durationSeconds", 0L)
                        ),
                        localUri = obj.optString("localUri"),
                        qualityBadge = obj.optString("qualityBadge"),
                        mimeType = obj.optString("mimeType", "audio/mp4"),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        downloadedAtMs = obj.optLong("downloadedAtMs", 0L),
                        localThumbnailUri = verifiedThumb
                    )
                )
            }
            _offlineTracks.value = list
        }
    }

    private fun saveIndex(list: List<OfflineTrack>) {
        runCatching {
            val arr = JSONArray()
            for (item in list) {
                arr.put(JSONObject().apply {
                    put("track", JSONObject().apply {
                        put("id", item.track.id)
                        put("url", item.track.url)
                        put("title", item.track.title)
                        put("artist", item.track.artist)
                        put("thumbnailUrl", item.track.thumbnailUrl)
                        put("durationSeconds", item.track.durationSeconds)
                    })
                    put("localUri", item.localUri)
                    put("qualityBadge", item.qualityBadge)
                    put("mimeType", item.mimeType)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("downloadedAtMs", item.downloadedAtMs)
                    if (!item.localThumbnailUri.isNullOrBlank()) {
                        put("localThumbnailUri", item.localThumbnailUri)
                    }
                })
            }
            prefs.edit().putString(KEY_INDEX, arr.toString()).apply()
        }
    }

    companion object {
        private const val PREF_NAME = "kuro_offline_music"
        private const val KEY_INDEX = "offline_index"

        @Volatile
        private var INSTANCE: OfflineMusicManager? = null

        fun getInstance(context: Context): OfflineMusicManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineMusicManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
