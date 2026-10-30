package com.dsann.music.desktop

import com.dsann.music.data.innertube.OnlineAlbum
import com.dsann.music.data.innertube.OnlineArtist
import com.dsann.music.data.innertube.OnlineArtistRef
import com.dsann.music.data.innertube.OnlinePlaylist
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.SearchFilter
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.data.innertube.SearchResult
import com.dsann.music.data.innertube.SearchResultSection
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Windows-side adaptation of the phone app's current YouTube Music search pipeline:
 * mixed search plus independently requested category shelves, a best-match result,
 * podcast and episode typing, and the same category-specific result grouping.
 *
 * Android Context, SharedPreferences, NewPipe and Media3 are intentionally not used here.
 */
internal object DesktopYouTubeMusic {
    private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1"
    private const val YOUTUBE_BASE = "https://www.youtube.com/youtubei/v1"
    private const val API_KEY = "AIzaSyAO_JVGg4tq4r2T5Co2t8G3oG1d1dQ"
    private const val CLIENT_VERSION = "1.20260531.05.00"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun searchTop(query: String): SearchResult = withTimeout(30_000L) {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "Enter a search query." }

        coroutineScope {
            val mixedRequest = async(Dispatchers.IO) {
                runCatching { search(normalizedQuery, null) }.getOrNull()
            }
            val requests = SearchFilter.values().map { filter ->
                filter to async(Dispatchers.IO) {
                    runCatching { search(normalizedQuery, filter) }.getOrNull()
                }
            }

            val mixed = mixedRequest.await()
            val sections = requests.mapNotNull { (filter, request) ->
                val found = request.await()?.items.orEmpty()
                val unique = found.distinctBy(::itemKey).take(6)
                unique.takeIf { it.isNotEmpty() }?.let { SearchResultSection(filter, it) }
            }

            val candidates = (mixed?.items.orEmpty() + sections.flatMap { it.items })
                .distinctBy(::itemKey)
            check(candidates.isNotEmpty()) {
                "No results were returned. YouTube Music may be temporarily unavailable."
            }

            val normalized = normalizeSearchText(normalizedQuery)
            fun score(item: SearchItem): Int {
                val title = normalizeSearchText(itemTitle(item))
                val detail = normalizeSearchText(itemSubtitle(item))
                return when {
                    title == normalized -> 1000
                    title.startsWith(normalized) -> 800
                    normalized.isNotBlank() && title.contains(normalized) -> 600
                    normalized.isNotBlank() && detail.contains(normalized) -> 300
                    else -> 0
                }
            }

            val top = candidates.maxWithOrNull(
                compareBy<SearchItem> { score(it) }
                    .thenBy { if (it is SearchItem.Song) 1 else 0 }
            )
            val topKey = top?.let(::itemKey)
            val visibleSections = sections.mapNotNull { section ->
                val rest = section.items.filterNot { itemKey(it) == topKey }
                rest.takeIf { it.isNotEmpty() }?.let { section.copy(items = it) }
            }
            val remaining = visibleSections.flatMap { it.items }
                .ifEmpty { candidates.filterNot { itemKey(it) == topKey } }

            SearchResult(
                items = remaining,
                topResult = top,
                sections = visibleSections
            )
        }
    }

    /** Fetch the current YouTube Music watch-next queue for related autoplay tracks. */
    suspend fun relatedSongs(videoId: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val client = JsonObject().apply {
            addProperty("clientName", "WEB_REMIX")
            addProperty("clientVersion", CLIENT_VERSION)
            addProperty("hl", Locale.getDefault().language.ifBlank { "en" })
            addProperty("gl", Locale.getDefault().country.ifBlank { "US" })
        }
        val body = JsonObject().apply {
            add("context", JsonObject().apply { add("client", client) })
            addProperty("videoId", videoId)
        }
        val request = Request.Builder()
            .url("$MUSIC_BASE/next?key=$API_KEY&prettyPrint=false")
            .post(JsonParserBody.encode(body))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            check(response.isSuccessful) { "YouTube radio returned HTTP ${response.code}." }
            val json = JsonParser.parseString(text).asJsonObject
            val panel = json.path(
                "contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.0.tabRenderer.content.musicQueueRenderer.content.playlistPanelRenderer.contents"
            )?.asJsonArray ?: JsonArray()
            panel.mapNotNull { content ->
                runCatching {
                    val renderer = content.asJsonObject.getAsJsonObject("playlistPanelVideoRenderer")
                        ?: return@runCatching null
                    val id = renderer.get("videoId")?.stringOrNull() ?: return@runCatching null
                    val title = renderer.path("title.runs.0.text")?.stringOrNull()
                        ?: return@runCatching null
                    val artist = renderer.path("shortBylineText.runs")?.asJsonArray
                        ?.joinToString("") { it.asJsonObject.get("text")?.stringOrNull().orEmpty() }
                        ?.trim().orEmpty()
                    val duration = renderer.path("lengthText.runs.0.text")?.stringOrNull()
                    val thumbnail = DesktopSearchParser.highResThumbnail(renderer.path("thumbnail.thumbnails")?.asJsonArray)
                    OnlineSong(
                        videoId = id,
                        title = title,
                        artist = artist,
                        duration = duration,
                        durationMs = parseDurationLabel(duration),
                        thumbnailUrl = thumbnail
                    )
                }.getOrNull()
            }.distinctBy { it.videoId }
        }
    }

    private fun parseDurationLabel(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val parts = value.split(":").mapNotNull { it.toLongOrNull() }
        if (parts.size !in 2..3) return 0L
        val seconds = if (parts.size == 3) {
            parts[0] * 3600L + parts[1] * 60L + parts[2]
        } else {
            parts[0] * 60L + parts[1]
        }
        return seconds * 1000L
    }

    suspend fun browse(browseId: String): List<SearchItem> = withContext(Dispatchers.IO) {
        val normalizedId = when {
            browseId.startsWith("VL") || browseId.startsWith("MPSP") ||
                browseId.startsWith("MPRE") || browseId.startsWith("UC") -> browseId
            else -> "VL" + browseId
        }
        val client = JsonObject().apply {
            addProperty("clientName", "WEB_REMIX")
            addProperty("clientVersion", CLIENT_VERSION)
            addProperty("hl", Locale.getDefault().language.ifBlank { "en" })
            addProperty("gl", Locale.getDefault().country.ifBlank { "US" })
        }
        val body = JsonObject().apply {
            add("context", JsonObject().apply { add("client", client) })
            addProperty("browseId", normalizedId)
        }
        val request = Request.Builder()
            .url("$MUSIC_BASE/browse?key=$API_KEY&prettyPrint=false")
            .post(JsonParserBody.encode(body))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            check(response.isSuccessful) { "YouTube browse returned HTTP ${response.code}." }
            DesktopSearchParser.parseBrowse(JsonParser.parseString(text).asJsonObject)
        }
    }

    private suspend fun search(query: String, filter: SearchFilter?): SearchResult = withContext(Dispatchers.IO) {
        val live = filter == SearchFilter.LIVE
        val web = live
        val clientName = if (web) "WEB" else "WEB_REMIX"
        val clientVersion = if (web) "2.20250310.01.00" else CLIENT_VERSION
        val client = JsonObject().apply {
            addProperty("clientName", clientName)
            addProperty("clientVersion", clientVersion)
            addProperty("hl", Locale.getDefault().language.ifBlank { "en" })
            addProperty("gl", Locale.getDefault().country.ifBlank { "US" })
        }
        val context = JsonObject().apply { add("client", client) }
        val body = JsonObject().apply {
            add("context", context)
            addProperty("query", query)
            if (live) addProperty("params", "EgJAAQ==")
            else filter?.value?.let { addProperty("params", it) }
        }
        val base = if (web) YOUTUBE_BASE else MUSIC_BASE
        val url = "$base/search?key=$API_KEY&prettyPrint=false"
        val requestBody = JsonParserBody.encode(body)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept-Language", "${Locale.getDefault().language},en;q=0.9")
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-Origin", if (web) "https://www.youtube.com" else "https://music.youtube.com")
            .header("Origin", if (web) "https://www.youtube.com" else "https://music.youtube.com")
            .header("Referer", if (web) "https://www.youtube.com/" else "https://music.youtube.com/")
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", if (web) "1" else "67")
            .header("X-YouTube-Client-Version", clientVersion)
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("YouTube search returned HTTP ${response.code}.")
            }
            val json = JsonParser.parseString(text).asJsonObject
            if (json.entrySet().isEmpty()) throw IllegalStateException("YouTube search returned an empty response.")
            DesktopSearchParser.parse(json, live)
        }.let { parsed ->
            val typedItems = when (filter) {
                SearchFilter.VIDEOS -> parsed.items.map { if (it is SearchItem.Song) SearchItem.Song(it.song.copy(isVideo = true)) else it }
                SearchFilter.EPISODES -> parsed.items.map { if (it is SearchItem.Song) SearchItem.Song(it.song.copy(isEpisode = true)) else it }
                SearchFilter.PODCASTS -> parsed.items.map { if (it is SearchItem.Playlist) SearchItem.Playlist(it.playlist.copy(isPodcast = true)) else it }
                else -> parsed.items
            }
            parsed.copy(items = typedItems)
        }
    }

    private fun itemKey(item: SearchItem): String = when (item) {
        is SearchItem.Song -> "song:${item.song.videoId}"
        is SearchItem.Album -> "album:${item.album.browseId}"
        is SearchItem.Artist -> "artist:${item.artist.browseId}"
        is SearchItem.Playlist -> "playlist:${item.playlist.playlistId}"
    }

    fun itemTitle(item: SearchItem): String = when (item) {
        is SearchItem.Song -> item.song.title
        is SearchItem.Album -> item.album.title
        is SearchItem.Artist -> item.artist.title
        is SearchItem.Playlist -> item.playlist.title
    }

    fun itemSubtitle(item: SearchItem): String = when (item) {
        is SearchItem.Song -> item.song.artist + (item.song.duration?.let { " • $it" } ?: "")
        is SearchItem.Album -> item.album.artists.joinToString(", ") { it.name } +
            (item.album.year?.let { " • $it" } ?: "")
        is SearchItem.Artist -> item.artist.subscriberCount ?: "Artist"
        is SearchItem.Playlist -> item.playlist.author?.name ?: item.playlist.songCount ?: if (item.playlist.isPodcast) "Podcast" else "Playlist"
    }

    private fun normalizeSearchText(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}

private object JsonParserBody {
    fun encode(body: JsonObject): okhttp3.RequestBody =
        com.google.gson.Gson().toJson(body).toRequestBody("application/json; charset=utf-8".toMediaType())
}

private object DesktopSearchParser {
    fun parse(json: JsonObject, live: Boolean): SearchResult =
        if (live) parseLive(json) else parseMusic(json)

    private fun parseMusic(json: JsonObject): SearchResult {
        val items = mutableListOf<SearchItem>()
        var continuation: String? = null
        val sections = json.path("contents.tabbedSearchResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents")?.asJsonArray
            ?: json.path("contents.sectionListRenderer.contents")?.asJsonArray

        sections?.forEach { section ->
            val obj = section.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val shelf = obj.getAsJsonObject("musicShelfRenderer")
                ?: obj.getAsJsonObject("musicCardShelfRenderer")
                ?: obj.getAsJsonObject("itemSectionRenderer")
                ?: obj.getAsJsonObject("gridRenderer")
            if (shelf != null) {
                val children = shelf.getAsJsonArray("contents") ?: shelf.getAsJsonArray("items")
                children?.forEach { child ->
                    parseMusicItem(child.asJsonObject)?.let(items::add)
                }
                if (continuation == null) {
                    continuation = shelf.path("continuations.0.nextContinuationData.continuation")?.stringOrNull()
                }
            }
        }

        if (items.isEmpty()) {
            val continuationItems = json.path("continuationContents.musicShelfContinuation.contents")?.asJsonArray
                ?: json.path("continuationContents.itemSectionContinuation.contents")?.asJsonArray
            continuationItems?.forEach { child ->
                if (child.isJsonObject) parseMusicItem(child.asJsonObject)?.let(items::add)
            }
            continuation = json.path("continuationContents.musicShelfContinuation.continuations.0.nextContinuationData.continuation")?.stringOrNull()
                ?: json.path("continuationContents.itemSectionContinuation.continuations.0.nextContinuationData.continuation")?.stringOrNull()
        }

        return SearchResult(items.distinctBy(::simpleKey), continuation)
    }

    private fun parseMusicItem(item: JsonObject): SearchItem? {
        val renderer = item.getAsJsonObject("musicResponsiveListItemRenderer")
            ?: item.getAsJsonObject("musicTwoRowItemRenderer")
            ?: return null
        val isTwoRow = renderer.has("title") && !renderer.has("flexColumns")
        val title = if (isTwoRow) renderer.path("title.runs.0.text")?.stringOrNull()
            else renderer.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.stringOrNull()
        if (title.isNullOrBlank()) return null

        val thumbnail = highResThumbnail(
            if (isTwoRow) renderer.path("thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails")?.asJsonArray
                else renderer.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")?.asJsonArray
        )
        val nav = renderer.path("navigationEndpoint")?.asJsonObject
        val watch = renderer.path("overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint.watchEndpoint")?.asJsonObject
            ?: nav?.getAsJsonObject("watchEndpoint")
            ?: renderer.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.watchEndpoint")?.asJsonObject
        val browse = nav?.getAsJsonObject("browseEndpoint")
            ?: renderer.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.browseEndpoint")?.asJsonObject

        if (watch != null) {
            val videoId = watch.get("videoId")?.stringOrNull() ?: return null
            val runs = (if (isTwoRow) renderer.path("subtitle.runs") else renderer.path("flexColumns.1.musicResponsiveListItemFlexColumnRenderer.text.runs"))?.asJsonArray
            val details = runs?.mapNotNull { it.asJsonObjectOrNull()?.get("text")?.stringOrNull() }.orEmpty()
            val artistRun = runs?.firstOrNull { run ->
                val pageType = run.asJsonObjectOrNull()?.path("navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.stringOrNull()
                pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL"
            }?.asJsonObjectOrNull()
            val artist = artistRun?.get("text")?.stringOrNull()
                ?: details.firstOrNull { it.isNotBlank() && !it.matches(Regex("(\\d+:)?\\d+:\\d+")) }
                ?: "Unknown Artist"
            val artistId = artistRun?.path("navigationEndpoint.browseEndpoint.browseId")?.stringOrNull()
            val albumRun = runs?.firstOrNull { run ->
                run.asJsonObjectOrNull()?.path("navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.stringOrNull() == "MUSIC_PAGE_TYPE_ALBUM"
            }?.asJsonObjectOrNull()
            val duration = details.lastOrNull { it.matches(Regex("(\\d+:)?\\d+:\\d+")) }
            val videoType = watch.path("watchEndpointMusicSupportedConfigs.watchEndpointMusicConfig.musicVideoType")?.stringOrNull()
            val podcastRun = runs?.firstOrNull { run ->
                run.asJsonObjectOrNull()?.path("navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.stringOrNull() == "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"
            }?.asJsonObjectOrNull()
            return SearchItem.Song(
                OnlineSong(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    artistId = artistId,
                    albumName = albumRun?.get("text")?.stringOrNull(),
                    albumId = albumRun?.path("navigationEndpoint.browseEndpoint.browseId")?.stringOrNull(),
                    duration = duration,
                    durationMs = parseDuration(duration),
                    thumbnailUrl = thumbnail,
                    isVideo = !videoType.isNullOrBlank() && videoType != "MUSIC_VIDEO_TYPE_ATV",
                    isEpisode = videoType == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE",
                    podcastId = podcastRun?.path("navigationEndpoint.browseEndpoint.browseId")?.stringOrNull()
                )
            )
        }

        if (browse != null) {
            val browseId = browse.get("browseId")?.stringOrNull() ?: return null
            val pageType = browse.path("browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.stringOrNull()
            val runs = (if (isTwoRow) renderer.path("subtitle.runs") else renderer.path("flexColumns.1.musicResponsiveListItemFlexColumnRenderer.text.runs"))?.asJsonArray
            val subtitle = runs?.mapNotNull { it.asJsonObjectOrNull()?.get("text")?.stringOrNull() }?.joinToString("").orEmpty()
            val parts = subtitle.split(" • ")
            return when (pageType) {
                "MUSIC_PAGE_TYPE_ALBUM" -> SearchItem.Album(
                    OnlineAlbum(browseId, title = title, artists = listOf(OnlineArtistRef(parts.drop(1).firstOrNull().orEmpty())), year = parts.lastOrNull()?.toIntOrNull(), thumbnailUrl = thumbnail)
                )
                "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" -> SearchItem.Artist(
                    OnlineArtist(browseId, title, thumbnail, subscriberCount = subtitle.takeIf { it.isNotBlank() })
                )
                "MUSIC_PAGE_TYPE_PLAYLIST" -> SearchItem.Playlist(
                    OnlinePlaylist(browseId.removePrefix("VL"), title, thumbnailUrl = thumbnail, songCount = parts.lastOrNull())
                )
                "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" -> SearchItem.Playlist(
                    OnlinePlaylist(browseId, title, author = OnlineArtistRef(parts.drop(1).firstOrNull().orEmpty()), thumbnailUrl = thumbnail, isPodcast = true)
                )
                else -> when {
                    browseId.startsWith("UC") -> SearchItem.Artist(OnlineArtist(browseId, title, thumbnail, subscriberCount = subtitle))
                    browseId.startsWith("VL") || browseId.startsWith("PL") -> SearchItem.Playlist(OnlinePlaylist(browseId.removePrefix("VL"), title, thumbnailUrl = thumbnail, songCount = parts.lastOrNull()))
                    browseId.startsWith("MPREb") -> SearchItem.Album(OnlineAlbum(browseId, title = title, artists = listOf(OnlineArtistRef(parts.drop(1).firstOrNull().orEmpty())), year = parts.lastOrNull()?.toIntOrNull(), thumbnailUrl = thumbnail))
                    browseId.startsWith("MPSP") -> SearchItem.Playlist(OnlinePlaylist(browseId, title, author = OnlineArtistRef(parts.drop(1).firstOrNull().orEmpty()), thumbnailUrl = thumbnail, isPodcast = true))
                    else -> null
                }
            }
        }
        return null
    }

    fun parseBrowse(json: JsonObject): List<SearchItem> {
        val results = mutableListOf<SearchItem>()
        findObjects(json, "musicResponsiveListItemRenderer").forEach { renderer ->
            val wrapper = JsonObject().apply { add("musicResponsiveListItemRenderer", renderer) }
            parseMusicItem(wrapper)?.let(results::add)
        }
        findObjects(json, "musicTwoRowItemRenderer").forEach { renderer ->
            val wrapper = JsonObject().apply { add("musicTwoRowItemRenderer", renderer) }
            parseMusicItem(wrapper)?.let(results::add)
        }
        return results.distinctBy(::simpleKey)
    }

    private fun parseLive(json: JsonObject): SearchResult {
        val songs = findObjects(json, "videoRenderer").mapNotNull { renderer ->
            runCatching {
                val id = renderer.get("videoId")?.stringOrNull() ?: return@runCatching null
                val badges = renderer.getAsJsonArray("badges")?.mapNotNull { it.asJsonObjectOrNull()?.path("metadataBadgeRenderer.style")?.stringOrNull() }.orEmpty()
                val overlays = renderer.getAsJsonArray("thumbnailOverlays")?.mapNotNull { it.asJsonObjectOrNull()?.path("thumbnailOverlayTimeStatusRenderer.style")?.stringOrNull() }.orEmpty()
                val watching = renderer.path("viewCountText.simpleText")?.stringOrNull()?.contains("watching", true) == true
                val live = badges.any { it.contains("LIVE_NOW") } || "LIVE" in overlays || (renderer.get("lengthText") == null && watching)
                if (!live) return@runCatching null
                val title = renderer.path("title.runs.0.text")?.stringOrNull()
                    ?: renderer.path("title.simpleText")?.stringOrNull() ?: return@runCatching null
                val artist = renderer.path("ownerText.runs.0.text")?.stringOrNull()
                    ?: renderer.path("longBylineText.runs.0.text")?.stringOrNull() ?: "Live"
                val thumbnail = highResThumbnail(renderer.path("thumbnail.thumbnails")?.asJsonArray)
                    ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                OnlineSong(id, title, artist, thumbnailUrl = thumbnail, isVideo = true, isLive = true)
            }.getOrNull()
        }.distinctBy { it.videoId }
        return SearchResult(songs.map { SearchItem.Song(it) })
    }

    private fun findObjects(root: JsonElement?, key: String): List<JsonObject> {
        val found = mutableListOf<JsonObject>()
        fun visit(node: JsonElement?) {
            when {
                node == null || node.isJsonNull -> Unit
                node.isJsonObject -> {
                    val obj = node.asJsonObject
                    obj.get(key)?.asJsonObjectOrNull()?.let(found::add)
                    obj.entrySet().forEach { visit(it.value) }
                }
                node.isJsonArray -> node.asJsonArray.forEach(::visit)
            }
        }
        visit(root)
        return found
    }

    fun highResThumbnail(thumbnails: JsonArray?): String? {
        val url = thumbnails?.lastOrNull()?.asJsonObjectOrNull()?.get("url")?.stringOrNull() ?: return null
        var result = if (url.startsWith("//")) "https:$url" else url
        if (result.contains("=w") && result.contains("-h")) {
            result = result.replace(Regex("=w\\d+-h\\d+"), "=w1080-h1080")
        }
        if (result.contains("mqdefault.jpg")) result = result.replace("mqdefault.jpg", "hqdefault.jpg")
        if (result.contains("sqdefault.jpg")) result = result.replace("sqdefault.jpg", "hqdefault.jpg")
        return result
    }

    private fun parseDuration(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val parts = value.split(":").mapNotNull { it.toLongOrNull() }
        if (parts.size !in 2..3) return 0L
        val seconds = if (parts.size == 3) parts[0] * 3600 + parts[1] * 60 + parts[2] else parts[0] * 60 + parts[1]
        return seconds * 1000L
    }

    private fun simpleKey(item: SearchItem): String = when (item) {
        is SearchItem.Song -> "song:${item.song.videoId}"
        is SearchItem.Album -> "album:${item.album.browseId}"
        is SearchItem.Artist -> "artist:${item.artist.browseId}"
        is SearchItem.Playlist -> "playlist:${item.playlist.playlistId}"
    }
}

private fun JsonElement?.path(path: String): JsonElement? {
    var current = this
    for (part in path.split(".")) {
        current = when {
            current == null || current.isJsonNull -> return null
            current.isJsonObject -> current.asJsonObject.get(part)
            current.isJsonArray -> current.asJsonArray.getOrNull(part.toIntOrNull() ?: return null)
            else -> return null
        }
    }
    return current
}

private fun JsonArray.getOrNull(index: Int): JsonElement? = if (index in 0 until size()) get(index) else null
private fun JsonElement.asJsonObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
private fun JsonElement?.stringOrNull(): String? = if (this == null || isJsonNull) null else runCatching { asString }.getOrNull()
