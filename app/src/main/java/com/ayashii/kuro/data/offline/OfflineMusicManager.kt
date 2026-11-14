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
import kotlinx.coroutines.launch
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
    val downloadedAtMs: Long
)

class OfflineMusicManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _offlineTracks = MutableStateFlow<List<OfflineTrack>>(emptyList())
    val offlineTracks: StateFlow<List<OfflineTrack>> = _offlineTracks.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    init {
        loadIndex()
    }

    fun isDownloaded(trackId: String): Boolean {
        return getLocalAudioUri(trackId) != null
    }

    fun getLocalAudioUri(trackId: String): String? {
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
        return if (exists) uriStr else null
    }

    fun downloadTrack(
        track: Track,
        subQuality: AudioSubQuality = SettingsManager.getInstance().downloadSubQuality.value
    ) {
        if (_downloadProgress.value.containsKey(track.id)) return
        scope.launch {
            updateProgress(track.id, 0.02f)
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

                val isOpus = subQuality.preferOpus || streamUrl.contains("audio%2Fwebm") || streamUrl.contains("itag=251")
                val ext = if (isOpus) "webm" else "m4a"
                val mimeType = if (isOpus) "audio/webm" else "audio/mp4"
                val safeName = "${track.artist} - ${track.title}"
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .trim()
                    .take(80)
                    .ifBlank { track.id }
                val fileName = "$safeName.$ext"

                val req = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .build()

                val customTreeUri = SettingsManager.getInstance().downloadTreeUri.value
                var savedUriString: String? = null
                var totalBytes = 0L

                HttpClientProvider.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        clearProgress(track.id)
                        return@launch
                    }
                    val body = resp.body ?: run {
                        clearProgress(track.id)
                        return@launch
                    }
                    val contentLength = body.contentLength()
                    val target = openOutputTarget(customTreeUri, fileName, mimeType, track.id, ext)
                    if (target == null) {
                        clearProgress(track.id)
                        return@launch
                    }
                    savedUriString = target.first
                    target.second.use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(16 * 1024)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                out.write(buf, 0, read)
                                totalBytes += read
                                if (contentLength > 0) {
                                    updateProgress(track.id, (totalBytes.toFloat() / contentLength.toFloat()).coerceIn(0.05f, 0.99f))
                                }
                            }
                            out.flush()
                        }
                    }
                }

                val finalUri = savedUriString
                if (!finalUri.isNullOrBlank() && totalBytes > 0L) {
                    val offlineItem = OfflineTrack(
                        track = track,
                        localUri = finalUri,
                        qualityBadge = subQuality.badge,
                        mimeType = mimeType,
                        fileSizeBytes = totalBytes,
                        downloadedAtMs = System.currentTimeMillis()
                    )
                    val updated = _offlineTracks.value.filterNot { it.track.id == track.id }.toMutableList().apply {
                        add(0, offlineItem)
                    }
                    _offlineTracks.value = updated
                    saveIndex(updated)
                }
            } catch (e: Exception) {
                Log.e("OfflineMusicManager", "Download failed for ${track.title}: ${e.message}", e)
            } finally {
                clearProgress(track.id)
            }
        }
    }

    fun deleteOfflineTrack(trackId: String) {
        scope.launch {
            val entry = _offlineTracks.value.firstOrNull { it.track.id == trackId }
            if (entry != null) {
                runCatching {
                    if (entry.localUri.startsWith("content://")) {
                        DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(entry.localUri))
                    } else {
                        File(entry.localUri.removePrefix("file://")).delete()
                    }
                }
                val updated = _offlineTracks.value.filterNot { it.track.id == trackId }
                _offlineTracks.value = updated
                saveIndex(updated)
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
                val stream = context.contentResolver.openOutputStream(docUri) ?: return@runCatching null
                Pair(docUri.toString(), stream)
            }.getOrNull()
            if (created != null) return created
        }

        val dir = File(context.filesDir, "offline_music").apply { mkdirs() }
        val file = File(dir, "$trackId.$ext")
        return Pair(Uri.fromFile(file).toString(), file.outputStream())
    }

    private fun updateProgress(trackId: String, value: Float) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { put(trackId, value) }
    }

    private fun clearProgress(trackId: String) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(trackId) }
    }

    private fun loadIndex() {
        runCatching {
            val raw = prefs.getString(KEY_INDEX, "[]") ?: "[]"
            val arr = JSONArray(raw)
            val list = ArrayList<OfflineTrack>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val tObj = obj.optJSONObject("track") ?: continue
                list.add(
                    OfflineTrack(
                        track = Track(
                            id = tObj.optString("id"),
                            url = tObj.optString("url"),
                            title = tObj.optString("title"),
                            artist = tObj.optString("artist"),
                            thumbnailUrl = tObj.optString("thumbnailUrl"),
                            durationSeconds = tObj.optLong("durationSeconds", 0L)
                        ),
                        localUri = obj.optString("localUri"),
                        qualityBadge = obj.optString("qualityBadge"),
                        mimeType = obj.optString("mimeType", "audio/mp4"),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        downloadedAtMs = obj.optLong("downloadedAtMs", 0L)
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
