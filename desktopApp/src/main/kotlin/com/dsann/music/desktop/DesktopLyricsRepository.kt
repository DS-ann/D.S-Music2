package com.dsann.music.desktop

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class DesktopLyricLine(val startMs: Long, val text: String)
internal data class DesktopLyrics(
    val raw: String,
    val provider: String,
    val synced: Boolean,
    val lines: List<DesktopLyricLine>,
    val instrumental: Boolean = false
)

internal object DesktopLyricsRepository {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun fetch(
        title: String,
        artist: String,
        durationMs: Long,
        videoId: String? = null,
        album: String? = null
    ): DesktopLyrics? = withContext(Dispatchers.IO) {
        if (!videoId.isNullOrBlank()) {
            fetchYouTubeLyrics(videoId)?.let { return@withContext it }
        }
        fetchLrclib(title, artist, durationMs, album)
    }

    private fun fetchYouTubeLyrics(videoId: String): DesktopLyrics? = runCatching {
        val nextBody = JsonObject().apply {
            add("context", JsonObject().apply {
                add("client", JsonObject().apply {
                    addProperty("clientName", "WEB_REMIX")
                    addProperty("clientVersion", "1.20260531.05.00")
                    addProperty("hl", Locale.getDefault().language.ifBlank { "en" })
                    addProperty("gl", Locale.getDefault().country.ifBlank { "US" })
                })
            })
            addProperty("videoId", videoId)
        }
        val next = postJson("https://music.youtube.com/youtubei/v1/next", nextBody)
        val tabs = next.path("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs")
            ?.asJsonArray ?: return null
        val browseId = tabs.firstNotNullOfOrNull { tab ->
            tab.path("tabRenderer.endpoint.browseEndpoint.browseId")?.stringOrNull()
                ?.takeIf { it.startsWith("MPLYt") }
        } ?: return null

        val browseBody = JsonObject().apply {
            add("context", nextBody.get("context").deepCopy())
            addProperty("browseId", browseId)
        }
        val browse = postJson("https://music.youtube.com/youtubei/v1/browse", browseBody)
        val raw = browse.path("contents.sectionListRenderer.contents.0.musicDescriptionShelfRenderer.description.runs")
            ?.asJsonArray?.joinToString("") { it.path("text")?.stringOrNull().orEmpty() }
            ?: browse.path("contents.sectionListRenderer.contents.0.musicDescriptionShelfRenderer.description.simpleText")?.stringOrNull()
            ?: browse.path("contents.sectionListRenderer.contents.0.messageRenderer.text.simpleText")?.stringOrNull()
            ?: return null
        raw.toLyrics("YouTube Music")
    }.getOrNull()

    private fun postJson(url: String, body: JsonObject): JsonObject {
        val request = Request.Builder()
            .url("$url?key=$API_KEY&prettyPrint=false")
            .post(com.google.gson.Gson().toJson(body).toRequestBodyJson())
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20260531.05.00")
            .build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "YouTube lyrics request returned HTTP ${response.code}." }
            JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
        }
    }

    private fun fetchLrclib(title: String, artist: String, durationMs: Long, album: String?): DesktopLyrics? {
        if (title.isBlank()) return null
        val durationSeconds = (durationMs / 1000L).takeIf { it > 0L }
        val directUrl = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply {
                if (!album.isNullOrBlank()) addQueryParameter("album_name", album)
                if (durationSeconds != null) addQueryParameter("duration", durationSeconds.toString())
            }
            .build()
        val direct = getJson(directUrl.toString())
        toLyrics(direct)?.let { return it }

        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        val searchUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val results = getJsonArray(searchUrl.toString()) ?: return null
        val normalizedTitle = normalize(title)
        val normalizedArtist = normalize(artist)
        val candidates = results.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject
        }
        val best = candidates.maxByOrNull { item ->
            val foundTitle = normalize(item.get("trackName")?.stringOrNull().orEmpty())
            val foundArtist = normalize(item.get("artistName")?.stringOrNull().orEmpty())
            (if (foundTitle == normalizedTitle) 100 else if (foundTitle.contains(normalizedTitle) || normalizedTitle.contains(foundTitle)) 55 else 0) +
                (if (foundArtist == normalizedArtist) 60 else if (foundArtist.contains(normalizedArtist) || normalizedArtist.contains(foundArtist)) 25 else 0)
        } ?: return null
        return toLyrics(best)
    }

    private fun getJson(url: String): JsonObject? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
        }
    }.getOrNull()

    private fun getJsonArray(url: String): List<com.google.gson.JsonElement>? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            JsonParser.parseString(response.body?.string().orEmpty()).asJsonArray.toList()
        }
    }.getOrNull()

    private fun toLyrics(item: JsonObject?): DesktopLyrics? {
        if (item == null) return null
        val synced = item.get("syncedLyrics")?.stringOrNull()?.takeIf { it.isNotBlank() }
        val plain = item.get("plainLyrics")?.stringOrNull()?.takeIf { it.isNotBlank() }
        val instrumental = item.get("instrumental")?.let { runCatching { it.asBoolean }.getOrDefault(false) } ?: false
        val raw = synced ?: plain ?: if (instrumental) "[Instrumental]" else return null
        val provider = if (item.has("trackName")) "LRCLIB" else "YouTube Music"
        return DesktopLyrics(raw, provider, synced != null, parseLines(raw), instrumental)
    }

    private fun String.toLyrics(provider: String): DesktopLyrics? {
        val text = trim()
        if (text.isEmpty()) return null
        val lines = parseLines(text)
        return DesktopLyrics(text, provider, lines.any { it.startMs > 0L }, lines)
    }

    fun parseOffline(raw: String): DesktopLyrics =
        DesktopLyrics(raw, "Offline lyrics", raw.contains(Regex("""\\[\\d{1,2}:\\d{2}""")), parseLines(raw))

    private fun parseLines(raw: String): List<DesktopLyricLine> {
        val timePattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
        return raw.lineSequence().flatMap { line ->
            val matches = timePattern.findAll(line).toList()
            if (matches.isEmpty()) {
                val text = line.trim()
                if (text.isBlank() || text.startsWith("[ti:", true) || text.startsWith("[ar:", true) || text.startsWith("[al:", true)) emptySequence()
                else sequenceOf(DesktopLyricLine(0L, text))
            } else {
                val content = timePattern.replace(line, "").trim()
                matches.asSequence().map { match ->
                    val fraction = match.groupValues[3]
                    val fractionMs = when (fraction.length) {
                        1 -> fraction.toLong() * 100L
                        2 -> fraction.toLong() * 10L
                        3 -> fraction.toLong()
                        else -> 0L
                    }
                    val ms = (match.groupValues[1].toLong() * 60L + match.groupValues[2].toLong()) * 1000L + fractionMs
                    DesktopLyricLine(ms, content)
                }
            }
        }.filter { it.text.isNotBlank() }.sortedBy { it.startMs }.toList()
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun JsonObject.path(path: String): com.google.gson.JsonElement? {
        var current: com.google.gson.JsonElement? = this
        path.split(".").forEach { key ->
            current = when {
                current == null || current!!.isJsonNull -> return null
                current!!.isJsonObject -> current!!.asJsonObject.get(key)
                current!!.isJsonArray -> current!!.asJsonArray.getOrNull(key.toIntOrNull() ?: return null)
                else -> return null
            }
        }
        return current
    }

    private fun com.google.gson.JsonElement?.stringOrNull(): String? =
        if (this == null || this.isJsonNull) null else runCatching { asString }.getOrNull()

    private fun com.google.gson.JsonElement?.path(path: String): com.google.gson.JsonElement? {
        var current = this
        path.split(".").forEach { key ->
            current = when {
                current == null || current!!.isJsonNull -> return null
                current!!.isJsonObject -> current!!.asJsonObject.get(key)
                current!!.isJsonArray -> current!!.asJsonArray.getOrNull(key.toIntOrNull() ?: return null)
                else -> return null
            }
        }
        return current
    }

    private fun String.toRequestBodyJson() =
        this.toRequestBody("application/json; charset=utf-8".toMediaType())

    private fun com.google.gson.JsonArray.getOrNull(index: Int): com.google.gson.JsonElement? =
        if (index in 0 until size()) get(index) else null

    private const val API_KEY = "AIzaSyAO_JVGg4tq4r2T5Co2t8G3oG1d1dQ"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
}
