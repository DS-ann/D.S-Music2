package com.codetrio.spatialflow.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A separate, local-only music destination.
 *
 * Every playback action builds the queue from MediaStore results, never from the online
 * Explore/library collections. Once a local track is started, next/previous stay in this
 * device-only queue until the user starts something elsewhere in the app.
 */
@Composable
fun LocalMusicScreen(viewModel: PlayerSharedViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val localSongs by viewModel.localSongs.collectAsStateWithLifecycle(emptyList())
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(SortOrder.A_Z) }
    var isScanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (localSongs.isEmpty()) {
            isScanning = true
            try {
                scanLocalFiles(context, viewModel)
            } finally {
                isScanning = false
            }
        }
    }

    // A large MediaStore library must not be filtered/sorted on the main thread.
    val visibleSongs by produceState(
        initialValue = emptyList<SongItem>(),
        localSongs,
        searchQuery,
        sortOrder
    ) {
        value = withContext(Dispatchers.Default) {
            val filtered = localSongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    (it.path?.contains(searchQuery, ignoreCase = true) == true)
            }
            when (sortOrder) {
                SortOrder.A_Z -> filtered.sortedBy { it.title.lowercase() }
                SortOrder.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
                SortOrder.ARTIST -> filtered.sortedWith(
                    compareBy({ it.artist.lowercase() }, { it.title.lowercase() })
                )
                SortOrder.DURATION -> filtered.sortedByDescending { it.duration }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Local music",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${localSongs.size} songs on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                enabled = !isScanning,
                onClick = {
                    scope.launch {
                        isScanning = true
                        try {
                            scanLocalFiles(context, viewModel)
                        } finally {
                            isScanning = false
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan local music")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search local music") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 8.dp),
            shape = CircleShape,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = visibleSongs.isNotEmpty(),
                onClick = {
                    // The queue is replaced by local MediaStore entries only.
                    viewModel.setSongList(visibleSongs)
                    viewModel.playSongAtIndex(0)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 2.dp))
                Text("Play all")
            }
            OutlinedButton(
                enabled = visibleSongs.isNotEmpty(),
                onClick = {
                    val shuffledLocalSongs = visibleSongs.shuffled()
                    viewModel.setSongList(shuffledLocalSongs)
                    viewModel.playSongAtIndex(0)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 2.dp))
                Text("Shuffle")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortOrder.entries.forEach { order ->
                FilterChip(
                    selected = sortOrder == order,
                    onClick = { sortOrder = order },
                    label = {
                        Text(
                            text = order.name.replace("_", " "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (visibleSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            isScanning -> "Scanning this device…"
                            searchQuery.isNotBlank() -> "No local songs match that search"
                            else -> "No local music found"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isScanning && localSongs.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isScanning = true
                                    try {
                                        scanLocalFiles(context, viewModel)
                                    } finally {
                                        isScanning = false
                                    }
                                }
                            }
                        ) {
                            Text("Scan device")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(visibleSongs, key = { _, song -> song.id }) { index, song ->
                    val isCurrentLocalSong =
                        currentSong?.videoId.isNullOrEmpty() && currentSong?.id == song.id
                    SongListItem(
                        song = song,
                        isPlaying = isCurrentLocalSong,
                        isFavorite = favoriteIds.contains(song.id),
                        localIndex = index,
                        localCount = visibleSongs.size,
                        onClick = {
                            // Replace any online queue with this filtered local-only queue.
                            viewModel.setSongList(visibleSongs)
                            viewModel.playSongAtIndex(index)
                        },
                        onLongClick = { }
                    )
                }
            }
        }
    }
}
