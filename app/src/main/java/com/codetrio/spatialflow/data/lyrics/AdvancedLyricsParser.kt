package com.codetrio.spatialflow.data.lyrics

import android.text.Html
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Converts karaoke formats from external lyrics catalogues into SpatialFlow's
 * existing LyricLine/LyricWord model. Raw payloads are wrapped with an internal
 * marker so they survive the existing LyricsResult and cache flow.
 */
object AdvancedLyricsParser {
    const val WORD_PREFIX = "SPATIALFLOW-WORDS:"
    const val LYRICS_PLUS_PREFIX = "SPATIALFLOW-LYRICSPLUS\n"

    enum class WordKind { QRC, YRC, KRC }

    private data class TimedToken(val startMs: Long, val durationMs: Long, val text: String)
    /** Preserve timed segments even when a lyric line contains no spaces. */
    private fun buildWords(tokens: List<TimedToken>): List<LyricWord> {
        val output = mutableListOf<LyricWord>()
        var cursor = 0
        tokens.forEach { token ->
            val text = decodeEntities(token.text)
            if (text.isEmpty()) return@forEach
            val start = cursor
            cursor += text.length
            output += LyricWord(
                text = text,
                absoluteStartTimeMs = token.startMs.coerceAtLeast(0L),
                durationMs = token.durationMs.coerceAtLeast(0L),
                charRange = start..(cursor - 1)
            )
        }
        return output
    }

    /** Recalculate character ranges after line-level trim and remove timings beyond visible text. */
    private fun alignWordRanges(words: List<LyricWord>, lineText: String): List<LyricWord> {
        val out = mutableListOf<LyricWord>()
        var cursor = 0
        words.forEach { word ->
            if (cursor >= lineText.length) return@forEach
            val visible = word.text.take(lineText.length - cursor)
            if (visible.isEmpty()) return@forEach
            out += word.copy(text = visible, charRange = cursor..(cursor + visible.length - 1))
            cursor += visible.length
        }
        return out
    }

    private fun addInterludes(input: List<LyricLine>): List<LyricLine> {
        if (input.isEmpty()) return input
        val sorted = input.sortedBy { it.startTimeMs }
        val output = mutableListOf<LyricLine>()
        val first = sorted.first()
        if (first.startTimeMs > 3000L) output += LyricLine(0L, "♪", isInterlude = true)
        sorted.forEachIndexed { index, line ->
            val previous = sorted.getOrNull(index - 1)
            if (previous != null) {
                val gap = line.startTimeMs - previous.startTimeMs
                if (gap > 5000L) {
                    val point = previous.startTimeMs + (gap / 3).coerceAtMost(3500L)
                    if (output.lastOrNull()?.isInterlude != true) output += LyricLine(point, "♪", isInterlude = true)
                }
            }
            output += line
        }
        return output
    }

    private fun isCreditLine(text: String): Boolean =
        Regex("""(?i)^(?:lyric(?:s)?\s*(?:by|:)|作词|作曲|编曲|produced by)\b""").containsMatchIn(text.trim())

    private fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        @Suppress("DEPRECATION")
        return Html.fromHtml(text).toString()
    }

    private fun JsonElement?.asObject(): JsonObject? = try {
        if (this != null && this.isJsonObject) this.asJsonObject else null
    } catch (_: Exception) { null }

    private fun JsonElement?.string(key: String): String? = this.asObject()?.string(key)
    private fun JsonElement?.obj(key: String): JsonObject? = this.asObject()?.get(key).asObject()
    private fun JsonElement?.array(key: String): JsonArray? = try {
        val element = this.asObject()?.get(key)
        if (element != null && element.isJsonArray) element.asJsonArray else null
    } catch (_: Exception) { null }

    private fun JsonObject.string(key: String): String? = try {
        val value = get(key)
        if (value == null || value.isJsonNull || !value.isJsonPrimitive) null else value.asString
    } catch (_: Exception) { null }

    private fun JsonObject.obj(key: String): JsonObject? = try {
        val value = get(key)
        if (value != null && value.isJsonObject) value.asJsonObject else null
    } catch (_: Exception) { null }

    private fun JsonObject.array(key: String): JsonArray? = try {
        val value = get(key)
        if (value != null && value.isJsonArray) value.asJsonArray else null
    } catch (_: Exception) { null }
}
