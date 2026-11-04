package com.ayashii.kuro.data.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String,
    var translation: String? = null
)

internal fun isIntroToken(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return true
    val lower = trimmed.lowercase()
    if (lower in setOf("♪", "♫", "🎶", "(instrumental)", "[instrumental]", "(intro)", "[intro]", "intro", "...", "---", "--", "null")) {
        return true
    }
    return lower.matches(Regex("^[♪♫🎶\\s\\-\\.\\(\\)\\[\\]]*(?:instrumental|intro|music)?[♪♫🎶\\s\\-\\.\\(\\)\\[\\]]*$"))
}

data class LyricsData(
    val trackId: String,
    val title: String,
    val artist: String,
    val lines: List<LyricLine>,
    val isSynced: Boolean = true
) {
    fun getCurrentLineIndex(positionMs: Long): Int {
        if (!isSynced || lines.isEmpty()) return -1

        val effectivePos = positionMs.coerceAtLeast(0L)
        val firstLine = lines[0]

        // Strict Intro Guard: If song is still playing opening instrumentals, no lyric is active!
        if (effectivePos < firstLine.timeMs) {
            return -1
        }

        // If line 0 is at 00:00 (timeMs == 0L), strictly prevent premature display during intros
        if (firstLine.timeMs == 0L) {
            // Case 1: Line 0 text is explicitly an intro placeholder or instrumental indicator
            if (isIntroToken(firstLine.text)) {
                val nextTime = if (lines.size > 1) lines[1].timeMs else Long.MAX_VALUE
                if (effectivePos < nextTime) {
                    return -1
                }
            }
            // Case 2: At timestamp 00:00 or leading instrumental prelude before vocals begin
            if (positionMs <= 0L) {
                return -1
            }
            if (lines.size > 1 && lines[1].timeMs >= 3000L && effectivePos < minOf(lines[1].timeMs / 2, 2000L)) {
                return -1
            }
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

        if (result == 0 && lines[0].timeMs == 0L && isIntroToken(lines[0].text)) {
            return -1
        }

        return result
    }
}
