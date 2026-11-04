@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dsann.music.ui.explore

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.model.SongItem
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.HorizontalDivider
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.LoadingIndicator
import com.dsann.music.ui.theme.ModalBottomSheet
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.rememberModalBottomSheetState
import com.dsann.music.ui.theme.rememberDsmusicModalSheetGeometry
import com.dsann.music.util.SongDownloader
import com.dsann.music.viewmodel.ExploreViewModel
import com.dsann.music.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.launch

@Composable
fun OnlineCollectionActionsBottomSheet(
    collection: SearchItem,
    initialSongs: List<OnlineSong>? = null,
    initialContinuation: String? = null,
    playerSharedViewModel: PlayerSharedViewModel,
    exploreViewModel: ExploreViewModel,
    onDismissRequest: () -> Unit,
    onAddToPlaylist: (List<SongItem>) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val modalGeometry = rememberDsmusicModalSheetGeometry()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cachedSongs by remember(collection, initialSongs, initialContinuation) {
        mutableStateOf(initialSongs?.takeIf { it.isNotEmpty() })
    }
    var isWorking by remember(collection) { mutableStateOf(false) }

    val title = when (collection) {
        is SearchItem.Album -> collection.album.title
        is SearchItem.Playlist -> collection.playlist.title
        else -> "Collection"
    }
    val subtitle = when (collection) {
        is SearchItem.Album -> "Album"
        is SearchItem.Playlist -> if (collection.playlist.isPodcast) "Podcast" else "Playlist"
        else -> ""
    }
    val thumbnail = when (collection) {
        is SearchItem.Album -> collection.album.thumbnailUrl
        is SearchItem.Playlist -> collection.playlist.thumbnailUrl
        else -> null
    }

    suspend fun loadAllPlaylistTracks(
        firstPageSongs: List<OnlineSong>,
        firstContinuation: String?
    ): List<OnlineSong> {
        val songs = firstPageSongs.toMutableList()
        val seenContinuations = HashSet<String>()
        var continuation = firstContinuation
        var pageCount = 0

        // Avoid unbounded requests if a continuation token is repeated or a huge playlist
        // has an unusually long tail. Normal playlists complete well before this limit.
        while (!continuation.isNullOrBlank() && pageCount < 40 && seenContinuations.add(continuation)) {
            val (nextSongs, nextContinuation) =
                YouTubeMusic.playlistContinuation(continuation).getOrThrow()
            if (nextSongs.isEmpty()) break
            songs.addAll(nextSongs)
            continuation = nextContinuation
            pageCount++
        }
        return songs.distinctBy { it.videoId }
    }

    suspend fun resolveSongs(): List<OnlineSong> {
        cachedSongs?.let { songs ->
            val completeSongs = if (collection is SearchItem.Playlist) {
                loadAllPlaylistTracks(songs, initialContinuation)
            } else {
                songs
            }
            if (completeSongs.isNotEmpty()) {
                cachedSongs = completeSongs
                return completeSongs
            }
        }

        val songs = when (collection) {
            is SearchItem.Album -> YouTubeMusic.album(collection.album.browseId).getOrThrow().songs
            is SearchItem.Playlist -> {
                val firstPage = YouTubeMusic.playlist(collection.playlist.playlistId).getOrThrow()
                loadAllPlaylistTracks(firstPage.songs, firstPage.continuation)
            }
            else -> emptyList()
        }
        if (songs.isEmpty()) {
            throw IllegalStateException("No tracks were found for this collection")
        }
        cachedSongs = songs
        return songs
    }

    fun runTrackAction(action: suspend (List<OnlineSong>) -> Unit) {
        if (isWorking) return
        isWorking = true
        scope.launch {
            try {
                action(resolveSongs())
            } catch (error: Exception) {
                com.dsann.music.ui.SnackbarController.showMessage(
                    error.message ?: "Could not load this collection"
                )
            } finally {
                isWorking = false
            }
        }
    }

    fun toSongItem(song: OnlineSong): SongItem = SongItem.createOnlineSong(
        song.videoId,
        song.title,
        song.artist,
        "https://music.youtube.com/watch?v=${song.videoId}",
        song.durationMs,
        song.thumbnailUrl,
        song.artistId,
        song.albumName,
        song.artistRefs
    )

    val shareUrl = when (collection) {
        is SearchItem.Album -> collection.album.playlistId
            ?.takeIf { it.isNotBlank() }
            ?.let { "https://music.youtube.com/playlist?list=$it" }
            ?: "https://music.youtube.com/browse/${collection.album.browseId}"
        is SearchItem.Playlist -> "https://music.youtube.com/playlist?list=${collection.playlist.playlistId}"
        else -> "https://music.youtube.com"
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = if (isLandscape) modalGeometry.width else androidx.compose.ui.unit.Dp.Unspecified,
        centeredLandscape = true,
        centerOffsetX = modalGeometry.centerOffsetX,
        containerColor = DsmusicTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExploreActionSheetHeader(
                thumbnailUrl = thumbnail,
                title = title,
                subtitle = subtitle,
                trailingContent = {
                    if (isWorking) {
                        LoadingIndicator(Modifier.size(22.dp))
                    } else {
                        DsmusicIconButton(onClick = onDismissRequest) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            )

            HorizontalDivider(color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                CollectionPrimaryAction(
                    modifier = Modifier.weight(1f),
                    title = "Play",
                    icon = Icons.Default.PlayArrow,
                    enabled = !isWorking
                ) {
                    runTrackAction { songs ->
                        if (songs.isNotEmpty()) {
                            exploreViewModel.playOnlineSongWithQueue(songs.first(), songs, 0)
                            com.dsann.music.ui.SnackbarController.showMessage("Playing $title")
                            onDismissRequest()
                        }
                    }
                }
                CollectionPrimaryAction(
                    modifier = Modifier.weight(1f),
                    title = "Queue",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    enabled = !isWorking
                ) {
                    runTrackAction { songs ->
                        songs.forEach { song ->
                            playerSharedViewModel.addToQueue(toSongItem(song))
                            exploreViewModel.addToQueueLast(song)
                        }
                        com.dsann.music.ui.SnackbarController.showMessage("Added ${songs.size} tracks to queue")
                        onDismissRequest()
                    }
                }
                CollectionPrimaryAction(
                    modifier = Modifier.weight(1f),
                    title = "Next",
                    icon = Icons.Default.PlayArrow,
                    enabled = !isWorking
                ) {
                    runTrackAction { songs ->
                        songs.asReversed().forEach { song ->
                            playerSharedViewModel.addToQueueNext(toSongItem(song))
                            exploreViewModel.addToQueueNext(song)
                        }
                        com.dsann.music.ui.SnackbarController.showMessage("Playing next: $title")
                        onDismissRequest()
                    }
                }
            }

            Text(
                "MORE OPTIONS",
                style = DsmusicTheme.typography.labelSmall.copy(
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = DsmusicTheme.colorScheme.onSurfaceVariant
            )

            CollectionActionRow(
                title = "Download",
                subtitle = "Download every track for offline playback",
                icon = { Icon(Icons.Default.Download, null) },
                enabled = !isWorking
            ) {
                runTrackAction { songs ->
                    songs.forEach { SongDownloader.downloadSong(context, toSongItem(it)) }
                    com.dsann.music.ui.SnackbarController.showMessage("Started downloads for ${songs.size} tracks")
                    onDismissRequest()
                }
            }

            CollectionActionRow(
                title = "Add to playlist",
                subtitle = "Add every track to one of your local playlists",
                icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                enabled = !isWorking
            ) {
                runTrackAction { songs ->
                    onAddToPlaylist(songs.map(::toSongItem))
                    onDismissRequest()
                }
            }

            CollectionActionRow(
                title = "Share",
                subtitle = "Share this ${subtitle.lowercase()}",
                icon = { Icon(Icons.Default.Share, null) },
                enabled = !isWorking
            ) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Check out $title: $shareUrl")
                }
                context.startActivity(Intent.createChooser(intent, "Share via"))
                onDismissRequest()
            }
        }
    }
}

@Composable
private fun CollectionPrimaryAction(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DsmusicTheme.colorScheme.primaryContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = DsmusicTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(23.dp)
        )
        Text(
            title,
            style = DsmusicTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = DsmusicTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun CollectionActionRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(DsmusicTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = DsmusicTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
