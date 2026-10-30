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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.prefs.Preferences
import com.dsann.music.data.innertube.SearchFilter
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.data.innertube.SearchResult
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val pageBackground = Color(0xFF0B0F15)
private val sidebarBackground = Color(0xFF101720)
private val panelBackground = Color(0xFF151E29)
private val accentColor = Color(0xFF9ABEFF)
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
    val coroutineScope = rememberCoroutineScope()

    val currentTrack = tracks.getOrNull(selectedIndex)
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
        selectedIndex = tracks.indexOf(track)
        statusMessage = ""
        player.play(track.file)
    }

    fun playRelative(direction: Int) {
        if (tracks.isEmpty()) return
        val base = selectedIndex.takeIf { it >= 0 } ?: 0
        selectedIndex = (base + direction + tracks.size) % tracks.size
        player.play(tracks[selectedIndex].file)
        statusMessage = ""
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
                    listOf("Explore", "Library", "Cache", "Settings").forEach { item ->
                        SidebarItem(
                            label = item,
                            selected = section == item,
                            onClick = { section = item }
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
                                        "Explore" -> "Explore"
                                        "Cache" -> "Offline cache"
                                        "Settings" -> "Settings"
                                        else -> "Your library"
                                    },
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    when (section) {
                                        "Explore" -> "Search YouTube Music using the current phone app's result categories."
                                        "Cache" -> "Offline downloads and cache management are not migrated yet."
                                        "Settings" -> "Desktop library and playback preferences."
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

                            "Explore" -> {
                                Column(Modifier.fillMaxSize()) {
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
                                            label = { Text("YouTube Music") }
                                        )
                                        Button(
                                            onClick = ::searchOnline,
                                            enabled = onlineQuery.isNotBlank() && !onlineSearching,
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = accentColor,
                                                contentColor = Color(0xFF0B1420)
                                            )
                                        ) {
                                            Text(if (onlineSearching) "Searching…" else "Search")
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    when {
                                        onlineSearching -> EmptyPanel("Searching YouTube Music…", "Phone-style top results are collected from separate result categories.")
                                        onlineError.isNotBlank() -> EmptyPanel("Search failed", onlineError)
                                        onlineResult == null -> EmptyPanel(
                                            "Explore music",
                                            "Search YouTube Music. Results are grouped into songs, videos, albums, artists, playlists, podcasts and episodes."
                                        )
                                        else -> {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                onlineResult?.topResult?.let { top ->
                                                    item(key = "top-result") {
                                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text("TOP RESULT", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            OnlineResultRow(top, featured = true) {
                                                                statusMessage = "Selected online result. Windows stream extraction and playback are the next platform adapter to port."
                                                            }
                                                        }
                                                    }
                                                }
                                                onlineResult?.sections.orEmpty().forEach { resultSection ->
                                                    item(key = "section-${resultSection.filter.name}") {
                                                        Text(
                                                            resultSection.filter.label.uppercase(Locale.ROOT),
                                                            color = mutedText,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                                                        )
                                                    }
                                                    items(resultSection.items.size) { index ->
                                                        val resultItem = resultSection.items[index]
                                                        OnlineResultRow(resultItem) {
                                                            statusMessage = "Selected online result. Windows stream extraction and playback are the next platform adapter to port."
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "Cache" -> {
                                FeaturePanel(
                                    title = "Offline cache migration is pending",
                                    body = "The Android cache is owned by Media3 and Android app storage. Windows needs its own download location, cache index and offline queue. This page is reserved for that implementation; it does not pretend Android cache files are already available on this PC.",
                                    footnote = "Your Android cache and downloads have not been changed."
                                )
                            }

                            else -> {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    FeaturePanel(
                                        title = "Music folder",
                                        body = folderPath.ifBlank { "No folder selected." },
                                        footnote = "The selected folder is saved in the current Windows user profile."
                                    )
                                    FeaturePanel(
                                        title = "Local playback formats in this first build",
                                        body = "MP3, OGG, WAV, AIFF and AU. M4A/AAC, FLAC, Opus and WebM need the wider desktop decoder integration and are not enabled in this initial port.",
                                        footnote = "The Android format selector and Android audio effects are not copied into Windows settings yet."
                                    )
                                }
                            }
                        }

                        if (statusMessage.isNotBlank() && section != "Library") {
                            Text(statusMessage, color = Color(0xFFFFC0A8), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                        }
                    }

                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = playing,
                        isPaused = paused,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onPrevious = { playRelative(-1) },
                        onNext = { playRelative(1) },
                        onPlayPause = {
                            when {
                                player.isPlaying -> player.togglePlayback()
                                currentTrack != null -> player.play(currentTrack.file)
                                tracks.isNotEmpty() -> {
                                    selectedIndex = 0
                                    player.play(tracks[0].file)
                                }
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
                    "Explore" -> "⌕"
                    "Library" -> "♫"
                    "Cache" -> "▣"
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
    track: DesktopTrack?,
    isPlaying: Boolean,
    isPaused: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var sliderPosition by remember(track, positionMs) { mutableStateOf(positionMs.toFloat()) }
    val maxPosition = durationMs.coerceAtLeast(1L).toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111923))
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track?.title ?: "Nothing playing", maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(track?.artist ?: "Choose a song from your library", maxLines = 1, color = mutedText, fontSize = 11.sp)
            }
            Button(
                onClick = onPrevious,
                enabled = track != null,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545)),
                elevation = ButtonDefaults.elevation(0.dp)
            ) { Text("◀") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onPlayPause,
                enabled = track != null,
                colors = ButtonDefaults.buttonColors(backgroundColor = accentColor, contentColor = Color(0xFF0B1420)),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                Text(if (isPlaying && !isPaused) "Ⅱ" else "▶", fontSize = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onNext,
                enabled = track != null,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263545)),
                elevation = ButtonDefaults.elevation(0.dp)
            ) { Text("▶|") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(positionMs), color = mutedText, fontSize = 10.sp)
            Slider(
                value = sliderPosition.coerceIn(0f, maxPosition),
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onSeek(sliderPosition.toLong()) },
                enabled = track != null && durationMs > 0L,
                valueRange = 0f..maxPosition,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            Text(formatTime(durationMs), color = mutedText, fontSize = 10.sp)
        }
    }
}

private fun scanMusicFolder(folder: File): List<DesktopTrack> {
    require(folder.exists() && folder.isDirectory) { "The selected music folder no longer exists." }
    val supported = setOf("mp3", "ogg", "wav", "aif", "aiff", "au")
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
    onClick: () -> Unit
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
            Text(type, color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
