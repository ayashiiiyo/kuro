package com.ayashii.kuro.data.remote

import android.util.Log
import android.util.LruCache
import com.ayashii.kuro.data.model.HomeSection
import com.ayashii.kuro.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val CACHE_EXPIRY_MS = 20 * 60 * 1000L
    private val HOME_CACHE_TTL = 10 * 60 * 1000L
    private val SEARCH_CACHE_TTL = 5 * 60 * 1000L
    private val RADIO_CACHE_TTL = 30 * 60 * 1000L

    companion object {
        private const val YTM_CLIENT_NAME = "WEB_REMIX"
        private const val YTM_CLIENT_VERSION = "1.20241028.01.00"
        private const val YTM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        private const val YTM_PARAMS_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        private const val YTM_PARAMS_VIDEOS = "EgWKAQJQAWoKEAkQBRAKEAMQBA=="

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
            return if (u.contains(THUMB_WH_CHECK_REGEX)) {
                u.replace(THUMB_WH_REGEX, "=w1080-h1080-l90-rj")
            } else if (u.contains(THUMB_S_CHECK_REGEX)) {
                u.replace(THUMB_S_REGEX, "=w1080-h1080-l90-rj")
            } else if (u.contains("=")) {
                u.replace(THUMB_EQ_REGEX, "=w1080-h1080-l90-rj")
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

        val fallbackTracks = if (ytmTracks.isEmpty()) {
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

            auth.applyAuthToRequest(reqBuilder, payload, origin)

            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            reqBuilder.post(reqBody)

            val response = client.newCall(reqBuilder.build()).execute()
            val jsonStr = response.use { it.body?.string() } ?: return emptyList()
            com.ayashii.kuro.data.native.KuroNativeCore.parseYtmSearch(jsonStr)
                ?: parseYtmSearchTracks(org.json.JSONObject(jsonStr))
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

    suspend fun getHomeShelves(): List<HomeSection> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        synchronized(homeCacheLock) {
            homeCache?.let { (cached, time) ->
                if (now - time < HOME_CACHE_TTL) {
                    return@withContext cached
                }
            }
        }
        val ytmShelves = fetchYouTubeMusicShelves()
        if (ytmShelves.isNotEmpty()) {
            synchronized(homeCacheLock) { homeCache = Pair(ytmShelves, now) }
            return@withContext ytmShelves
        }

        val defaultTracks = searchTracks("Top Global Hits 2026")
        if (defaultTracks.isNotEmpty()) {
            listOf(
                HomeSection(
                    title = getLocString(com.ayashii.kuro.R.string.home_quick_picks_title, "Quick Picks"),
                    subtitle = getLocString(com.ayashii.kuro.R.string.home_quick_picks_sub, "Listen to your favorite music"),
                    tracks = defaultTracks.take(10)
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

    suspend fun getRadioTracks(videoId: String, tunerSetting: String? = null): List<Track> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        val cacheKey = if (tunerSetting != null) "${videoId}_$tunerSetting" else videoId
        val now = System.currentTimeMillis()
        synchronized(radioCache) {
            radioCache.get(cacheKey)?.let { (cached, time) ->
                if (now - time < RADIO_CACHE_TTL) {
                    return@withContext cached
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

    private suspend fun fetchYouTubeMusicShelves(): List<HomeSection> = withContext(Dispatchers.IO) {
        val result = mutableListOf<HomeSection>()
        try {
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"
            val client = HttpClientProvider.client
            val (hlCode, glCode) = resolveHlGl()

            fun fetchBrowse(browseId: String): org.json.JSONObject? {
                val payload = org.json.JSONObject().apply {
                    put("context", org.json.JSONObject().apply {
                        put("client", org.json.JSONObject().apply {
                            put("clientName", YTM_CLIENT_NAME)
                            put("clientVersion", YTM_CLIENT_VERSION)
                            put("hl", hlCode)
                            put("gl", glCode)
                        })
                    })
                    put("browseId", browseId)
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

            val isLoggedIn = auth.isLoggedIn()
            coroutineScope {
                val homeDeferred = if (isLoggedIn) async { fetchBrowse("FEmusic_home") } else null
                val exploreDeferred = async { fetchBrowse("FEmusic_explore") }
                val homeJson = homeDeferred?.await()
                val exploreJson = exploreDeferred.await()

                if (homeJson != null) {
                    result.addAll(parseYtmSections(homeJson, getLocString(com.ayashii.kuro.R.string.home_quick_picks_title, "Quick Picks")))
                }
                if (exploreJson != null) {
                    val exploreShelves = parseYtmSections(exploreJson, getLocString(com.ayashii.kuro.R.string.home_trending_shelf_title, "Trending Songs"))
                    result.addAll(if (isLoggedIn) exploreShelves else exploreShelves.take(2))
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
                if (cols.length() < 1) return null

                val col0Runs = cols.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs") ?: return null
                if (col0Runs.length() == 0) return null

                val title = col0Runs.optJSONObject(0)?.optString("text", "") ?: ""
                if (title.isBlank()) return null

                val videoId = extractVideoIdFromResponsiveItem(r, cols)
                if (videoId.isBlank()) return null

                val fixedCols = r.optJSONArray("fixedColumns")
                val (artist, durationSec) = extractArtistAndDuration(cols, fixedCols)

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
                    artist = artist,
                    thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                    durationSeconds = durationSec
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
                if (videoId.isBlank()) return null

                val subtitleRuns = r.optJSONObject("subtitle")?.optJSONArray("runs")
                var artist = "Unknown Artist"
                if (subtitleRuns != null) {
                    for (k in 0 until subtitleRuns.length()) {
                        val txt = subtitleRuns.optJSONObject(k)?.optString("text", "")?.trim().orEmpty()
                        if (!isMetadataNoiseToken(txt)) {
                            artist = txt.replace(" - Topic", "").trim()
                            break
                        }
                    }
                }

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
                    artist = artist,
                    thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                    durationSeconds = 0L
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
        quality: com.ayashii.kuro.data.settings.AudioQuality = com.ayashii.kuro.data.settings.AudioQuality.HIGH
    ): String? = withContext(Dispatchers.IO) {
        val subQuality = resolveActiveSubQuality(quality)
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
                    preferredPool.minByOrNull { it.averageBitrate }
                        ?: httpAudios.minByOrNull { it.averageBitrate }
                }
                com.ayashii.kuro.data.settings.AudioSubQuality.LOW_M4A_48 -> {
                    m4aStreams.minByOrNull { it.averageBitrate }
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

    suspend fun getLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
        val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
        val cookie = auth.getCookie()
        if (cookie.isNullOrBlank()) return@withContext emptyList()

        try {
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
                put("browseId", "FEmusic_liked_videos")
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

            auth.applyAuthToRequest(reqBuilder, payload, origin)
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            reqBuilder.post(reqBody)

            val resp = client.newCall(reqBuilder.build()).execute()
            val respBody = try {
                if (resp.isSuccessful) resp.body?.string() else null
            } finally {
                resp.close()
            }
            if (respBody != null) {
                val json = org.json.JSONObject(respBody)
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
                    ?: contents?.optJSONObject(0)?.optJSONObject("musicPlaylistShelfRenderer")
                val items = musicShelf?.optJSONArray("contents")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val itemObj = items.optJSONObject(i) ?: continue
                        val parsed = parseSingleTrackItem(itemObj)
                        if (parsed != null && parsed.id.isNotBlank()) {
                            trackList.add(parsed.copy(isFavorite = true))
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
