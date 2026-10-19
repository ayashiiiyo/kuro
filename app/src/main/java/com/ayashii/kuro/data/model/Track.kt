package com.ayashii.kuro.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Track(
    val id: String,
    val url: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long = 0,
    val streamUrl: String? = null,
    val mimeType: String? = null,
    val isFavorite: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return "--:--"
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

@Immutable
data class HomeSection(
    val title: String,
    val subtitle: String = "",
    val tracks: List<Track>
)
