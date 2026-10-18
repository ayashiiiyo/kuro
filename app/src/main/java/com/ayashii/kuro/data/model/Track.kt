package com.ayashii.kuro.data.model

data class Track(
    val id: String,
    val url: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long = 0,
    val streamUrl: String? = null,
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
