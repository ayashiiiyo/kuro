package com.ayashii.kuro.data.remote

import android.util.Log
import android.util.LruCache
import com.ayashii.kuro.data.model.CommentsResult
import com.ayashii.kuro.data.model.HomeSection
import com.ayashii.kuro.data.model.HomeSectionType
import com.ayashii.kuro.data.model.PlaylistBrowseDetails
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.model.YtmComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class ResolvedStream(
    val url: String,
    val mimeType: String? = null
)

class MusicRepository {

    private val service = ServiceList.YouTube
    private val CACHE_EXPIRY_MS = 20 * 60 * 1000L
    private val HOME_CACHE_TTL = 2 * 60 * 1000L
    private val SEARCH_CACHE_TTL = 5 * 60 * 1000L
    private val RADIO_CACHE_TTL = 30 * 60 * 1000L

    companion object {
        private const val YTM_CLIENT_NAME = "WEB_REMIX"
        private const val YTM_CLIENT_VERSION = "1.20260401.01.00"
        private const val YTM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        private const val YTM_PARAMS_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        private const val YTM_PARAMS_VIDEOS = "EgWKAQJQAWoKEAkQBRAKEAMQBA=="
        private const val YTM_PARAMS_ALBUMS = "EgWKAQIYAWoKEAkQBRAKEAMQBA=="
        private const val YTM_PARAMS_ARTISTS = "EgWKAQIgAWoKEAkQBRAKEAMQBA=="
        private const val YTM_PARAMS_PLAYLISTS = "EgWKAQIoAWoKEAkQBRAKEAMQBA=="

        private val THUMB_WH_CHECK_REGEX = Regex("=w\\d+-h\\d+")
        private val THUMB_WH_REGEX = Regex("=w\\d+-h\\d+[^&?]*")
        private val THUMB_S_CHECK_REGEX = Regex("=s\\d+")
        private val THUMB_S_REGEX = Regex("=s\\d+[^&?]*")
        private val THUMB_EQ_REGEX = Regex("=[^=]*$")
        private val DURATION_TEXT_REGEX = Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?$")

        private val METADATA_TYPE_LABELS = setOf(
            "song", "video", "lagu", "video musik", "music video", "single",
            "album", "ep", "episode", "podcast", "playlist", "artist", "artis",
            "•", "·", "-", "|", "explicit", "e"
        )

        private val streamUrlCache = LruCache<String, Pair<String, Long>>(60)
        private var homeCache: Pair<List<HomeSection>, Long>? = null
        private val homeCacheLock = Any()
        private val searchCache = LruCache<String, Pair<List<Track>, Long>>(30)
        private val radioCache = LruCache<String, Pair<List<Track>, Long>>(20)
        private val VISITOR_DATA_REGEX = Regex("\"VISITOR_DATA\"\\s*:\\s*\"([^\"]+)\"")

        @Volatile
        private var cachedVisitorData: String? = null

        @Volatile
        private var INSTANCE: MusicRepository? = null

        fun getInstance(): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicRepository().also { INSTANCE = it }
            }
        }
    }

    fun invalidateHomeCache() {
        synchronized(homeCacheLock) {
            homeCache = null
        }
        synchronized(searchCache) {
            searchCache.evictAll()
        }
    }

    private fun ensureVisitorData(): String? {
        cachedVisitorData?.let { return it }
        return try {
            val req = okhttp3.Request.Builder()
                .url("https://music.youtube.com/sw.js_data")
                .header("User-Agent", YTM_USER_AGENT)
                .header("Referer", "https://music.youtube.com/")
                .build()
            HttpClientProvider.baseClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val raw = resp.body?.string().orEmpty()
                val match = VISITOR_DATA_REGEX.find(raw)?.groupValues?.getOrNull(1)
                    ?: Regex("\"visitorData\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
                if (!match.isNullOrBlank()) {
                    cachedVisitorData = match
                }
                cachedVisitorData
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveHlGl(): Pair<String, String> {
        return try {
            com.ayashii.kuro.data.settings.SettingsManager.getInstance().getEffectiveHlAndGl()
        } catch (_: Throwable) {
            Pair("en", "US")
        }
    }

    private fun getLocString(resId: Int, fallback: String): String {
        return try {
            com.ayashii.kuro.data.settings.SettingsManager.getInstance().getLocalizedString(resId)
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun cleanTrackTitle(raw: String): String {
        return com.ayashii.kuro.data.native.KuroNativeCore.sanitizeTitle(raw)
    }

    fun upgradeThumbnailUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        var u = rawUrl.trim()
        if (u.startsWith("//")) {
            u = "https:$u"
        }
        if (u.contains("googleusercontent.com")) {
            val queryIdx = u.indexOf('?')
            val pathPart = if (queryIdx >= 0) u.substring(0, queryIdx) else u
            val queryPart = if (queryIdx >= 0) u.substring(queryIdx) else ""
            val resizedPath = if (pathPart.contains(THUMB_WH_CHECK_REGEX)) {
                pathPart.replace(THUMB_WH_REGEX, "=w1080-h1080-l90-rj")
            } else if (pathPart.contains(THUMB_S_CHECK_REGEX)) {
                pathPart.replace(THUMB_S_REGEX, "=w1080-h1080-l90-rj")
            } else if (pathPart.contains("=")) {
                pathPart.replace(THUMB_EQ_REGEX, "=w1080-h1080-l90-rj")
            } else {
                "$pathPart=w1080-h1080-l90-rj"
            }
            return resizedPath + queryPart
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
        val alreadyHighRes = upgraded.contains("googleusercontent.com") ||
            upgraded.contains("=w1080") ||
            upgraded.contains("maxresdefault")
        if (!alreadyHighRes && upgraded.contains("ytimg.com") && track.id.isNotBlank()) {
            val maxres = "https://i.ytimg.com/vi/${track.id}/maxresdefault.jpg"
            try {
                val req = okhttp3.Request.Builder()
                    .url(maxres)
                    .head()
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val client = HttpClientProvider.client
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext maxres
                    }
                }
            } catch (_: Exception) {}
        }
        upgraded
    }

    private fun parseDurationToSeconds(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L
        val clean = durationStr.replace(".", ":").trim()
        if (!DURATION_TEXT_REGEX.matches(clean)) return 0L
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

    private fun isMetadataNoiseToken(text: String): Boolean {
        val lower = text.trim().lowercase()
        if (lower.isEmpty()) return true
        if (lower in METADATA_TYPE_LABELS) return true
        if (DURATION_TEXT_REGEX.matches(lower.replace(".", ":"))) return true
        if (lower.contains("views") || lower.contains("ditonton") || lower.contains("plays") || lower.contains("diputar") || lower.contains("rb x") || lower.contains("jt x")) {
            return true
        }
        return false
    }

    private fun extractArtistAndDuration(cols: org.json.JSONArray?, fixedCols: org.json.JSONArray?): Pair<String, Long> {
        var artist = ""
        var fallbackArtist = ""
        var durationSec = 0L

        if (fixedCols != null) {
            for (f in 0 until fixedCols.length()) {
                val fRuns = fixedCols.optJSONObject(f)
                    ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs") ?: continue
                for (rIdx in 0 until fRuns.length()) {
                    val txt = fRuns.optJSONObject(rIdx)?.optString("text", "")?.trim().orEmpty()
                    val parsed = parseDurationToSeconds(txt)
                    if (parsed > 0L) {
                        durationSec = parsed
                        break
                    }
                }
            }
        }

        if (cols != null && cols.length() > 1) {
            for (cIdx in 1 until cols.length()) {
                val runs = cols.optJSONObject(cIdx)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs") ?: continue
                for (rIdx in 0 until runs.length()) {
                    val runObj = runs.optJSONObject(rIdx) ?: continue
                    val txt = runObj.optString("text", "").trim()
                    if (txt.isEmpty()) continue

                    val parsedDur = parseDurationToSeconds(txt)
                    if (parsedDur > 0L && durationSec <= 0L) {
                        durationSec = parsedDur
                        continue
                    }

                    if (isMetadataNoiseToken(txt)) continue

                    val browseId = runObj.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId", "")
                        .orEmpty()
                    if (browseId.startsWith("UC") || browseId.contains("artist", ignoreCase = true)) {
                        if (artist.isBlank()) {
                            artist = txt
                        }
                    } else if (fallbackArtist.isBlank()) {
                        fallbackArtist = txt
                    }
                }
            }
        }

        val finalArtist = artist.ifBlank { fallbackArtist }.ifBlank { "Unknown Artist" }
            .replace(" - Topic", "")
            .trim()
        return Pair(finalArtist, durationSec)
    }

    private fun extractVideoIdFromResponsiveItem(r: org.json.JSONObject, cols: org.json.JSONArray?): String {
        val fromPlaylistData = r.optJSONObject("playlistItemData")?.optString("videoId", "").orEmpty()
        if (fromPlaylistData.isNotBlank()) return fromPlaylistData

        if (cols != null) {
            for (c in 0 until cols.length()) {
                val runs = cols.optJSONObject(c)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs") ?: continue
                for (k in 0 until runs.length()) {
                    val vid = runs.optJSONObject(k)
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId", "")
                        .orEmpty()
                    if (vid.isNotBlank()) return vid
                }
            }
        }

        val fromOverlay = r.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId", "")
            .orEmpty()
        if (fromOverlay.isNotBlank()) return fromOverlay

        val fromDirectNav = r.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId", "")
            .orEmpty()
        if (fromDirectNav.isNotBlank()) return fromDirectNav

        val menuItems = r.optJSONObject("menu")?.optJSONObject("menuRenderer")?.optJSONArray("items")
        if (menuItems != null) {
            for (m in 0 until menuItems.length()) {
                val navVid = menuItems.optJSONObject(m)
                    ?.optJSONObject("menuNavigationItemRenderer")
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "")
                    .orEmpty()
                if (navVid.isNotBlank()) return navVid
            }
        }

        return ""
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

        val ytmTracks = coroutineScope {
            val songsDeferred = async { searchYouTubeMusicByParams(query, YTM_PARAMS_SONGS) }
            val generalDeferred = async { searchYouTubeMusicByParams(query, null) }
            val videosDeferred = async { searchYouTubeMusicByParams(query, YTM_PARAMS_VIDEOS) }

            val songsList = songsDeferred.await()
            val generalList = generalDeferred.await()
            val videosList = videosDeferred.await()

            val merged = LinkedHashMap<String, Track>()
            /* Prioritize official YouTube Music Songs (Official Audio) at #1-#5 */
            for (t in songsList.take(8)) {
                if (t.id.isNotBlank() && !merged.containsKey(t.id)) {
                    merged[t.id] = t
                }
            }
            for (t in generalList) {
                if (t.id.isNotBlank()) {
                    val existing = merged[t.id]
                    if (existing == null) {
                        merged[t.id] = t
                    } else if (existing.durationSeconds <= 0L && t.durationSeconds > 0L) {
                        merged[t.id] = existing.copy(durationSeconds = t.durationSeconds)
                    }
                }
            }
            for (t in songsList.drop(8)) {
                val existing = merged[t.id]
                if (existing == null) {
                    merged[t.id] = t
                } else if (existing.durationSeconds <= 0L && t.durationSeconds > 0L) {
                    merged[t.id] = existing.copy(durationSeconds = t.durationSeconds)
                }
            }
            for (t in videosList) {
                val existing = merged[t.id]
                if (existing == null) {
                    merged[t.id] = t
                } else if (existing.durationSeconds <= 0L && t.durationSeconds > 0L) {
                    merged[t.id] = existing.copy(durationSeconds = t.durationSeconds)
                }
            }
            merged.values.toList()
        }

        val fallbackTracks = if (ytmTracks.size < 8) {
            fetchNewPipeSearchTracks(query)
        } else {
            emptyList()
        }

        val combinedMap = LinkedHashMap<String, Track>()
        for (t in ytmTracks) {
            if (t.id.isNotBlank()) combinedMap[t.id] = t
        }
        for (t in fallbackTracks) {
            if (t.id.isNotBlank() && !combinedMap.containsKey(t.id)) {
                combinedMap[t.id] = t
            }
        }

        val finalResults = combinedMap.values.toList()
        if (finalResults.isNotEmpty()) {
            synchronized(searchCache) { searchCache.put(q, Pair(finalResults, now)) }
        }
        finalResults
    }

    private fun fetchNewPipeSearchTracks(query: String): List<Track> {
        return try {
            val searchExtractor = service.getSearchExtractor(
                query,
                listOf("videos"),
                ""
            )
            searchExtractor.fetchPage()
            val page = searchExtractor.initialPage
            page.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item ->
                    val vid = item.url.substringAfter("v=").substringBefore("&").trim()
                    if (vid.isBlank()) return@mapNotNull null
                    val rawThumb = item.thumbnails.maxByOrNull { it.width * it.height }?.url
                        ?: item.thumbnails.lastOrNull()?.url
                        ?: item.thumbnails.firstOrNull()?.url
                        ?: ""
                    Track(
                        id = vid,
                        url = "https://www.youtube.com/watch?v=$vid",
                        title = cleanTrackTitle(item.name ?: "Unknown Title"),
                        artist = item.uploaderName?.replace(" - Topic", "")?.trim() ?: "Unknown Artist",
                        thumbnailUrl = upgradeThumbnailUrl(rawThumb),
                        durationSeconds = item.duration.coerceAtLeast(0L)
                    )
                }
        } catch (e: Exception) {
            Log.e("MusicRepository", "NewPipe search failed: ${e.message}")
            emptyList()
        }
    }

    private fun searchYouTubeMusicByParams(query: String, params: String?): List<Track> {
        return try {
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"

            val visitorData = ensureVisitorData()
            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", YTM_CLIENT_NAME)
                        put("clientVersion", YTM_CLIENT_VERSION)
                        val (_, glCode) = resolveHlGl()
                        put("hl", "en")
                        put("gl", glCode)
                        if (!visitorData.isNullOrBlank()) {
                            put("visitorData", visitorData)
                        }
                    })
                })
                put("query", query)
                if (!params.isNullOrBlank()) {
                    put("params", params)
                }
            }

            val client = HttpClientProvider.client
            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/search")
                .header("Content-Type", "application/json")
                .header("User-Agent", YTM_USER_AGENT)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)
            if (!visitorData.isNullOrBlank()) {
                reqBuilder.header("X-Goog-Visitor-Id", visitorData)
            }

            auth.applyAuthToRequest(reqBuilder, payload, origin)

            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            reqBuilder.post(reqBody)

            val response = client.newCall(reqBuilder.build()).execute()
            val jsonStr = response.use { it.body?.string() } ?: return emptyList()
            val canUseNativeSongParser = params == null || params == YTM_PARAMS_SONGS || params == YTM_PARAMS_VIDEOS
            val nativeParsed = if (canUseNativeSongParser) {
                com.ayashii.kuro.data.native.KuroNativeCore.parseYtmSearch(jsonStr)
            } else null
            nativeParsed ?: parseYtmSearchTracks(org.json.JSONObject(jsonStr))
        } catch (e: Exception) {
            Log.e("MusicRepository", "YTM Search exception: ${e.message}")
            emptyList()
        }
    }

    private fun parseCardShelfTrack(cardShelf: org.json.JSONObject): List<Track> {
        val results = mutableListOf<Track>()
        try {
            val titleRuns = cardShelf.optJSONObject("title")?.optJSONArray("runs")
            val title = titleRuns?.optJSONObject(0)?.optString("text", "").orEmpty()
            var videoId = titleRuns?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId", "")
                .orEmpty()

            if (videoId.isBlank()) {
                videoId = cardShelf.optJSONObject("onTap")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "")
                    .orEmpty()
            }
            if (videoId.isBlank()) {
                videoId = cardShelf.optJSONObject("thumbnailOverlay")
                    ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicPlayButtonRenderer")
                    ?.optJSONObject("playNavigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "")
                    .orEmpty()
            }
            if (videoId.isBlank()) {
                val buttons = cardShelf.optJSONArray("buttons")
                if (buttons != null) {
                    for (b in 0 until buttons.length()) {
                        val bVid = buttons.optJSONObject(b)
                            ?.optJSONObject("buttonRenderer")
                            ?.optJSONObject("command")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId", "")
                            .orEmpty()
                        if (bVid.isNotBlank()) {
                            videoId = bVid
                            break
                        }
                    }
                }
            }
            if (videoId.isBlank()) {
                val menuItems = cardShelf.optJSONObject("menu")
                    ?.optJSONObject("menuRenderer")
                    ?.optJSONArray("items")
                if (menuItems != null) {
                    for (m in 0 until menuItems.length()) {
                        val mVid = menuItems.optJSONObject(m)
                            ?.optJSONObject("menuNavigationItemRenderer")
                            ?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId", "")
                            .orEmpty()
                        if (mVid.isNotBlank()) {
                            videoId = mVid
                            break
                        }
                    }
                }
            }

            if (videoId.isNotBlank() && title.isNotBlank()) {
                val subRuns = cardShelf.optJSONObject("subtitle")?.optJSONArray("runs")
                var artist = "Unknown Artist"
                var durationSec = 0L
                if (subRuns != null) {
                    for (k in 0 until subRuns.length()) {
                        val txt = subRuns.optJSONObject(k)?.optString("text", "")?.trim().orEmpty()
                        if (txt.isEmpty()) continue
                        val d = parseDurationToSeconds(txt)
                        if (d > 0L && durationSec <= 0L) {
                            durationSec = d
                            continue
                        }
                        if (!isMetadataNoiseToken(txt) && artist == "Unknown Artist") {
                            artist = txt.replace(" - Topic", "").trim()
                        }
                    }
                }

                val thumbs = cardShelf.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "").orEmpty()
                } else ""

                results.add(
                    Track(
                        id = videoId,
                        url = "https://www.youtube.com/watch?v=$videoId",
                        title = cleanTrackTitle(title),
                        artist = artist,
                        thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                        durationSeconds = durationSec
                    )
                )
            }

            val subContents = cardShelf.optJSONArray("contents")
            if (subContents != null) {
                for (i in 0 until subContents.length()) {
                    val itemObj = subContents.optJSONObject(i) ?: continue
                    parseSingleTrackItem(itemObj)?.let { results.add(it) }
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private fun parseYtmSearchTracks(root: org.json.JSONObject): List<Track> {
        val list = mutableListOf<Track>()
        val seenIds = HashSet<String>()
        try {
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return emptyList()

            for (tIdx in 0 until tabs.length()) {
                val sections = tabs.optJSONObject(tIdx)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: continue

                for (s in 0 until sections.length()) {
                    val secObj = sections.optJSONObject(s) ?: continue

                    secObj.optJSONObject("musicCardShelfRenderer")?.let { cardShelf ->
                        for (trk in parseCardShelfTrack(cardShelf)) {
                            if (trk.id.isNotBlank() && seenIds.add(trk.id)) {
                                list.add(trk)
                            }
                        }
                    }

                    val shelf = secObj.optJSONObject("musicShelfRenderer") ?: continue
                    val items = shelf.optJSONArray("contents") ?: continue
                    for (i in 0 until items.length()) {
                        val itemObj = items.optJSONObject(i) ?: continue
                        val parsed = parseSingleTrackItem(itemObj)
                        if (parsed != null && parsed.id.isNotBlank() && seenIds.add(parsed.id)) {
                            list.add(parsed)
                        }
                    }
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
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"
            val (hlCode, glCode) = resolveHlGl()
            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", YTM_CLIENT_NAME)
                        put("clientVersion", YTM_CLIENT_VERSION)
                        put("hl", hlCode)
                        put("gl", glCode)
                    })
                })
                put("input", query.trim())
            }
            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/music/get_search_suggestions")
                .header("Content-Type", "application/json")
                .header("User-Agent", YTM_USER_AGENT)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)
            auth.applyAuthToRequest(reqBuilder, payload, origin)
            reqBuilder.post(payload.toString().toRequestBody("application/json".toMediaType()))

            val jsonStr = HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
            if (!jsonStr.isNullOrBlank()) {
                val root = org.json.JSONObject(jsonStr)
                val sections = root.optJSONArray("contents")
                if (sections != null) {
                    val out = ArrayList<String>(10)
                    val seen = HashSet<String>()
                    for (s in 0 until sections.length()) {
                        val items = sections.optJSONObject(s)
                            ?.optJSONObject("searchSuggestionsSectionRenderer")
                            ?.optJSONArray("contents") ?: continue
                        for (i in 0 until items.length()) {
                            val itemObj = items.optJSONObject(i) ?: continue
                            val renderer = itemObj.optJSONObject("searchSuggestionRenderer")
                                ?: itemObj.optJSONObject("historySuggestionRenderer")
                                ?: continue
                            var suggestionText = renderer.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("searchEndpoint")
                                ?.optString("query", "")
                                ?.trim()
                                .orEmpty()
                            if (suggestionText.isEmpty()) {
                                val runs = renderer.optJSONObject("suggestion")?.optJSONArray("runs")
                                if (runs != null) {
                                    val sb = StringBuilder()
                                    for (r in 0 until runs.length()) {
                                        sb.append(runs.optJSONObject(r)?.optString("text", "").orEmpty())
                                    }
                                    suggestionText = sb.toString().trim()
                                }
                            }
                            if (suggestionText.isNotEmpty() && seen.add(suggestionText.lowercase())) {
                                out.add(suggestionText)
                            }
                        }
                    }
                    if (out.isNotEmpty()) {
                        return@withContext out
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("MusicRepository", "YTM suggestions fallback: ${e.message}")
        }
        emptyList()
    }

    @Volatile
    private var homeChipTokens: Map<String, String> = emptyMap()

    fun getDiscoveredMoodChips(): Map<String, String> = homeChipTokens

    suspend fun getHomeShelves(forceRefresh: Boolean = false, filterParams: String? = null): List<HomeSection> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && filterParams.isNullOrBlank()) {
            synchronized(homeCacheLock) {
                homeCache?.let { (cached, time) ->
                    if (now - time < HOME_CACHE_TTL) {
                        return@withContext cached
                    }
                }
            }
        }
        if (forceRefresh && !com.ayashii.kuro.data.auth.AuthManager.getInstance().isLoggedIn()) {
            cachedVisitorData = null
        }
        val ytmShelves = fetchYouTubeMusicShelves(filterParams = filterParams)
        if (ytmShelves.isNotEmpty()) {
            if (filterParams.isNullOrBlank()) {
                synchronized(homeCacheLock) { homeCache = Pair(ytmShelves, now) }
            }
            return@withContext ytmShelves
        }

        val defaultTracks = searchTracks("Top Global Hits 2026")
        if (defaultTracks.isNotEmpty()) {
            listOf(
                HomeSection(
                    title = getLocString(com.ayashii.kuro.R.string.home_quick_picks_title, "Quick Picks"),
                    subtitle = getLocString(com.ayashii.kuro.R.string.home_quick_picks_sub, "Listen to your favorite music"),
                    tracks = defaultTracks.take(16),
                    layoutType = HomeSectionType.COMPACT_COLUMNS
                ),
                HomeSection(
                    title = getLocString(com.ayashii.kuro.R.string.home_trending_shelf_title, "Trending Songs"),
                    subtitle = getLocString(com.ayashii.kuro.R.string.home_trending_shelf_sub, "Most played right now"),
                    tracks = defaultTracks.drop(10)
                )
            )
        } else {
            emptyList()
        }
    }

    suspend fun getTrendingTracks(): List<Track> = withContext(Dispatchers.IO) {
        searchTracks("Top Global Hits 2026")
    }

    suspend fun getPlaylistOrBrowseTracks(browseId: String?, playlistId: String?): List<Track> = withContext(Dispatchers.IO) {
        val details = getPlaylistBrowseDetails(browseId = browseId, playlistId = playlistId, fallbackTitle = "", fallbackSubtitle = "", fallbackThumb = "")
        details.tracks
    }

    suspend fun getPlaylistBrowseDetails(
        browseId: String?,
        playlistId: String?,
        fallbackTitle: String = "",
        fallbackSubtitle: String = "",
        fallbackThumb: String = ""
    ): PlaylistBrowseDetails = withContext(Dispatchers.IO) {
        var resolvedTitle = fallbackTitle
        var resolvedSubtitle = fallbackSubtitle
        var resolvedDesc = ""
        var resolvedThumb = fallbackThumb
        val tracks = mutableListOf<Track>()
        val seenIds = HashSet<String>()

        try {
            val rawBrowse = browseId?.trim().orEmpty()
            val rawPlaylist = playlistId?.trim().orEmpty()
            val isDirectMixQueue = rawPlaylist.startsWith("RDTMAK") ||
                rawPlaylist.startsWith("RDCLAK") ||
                rawPlaylist.startsWith("RDAMPL") ||
                rawPlaylist.startsWith("RDEM") ||
                rawPlaylist.startsWith("RDQM")

            if (isDirectMixQueue && rawBrowse.isBlank()) {
                val mixTracks = getWatchPlaylistTracks(rawPlaylist)
                for (t in mixTracks) {
                    if (t.id.isNotBlank() && seenIds.add(t.id)) {
                        tracks.add(t)
                    }
                }
            }

            if (tracks.isEmpty()) {
                val targetBrowse = when {
                    rawBrowse.isNotBlank() -> {
                        if (rawBrowse.startsWith("OLAK")) "VL$rawBrowse" else rawBrowse
                    }
                    rawPlaylist.isNotBlank() -> {
                        if (rawPlaylist.startsWith("VL") || rawPlaylist.startsWith("MPRE") || rawPlaylist.startsWith("UC") || rawPlaylist.startsWith("FEmusic_")) {
                            rawPlaylist
                        } else {
                            "VL$rawPlaylist"
                        }
                    }
                    else -> ""
                }

                if (targetBrowse.isNotBlank()) {
                    val json = postYtmBrowse(targetBrowse, allowAnonymous = true)
                    if (json != null) {
                        val headerNode = json.optJSONObject("header")
                            ?.let { h ->
                                h.optJSONObject("musicDetailHeaderRenderer")
                                    ?: h.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                                        ?.optJSONObject("header")
                                        ?.optJSONObject("musicDetailHeaderRenderer")
                                    ?: h.optJSONObject("musicImmersiveHeaderRenderer")
                                    ?: h.optJSONObject("musicVisualHeaderRenderer")
                            }
                            ?: json.optJSONObject("contents")
                                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                                ?.optJSONArray("tabs")
                                ?.optJSONObject(0)
                                ?.optJSONObject("tabRenderer")
                                ?.optJSONObject("content")
                                ?.optJSONObject("sectionListRenderer")
                                ?.optJSONArray("contents")
                                ?.optJSONObject(0)
                                ?.let { firstSec ->
                                    firstSec.optJSONObject("musicResponsiveHeaderRenderer")
                                        ?: firstSec.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                                            ?.optJSONObject("header")
                                            ?.optJSONObject("musicResponsiveHeaderRenderer")
                                }

                        if (headerNode != null) {
                            val hTitle = extractRunsText(headerNode.optJSONObject("title"))
                            if (hTitle.isNotBlank()) resolvedTitle = hTitle
                            val hSub = extractRunsText(headerNode.optJSONObject("subtitle"))
                                .ifBlank { extractRunsText(headerNode.optJSONObject("straplineTextOne")) }
                            if (hSub.isNotBlank()) resolvedSubtitle = hSub
                            val hSecondSub = extractRunsText(headerNode.optJSONObject("secondSubtitle"))
                            if (hSecondSub.isNotBlank()) {
                                resolvedSubtitle = if (resolvedSubtitle.isNotBlank()) "$resolvedSubtitle • $hSecondSub" else hSecondSub
                            }
                            val hDesc = extractRunsText(headerNode.optJSONObject("description"))
                                .ifBlank {
                                    extractRunsText(
                                        headerNode.optJSONObject("description")
                                            ?.optJSONObject("musicDescriptionShelfRenderer")
                                            ?.optJSONObject("description")
                                    )
                                }
                            if (hDesc.isNotBlank()) resolvedDesc = hDesc

                            val hThumbs = headerNode.optJSONObject("thumbnail")
                                ?.let { tObj ->
                                    tObj.optJSONObject("musicThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")
                                        ?.optJSONArray("thumbnails")
                                        ?: tObj.optJSONObject("croppedSquareThumbnailRenderer")
                                            ?.optJSONObject("thumbnail")
                                            ?.optJSONArray("thumbnails")
                                }
                            if (hThumbs != null && hThumbs.length() > 0) {
                                val u = hThumbs.optJSONObject(hThumbs.length() - 1)?.optString("url", "").orEmpty()
                                if (u.isNotBlank()) resolvedThumb = upgradeThumbnailUrl(u)
                            }
                        }

                        val paginated = parseBrowseTracksFromRoot(json, targetBrowse, maxPages = 6)
                        for (t in paginated) {
                            if (t.id.isNotBlank() && seenIds.add(t.id)) {
                                val enriched = if (t.thumbnailUrl.isBlank() && resolvedThumb.isNotBlank()) {
                                    t.copy(thumbnailUrl = resolvedThumb)
                                } else t
                                tracks.add(enriched)
                            }
                        }
                    }
                }
            }

            if (tracks.isEmpty() && rawPlaylist.isNotBlank()) {
                val nextTracks = getWatchPlaylistTracks(rawPlaylist.removePrefix("VL"))
                for (t in nextTracks) {
                    if (t.id.isNotBlank() && seenIds.add(t.id)) {
                        tracks.add(t)
                    }
                }
            }

            if (tracks.isEmpty() && rawBrowse.startsWith("VL")) {
                val stripped = rawBrowse.removePrefix("VL")
                val nextTracks = getWatchPlaylistTracks(stripped)
                for (t in nextTracks) {
                    if (t.id.isNotBlank() && seenIds.add(t.id)) {
                        tracks.add(t)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "getPlaylistBrowseDetails error: ${e.message}")
        }

        if (resolvedThumb.isBlank()) {
            resolvedThumb = tracks.firstOrNull()?.thumbnailUrl.orEmpty()
        }

        PlaylistBrowseDetails(
            title = resolvedTitle.ifBlank { "Mix" },
            subtitle = resolvedSubtitle,
            description = resolvedDesc,
            thumbnailUrl = resolvedThumb,
            tracks = tracks,
            browseId = browseId,
            playlistId = playlistId
        )
    }

    suspend fun getWatchPlaylistTracks(playlistId: String, videoId: String? = null): List<Track> = withContext(Dispatchers.IO) {
        if (playlistId.isBlank() && videoId.isNullOrBlank()) return@withContext emptyList()
        val cleanPid = playlistId.trim()
        try {
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"
            val visitorData = ensureVisitorData()
            val (hlCode, glCode) = resolveHlGl()

            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", YTM_CLIENT_NAME)
                        put("clientVersion", YTM_CLIENT_VERSION)
                        put("hl", hlCode)
                        put("gl", glCode)
                        if (!visitorData.isNullOrBlank()) {
                            put("visitorData", visitorData)
                        }
                    })
                })
                if (!videoId.isNullOrBlank()) {
                    put("videoId", videoId)
                }
                if (cleanPid.isNotBlank()) {
                    put("playlistId", cleanPid)
                }
                put("isAudioOnly", true)
                put("enablePersistentPlaylistPanel", true)
            }

            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/next")
                .header("Content-Type", "application/json")
                .header("User-Agent", YTM_USER_AGENT)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)
            if (!visitorData.isNullOrBlank()) {
                reqBuilder.header("X-Goog-Visitor-Id", visitorData)
            }

            auth.applyAuthToRequest(reqBuilder, payload, origin)
            reqBuilder.post(payload.toString().toRequestBody("application/json".toMediaType()))

            val jsonStr = HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string().orEmpty()
            }
            if (jsonStr.isBlank()) return@withContext emptyList()

            val parsed = parseWatchNextQueueItems(org.json.JSONObject(jsonStr), excludeVideoId = null)
            if (parsed.isNotEmpty()) return@withContext parsed
        } catch (e: Exception) {
            Log.e("MusicRepository", "getWatchPlaylistTracks error: ${e.message}")
        }
        emptyList()
    }

    suspend fun getRadioTracks(videoId: String, tunerSetting: String? = null, forceRefresh: Boolean = false): List<Track> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        val cacheKey = if (tunerSetting != null) "${videoId}_$tunerSetting" else videoId
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            synchronized(radioCache) {
                radioCache.get(cacheKey)?.let { (cached, time) ->
                    if (now - time < RADIO_CACHE_TTL) {
                        return@withContext cached
                    }
                }
            }
        }
        try {
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"

            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", YTM_CLIENT_NAME)
                        put("clientVersion", YTM_CLIENT_VERSION)
                        val (hlCode, glCode) = resolveHlGl()
                        put("hl", hlCode)
                        put("gl", glCode)
                    })
                })
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("isAudioOnly", true)
                put("tunerSettingValue", tunerSetting ?: "AUTOMIX_SETTING_NORMAL")
                put("enablePersistentPlaylistPanel", true)
            }

            val client = HttpClientProvider.client
            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/next")
                .header("Content-Type", "application/json")
                .header("User-Agent", YTM_USER_AGENT)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)

            auth.applyAuthToRequest(reqBuilder, payload, origin)

            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            reqBuilder.post(reqBody)

            val jsonStr = client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string().orEmpty()
            }
            if (jsonStr.isBlank()) return@withContext emptyList()

            val nativeParsed = com.ayashii.kuro.data.native.KuroNativeCore.parseAutomix(jsonStr, videoId)
            if (nativeParsed != null && nativeParsed.isNotEmpty()) {
                synchronized(radioCache) { radioCache.put(cacheKey, Pair(nativeParsed, now)) }
                return@withContext nativeParsed
            }

            val json = org.json.JSONObject(jsonStr)
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
                ?.optJSONObject("playlistPanelRenderer")?.optJSONArray("contents") ?: org.json.JSONArray()

            val radioList = mutableListOf<Track>()
            for (i in 0 until items.length()) {
                val rawItem = items.optJSONObject(i) ?: continue
                val unwrapped = rawItem.optJSONObject("playlistPanelVideoWrapperRenderer")
                    ?.optJSONObject("primaryRenderer") ?: rawItem
                val r = unwrapped.optJSONObject("playlistPanelVideoRenderer") ?: continue
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
                synchronized(radioCache) { radioCache.put(cacheKey, Pair(radioList, now)) }
                return@withContext radioList
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "getRadioTracks error: ${e.message}")
        }
        val npRelated = fetchNewPipeRelatedTracks(videoId)
        if (npRelated.isNotEmpty()) {
            synchronized(radioCache) { radioCache.put(cacheKey, Pair(npRelated, now)) }
        }
        npRelated
    }

    private fun fetchNewPipeRelatedTracks(videoId: String): List<Track> {
        return try {
            val streamInfo = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId")
            streamInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item ->
                    val vid = item.url.substringAfter("v=").substringBefore("&").trim()
                    if (vid.isBlank() || vid == videoId) return@mapNotNull null
                    val rawThumb = item.thumbnails.maxByOrNull { it.width * it.height }?.url
                        ?: item.thumbnails.lastOrNull()?.url
                        ?: item.thumbnails.firstOrNull()?.url
                        ?: ""
                    Track(
                        id = vid,
                        url = "https://www.youtube.com/watch?v=$vid",
                        title = cleanTrackTitle(item.name ?: "Unknown Title"),
                        artist = item.uploaderName?.replace(" - Topic", "")?.trim() ?: "Unknown Artist",
                        thumbnailUrl = upgradeThumbnailUrl(rawThumb),
                        durationSeconds = item.duration.coerceAtLeast(0L)
                    )
                }
        } catch (e: Exception) {
            Log.d("MusicRepository", "NewPipe related tracks fallback failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseWatchNextQueueItems(json: org.json.JSONObject, excludeVideoId: String?): List<Track> {
        val tabs = json.optJSONObject("contents")
            ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            ?.optJSONObject("tabbedRenderer")
            ?.optJSONObject("watchNextTabbedResultsRenderer")
            ?.optJSONArray("tabs") ?: return emptyList()

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
            ?.optJSONObject("playlistPanelRenderer")?.optJSONArray("contents") ?: org.json.JSONArray()

        val out = mutableListOf<Track>()
        val seen = HashSet<String>()
        for (i in 0 until items.length()) {
            val rawItem = items.optJSONObject(i) ?: continue
            val unwrapped = rawItem.optJSONObject("playlistPanelVideoWrapperRenderer")
                ?.optJSONObject("primaryRenderer") ?: rawItem
            val r = unwrapped.optJSONObject("playlistPanelVideoRenderer") ?: continue
            val vid = r.optString("videoId", "")
            if (vid.isBlank() || (excludeVideoId != null && vid == excludeVideoId)) continue
            if (!seen.add(vid)) continue

            val t = extractRunsText(r.optJSONObject("title"))
            val a = r.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "Unknown Artist") ?: "Unknown Artist"
            val durStr = extractRunsText(r.optJSONObject("lengthText"))
            val durSec = parseDurationToSeconds(durStr)

            val thumbs = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
            } else ""

            out.add(
                Track(
                    id = vid,
                    url = "https://www.youtube.com/watch?v=$vid",
                    title = cleanTrackTitle(t.ifBlank { "Unknown Title" }),
                    artist = a.replace(" - Topic", "").trim(),
                    thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                    durationSeconds = durSec
                )
            )
        }
        return out
    }

    private fun extractHomeMoodChips(root: org.json.JSONObject) {
        try {
            val chips = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONObject("header")
                ?.optJSONObject("chipCloudRenderer")
                ?.optJSONArray("chips") ?: return
            val map = LinkedHashMap<String, String>()
            for (i in 0 until chips.length()) {
                val chip = chips.optJSONObject(i)?.optJSONObject("chipCloudChipRenderer") ?: continue
                val text = extractRunsText(chip.optJSONObject("text"))
                val params = chip.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("params", "")
                    .orEmpty()
                if (text.isNotBlank() && params.isNotBlank()) {
                    map[text] = params
                }
            }
            if (map.isNotEmpty()) {
                homeChipTokens = map
            }
        } catch (_: Exception) {}
    }

    suspend fun getExploreShelves(browseId: String): List<HomeSection> = withContext(Dispatchers.IO) {
        try {
            val json = postYtmBrowse(browseId, allowAnonymous = true) ?: return@withContext emptyList()
            parseYtmSections(json, "Explore")
        } catch (e: Exception) {
            Log.e("MusicRepository", "getExploreShelves($browseId) error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchYouTubeMusicShelves(filterParams: String? = null): List<HomeSection> = withContext(Dispatchers.IO) {
        val result = mutableListOf<HomeSection>()
        try {
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"
            val client = HttpClientProvider.client
            val (hlCode, glCode) = resolveHlGl()
            val visitorData = ensureVisitorData()
            fun fetchBrowse(browseId: String, continuation: String? = null, params: String? = null): org.json.JSONObject? {
                val payload = org.json.JSONObject().apply {
                    put("context", org.json.JSONObject().apply {
                        put("client", org.json.JSONObject().apply {
                            put("clientName", YTM_CLIENT_NAME)
                            put("clientVersion", YTM_CLIENT_VERSION)
                            put("hl", hlCode)
                            put("gl", glCode)
                            if (!visitorData.isNullOrBlank()) {
                                put("visitorData", visitorData)
                            }
                        })
                    })
                    if (continuation != null) {
                        put("continuation", continuation)
                    } else {
                        put("browseId", browseId)
                        if (!params.isNullOrBlank()) {
                            put("params", params)
                        }
                    }
                }

                val reqBuilder = okhttp3.Request.Builder()
                    .url("$origin/youtubei/v1/browse")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", YTM_USER_AGENT)
                    .header("Origin", origin)
                    .header("Referer", "$origin/")
                    .header("X-Origin", origin)
                    .header("X-YouTube-Client-Name", "67")
                    .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)
                if (!visitorData.isNullOrBlank()) {
                    reqBuilder.header("X-Goog-Visitor-Id", visitorData)
                }

                auth.applyAuthToRequest(reqBuilder, payload, origin)
                reqBuilder.post(payload.toString().toRequestBody("application/json".toMediaType()))

                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return null
                        return org.json.JSONObject(body)
                    }
                }
                return null
            }

            coroutineScope {
                val homeDeferred = async { fetchBrowse("FEmusic_home", params = filterParams) }
                val exploreDeferred = if (filterParams.isNullOrBlank()) async { fetchBrowse("FEmusic_explore") } else null
                val homeJson = homeDeferred.await()
                val exploreJson = exploreDeferred?.await()

                val defaultHomeTitle = getLocString(com.ayashii.kuro.R.string.home_quick_picks_title, "Quick Picks")
                if (homeJson != null) {
                    if (filterParams.isNullOrBlank()) {
                        extractHomeMoodChips(homeJson)
                    }
                    result.addAll(parseYtmSections(homeJson, defaultHomeTitle))
                    var homeCont = homeJson.optJSONObject("contents")
                        ?.optJSONObject("singleColumnBrowseResultsRenderer")
                        ?.optJSONArray("tabs")
                        ?.optJSONObject(0)
                        ?.optJSONObject("tabRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("continuations")
                        ?.optJSONObject(0)
                        ?.optJSONObject("nextContinuationData")
                        ?.optString("continuation")
                        ?.takeIf { it.isNotBlank() }
                    val maxContPages = if (filterParams.isNullOrBlank()) 8 else 3
                    var contPages = 0
                    while (homeCont != null && contPages < maxContPages) {
                        val nextHomeJson = fetchBrowse("FEmusic_home", homeCont) ?: break
                        val contSecList = nextHomeJson.optJSONObject("continuationContents")
                            ?.optJSONObject("sectionListContinuation")
                        val contSections = contSecList?.optJSONArray("contents")
                        if (contSections != null) {
                            result.addAll(parseYtmSectionsArray(contSections, defaultHomeTitle))
                        }
                        homeCont = contSecList?.optJSONArray("continuations")
                            ?.optJSONObject(0)
                            ?.optJSONObject("nextContinuationData")
                            ?.optString("continuation")
                            ?.takeIf { it.isNotBlank() }
                        contPages++
                    }
                }
                if (exploreJson != null) {
                    val exploreShelves = parseYtmSections(exploreJson, getLocString(com.ayashii.kuro.R.string.home_trending_shelf_title, "Trending Songs"))
                    result.addAll(exploreShelves)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "fetchYouTubeMusicShelves error: ${e.message}")
        }
        result
    }

    private fun extractRunsText(obj: org.json.JSONObject?): String {
        val runs = obj?.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text", "").orEmpty())
        }
        return sb.toString().trim()
    }

    private fun parseYtmSections(root: org.json.JSONObject, defaultTitle: String): List<HomeSection> {
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs") ?: return emptyList()
        val sections = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()
        return parseYtmSectionsArray(sections, defaultTitle)
    }

    private fun parseYtmSectionsArray(sections: org.json.JSONArray, defaultTitle: String): List<HomeSection> {
        val list = mutableListOf<HomeSection>()
        try {
            for (s in 0 until sections.length()) {
                val secNode = sections.optJSONObject(s) ?: continue
                val gridObj = secNode.optJSONObject("gridRenderer")
                val shelfObj = secNode.optJSONObject("musicCarouselShelfRenderer")
                    ?: secNode.optJSONObject("musicShelfRenderer")
                    ?: secNode.optJSONObject("musicPlaylistShelfRenderer")
                    ?: gridObj
                    ?: continue

                val headerRenderer = shelfObj.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                val hRuns = headerRenderer?.optJSONObject("title")?.optJSONArray("runs")
                    ?: shelfObj.optJSONObject("title")?.optJSONArray("runs")
                var shelfTitle = ""
                if (hRuns != null) {
                    val sb = StringBuilder()
                    for (k in 0 until hRuns.length()) {
                        sb.append(hRuns.optJSONObject(k)?.optString("text", ""))
                    }
                    shelfTitle = sb.toString().trim()
                }
                if (shelfTitle.isBlank()) shelfTitle = defaultTitle

                val sRuns = headerRenderer?.optJSONObject("strapline")?.optJSONArray("runs")
                var strapline = ""
                if (sRuns != null) {
                    val sb = StringBuilder()
                    for (k in 0 until sRuns.length()) {
                        sb.append(sRuns.optJSONObject(k)?.optString("text", ""))
                    }
                    strapline = sb.toString().trim()
                }

                var headerAvatarUrl: String? = null
                val headerThumbs = headerRenderer?.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                if (headerThumbs != null && headerThumbs.length() > 0) {
                    val rawAvatar = headerThumbs.optJSONObject(headerThumbs.length() - 1)?.optString("url", "").orEmpty()
                    if (rawAvatar.isNotBlank()) headerAvatarUrl = upgradeThumbnailUrl(rawAvatar)
                }

                val items = shelfObj.optJSONArray("contents") ?: shelfObj.optJSONArray("items") ?: continue
                val tracks = mutableListOf<Track>()
                var hasResponsiveRows = false
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    if (it.has("musicResponsiveListItemRenderer")) hasResponsiveRows = true
                    val track = parseSingleTrackItem(it)
                    if (track != null && tracks.none { existing ->
                            com.ayashii.kuro.data.native.KuroNativeCore.isDuplicateOrVariantTrack(track, existing)
                        }
                    ) {
                        tracks.add(track)
                    }
                }

                if (tracks.isNotEmpty()) {
                    val lowerTitle = shelfTitle.lowercase()
                    val lowerSub = strapline.lowercase()
                    val layoutType = when {
                        gridObj != null || "speed dial" in lowerTitle -> HomeSectionType.SPEED_DIAL
                        "similar to" in lowerSub || !headerAvatarUrl.isNullOrBlank() -> HomeSectionType.SIMILAR_TO
                        hasResponsiveRows || "quick picks" in lowerTitle || "covers" in lowerTitle || "trending songs" in lowerTitle || "long listens" in lowerTitle -> HomeSectionType.COMPACT_COLUMNS
                        "forgotten" in lowerTitle || "live" in lowerTitle || "performances" in lowerTitle || "videos" in lowerTitle -> HomeSectionType.WIDE_CARDS
                        else -> HomeSectionType.SQUARE_CAROUSEL
                    }
                    list.add(
                        HomeSection(
                            title = shelfTitle,
                            subtitle = strapline,
                            tracks = tracks,
                            layoutType = layoutType,
                            headerAvatarUrl = headerAvatarUrl
                        )
                    )
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
                if (cols.length() < 1) return null

                val col0Runs = cols.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs") ?: return null
                if (col0Runs.length() == 0) return null

                val title = col0Runs.optJSONObject(0)?.optString("text", "") ?: ""
                if (title.isBlank()) return null

                val videoId = extractVideoIdFromResponsiveItem(r, cols)
                var browseId = r.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId", "")
                    .orEmpty()
                if (browseId.isBlank()) {
                    browseId = col0Runs.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId", "")
                        .orEmpty()
                }
                var playlistId = r.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchPlaylistEndpoint")
                    ?.optString("playlistId", "")
                    .orEmpty()
                if (playlistId.isBlank()) {
                    playlistId = r.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchPlaylistEndpoint")
                        ?.optString("playlistId", "")
                        .orEmpty()
                }
                if (videoId.isBlank() && browseId.isBlank() && playlistId.isBlank()) return null

                val fixedCols = r.optJSONArray("fixedColumns")
                val (artist, durationSec) = extractArtistAndDuration(cols, fixedCols)

                var thumbUrl = ""
                val thumbs = r.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                }

                val effectiveId = videoId.ifBlank { playlistId.ifBlank { browseId } }
                val effectiveUrl = if (videoId.isNotBlank()) {
                    "https://www.youtube.com/watch?v=$videoId"
                } else {
                    "https://music.youtube.com/playlist?list=${playlistId.ifBlank { browseId }}"
                }

                return Track(
                    id = effectiveId,
                    url = effectiveUrl,
                    title = cleanTrackTitle(title),
                    artist = artist,
                    thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                    durationSeconds = durationSec,
                    browseId = browseId.takeIf { it.isNotBlank() },
                    playlistId = playlistId.takeIf { it.isNotBlank() }
                )
            } else if (it.has("musicTwoRowItemRenderer")) {
                val r = it.getJSONObject("musicTwoRowItemRenderer")
                val titleRuns = r.optJSONObject("title")?.optJSONArray("runs") ?: return null
                if (titleRuns.length() == 0) return null

                val title = titleRuns.optJSONObject(0)?.optString("text", "") ?: ""
                var videoId = r.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "") ?: ""
                if (videoId.isBlank()) {
                    videoId = r.optJSONObject("thumbnailOverlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId", "")
                        .orEmpty()
                }
                if (videoId.isBlank()) {
                    val menuItems = r.optJSONObject("menu")?.optJSONObject("menuRenderer")?.optJSONArray("items")
                    if (menuItems != null) {
                        for (m in 0 until menuItems.length()) {
                            val mVid = menuItems.optJSONObject(m)
                                ?.optJSONObject("menuNavigationItemRenderer")
                                ?.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("watchEndpoint")
                                ?.optString("videoId", "")
                                .orEmpty()
                            if (mVid.isNotBlank()) {
                                videoId = mVid
                                break
                            }
                        }
                    }
                }
                val browseId = r.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId", "")
                    .orEmpty()
                var playlistId = r.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchPlaylistEndpoint")
                    ?.optString("playlistId", "")
                    .orEmpty()
                if (playlistId.isBlank()) {
                    playlistId = r.optJSONObject("thumbnailOverlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchPlaylistEndpoint")
                        ?.optString("playlistId", "")
                        .orEmpty()
                }
                if (videoId.isBlank() && browseId.isBlank() && playlistId.isBlank()) return null

                val subtitleRuns = r.optJSONObject("subtitle")?.optJSONArray("runs")
                var artist = "Unknown Artist"
                if (subtitleRuns != null) {
                    val parts = mutableListOf<String>()
                    for (k in 0 until subtitleRuns.length()) {
                        val txt = subtitleRuns.optJSONObject(k)?.optString("text", "")?.trim().orEmpty()
                        if (txt.isNotBlank() && txt != "•" && txt != "·") {
                            parts.add(txt.replace(" - Topic", "").trim())
                        }
                    }
                    if (parts.isNotEmpty()) {
                        artist = parts.take(2).joinToString(" • ")
                    }
                }

                var thumbUrl = ""
                val thumbs = r.optJSONObject("thumbnailRenderer")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                }

                val effectiveId = videoId.ifBlank { playlistId.ifBlank { browseId } }
                val effectiveUrl = if (videoId.isNotBlank()) {
                    "https://www.youtube.com/watch?v=$videoId"
                } else {
                    "https://music.youtube.com/playlist?list=${playlistId.ifBlank { browseId }}"
                }

                return Track(
                    id = effectiveId,
                    url = effectiveUrl,
                    title = cleanTrackTitle(title),
                    artist = artist,
                    thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                    durationSeconds = 0L,
                    browseId = browseId.takeIf { it.isNotBlank() },
                    playlistId = playlistId.takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {}
        return null
    }

    private fun resolveActiveSubQuality(
        quality: com.ayashii.kuro.data.settings.AudioQuality
    ): com.ayashii.kuro.data.settings.AudioSubQuality {
        return try {
            val active = com.ayashii.kuro.data.settings.SettingsManager.getInstance().audioSubQuality.value
            if (active.parent == quality) active else com.ayashii.kuro.data.settings.AudioSubQuality.defaultFor(quality)
        } catch (_: Throwable) {
            com.ayashii.kuro.data.settings.AudioSubQuality.defaultFor(quality)
        }
    }

    suspend fun getAudioStreamUrl(
        url: String,
        quality: com.ayashii.kuro.data.settings.AudioQuality = com.ayashii.kuro.data.settings.AudioQuality.HIGH,
        subQualityOverride: com.ayashii.kuro.data.settings.AudioSubQuality? = null
    ): String? = withContext(Dispatchers.IO) {
        val subQuality = subQualityOverride ?: resolveActiveSubQuality(quality)
        val cacheKey = "$url#${quality.name}#${subQuality.name}"
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

            val opusStreams = httpAudios.filter {
                it.format == org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS ||
                it.format == org.schabi.newpipe.extractor.MediaFormat.WEBMA ||
                it.format?.name.equals("webm", ignoreCase = true) ||
                it.format?.name.equals("opus", ignoreCase = true) ||
                it.content.contains("audio%2Fwebm") ||
                it.content.contains("itag=251") ||
                it.content.contains("itag=250") ||
                it.content.contains("itag=249")
            }

            val m4aStreams = httpAudios.filter {
                it.format == org.schabi.newpipe.extractor.MediaFormat.M4A ||
                it.format?.name.equals("m4a", ignoreCase = true) ||
                it.format?.suffix.equals("m4a", ignoreCase = true) ||
                it.format.toString().contains("M4A", ignoreCase = true) ||
                it.content.contains("audio%2Fmp4") ||
                it.content.contains("itag=140") ||
                it.content.contains("itag=141") ||
                it.content.contains("itag=139")
            }

            val preferredPool = if (subQuality.preferOpus) {
                opusStreams.ifEmpty { httpAudios }
            } else {
                m4aStreams.ifEmpty { httpAudios }
            }

            val chosenAudio = when (subQuality) {
                com.ayashii.kuro.data.settings.AudioSubQuality.HIGH_OPUS_160 -> {
                    opusStreams.maxByOrNull { it.averageBitrate }
                        ?: httpAudios.maxByOrNull { it.averageBitrate }
                }
                com.ayashii.kuro.data.settings.AudioSubQuality.HIGH_M4A_256 -> {
                    m4aStreams.maxByOrNull { it.averageBitrate }
                        ?: httpAudios.maxByOrNull { it.averageBitrate }
                }
                com.ayashii.kuro.data.settings.AudioSubQuality.MED_M4A_128,
                com.ayashii.kuro.data.settings.AudioSubQuality.MED_OPUS_128 -> {
                    preferredPool.minByOrNull { kotlin.math.abs(it.averageBitrate - 128) }
                        ?: httpAudios.minByOrNull { kotlin.math.abs(it.averageBitrate - 128) }
                }
                com.ayashii.kuro.data.settings.AudioSubQuality.LOW_OPUS_70 -> {
                    preferredPool.minByOrNull { kotlin.math.abs(it.averageBitrate - 70) }
                        ?: httpAudios.minByOrNull { it.averageBitrate }
                }
                com.ayashii.kuro.data.settings.AudioSubQuality.LOW_OPUS_50 -> {
                    preferredPool.filter { it.averageBitrate > 0 }.minByOrNull { it.averageBitrate }
                        ?: preferredPool.minByOrNull { it.averageBitrate }
                        ?: httpAudios.filter { it.averageBitrate > 0 }.minByOrNull { it.averageBitrate }
                        ?: httpAudios.minByOrNull { it.averageBitrate }
                }
                com.ayashii.kuro.data.settings.AudioSubQuality.LOW_M4A_48 -> {
                    m4aStreams.filter { it.averageBitrate > 0 }.minByOrNull { it.averageBitrate }
                        ?: m4aStreams.minByOrNull { it.averageBitrate }
                        ?: httpAudios.filter { it.averageBitrate > 0 }.minByOrNull { it.averageBitrate }
                        ?: httpAudios.minByOrNull { it.averageBitrate }
                }
            } ?: httpAudios.maxByOrNull { it.averageBitrate } ?: httpAudios.firstOrNull()

            val resolved = chosenAudio?.content ?: run {
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

    fun evictStreamUrl(songUrl: String) {
        synchronized(streamUrlCache) {
            val snapshot = streamUrlCache.snapshot()
            for (key in snapshot.keys) {
                if (key == songUrl || key.startsWith("$songUrl#") || snapshot[key]?.first == songUrl) {
                    streamUrlCache.remove(key)
                }
            }
        }
    }

    suspend fun getAudioStream(
        url: String,
        quality: com.ayashii.kuro.data.settings.AudioQuality = com.ayashii.kuro.data.settings.AudioQuality.HIGH
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val streamUrl = getAudioStreamUrl(url, quality) ?: return@withContext null
        val mime = if (
            streamUrl.contains("audio%2Fwebm") ||
            streamUrl.contains("audio/webm") ||
            streamUrl.contains("itag=251") ||
            streamUrl.contains("itag=250") ||
            streamUrl.contains("itag=249") ||
            streamUrl.contains("itag=774")
        ) {
            "audio/webm"
        } else if (
            streamUrl.contains("audio%2Fmp4") ||
            streamUrl.contains("audio/mp4") ||
            streamUrl.contains("itag=140") ||
            streamUrl.contains("itag=141") ||
            streamUrl.contains("itag=139")
        ) {
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

    /**
     * Two-way YouTube Music Like/Unlike synchronization via InnerTube API.
     * Mutates the user's official YouTube Music "Liked Music" library when logged in.
     */
    suspend fun reportPlaybackToYouTubeMusic(videoId: String): Boolean = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext false
        val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
        if (!auth.isLoggedIn() || auth.getCookie().isNullOrBlank()) return@withContext false

        try {
            val origin = "https://music.youtube.com"
            val (hlCode, glCode) = resolveHlGl()
            val cpnChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
            val rng = java.util.Random()
            val cpn = (1..16).map { cpnChars[rng.nextInt(cpnChars.length)] }.joinToString("")

            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", YTM_CLIENT_NAME)
                        put("clientVersion", YTM_CLIENT_VERSION)
                        put("hl", hlCode)
                        put("gl", glCode)
                    })
                })
                put("videoId", videoId)
                put("cpn", cpn)
                put("playbackContext", org.json.JSONObject().apply {
                    put("contentPlaybackContext", org.json.JSONObject().apply {
                        put("signatureTimestamp", 20024)
                    })
                })
            }

            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/player")
                .header("Content-Type", "application/json")
                .header("User-Agent", YTM_USER_AGENT)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)

            auth.applyAuthToRequest(reqBuilder, payload, origin)
            reqBuilder.post(payload.toString().toRequestBody("application/json".toMediaType()))

            val playerJsonStr = HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                resp.body?.string().orEmpty()
            }
            if (playerJsonStr.isBlank()) return@withContext false

            val playerJson = org.json.JSONObject(playerJsonStr)
            val trackingUrl = playerJson.optJSONObject("playbackTracking")
                ?.optJSONObject("videostatsPlaybackUrl")
                ?.optString("baseUrl", "")
                .orEmpty()

            val watchtimeUrl = playerJson.optJSONObject("playbackTracking")
                ?.optJSONObject("videostatsWatchtimeUrl")
                ?.optString("baseUrl", "")
                .orEmpty()

            if (trackingUrl.startsWith("http")) {
                val sep = if (trackingUrl.contains("?")) "&" else "?"
                val finalTrackingUrl = "${trackingUrl}${sep}ver=2&c=WEB_REMIX&cpn=$cpn&el=detailpage"
                val pingBuilder = okhttp3.Request.Builder()
                    .url(finalTrackingUrl)
                    .header("User-Agent", YTM_USER_AGENT)
                    .header("Origin", origin)
                    .header("Referer", "$origin/")
                    .get()
                auth.applyAuthToRequest(pingBuilder, null, origin)
                HttpClientProvider.client.newCall(pingBuilder.build()).execute().close()

                if (watchtimeUrl.startsWith("http")) {
                    val wSep = if (watchtimeUrl.contains("?")) "&" else "?"
                    val finalWatchUrl = "${watchtimeUrl}${wSep}ver=2&c=WEB_REMIX&cpn=$cpn&el=detailpage&st=0&et=30&cmt=30&state=playing"
                    val watchBuilder = okhttp3.Request.Builder()
                        .url(finalWatchUrl)
                        .header("User-Agent", YTM_USER_AGENT)
                        .header("Origin", origin)
                        .header("Referer", "$origin/")
                        .get()
                    auth.applyAuthToRequest(watchBuilder, null, origin)
                    HttpClientProvider.client.newCall(watchBuilder.build()).execute().close()
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.d("MusicRepository", "Playback reporting skipped: ${e.message}")
            false
        }
    }

    suspend fun setTrackLikeStatus(videoId: String, isLiked: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext false
        val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
        val cookie = auth.getCookie()
        if (cookie.isNullOrBlank()) return@withContext false

        try {
            val origin = "https://music.youtube.com"
            val endpoint = if (isLiked) "like/like" else "like/removelike"

            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", YTM_CLIENT_NAME)
                        put("clientVersion", YTM_CLIENT_VERSION)
                        val (hlCode, glCode) = resolveHlGl()
                        put("hl", hlCode)
                        put("gl", glCode)
                    })
                })
                put("target", org.json.JSONObject().apply {
                    put("videoId", videoId)
                })
            }

            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/$endpoint")
                .header("Content-Type", "application/json")
                .header("User-Agent", YTM_USER_AGENT)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)

            auth.applyAuthToRequest(reqBuilder, payload, origin)
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val req = reqBuilder.post(reqBody).build()

            HttpClientProvider.client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to sync like status to YTM: ${e.message}")
            false
        }
    }

    private fun postYtmBrowse(browseId: String, continuation: String? = null, allowAnonymous: Boolean = false): org.json.JSONObject? {
        val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
        val cookie = auth.getCookie()
        if (!allowAnonymous && cookie.isNullOrBlank()) return null

        val origin = "https://music.youtube.com"
        val (hlCode, glCode) = resolveHlGl()
        val visitorData = ensureVisitorData()
        val payload = org.json.JSONObject().apply {
            put("context", org.json.JSONObject().apply {
                put("client", org.json.JSONObject().apply {
                    put("clientName", YTM_CLIENT_NAME)
                    put("clientVersion", YTM_CLIENT_VERSION)
                    put("hl", hlCode)
                    put("gl", glCode)
                    if (!visitorData.isNullOrBlank()) {
                        put("visitorData", visitorData)
                    }
                })
            })
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("browseId", browseId)
            }
        }

        val client = HttpClientProvider.client
        val reqBuilder = okhttp3.Request.Builder()
            .url("$origin/youtubei/v1/browse")
            .header("Content-Type", "application/json")
            .header("User-Agent", YTM_USER_AGENT)
            .header("Origin", origin)
            .header("Referer", "$origin/")
            .header("X-Origin", origin)
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)
        if (!visitorData.isNullOrBlank()) {
            reqBuilder.header("X-Goog-Visitor-Id", visitorData)
        }

        auth.applyAuthToRequest(reqBuilder, payload, origin)
        val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
        reqBuilder.post(reqBody)

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            return org.json.JSONObject(body)
        }
    }

    private fun parseBrowseTracksFromRoot(
        firstJson: org.json.JSONObject,
        browseId: String,
        markFavorite: Boolean = false,
        maxPages: Int = 25
    ): List<Track> {
        val trackList = mutableListOf<Track>()
        val seenIds = HashSet<String>()

        val contentsObj = firstJson.optJSONObject("contents")
        val contents = contentsObj
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: contentsObj
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONObject("secondaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

        var shelf: org.json.JSONObject? = null
        if (contents != null) {
            for (i in 0 until contents.length()) {
                val obj = contents.optJSONObject(i) ?: continue
                shelf = obj.optJSONObject("musicShelfRenderer")
                    ?: obj.optJSONObject("musicPlaylistShelfRenderer")
                    ?: obj.optJSONObject("musicCarouselShelfRenderer")
                if (shelf != null) break
            }
        }

        val initialItems = shelf?.optJSONArray("contents")
        if (initialItems != null) {
            for (i in 0 until initialItems.length()) {
                val itemObj = initialItems.optJSONObject(i) ?: continue
                val parsed = parseSingleTrackItem(itemObj)
                if (parsed != null && parsed.id.isNotBlank() && seenIds.add(parsed.id)) {
                    trackList.add(if (markFavorite) parsed.copy(isFavorite = true) else parsed)
                }
            }
        }

        var continuation = extractContinuationToken(shelf)
        var pageCount = 1
        while (continuation != null && pageCount < maxPages) {
            val nextJson = postYtmBrowse(browseId, continuation, allowAnonymous = true) ?: break
            val contShelf = nextJson.optJSONObject("continuationContents")
                ?.optJSONObject("musicPlaylistShelfContinuation")
                ?: nextJson.optJSONObject("continuationContents")
                    ?.optJSONObject("musicShelfContinuation")
            val contItems = contShelf?.optJSONArray("contents")
                ?: nextJson.optJSONArray("onResponseReceivedActions")
                    ?.optJSONObject(0)
                    ?.optJSONObject("appendContinuationItemsAction")
                    ?.optJSONArray("continuationItems")

            if (contItems != null) {
                for (i in 0 until contItems.length()) {
                    val itemObj = contItems.optJSONObject(i) ?: continue
                    val parsed = parseSingleTrackItem(itemObj)
                    if (parsed != null && parsed.id.isNotBlank() && seenIds.add(parsed.id)) {
                        trackList.add(if (markFavorite) parsed.copy(isFavorite = true) else parsed)
                    }
                }
            }

            var nextToken = extractContinuationToken(contShelf)
            if (nextToken == null && contItems != null) {
                for (i in contItems.length() - 1 downTo 0) {
                    nextToken = contItems.optJSONObject(i)
                        ?.optJSONObject("continuationItemRenderer")
                        ?.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token")
                        ?.takeIf { it.isNotBlank() }
                    if (nextToken != null) break
                }
            }
            if (nextToken == continuation) break
            continuation = nextToken
            pageCount++
        }

        return trackList
    }

    private fun extractContinuationToken(shelf: org.json.JSONObject?): String? {
        if (shelf == null) return null
        val directToken = shelf.optJSONArray("continuations")
            ?.optJSONObject(0)
            ?.optJSONObject("nextContinuationData")
            ?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
        if (directToken != null) return directToken

        val items = shelf.optJSONArray("contents") ?: return null
        for (i in items.length() - 1 downTo 0) {
            val cont = items.optJSONObject(i)
                ?.optJSONObject("continuationItemRenderer")
                ?.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")
                ?.optString("token")
                ?.takeIf { it.isNotBlank() }
            if (cont != null) return cont
        }
        return null
    }

    private fun fetchBrowseTrackListPaginated(
        browseId: String,
        markFavorite: Boolean = false,
        maxPages: Int = 25
    ): List<Track> {
        val trackList = mutableListOf<Track>()
        val seenIds = HashSet<String>()

        val firstJson = postYtmBrowse(browseId) ?: return emptyList()
        val contentsObj = firstJson.optJSONObject("contents")
        val contents = contentsObj
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: contentsObj
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONObject("secondaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

        var shelf: org.json.JSONObject? = null
        if (contents != null) {
            for (i in 0 until contents.length()) {
                val obj = contents.optJSONObject(i) ?: continue
                shelf = obj.optJSONObject("musicShelfRenderer")
                    ?: obj.optJSONObject("musicPlaylistShelfRenderer")
                if (shelf != null) break
            }
        }

        val initialItems = shelf?.optJSONArray("contents")
        if (initialItems != null) {
            for (i in 0 until initialItems.length()) {
                val itemObj = initialItems.optJSONObject(i) ?: continue
                val parsed = parseSingleTrackItem(itemObj)
                if (parsed != null && parsed.id.isNotBlank() && seenIds.add(parsed.id)) {
                    trackList.add(if (markFavorite) parsed.copy(isFavorite = true) else parsed)
                }
            }
        }

        var continuation = extractContinuationToken(shelf)
        var pageCount = 1
        while (continuation != null && pageCount < maxPages) {
            val nextJson = postYtmBrowse(browseId, continuation) ?: break
            val contShelf = nextJson.optJSONObject("continuationContents")
                ?.optJSONObject("musicPlaylistShelfContinuation")
                ?: nextJson.optJSONObject("continuationContents")
                    ?.optJSONObject("musicShelfContinuation")
            val contItems = contShelf?.optJSONArray("contents")
                ?: nextJson.optJSONArray("onResponseReceivedActions")
                    ?.optJSONObject(0)
                    ?.optJSONObject("appendContinuationItemsAction")
                    ?.optJSONArray("continuationItems")

            if (contItems != null) {
                for (i in 0 until contItems.length()) {
                    val itemObj = contItems.optJSONObject(i) ?: continue
                    val parsed = parseSingleTrackItem(itemObj)
                    if (parsed != null && parsed.id.isNotBlank() && seenIds.add(parsed.id)) {
                        trackList.add(if (markFavorite) parsed.copy(isFavorite = true) else parsed)
                    }
                }
            }

            var nextToken = extractContinuationToken(contShelf)
            if (nextToken == null && contItems != null) {
                for (i in contItems.length() - 1 downTo 0) {
                    nextToken = contItems.optJSONObject(i)
                        ?.optJSONObject("continuationItemRenderer")
                        ?.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token")
                        ?.takeIf { it.isNotBlank() }
                    if (nextToken != null) break
                }
            }
            if (nextToken == continuation) break
            continuation = nextToken
            pageCount++
        }

        return trackList
    }

    suspend fun getLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
        val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
        val cookie = auth.getCookie()
        if (cookie.isNullOrBlank()) return@withContext emptyList()

        try {
            val tracks = fetchBrowseTrackListPaginated("FEmusic_liked_videos", markFavorite = true, maxPages = 30)
            if (tracks.isNotEmpty()) {
                return@withContext tracks
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Liked songs InnerTube fetch error: ${e.message}", e)
        }

        try {
            val playlistExtractor = service.getPlaylistExtractor("https://www.youtube.com/playlist?list=LL")
            playlistExtractor.fetchPage()
            val allTracks = mutableListOf<Track>()
            val seenIds = HashSet<String>()
            var page = playlistExtractor.initialPage
            var fallbackPages = 0
            while (true) {
                for (item in page.items.filterIsInstance<StreamInfoItem>()) {
                    val vidId = item.url.substringAfter("v=").substringBefore("&")
                    if (vidId.isNotBlank() && seenIds.add(vidId)) {
                        val rawThumb = item.thumbnails.maxByOrNull { it.width * it.height }?.url
                            ?: item.thumbnails.lastOrNull()?.url
                            ?: item.thumbnails.firstOrNull()?.url
                            ?: ""
                        allTracks.add(
                            Track(
                                id = vidId,
                                url = item.url,
                                title = cleanTrackTitle(item.name ?: "Unknown Title"),
                                artist = item.uploaderName?.replace(" - Topic", "") ?: "Unknown Artist",
                                thumbnailUrl = upgradeThumbnailUrl(rawThumb),
                                durationSeconds = item.duration,
                                isFavorite = true
                            )
                        )
                    }
                }
                if (!page.hasNextPage() || fallbackPages >= 20) break
                page = playlistExtractor.getPage(page.nextPage)
                fallbackPages++
            }
            allTracks
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to fetch liked tracks via NewPipe: ${e.message}")
            emptyList()
        }
    }

    suspend fun getUserPlaylists(): List<Triple<String, String, List<Track>>> = withContext(Dispatchers.IO) {
        val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
        if (auth.getCookie().isNullOrBlank()) return@withContext emptyList()

        try {
            val json = postYtmBrowse("FEmusic_liked_playlists") ?: return@withContext emptyList()
            val sections = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@withContext emptyList()

            val candidates = mutableListOf<Pair<String, String>>()
            val seenBrowseIds = HashSet<String>()

            for (s in 0 until sections.length()) {
                val sec = sections.optJSONObject(s) ?: continue
                val gridItems = sec.optJSONObject("gridRenderer")?.optJSONArray("items")
                if (gridItems != null) {
                    for (i in 0 until gridItems.length()) {
                        val twoRow = gridItems.optJSONObject(i)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
                        val browseId = twoRow.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                            ?.takeIf { it.startsWith("VL") || it.startsWith("PL") } ?: continue
                        if (browseId == "VLLM" || browseId == "LM") continue
                        val title = extractRunsText(twoRow.optJSONObject("title")).ifBlank { "Playlist" }
                        if (seenBrowseIds.add(browseId)) {
                            candidates.add(browseId to title)
                        }
                    }
                }
                val shelfItems = sec.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                if (shelfItems != null) {
                    for (i in 0 until shelfItems.length()) {
                        val row = shelfItems.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val browseId = row.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                            ?.takeIf { it.startsWith("VL") || it.startsWith("PL") } ?: continue
                        if (browseId == "VLLM" || browseId == "LM") continue
                        val title = extractRunsText(
                            row.optJSONArray("flexColumns")
                                ?.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                        ).ifBlank { "Playlist" }
                        if (seenBrowseIds.add(browseId)) {
                            candidates.add(browseId to title)
                        }
                    }
                }
            }

            candidates.map { (browseId, title) ->
                val normalizedId = if (browseId.startsWith("VL")) browseId else "VL$browseId"
                val tracks = fetchBrowseTrackListPaginated(normalizedId, markFavorite = false, maxPages = 15)
                Triple(browseId, title, tracks)
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to fetch user playlists: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun searchByCategoryFilter(query: String, filterType: String): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        when (filterType) {
            "Songs" -> searchYouTubeMusicByParams(query, YTM_PARAMS_SONGS)
            "Videos" -> searchYouTubeMusicByParams(query, YTM_PARAMS_VIDEOS)
            "Albums" -> searchYouTubeMusicByParams(query, YTM_PARAMS_ALBUMS)
            "Artists" -> searchYouTubeMusicByParams(query, YTM_PARAMS_ARTISTS)
            "Playlists" -> searchYouTubeMusicByParams(query, YTM_PARAMS_PLAYLISTS)
            else -> searchTracks(query)
        }
    }

    private val CREATE_COMMENT_PARAMS_REGEX = Regex("\"createCommentParams\"\\s*:\\s*\"([^\"]+)\"")

    private fun extractCreateCommentParamsDeep(jsonStr: String): String? {
        if (jsonStr.isBlank()) return null
        val m = CREATE_COMMENT_PARAMS_REGEX.find(jsonStr)?.groupValues?.getOrNull(1)
        return m?.takeIf { it.isNotBlank() }
    }

    private fun extractCommentTokensRecursive(node: Any?, outTokens: MutableList<String>) {
        when (node) {
            is org.json.JSONObject -> {
                val panelId = node.optString("panelIdentifier", "")
                val secId = node.optString("sectionIdentifier", "")
                val targetId = node.optString("targetId", "")
                val isCommentSection = panelId.contains("comment", ignoreCase = true) ||
                    secId.contains("comment", ignoreCase = true) ||
                    targetId.contains("comment", ignoreCase = true)

                if (isCommentSection) {
                    val subStr = node.toString()
                    val tokenMatches = Regex("\"(?:token|continuation)\"\\s*:\\s*\"([^\"]+)\"").findAll(subStr)
                    for (tm in tokenMatches) {
                        val tok = tm.groupValues.getOrNull(1).orEmpty()
                        if (tok.length >= 16 && tok !in outTokens) {
                            outTokens.add(tok)
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    extractCommentTokensRecursive(node.opt(k), outTokens)
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until node.length()) {
                    extractCommentTokensRecursive(node.opt(i), outTokens)
                }
            }
        }
    }

    private fun parseCommentsFromResponseJson(
        commentsJson: org.json.JSONObject,
        rawJsonStr: String,
        commentsList: MutableList<YtmComment>,
        seenIds: MutableSet<String>
    ): Pair<String, String?> {
        var commentCountText = ""
        var createCommentParams = extractCreateCommentParamsDeep(rawJsonStr)

        val entityMap = HashMap<String, org.json.JSONObject>()
        val mutations = commentsJson.optJSONObject("frameworkUpdates")
            ?.optJSONObject("entityBatchUpdate")
            ?.optJSONArray("mutations")
        if (mutations != null) {
            for (m in 0 until mutations.length()) {
                val payloadObj = mutations.optJSONObject(m)
                    ?.optJSONObject("payload")
                    ?.optJSONObject("commentEntityPayload") ?: continue
                val cid = payloadObj.optJSONObject("properties")?.optString("commentId", "").orEmpty()
                if (cid.isNotBlank()) {
                    entityMap[cid] = payloadObj
                }
            }
        }

        fun inspectItemsArray(items: org.json.JSONArray?) {
            if (items == null) return
            for (h in 0 until items.length()) {
                val item = items.optJSONObject(h) ?: continue
                val headerR = item.optJSONObject("commentsHeaderRenderer")
                if (headerR != null) {
                    val cText = extractRunsText(headerR.optJSONObject("countText"))
                        .ifBlank { extractRunsText(headerR.optJSONObject("commentsCount")) }
                    if (cText.isNotBlank()) commentCountText = cText
                    if (createCommentParams.isNullOrBlank()) {
                        createCommentParams = extractCreateCommentParamsDeep(headerR.toString())
                    }
                }

                val thread = item.optJSONObject("commentThreadRenderer") ?: continue
                val legacyComment = thread.optJSONObject("comment")?.optJSONObject("commentRenderer")
                if (legacyComment != null) {
                    val cid = legacyComment.optString("commentId", "c_$h")
                    val author = extractRunsText(legacyComment.optJSONObject("authorText")).ifBlank { "User" }
                    val textContent = extractRunsText(legacyComment.optJSONObject("contentText"))
                    val pubTime = extractRunsText(legacyComment.optJSONObject("publishedTimeText"))
                    val likes = extractRunsText(legacyComment.optJSONObject("voteCount"))
                    val thumbs = legacyComment.optJSONObject("authorThumbnail")?.optJSONArray("thumbnails")
                    val avatar = if (thumbs != null && thumbs.length() > 0) {
                        thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "").orEmpty()
                    } else ""
                    if (textContent.isNotBlank() && seenIds.add(cid)) {
                        commentsList.add(
                            YtmComment(
                                id = cid,
                                authorName = author,
                                authorAvatarUrl = avatar,
                                publishedTimeText = pubTime,
                                text = textContent,
                                likeCountText = likes,
                                isHearted = legacyComment.has("actionButtons") && legacyComment.toString().contains("creatorHeart")
                            )
                        )
                    }
                } else {
                    val outerVm = thread.optJSONObject("commentViewModel")
                    val vm = outerVm?.optJSONObject("commentViewModel") ?: outerVm
                    val cid = vm?.optString("commentId", "").orEmpty()
                    val entity = entityMap[cid]
                    if (entity != null) {
                        val props = entity.optJSONObject("properties")
                        val authorObj = entity.optJSONObject("author")
                        val toolbar = entity.optJSONObject("toolbar")
                        val textContent = props?.optJSONObject("content")?.optString("content", "").orEmpty()
                        val pubTime = props?.optString("publishedTime", "").orEmpty()
                        val authorName = authorObj?.optString("displayName", "User").orEmpty()
                        val avatar = authorObj?.optString("avatarThumbnailUrl", "").orEmpty()
                        val likes = toolbar?.optString("likeCountNotliked", "")
                            ?.ifBlank { toolbar.optString("likeCountLiked", "") }.orEmpty()
                        val hearted = !toolbar?.optString("heartActiveTooltip", "").isNullOrBlank()
                        if (textContent.isNotBlank() && seenIds.add(cid)) {
                            commentsList.add(
                                YtmComment(
                                    id = cid,
                                    authorName = authorName,
                                    authorAvatarUrl = avatar,
                                    publishedTimeText = pubTime,
                                    text = textContent,
                                    likeCountText = likes,
                                    isHearted = hearted
                                )
                            )
                        }
                    }
                }
            }
        }

        val actions = commentsJson.optJSONArray("onResponseReceivedEndpoints")
            ?: commentsJson.optJSONArray("onResponseReceivedActions")
        if (actions != null) {
            for (a in 0 until actions.length()) {
                val act = actions.optJSONObject(a) ?: continue
                inspectItemsArray(act.optJSONObject("reloadContinuationItemsCommand")?.optJSONArray("continuationItems"))
                inspectItemsArray(act.optJSONObject("appendContinuationItemsAction")?.optJSONArray("continuationItems"))
            }
        }

        val contItems = commentsJson.optJSONObject("continuationContents")
            ?.optJSONObject("itemSectionContinuation")
            ?.optJSONArray("contents")
        inspectItemsArray(contItems)

        if (commentsList.isEmpty() && entityMap.isNotEmpty()) {
            for ((cid, entity) in entityMap) {
                val props = entity.optJSONObject("properties")
                val authorObj = entity.optJSONObject("author")
                val toolbar = entity.optJSONObject("toolbar")
                val textContent = props?.optJSONObject("content")?.optString("content", "").orEmpty()
                val pubTime = props?.optString("publishedTime", "").orEmpty()
                val authorName = authorObj?.optString("displayName", "User").orEmpty()
                val avatar = authorObj?.optString("avatarThumbnailUrl", "").orEmpty()
                val likes = toolbar?.optString("likeCountNotliked", "")
                    ?.ifBlank { toolbar.optString("likeCountLiked", "") }.orEmpty()
                val hearted = !toolbar?.optString("heartActiveTooltip", "").isNullOrBlank()
                if (textContent.isNotBlank() && seenIds.add(cid)) {
                    commentsList.add(
                        YtmComment(
                            id = cid,
                            authorName = authorName,
                            authorAvatarUrl = avatar,
                            publishedTimeText = pubTime,
                            text = textContent,
                            likeCountText = likes,
                            isHearted = hearted
                        )
                    )
                }
            }
        }

        return Pair(commentCountText, createCommentParams)
    }

    suspend fun getTrackComments(videoId: String): CommentsResult = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext CommentsResult()
        val commentsList = mutableListOf<YtmComment>()
        val seenIds = HashSet<String>()
        var commentCountText = ""
        var createCommentParams: String? = null

        try {
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val origin = "https://www.youtube.com"
            val (hlCode, glCode) = resolveHlGl()
            val clientVersion = "2.20250214.01.00"
            val visitorData = ensureVisitorData()

            fun postWebNext(continuation: String? = null): Pair<org.json.JSONObject, String>? {
                val payload = org.json.JSONObject().apply {
                    put("context", org.json.JSONObject().apply {
                        put("client", org.json.JSONObject().apply {
                            put("clientName", "WEB")
                            put("clientVersion", clientVersion)
                            put("hl", hlCode)
                            put("gl", glCode)
                            if (!visitorData.isNullOrBlank()) {
                                put("visitorData", visitorData)
                            }
                        })
                    })
                    if (continuation != null) {
                        put("continuation", continuation)
                    } else {
                        put("videoId", videoId)
                    }
                }
                val reqBuilder = okhttp3.Request.Builder()
                    .url("$origin/youtubei/v1/next")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", YTM_USER_AGENT)
                    .header("Origin", origin)
                    .header("Referer", "$origin/watch?v=$videoId")
                    .header("X-Origin", origin)
                    .header("X-YouTube-Client-Name", "1")
                    .header("X-YouTube-Client-Version", clientVersion)
                if (!visitorData.isNullOrBlank()) {
                    reqBuilder.header("X-Goog-Visitor-Id", visitorData)
                }

                auth.applyAuthToRequest(reqBuilder, payload, origin)
                reqBuilder.post(payload.toString().toRequestBody("application/json".toMediaType()))
                HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body?.string() ?: return null
                    return Pair(org.json.JSONObject(body), body)
                }
            }

            val initialPair = postWebNext(null)
            if (initialPair != null) {
                val (initialJson, initialRaw) = initialPair
                createCommentParams = extractCreateCommentParamsDeep(initialRaw)

                val panels = initialJson.optJSONArray("engagementPanels")
                if (panels != null) {
                    for (p in 0 until panels.length()) {
                        val panel = panels.optJSONObject(p)?.optJSONObject("engagementPanelSectionListRenderer") ?: continue
                        val id = panel.optString("panelIdentifier", "")
                        if (id.contains("comment", ignoreCase = true)) {
                            val headerRenderer = panel.optJSONObject("header")
                                ?.optJSONObject("engagementPanelTitleHeaderRenderer")
                            val titleTxt = extractRunsText(headerRenderer?.optJSONObject("title"))
                            val countTxt = extractRunsText(headerRenderer?.optJSONObject("contextualInfo"))
                            if (countTxt.isNotBlank()) {
                                commentCountText = countTxt
                            } else if (titleTxt.isNotBlank()) {
                                commentCountText = titleTxt
                            }
                        }
                    }
                }

                val candidateTokens = mutableListOf<String>()
                extractCommentTokensRecursive(initialJson, candidateTokens)

                for (token in candidateTokens.take(3)) {
                    val stepPair = postWebNext(token) ?: continue
                    val (stepJson, stepRaw) = stepPair
                    val (cCount, cParams) = parseCommentsFromResponseJson(stepJson, stepRaw, commentsList, seenIds)
                    if (cCount.isNotBlank() && commentCountText.isBlank()) {
                        commentCountText = cCount
                    }
                    if (!cParams.isNullOrBlank() && createCommentParams.isNullOrBlank()) {
                        createCommentParams = cParams
                    }
                    if (commentsList.isNotEmpty() && !createCommentParams.isNullOrBlank()) {
                        break
                    }
                    if (commentsList.isEmpty()) {
                        val nestedTokens = mutableListOf<String>()
                        val reloadItems = stepJson.optJSONArray("onResponseReceivedEndpoints")
                            ?.optJSONObject(0)
                            ?.optJSONObject("reloadContinuationItemsCommand")
                            ?.optJSONArray("continuationItems")
                        if (reloadItems != null) {
                            for (r in 0 until reloadItems.length()) {
                                val subTok = reloadItems.optJSONObject(r)
                                    ?.optJSONObject("continuationItemRenderer")
                                    ?.optJSONObject("continuationEndpoint")
                                    ?.optJSONObject("continuationCommand")
                                    ?.optString("token", "")
                                    .orEmpty()
                                if (subTok.isNotBlank()) nestedTokens.add(subTok)
                            }
                        }
                        for (subTok in nestedTokens.take(2)) {
                            val subPair = postWebNext(subTok) ?: continue
                            val (subJson, subRaw) = subPair
                            val (subCount, subParams) = parseCommentsFromResponseJson(subJson, subRaw, commentsList, seenIds)
                            if (subCount.isNotBlank() && commentCountText.isBlank()) commentCountText = subCount
                            if (!subParams.isNullOrBlank() && createCommentParams.isNullOrBlank()) createCommentParams = subParams
                            if (commentsList.isNotEmpty()) break
                        }
                    }
                    if (commentsList.isNotEmpty()) break
                }
            }
        } catch (e: Exception) {
            Log.d("MusicRepository", "InnerTube comments fetch fallback: ${e.message}")
        }

        if (commentsList.isEmpty()) {
            try {
                val commentsInfo = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(
                    service,
                    "https://www.youtube.com/watch?v=$videoId"
                )
                if (commentCountText.isBlank() && commentsInfo.commentsCount > 0) {
                    commentCountText = "${commentsInfo.commentsCount}"
                }
                for (item in commentsInfo.relatedItems) {
                    val text = item.commentText?.content?.trim().orEmpty()
                    if (text.isBlank()) continue
                    val avatar = item.uploaderAvatars.lastOrNull()?.url.orEmpty()
                    val likes = if (item.likeCount > 0) item.likeCount.toString() else item.textualLikeCount.orEmpty()
                    val cid = item.commentId ?: "${item.uploaderName}_${commentsList.size}"
                    if (seenIds.add(cid)) {
                        commentsList.add(
                            YtmComment(
                                id = cid,
                                authorName = item.uploaderName ?: "YouTube User",
                                authorAvatarUrl = avatar,
                                publishedTimeText = item.textualUploadDate ?: "",
                                text = text,
                                likeCountText = likes,
                                isHearted = item.isHeartedByUploader
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.d("MusicRepository", "NewPipe comments fallback failed: ${e.message}")
            }
        }

        CommentsResult(
            comments = commentsList,
            commentCountText = commentCountText,
            createCommentParams = createCommentParams
        )
    }

    private fun encodeProtobufVarint(value: Int, out: java.io.ByteArrayOutputStream) {
        var v = value
        while ((v and 0x7F.inv()) != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }

    private fun synthesizeCreateCommentParams(videoId: String): String {
        val vidBytes = videoId.trim().toByteArray(Charsets.UTF_8)
        val inner = java.io.ByteArrayOutputStream()
        inner.write(0x10)
        inner.write(0x00)
        val innerBytes = inner.toByteArray()

        val outer = java.io.ByteArrayOutputStream()
        outer.write(0x12)
        encodeProtobufVarint(vidBytes.size, outer)
        outer.write(vidBytes)
        outer.write(0x2A)
        encodeProtobufVarint(innerBytes.size, outer)
        outer.write(innerBytes)
        outer.write(0x38)
        outer.write(0x01)

        return android.util.Base64.encodeToString(
            outer.toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
    }

    suspend fun saveRemotePlaylistToLibrary(playlistId: String, isSaved: Boolean): Boolean = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("VL").trim()
        if (cleanId.isBlank()) return@withContext false
        val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
        if (!auth.isLoggedIn() || auth.getCookie().isNullOrBlank()) return@withContext false

        try {
            val origin = "https://music.youtube.com"
            val endpoint = if (isSaved) "like/like" else "like/removelike"
            val (hlCode, glCode) = resolveHlGl()
            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", YTM_CLIENT_NAME)
                        put("clientVersion", YTM_CLIENT_VERSION)
                        put("hl", hlCode)
                        put("gl", glCode)
                    })
                })
                put("target", org.json.JSONObject().apply {
                    put("playlistId", cleanId)
                })
            }

            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/$endpoint")
                .header("Content-Type", "application/json")
                .header("User-Agent", YTM_USER_AGENT)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)

            auth.applyAuthToRequest(reqBuilder, payload, origin)
            reqBuilder.post(payload.toString().toRequestBody("application/json".toMediaType()))
            HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "saveRemotePlaylistToLibrary error: ${e.message}")
            false
        }
    }

    suspend fun postTrackComment(videoId: String, createCommentParams: String?, commentText: String): Boolean = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || commentText.isBlank()) return@withContext false
        val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
        if (!auth.isLoggedIn() || auth.getCookie().isNullOrBlank()) return@withContext false

        try {
            val fetchedParams = if (!createCommentParams.isNullOrBlank()) {
                createCommentParams
            } else {
                getTrackComments(videoId).createCommentParams
            }
            val candidateParams = listOfNotNull(
                fetchedParams?.takeIf { it.isNotBlank() },
                synthesizeCreateCommentParams(videoId)
            ).distinct()

            val (hlCode, glCode) = resolveHlGl()
            val visitorData = ensureVisitorData()
            val targets = listOf(
                Triple("https://www.youtube.com", "WEB", "2.20250214.01.00" to "1"),
                Triple("https://music.youtube.com", YTM_CLIENT_NAME, YTM_CLIENT_VERSION to "67")
            )

            for (paramsToUse in candidateParams) {
                for ((origin, clientName, verAndId) in targets) {
                    val (clientVer, clientIdNum) = verAndId
                    val payload = org.json.JSONObject().apply {
                        put("context", org.json.JSONObject().apply {
                            put("client", org.json.JSONObject().apply {
                                put("clientName", clientName)
                                put("clientVersion", clientVer)
                                put("hl", hlCode)
                                put("gl", glCode)
                                if (!visitorData.isNullOrBlank()) {
                                    put("visitorData", visitorData)
                                }
                            })
                        })
                        put("createCommentParams", paramsToUse)
                        put("commentText", commentText.trim())
                    }

                    val reqBuilder = okhttp3.Request.Builder()
                        .url("$origin/youtubei/v1/comment/create_comment")
                        .header("Content-Type", "application/json")
                        .header("User-Agent", YTM_USER_AGENT)
                        .header("Origin", origin)
                        .header("Referer", "$origin/watch?v=$videoId")
                        .header("X-Origin", origin)
                        .header("X-YouTube-Client-Name", clientIdNum)
                        .header("X-YouTube-Client-Version", clientVer)
                    if (!visitorData.isNullOrBlank()) {
                        reqBuilder.header("X-Goog-Visitor-Id", visitorData)
                    }

                    auth.applyAuthToRequest(reqBuilder, payload, origin)
                    reqBuilder.post(payload.toString().toRequestBody("application/json".toMediaType()))

                    val ok = HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
                        val bodyStr = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful || bodyStr.isBlank()) {
                            false
                        } else {
                            runCatching {
                                val json = JSONObject(bodyStr)
                                !json.has("error") && (
                                    json.has("actions") ||
                                        json.has("actionResults") ||
                                        json.has("frameworkUpdates")
                                    )
                            }.getOrDefault(false)
                        }
                    }
                    if (ok) return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            Log.e("MusicRepository", "postTrackComment failed: ${e.message}")
            false
        }
    }
}