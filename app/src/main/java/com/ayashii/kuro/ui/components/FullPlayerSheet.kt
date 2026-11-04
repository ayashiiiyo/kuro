package com.ayashii.kuro.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BluetoothAudio
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.ui.theme.rememberDynamicDominantColor
import com.ayashii.kuro.viewmodel.MusicViewModel

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
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    val currentLyrics by viewModel.currentLyrics.collectAsState()
    val audioDeviceState by viewModel.audioDeviceState.collectAsState()
    val currentPositionMs by viewModel.playbackPosition.collectAsState()

    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showFullScreenLyrics by remember { mutableStateOf(false) }
    var showTranslationInPreview by remember { mutableStateOf(true) }

    val colors = KuroAppTheme.colors
    val dominantColor = rememberDynamicDominantColor(track.thumbnailUrlForDisplay(768))
    val ambientGradient = remember(dominantColor) {
        val topTint = androidx.compose.ui.graphics.lerp(PureBlack, dominantColor, 0.40f)
        val midTint = androidx.compose.ui.graphics.lerp(PureBlack, dominantColor, 0.15f)
        Brush.verticalGradient(
            0.0f to topTint,
            0.35f to midTint,
            0.65f to PureBlack,
            1.0f to PureBlack
        )
    }

    // Derive active lyric snippet for display right under artwork
    val activeLineText: String by remember(currentLyrics, currentPositionMs) {
        derivedStateOf {
            val lyrics = currentLyrics
            if (lyrics != null && lyrics.lines.isNotEmpty()) {
                val idx = lyrics.getCurrentLineIndex(currentPositionMs)
                if (idx in lyrics.lines.indices) {
                    val line = lyrics.lines[idx]
                    val trans = line.translation
                    if (!trans.isNullOrBlank()) trans else line.text
                } else ""
            } else ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .background(ambientGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
            // Modern Header Bar: Collapse Arrow, Playing From context, More options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.player_playing_from).uppercase(),
                        style = Typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        ),
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist.ifEmpty { "Kuro" },
                        style = Typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { showOptionsMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opsi lagu",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Centered 1:1 Album Artwork (Clean & Focused)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Lyric Line Snippet (Right under artwork, dynamically updating with smooth slide-up animation)
            if (activeLineText.isNotBlank()) {
                AnimatedContent(
                    targetState = activeLineText,
                    transitionSpec = {
                        (slideInVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) { height -> height / 2 } + fadeIn(tween(260)))
                            .togetherWith(
                                slideOutVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { height -> -height / 2 } + fadeOut(tween(220))
                            )
                    },
                    label = "live_lyric_snippet",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clickable { showFullScreenLyrics = true }
                ) { snippet ->
                    Text(
                        text = snippet,
                        style = Typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 21.sp
                        ),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Song Info: Title & Artist on left, (+) / Favorite button on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
                        label = "full_player_title"
                    ) { animatedTrack ->
                        Column {
                            Text(
                                text = animatedTrack.title,
                                style = Typography.headlineMedium.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = animatedTrack.artist,
                                style = Typography.titleMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFavoriteClick()
                    },
                    modifier = Modifier.size(46.dp)
                ) {
                    val favScale by animateFloatAsState(
                        targetValue = if (track.isFavorite) 1.15f else 1f,
                        animationSpec = tween(durationMillis = 150),
                        label = "player_fav_scale"
                    )
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.CheckCircle else Icons.Outlined.AddCircleOutline,
                        contentDescription = "Save / Favorite",
                        tint = if (track.isFavorite) colors.textPrimary else colors.textPrimary.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer {
                                scaleX = favScale
                                scaleY = favScale
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrubber Progress Slider
            PlayerScrubber(
                viewModel = viewModel,
                track = track,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Main Playback Controls: Shuffle | Prev | Play/Pause (Big Solid White) | Next | Repeat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShuffleClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                        if (isShuffle) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(colors.textPrimary)
                                    .align(Alignment.BottomCenter)
                            )
                        }
                    }
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
                        val playScale by animateFloatAsState(
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
                                modifier = Modifier
                                    .size(34.dp)
                                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
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
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Utility Bar: Audio Output Icon (NO "Phone Speaker" TEXT!) | Share & Queue on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sleek Icon-only Audio Output Switcher (replaces old bulky text chip)
                IconButton(
                    onClick = { viewModel.openAudioOutputSettings() },
                    modifier = Modifier.size(40.dp)
                ) {
                    val icon = when (audioDeviceState.iconType) {
                        com.ayashii.kuro.data.audio.OutputType.WIRED -> Icons.Outlined.Headphones
                        com.ayashii.kuro.data.audio.OutputType.BLUETOOTH -> Icons.Outlined.BluetoothAudio
                        com.ayashii.kuro.data.audio.OutputType.SPEAKER -> Icons.Outlined.Speaker
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Audio Output",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            try {
                                val shareTemplate = context.getString(R.string.player_share_text, track.title, track.artist, track.url)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, shareTemplate)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.player_share_via)))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { showQueueSheet = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QueueMusic,
                            contentDescription = "Queue",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lyrics Preview Card (Spotify-style peeking bottom card with "Show lyrics" pill)
            val hasLyrics = currentLyrics != null && currentLyrics!!.lines.isNotEmpty() &&
                    currentLyrics!!.lines.none { it.text.trim().equals("null", ignoreCase = true) }

            if (hasLyrics) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.lerp(colors.card, dominantColor, 0.28f),
                                    colors.card
                                )
                            )
                        )
                        .border(1.dp, androidx.compose.ui.graphics.lerp(colors.border, dominantColor, 0.35f), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.player_lyrics_preview),
                                style = Typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.textPrimary
                            )

                            IconButton(
                                onClick = { showTranslationInPreview = !showTranslationInPreview },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Translate,
                                    contentDescription = "Toggle Translation",
                                    tint = if (showTranslationInPreview) colors.textPrimary else colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Preview lines (around active line or first 4 lines)
                        val lines = currentLyrics!!.lines
                        val activeIdx = currentLyrics!!.getCurrentLineIndex(currentPositionMs)
                        val previewStart = if (activeIdx >= 0) (activeIdx - 1).coerceAtLeast(0) else 0
                        val previewLines = lines.drop(previewStart).take(4)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            previewLines.forEachIndexed { idx, line ->
                                val isCurLine = (previewStart + idx) == activeIdx
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = line.text,
                                        style = Typography.bodyLarge.copy(
                                            fontSize = if (isCurLine) 17.sp else 15.sp,
                                            fontWeight = if (isCurLine) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            lineHeight = 22.sp
                                        ),
                                        color = if (isCurLine) colors.textPrimary else colors.textSecondary.copy(alpha = 0.5f)
                                    )
                                    if (showTranslationInPreview && !line.translation.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = line.translation!!,
                                            style = Typography.bodySmall.copy(
                                                fontSize = if (isCurLine) 13.sp else 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                lineHeight = 16.sp
                                            ),
                                            color = if (isCurLine) colors.textSecondary else colors.textSecondary.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // "Show lyrics" Pill Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.textPrimary)
                                .clickable { showFullScreenLyrics = true }
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.player_show_lyrics),
                                style = Typography.labelMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.background
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(36.dp))
            } else {
                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        // Dedicated Full Screen Lyrics Sheet (Image 3)
        AnimatedVisibility(
            visible = showFullScreenLyrics,
            enter = fadeIn(tween(220)) + slideInVertically(
                animationSpec = tween(280),
                initialOffsetY = { it }
            ),
            exit = fadeOut(tween(200)) + slideOutVertically(
                animationSpec = tween(260),
                targetOffsetY = { it }
            )
        ) {
            FullScreenLyricsSheet(
                track = track,
                lyricsData = currentLyrics,
                viewModel = viewModel,
                isPlaying = isPlaying,
                isLoadingTrack = isLoading,
                onPlayPauseClick = onPlayPauseClick,
                onDismiss = { showFullScreenLyrics = false }
            )
        }
    }

    if (showOptionsMenu) {
        TrackOptionsBottomSheet(
            track = track,
            viewModel = viewModel,
            onDismiss = { showOptionsMenu = false },
            onOpenLyrics = { showFullScreenLyrics = true },
            onOpenQueue = { showQueueSheet = true },
            onAddToPlaylist = { showAddToPlaylist = true },
            onOpenSettings = { showSettingsDialog = true }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            viewModel = viewModel,
            onDismiss = { showQueueSheet = false }
        )
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
