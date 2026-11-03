package com.dsann.music.ui.explore

import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicListItem
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.VpnKey
import com.dsann.music.ui.theme.AlertDialog
import com.dsann.music.ui.theme.Button
import com.dsann.music.ui.theme.ButtonDefaults
import com.dsann.music.ui.theme.Card
import com.dsann.music.ui.theme.CardDefaults
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.ListItem
import com.dsann.music.ui.theme.ListItemDefaults
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Scaffold
import com.dsann.music.ui.theme.Switch
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import com.dsann.music.ui.theme.TopAppBar
import com.dsann.music.ui.theme.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dsann.music.data.innertube.AccountManager
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.UserProfile
import com.dsann.music.viewmodel.AccountViewModel

/**
 * Dedicated account portal presenting library details and core auth instrumentation 
 */

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit = { _, _, _ -> },
    onNavigateToSignIn: () -> Unit = {}
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (AccountManager.isLoggedIn(context)) {
            viewModel.refreshAll()
        }
    }
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Account Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    DsmusicIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(DsmusicTheme.colorScheme.surface)) {
            AccountSettingsSection(
                viewModel = viewModel,
                userProfile = userProfile,
                history = history,
                onSongClick = onSongClick,
                onClose = onBack,
                onNavigateToSignIn = onNavigateToSignIn
            )
        }
    }
}


@Composable
fun AccountSettingsSection(
    viewModel: AccountViewModel,
    userProfile: UserProfile?,
    history: List<OnlineSong> = emptyList(),
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit = { _, _, _ -> },
    onClose: () -> Unit,
    onNavigateToSignIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val isLoggedIn = remember(userProfile) { AccountManager.isLoggedIn(context) }
    val accountName = userProfile?.name ?: AccountManager.getAccountName(context)
    val token = remember { AccountManager.getAuthCookie(context) ?: "No persistent credentials detected" }
    
    var syncEnabled by remember { mutableStateOf(AccountManager.isSyncEnabled(context)) }
    var showTokenAlert by remember { mutableStateOf(false) }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(DsmusicTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoggedIn && !userProfile?.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = rememberExploreImageRequest(userProfile!!.avatarUrl, 96),
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PersonOutline,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = accountName,
                    style = DsmusicTheme.typography.headlineMedium.copy(letterSpacing = (-0.5).sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = DsmusicTheme.colorScheme.onSurface
                )
                if (isLoggedIn) {
                    Text(
                        text = userProfile?.email ?: "YouTube Music Connected",
                        style = DsmusicTheme.typography.labelLarge,
                        color = DsmusicTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Recent Listening History", 
                        style = DsmusicTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold, 
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        this.itemsIndexed(history) { index, song ->
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSongClick(song, history, index) }
                            ) {
                                DsmusicCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DsmusicTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (!song.thumbnailUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = rememberExploreImageRequest(song.thumbnailUrl, 128),
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(32.dp), tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = song.title,
                                    style = DsmusicTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = DsmusicTheme.typography.bodySmall,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                DsmusicListItem(
                    onClick = {
                        val next = !syncEnabled
                        syncEnabled = next
                        viewModel.toggleSync(next)
                    },
                    leadingContent = { Icon(Icons.Rounded.CloudSync, null) },
                    trailingContent = {
                        DsmusicSwitchControl(checked = syncEnabled, onCheckedChange = null)
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2).shape,
                    colors = ListItemDefaults.colors(containerColor = DsmusicTheme.colorScheme.surfaceContainer),
                    content = {
                        Column {
                            Text("Sync Playlists", fontWeight = FontWeight.Medium, style = DsmusicTheme.typography.titleMedium)
                            Text("Auto-update library states with remote servers", style = DsmusicTheme.typography.bodyMedium, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )

                DsmusicListItem(
                    onClick = { showTokenAlert = true },
                    leadingContent = { Icon(Icons.Rounded.VpnKey, null) },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2).shape,
                    colors = ListItemDefaults.colors(containerColor = DsmusicTheme.colorScheme.surfaceContainer),
                    content = {
                        Column {
                            Text("Diagnostics Tool", fontWeight = FontWeight.Medium, style = DsmusicTheme.typography.titleMedium)
                            Text("Inspect background security context", style = DsmusicTheme.typography.bodyMedium, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isLoggedIn) {
                DsmusicButton(
                    onClick = { viewModel.logout(); onClose() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DsmusicTheme.colorScheme.errorContainer,
                        contentColor = DsmusicTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Disconnect Account", fontWeight = FontWeight.Bold)
                }
            } else {
                DsmusicButton(
                    onClick = onNavigateToSignIn,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DsmusicTheme.colorScheme.primary,
                        contentColor = DsmusicTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Login, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Sign In to YouTube Music", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showTokenAlert) {
        AlertDialog(
            onDismissRequest = { showTokenAlert = false },
            title = { Text("Authentication Signature") },
            text = { Text(token, fontSize = 11.sp, style = DsmusicTheme.typography.labelSmall) },
            confirmButton = {
                DsmusicTextButton(onClick = { showTokenAlert = false }) { Text("Done") }
            }
        )
    }
}

