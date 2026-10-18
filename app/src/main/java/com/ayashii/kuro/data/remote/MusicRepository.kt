package com.ayashii.kuro.data.remote

import android.util.Log
import com.ayashii.kuro.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class MusicRepository {

    private val service = ServiceList.YouTube

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
                        title = item.name ?: "Unknown Title",
                        artist = item.uploaderName ?: "Unknown Artist",
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
                        title = item.name ?: "Unknown Title",
                        artist = item.uploaderName ?: "Unknown Artist",
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
        try {
            val streamInfo = StreamInfo.getInfo(service, url)
            val audioStreams = streamInfo.audioStreams
            val bestAudio = audioStreams
                .filter { it.content != null && it.content.startsWith("http") }
                .maxByOrNull { it.averageBitrate }
                ?: audioStreams.firstOrNull()

            bestAudio?.content
        } catch (e: Exception) {
            Log.e("MusicRepository", "Get stream URL failed: ${e.message}", e)
            null
        }
    }
}
