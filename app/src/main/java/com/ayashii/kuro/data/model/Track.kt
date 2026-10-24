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
    val highResThumbnailUrl: String
        get() {
            if (thumbnailUrl.isBlank()) return thumbnailUrl
            var u = thumbnailUrl.trim()
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

    /** Returns a server-sized artwork URL for small UI slots to avoid downloading full artwork. */
    fun thumbnailUrlForDisplay(maxPixels: Int): String {
        val boundedSize = maxPixels.coerceIn(96, 1080)
        val highRes = highResThumbnailUrl
        return if (highRes.contains("googleusercontent.com")) {
            highRes.replace(Regex("=w\\d+-h\\d+[^&?]*"), "=w$boundedSize-h$boundedSize-l90-rj")
        } else {
            highRes
        }
    }

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
