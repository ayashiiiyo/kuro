package com.ayashii.kuro.data.remote

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
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getTrendingTracks(): List<Track> = withContext(Dispatchers.IO) {
        try {
            val kiosk = service.kioskList.defaultKioskExtractor
            kiosk.fetchPage()
            val page = kiosk.initialPage
            page.items
                .filterIsInstance<StreamInfoItem>()
                .take(25)
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
            e.printStackTrace()
            searchTracks("Top Global Hits")
        }
    }

    suspend fun getAudioStreamUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamInfo = StreamInfo.getInfo(service, url)
            val audioStreams = streamInfo.audioStreams
            audioStreams.maxByOrNull { it.averageBitrate }?.content
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
