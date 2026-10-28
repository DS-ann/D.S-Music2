@file:Suppress("DEPRECATION")

package com.codetrio.spatialflow.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import java.io.File
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.Glide
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.service.AudioPlaybackService
import com.codetrio.spatialflow.service.CurrentAudioFormatDetails
import com.codetrio.spatialflow.ui.player.OnlineStreamMetadata
import com.codetrio.spatialflow.ui.player.OnlineStreamMetadataReader
import com.codetrio.spatialflow.ui.theme.GoogleSansFlex
import com.codetrio.spatialflow.ui.theme.SpatialFlowTheme
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class SongActionsBottomSheet : BottomSheetDialogFragment() {

    private var song: SongItem? = null
    private var actionListener: ActionListener? = null

    interface ActionListener {
        fun onPlayNext(song: SongItem)
        fun onAddToQueue(song: SongItem)
        fun onDelete(song: SongItem)
        fun onEdit(song: SongItem)
        fun onPlay(song: SongItem)
        fun onFavorite(song: SongItem, isFav: Boolean)
        fun onShare(song: SongItem)
    }

    companion object {
        @JvmStatic
        fun newInstance(song: SongItem): SongActionsBottomSheet {
            val fragment = SongActionsBottomSheet()
            val args = Bundle()
            args.putSerializable("song", song)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var viewModel: PlayerSharedViewModel

    fun setActionListener(listener: ActionListener) {
        this.actionListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        song = arguments?.getSerializable("song") as? SongItem
        viewModel = ViewModelProvider(requireActivity())[PlayerSharedViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SpatialFlowTheme {
                    BottomSheetScaffoldContent(
                        song = song,
                        actionListener = actionListener,
                        viewModel = viewModel,
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }
}

enum class BottomSheetTab { OPTIONS, INFO }

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomSheetScaffoldContent(
    song: SongItem?,
    actionListener: SongActionsBottomSheet.ActionListener?,
    viewModel: PlayerSharedViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(BottomSheetTab.OPTIONS) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val favoritesManager = remember { com.codetrio.spatialflow.util.FavoritesManager(context) }
    var isFavorite by remember { mutableStateOf(song?.let { favoritesManager.isFavorite(it.id) } ?: false) }
    var albumArtBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val localPlaylists by viewModel.localPlaylistsFlow.collectAsStateWithLifecycle(emptyList())

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(song) {
        if (song != null) {
            withContext(Dispatchers.IO) {
                val embedded = song.getEmbeddedPicture(context)
                val model = embedded ?: song.getAlbumArtUri()
                if (model != null) {
                    try {
                        albumArtBitmap = Glide.with(context).asBitmap().load(model).override(256).submit().get()
                    } catch (_: Exception) { /* Fallback */ }
                }
            }
        }
    }

    if (showAddToPlaylistDialog) {
        LocalPlaylistPickerDialog(
            playlists = localPlaylists,
            onCreateNew = {
                showCreatePlaylistDialog = true
                showAddToPlaylistDialog = false
            },
            onPlaylistSelected = { playlist ->
                if (song != null) {
                    viewModel.addSongToLocalPlaylist(playlist.id, song)
                }
                showAddToPlaylistDialog = false
                onDismiss()
            },
            onDismiss = { showAddToPlaylistDialog = false }
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge, // Expressive corner radius
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp)
                .then(if (isLandscape) Modifier.padding(start = 88.dp, end = 20.dp) else Modifier.padding(horizontal = 20.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Expressive Handle
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isLandscape) {
                // PORTRAIT: Standard Vertical Layout
                HeaderSection(song, albumArtBitmap, onEdit = {
                    song?.let { actionListener?.onEdit(it) }
                    onDismiss()
                })

                Spacer(modifier = Modifier.height(28.dp))

                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val direction = if (targetState == BottomSheetTab.INFO) 1 else -1
                            (slideInHorizontally { direction * it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -direction * it } + fadeOut())
                        },
                        label = "TabTransition"
                    ) { tab ->
                        if (tab == BottomSheetTab.OPTIONS) {
                            OptionsContent(song, isFavorite, { isFavorite = it }, actionListener, onAddToPlaylist = { showAddToPlaylistDialog = true }, onDismiss)
                        } else {
                            InfoContent(song)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                ExpressiveTabSwitcher(selectedTab) { selectedTab = it }
            } else {
                // LANDSCAPE: Side-by-Side Split View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Pane (Fixed identity & Switcher)
                    Column(
                        modifier = Modifier.weight(0.4f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HeaderSection(
                            song = song,
                            bitmap = albumArtBitmap,
                            onEdit = {
                                song?.let { actionListener?.onEdit(it) }
                                onDismiss()
                            },
                            isLandscape = true
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        ExpressiveTabSwitcher(selectedTab) { selectedTab = it }
                    }

                    // Right Pane (Scrollable content)
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                val direction = if (targetState == BottomSheetTab.INFO) 1 else -1
                                (slideInHorizontally { direction * it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -direction * it } + fadeOut())
                            },
                            label = "TabTransition"
                        ) { tab ->
                            if (tab == BottomSheetTab.OPTIONS) {
                                OptionsContent(song, isFavorite, { isFavorite = it }, actionListener, onAddToPlaylist = { showAddToPlaylistDialog = true }, onDismiss)
                            } else {
                                InfoContent(song)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OptionsContent(
    song: SongItem?,
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    actionListener: SongActionsBottomSheet.ActionListener?,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Main Actions (Play, Fav, Share)
        ActionButtonRow1(song, isFavorite, onFavoriteChange, actionListener, onDismiss)
        
        // Secondary Actions (Queue, Next)
        ActionButtonRow2(song, actionListener, onDismiss)
        
        // Danger Zone / Playlist
        DestructiveActionRow(song, actionListener, onAddToPlaylist, onDismiss)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DestructiveActionRow(
    song: SongItem?,
    actionListener: SongActionsBottomSheet.ActionListener?,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    val deleteInteraction = remember { MutableInteractionSource() }
    val dPressed by deleteInteraction.collectIsPressedAsState()

    val addInteraction = remember { MutableInteractionSource() }
    val aPressed by addInteraction.collectIsPressedAsState()

    Row(modifier = Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Add to Playlist Button
        ToggleButton(
            checked = aPressed,
            onCheckedChange = { 
                onAddToPlaylist()
            },
            interactionSource = addInteraction,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Default.PlaylistAdd, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text("Add to Playlist", style = MaterialTheme.typography.labelLarge, fontFamily = GoogleSansFlex, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }

        // Delete Button
        ToggleButton(
            checked = dPressed,
            onCheckedChange = { 
                song?.let { actionListener?.onDelete(it) }
                onDismiss()
            },
            interactionSource = deleteInteraction,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text("Delete", style = MaterialTheme.typography.labelLarge, fontFamily = GoogleSansFlex, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
fun LocalPlaylistPickerDialog(
    playlists: List<com.codetrio.spatialflow.data.db.PlaylistEntity>,
    onCreateNew: () -> Unit,
    onPlaylistSelected: (com.codetrio.spatialflow.data.db.PlaylistEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add to Playlist",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                // Button to create new playlist
                Button(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Playlist")
                }

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No playlists yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(playlists) { playlist ->
                            Surface(
                                onClick = { onPlaylistSelected(playlist) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PlaylistPlay,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        playlist.title,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateLocalPlaylistDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "New Playlist",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
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
                        onConfirm(name)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HeaderSection(
    song: SongItem?,
    bitmap: android.graphics.Bitmap?,
    onEdit: () -> Unit,
    isLandscape: Boolean = false
) {
    if (!isLandscape) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), null, Modifier.size(84.dp), contentScale = ContentScale.Crop)
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.default_album_art),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    song?.title ?: "Unknown",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansFlex,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Filled.Edit, null)
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), null, Modifier.size(112.dp), contentScale = ContentScale.Crop)
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.default_album_art),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(112.dp)
                        )
                    }
                }
                
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(32.dp)
                        .offset(x = 6.dp, y = 6.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                song?.title ?: "Unknown",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                song?.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansFlex,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionButtonRow1(
    song: SongItem?,
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    actionListener: SongActionsBottomSheet.ActionListener?,
    onDismiss: () -> Unit
) {
    var favLocal by remember { mutableStateOf(isFavorite) }

    val playInteraction = remember { MutableInteractionSource() }
    val favInteraction = remember { MutableInteractionSource() }
    val shareInteraction = remember { MutableInteractionSource() }

    val playPressed by playInteraction.collectIsPressedAsState()
    val favPressed by favInteraction.collectIsPressedAsState()
    val sharePressed by shareInteraction.collectIsPressedAsState()

    val playWeight by animateFloatAsState(
        targetValue = if (playPressed) 1.5f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f), label = "playWeight"
    )
    val favWeight by animateFloatAsState(
        targetValue = if (favPressed) 1.5f else 1f, 
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f), label = "favWeight"
    )
    val shareWeight by animateFloatAsState(
        targetValue = if (sharePressed) 1.5f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f), label = "shareWeight"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ToggleButton(
            checked = playPressed,
            onCheckedChange = {
                song?.let { actionListener?.onPlay(it) }
                onDismiss()
            },
            interactionSource = playInteraction,
            modifier = Modifier
                .weight(playWeight)
                .fillMaxHeight()
                .zIndex(if (playPressed) 1f else 0f),
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = GoogleSansFlex)
            }
        }

        ToggleButton(
            checked = favLocal,
            onCheckedChange = {
                favLocal = it
                onFavoriteChange(it)
                song?.let { actionListener?.onFavorite(it, favLocal) }
            },
            interactionSource = favInteraction,
            modifier = Modifier
                .weight(favWeight)
                .fillMaxHeight()
                .zIndex(if (favPressed) 1f else 0f),
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = Color(0xFFE91E63)
            )
        ) {
            Icon(
                imageVector = if (favLocal) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = if (favLocal) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }

        ToggleButton(
            checked = sharePressed,
            onCheckedChange = { song?.let { actionListener?.onShare(it) } },
            interactionSource = shareInteraction,
            modifier = Modifier
                .weight(shareWeight)
                .fillMaxHeight()
                .zIndex(if (sharePressed) 1f else 0f),
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        ) {
            Icon(Icons.Filled.Share, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionButtonRow2(
    song: SongItem?,
    actionListener: SongActionsBottomSheet.ActionListener?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val queueInteraction = remember { MutableInteractionSource() }
        val queuePressed by queueInteraction.collectIsPressedAsState()
        val queueWeight by animateFloatAsState(
            targetValue = if (queuePressed) 1.4f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
        )
        ToggleButton(
            checked = queuePressed,
            onCheckedChange = {
                song?.let { actionListener?.onAddToQueue(it) }
                onDismiss()
            },
            interactionSource = queueInteraction,
            modifier = Modifier
                .weight(queueWeight)
                .height(72.dp)
                .zIndex(if (queuePressed) 1f else 0f),
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                checkedContainerColor = MaterialTheme.colorScheme.secondary,
                checkedContentColor = MaterialTheme.colorScheme.onSecondary
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Queue",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GoogleSansFlex
                )
            }
        }

        val nextInteraction = remember { MutableInteractionSource() }
        val nextPressed by nextInteraction.collectIsPressedAsState()
        val nextWeight by animateFloatAsState(
            targetValue = if (nextPressed) 1.4f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
        )
        ToggleButton(
            checked = nextPressed,
            onCheckedChange = {
                song?.let { actionListener?.onPlayNext(it) }
                onDismiss()
            },
            interactionSource = nextInteraction,
            modifier = Modifier
                .weight(nextWeight)
                .height(72.dp)
                .zIndex(if (nextPressed) 1f else 0f),
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                checkedContainerColor = MaterialTheme.colorScheme.tertiary,
                checkedContentColor = MaterialTheme.colorScheme.onTertiary
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Next",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GoogleSansFlex
                )
            }
        }

        val downloadInteraction = remember { MutableInteractionSource() }
        val downloadPressed by downloadInteraction.collectIsPressedAsState()
        val downloadWeight by animateFloatAsState(
            targetValue = if (downloadPressed) 1.4f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
        )
        val isOnline = song?.videoId != null

        ToggleButton(
            checked = downloadPressed,
            onCheckedChange = {
                if (isOnline) {
                    com.codetrio.spatialflow.util.SongDownloader.downloadSong(context, song)
                    onDismiss()
                }
            },
            enabled = isOnline,
            interactionSource = downloadInteraction,
            modifier = Modifier
                .weight(downloadWeight)
                .height(72.dp)
                .zIndex(if (downloadPressed) 1f else 0f),
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = if (isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isOnline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.Download else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isOnline) "Download" else "Offline",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GoogleSansFlex
                )
            }
        }
    }
}

@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun ExpressiveTabSwitcher(selectedTab: BottomSheetTab, onTabSelected: (BottomSheetTab) -> Unit) {
    val offset by animateDpAsState(
        targetValue = if (selectedTab == BottomSheetTab.OPTIONS) 0.dp else 160.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "TabOffset"
    )

    Box(
        modifier = Modifier
            .width(320.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(160.dp)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(BottomSheetTab.OPTIONS) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = null,
                        tint = if (selectedTab == BottomSheetTab.OPTIONS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OPTIONS",
                        color = if (selectedTab == BottomSheetTab.OPTIONS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFlex
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(BottomSheetTab.INFO) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = if (selectedTab == BottomSheetTab.INFO) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INFO",
                        color = if (selectedTab == BottomSheetTab.INFO) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFlex
                    )
                }
            }
        }
    }
}

private data class SongInfoDetails(
    val duration: String,
    val album: String,
    val artist: String,
    val size: String,
    val bitrate: String,
    val format: String,
    val sampleRate: String,
    val location: String
)

@Composable
fun InfoContent(song: SongItem?) {
    val context = LocalContext.current
    val mainActivity = remember(context) { findMainActivity(context) }
    val info by produceState(
        initialValue = SongInfoDetails(
            duration = "Reading metadata…",
            album = "Reading metadata…",
            artist = song?.artist ?: "Reading metadata…",
            size = "Calculating file size…",
            bitrate = "Reading audio format…",
            format = "Detecting format…",
            sampleRate = "Reading audio format…",
            location = song?.path?.takeIf { it.isNotBlank() } ?: "Online source"
        ),
        song,
        context,
        mainActivity?.audioService
    ) {
        val liveFormat = song?.let {
            mainActivity?.audioService?.getCurrentAudioFormatDetails(it.id.toString())
        }
        value = withContext(Dispatchers.IO) {
            val onlineMetadata = song?.videoId?.takeIf { it.isNotBlank() }?.let {
                OnlineStreamMetadataReader.read(it)
            }
            readSongInfoDetails(context, song, liveFormat, onlineMetadata)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoRow("Duration", info.duration, Icons.Filled.DateRange)
        InfoRow("Album", info.album, Icons.Filled.Face)
        InfoRow("Artist", info.artist, Icons.Filled.Person)
        InfoRow("Size", info.size, Icons.Filled.Download)
        InfoRow("Bitrate", info.bitrate, Icons.Filled.Info)
        InfoRow("Format", info.format, Icons.Filled.Info)
        InfoRow("Sample rate", info.sampleRate, Icons.Filled.Info)
        InfoRow(if (song?.videoId.isNullOrBlank()) "Location" else "Source", info.location, Icons.Filled.Menu)
    }
}

private fun findMainActivity(context: Context): MainActivity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is MainActivity) return current
        current = current.baseContext
    }
    return current as? MainActivity
}

private fun readSongInfoDetails(
    context: Context,
    song: SongItem?,
    liveFormat: CurrentAudioFormatDetails?,
    onlineStreamMetadata: OnlineStreamMetadata? = null
): SongInfoDetails {
    if (song == null) {
        return SongInfoDetails(
            duration = "No track selected",
            album = "No track selected",
            artist = "No track selected",
            size = "No track selected",
            bitrate = "No track selected",
            format = "No track selected",
            sampleRate = "No track selected",
            location = "No track selected"
        )
    }

    val rawPath = song.path?.trim().orEmpty()
    val uriFromPath = rawPath.takeIf {
        it.startsWith("content://", ignoreCase = true) ||
            it.startsWith("file://", ignoreCase = true)
    }?.let { Uri.parse(it) }
    val sourceUri = uriFromPath ?: song.contentUri
    val file = rawPath.takeIf {
        it.isNotBlank() &&
            !it.startsWith("http://", ignoreCase = true) &&
            !it.startsWith("https://", ignoreCase = true) &&
            !it.startsWith("content://", ignoreCase = true) &&
            !it.startsWith("file://", ignoreCase = true)
    }?.let { File(it) }?.takeIf { it.isFile }
    val remote = !song.videoId.isNullOrBlank() ||
        rawPath.startsWith("http://", ignoreCase = true) ||
        rawPath.startsWith("https://", ignoreCase = true) ||
        sourceUri.scheme == "http" || sourceUri.scheme == "https"
    val localUri = !remote && (sourceUri.scheme == "content" || sourceUri.scheme == "file")

    var durationMs = song.duration
    var albumTag: String? = null
    var artistTag: String? = null
    var bitrateBps: Long? = null
    var sampleRateHz: Long? = null
    var mimeType: String? = null

    val retriever = MediaMetadataRetriever()
    try {
        when {
            file != null -> retriever.setDataSource(file.absolutePath)
            localUri -> retriever.setDataSource(context, sourceUri)
        }
        albumTag = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            ?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }
        artistTag = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()?.takeIf { it > 0L } ?: durationMs
        bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toLongOrNull()?.takeIf { it > 0L }
        sampleRateHz = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            ?.toLongOrNull()?.takeIf { it > 0L }
        mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            ?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        // Older decoders and some MediaStore providers omit optional tags.
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
            // Preserve metadata already extracted.
        }
    }

    var sizeBytes = file?.length()?.takeIf { it > 0L } ?: 0L
    if (sizeBytes <= 0L && localUri) {
        try {
            context.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { descriptor ->
                if (descriptor.length > 0L) sizeBytes = descriptor.length
            }
        } catch (_: Exception) {
            // Use OpenableColumns as a secondary fallback.
        }
        if (sizeBytes <= 0L) {
            try {
                context.contentResolver.query(
                    sourceUri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index >= 0 && !cursor.isNull(index)) {
                            sizeBytes = cursor.getLong(index).takeIf { it > 0L } ?: 0L
                        }
                    }
                }
            } catch (_: Exception) {
                // Some content providers intentionally hide file size.
            }
        }
    }

    if (durationMs <= 0L) {
        durationMs = onlineStreamMetadata?.durationMs?.takeIf { it > 0L }
            ?: liveFormat?.durationMs?.takeIf { it > 0L }
            ?: durationMs
    }

    var cachedStreamBytes = 0L
    if (remote) {
        try {
            val snapshot = AudioPlaybackService.getMediaCacheSnapshot(context)
            cachedStreamBytes = snapshot.second.sumOf { entry ->
                val metadata = AudioPlaybackService.getCachedMediaMetadata(context, entry.first)
                if (metadata?.songId == song.id.toString()) entry.second else 0L
            }
        } catch (_: Exception) {
            // The current track information remains valid if cache data isn't available.
        }
    }

    val effectiveBitrate = bitrateBps
        ?: liveFormat?.bitrateBps?.toLong()?.takeIf { it > 0L }
        ?: onlineStreamMetadata?.bitrateBps?.toLong()?.takeIf { it > 0L }
    val effectiveSampleRate = sampleRateHz ?: liveFormat?.sampleRateHz?.toLong()?.takeIf { it > 0L }
    val reportedOnlineStreamBytes = onlineStreamMetadata?.contentLengthBytes?.takeIf { it > 0L }
    val durationText = if (durationMs > 0L) {
        val seconds = durationMs / 1000L
        String.format(java.util.Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
    } else {
        "Duration not supplied by source"
    }
    val album = albumTag ?: if (remote) {
        "Online track; album metadata not supplied"
    } else {
        "No album tag embedded in this file"
    }
    val artist = artistTag ?: song.artist.trim().takeIf {
        it.isNotEmpty() &&
            !it.equals("Unknown Artist", ignoreCase = true) &&
            !it.equals("<unknown>", ignoreCase = true)
    } ?: "Artist metadata not supplied"

    val estimatedRemoteBytes = if (remote && effectiveBitrate != null && durationMs > 0L) {
        (effectiveBitrate * durationMs / 8000L).takeIf { it > 0L }
    } else null
    val sizeText = when {
        sizeBytes > 0L -> Formatter.formatFileSize(context, sizeBytes)
        remote && reportedOnlineStreamBytes != null ->
            Formatter.formatFileSize(context, reportedOnlineStreamBytes) + " (reported audio stream length)"
        remote && cachedStreamBytes > 0L && estimatedRemoteBytes != null ->
            Formatter.formatFileSize(context, cachedStreamBytes) + " cached of about " +
                Formatter.formatFileSize(context, estimatedRemoteBytes) + " estimated stream size"
        remote && cachedStreamBytes > 0L ->
            Formatter.formatFileSize(context, cachedStreamBytes) + " of stream data cached so far"
        remote && estimatedRemoteBytes != null ->
            Formatter.formatFileSize(context, estimatedRemoteBytes) + " estimated from bitrate and duration"
        remote -> "Online stream; no fixed local file size"
        else -> "Size is not exposed by this media provider"
    }
    val bitrateText = when {
        effectiveBitrate != null ->
            String.format(java.util.Locale.US, "%.0f kbps", effectiveBitrate / 1000.0) +
                when {
                    !remote -> ""
                    liveFormat?.bitrateBps != null -> " (current stream)"
                    else -> " (selected source stream)"
                }
        sizeBytes > 0L && durationMs > 0L ->
            String.format(java.util.Locale.US, "%.0f kbps estimated from file size", sizeBytes * 8.0 / durationMs)
        remote -> "Selected dynamically by the stream"
        else -> "Bitrate tag not embedded; no reliable estimate"
    }
    val format = file?.extension?.takeIf { it.isNotBlank() }?.uppercase(java.util.Locale.US)
        ?: mimeType?.substringAfter('/', "")?.takeIf { it.isNotBlank() }?.uppercase(java.util.Locale.US)
        ?: onlineStreamMetadata?.mimeType?.substringAfter('/', "")?.takeIf { it.isNotBlank() }
            ?.uppercase(java.util.Locale.US)
        ?: if (remote) "Online audio stream" else "Audio format not tagged"
    val sampleRateText = effectiveSampleRate?.let {
        if (it % 1000L == 0L) (it / 1000L).toString() + " kHz"
        else String.format(java.util.Locale.US, "%.1f kHz", it / 1000.0)
    } ?: if (remote) {
        "Reported by decoder during playback"
    } else {
        "Sample rate tag not included"
    }
    val location = when {
        file != null -> file.absolutePath
        localUri -> sourceUri.toString()
        remote -> {
            val host = runCatching { Uri.parse(rawPath).host }.getOrNull()
            if (!host.isNullOrBlank()) "Online stream (" + host + ")"
            else "YouTube Music stream • " + song.videoId
        }
        rawPath.isNotBlank() -> rawPath
        else -> "MediaStore audio item " + song.id
    }

    return SongInfoDetails(
        duration = durationText,
        album = album,
        artist = artist,
        size = sizeText,
        bitrate = bitrateText,
        format = format,
        sampleRate = sampleRateText,
        location = location
    )
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GoogleSansFlex
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontFamily = GoogleSansFlex
            )
        }
    }
}

@SuppressLint("DefaultLocale")
fun SongItem.getFormattedDuration(): String {
    val totalSeconds = duration / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
