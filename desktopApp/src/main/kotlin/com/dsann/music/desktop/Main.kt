package com.dsann.music.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import java.awt.EventQueue
import java.nio.file.Files
import java.util.Locale
import java.util.prefs.Preferences
import com.dsann.music.data.innertube.SearchFilter
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.data.innertube.SearchResult
import com.dsann.music.desktop.DesktopHomePage
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val pageBackground = Color(0xFF0B0F15)
private val sidebarBackground = Color(0xFF101720)
private val panelBackground = Color(0xFF151E29)
private var accentColor by mutableStateOf(Color(0xFF9ABEFF))

private fun desktopAccentFor(preset: String): Color = when (preset) {
    "Violet" -> Color(0xFFB69CFF)
    "Emerald" -> Color(0xFF62C9A2)
    "Amber" -> Color(0xFFFFC36F)
    "Rose" -> Color(0xFFFF8FA3)
    "Monochrome" -> Color(0xFFC9CDD4)
    else -> Color(0xFF6FA8FF)
}
private val mutedText = Color(0xFF9AA8B8)

private data class DesktopTrack(val file: File) {
    val title: String
        get() = file.nameWithoutExtension

    val artist: String
        get() = file.parentFile?.name?.takeIf { it.isNotBlank() } ?: "Local audio"

    val extension: String
        get() = file.extension.uppercase(Locale.ROOT)

    val sizeLabel: String
        get() = String.format(Locale.getDefault(), "%.1f MB", file.length() / (1024.0 * 1024.0))
}

private object DesktopPreferenceKey

fun main() = application {
    val player = remember { DesktopAudioPlayer() }
    DisposableEffect(player) {
        onDispose { player.close() }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "SpatialFlow",
        state = rememberWindowState(width = 1320.dp, height = 850.dp),
        resizable = true
    ) {
        SpatialFlowDesktopApp(player)
    }
}

@Composable
private fun SpatialFlowDesktopApp(player: DesktopAudioPlayer) {
    val preferences = remember {
        Preferences.userNodeForPackage(DesktopPreferenceKey::class.java)
    }

    var folderPath by remember {
        mutableStateOf(preferences.get("music_folder", ""))
    }
    var tracks by remember { mutableStateOf<List<DesktopTrack>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(-1) }
    var section by remember { mutableStateOf("Library") }
    var search by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(true) }
    var seekValue by remember { mutableStateOf(0f) }
    var onlineQuery by remember { mutableStateOf("") }
    var onlineResult by remember { mutableStateOf<SearchResult?>(null) }
    var onlineSearching by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf("") }
    var currentOnlineSong by remember { mutableStateOf<OnlineSong?>(null) }
    var currentSavedSong by remember { mutableStateOf<DesktopSavedSong?>(null) }
    var onlineQueue by remember { mutableStateOf<List<OnlineSong>>(emptyList()) }
    var onlineQueueIndex by remember { mutableStateOf(-1) }
    var lyricsData by remember { mutableStateOf<DesktopLyrics?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf("") }
    var downloadingVideoId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadMessage by remember { mutableStateOf("") }
    var downloads by remember { mutableStateOf(DesktopLibraryStore.downloads()) }
    var cacheEntries by remember { mutableStateOf(DesktopLibraryStore.cache()) }
    var favoriteSongs by remember { mutableStateOf(DesktopLibraryStore.favoriteSongs()) }
    var favoriteIds by remember { mutableStateOf(favoriteSongs.map { it.videoId }.toSet()) }
    var playlists by remember { mutableStateOf(DesktopLibraryStore.playlists()) }
    var playlistTitle by remember { mutableStateOf("") }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var collectionTitle by remember { mutableStateOf("") }
    var collectionItems by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    var collectionLoading by remember { mutableStateOf(false) }
    var playbackFormat by remember { mutableStateOf(preferences.get("playback_format", "m4a")) }
    var targetBitrate by remember { mutableStateOf(preferences.getInt("target_bitrate", 256)) }
    var autoplayEnabled by remember { mutableStateOf(preferences.getBoolean("autoplay_enabled", true)) }
    var appearancePreset by remember { mutableStateOf(preferences.get("appearance_preset", "Ocean")) }
    var videoMode by remember { mutableStateOf(false) }
    var homeFeed by remember { mutableStateOf<DesktopHomePage?>(null) }
    var exploreFeed by remember { mutableStateOf<DesktopHomePage?>(null) }
    var feedLoading by remember { mutableStateOf(false) }
    var feedError by remember { mutableStateOf("") }
    var cookieDraft by remember { mutableStateOf("") }
    var accountProfile by remember { mutableStateOf<DesktopAccountProfile?>(null) }
    var accountLoading by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf("") }
    var remoteLibraryKind by remember { mutableStateOf("Playlists") }
    var remoteLibraryItems by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    var remoteLibraryLoading by remember { mutableStateOf(false) }
    var remoteLibraryError by remember { mutableStateOf("") }
    var remotePlaylistTitle by remember { mutableStateOf("") }
    var selectedRemotePlaylistId by remember { mutableStateOf<String?>(null) }
    val videoSurface = remember { DesktopVideoSurface() }
    val coroutineScope = rememberCoroutineScope()

    val currentTrack = tracks.getOrNull(selectedIndex)
    val nowPlayingTitle = currentOnlineSong?.title ?: currentSavedSong?.title ?: currentTrack?.title.orEmpty()

    LaunchedEffect(appearancePreset) {
        accentColor = desktopAccentFor(appearancePreset)
        preferences.put("appearance_preset", appearancePreset)
        runCatching { preferences.flush() }
    }
    val nowPlayingArtist = currentOnlineSong?.artist ?: currentSavedSong?.artist ?: currentTrack?.artist.orEmpty()
    val hasNowPlaying = nowPlayingTitle.isNotBlank()
    val visibleTracks = remember(tracks, search) {
        val query = search.trim()
        if (query.isEmpty()) tracks
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.extension.contains(query, ignoreCase = true)
        }
    }

    LaunchedEffect(folderPath) {
        if (folderPath.isBlank()) {
            tracks = emptyList()
            selectedIndex = -1
            scanning = false
            return@LaunchedEffect
        }

        scanning = true
        statusMessage = ""
        player.stop()
        selectedIndex = -1
        try {
            val folder = File(folderPath)
            val found = withContext(Dispatchers.IO) { scanMusicFolder(folder) }
            tracks = found
            statusMessage = if (found.isEmpty()) {
                "No supported audio files were found in this folder."
            } else {
                "Found ${found.size} audio files."
            }
        } catch (error: Exception) {
            tracks = emptyList()
            statusMessage = error.message ?: "Could not scan this folder."
        } finally {
            scanning = false
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.positionMillis
            durationMs = player.durationMillis
            playing = player.isPlaying
            paused = player.isPaused
            player.errorMessage?.let { statusMessage = it }
            delay(200)
        }
    }

    fun searchOnline() {
        val query = onlineQuery.trim()
        if (query.isEmpty() || onlineSearching) return
        onlineSearching = true
        onlineError = ""
        onlineResult = null
        coroutineScope.launch {
            try {
                onlineResult = DesktopYouTubeMusic.searchTop(query)
            } catch (error: Exception) {
                onlineError = error.message ?: "YouTube Music search failed."
            } finally {
                onlineSearching = false
            }
        }
    }

    fun loadFeed(explore: Boolean) {
        feedLoading = true
        feedError = ""
        coroutineScope.launch {
            try {
                val response = DesktopYouTubeMusic.homePage(explore)
                if (explore) exploreFeed = response else homeFeed = response
            } catch (error: Exception) {
                feedError = error.message ?: "Could not load the YouTube Music feed."
            } finally {
                feedLoading = false
            }
        }
    }

    fun connectAccount() {
        val cookie = cookieDraft.trim()
        if (cookie.isBlank()) {
            accountError = "Paste the YouTube Music session cookie from your browser."
            return
        }
        accountLoading = true
        accountError = ""
        DesktopYouTubeMusic.setSessionCookie(cookie)
        coroutineScope.launch {
            try {
                accountProfile = DesktopYouTubeMusic.accountProfile()
                accountError = ""
                section = "Account"
            } catch (error: Exception) {
                DesktopYouTubeMusic.setSessionCookie(null)
                accountProfile = null
                accountError = error.message ?: "Could not connect this YouTube Music session."
            } finally {
                accountLoading = false
            }
        }
    }

    fun disconnectAccount() {
        DesktopYouTubeMusic.setSessionCookie(null)
        accountProfile = null
        remoteLibraryItems = emptyList()
        selectedRemotePlaylistId = null
        cookieDraft = ""
        accountError = "Disconnected. Account cookies are kept only in memory for this session."
    }

    fun loadRemoteLibrary(kind: String = remoteLibraryKind) {
        remoteLibraryKind = kind
        remoteLibraryLoading = true
        remoteLibraryError = ""
        coroutineScope.launch {
            try {
                remoteLibraryItems = DesktopYouTubeMusic.accountLibrary(kind.lowercase(Locale.ROOT))
            } catch (error: Exception) {
                remoteLibraryItems = emptyList()
                remoteLibraryError = error.message ?: "Could not load the online library."
            } finally {
                remoteLibraryLoading = false
            }
        }
    }

    fun refreshSavedLists() {
        downloads = DesktopLibraryStore.downloads()
        cacheEntries = DesktopLibraryStore.cache()
        favoriteSongs = DesktopLibraryStore.favoriteSongs()
        favoriteIds = favoriteSongs.map { it.videoId }.toSet()
        playlists = DesktopLibraryStore.playlists()
    }

    fun addSongToRemotePlaylist(song: OnlineSong) {
        val playlistId = selectedRemotePlaylistId
        if (!DesktopYouTubeMusic.hasSession() || playlistId.isNullOrBlank()) {
            val local = playlists.firstOrNull() ?: runCatching {
                DesktopLibraryStore.createPlaylist("My playlist")
            }.getOrNull()
            if (local == null) {
                downloadMessage = "Could not create a local playlist."
                return
            }
            DesktopLibraryStore.addToPlaylist(local.id, song)
            refreshSavedLists()
            downloadMessage = "Added to ${local.title}."
            return
        }
        coroutineScope.launch {
            try {
                DesktopYouTubeMusic.addRemotePlaylistSongs(playlistId, listOf(song.videoId))
                downloadMessage = "Added to your YouTube Music playlist."
            } catch (error: Exception) {
                downloadMessage = error.message ?: "Could not add the song to the online playlist."
            }
        }
    }

    fun loadLyrics(title: String, artist: String, durationMs: Long, videoId: String? = null, album: String? = null) {
        lyricsData = null
        lyricsError = ""
        lyricsLoading = true
        coroutineScope.launch {
            try {
                lyricsData = DesktopLyricsRepository.fetch(title, artist, durationMs, videoId, album)
                if (lyricsData == null) lyricsError = "No lyrics were found for this track."
            } catch (error: Exception) {
                lyricsError = error.message ?: "Could not load lyrics."
            } finally {
                lyricsLoading = false
            }
        }
    }

    fun playOnlineSong(song: OnlineSong, queue: List<OnlineSong>? = null) {
        videoMode = false
        if (queue != null) onlineQueue = queue
        if (onlineQueue.isEmpty()) onlineQueue = listOf(song)
        onlineQueueIndex = onlineQueue.indexOfFirst { it.videoId == song.videoId }.takeIf { it >= 0 } ?: 0
        currentOnlineSong = song
        currentSavedSong = null
        selectedIndex = -1
        statusMessage = "Resolving audio stream…"
        section = "Explore"

        coroutineScope.launch {
            try {
                val stream = DesktopNewPipeExtractor.resolve(song.videoId, targetBitrate, playbackFormat)
                player.playUrl(stream.url, song.durationMs.takeIf { it > 0L } ?: stream.durationMs)
                statusMessage = ""
            } catch (error: Exception) {
                statusMessage = error.message ?: "Could not resolve this YouTube Music stream."
            }
        }
        loadLyrics(song.title, song.artist, song.durationMs, song.videoId, song.albumName)
    }

    fun toggleVideoMode() {
        val song = currentOnlineSong ?: return
        val resumePosition = positionMs.coerceAtLeast(0L)
        val wasVideo = videoMode
        statusMessage = if (wasVideo) "Switching to audio…" else "Resolving video stream…"
        coroutineScope.launch {
            try {
                val stream = if (wasVideo) {
                    DesktopNewPipeExtractor.resolve(song.videoId, targetBitrate, playbackFormat)
                } else {
                    DesktopNewPipeExtractor.resolveVideo(song.videoId)
                }
                player.playUrl(
                    stream.url,
                    song.durationMs.takeIf { it > 0L } ?: stream.durationMs,
                    resumePosition
                )
                videoMode = !wasVideo
                statusMessage = ""
            } catch (error: Exception) {
                statusMessage = error.message ?: if (wasVideo) {
                    "Could not restore audio playback."
                } else {
                    "No compatible muxed video stream is available for this track."
                }
            }
        }
    }

    fun openCollection(browseId: String, title: String) {
        collectionTitle = title
        collectionItems = emptyList()
        collectionLoading = true
        section = "Collection"
        coroutineScope.launch {
            try {
                collectionItems = DesktopYouTubeMusic.browse(browseId)
                val queue = collectionItems.filterIsInstance<SearchItem.Song>().map { it.song }
                if (queue.isNotEmpty()) onlineQueue = queue
            } catch (error: Exception) {
                statusMessage = error.message ?: "Could not open this collection."
            } finally {
                collectionLoading = false
            }
        }
    }

    fun openOnlineItem(item: SearchItem) {
        when (item) {
            is SearchItem.Song -> {
                val queue = buildList {
                    onlineResult?.topResult?.let { if (it is SearchItem.Song) add(it.song) }
                    onlineResult?.sections.orEmpty().forEach { section ->
                        section.items.filterIsInstance<SearchItem.Song>().forEach { add(it.song) }
                    }
                    onlineResult?.items.orEmpty().filterIsInstance<SearchItem.Song>().forEach { add(it.song) }
                }.distinctBy { it.videoId }.ifEmpty { listOf(item.song) }
                playOnlineSong(item.song, queue)
            }
            is SearchItem.Album -> openCollection(item.album.browseId, item.album.title)
            is SearchItem.Artist -> openCollection(item.artist.browseId, item.artist.title)
            is SearchItem.Playlist -> openCollection(item.playlist.playlistId, item.playlist.title)
        }
    }

    fun toggleFavorite(song: OnlineSong) {
        val enabled = song.videoId !in favoriteIds
        DesktopLibraryStore.setFavorite(song, enabled)
        refreshSavedLists()
        if (DesktopYouTubeMusic.hasSession()) {
            coroutineScope.launch {
                runCatching { DesktopYouTubeMusic.setRemoteLike(song.videoId, enabled) }
                    .onFailure { downloadMessage = it.message ?: "Local favorite saved; online like did not sync." }
            }
        }
    }

    fun addSongToPlaylist(song: OnlineSong) {
        var target = playlists.firstOrNull()
        if (target == null) {
            target = DesktopLibraryStore.createPlaylist("My playlist")
        }
        DesktopLibraryStore.addToPlaylist(target.id, song)
        refreshSavedLists()
        downloadMessage = "Added to ${target.title}."
    }

    fun startDownload(song: OnlineSong) {
        if (downloadingVideoId != null) return
        downloadingVideoId = song.videoId
        downloadProgress = 0
        downloadMessage = "Preparing download…"
        coroutineScope.launch {
            try {
                DesktopDownloadManager.download(song, targetBitrate, playbackFormat) { value ->
                    EventQueue.invokeLater { downloadProgress = value }
                }
                refreshSavedLists()
                downloadMessage = if (cacheEntries.any { it.videoId == song.videoId }) {
                    "Downloaded with cover and lyrics; added to offline cache."
                } else {
                    "Downloaded. A complete cache entry needs both cover art and lyrics."
                }
            } catch (error: Exception) {
                downloadMessage = error.message ?: "Download failed."
            } finally {
                downloadingVideoId = null
            }
        }
    }

    fun playSavedSong(song: DesktopSavedSong) {
        val audioFile = song.audioPath.takeIf { it.isNotBlank() }?.let(::File)
        if (audioFile != null && audioFile.isFile) {
            currentOnlineSong = null
            currentSavedSong = song
            selectedIndex = -1
            player.play(audioFile, 0L, song.durationMs)
            val lyricFile = song.lyricsPath?.let(::File)
            if (lyricFile != null && lyricFile.isFile) {
                val raw = lyricFile.readText()
                lyricsData = DesktopLyricsRepository.parseOffline(raw)
                section = "Lyrics"
            } else {
                loadLyrics(song.title, song.artist, song.durationMs, song.videoId, song.album)
            }
            return
        }
        playOnlineSong(
            OnlineSong(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                albumName = song.album,
                durationMs = song.durationMs,
                thumbnailUrl = song.thumbnailUrl
            )
        )
    }

    fun playCollectionItem(item: SearchItem) {
        when (item) {
            is SearchItem.Song -> playOnlineSong(
                item.song,
                collectionItems.filterIsInstance<SearchItem.Song>().map { it.song }
            )
            else -> openOnlineItem(item)
        }
    }

    fun chooseMusicFolder() {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Choose your music folder"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val selectedFolder = chooser.selectedFile
            folderPath = selectedFolder.absolutePath
            preferences.put("music_folder", folderPath)
            try {
                preferences.flush()
            } catch (_: Exception) {
                // The selected folder still works for this session.
            }
        }
    }

    fun playTrack(track: DesktopTrack) {
        videoMode = false
        selectedIndex = tracks.indexOf(track)
        currentOnlineSong = null
        currentSavedSong = null
        statusMessage = ""
        player.play(track.file)
        loadLyrics(track.title, track.artist, player.durationMillis)
    }

    fun playRelative(direction: Int) {
        if (currentOnlineSong != null && onlineQueue.isNotEmpty()) {
            val next = (onlineQueueIndex + direction + onlineQueue.size) % onlineQueue.size
            playOnlineSong(onlineQueue[next])
            return
        }
        if (tracks.isEmpty()) return
        val base = selectedIndex.takeIf { it >= 0 } ?: 0
        selectedIndex = (base + direction + tracks.size) % tracks.size
        currentOnlineSong = null
        currentSavedSong = null
        player.play(tracks[selectedIndex].file)
        loadLyrics(tracks[selectedIndex].title, tracks[selectedIndex].artist, player.durationMillis)
        statusMessage = ""
    }

    val autoplayState = rememberUpdatedState(autoplayEnabled)
    val currentOnlineSongState = rememberUpdatedState(currentOnlineSong)
    val onlineQueueState = rememberUpdatedState(onlineQueue)
    val onlineQueueIndexState = rememberUpdatedState(onlineQueueIndex)
    val currentTrackState = rememberUpdatedState(currentTrack)
    val tracksState = rememberUpdatedState(tracks)
    val selectedIndexState = rememberUpdatedState(selectedIndex)

    DisposableEffect(player) {
        player.setOnPlaybackCompleted {
            coroutineScope.launch {
                if (!autoplayState.value) return@launch
                val onlineSong = currentOnlineSongState.value
                if (onlineSong != null) {
                    val queue = onlineQueueState.value
                    val nextIndex = onlineQueueIndexState.value + 1
                    if (nextIndex in queue.indices) {
                        playOnlineSong(queue[nextIndex], queue)
                    } else {
                        statusMessage = "Finding similar tracks…"
                        val related = runCatching {
                            DesktopYouTubeMusic.relatedSongs(onlineSong.videoId)
                        }.getOrElse { emptyList() }
                        val existingIds = queue.map { it.videoId }.toSet() + onlineSong.videoId
                        val suggestions = related.filterNot { it.videoId in existingIds }
                        if (suggestions.isNotEmpty()) {
                            val expandedQueue = queue + suggestions
                            onlineQueue = expandedQueue
                            playOnlineSong(suggestions.first(), expandedQueue)
                        } else {
                            statusMessage = "No further related tracks were returned."
                        }
                    }
                } else if (currentTrackState.value != null) {
                    val currentTracks = tracksState.value
                    if (currentTracks.isNotEmpty()) {
                        val next = (selectedIndexState.value + 1) % currentTracks.size
                        playTrack(currentTracks[next])
                    }
                }
            }
        }
        onDispose { player.setOnPlaybackCompleted(null) }
    }

    MaterialTheme(
        colors = darkColors(
            primary = accentColor,
            secondary = Color(0xFF84D8C8),
            background = pageBackground,
            surface = panelBackground,
            onPrimary = Color(0xFF0B1420),
            onBackground = Color(0xFFE8EEF7),
            onSurface = Color(0xFFE8EEF7)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = pageBackground
        ) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(224.dp)
                        .fillMaxHeight()
                        .background(sidebarBackground)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(Color(0xFF243A57)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("S", color = accentColor, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.padding(start = 11.dp)) {
                            Text("SpatialFlow", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("DESKTOP", fontSize = 10.sp, color = mutedText, letterSpacing = 2.sp)
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("YOUR SPACE", color = mutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    listOf("Home", "Explore", "Library", "Queue", "Online Library", "Playlists", "Favorites", "Downloads", "Cache", "Lyrics", "Account", "Settings").forEach { item ->
                        SidebarItem(
                            label = item,
                            selected = section == item,
                            onClick = {
                                section = item
                                when (item) {
                                    "Home" -> if (homeFeed == null) loadFeed(false)
                                    "Explore" -> if (exploreFeed == null) loadFeed(true)
                                    "Online Library" -> if (DesktopYouTubeMusic.hasSession() && remoteLibraryItems.isEmpty()) loadRemoteLibrary()
                                }
                            }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Divider(color = Color(0xFF263241))
                    Text("Windows desktop preview", color = mutedText, fontSize = 11.sp)
                    Text("Phone source synchronized", color = mutedText, fontSize = 10.sp)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp, vertical = 26.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (section) {
                                        "Home" -> "Home"
                                        "Home" -> "Home"
                                        "Explore" -> "Explore"
                                        "Queue" -> "Playback queue"
                                        "Online Library" -> "YouTube Music library"
                                        "Account" -> "Account"
                                        "Cache" -> "Offline cache"
                                        "Downloads" -> "Downloads"
                                        "Favorites" -> "Favorites"
                                        "Playlists" -> "Playlists"
                                        "Lyrics" -> "Lyrics"
                                        "Collection" -> collectionTitle
                                        "Settings" -> "Settings"
                                        else -> "Your library"
                                    },
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    when (section) {
                                        "Home" -> "Personalized music shelves from YouTube Music."
                                        "Explore" -> "Charts, new releases and discovery shelves."
                                        "Queue" -> "Upcoming tracks and playback order."
                                        "Online Library" -> "Your playlists, artists, albums, podcasts and liked tracks."
                                        "Account" -> "Connect your YouTube Music browser session."
                                        "Cache" -> "Offline songs with cover art and saved lyrics."
                                        "Downloads" -> "Downloaded audio files on this PC."
                                        "Favorites" -> "Your saved songs."
                                        "Playlists" -> "Create and manage desktop playlists."
                                        "Lyrics" -> "Synchronized and plain lyrics for the current track."
                                        "Collection" -> "Browse and play tracks in this collection."
                                        "Settings" -> "Playback format, audio quality and library storage."
                                        else -> "Your local music, in one place."
                                    },
                                    color = mutedText,
                                    fontSize = 13.sp
                                )
                            }

                            if (section == "Library" || section == "Settings") {
                                Button(
                                    onClick = ::chooseMusicFolder,
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = accentColor,
                                        contentColor = Color(0xFF0B1420)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (folderPath.isBlank()) "Choose music folder" else "Change folder")
                                }
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        if (videoMode && currentOnlineSong != null) {
                            SwingPanel(
                                factory = { videoSurface },
                                update = { it.attach(player.getRemoteMediaPlayer()) },
                                modifier = Modifier.fillMaxWidth().height(300.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        when (section) {
                            "Library" -> {
                                if (folderPath.isNotBlank()) {
                                    OutlinedTextField(
                                        value = search,
                                        onValueChange = { search = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("Search songs, artists or formats") },
                                        label = { Text("Search library") }
                                    )
                                    Spacer(Modifier.height(14.dp))
                                }

                                when {
                                    scanning -> EmptyPanel("Scanning your music folder…", "Large libraries are scanned in the background.")
                                    folderPath.isBlank() -> EmptyPanel(
                                        "Start with your music folder",
                                        "Choose a folder on this PC. SpatialFlow will scan supported audio files and build your local library."
                                    )
                                    tracks.isEmpty() -> EmptyPanel("Your library is empty", statusMessage.ifBlank { "Choose another folder to scan for music." })
                                    visibleTracks.isEmpty() -> EmptyPanel("No matches", "Try another title, artist or format.")
                                    else -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, start = 6.dp, end = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${visibleTracks.size} TRACKS", color = mutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            Text(File(folderPath).absolutePath, color = mutedText, fontSize = 10.sp)
                                        }
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            items(visibleTracks, key = { it.file.absolutePath }) { track ->
                                                TrackRow(
                                                    track = track,
                                                    selected = currentTrack?.file == track.file,
                                                    onClick = { playTrack(track) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            "Home", "Explore" -> {
                                val isExplore = section == "Explore"
                                val feed = if (isExplore) exploreFeed else homeFeed
                                Column(Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { section = "Home"; if (homeFeed == null) loadFeed(false) },
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = if (!isExplore) accentColor else Color(0xFF263545),
                                                contentColor = if (!isExplore) Color(0xFF0B1420) else Color.White
                                            )
                                        ) { Text("Home") }
                                        Button(
                                            onClick = { section = "Explore"; if (exploreFeed == null) loadFeed(true) },
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = if (isExplore) accentColor else Color(0xFF263545),
                                                contentColor = if (isExplore) Color(0xFF0B1420) else Color.White
                                            )
                                        ) { Text("Explore") }
                                        Spacer(Modifier.weight(1f))
                                        Button(onClick = { loadFeed(isExplore) }, enabled = !feedLoading) { Text("Refresh") }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    if (!isExplore) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = onlineQuery,
                                                onValueChange = { onlineQuery = it },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                placeholder = { Text("Search songs, albums, artists, playlists or podcasts") },
                                                label = { Text("Search YouTube Music") }
                                            )
                                            Button(
                                                onClick = ::searchOnline,
                                                enabled = onlineQuery.isNotBlank() && !onlineSearching,
                                                colors = ButtonDefaults.buttonColors(backgroundColor = accentColor, contentColor = Color(0xFF0B1420))
                                            ) { Text(if (onlineSearching) "Searching…" else "Search") }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                    }
                                    when {
                                        feedLoading || onlineSearching -> EmptyPanel(
                                            if (onlineSearching) "Searching YouTube Music…" else "Loading music feed…",
                                            "Fetching phone-style categories and recommendation shelves."
                                        )
                                        feedError.isNotBlank() && feed == null -> EmptyPanel("Feed unavailable", feedError)
                                        !isExplore && onlineError.isNotBlank() -> EmptyPanel("Search failed", onlineError)
                                        !isExplore && onlineResult != null -> {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(9.dp)
                                            ) {
                                                onlineResult?.topResult?.let { top ->
                                                    item(key = "top-result") {
                                                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                            Text("TOP RESULT", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            OnlineResultRow(
                                                                item = top,
                                                                featured = true,
                                                                isFavorite = ((top as? SearchItem.Song)?.song?.videoId?.let { it in favoriteIds } == true),
                                                                isDownloading = (top as? SearchItem.Song)?.song?.videoId == downloadingVideoId,
                                                                downloadProgress = downloadProgress,
                                                                onClick = { openOnlineItem(top) },
                                                                onFavorite = { (top as? SearchItem.Song)?.song?.let(::toggleFavorite) },
                                                                onDownload = { (top as? SearchItem.Song)?.song?.let(::startDownload) },
                                                                onAddToPlaylist = { (top as? SearchItem.Song)?.song?.let(::addSongToRemotePlaylist) }
                                                            )
                                                        }
                                                    }
                                                }
                                                onlineResult?.sections.orEmpty().forEach { resultSection ->
                                                    item(key = "search-section-${resultSection.filter.name}") {
                                                        Text(resultSection.filter.label.uppercase(Locale.ROOT), color = mutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    items(resultSection.items, key = { item -> DesktopYouTubeMusic.itemTitle(item) + DesktopYouTubeMusic.itemSubtitle(item) }) { item ->
                                                        OnlineResultRow(
                                                            item = item,
                                                            isFavorite = ((item as? SearchItem.Song)?.song?.videoId?.let { it in favoriteIds } == true),
                                                            isDownloading = (item as? SearchItem.Song)?.song?.videoId == downloadingVideoId,
                                                            downloadProgress = downloadProgress,
                                                            onClick = { openOnlineItem(item) },
                                                            onFavorite = { (item as? SearchItem.Song)?.song?.let(::toggleFavorite) },
                                                            onDownload = { (item as? SearchItem.Song)?.song?.let(::startDownload) },
                                                            onAddToPlaylist = { (item as? SearchItem.Song)?.song?.let(::addSongToRemotePlaylist) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        feed == null -> EmptyPanel(
                                            if (isExplore) "Explore music" else "Your YouTube Music home",
                                            if (isExplore) "Load charts, moods and new releases." else "Load personalized recommendations from your YouTube Music home feed."
                                        )
                                        else -> {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(18.dp)
                                            ) {
                                                if (feed!!.moods.isNotEmpty()) {
                                                    item(key = "home-moods") {
                                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Text("MOODS & GENRES", color = mutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                                                items(feed!!.moods) { mood ->
                                                                    Button(
                                                                        onClick = {
                                                                            onlineQuery = mood
                                                                            searchOnline()
                                                                        },
                                                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545)),
                                                                        elevation = ButtonDefaults.elevation(0.dp)
                                                                    ) { Text(mood) }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                feed!!.sections.forEachIndexed { sectionIndex, shelf ->
                                                    item(key = "feed-shelf-$sectionIndex") {
                                                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(shelf.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                                                if (!shelf.browseId.isNullOrBlank()) {
                                                                    Button(
                                                                        onClick = { openCollection(shelf.browseId, shelf.title) },
                                                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545)),
                                                                        elevation = ButtonDefaults.elevation(0.dp)
                                                                    ) { Text("See all") }
                                                                }
                                                            }
                                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                                                items(shelf.items.size) { index ->
                                                                    val item = shelf.items[index]
                                                                    Box(Modifier.width(320.dp)) {
                                                                        OnlineResultRow(
                                                                            item = item,
                                                                            isFavorite = ((item as? SearchItem.Song)?.song?.videoId?.let { it in favoriteIds } == true),
                                                                            isDownloading = (item as? SearchItem.Song)?.song?.videoId == downloadingVideoId,
                                                                            downloadProgress = downloadProgress,
                                                                            onClick = { openOnlineItem(item) },
                                                                            onFavorite = { (item as? SearchItem.Song)?.song?.let(::toggleFavorite) },
                                                                            onDownload = { (item as? SearchItem.Song)?.song?.let(::startDownload) },
                                                                            onAddToPlaylist = { (item as? SearchItem.Song)?.song?.let(::addSongToRemotePlaylist) }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "Queue" -> {
                                if (onlineQueue.isEmpty() && currentTrack == null) {
                                    EmptyPanel("Queue is empty", "Play a song to build your upcoming queue.")
                                } else {
                                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        currentOnlineSong?.let { current ->
                                            item(key = "queue-current") {
                                                Text("NOW PLAYING", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                OnlineResultRow(
                                                    item = SearchItem.Song(current),
                                                    featured = true,
                                                    onClick = { playOnlineSong(current, onlineQueue) }
                                                )
                                            }
                                        }
                                        items(onlineQueue.size) { index ->
                                            val song = onlineQueue[index]
                                            OnlineResultRow(
                                                item = SearchItem.Song(song),
                                                featured = index == onlineQueueIndex,
                                                isFavorite = song.videoId in favoriteIds,
                                                isDownloading = song.videoId == downloadingVideoId,
                                                downloadProgress = downloadProgress,
                                                onClick = { playOnlineSong(song, onlineQueue) },
                                                onFavorite = { toggleFavorite(song) },
                                                onDownload = { startDownload(song) },
                                                onAddToPlaylist = { addSongToRemotePlaylist(song) }
                                            )
                                        }
                                        if (currentTrack != null && onlineQueue.isEmpty()) {
                                            item(key = "local-queue-current") {
                                                TrackRow(track = currentTrack, selected = true, onClick = { playTrack(currentTrack) })
                                            }
                                        }
                                    }
                                }
                            }

                            "Online Library" -> {
                                Column(Modifier.fillMaxSize()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf("Playlists", "Artists", "Albums", "Podcasts", "Liked").forEach { kind ->
                                            Button(
                                                onClick = { loadRemoteLibrary(kind) },
                                                enabled = DesktopYouTubeMusic.hasSession() && !remoteLibraryLoading,
                                                colors = ButtonDefaults.buttonColors(
                                                    backgroundColor = if (remoteLibraryKind == kind) accentColor else Color(0xFF263545),
                                                    contentColor = if (remoteLibraryKind == kind) Color(0xFF0B1420) else Color.White
                                                ),
                                                elevation = ButtonDefaults.elevation(0.dp)
                                            ) { Text(kind) }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    when {
                                        !DesktopYouTubeMusic.hasSession() -> EmptyPanel("Connect your account", "Open Account and connect a YouTube Music browser session to load your online library.")
                                        remoteLibraryLoading -> EmptyPanel("Loading library…", "Fetching ${remoteLibraryKind.lowercase(Locale.ROOT)} from YouTube Music.")
                                        remoteLibraryError.isNotBlank() -> EmptyPanel("Library unavailable", remoteLibraryError)
                                        remoteLibraryItems.isEmpty() -> EmptyPanel("No online ${remoteLibraryKind.lowercase(Locale.ROOT)}", "This category did not return any items.")
                                        else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            items(remoteLibraryItems.size) { index ->
                                                val item = remoteLibraryItems[index]
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(Modifier.weight(1f)) {
                                                        OnlineResultRow(
                                                            item = item,
                                                            onClick = { openOnlineItem(item) }
                                                        )
                                                    }
                                                    if (item is SearchItem.Playlist) {
                                                        Button(
                                                            onClick = { selectedRemotePlaylistId = item.playlist.playlistId; downloadMessage = "Selected online playlist: ${item.playlist.title}" },
                                                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545))
                                                        ) { Text(if (selectedRemotePlaylistId == item.playlist.playlistId) "Selected" else "Select") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (downloadMessage.isNotBlank()) Text(downloadMessage, color = mutedText, fontSize = 11.sp)
                                }
                            }

                            "Account" -> {
                                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FeaturePanel(
                                        title = "YouTube Music account",
                                        body = accountProfile?.let { listOfNotNull(it.name, it.handle, it.email).joinToString("\n") }
                                            ?: "A browser session cookie is used to access your YouTube Music library and account playlists.",
                                        footnote = "The session cookie is held in memory only and is not saved to disk. Disconnect or restart the app to clear it."
                                    )
                                    OutlinedTextField(
                                        value = cookieDraft,
                                        onValueChange = { cookieDraft = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = false,
                                        label = { Text("YouTube Music session cookie") },
                                        placeholder = { Text("Paste Cookie header from music.youtube.com") }
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = ::connectAccount,
                                            enabled = cookieDraft.isNotBlank() && !accountLoading,
                                            colors = ButtonDefaults.buttonColors(backgroundColor = accentColor, contentColor = Color(0xFF0B1420))
                                        ) { Text(if (accountLoading) "Connecting…" else "Connect") }
                                        Button(
                                            onClick = ::disconnectAccount,
                                            enabled = DesktopYouTubeMusic.hasSession(),
                                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545))
                                        ) { Text("Disconnect") }
                                        Button(
                                            onClick = { section = "Online Library"; loadRemoteLibrary("Playlists") },
                                            enabled = accountProfile != null
                                        ) { Text("Open online library") }
                                    }
                                    if (accountError.isNotBlank()) Text(accountError, color = Color(0xFFFFC0A8), fontSize = 12.sp)
                                    if (accountProfile != null) Text("Connected as ${accountProfile!!.name}", color = accentColor, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            "Collection" -> {
                                Column(Modifier.fillMaxSize()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button(onClick = { section = "Explore" }) { Text("Back to Explore") }
                                        Text(collectionTitle, modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    when {
                                        collectionLoading -> EmptyPanel("Loading collection…", "Fetching tracks from YouTube Music.")
                                        collectionItems.isEmpty() -> EmptyPanel("No tracks found", "This collection did not return playable song rows.")
                                        else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            items(collectionItems.size) { index ->
                                                val item = collectionItems[index]
                                                OnlineResultRow(
                                                    item = item,
                                                    isFavorite = ((item as? SearchItem.Song)?.song?.videoId?.let { it in favoriteIds } == true),
                                                    isDownloading = (item as? SearchItem.Song)?.song?.videoId == downloadingVideoId,
                                                    downloadProgress = downloadProgress,
                                                    onClick = { playCollectionItem(item) },
                                                    onFavorite = { (item as? SearchItem.Song)?.song?.let(::toggleFavorite) },
                                                    onDownload = { (item as? SearchItem.Song)?.song?.let(::startDownload) },
                                                    onAddToPlaylist = { (item as? SearchItem.Song)?.song?.let(::addSongToPlaylist) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            "Favorites" -> {
                                if (favoriteSongs.isEmpty()) EmptyPanel("No favorites yet", "Use the heart button on an online song to save it here.")
                                else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(favoriteSongs, key = { it.videoId }) { saved ->
                                        SavedTrackRow(
                                            song = saved,
                                            label = if (saved.audioPath.isNotBlank() && File(saved.audioPath).isFile) "DOWNLOADED" else "ONLINE",
                                            onClick = { playSavedSong(saved) },
                                            onRemove = {
                                                DesktopLibraryStore.setFavorite(
                                                    OnlineSong(
                                                        videoId = saved.videoId,
                                                        title = saved.title,
                                                        artist = saved.artist,
                                                        albumName = saved.album,
                                                        durationMs = saved.durationMs,
                                                        thumbnailUrl = saved.thumbnailUrl
                                                    ),
                                                    false
                                                )
                                                refreshSavedLists()
                                            }
                                        )
                                    }
                                }
                            }

                            "Downloads" -> {
                                Column(Modifier.fillMaxSize()) {
                                    if (downloadMessage.isNotBlank()) {
                                        Text(downloadMessage, color = mutedText, fontSize = 12.sp)
                                        if (downloadingVideoId != null) LinearProgressIndicator(downloadProgress / 100f, Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(10.dp))
                                    }
                                    if (downloads.isEmpty()) EmptyPanel("No downloads", "Use the download arrow beside an online song to save it to this PC.")
                                    else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        items(downloads, key = { it.videoId }) { saved ->
                                            SavedTrackRow(
                                                song = saved,
                                                label = if (cacheEntries.any { it.videoId == saved.videoId }) "CACHED" else "DOWNLOADED",
                                                onClick = { playSavedSong(saved) },
                                                onRemove = {
                                                    DesktopLibraryStore.removeDownload(saved.videoId)
                                                    refreshSavedLists()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            "Cache" -> {
                                if (cacheEntries.isEmpty()) EmptyPanel("Cache is empty", "A song is added here only when its audio, cover art and lyrics are all saved.")
                                else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(cacheEntries, key = { it.videoId }) { saved ->
                                        SavedTrackRow(
                                            song = saved,
                                            label = "OFFLINE READY",
                                            onClick = { playSavedSong(saved) },
                                            onRemove = {
                                                DesktopLibraryStore.removeDownload(saved.videoId)
                                                refreshSavedLists()
                                            }
                                        )
                                    }
                                }
                            }

                            "Playlists" -> {
                                Column(Modifier.fillMaxSize()) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = playlistTitle,
                                            onValueChange = { playlistTitle = it },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            label = { Text("New playlist name") }
                                        )
                                        Button(
                                            onClick = {
                                                runCatching { DesktopLibraryStore.createPlaylist(playlistTitle) }
                                                    .onSuccess { playlistTitle = ""; refreshSavedLists(); selectedPlaylistId = it.id }
                                                    .onFailure { downloadMessage = it.message.orEmpty() }
                                            },
                                            enabled = playlistTitle.isNotBlank()
                                        ) { Text("Create") }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    if (playlists.isEmpty()) EmptyPanel("No playlists", "Create a playlist, then use the plus button on a search result to add songs.")
                                    else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                        items(playlists, key = { it.id }) { playlist ->
                                            Column(
                                                Modifier.fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(if (selectedPlaylistId == playlist.id) Color(0xFF1D3045) else panelBackground)
                                                    .padding(12.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f).clickable { selectedPlaylistId = playlist.id }) {
                                                        Text(playlist.title, fontWeight = FontWeight.SemiBold)
                                                        Text("${playlist.songs.size} songs", color = mutedText, fontSize = 11.sp)
                                                    }
                                                    Button(onClick = { selectedPlaylistId = playlist.id }) { Text("Open") }
                                                    Spacer(Modifier.width(6.dp))
                                                    Button(onClick = {
                                                        DesktopLibraryStore.deletePlaylist(playlist.id)
                                                        if (selectedPlaylistId == playlist.id) selectedPlaylistId = null
                                                        refreshSavedLists()
                                                    }) { Text("Delete") }
                                                }
                                                if (selectedPlaylistId == playlist.id) {
                                                    playlist.songs.forEach { saved ->
                                                        SavedTrackRow(
                                                            song = saved,
                                                            label = "PLAYLIST",
                                                            onClick = { playSavedSong(saved) },
                                                            onRemove = { downloadMessage = "Playlist item removal is not yet available." }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "Lyrics" -> {
                                Column(Modifier.fillMaxSize()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(nowPlayingTitle.ifBlank { "No track selected" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            Text(nowPlayingArtist, color = mutedText, fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = {
                                                currentOnlineSong?.let { loadLyrics(it.title, it.artist, it.durationMs, it.videoId, it.albumName) }
                                                    ?: currentSavedSong?.let { loadLyrics(it.title, it.artist, it.durationMs, it.videoId, it.album) }
                                                    ?: currentTrack?.let { loadLyrics(it.title, it.artist, player.durationMillis) }
                                            },
                                            enabled = hasNowPlaying && !lyricsLoading
                                        ) { Text("Refresh lyrics") }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    when {
                                        lyricsLoading -> EmptyPanel("Loading lyrics…", "Checking YouTube Music and LRCLIB.")
                                        lyricsError.isNotBlank() -> EmptyPanel("Lyrics unavailable", lyricsError)
                                        lyricsData == null -> EmptyPanel("No lyrics loaded", "Play a song or refresh lyrics to look up the current track.")
                                        else -> {
                                            Text("${lyricsData!!.provider} • ${if (lyricsData!!.synced) "Synced" else "Plain"}", color = mutedText, fontSize = 11.sp)
                                            val activeLine = if (lyricsData!!.synced) lyricsData!!.lines.indexOfLast { it.startMs <= positionMs } else -1
                                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                items(lyricsData!!.lines.size) { index ->
                                                    val line = lyricsData!!.lines[index]
                                                    Text(
                                                        line.text,
                                                        color = if (index == activeLine) accentColor else Color(0xFFE8EEF7),
                                                        fontSize = if (index == activeLine) 21.sp else 17.sp,
                                                        fontWeight = if (index == activeLine) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "Settings" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    FeaturePanel(
                                        title = "Music folder",
                                        body = folderPath.ifBlank { "No folder selected." },
                                        footnote = "The selected folder is saved in the current Windows user profile."
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Autoplay", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Continue with related YouTube Music tracks, or the next local-library track, when playback ends.",
                                                color = mutedText,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Button(onClick = {
                                            autoplayEnabled = !autoplayEnabled
                                            preferences.putBoolean("autoplay_enabled", autoplayEnabled)
                                            runCatching { preferences.flush() }
                                        }) { Text(if (autoplayEnabled) "On" else "Off") }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Appearance palette", fontWeight = FontWeight.SemiBold)
                                        Text("Choose the accent used by the Windows player and controls.", color = mutedText, fontSize = 11.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            listOf("Ocean", "Violet", "Emerald", "Amber", "Rose", "Monochrome").forEach { preset ->
                                                Button(
                                                    onClick = { appearancePreset = preset },
                                                    colors = ButtonDefaults.buttonColors(
                                                        backgroundColor = desktopAccentFor(preset),
                                                        contentColor = Color(0xFF0B1420)
                                                    ),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) { Text(if (appearancePreset == preset) "✓ $preset" else preset) }
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Preferred playback format", fontWeight = FontWeight.SemiBold)
                                            Text("AAC/M4A is preferred for Windows playback; unavailable streams fall back to a supported audio stream.", color = mutedText, fontSize = 11.sp)
                                        }
                                        Button(onClick = {
                                            playbackFormat = if (playbackFormat == "m4a") "auto" else "m4a"
                                            preferences.put("playback_format", playbackFormat)
                                        }) { Text(playbackFormat.uppercase(Locale.ROOT)) }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Playback and download bitrate", fontWeight = FontWeight.SemiBold)
                                            Text("Target quality for stream selection and saved downloads.", color = mutedText, fontSize = 11.sp)
                                        }
                                        Button(onClick = {
                                            val options = listOf(64, 96, 128, 160, 192, 256, 320)
                                            targetBitrate = options[(options.indexOf(targetBitrate).coerceAtLeast(0) + 1) % options.size]
                                            preferences.putInt("target_bitrate", targetBitrate)
                                        }) { Text("${targetBitrate} kbps") }
                                    }
                                    Text("Files are stored in ${DesktopLibraryStore.root.absolutePath}", color = mutedText, fontSize = 11.sp)
                                }
                            }
                        }

                        if (statusMessage.isNotBlank() && section != "Library") {
                            Text(statusMessage, color = Color(0xFFFFC0A8), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                        }
                    }

                    MiniPlayer(
                        title = nowPlayingTitle,
                        artist = nowPlayingArtist,
                        hasTrack = hasNowPlaying,
                        isPlaying = playing,
                        isPaused = paused,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onPrevious = { playRelative(-1) },
                        onNext = { playRelative(1) },
                        onLyrics = { section = "Lyrics" },
                        onVideoToggle = if (currentOnlineSong != null) ::toggleVideoMode else null,
                        videoMode = videoMode,
                        onPlayPause = {
                            when {
                                player.isPlaying -> player.togglePlayback()
                                currentOnlineSong != null -> playOnlineSong(currentOnlineSong!!)
                                currentSavedSong != null -> playSavedSong(currentSavedSong!!)
                                currentTrack != null -> playTrack(currentTrack)
                                tracks.isNotEmpty() -> playTrack(tracks.first())
                            }
                        },
                        onSeek = { player.seekTo(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        color = if (selected) Color(0xFF23364D) else Color.Transparent,
        shape = RoundedCornerShape(13.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (label) {
                    "Home" -> "⌂"
                    "Explore" -> "⌕"
                    "Library" -> "♫"
                    "Queue" -> "☷"
                    "Online Library" -> "☁"
                    "Account" -> "◎"
                    "Playlists" -> "≡"
                    "Favorites" -> "♥"
                    "Downloads" -> "↓"
                    "Cache" -> "▣"
                    "Lyrics" -> "♪"
                    else -> "⚙"
                },
                color = if (selected) accentColor else mutedText,
                fontSize = 18.sp
            )
            Text(
                label,
                modifier = Modifier.padding(start = 12.dp),
                color = if (selected) Color.White else mutedText,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun EmptyPanel(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        backgroundColor = panelBackground,
        shape = RoundedCornerShape(20.dp),
        elevation = 0.dp
    ) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = mutedText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FeaturePanel(title: String, body: String, footnote: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = panelBackground,
        shape = RoundedCornerShape(20.dp),
        elevation = 0.dp
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Color(0xFFD7E0EC), fontSize = 13.sp)
            Text(footnote, color = mutedText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TrackRow(track: DesktopTrack, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        color = if (selected) Color(0xFF1D3045) else Color(0xFF111923),
        shape = RoundedCornerShape(13.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) Color(0xFF334D6D) else Color(0xFF253343)),
                contentAlignment = Alignment.Center
            ) {
                Text("♫", color = accentColor, fontSize = 19.sp)
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(track.title, maxLines = 1, fontWeight = FontWeight.Medium)
                Text(track.artist, maxLines = 1, color = mutedText, fontSize = 11.sp)
            }
            Text(track.extension, color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(track.sizeLabel, modifier = Modifier.width(74.dp).padding(start = 8.dp), color = mutedText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    artist: String,
    hasTrack: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLyrics: () -> Unit,
    onVideoToggle: (() -> Unit)?,
    videoMode: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var sliderPosition by remember(title, positionMs) { mutableStateOf(positionMs.toFloat()) }
    val maxPosition = durationMs.coerceAtLeast(1L).toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111923))
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title.ifBlank { "Nothing playing" }, maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(artist.ifBlank { "Choose a song from Explore or Library" }, maxLines = 1, color = mutedText, fontSize = 11.sp)
            }
            Button(
                onClick = onPrevious,
                enabled = hasTrack,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545)),
                elevation = ButtonDefaults.elevation(0.dp)
            ) { Text("◀") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onPlayPause,
                enabled = hasTrack,
                colors = ButtonDefaults.buttonColors(backgroundColor = accentColor, contentColor = Color(0xFF0B1420)),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                Text(if (isPlaying && !isPaused) "Ⅱ" else "▶", fontSize = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onNext,
                enabled = hasTrack,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545)),
                elevation = ButtonDefaults.elevation(0.dp)
            ) { Text("▶|") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onLyrics,
                enabled = hasTrack,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545)),
                elevation = ButtonDefaults.elevation(0.dp)
            ) { Text("Lyrics") }
            if (onVideoToggle != null) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onVideoToggle,
                    enabled = hasTrack,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (videoMode) accentColor else Color(0xFF263545),
                        contentColor = if (videoMode) Color(0xFF0B1420) else Color.White
                    ),
                    elevation = ButtonDefaults.elevation(0.dp)
                ) { Text(if (videoMode) "Audio" else "Video") }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(positionMs), color = mutedText, fontSize = 10.sp)
            Slider(
                value = sliderPosition.coerceIn(0f, maxPosition),
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onSeek(sliderPosition.toLong()) },
                enabled = hasTrack && durationMs > 0L,
                valueRange = 0f..maxPosition,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            Text(formatTime(durationMs), color = mutedText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SavedTrackRow(
    song: DesktopSavedSong,
    label: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick),
        color = Color(0xFF111923),
        shape = RoundedCornerShape(13.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF253343)),
                contentAlignment = Alignment.Center
            ) { Text("♫", color = accentColor, fontSize = 19.sp) }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(song.title, maxLines = 1, fontWeight = FontWeight.Medium)
                Text(song.artist, maxLines = 1, color = mutedText, fontSize = 11.sp)
            }
            Text(label, color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Button(onClick = onRemove, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545))) {
                Text("Remove")
            }
        }
    }
}

private fun scanMusicFolder(folder: File): List<DesktopTrack> {
    require(folder.exists() && folder.isDirectory) { "The selected music folder no longer exists." }
    val supported = setOf("mp3", "m4a", "aac", "ogg", "wav", "aif", "aiff", "au")
    return Files.walk(folder.toPath()).use { paths ->
        paths
            .filter { Files.isRegularFile(it) }
            .filter { path -> path.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT) in supported }
            .sorted()
            .map { path -> DesktopTrack(path.toFile()) }
            .toList()
    }
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val seconds = milliseconds / 1000L
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    return String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds)
}


@Composable
private fun OnlineResultRow(
    item: SearchItem,
    featured: Boolean = false,
    isFavorite: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Int = 0,
    onClick: () -> Unit,
    onFavorite: () -> Unit = {},
    onDownload: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {}
) {
    val title = DesktopYouTubeMusic.itemTitle(item)
    val subtitle = DesktopYouTubeMusic.itemSubtitle(item)
    val type = when (item) {
        is SearchItem.Song -> when {
            item.song.isLive -> "LIVE"
            item.song.isEpisode -> "EPISODE"
            item.song.isVideo -> "VIDEO"
            else -> "SONG"
        }
        is SearchItem.Album -> "ALBUM"
        is SearchItem.Artist -> "ARTIST"
        is SearchItem.Playlist -> if (item.playlist.isPodcast) "PODCAST" else "PLAYLIST"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (featured) Color(0xFF1E344B) else Color(0xFF111923),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (featured) 16.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (featured) 52.dp else 42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF263B52)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (item) {
                        is SearchItem.Song -> "♫"
                        is SearchItem.Album -> "▣"
                        is SearchItem.Artist -> "◉"
                        is SearchItem.Playlist -> "≡"
                    },
                    color = accentColor,
                    fontSize = 19.sp
                )
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(subtitle, maxLines = 1, color = mutedText, fontSize = 11.sp)
            }
            if (item is SearchItem.Song) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onFavorite,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545)),
                        elevation = ButtonDefaults.elevation(0.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                    ) { Text(if (isFavorite) "♥" else "♡") }
                    Button(
                        onClick = onDownload,
                        enabled = !isDownloading,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545)),
                        elevation = ButtonDefaults.elevation(0.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                    ) { Text(if (isDownloading) "$downloadProgress%" else "↓") }
                    Button(
                        onClick = onAddToPlaylist,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545)),
                        elevation = ButtonDefaults.elevation(0.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                    ) { Text("+") }
                }
            } else {
                Text(type, color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
