package com.ayashii.kuro.data.remote

import android.util.Log
import android.util.LruCache
import com.ayashii.kuro.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class MusicRepository {

    private val service = ServiceList.YouTube
    private val streamUrlCache = LruCache<String, Pair<String, Long>>(50)
    private val CACHE_EXPIRY_MS = 3 * 60 * 60 * 1000L

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                service.suggestionExtractor.suggestionList("trending")
            } catch (e: Exception) {
                Log.d("MusicRepository", "Prewarm notice: ${e.message}")
            }
        }
    }

    private fun cleanTrackTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*[\(\[](?:official\s*(?:music\s*)?video|official\s*audio|music\s*video|lyric\s*video|lyrics|audio|visualizer|remastered|remaster|4k|hd|hq|mv|clip\s*officiel|video\s*oficial)[\)\]]"""), "")
            .replace(Regex("""(?i)\s*\|\s*(?:official\s*(?:music\s*)?video|official\s*audio|visualizer).*"""), "")
            .trim()
    }

    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val searchExtractor = service.getSearchExtractor(
                query,
                listOf("videos"),
                ""
            )
            searchExtractor.fetchPage()
            val page = searchExtractor.initialPage
            page.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    Track(
                        id = item.url.substringAfter("v=").substringBefore("&"),
                        url = item.url,
                        title = cleanTrackTitle(item.name ?: "Unknown Title"),
                        artist = item.uploaderName?.replace(" - Topic", "") ?: "Unknown Artist",
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                        durationSeconds = item.duration
                    )
                }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Search failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val extractor = service.suggestionExtractor
            extractor.suggestionList(query)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Suggestions failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getTrendingTracks(): List<Track> = withContext(Dispatchers.IO) {
        try {
            val kiosk = service.kioskList.defaultKioskExtractor
            kiosk.fetchPage()
            val page = kiosk.initialPage
            val tracks = page.items
                .filterIsInstance<StreamInfoItem>()
                .take(30)
                .map { item ->
                    Track(
                        id = item.url.substringAfter("v=").substringBefore("&"),
                        url = item.url,
                        title = cleanTrackTitle(item.name ?: "Unknown Title"),
                        artist = item.uploaderName?.replace(" - Topic", "") ?: "Unknown Artist",
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                        durationSeconds = item.duration
                    )
                }
            if (tracks.isNotEmpty()) tracks else searchTracks("Top Global Hits 2026")
        } catch (e: Exception) {
            Log.e("MusicRepository", "Trending failed: ${e.message}", e)
            searchTracks("Top Global Hits 2026")
        }
    }

    suspend fun getAudioStreamUrl(url: String): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        synchronized(streamUrlCache) {
            streamUrlCache.get(url)?.let { (cachedUrl, timestamp) ->
                if (now - timestamp < CACHE_EXPIRY_MS) {
                    return@withContext cachedUrl
                }
            }
        }

        try {
            val streamInfo = StreamInfo.getInfo(service, url)
            val audioStreams = streamInfo.audioStreams

            val bestAudio = audioStreams
                .filter { it.content != null && it.content.startsWith("http") }
                .sortedWith(
                    compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> {
                        it.format.toString().contains("M4A")
                    }.thenByDescending { it.averageBitrate }
                )
                .firstOrNull() ?: audioStreams.firstOrNull()

            val resolved = bestAudio?.content
            if (resolved != null) {
                synchronized(streamUrlCache) {
                    streamUrlCache.put(url, Pair(resolved, now))
                }
            }
            resolved
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to get audio stream for $url: ${e.message}", e)
            null
        }
    }

    suspend fun getLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
        val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
        val cookie = auth.getCookie()
        if (cookie.isNullOrBlank()) return@withContext emptyList()

        try {
            val origin = "https://music.youtube.com"
            val sapisidHash = auth.getSapisidHash(origin)

            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("browseId", "FEmusic_liked_videos")
            }

            val client = okhttp3.OkHttpClient()
            val mediaType = okhttp3.MediaType.parse("application/json")
            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/browse")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240101.01.00")
                .header("Cookie", cookie)
                .post(okhttp3.RequestBody.create(mediaType, payload.toString()))

            if (sapisidHash != null) {
                reqBuilder.header("Authorization", sapisidHash)
            }

            val resp = client.newCall(reqBuilder.build()).execute()
            if (resp.isSuccessful) {
                val json = org.json.JSONObject(resp.body?.string().orEmpty())
                val contents = json.optJSONObject("contents")
                    ?.optJSONObject("singleColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                val trackList = mutableListOf<Track>()
                val musicShelf = contents?.optJSONObject(0)?.optJSONObject("musicShelfRenderer")
                val items = musicShelf?.optJSONArray("contents")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val row = items.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer")
                        if (row != null) {
                            val cols = row.optJSONArray("flexColumns")
                            val title = cols?.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optString("text") ?: ""

                            val videoId = cols?.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("watchEndpoint")
                                ?.optString("videoId") ?: ""

                            val artist = cols?.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optString("text") ?: "YouTube Music"

                            val thumb = row.optJSONObject("thumbnail")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                                ?.let { it.optJSONObject(it.length() - 1)?.optString("url") } ?: ""

                            if (videoId.isNotBlank() && title.isNotBlank()) {
                                trackList.add(
                                    Track(
                                        id = videoId,
                                        url = "https://www.youtube.com/watch?v=$videoId",
                                        title = cleanTrackTitle(title),
                                        artist = artist.replace(" - Topic", ""),
                                        thumbnailUrl = thumb,
                                        durationSeconds = 0,
                                        isFavorite = true
                                    )
                                )
                            }
                        }
                    }
                }
                if (trackList.isNotEmpty()) {
                    return@withContext trackList
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Liked songs InnerTube fetch error: ${e.message}", e)
        }

        // Fallback: NewPipe playlist extractor
        try {
            val playlistExtractor = service.getPlaylistExtractor("https://www.youtube.com/playlist?list=LL")
            playlistExtractor.fetchPage()
            val page = playlistExtractor.initialPage
            page.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    Track(
                        id = item.url.substringAfter("v=").substringBefore("&"),
                        url = item.url,
                        title = cleanTrackTitle(item.name ?: "Unknown Title"),
                        artist = item.uploaderName?.replace(" - Topic", "") ?: "Unknown Artist",
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                        durationSeconds = item.duration,
                        isFavorite = true
                    )
                }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to fetch liked tracks via NewPipe: ${e.message}")
            emptyList()
        }
    }
}
