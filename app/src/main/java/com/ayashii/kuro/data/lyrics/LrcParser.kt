package com.ayashii.kuro.data.lyrics

import org.json.JSONObject

object LrcParser {

    private val TIME_TAG_REGEX = Regex("""\[\s*(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\s*\]""")
    private val OFFSET_TAG_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

    fun unwrapNetEaseJsonText(raw: String): Pair<Long?, String>? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        return try {
            val obj = JSONObject(trimmed)
            val timeMs = if (obj.has("t")) obj.optLong("t", -1L).takeIf { it >= 0L } else null
            val cArr = obj.optJSONArray("c")
            if (cArr != null) {
                val sb = StringBuilder()
                for (i in 0 until cArr.length()) {
                    val token = cArr.optJSONObject(i)
                    if (token != null) {
                        sb.append(token.optString("tx", ""))
                    }
                }
                timeMs to sb.toString().trim()
            } else {
                timeMs to ""
            }
        } catch (_: Exception) {
            null to ""
        }
    }

    fun isJunkOrCreditLine(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        val unwrapped = unwrapNetEaseJsonText(t)
        if (unwrapped != null) {
            val inner = unwrapped.second
            if (inner.isEmpty()) return true
            return isJunkOrCreditLine(inner)
        }
        if ((t.startsWith("{") && t.endsWith("}")) || t.contains("\"tx\":") || t.contains("\"c\":[")) {
            return true
        }
        val lower = t.lowercase()
        if (lower.startsWith("——") || lower.startsWith("···")) return true
        if (lower.contains("纯音乐") ||
            lower.contains("请欣赏") ||
            lower.contains("无歌词") ||
            lower.contains("純音樂") ||
            lower.contains("instrumental") ||
            lower.contains("translation provided") ||
            lower.contains("版权所有") ||
            lower.contains("lirik tidak tersedia")
        ) {
            return true
        }
        return lower.startsWith("written by") ||
            lower.startsWith("produced by") ||
            lower.startsWith("composed by") ||
            lower.startsWith("arranged by") ||
            lower.startsWith("lyrics by") ||
            lower.startsWith("lyricist") ||
            lower.startsWith("composer") ||
            lower.startsWith("arranger") ||
            lower.startsWith("penulis lirik") ||
            lower.startsWith("komposer") ||
            lower.startsWith("aransemen") ||
            lower.startsWith("produser") ||
            lower.startsWith("作词") ||
            lower.startsWith("作詞") ||
            lower.startsWith("作曲") ||
            lower.startsWith("编曲") ||
            lower.startsWith("編曲") ||
            lower.startsWith("词曲") ||
            lower.startsWith("制作人") ||
            lower.startsWith("製作人") ||
            lower.startsWith("和声") ||
            lower.startsWith("混音") ||
            lower.startsWith("母带") ||
            lower.startsWith("录音") ||
            lower.startsWith("吉他") ||
            lower.startsWith("贝斯") ||
            lower.startsWith("鼓") ||
            lower.startsWith("弦乐") ||
            lower.startsWith("演唱") ||
            lower.startsWith("原唱") ||
            lower.startsWith("出品") ||
            lower.startsWith("发行") ||
            lower.startsWith("词:") ||
            lower.startsWith("词：") ||
            lower.startsWith("曲:") ||
            lower.startsWith("曲：") ||
            lower.startsWith("编:") ||
            lower.startsWith("编：")
    }

    fun sanitizeLyricText(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val unwrapped = unwrapNetEaseJsonText(trimmed)
        val candidate = unwrapped?.second ?: trimmed
        if (candidate.isEmpty() || isJunkOrCreditLine(candidate)) return null
        return candidate
    }

    fun sanitizeLines(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return emptyList()
        val cleaned = mutableListOf<LyricLine>()
        for (line in lines) {
            val cleanText = sanitizeLyricText(line.text) ?: continue
            val cleanTrans = line.translation?.let { trans ->
                val unwrappedTrans = unwrapNetEaseJsonText(trans)?.second ?: trans.trim()
                if (unwrappedTrans.isEmpty() || isJunkOrCreditLine(unwrappedTrans)) null else unwrappedTrans
            }
            cleaned.add(line.copy(text = cleanText, translation = cleanTrans))
        }
        return cleaned
    }

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
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    val unwrapped = unwrapNetEaseJsonText(trimmed)
                    val jsonTime = unwrapped?.first
                    val cleanText = unwrapped?.second?.let { sanitizeLyricText(it) }
                    if (jsonTime != null && !cleanText.isNullOrBlank()) {
                        rawLines.add(LyricLine(timeMs = (jsonTime + offsetMs).coerceAtLeast(0L), text = cleanText))
                    }
                    return@forEach
                }
                val matches = TIME_TAG_REGEX.findAll(trimmed).toList()
                if (matches.isNotEmpty()) {
                    val lastMatch = matches.last()
                    val rawText = trimmed.substring(lastMatch.range.last + 1).trim()
                    val cleanText = if (rawText.isBlank()) "" else (sanitizeLyricText(rawText) ?: return@forEach)
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
                        rawLines.add(LyricLine(timeMs = timeMs, text = cleanText))
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
            } else {
                result.add(line)
            }
        }

        return result
    }

    fun parsePlain(plainContent: String, durationMs: Long = 0L): List<LyricLine> {
        val lines = plainContent.lineSequence()
            .mapNotNull { sanitizeLyricText(it) }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) return emptyList()

        return lines.map { text ->
            LyricLine(timeMs = -1L, text = text)
        }
    }
}
