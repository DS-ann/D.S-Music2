@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS",
    "TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS"
)

package com.codetrio.spatialflow.ui.library

import android.app.Activity
import androidx.activity.compose.BackHandler
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.db.PlaylistEntity
import com.codetrio.spatialflow.data.lyrics.MetadataRepair
import com.codetrio.spatialflow.data.lyrics.engine.LyricsCacheManager
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.service.AudioPlaybackService
import com.codetrio.spatialflow.service.CachedMediaMetadata
import com.codetrio.spatialflow.util.SongDownloader
import com.codetrio.spatialflow.model.toSongItem
import com.codetrio.spatialflow.ui.CreateLocalPlaylistDialog
import com.codetrio.spatialflow.ui.LocalPlaylistPickerDialog
import com.codetrio.spatialflow.ui.SongActionsBottomSheet
import com.codetrio.spatialflow.ui.explore.AccountScreen
import com.codetrio.spatialflow.ui.explore.OnlineSongBottomSheet
import com.codetrio.spatialflow.ui.explore.SongCreditsScreen
import com.codetrio.spatialflow.viewmodel.AccountViewModel
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class SortOrder {
    A_Z,
    DATE_ADDED,
    ARTIST,
    DURATION
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: PlayerSharedViewModel,
    onEditSong: (SongItem) -> Unit = {},
    onNavigateToExplore: () -> Unit = {}
) {
    val context = LocalContext.current
    val mainActivity = remember(context) { getActivityFromContext(context) as? MainActivity }
    val fragmentActivity = remember(context) { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }
    val fragmentManager = remember(fragmentActivity) { fragmentActivity?.supportFragmentManager }

    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var activeTab by rememberSaveable { mutableStateOf("Playlists") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(SortOrder.A_Z) }
    var playlistsAsList by rememberSaveable { mutableStateOf(false) }
    var albumsAsList by rememberSaveable { mutableStateOf(false) }
    var artistsAsList by rememberSaveable { mutableStateOf(false) }
    val currentCollectionAsList = when (activeTab) {
        "Playlists" -> playlistsAsList
        "Albums" -> albumsAsList
        "Artists" -> artistsAsList
        else -> false
    }
    var isRefreshing by remember { mutableStateOf(false) }

    var showAccountScreen by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }

    val accountViewModel = remember(fragmentActivity) {
        fragmentActivity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(fragmentActivity) {
        fragmentActivity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val history by accountViewModel?.history?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val currentOnlineSong by exploreViewModel?.currentOnlineSong?.collectAsStateWithLifecycle(null) ?: remember { mutableStateOf(null) }
    val isLoadingStream by exploreViewModel?.isLoadingStream?.collectAsStateWithLifecycle(false) ?: remember { mutableStateOf(false) }

    val localSongs by viewModel.localSongs.collectAsStateWithLifecycle(emptyList())
    val downloadProgress by SongDownloader.downloadProgress.collectAsStateWithLifecycle(emptyMap())
    val favoriteIds by viewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()

    var selectedSongForMenu by remember { mutableStateOf<OnlineSong?>(null) }
    var songToAddPlaylist by remember { mutableStateOf<SongItem?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCreditsForSong by remember { mutableStateOf<OnlineSong?>(null) }
    val localPlaylists by viewModel.localPlaylistsFlow.collectAsStateWithLifecycle(emptyList())

    // Library sections are local UI state rather than separate NavController destinations.
    // Consume Back here so it returns to the Library landing page.
    BackHandler(
        enabled = activeTab != "Playlists" || showAccountScreen || showHistoryScreen || showCreditsForSong != null
    ) {
        when {
            showCreditsForSong != null -> showCreditsForSong = null
            showHistoryScreen -> showHistoryScreen = false
            showAccountScreen -> showAccountScreen = false
            activeTab != "Playlists" -> {
                activeTab = "Playlists"
                searchQuery = ""
                mainActivity?.showBottomNavWithAnimation()
            }
        }
    }

    // Migrate old saved Cache/blank destinations to Playlists; Cache now lives inside Playlists.
    LaunchedEffect(Unit) {
        if (activeTab.isBlank() || activeTab == "Cache") activeTab = "Playlists"
    }

    // Trigger local files scan on first composition
    LaunchedEffect(Unit) {
        if (localSongs.isEmpty()) {
            scanLocalFiles(context, viewModel)
        }
    }

    // Refresh the downloaded-songs folder when opened and when an active download finishes.
    LaunchedEffect(activeTab, downloadProgress.isEmpty()) {
        if (activeTab == "Downloaded songs" && downloadProgress.isEmpty()) {
            scanLocalFiles(context, viewModel)
        }
    }

    // Observe online playback trigger from ExploreViewModel for History/Account pages
    LaunchedEffect(exploreViewModel) {
        exploreViewModel?.playbackTriggerEvent?.collect {
            val song = exploreViewModel.currentOnlineSong.value
            if (song != null) {
                val videoId = song.videoId
                val durationMs = song.durationMs
                val onlineQueue = exploreViewModel.onlineQueue.value
                if (onlineQueue.isNotEmpty()) {
                    val songItems = onlineQueue.map { os ->
                        SongItem.createOnlineSong(
                            os.videoId,
                            os.title,
                            os.artist,
                            "", // Empty streamUrl permits automatic resolving pipeline promotion
                            os.durationMs,
                            os.thumbnailUrl,
                            os.artistId,
                            os.albumName
                        )
                    }
                    viewModel.setSongList(songItems)
                    val currentIdx = exploreViewModel.currentOnlineIndex.value
                    viewModel.playSongAtIndex(currentIdx)
                } else {
                    val songItem = SongItem.createOnlineSong(
                        videoId, song.title, song.artist, "", durationMs, song.thumbnailUrl, song.artistId, song.albumName
                    )
                    viewModel.playSong(songItem)
                }
            }
        }
    }

    val subscriptionChanged by exploreViewModel?.subscriptionChanged?.collectAsStateWithLifecycle(false) ?: remember { mutableStateOf(false) }
    LaunchedEffect(subscriptionChanged) {
        if (subscriptionChanged) {
            accountViewModel?.loadLibrary()
            exploreViewModel?.consumeSubscriptionChanged()
        }
    }

    val nestedScrollConnection = remember(mainActivity) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (mainActivity != null) {
                    if (consumed.y < -10f) {
                        mainActivity.hideBottomNavWithAnimation()
                    } else if (consumed.y > 10f) {
                        mainActivity.showBottomNavWithAnimation()
                    }
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    Scaffold(
        // snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, // Replaced by global SnackbarController
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Row (Hides in Device Files)
                AnimatedVisibility(
                    visible = activeTab != "Device Files",
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (activeTab == "Playlists" || activeTab == "Albums" || activeTab == "Artists") {
                                IconButton(
                                    onClick = {
                                        when (activeTab) {
                                            "Playlists" -> playlistsAsList = !playlistsAsList
                                            "Albums" -> albumsAsList = !albumsAsList
                                            "Artists" -> artistsAsList = !artistsAsList
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentCollectionAsList) Icons.Default.GridView else Icons.Default.ViewList,
                                        contentDescription = if (currentCollectionAsList) "Show as grid" else "Show as list",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            IconButton(onClick = { activeTab = "History" }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_history),
                                    contentDescription = "Listening History",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            val userProfile by accountViewModel?.userProfile?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
                            if (userProfile?.avatarUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(userProfile?.avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Account Settings",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { showAccountScreen = true },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                IconButton(onClick = { showAccountScreen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Account Settings",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Keep Library navigation visible while browsing, like Prism's tab-first Library.
                val tabs = listOf(
                    "Playlists" to "Playlists",
                    "Songs" to "Songs",
                    "Albums" to "Albums",
                    "Artists" to "Artists",
                    "History" to "History",
                    "Downloads" to "Downloaded songs",
                    "Podcasts" to "Podcasts",
                    "Recap" to "Recap",
                    "Local music" to "Device Files"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { (label, tabKey) ->
                        FilterChip(
                            selected = activeTab == tabKey,
                            onClick = {
                                activeTab = tabKey
                                if (tabKey == "History") accountViewModel?.loadLibrary()
                            },
                            label = { Text(label, fontWeight = FontWeight.SemiBold) },
                            shape = CircleShape,
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
            ) {
                when (activeTab) {
                    "Playlists" -> PlaylistsTabContent(nestedScrollConnection, viewModel, playlistsAsList)
                    "Podcasts" -> PodcastsTabContent(nestedScrollConnection)
                    "Songs" -> SongsTabContent(nestedScrollConnection, viewModel)
                    "Albums" -> AlbumsTabContent(nestedScrollConnection, albumsAsList)
                    "Artists" -> ArtistsTabContent(nestedScrollConnection, artistsAsList)
                    "Recap" -> RecapTabContent(nestedScrollConnection, viewModel, onNavigateToExplore)
                    "Device Files", "Downloaded songs" -> {
                        // Scan Local Files helper
                        val onRefreshAction = {
                            scope.launch {
                                isRefreshing = true
                                scanLocalFiles(context, viewModel)
                                isRefreshing = false
                            }
                        }

                        val pullToRefreshState = rememberPullToRefreshState()

                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { onRefreshAction() },
                            state = pullToRefreshState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Search Bar
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search local songs...") },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Clear, null)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = CircleShape,
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )

                                // Sorting chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SortOrder.entries.forEach { order ->
                                        val isSel = sortOrder == order
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { sortOrder = order },
                                            label = {
                                                Text(
                                                    text = order.name.replace("_", " "),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            border = null,
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Keep this folder scoped to files saved by SpatialFlow's downloader.
                                val sourceSongs = remember(localSongs, activeTab) {
                                    if (activeTab == "Downloaded songs") {
                                        localSongs.filter(::isSpatialFlowDownloadedSong)
                                    } else {
                                        localSongs
                                    }
                                }

                                // Filter, Sort, and Group local songs
                                val filteredSongs = remember(sourceSongs, searchQuery) {
                                    sourceSongs.filter {
                                        it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true)
                                    }
                                }

                                val sortedAndGrouped = remember(filteredSongs, sortOrder) {
                                    if (filteredSongs.isEmpty()) return@remember emptyList()

                                    val sorted = when (sortOrder) {
                                        SortOrder.A_Z -> filteredSongs.sortedWith(compareBy { it.title.lowercase() })
                                        SortOrder.DATE_ADDED -> filteredSongs.sortedWith(compareByDescending { it.dateAdded })
                                        SortOrder.ARTIST -> filteredSongs.sortedWith(compareBy { it.artist.lowercase() })
                                        SortOrder.DURATION -> filteredSongs.sortedWith(compareByDescending { it.duration })
                                    }

                                    val groups = LinkedHashMap<String, MutableList<SongItem>>()
                                    when (sortOrder) {
                                        SortOrder.A_Z -> {
                                            for (song in sorted) {
                                                val header = if (song.title.isEmpty()) "#" else {
                                                    val char = song.title.uppercase().first()
                                                    if (char.isLetter()) char.toString() else "#"
                                                }
                                                groups.getOrPut(header) { mutableListOf() }.add(song)
                                            }
                                        }
                                        SortOrder.DATE_ADDED -> {
                                            for (song in sorted) {
                                                val diff = (System.currentTimeMillis() / 1000) - song.dateAdded
                                                val days = diff / (60 * 60 * 24)
                                                val header = when {
                                                    days < 1 -> "Today"
                                                    days < 2 -> "Yesterday"
                                                    days < 7 -> "This Week"
                                                    days < 30 -> "This Month"
                                                    days < 365 -> "This Year"
                                                    else -> "Older"
                                                }
                                                groups.getOrPut(header) { mutableListOf() }.add(song)
                                            }
                                        }
                                        SortOrder.ARTIST -> {
                                            for (song in sorted) {
                                                val header = song.artist.ifBlank { "Unknown Artist" }
                                                groups.getOrPut(header) { mutableListOf() }.add(song)
                                            }
                                        }
                                        SortOrder.DURATION -> {
                                            for (song in sorted) {
                                                val min = song.duration / 60000
                                                val header = when {
                                                    min < 2 -> "Under 2 min"
                                                    min < 4 -> "2-4 min"
                                                    min < 6 -> "4-6 min"
                                                    min < 10 -> "6-10 min"
                                                    else -> "Over 10 min"
                                                }
                                                groups.getOrPut(header) { mutableListOf() }.add(song)
                                            }
                                        }
                                    }

                                    val list = mutableListOf<Any>()
                                    for ((header, groupSongs) in groups) {
                                        list.add(header)
                                        list.addAll(groupSongs)
                                    }
                                    list
                                }

                                if (sortedAndGrouped.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            when {
                                                searchQuery.isNotEmpty() -> "No matching songs found"
                                                activeTab == "Downloaded songs" -> "No downloaded songs yet. Downloaded tracks will appear here."
                                                else -> "Scan device files to load songs"
                                            },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .weight(1f),
                                        contentPadding = PaddingValues(bottom = 160.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        itemsIndexed(sortedAndGrouped) { index, item ->
                                            if (item is String) {
                                                Text(
                                                    text = item,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                                )
                                            } else if (item is SongItem) {
                                                val isPlaying = currentSong?.id == item.id
                                                val isFav = favoriteIds.contains(item.id)

                                                // Calculate segment position
                                                var sectionStartIndex = index
                                                for (i in index - 1 downTo 0) {
                                                    if (sortedAndGrouped[i] is String) {
                                                        sectionStartIndex = i + 1
                                                        break
                                                    }
                                                    if (i == 0) sectionStartIndex = 0
                                                }

                                                var count = 0
                                                for (i in sectionStartIndex until sortedAndGrouped.size) {
                                                    if (sortedAndGrouped[i] is String) break
                                                    count++
                                                }

                                                val localIndex = index - sectionStartIndex

                                                // SwipeToDismissBox implementation
                                                val dismissState = rememberSwipeToDismissBoxState(
                                                    confirmValueChange = { dismissValue ->
                                                        when (dismissValue) {
                                                            SwipeToDismissBoxValue.StartToEnd -> {
                                                                viewModel.addToQueueNext(item)
                                                                scope.launch {
                                                                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Playing next: ${item.title}")
                                                                }
                                                                false
                                                            }
                                                            SwipeToDismissBoxValue.EndToStart -> {
                                                                viewModel.addToQueue(item)
                                                                scope.launch {
                                                                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Added to queue: ${item.title}")
                                                                }
                                                                false
                                                            }
                                                            else -> false
                                                        }
                                                    }
                                                )

                                                SwipeToDismissBox(
                                                    state = dismissState,
                                                    backgroundContent = {
                                                        val color = when (dismissState.dismissDirection) {
                                                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondaryContainer
                                                            else -> Color.Transparent
                                                        }
                                                        val icon = when (dismissState.dismissDirection) {
                                                            SwipeToDismissBoxValue.StartToEnd -> Icons.Default.SkipNext
                                                            SwipeToDismissBoxValue.EndToStart -> Icons.AutoMirrored.Filled.QueueMusic
                                                            else -> null
                                                        }
                                                        val alignment = when (dismissState.dismissDirection) {
                                                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                                            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                                            else -> Alignment.Center
                                                        }
                                                        val topRadius = if (localIndex == 0) 24.dp else 4.dp
                                                        val bottomRadius = if (localIndex == count - 1) 24.dp else 4.dp
                                                        val backgroundShape = RoundedCornerShape(
                                                            topStart = topRadius, topEnd = topRadius,
                                                            bottomStart = bottomRadius, bottomEnd = bottomRadius
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(horizontal = 8.dp, vertical = 1.dp)
                                                                .clip(backgroundShape)
                                                                .background(color.copy(alpha = dismissState.progress.coerceIn(0f, 1f))),
                                                            contentAlignment = alignment
                                                        ) {
                                                            if (icon != null) {
                                                                Icon(
                                                                    imageVector = icon,
                                                                    contentDescription = null,
                                                                    modifier = Modifier
                                                                        .padding(horizontal = 24.dp)
                                                                        .graphicsLayer {
                                                                            val p = dismissState.progress
                                                                            scaleX = (p * 1.2f).coerceIn(0.6f, 1.1f)
                                                                            scaleY = (p * 1.2f).coerceIn(0.6f, 1.1f)
                                                                            alpha = p.coerceIn(0.3f, 1.0f)
                                                                        },
                                                                    tint = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                                    } else {
                                                                        MaterialTheme.colorScheme.onSecondaryContainer
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    },
                                                    content = {
                                                        SongListItem(
                                                            song = item,
                                                            isPlaying = isPlaying,
                                                            isFavorite = isFav,
                                                            localIndex = localIndex,
                                                            localCount = count,
                                                            onClick = {
                                                                // Play clicked song, set the entire filtered songs list as queue
                                                                viewModel.setSongList(filteredSongs)
                                                                viewModel.playSong(item)
                                                            },
                                                            onLongClick = {
                                                                val bs = SongActionsBottomSheet.newInstance(item)
                                                                bs.setActionListener(object : SongActionsBottomSheet.ActionListener {
                                                                    override fun onPlay(song: SongItem) {
                                                                        viewModel.playSong(song)
                                                                    }
                                                                    override fun onPlayNext(song: SongItem) {
                                                                        viewModel.addToQueueNext(song)
                                                                        scope.launch {
                                                                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Playing next: ${song.title}")
                                                                        }
                                                                    }
                                                                    override fun onAddToQueue(song: SongItem) {
                                                                        viewModel.addToQueue(song)
                                                                        scope.launch {
                                                                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Added to queue: ${song.title}")
                                                                        }
                                                                    }
                                                                    override fun onDelete(song: SongItem) {
                                                                        try {
                                                                            val file = java.io.File(song.path)
                                                                            if (file.exists()) file.delete()
                                                                            // Rescan
                                                                            scope.launch {
                                                                                scanLocalFiles(context, viewModel)
                                                                            }
                                                                        } catch (_: Exception) {}
                                                                    }
                                                                    override fun onEdit(song: SongItem) {
                                                                        onEditSong(song)
                                                                    }
                                                                    override fun onFavorite(song: SongItem, isFav: Boolean) {
                                                                        viewModel.toggleFavorite(song.id)
                                                                    }
                                                                    override fun onShare(song: SongItem) {
                                                                        try {
                                                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                                                type = "audio/*"
                                                                                putExtra(Intent.EXTRA_STREAM, song.contentUri)
                                                                                putExtra(Intent.EXTRA_TEXT, "Listen to ${song.title}")
                                                                            }
                                                                            context.startActivity(Intent.createChooser(intent, "Share Song"))
                                                                        } catch (_: Exception) {}
                                                                    }
                                                                })
                                                                fragmentManager?.let { bs.show(it, "SongActionsBottomSheet") }
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "History" -> HistoryScreen(
                        history = history,
                        currentOnlineSong = currentOnlineSong,
                        isLoadingStream = isLoadingStream,
                        isRefreshing = accountViewModel?.isLoading?.collectAsStateWithLifecycle()?.value == true,
                        onRefresh = { accountViewModel?.loadLibrary() },
                        onBack = { activeTab = "Playlists" },
                        onSongClick = { song, queue, index ->
                            exploreViewModel?.playOnlineSongWithQueue(song, queue, index)
                        },
                        onSongMenuClick = { song -> selectedSongForMenu = song },
                        showTopBar = false
                    )
                    "" -> PlaylistsTabContent(nestedScrollConnection, viewModel, playlistsAsList)
                }
            }
        }

        // Overlay AccountScreen
        AnimatedVisibility(
            visible = showAccountScreen && accountViewModel != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (accountViewModel != null) {
                AccountScreen(
                    viewModel = accountViewModel,
                    onBack = { showAccountScreen = false },
                    onSongClick = { song, queue, index ->
                        exploreViewModel?.playOnlineSongWithQueue(song, queue, index)
                    },
                    onNavigateToSignIn = {
                        showAccountScreen = false
                        mainActivity?.navigateToGoogleSignIn()
                    }
                )
            }
        }

        // Overlay HistoryScreen
        AnimatedVisibility(
            visible = showHistoryScreen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            HistoryScreen(
                history = history,
                currentOnlineSong = currentOnlineSong,
                isLoadingStream = isLoadingStream,
                isRefreshing = accountViewModel?.isLoading?.collectAsStateWithLifecycle()?.value == true,
                onRefresh = { accountViewModel?.loadLibrary() },
                onBack = { showHistoryScreen = false },
                onSongClick = { song, queue, index ->
                    exploreViewModel?.playOnlineSongWithQueue(song, queue, index)
                },
                onSongMenuClick = { song ->
                    selectedSongForMenu = song
                }
            )
        }

        // Song 3-Dot Menu Bottom Sheet
        selectedSongForMenu?.let { song ->
            val isPinned = exploreViewModel?.uiState?.collectAsState()?.value?.pinnedSpeedDialIds?.contains(song.videoId) == true
            if (exploreViewModel != null) {
                OnlineSongBottomSheet(
                    song = song,
                    isPinned = isPinned,
                    onDismissRequest = { selectedSongForMenu = null },
                    playerSharedViewModel = viewModel,
                    exploreViewModel = exploreViewModel,
                    onPlaylistAddClick = { onlineSongItem ->
                        songToAddPlaylist = onlineSongItem
                        showAddToPlaylistDialog = true
                    },
                    onViewCreditsClick = { s ->
                        showCreditsForSong = s
                    }
                )
            }
        }

        if (showAddToPlaylistDialog && songToAddPlaylist != null) {
            LocalPlaylistPickerDialog(
                playlists = localPlaylists,
                onCreateNew = {
                    showCreatePlaylistDialog = true
                    showAddToPlaylistDialog = false
                },
                onPlaylistSelected = { playlist ->
                    viewModel.addSongToLocalPlaylist(playlist.id, songToAddPlaylist!!)
                    showAddToPlaylistDialog = false
                    songToAddPlaylist = null
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Added to playlist: ${playlist.title}")
                },
                onDismiss = {
                    showAddToPlaylistDialog = false
                    songToAddPlaylist = null
                }
            )
        }

        if (showCreatePlaylistDialog) {
            CreateLocalPlaylistDialog(
                onConfirm = { name ->
                    viewModel.createLocalPlaylist(name)
                    showCreatePlaylistDialog = false
                    showAddToPlaylistDialog = true
                },
                onDismiss = {
                    showCreatePlaylistDialog = false
                    showAddToPlaylistDialog = true
                }
            )
        }

        // Overlay SongCreditsScreen
        AnimatedVisibility(
            visible = showCreditsForSong != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            showCreditsForSong?.let { song ->
                SongCreditsScreen(
                    song = song,
                    onBack = { showCreditsForSong = null }
                )
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongListItem(
    song: SongItem,
    isPlaying: Boolean,
    isFavorite: Boolean,
    localIndex: Int,
    localCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shapes = ListItemDefaults.segmentedShapes(index = localIndex, count = localCount)
    val defaultBg = MaterialTheme.colorScheme.surfaceContainer
    val playingBg = MaterialTheme.colorScheme.primaryContainer

    val contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary

    ListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = shapes,
        colors = ListItemDefaults.colors(
            containerColor = if (isPlaying) playingBg else defaultBg,
            headlineColor = contentColor,
            supportingColor = subTextColor,
            leadingIconColor = accentColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp),
        leadingContent = {
            val context = LocalContext.current
            val artModel = remember(song) { song.getAlbumArtUri() ?: R.drawable.ic_music_note }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artModel)
                    .crossfade(true)
                    .error(R.drawable.ic_music_note)
                    .placeholder(R.drawable.ic_music_note)
                    .fallback(R.drawable.ic_music_note)
                    .build(),
                contentDescription = "Album Art",
                modifier = Modifier
                    .size(54.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Now Playing",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        content = {
            Column {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

@Composable
private fun UnifiedLibraryContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    onNavigateToTab: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }
    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlinePlaylists by accountViewModel?.playlists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlineAlbums by accountViewModel?.albums?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlineArtists by accountViewModel?.artists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlinePodcasts by accountViewModel?.podcasts?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlineSongs by accountViewModel?.songs?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    // Keep category callbacks current without rebuilding all library tiles on parent recomposition.
    val navigateToTab = rememberUpdatedState(onNavigateToTab)

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    val unifiedItems = remember(onlinePlaylists, onlineAlbums, onlineArtists, onlinePodcasts, onlineSongs) {
        val list = mutableListOf<UnifiedLibraryItem>()

        // Keep the key library actions first, in a stable Prism-style tile order.
        list.addAll(
            listOf(
                LibraryCategoryItem("Playlists", "Your playlists", Icons.AutoMirrored.Filled.PlaylistPlay) {
                    navigateToTab.value("Playlists")
                },
                LibraryCategoryItem("Liked songs", "Your YouTube Music likes", Icons.Default.Favorite) {
                    navigateToTab.value("Songs")
                },
                LibraryCategoryItem("Albums", "Browse saved albums", Icons.Default.Album) {
                    navigateToTab.value("Albums")
                },
                LibraryCategoryItem("Artists", "Browse artists", Icons.Default.Person) {
                    navigateToTab.value("Artists")
                },
                LibraryCategoryItem("Podcasts", "Podcast library", Icons.Default.Podcasts) {
                    navigateToTab.value("Podcasts")
                },
                LibraryCategoryItem("Recap", "Your listening recap", Icons.Default.BarChart) {
                    navigateToTab.value("Recap")
                },
                LibraryCategoryItem("Downloaded songs", "Music saved on this device", Icons.AutoMirrored.Filled.QueueMusic) {
                    navigateToTab.value("Downloaded songs")
                },
            )
        )

        onlinePlaylists.forEach { playlist ->
            list.add(object : UnifiedLibraryItem {
                override val title = playlist.title
                override val thumbnailUrl = playlist.thumbnailUrl
                override val subtitle = "Playlist" + (if (playlist.songCount?.isNotEmpty() == true) " • ${playlist.songCount}" else "")
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadPlaylist(playlist.playlistId)
                    navigateToExplore(activity)
                }
                override val isCircle = false
            })
        }

        onlineAlbums.forEach { album ->
            list.add(object : UnifiedLibraryItem {
                override val title = album.title
                override val thumbnailUrl = album.thumbnailUrl
                override val subtitle = "Album" + (if (album.artists.isNotEmpty()) " • ${album.artists.firstOrNull()?.name}" else "")
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadAlbum(album.browseId)
                    navigateToExplore(activity)
                }
                override val isCircle = false
            })
        }

        onlineArtists.forEach { artist ->
            list.add(object : UnifiedLibraryItem {
                override val title = artist.title
                override val thumbnailUrl = artist.thumbnailUrl
                override val subtitle = "Artist"
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadArtist(artist.browseId, artist.thumbnailUrl)
                    navigateToExplore(activity)
                }
                override val isCircle = true
            })
        }

        onlinePodcasts.forEach { podcast ->
            list.add(object : UnifiedLibraryItem {
                override val title = podcast.title
                override val thumbnailUrl = podcast.thumbnailUrl
                override val subtitle = "Podcast"
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadPlaylist(podcast.playlistId)
                    navigateToExplore(activity)
                }
                override val isCircle = false
            })
        }

        // Categories stay first; the saved items below retain their source order.
        list
    }

    if (unifiedItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No items found in your library", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            items(unifiedItems) { item ->
                UnifiedLibraryCard(item)
            }
        }
    }
}

private interface UnifiedLibraryItem {
    val title: String
    val thumbnailUrl: String?
    val subtitle: String
    val onClick: () -> Unit
    val isCircle: Boolean
    val icon: ImageVector? get() = null
}

private data class LibraryCategoryItem(
    override val title: String,
    override val subtitle: String,
    override val icon: ImageVector,
    override val onClick: () -> Unit
) : UnifiedLibraryItem {
    override val thumbnailUrl: String? = null
    override val isCircle: Boolean = false
}

private fun libraryCardGradient(title: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)),
        listOf(Color(0xFF4776E6), Color(0xFF8E54E9)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
        listOf(Color(0xFF0575E6), Color(0xFF00F260)),
        listOf(Color(0xFF7F00FF), Color(0xFFE100FF)),
        listOf(Color(0xFFFC4A1A), Color(0xFFF7B733))
    )
    return palettes[Math.floorMod(title.hashCode(), palettes.size)]
}

@Composable
private fun UnifiedLibraryCard(item: UnifiedLibraryItem) {
    val accent = remember(item.title) { libraryCardGradient(item.title) }
    val hasArtwork = !item.thumbnailUrl.isNullOrBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = accent.map { it.copy(alpha = if (hasArtwork) 0.13f else 0.20f) }
                )
            )
            .clickable { item.onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(if (item.isCircle) CircleShape else RoundedCornerShape(12.dp))
                .background(
                    if (hasArtwork) {
                        Brush.linearGradient(accent.map { it.copy(alpha = 0.16f) })
                    } else {
                        Brush.linearGradient(accent)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hasArtwork) {
                androidx.compose.foundation.Image(
                    painter = coil.compose.rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.thumbnailUrl)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon ?: if (item.isCircle) Icons.Default.Person else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            item.title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            item.subtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistsTabContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    viewModel: PlayerSharedViewModel,
    showAsList: Boolean = false
) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlinePlaylists by accountViewModel?.playlists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val localPlaylists by viewModel.localPlaylistsFlow.collectAsStateWithLifecycle(emptyList())

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedLocalPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showCachePlaylist by remember { mutableStateOf(false) }

    BackHandler(enabled = showCachePlaylist) {
        showCachePlaylist = false
    }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    if (showCreatePlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.createLocalPlaylist(name)
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedLocalPlaylist?.let { playlist ->
        LocalPlaylistDetailsBottomSheet(
            playlist = playlist,
            viewModel = viewModel,
            onDismiss = { selectedLocalPlaylist = null }
        )
    }

    if (showCachePlaylist) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showCachePlaylist = false }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to playlists",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "Cache",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                StreamCacheTabContent(context)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
        // My Playlists Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "My Playlists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = { showCreatePlaylistDialog = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create Playlist",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Cache is a virtual playlist next to user-created playlists. It opens the
        // existing cache-backed list without being a separate category in the Library rail.
        if (showAsList) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item(key = "spatialflow-cache-playlist") {
                    ListItem(
                        headlineContent = {
                            Text("Cache", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text("Cached songs with artwork and lyrics", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { showCachePlaylist = true }
                    )
                }
                if (localPlaylists.isEmpty()) {
                    item(key = "create-playlist-prompt") {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(72.dp)
                                .clickable { showCreatePlaylistDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "+ Create a playlist to get started",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else {
                    items(localPlaylists, key = { it.id }) { playlist ->
                        ListItem(
                            headlineContent = {
                                Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = { Text("Playlist", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { selectedLocalPlaylist = playlist }
                        )
                    }
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item(key = "spatialflow-cache-playlist") {
                    Column(
                        modifier = Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenCachePlaylist)
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Cache",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Cached songs",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (localPlaylists.isEmpty()) {
                    item(key = "create-playlist-prompt") {
                        Column(
                            modifier = Modifier
                                .width(144.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showCreatePlaylistDialog = true }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Create Playlist",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Create playlist",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    items(localPlaylists) { playlist ->
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedLocalPlaylist = playlist }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistPlay,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                playlist.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "YT Music Playlists",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlinePlaylists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No online playlists found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (showAsList) 1 else 2),
                contentPadding = PaddingValues(bottom = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(onlinePlaylists) { playlist ->
                    OnlinePlaylistCard(playlist, asList = showAsList, onClick = {
                        exploreViewModel?.cameFromLibrary = true
                        exploreViewModel?.loadPlaylist(playlist.playlistId)
                        navigateToExplore(activity)
                    })
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalPlaylistDetailsBottomSheet(
    playlist: PlaylistEntity,
    viewModel: PlayerSharedViewModel,
    onDismiss: () -> Unit
) {
    val songsEntity by viewModel.getSongsForLocalPlaylist(playlist.id).collectAsStateWithLifecycle(emptyList())
    val songs = remember(songsEntity) { songsEntity.map { it.toSongItem() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${songs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Delete entire playlist button
                IconButton(
                    onClick = {
                        viewModel.deleteLocalPlaylist(playlist.id)
                        onDismiss()
                    }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Playlist",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons (Play & Shuffle)
            if (songs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.setSongList(songs)
                            if (songs.isNotEmpty()) {
                                viewModel.playSong(songs.first())
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play All")
                    }

                    OutlinedButton(
                        onClick = {
                            val shuffled = songs.shuffled()
                            viewModel.setSongList(shuffled)
                            if (shuffled.isNotEmpty()) {
                                viewModel.playSong(shuffled.first())
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shuffle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Songs List
            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "This playlist is empty",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(songs) { song ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSongList(songs)
                                        viewModel.playSong(song)
                                        onDismiss()
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mini Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (song.thumbnailUrl?.isNotEmpty() == true) {
                                        androidx.compose.foundation.Image(
                                            painter = coil.compose.rememberAsyncImagePainter(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(song.thumbnailUrl)
                                                    .crossfade(true)
                                                    .build()
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Delete single song from playlist button
                                IconButton(
                                    onClick = {
                                        viewModel.removeSongFromLocalPlaylist(playlist.id, song.videoId ?: song.id.toString())
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove song",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun OnlinePlaylistCard(
    playlist: OnlinePlaylist,
    asList: Boolean = false,
    onClick: () -> Unit
) {
    if (asList) {
        ListItem(
            headlineContent = {
                Text(playlist.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(playlist.songCount ?: "Playlist", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            },
            leadingContent = {
                if (!playlist.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(playlist.thumbnailUrl).crossfade(false).build(),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        )
        return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (playlist.thumbnailUrl?.isNotEmpty() == true) {
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(playlist.thumbnailUrl)
                                .crossfade(true)
                                .build()
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    playlist.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    playlist.songCount ?: "Playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PodcastsTabContent(nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlinePodcasts by accountViewModel?.podcasts?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Your Subscribed Podcasts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlinePodcasts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Podcasts,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .alpha(0.6f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No podcasts found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(onlinePodcasts) { podcast ->
                    OnlinePlaylistCard(podcast, onClick = {
                        exploreViewModel?.cameFromLibrary = true
                        exploreViewModel?.loadPlaylist(podcast.playlistId)
                        navigateToExplore(activity)
                    })
                }
            }
        }
    }
}

@Composable
private fun SongsTabContent(nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection, viewModel: PlayerSharedViewModel) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }

    val onlineSongs by accountViewModel?.songs?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Liked Songs (YT Music)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlineSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No online songs found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(onlineSongs) { song ->
                    ListItem(
                        headlineContent = { Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            if (song.thumbnailUrl?.isNotEmpty() == true) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(song.thumbnailUrl)
                                            .crossfade(true)
                                            .build()
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(48.dp))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val list = onlineSongs.map { s ->
                                    SongItem.createOnlineSong(
                                        s.videoId,
                                        s.title,
                                        s.artist,
                                        "",
                                        s.durationMs,
                                        s.thumbnailUrl,
                                        s.artistId
                                    )
                                }
                                viewModel.setSongList(list)
                                val idx = list.indexOfFirst { it.videoId == song.videoId }
                                if (idx != -1) {
                                    viewModel.playSongAtIndex(idx)
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsTabContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    showAsList: Boolean = false
) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlineAlbums by accountViewModel?.albums?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "YT Music Albums",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlineAlbums.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No online albums found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (showAsList) 1 else 2),
                contentPadding = PaddingValues(bottom = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(onlineAlbums) { album ->
                    OnlineAlbumCard(album, asList = showAsList, onClick = {
                        exploreViewModel?.cameFromLibrary = true
                        exploreViewModel?.loadAlbum(album.browseId)
                        navigateToExplore(activity)
                    })
                }
            }
        }
    }
}

@Composable
private fun OnlineAlbumCard(
    album: OnlineAlbum,
    asList: Boolean = false,
    onClick: () -> Unit
) {
    if (asList) {
        ListItem(
            headlineContent = { Text(album.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(album.artists.firstOrNull()?.name ?: "Unknown Artist", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) },
            leadingContent = {
                if (!album.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(album.thumbnailUrl).crossfade(false).build(),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Album, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        )
        return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (album.thumbnailUrl?.isNotEmpty() == true) {
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(album.thumbnailUrl)
                                .crossfade(true)
                                .build()
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Album,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    album.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    album.artists.firstOrNull()?.name ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    showAsList: Boolean = false
) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlineArtists by accountViewModel?.artists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Followed Artists (YT Music)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlineArtists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No online artists found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (showAsList) 1 else 2),
                contentPadding = PaddingValues(bottom = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(onlineArtists) { artist ->
                    OnlineArtistCard(artist, asList = showAsList, onClick = {
                        exploreViewModel?.cameFromLibrary = true
                        exploreViewModel?.loadArtist(artist.browseId, artist.thumbnailUrl)
                        navigateToExplore(activity)
                    })
                }
            }
        }
    }
}

@Composable
private fun OnlineArtistCard(
    artist: OnlineArtist,
    asList: Boolean = false,
    onClick: () -> Unit
) {
    if (asList) {
        ListItem(
            headlineContent = { Text(artist.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text("Artist", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) },
            leadingContent = {
                if (!artist.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(artist.thumbnailUrl).crossfade(false).build(),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (artist.thumbnailUrl?.isNotEmpty() == true) {
                androidx.compose.foundation.Image(
                    painter = coil.compose.rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artist.thumbnailUrl)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            artist.title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Artist",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecapTabContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    viewModel: PlayerSharedViewModel,
    onNavigateToExplore: () -> Unit
) {
    val context = LocalContext.current
    val recap by viewModel.listeningRecapFlow.collectAsStateWithLifecycle(null)

    if (recap == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "No Stats",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = "Your Flow is warming up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Start playing your favorite tracks, and your listening flow highlights will appear here!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    } else {
        val data = recap!!

        val heroSong = remember(data.topSongs) { data.topSongs.firstOrNull() }
        var topSongColor by remember(heroSong?.thumbnailUrl) { mutableStateOf<Color?>(null) }
        LaunchedEffect(heroSong?.thumbnailUrl) {
            val url = heroSong?.thumbnailUrl
            if (!url.isNullOrEmpty()) {
                withContext(Dispatchers.IO) {
                    try {
                        val loader = context.imageLoader
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .allowHardware(false)
                            .build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) {
                            val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                val palette = Palette.from(bitmap).generate()
                                val vibrantColor = palette.getVibrantColor(0)
                                val dominantColor = palette.getDominantColor(0)
                                val darkVibrantColor = palette.getDarkVibrantColor(0)
                                val mutedColor = palette.getMutedColor(0)
                                val lightVibrantColor = palette.getLightVibrantColor(0)
                                
                                val extractedInt = if (vibrantColor != 0) vibrantColor
                                                   else if (dominantColor != 0) dominantColor
                                                   else if (darkVibrantColor != 0) darkVibrantColor
                                                   else if (mutedColor != 0) mutedColor
                                                   else if (lightVibrantColor != 0) lightVibrantColor
                                                   else 0
                                if (extractedInt != 0) {
                                    topSongColor = Color(extractedInt)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RecapColor", "Failed to extract color: ${e.message}")
                    }
                }
            }
        }

        LaunchedEffect(data.topArtists) {
            data.topArtists.forEach { item ->
                viewModel.resolveArtistProfileImage(item.artist)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            // Sleek Gradient Banner
            item {
                val brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.background
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brush)
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "YOUR FLOW HIGHLIGHTS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Your Personal Listening Recap",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "A dynamic reflection of your musical journey on SpatialFlow.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Glassmorphic Summary Metrics
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Total Listening Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headset,
                                contentDescription = "Minutes Played",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${data.totalMinutes} min",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Total listen time",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Peak Habit Mood Card
                    val moodIconRes = when (data.peakMood) {
                        "Morning Spark" -> R.drawable.ic_morning
                        "Afternoon Groove" -> R.drawable.ic_afternoon
                        "Evening Harmony" -> R.drawable.ic_evening
                        else -> R.drawable.ic_evening
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = moodIconRes),
                                contentDescription = "Peak Vibe",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = data.peakMood,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = data.peakMoodDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Top Songs Ranked Shelf
            item {
                Text(
                    text = "Top Played Songs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 12.dp)
                )
            }

            // Hero #1 Song Card
            if (data.topSongs.isNotEmpty()) {
                val heroSong = data.topSongs.first()
                item {
                    val cardBgColor = topSongColor ?: MaterialTheme.colorScheme.primaryContainer
                    val isDark = topSongColor?.let { 
                        (0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue) < 0.5f 
                    } ?: true

                    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
                    val animatedCardBgColor by animateColorAsState(
                        targetValue = cardBgColor,
                        animationSpec = effectsSpec,
                        label = "TopSongCardBgAnimation"
                    )

                    val titleColor = if (topSongColor != null) {
                        if (isDark) Color.White else Color(0xFF1C1B1F)
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }

                    val artistColor = if (topSongColor != null) {
                        if (isDark) Color.White.copy(alpha = 0.75f) else Color(0xFF1C1B1F).copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    }

                    val playedCountColor = if (topSongColor != null) {
                        if (isDark) Color.White else Color(0xFF1C1B1F)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }

                    val badgeBgColor = if (topSongColor != null) {
                        if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }

                    val badgeTextColor = if (topSongColor != null) {
                        if (isDark) Color.White else Color.Black
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clickable {
                                val songItem = if (heroSong.songId.toLongOrNull() != null && !heroSong.thumbnailUrl.isNullOrEmpty() && !heroSong.thumbnailUrl.startsWith("http")) {
                                    SongItem(
                                        heroSong.songId.toLong(),
                                        heroSong.title,
                                        heroSong.artist,
                                        -1L,
                                        heroSong.thumbnailUrl,
                                        0L,
                                        System.currentTimeMillis()
                                    )
                                } else {
                                    SongItem.createOnlineSong(
                                        videoId = heroSong.songId,
                                        title = heroSong.title,
                                        artist = heroSong.artist,
                                        streamUrl = null,
                                        durationMs = 0L,
                                        thumbnailUrl = heroSong.thumbnailUrl
                                    )
                                }
                                viewModel.playSong(songItem)
                            },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = animatedCardBgColor
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.size(110.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                if (!heroSong.thumbnailUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = heroSong.thumbnailUrl,
                                        contentDescription = "Cover Art",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = "Placeholder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = badgeBgColor,
                                    modifier = Modifier.wrapContentSize()
                                ) {
                                    Text(
                                        text = "YOUR #1 TRACK",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeTextColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = heroSong.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = titleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = heroSong.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = artistColor
                                )
                                Text(
                                    text = "Played ${heroSong.count} times",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = playedCountColor
                                )
                            }
                        }
                    }
                }
            }

            // Remaining Top Songs (Ranked List)
            if (data.topSongs.size > 1) {
                items(data.topSongs.drop(1)) { song ->
                    ListItem(
                        headlineContent = { Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!song.thumbnailUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = song.thumbnailUrl,
                                        contentDescription = "Cover",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.MusicNote, null)
                                }
                            }
                        },
                        trailingContent = {
                            Text(
                                text = "${song.count} plays",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clickable {
                                val songItem = if (song.songId.toLongOrNull() != null && !song.thumbnailUrl.isNullOrEmpty() && !song.thumbnailUrl.startsWith("http")) {
                                    SongItem(
                                        song.songId.toLong(),
                                        song.title,
                                        song.artist,
                                        -1L,
                                        song.thumbnailUrl,
                                        0L,
                                        System.currentTimeMillis()
                                    )
                                } else {
                                    SongItem.createOnlineSong(
                                        videoId = song.songId,
                                        title = song.title,
                                        artist = song.artist,
                                        streamUrl = null,
                                        durationMs = 0L,
                                        thumbnailUrl = song.thumbnailUrl
                                    )
                                }
                                viewModel.playSong(songItem)
                            }
                    )
                }
            }

            // Top Artists Shelf
            if (data.topArtists.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Top Artists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp)
                    )
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(data.topArtists) { item ->
                            val imageUrl = viewModel.artistProfileMap[item.artist]
                            Column(
                                modifier = Modifier
                                    .width(96.dp)
                                    .clickable {
                                        onNavigateToExplore()
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!imageUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = imageUrl,
                                            contentDescription = "Artist picture",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, null, modifier = Modifier.size(36.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "${item.count} songs played",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isSpatialFlowDownloadedSong(song: SongItem): Boolean =
    isSpatialFlowDownloadedLocation(song.mediaStoreRelativePath, song.path ?: song.data)

private fun isSpatialFlowDownloadedLocation(relativePathValue: String?, rawPath: String?): Boolean {
    val relativePath = relativePathValue.orEmpty().trim('/').lowercase(java.util.Locale.ROOT)
    if (
        relativePath == "music/spatialflow" || relativePath.startsWith("music/spatialflow/") ||
        relativePath == "download/spatialflow" || relativePath.startsWith("download/spatialflow/") ||
        relativePath == "downloads/spatialflow" || relativePath.startsWith("downloads/spatialflow/")
    ) return true

    val path = rawPath.orEmpty().replace('\\', '/').lowercase(java.util.Locale.ROOT)
    return path.contains("/music/spatialflow/") ||
        path.contains("/download/spatialflow/") ||
        path.contains("/downloads/spatialflow/")
}

// Background scanner helper
internal suspend fun scanLocalFiles(context: Context, viewModel: PlayerSharedViewModel) {
    withContext(Dispatchers.IO) {
        val songs = ArrayList<SongItem>()
        try {
            val proj = mutableListOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.IS_MUSIC,
                android.provider.MediaStore.Audio.Media.ALBUM_ID,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.DATE_ADDED
            ).apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    add(android.provider.MediaStore.Audio.Media.RELATIVE_PATH)
                }
            }.toTypedArray()
            val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val hiddenFolders = (prefs.getString("hidden_folders", "") ?: "").split("||").filter { it.isNotEmpty() }
            val ignoreShort = prefs.getBoolean("ignore_short_audio", false)
            val minDurationMs = if (ignoreShort) prefs.getFloat("ignore_short_audio_duration", 30f).toLong() * 1000L else 0L

            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                proj,
                null,
                null,
                "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                val displayNameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                val isMusicCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.IS_MUSIC)
                val albumCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)
                val albumNameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                val relativePathCol = c.getColumnIndex("relative_path")
                val durCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                val dateCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val path = c.getString(dataCol)
                    val relativePath = if (relativePathCol >= 0) c.getString(relativePathCol) else null
                    val isDownloadedFile = isSpatialFlowDownloadedLocation(relativePath, path)
                    if (!isDownloadedFile && c.getInt(isMusicCol) == 0) continue
                    if (!isDownloadedFile && path != null && hiddenFolders.any { path.startsWith(it) }) continue

                    val duration = c.getLong(durCol)
                    if (!isDownloadedFile && ignoreShort && duration < minDurationMs) continue

                    // Some source containers cannot store tags. Recover basic display metadata
                    // from the downloaded filename instead of returning blank/unknown tracks.
                    val displayName = c.getString(displayNameCol).orEmpty().ifBlank {
                        path?.substringAfterLast('/') ?: ""
                    }
                    val baseName = displayName.substringBeforeLast('.', displayName)
                    val nameSeparator = baseName.lastIndexOf(" - ")
                    val fallbackTitle = if (nameSeparator > 0) baseName.substring(0, nameSeparator).trim() else baseName
                    val fallbackArtist = if (nameSeparator > 0) baseName.substring(nameSeparator + 3).trim() else ""
                    val storedTitle = c.getString(titleCol).orEmpty()
                    val storedArtist = c.getString(artistCol).orEmpty()
                    val title = if (
                        isDownloadedFile && (
                            storedTitle.isBlank() ||
                                storedTitle.equals("<unknown>", ignoreCase = true) ||
                                storedTitle.equals(baseName, ignoreCase = true)
                        )
                    ) fallbackTitle else storedTitle.ifBlank { fallbackTitle }
                    val artist = if (
                        isDownloadedFile &&
                            (storedArtist.isBlank() ||
                                storedArtist.equals("<unknown>", ignoreCase = true) ||
                                storedArtist.equals("unknown artist", ignoreCase = true)) &&
                            fallbackArtist.isNotBlank()
                    ) fallbackArtist else storedArtist.ifBlank { "Unknown Artist" }

                    songs.add(
                        SongItem(
                            c.getLong(idCol),
                            title,
                            artist,
                            c.getLong(albumCol),
                            c.getString(dataCol),
                            duration,
                            c.getLong(dateCol)
                        ).apply {
                            mediaStoreRelativePath = relativePath
                            albumName = c.getString(albumNameCol)?.takeIf {
                                it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
                            }
                        }
                    )
                }
            }
        } catch (_: Exception) {}
        withContext(Dispatchers.Main) {
            viewModel.setLocalSongs(songs)
        }
    }
}

private fun getActivityFromContext(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun navigateToExplore(activity: Activity?) {
    (activity as? MainActivity)?.showArtistPage(null, null)
}


private data class CachedSongEntry(
    val songId: String,
    val title: String,
    val artist: String,
    val bytes: Long
)

private data class CacheTabSnapshot(
    val entries: List<CachedSongEntry>
)

private suspend fun readCacheTabSnapshot(
    context: Context,
    lyricsCache: LyricsCacheManager
): CacheTabSnapshot = withContext(Dispatchers.IO) {
    val summary = try {
        AudioPlaybackService.getMediaCacheSnapshot(context)
    } catch (_: Exception) {
        0L to emptyList<Pair<String, Long>>()
    }
    val metadataByKey = summary.second.mapNotNull { (key, _) ->
        AudioPlaybackService.getCachedMediaMetadata(context, key)?.let { key to it }
    }.toMap()
    val metadataBySong = LinkedHashMap<String, Pair<com.codetrio.spatialflow.service.CachedMediaMetadata, Long>>()
    val metadataRepair = MetadataRepair()

    summary.second.forEach { (key, bytes) ->
        val metadata = metadataByKey[key] ?: return@forEach
        if (metadata.songId.isBlank() || metadata.title.isBlank()) return@forEach
        val artwork = AudioPlaybackService.getCachedMediaArtworkFile(context, metadata.songId)
        if (!artwork.isFile || artwork.length() <= 0L) return@forEach

        val track = metadataRepair.repair(
            metadata.title,
            metadata.artist,
            null,
            metadata.durationMs,
            null,
            null
        )
        if (!lyricsCache.hasCachedLyrics(track)) return@forEach

        val existing = metadataBySong[metadata.songId]
        metadataBySong[metadata.songId] = if (existing == null) {
            metadata to bytes
        } else {
            // Multiple stream URLs can refer to the same track; report their combined cached bytes.
            existing.first to (existing.second + bytes)
        }
    }

    val entries = metadataBySong.values.map { (metadata, bytes) ->
        CachedSongEntry(
            songId = metadata.songId,
            title = metadata.title,
            artist = metadata.artist.ifBlank { "Unknown artist" },
            bytes = bytes
        )
    }.sortedByDescending { it.bytes }

    CacheTabSnapshot(entries = entries)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun StreamCacheTabContent(context: Context) {
    var cacheEntries by remember { mutableStateOf(emptyList<CachedSongEntry>()) }
    val lyricsCache = remember(context) { LyricsCacheManager(context.applicationContext) }

    LaunchedEffect(context, lyricsCache) {
        cacheEntries = readCacheTabSnapshot(context, lyricsCache).entries
        while (true) {
            delay(3000L)
            cacheEntries = readCacheTabSnapshot(context, lyricsCache).entries
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Cached songs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Only tracks with cached audio, cover art, and lyrics are listed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (cacheEntries.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("No cached songs yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A track appears after its audio data, cover art, and lyrics have all been cached on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(cacheEntries, key = { _, item -> item.songId }) { _, item ->
                val artwork = AudioPlaybackService.getCachedMediaArtworkFile(context, item.songId)
                ListItem(
                    leadingContent = {
                        AsyncImage(
                            model = artwork,
                            contentDescription = item.title + " cover art",
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    },
                    headlineContent = {
                        Text(
                            item.title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Text(
                            android.text.format.Formatter.formatFileSize(context, item.bytes) + " cached",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }
        }
    }
}
