package com.ayashii.kuro.data.lyrics

object LrcParser {

    private val TIME_TAG_REGEX = Regex("""\[\s*(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\s*\]""")
    private val OFFSET_TAG_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

    fun parse(lrcContent: String): List<LyricLine> {
        val offsetMatch = OFFSET_TAG_REGEX.find(lrcContent)
        val offsetMs = if (offsetMatch != null) {
            val rawOffset = offsetMatch.groupValues[1].trim()
            val sign = if (rawOffset.startsWith("-")) -1L else 1L
            val num = rawOffset.removePrefix("+").removePrefix("-").toLongOrNull() ?: 0L
            sign * num
        } else {
            0L
        }

        val rawLines = mutableListOf<LyricLine>()

        lrcContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                val matches = TIME_TAG_REGEX.findAll(trimmed).toList()
                if (matches.isNotEmpty()) {
                    val lastMatch = matches.last()
                    val text = trimmed.substring(lastMatch.range.last + 1).trim()
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
                        val timeMs = (min * 60_000L + sec * 1_000L + ms + offsetMs).coerceAtLeast(0L)
                        rawLines.add(LyricLine(timeMs = timeMs, text = text))
                    }
                }
            }
        }

        val sorted = rawLines.sortedBy { it.timeMs }
        val result = mutableListOf<LyricLine>()
        for (line in sorted) {
            if (line.text.isBlank()) {
                if (result.isNotEmpty() && result.last().endTimeMs == null) {
                    val prev = result.removeAt(result.size - 1)
                    result.add(prev.copy(endTimeMs = line.timeMs))
                }
                result.add(line)
            } else {
                result.add(line)
            }
        }

        return result
    }

    fun parsePlain(plainContent: String, durationMs: Long = 0L): List<LyricLine> {
        val lines = plainContent.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) return emptyList()

        return lines.map { text ->
            LyricLine(timeMs = -1L, text = text)
        }
    }
}
