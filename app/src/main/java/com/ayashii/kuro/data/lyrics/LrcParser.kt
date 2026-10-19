package com.ayashii.kuro.data.lyrics

object LrcParser {

    private val TIME_TAG_REGEX = Regex("""\[\s*(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\s*\]""")

    fun parse(lrcContent: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()

        lrcContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                val matches = TIME_TAG_REGEX.findAll(trimmed).toList()
                if (matches.isNotEmpty()) {
                    val lastMatch = matches.last()
                    val text = trimmed.substring(lastMatch.range.last + 1).trim()
                    if (text.isNotBlank()) {
                        for (match in matches) {
                            val min = match.groupValues[1].toLongOrNull() ?: 0L
                            val sec = match.groupValues[2].toLongOrNull() ?: 0L
                            val msStr = match.groupValues[3]
                            val ms = when (msStr.length) {
                                1 -> msStr.toLong() * 100
                                2 -> msStr.toLong() * 10
                                3 -> msStr.toLong()
                                else -> 0L
                            }
                            val timeMs = min * 60_000L + sec * 1_000L + ms
                            result.add(LyricLine(timeMs = timeMs, text = text))
                        }
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
