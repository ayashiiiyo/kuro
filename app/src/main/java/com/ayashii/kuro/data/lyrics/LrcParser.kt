package com.ayashii.kuro.data.lyrics

object LrcParser {

    private val LRC_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{2,3}))?\](.*)""")

    fun parse(lrcContent: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()

        lrcContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                val match = LRC_REGEX.find(trimmed)
                if (match != null) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val msStr = match.groupValues[3]
                    val ms = when (msStr.length) {
                        2 -> msStr.toLong() * 10
                        3 -> msStr.toLong()
                        else -> 0L
                    }
                    val timeMs = min * 60_000L + sec * 1_000L + ms
                    val text = match.groupValues[4].trim()
                    if (text.isNotBlank()) {
                        result.add(LyricLine(timeMs = timeMs, text = text))
                    }
                }
            }
        }

        return result.sortedBy { it.timeMs }
    }

    fun parsePlain(plainContent: String, durationMs: Long): List<LyricLine> {
        val lines = plainContent.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) return emptyList()

        val interval = if (durationMs > 0) durationMs / lines.size else 4000L
        return lines.mapIndexed { index, text ->
            LyricLine(timeMs = index * interval, text = text)
        }
    }
}
