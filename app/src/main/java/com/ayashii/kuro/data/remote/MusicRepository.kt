package com.ayashii.kuro.data.remote

import android.util.Log
import android.util.LruCache
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.model.HomeSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class ResolvedStream(
    val url: String,
    val mimeType: String? = null
)

class MusicRepository {

    private val service = ServiceList.YouTube
    private val streamUrlCache = LruCache<String, Pair<String, Long>>(50)
    private val CACHE_EXPIRY_MS = 3 * 60 * 60 * 1000L

    private var homeCache: Pair<List<HomeSection>, Long>? = null
    private val HOME_CACHE_TTL = 10 * 60 * 1000L // 10 minutes

    private val searchCache = LruCache<String, Pair<List<Track>, Long>>(30)
    private val SEARCH_CACHE_TTL = 5 * 60 * 1000L // 5 minutes

    private val radioCache = LruCache<String, Pair<List<Track>, Long>>(20)
    private val RADIO_CACHE_TTL = 30 * 60 * 1000L // 30 minutes

    private fun cleanTrackTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*[\(\[](?:official\s*(?:music\s*)?video|official\s*audio|music\s*video|lyric\s*video|lyrics|audio|visualizer|remastered|remaster|4k|hd|hq|mv|clip\s*officiel|video\s*oficial)[\)\]]"""), "")
            .replace(Regex("""(?i)\s*\|\s*(?:official\s*(?:music\s*)?video|official\s*audio|visualizer).*"""), "")
            .trim()
    }

    fun upgradeThumbnailUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        var u = rawUrl.trim()
        if (u.startsWith("//")) {
            u = "https:$u"
        }
        if (u.contains("googleusercontent.com")) {
            return if (u.contains(Regex("=w\\d+-h\\d+"))) {
                u.replace(Regex("=w\\d+-h\\d+[^&?]*"), "=w1080-h1080-l90-rj")
            } else if (u.contains(Regex("=s\\d+"))) {
                u.replace(Regex("=s\\d+[^&?]*"), "=w1080-h1080-l90-rj")
            } else if (u.contains("=")) {
                u.replace(Regex("=[^=]*$"), "=w1080-h1080-l90-rj")
            } else {
                "$u=w1080-h1080-l90-rj"
            }
        }
        if (u.contains("ytimg.com")) {
            return u
                .replace("/default.jpg", "/hqdefault.jpg")
                .replace("/default.webp", "/hqdefault.webp")
                .replace("/mqdefault.jpg", "/hqdefault.jpg")
                .replace("/mqdefault.webp", "/hqdefault.webp")
        }
        return u
    }

    suspend fun resolveBestThumbnail(track: Track): String = withContext(Dispatchers.IO) {
        val upgraded = upgradeThumbnailUrl(track.thumbnailUrl)
        if (upgraded.contains("ytimg.com") && track.id.isNotBlank()) {
            val maxres = "https://i.ytimg.com/vi/${track.id}/maxresdefault.jpg"
            try {
                val req = okhttp3.Request.Builder()
                    .url(maxres)
                    .head()
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val client = HttpClientProvider.client
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    return@withContext maxres
                }
            } catch (_: Exception) {}
        }
        upgraded
    }

    private fun parseDurationToSeconds(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L
        val clean = durationStr.replace(".", ":").trim()
        val parts = clean.split(":")
        return try {
            when (parts.size) {
                2 -> (parts[0].toLong() * 60) + parts[1].toLong()
                3 -> (parts[0].toLong() * 3600) + (parts[1].toLong() * 60) + parts[2].toLong()
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim().lowercase()
        val now = System.currentTimeMillis()
        synchronized(searchCache) {
            searchCache.get(q)?.let { (cached, time) ->
                if (now - time < SEARCH_CACHE_TTL) {
                    return@withContext cached
                }
            }
        }

        // 1. Prioritize YouTube Music Song Search (Pure Music Tracks!)
        val ytmTracks = searchYouTubeMusic(query)
        if (ytmTracks.isNotEmpty()) {
            synchronized(searchCache) { searchCache.put(q, Pair(ytmTracks, now)) }
            return@withContext ytmTracks
        }

        // 2. Fallback to NewPipe Extractor
        try {
            val searchExtractor = service.getSearchExtractor(
                query,
                listOf("videos"),
                ""
            )
            searchExtractor.fetchPage()
            val page = searchExtractor.initialPage
            val list = page.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    val rawThumb = item.thumbnails.maxByOrNull { it.width * it.height }?.url
                        ?: item.thumbnails.lastOrNull()?.url
                        ?: item.thumbnails.firstOrNull()?.url
                        ?: ""
                    Track(
                        id = item.url.substringAfter("v=").substringBefore("&"),
                        url = item.url,
                        title = cleanTrackTitle(item.name ?: "Unknown Title"),
                        artist = item.uploaderName?.replace(" - Topic", "") ?: "Unknown Artist",
                        thumbnailUrl = upgradeThumbnailUrl(rawThumb),
                        durationSeconds = item.duration
                    )
                }
            if (list.isNotEmpty()) {
                synchronized(searchCache) { searchCache.put(q, Pair(list, now)) }
            }
            list
        } catch (e: Exception) {
            Log.e("MusicRepository", "Search failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun searchYouTubeMusic(query: String): List<Track> {
        return try {
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"
            val cookie = auth.getCookie() ?: ""
            val sapisidHash = auth.getSapisidHash(origin)

            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
                put("query", query)
                put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA==")
            }

            val client = HttpClientProvider.client
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/search")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240101.01.00")
                .post(reqBody)

            if (cookie.isNotBlank()) reqBuilder.header("Cookie", cookie)
            if (!sapisidHash.isNullOrBlank()) {
                reqBuilder.header("Authorization", sapisidHash)
                reqBuilder.header("X-Goog-AuthUser", "0")
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val jsonStr = response.body?.string() ?: return emptyList()
            parseYtmSearchTracks(org.json.JSONObject(jsonStr))
        } catch (e: Exception) {
            Log.e("MusicRepository", "YTM Search exception: ${e.message}")
            emptyList()
        }
    }

    private fun parseYtmSearchTracks(root: org.json.JSONObject): List<Track> {
        val list = mutableListOf<Track>()
        try {
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return emptyList()
            val sections = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (s in 0 until sections.length()) {
                val shelf = sections.optJSONObject(s)?.optJSONObject("musicShelfRenderer") ?: continue
                val items = shelf.optJSONArray("contents") ?: continue
                for (i in 0 until items.length()) {
                    val r = items.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val cols = r.optJSONArray("flexColumns") ?: continue
                    if (cols.length() < 2) continue

                    val col0Runs = cols.optJSONObject(0)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs") ?: continue
                    if (col0Runs.length() == 0) continue

                    val title = col0Runs.optJSONObject(0)?.optString("text", "") ?: ""
                    val videoId = col0Runs.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId", "") ?: ""
                    if (videoId.isBlank()) continue

                    val col1Runs = cols.optJSONObject(1)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")
                    val artist = col1Runs?.optJSONObject(0)?.optString("text", "Unknown Artist") ?: "Unknown Artist"

                    var durationSec = 0L
                    if (col1Runs != null && col1Runs.length() > 0) {
                        val lastRunText = col1Runs.optJSONObject(col1Runs.length() - 1)?.optString("text", "") ?: ""
                        durationSec = parseDurationToSeconds(lastRunText)
                    }

                    var thumbUrl = ""
                    val thumbs = r.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                    }

                    list.add(
                        Track(
                            id = videoId,
                            url = "https://www.youtube.com/watch?v=$videoId",
                            title = cleanTrackTitle(title),
                            artist = artist.replace(" - Topic", ""),
                            thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                            durationSeconds = durationSec
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "parseYtmSearchTracks error: ${e.message}")
        }
        return list
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

    suspend fun getHomeShelves(): List<HomeSection> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            homeCache?.let { (cached, time) ->
                if (now - time < HOME_CACHE_TTL) {
                    return@withContext cached
                }
            }
        }
        val ytmShelves = fetchYouTubeMusicShelves()
        if (ytmShelves.isNotEmpty()) {
            synchronized(this) { homeCache = Pair(ytmShelves, now) }
            return@withContext ytmShelves
        }

        // Fallback: group into categories
        val defaultTracks = searchTracks("Top Global Hits 2026")
        if (defaultTracks.isNotEmpty()) {
            listOf(
                HomeSection(title = "Pilihan Cepat", subtitle = "Dengarkan musik favoritmu", tracks = defaultTracks.take(10)),
                HomeSection(title = "Lagu Trending", subtitle = "Paling banyak diputar", tracks = defaultTracks.drop(10))
            )
        } else {
            emptyList()
        }
    }

    suspend fun getTrendingTracks(): List<Track> = withContext(Dispatchers.IO) {
        searchTracks("Top Global Hits 2026")
    }

    suspend fun getRadioTracks(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        synchronized(radioCache) {
            radioCache.get(videoId)?.let { (cached, time) ->
                if (now - time < RADIO_CACHE_TTL) {
                    return@withContext cached
                }
            }
        }
        try {
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"
            val cookie = auth.getCookie() ?: ""
            val sapisidHash = auth.getSapisidHash(origin)

            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
            }

            val client = HttpClientProvider.client
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/next")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240101.01.00")
                .post(reqBody)

            if (cookie.isNotBlank()) reqBuilder.header("Cookie", cookie)
            if (!sapisidHash.isNullOrBlank()) {
                reqBuilder.header("Authorization", sapisidHash)
            }

            val resp = client.newCall(reqBuilder.build()).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val json = org.json.JSONObject(resp.body?.string().orEmpty())
            val tabs = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return@withContext emptyList()

            var queueTab: org.json.JSONObject? = null
            for (ti in 0 until tabs.length()) {
                val q = tabs.optJSONObject(ti)?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")?.optJSONObject("musicQueueRenderer")
                if (q != null) {
                    queueTab = q
                    break
                }
            }
            if (queueTab == null) {
                queueTab = tabs.optJSONObject(0)?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")?.optJSONObject("musicQueueRenderer")
            }

            val items = queueTab?.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")?.optJSONArray("contents") ?: return@withContext emptyList()

            val radioList = mutableListOf<Track>()
            for (i in 0 until items.length()) {
                val r = items.optJSONObject(i)?.optJSONObject("playlistPanelVideoRenderer") ?: continue
                val vid = r.optString("videoId", "")
                if (vid.isBlank() || vid == videoId) continue

                val t = r.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                val a = r.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "Unknown Artist") ?: "Unknown Artist"
                val durStr = r.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                val durSec = parseDurationToSeconds(durStr)

                val thumbs = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                } else ""

                radioList.add(
                    Track(
                        id = vid,
                        url = "https://www.youtube.com/watch?v=$vid",
                        title = cleanTrackTitle(t),
                        artist = a.replace(" - Topic", ""),
                        thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                        durationSeconds = durSec
                    )
                )
            }
            if (radioList.isNotEmpty()) {
                synchronized(radioCache) { radioCache.put(videoId, Pair(radioList, now)) }
            }
            radioList
        } catch (e: Exception) {
            Log.e("MusicRepository", "getRadioTracks error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchYouTubeMusicShelves(): List<HomeSection> = withContext(Dispatchers.IO) {
        val result = mutableListOf<HomeSection>()
        try {
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"
            val cookie = auth.getCookie() ?: ""
            val sapisidHash = auth.getSapisidHash(origin)
            val client = HttpClientProvider.client

            fun fetchBrowse(browseId: String): org.json.JSONObject? {
                val payload = org.json.JSONObject().apply {
                    put("context", org.json.JSONObject().apply {
                        put("client", org.json.JSONObject().apply {
                            put("clientName", "WEB_REMIX")
                            put("clientVersion", "1.20240101.01.00")
                            put("hl", "id")
                            put("gl", "ID")
                        })
                    })
                    put("browseId", browseId)
                }
                val reqBuilder = okhttp3.Request.Builder()
                    .url("$origin/youtubei/v1/browse")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Origin", origin)
                    .header("Referer", "$origin/")
                    .header("X-Origin", origin)
                    .header("X-YouTube-Client-Name", "67")
                    .header("X-YouTube-Client-Version", "1.20240101.01.00")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))

                if (cookie.isNotBlank()) reqBuilder.header("Cookie", cookie)
                if (!sapisidHash.isNullOrBlank()) {
                    reqBuilder.header("Authorization", sapisidHash)
                    reqBuilder.header("X-Goog-AuthUser", "0")
                }

                val resp = client.newCall(reqBuilder.build()).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return null
                    return org.json.JSONObject(body)
                }
                return null
            }

            coroutineScope {
                val homeDeferred = async { fetchBrowse("FEmusic_home") }
                val exploreDeferred = async { fetchBrowse("FEmusic_explore") }
                val homeJson = homeDeferred.await()
                val exploreJson = exploreDeferred.await()

                if (homeJson != null) {
                    result.addAll(parseYtmSections(homeJson, "Pilihan Cepat"))
                }
                if (exploreJson != null) {
                    result.addAll(parseYtmSections(exploreJson, "Lagu Trending"))
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "fetchYouTubeMusicShelves error: ${e.message}")
        }
        result
    }

    private fun parseYtmSections(root: org.json.JSONObject, defaultTitle: String): List<HomeSection> {
        val list = mutableListOf<HomeSection>()
        try {
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs") ?: return emptyList()
            val sections = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (s in 0 until sections.length()) {
                val shelfObj = sections.optJSONObject(s)?.optJSONObject("musicCarouselShelfRenderer") ?: continue
                val hRuns = shelfObj.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("title")?.optJSONArray("runs")
                var shelfTitle = ""
                if (hRuns != null) {
                    val sb = StringBuilder()
                    for (k in 0 until hRuns.length()) {
                        sb.append(hRuns.optJSONObject(k)?.optString("text", ""))
                    }
                    shelfTitle = sb.toString().trim()
                }
                if (shelfTitle.isBlank()) shelfTitle = defaultTitle

                val sRuns = shelfObj.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("strapline")?.optJSONArray("runs")
                var strapline = ""
                if (sRuns != null) {
                    val sb = StringBuilder()
                    for (k in 0 until sRuns.length()) {
                        sb.append(sRuns.optJSONObject(k)?.optString("text", ""))
                    }
                    strapline = sb.toString().trim()
                }

                val items = shelfObj.optJSONArray("contents") ?: continue
                val tracks = mutableListOf<Track>()
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    val track = parseSingleTrackItem(it)
                    if (track != null) tracks.add(track)
                }

                if (tracks.isNotEmpty()) {
                    list.add(HomeSection(title = shelfTitle, subtitle = strapline, tracks = tracks))
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "parseYtmSections error: ${e.message}")
        }
        return list
    }

    private fun parseSingleTrackItem(it: org.json.JSONObject): Track? {
        try {
            if (it.has("musicResponsiveListItemRenderer")) {
                val r = it.getJSONObject("musicResponsiveListItemRenderer")
                val cols = r.optJSONArray("flexColumns") ?: return null
                if (cols.length() < 2) return null

                val col0Runs = cols.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs") ?: return null
                if (col0Runs.length() == 0) return null

                val title = col0Runs.optJSONObject(0)?.optString("text", "") ?: ""
                val videoId = col0Runs.optJSONObject(0)
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "") ?: ""
                if (videoId.isBlank()) return null

                val col1Runs = cols.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs")
                val artist = col1Runs?.optJSONObject(0)?.optString("text", "Unknown Artist") ?: "Unknown Artist"

                var durationSec = 0L
                if (col1Runs != null && col1Runs.length() > 0) {
                    val lastRunText = col1Runs.optJSONObject(col1Runs.length() - 1)?.optString("text", "") ?: ""
                    durationSec = parseDurationToSeconds(lastRunText)
                }

                var thumbUrl = ""
                val thumbs = r.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                }

                return Track(
                    id = videoId,
                    url = "https://www.youtube.com/watch?v=$videoId",
                    title = cleanTrackTitle(title),
                    artist = artist.replace(" - Topic", ""),
                    thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                    durationSeconds = durationSec
                )
            } else if (it.has("musicTwoRowItemRenderer")) {
                val r = it.getJSONObject("musicTwoRowItemRenderer")
                val titleRuns = r.optJSONObject("title")?.optJSONArray("runs") ?: return null
                if (titleRuns.length() == 0) return null

                val title = titleRuns.optJSONObject(0)?.optString("text", "") ?: ""
                val videoId = r.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "") ?: ""
                if (videoId.isBlank()) return null

                val subtitleRuns = r.optJSONObject("subtitle")?.optJSONArray("runs")
                val artist = subtitleRuns?.optJSONObject(0)?.optString("text", "Unknown Artist") ?: "Unknown Artist"

                var thumbUrl = ""
                val thumbs = r.optJSONObject("thumbnailRenderer")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                }

                return Track(
                    id = videoId,
                    url = "https://www.youtube.com/watch?v=$videoId",
                    title = cleanTrackTitle(title),
                    artist = artist.replace(" - Topic", ""),
                    thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                    durationSeconds = 0L
                )
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun getAudioStreamUrl(
        url: String,
        quality: com.ayashii.kuro.data.settings.AudioQuality = com.ayashii.kuro.data.settings.AudioQuality.HIGH
    ): String? = withContext(Dispatchers.IO) {
        val cacheKey = "$url#${quality.name}"
        val now = System.currentTimeMillis()
        synchronized(streamUrlCache) {
            streamUrlCache.get(cacheKey)?.let { (cachedUrl, timestamp) ->
                if (now - timestamp < CACHE_EXPIRY_MS) {
                    return@withContext cachedUrl
                }
            }
        }

        try {
            val streamInfo = StreamInfo.getInfo(service, url)
            val audioStreams = streamInfo.audioStreams
            val httpAudios = audioStreams.filter { it.content != null && it.content.startsWith("http") }

            val chosenAudio = when (quality) {
                com.ayashii.kuro.data.settings.AudioQuality.LOW -> {
                    httpAudios.minByOrNull { it.averageBitrate }
                }
                com.ayashii.kuro.data.settings.AudioQuality.MEDIUM -> {
                    httpAudios.filter { it.averageBitrate in 90..180 }
                        .minByOrNull { kotlin.math.abs(it.averageBitrate - 128) }
                        ?: httpAudios.sortedBy { it.averageBitrate }.getOrNull(httpAudios.size / 2)
                }
                com.ayashii.kuro.data.settings.AudioQuality.HIGH -> {
                    val m4aStreams = httpAudios.filter {
                        it.format == org.schabi.newpipe.extractor.MediaFormat.M4A ||
                        it.format?.name.equals("m4a", ignoreCase = true) ||
                        it.format?.suffix.equals("m4a", ignoreCase = true) ||
                        it.format.toString().contains("M4A", ignoreCase = true)
                    }
                    m4aStreams.maxByOrNull { it.averageBitrate }
                        ?: httpAudios.maxByOrNull { it.averageBitrate }
                }
            } ?: httpAudios.maxByOrNull { it.averageBitrate } ?: httpAudios.firstOrNull()

            val resolved = chosenAudio?.content ?: run {
                // Fallback to muxed video/audio stream if separate audio stream is unavailable
                streamInfo.videoStreams
                    .filter { it.content != null && it.content.startsWith("http") }
                    .firstOrNull()?.content
            }
            if (resolved != null) {
                synchronized(streamUrlCache) {
                    streamUrlCache.put(cacheKey, Pair(resolved, now))
                }
            }
            resolved
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to get audio stream for $url: ${e.message}", e)
            null
        }
    }

    suspend fun getAudioStream(
        url: String,
        quality: com.ayashii.kuro.data.settings.AudioQuality = com.ayashii.kuro.data.settings.AudioQuality.HIGH
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val streamUrl = getAudioStreamUrl(url, quality) ?: return@withContext null
        val mime = if (streamUrl.contains("audio%2Fwebm") || streamUrl.contains("audio/webm")) {
            "audio/webm"
        } else if (streamUrl.contains("audio%2Fmp4") || streamUrl.contains("audio/mp4") || streamUrl.contains("itag=140") || streamUrl.contains("itag=141") || streamUrl.contains("itag=139")) {
            "audio/mp4"
        } else if (quality == com.ayashii.kuro.data.settings.AudioQuality.HIGH) {
            "audio/mp4"
        } else {
            null
        }
        ResolvedStream(streamUrl, mime)
    }

    suspend fun getFallbackStreamUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamInfo = StreamInfo.getInfo(service, url)
            val audioStreams = streamInfo.audioStreams
            val httpAudios = audioStreams.filter { it.content != null && it.content.startsWith("http") }
            val opusStream = httpAudios.filter {
                it.format == org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS ||
                it.format == org.schabi.newpipe.extractor.MediaFormat.WEBMA ||
                it.format?.name.equals("webm", ignoreCase = true) ||
                it.format?.name.equals("opus", ignoreCase = true)
            }.maxByOrNull { it.averageBitrate }
            opusStream?.content ?: httpAudios.maxByOrNull { it.averageBitrate }?.content
        } catch (_: Exception) {
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

            val client = HttpClientProvider.client
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
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
                .post(reqBody)

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
                                        thumbnailUrl = upgradeThumbnailUrl(thumb),
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
                    val rawThumb = item.thumbnails.maxByOrNull { it.width * it.height }?.url
                        ?: item.thumbnails.lastOrNull()?.url
                        ?: item.thumbnails.firstOrNull()?.url
                        ?: ""
                    Track(
                        id = item.url.substringAfter("v=").substringBefore("&"),
                        url = item.url,
                        title = cleanTrackTitle(item.name ?: "Unknown Title"),
                        artist = item.uploaderName?.replace(" - Topic", "") ?: "Unknown Artist",
                        thumbnailUrl = upgradeThumbnailUrl(rawThumb),
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
