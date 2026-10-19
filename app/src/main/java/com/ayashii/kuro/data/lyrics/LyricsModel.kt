package com.ayashii.kuro.data.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String,
    var translation: String? = null
)

data class LyricsData(
    val trackId: String,
    val title: String,
    val artist: String,
    val lines: List<LyricLine>,
    val isSynced: Boolean = true
) {
    fun getCurrentLineIndex(positionMs: Long, offsetMs: Long = 1200L): Int {
        if (!isSynced || lines.isEmpty()) return -1
        val effectivePos = (positionMs + offsetMs).coerceAtLeast(0L)

        // Strict Intro Guard: If song is still playing opening instrumentals, no lyric is active!
        if (lines.isNotEmpty() && effectivePos < lines[0].timeMs) {
            return -1
        }

        var left = 0
        var right = lines.size - 1
        var result = -1

        while (left <= right) {
            val mid = (left + right) / 2
            if (lines[mid].timeMs <= effectivePos) {
                result = mid
                left = mid + 1
            } else {
                right = mid - 1
            }
        }
        return result
    }
}
