package com.ayashii.kuro.data.lyrics

object TtmlParser {

    private val pTagRegex = Regex("""<p\s+([^>]+)>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val beginAttrRegex = Regex("""begin=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val endAttrRegex = Regex("""end=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val xmlTagRegex = Regex("""<[^>]+>""")

    fun parseTtml(ttml: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        if (ttml.isBlank()) return lines

        val matches = pTagRegex.findAll(ttml)
        for (match in matches) {
            val attrs = match.groupValues[1]
            val innerContent = match.groupValues[2]

            val beginStr = beginAttrRegex.find(attrs)?.groupValues?.get(1)
                ?: beginAttrRegex.find(innerContent)?.groupValues?.get(1)
                ?: continue

            val endStr = endAttrRegex.find(attrs)?.groupValues?.get(1)
                ?: endAttrRegex.find(innerContent)?.groupValues?.get(1)

            val beginMs = parseTimeToMs(beginStr) ?: continue
            val endMs = endStr?.let { parseTimeToMs(it) }

            val rawText = xmlTagRegex.replace(innerContent, "")
            val cleanText = unescapeXml(rawText).trim()

            if (cleanText.isNotEmpty()) {
                lines.add(LyricLine(timeMs = beginMs, text = cleanText, endTimeMs = endMs))
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    fun parseTimeToMs(timeStr: String): Long? {
        val trimmed = timeStr.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val clean = if (trimmed.endsWith("s", ignoreCase = true) && !trimmed.endsWith("ms", ignoreCase = true)) {
                trimmed.substring(0, trimmed.length - 1)
            } else if (trimmed.endsWith("ms", ignoreCase = true)) {
                return trimmed.substring(0, trimmed.length - 2).toDoubleOrNull()?.toLong()
            } else {
                trimmed
            }

            val colonCount = clean.count { it == ':' }
            when (colonCount) {
                0 -> {
                    val sec = clean.toDouble()
                    (sec * 1000.0).toLong()
                }
                1 -> {
                    val parts = clean.split(':')
                    val m = parts[0].toLong()
                    val s = parts[1].toDouble()
                    (m * 60_000L) + (s * 1000.0).toLong()
                }
                2 -> {
                    val parts = clean.split(':')
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toDouble()
                    (h * 3_600_000L) + (m * 60_000L) + (s * 1000.0).toLong()
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun unescapeXml(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }
}
