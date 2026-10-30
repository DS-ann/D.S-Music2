package com.dsann.music.ui.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.dsann.music.R
import com.dsann.music.model.SongItem
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.dsann.music.ui.theme.observeKey

@Composable
fun ArtworkPager(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem,
    songList: List<SongItem>,
    currentSongIndex: Int,
    context: Context,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true
) {
    val artworkShapeName by context
        .getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        .observeKey("player_artwork_shape", "Rounded")
    val playerLayout by context
        .getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        .observeKey("player_layout", "Standard")
    val artworkShape = remember(artworkShapeName, playerLayout) {
        when (playerLayout) {
            "Vinyl" -> CircleShape
            "Cassette" -> RoundedCornerShape(14.dp)
            "Cover" -> RoundedCornerShape(8.dp)
            "Minimal" -> RoundedCornerShape(18.dp)
            else -> DsmCoverShapes.of(artworkShapeName, 24.dp)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = currentSongIndex.coerceAtLeast(0)
    ) {
        songList.size.coerceAtLeast(1)
    }

    // Sync Pager Page with VM when active song changes externally
    LaunchedEffect(currentSongIndex) {
        if (currentSongIndex >= 0 && currentSongIndex < pagerState.pageCount && pagerState.currentPage != currentSongIndex) {
            pagerState.animateScrollToPage(currentSongIndex)
        }
    }

    // Sync VM when swiped in Pager (only when settling/finished scrolling to avoid race conditions)
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && currentSongIndex >= 0 && pagerState.currentPage != currentSongIndex) {
            viewModel.playSongAtIndex(pagerState.currentPage)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val song = if (page == currentSongIndex) currentSong else (songList.getOrNull(page) ?: currentSong)
            val rawUri = song.getAlbumArtUri()
            val videoId = song.videoId

            val artworkUrl = remember(rawUri, videoId) {
                if (rawUri != null && rawUri.toString().isNotEmpty()) {
                    SongItem.enhanceThumbnailUrl(rawUri.toString())
                } else if (!videoId.isNullOrEmpty()) {
                    "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                } else {
                    null
                }
            }
            val imageRequest = remember(context, artworkUrl) {
                artworkUrl?.let {
                    ImageRequest.Builder(context)
                        .data(it.toUri())
                        .crossfade(true)
                        .precision(coil.size.Precision.EXACT)
                        .build()
                }
            }

            var isError by remember(artworkUrl) { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val side = size.minDimension
                        when (playerLayout) {
                            "Vinyl" -> {
                                // Pressed-record grooves and a centre label.
                                val ringColor = androidx.compose.ui.graphics.Color.White
                                listOf(0.28f, 0.38f, 0.47f).forEach { fraction ->
                                    drawCircle(
                                        color = ringColor.copy(alpha = 0.16f),
                                        radius = side * fraction,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = (side * 0.004f).coerceAtLeast(1f)
                                        )
                                    )
                                }
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                                    radius = side * 0.105f
                                )
                                drawCircle(
                                    color = ringColor.copy(alpha = 0.55f),
                                    radius = side * 0.105f,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = (side * 0.006f).coerceAtLeast(1f)
                                    )
                                )
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                    radius = side * 0.012f
                                )
                            }
                            "Cassette" -> {
                                // Tape window, twin reels, and a shell outline.
                                val shell = androidx.compose.ui.graphics.Color.White
                                drawRoundRect(
                                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.36f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.045f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = (side * 0.018f).coerceAtLeast(1.5f)
                                    )
                                )
                                drawRoundRect(
                                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.34f),
                                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.16f),
                                    size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.19f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.025f)
                                )
                                listOf(0.34f, 0.66f).forEach { x ->
                                    val reelCenter = androidx.compose.ui.geometry.Offset(size.width * x, size.height * 0.68f)
                                    drawCircle(
                                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                                        radius = side * 0.115f,
                                        center = reelCenter
                                    )
                                    drawCircle(
                                        color = shell.copy(alpha = 0.64f),
                                        radius = side * 0.115f,
                                        center = reelCenter,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = (side * 0.012f).coerceAtLeast(1f)
                                        )
                                    )
                                    drawCircle(
                                        color = shell.copy(alpha = 0.52f),
                                        radius = side * 0.035f,
                                        center = reelCenter
                                    )
                                }
                                drawRoundRect(
                                    color = shell.copy(alpha = 0.54f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.045f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = (side * 0.006f).coerceAtLeast(1f)
                                    )
                                )
                            }
                            else -> Unit
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!artworkUrl.isNullOrEmpty() && !isError) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Error) {
                                isError = true
                            }
                        },
                        modifier = Modifier.fillMaxSize().clip(artworkShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(artworkShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }
            }
        }
    }
}
