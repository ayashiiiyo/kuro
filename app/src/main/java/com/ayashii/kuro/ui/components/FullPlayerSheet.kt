package com.ayashii.kuro.ui.components

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.BluetoothAudio
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

@Composable
fun FullPlayerSheet(
    track: Track,
    viewModel: MusicViewModel,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    isShuffle: Boolean = false,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currentLyrics by viewModel.currentLyrics.collectAsState()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()
    val audioDeviceState by viewModel.audioDeviceState.collectAsState()

    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var isLyricsExpanded by remember { mutableStateOf(false) }

    val colors = KuroAppTheme.colors
    val artworkHeight = (LocalConfiguration.current.screenHeightDp * 0.32f).coerceIn(210f, 290f).dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        if (totalDragY > 140f && scrollState.value == 0) {
                            onDismiss()
                        }
                    },
                    onDragCancel = { totalDragY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        if (scrollState.value == 0 && dragAmount > 0) {
                            totalDragY += dragAmount
                            change.consume()
                        }
                    }
                )
            }
            .verticalScroll(scrollState)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artworkHeight)
        ) {
            AnimatedContent(
                targetState = track,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "full_player_artwork"
            ) { animatedTrack ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(animatedTrack.thumbnailUrlForDisplay(768))
                        .crossfade(false)
                        .build(),
                    contentDescription = animatedTrack.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                colors.background.copy(alpha = 0.35f),
                                colors.background.copy(alpha = 0.85f),
                                colors.background
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.player_now_playing),
                    style = Typography.labelSmall.copy(
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White.copy(alpha = 0.9f)
                )

                Box {
                    IconButton(
                        onClick = { showOptionsMenu = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opsi lagu",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        modifier = Modifier.background(colors.card)
                    ) {
                        val playlists by viewModel.playlists.collectAsState()
                        val isWatchLater = remember(playlists, track.id) {
                            playlists.firstOrNull { it.name == "Putar Nanti" }?.tracks?.any { it.id == track.id } == true
                        }
                        DropdownMenuItem(
                            text = { Text(if (isWatchLater) stringResource(R.string.player_remove_watch_later) else stringResource(R.string.player_save_watch_later), color = colors.textPrimary) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(if (isWatchLater) R.drawable.ic_kuro_bookmark_filled else R.drawable.ic_kuro_bookmark_border),
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                viewModel.toggleWatchLater(track)
                                showOptionsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_add_to_playlist), color = colors.textPrimary) },
                            leadingIcon = { Icon(Icons.Outlined.QueueMusic, contentDescription = null, tint = colors.textPrimary) },
                            onClick = {
                                showOptionsMenu = false
                                showAddToPlaylist = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_share_song), color = colors.textPrimary) },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null, tint = colors.textPrimary) },
                            onClick = {
                                showOptionsMenu = false
                                try {
                                    val shareTemplate = context.getString(R.string.player_share_text, track.title, track.artist, track.url)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareTemplate)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.player_share_via)))
                                } catch (_: Exception) {}
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_audio_settings), color = colors.textPrimary) },
                            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, tint = colors.textPrimary) },
                            onClick = {
                                showOptionsMenu = false
                                showSettingsDialog = true
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                        label = "full_player_metadata"
                    ) { animatedTrack ->
                        Column {
                            Text(
                                text = animatedTrack.title,
                                style = Typography.headlineMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = animatedTrack.artist,
                                style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onFavoriteClick()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    val favScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (track.isFavorite) 1.15f else 1f,
                        animationSpec = tween(durationMillis = 150),
                        label = "player_fav_scale"
                    )
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) Color(0xFFFF3B30) else colors.textMuted,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = favScale
                                scaleY = favScale
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PlayerScrubber(
                viewModel = viewModel,
                track = track
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShuffleClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onSkipPreviousClick,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_kuro_rewind),
                        contentDescription = "Previous Track",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.textPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = colors.background,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        val playScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isPlaying) 0.94f else 1f,
                            animationSpec = tween(durationMillis = 140),
                            label = "full_player_play_scale"
                        )
                        IconButton(
                            onClick = onPlayPauseClick,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                painter = if (isPlaying) painterResource(R.drawable.ic_kuro_pause) else painterResource(R.drawable.ic_kuro_play),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = colors.background,
                                modifier = Modifier.size(34.dp).graphicsLayer { scaleX = playScale; scaleY = playScale }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onSkipNextClick,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_kuro_fast_forward),
                        contentDescription = "Next Track",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = onRepeatClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    val tint = if (repeatMode != Player.REPEAT_MODE_OFF) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f)
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .clickable { viewModel.openAudioOutputSettings() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (audioDeviceState.iconType) {
                        com.ayashii.kuro.data.audio.OutputType.WIRED -> Icons.Outlined.Headphones
                        com.ayashii.kuro.data.audio.OutputType.BLUETOOTH -> Icons.Outlined.BluetoothAudio
                        com.ayashii.kuro.data.audio.OutputType.SPEAKER -> Icons.Outlined.VolumeUp
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Audio Output",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = audioDeviceState.title,
                        style = Typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                }

            }

            Spacer(modifier = Modifier.height(12.dp))

            val hasLyrics = currentLyrics != null && currentLyrics!!.lines.isNotEmpty() &&
                    currentLyrics!!.lines.none { it.text.trim().equals("null", ignoreCase = true) }

            if (hasLyrics) {
                val lyricsHeight = if (isLyricsExpanded) 380.dp else 160.dp
                SyncedLyricsView(
                    lyricsData = currentLyrics,
                    isLoading = isLoadingLyrics,
                    playbackPositionFlow = viewModel.playbackPosition,
                    onSeekTo = { viewModel.seekTo(it) },
                    isExpanded = isLyricsExpanded,
                    onExpandToggle = {
                        isLyricsExpanded = !isLyricsExpanded
                        if (isLyricsExpanded) {
                            scope.launch {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lyricsHeight)
                )
                Spacer(modifier = Modifier.height(36.dp))
            } else {
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            track = track,
            viewModel = viewModel,
            onDismiss = { showAddToPlaylist = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun PlayerScrubber(
    viewModel: MusicViewModel,
    track: Track,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by viewModel.playbackPosition.collectAsState()
    val durationMs by viewModel.duration.collectAsState()

    val effectiveDuration = if (durationMs > 0) durationMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)
    val progress = if (effectiveDuration > 0) {
        (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val currentFormatted = formatMs(currentPositionMs)
    val totalFormatted = formatMs(effectiveDuration)

    val colors = KuroAppTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { newProgress ->
                viewModel.seekTo((newProgress * effectiveDuration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = colors.textPrimary,
                activeTrackColor = colors.textPrimary,
                inactiveTrackColor = colors.border
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = currentFormatted, style = Typography.bodyMedium, color = colors.textSecondary)
            Text(text = totalFormatted, style = Typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
