package com.ayashii.kuro.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BluetoothAudio
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ayashii.kuro.R
import com.ayashii.kuro.data.lyrics.LrcParser
import com.ayashii.kuro.data.lyrics.LyricsData
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.KuroMotion
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.ui.theme.rememberDynamicDominantColor
import com.ayashii.kuro.viewmodel.MusicViewModel
import kotlinx.coroutines.delay

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

    var showAddToPlaylist by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showOptionsMenu by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var showFullScreenLyrics by rememberSaveable { mutableStateOf(false) }
    var showTranslationInPreview by rememberSaveable { mutableStateOf(true) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var lastDragDeltaY by remember { mutableFloatStateOf(0f) }
    var isDraggingSheet by remember { mutableStateOf(false) }
    var isDismissingSheet by remember { mutableStateOf(false) }
    var pullUpLyricsY by remember { mutableFloatStateOf(0f) }
    var artworkDragX by remember { mutableFloatStateOf(0f) }
    var showHeartBurst by remember { mutableStateOf(false) }

    LaunchedEffect(track.id, showFullScreenLyrics) {
        dragOffsetY = 0f
        isDraggingSheet = false
        isDismissingSheet = false
        pullUpLyricsY = 0f
    }

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(650L)
            showHeartBurst = false
        }
    }

    val settledDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sheet_drag_offset"
    )
    val animatedDragY = if (isDraggingSheet || isDismissingSheet) dragOffsetY else settledDragY

    val animatedArtworkX by animateFloatAsState(
        targetValue = artworkDragX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "artwork_swipe_offset"
    )

    BackHandler(enabled = !showFullScreenLyrics) {
        onDismiss()
    }

    BackHandler(enabled = showFullScreenLyrics) {
        showFullScreenLyrics = false
    }

    val colors = KuroAppTheme.colors
    val dominantColor = rememberDynamicDominantColor(track.thumbnailUrlForDisplay(768))
    val baseBg = colors.background
    val ambientGradient = remember(dominantColor, baseBg, colors.isDark) {
        val topWeight = if (colors.isDark) 0.40f else 0.22f
        val midWeight = if (colors.isDark) 0.15f else 0.08f
        val topTint = androidx.compose.ui.graphics.lerp(baseBg, dominantColor, topWeight)
        val midTint = androidx.compose.ui.graphics.lerp(baseBg, dominantColor, midWeight)
        Brush.verticalGradient(
            0.0f to topTint,
            0.35f to midTint,
            0.65f to baseBg,
            1.0f to baseBg
        )
    }

    val validLyricsLines = remember(currentLyrics) {
        currentLyrics?.let { LrcParser.sanitizeLines(it.lines) } ?: emptyList()
    }
    val hasLyricsAvailable = validLyricsLines.isNotEmpty()
    val sanitizedLyricsData = remember(currentLyrics, validLyricsLines) {
        currentLyrics?.copy(lines = validLyricsLines)
    }
    val nestedScrollConnection = remember(scrollState, hasLyricsAvailable) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && dragOffsetY > 0f) {
                    val nextOffset = (dragOffsetY + available.y).coerceAtLeast(0f)
                    val consumedY = nextOffset - dragOffsetY
                    dragOffsetY = nextOffset
                    lastDragDeltaY = available.y
                    isDraggingSheet = nextOffset > 0f
                    return Offset(0f, consumedY)
                }
                if (source == NestedScrollSource.Drag && pullUpLyricsY > 0f && available.y > 0f) {
                    val nextPull = (pullUpLyricsY - available.y).coerceAtLeast(0f)
                    val consumedY = pullUpLyricsY - nextPull
                    pullUpLyricsY = nextPull
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag) {
                    if (available.y > 0f && scrollState.value == 0) {
                        isDraggingSheet = true
                        lastDragDeltaY = available.y
                        dragOffsetY = (dragOffsetY + available.y).coerceAtLeast(0f)
                        return Offset(0f, available.y)
                    } else if (available.y < 0f && scrollState.value >= scrollState.maxValue && hasLyricsAvailable) {
                        pullUpLyricsY = (pullUpLyricsY - available.y).coerceAtMost(180f)
                        if (pullUpLyricsY > 72f && !showFullScreenLyrics) {
                            pullUpLyricsY = 0f
                            dragOffsetY = 0f
                            isDraggingSheet = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showFullScreenLyrics = true
                        }
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pullUpLyricsY = 0f
                if (dragOffsetY > 0f) {
                    val shouldDismiss = dragOffsetY > 105f || available.y > 620f || (dragOffsetY > 34f && lastDragDeltaY > 11f)
                    isDraggingSheet = false
                    lastDragDeltaY = 0f
                    if (shouldDismiss) {
                        isDismissingSheet = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    } else {
                        dragOffsetY = 0f
                    }
                    return available
                }
                if (available.y < -1100f && scrollState.value >= scrollState.maxValue && hasLyricsAvailable && !showFullScreenLyrics) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showFullScreenLyrics = true
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val currentY = animatedDragY.coerceAtLeast(0f)
                val dismissProgress = (currentY / 560f).coerceIn(0f, 1f)
                translationY = currentY
                alpha = 1f - (dismissProgress * 0.16f)
            }
            .background(baseBg)
            .background(ambientGradient)
            .nestedScroll(nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .pointerInput(hasLyricsAvailable) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDraggingSheet = true
                                lastDragDeltaY = 0f
                            },
                            onDragEnd = {
                                val shouldDismiss = dragOffsetY > 90f || (dragOffsetY > 30f && lastDragDeltaY > 11f)
                                isDraggingSheet = false
                                lastDragDeltaY = 0f
                                if (shouldDismiss) {
                                    isDismissingSheet = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDismiss()
                                } else {
                                    dragOffsetY = 0f
                                }
                            },
                            onDragCancel = {
                                isDraggingSheet = false
                                dragOffsetY = 0f
                                lastDragDeltaY = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 0 || dragOffsetY > 0f) {
                                    lastDragDeltaY = dragAmount
                                    dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                    change.consume()
                                } else if (dragAmount < -18f && hasLyricsAvailable && !showFullScreenLyrics) {
                                    isDraggingSheet = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showFullScreenLyrics = true
                                    change.consume()
                                }
                            }
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.textMuted.copy(alpha = 0.36f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                        .graphicsLayer {
                            translationX = animatedArtworkX * 0.55f
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .pointerInput(track.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showHeartBurst = true
                                    if (!track.isFavorite) {
                                        onFavoriteClick()
                                    }
                                }
                            )
                        }
                        .pointerInput(track.id) {
                            detectHorizontalDragGestures(
                                onDragStart = { artworkDragX = 0f },
                                onDragEnd = {
                                    if (artworkDragX < -85f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        artworkDragX = 0f
                                        onSkipNextClick()
                                    } else if (artworkDragX > 85f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        artworkDragX = 0f
                                        onSkipPreviousClick()
                                    } else {
                                        artworkDragX = 0f
                                    }
                                },
                                onDragCancel = { artworkDragX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    artworkDragX = (artworkDragX + dragAmount).coerceIn(-220f, 220f)
                                    change.consume()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = track,
                        contentKey = { it.id },
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                        label = "full_player_artwork"
                    ) { animatedTrack ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(animatedTrack.thumbnailUrlForDisplay(768))
                                .crossfade(200)
                                .build(),
                            contentDescription = animatedTrack.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showHeartBurst,
                        enter = scaleIn(
                            initialScale = 0.4f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(tween(120)),
                        exit = scaleOut(targetScale = 1.35f, animationSpec = tween(220)) + fadeOut(tween(220))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF3B30),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LiveLyricSnippet(
                lyricsData = sanitizedLyricsData,
                viewModel = viewModel,
                onClick = { showFullScreenLyrics = true }
            )

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
                        contentKey = { it.id },
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
                    val favTint by animateColorAsState(
                        targetValue = if (track.isFavorite) Color(0xFFFF3B30) else colors.textPrimary.copy(alpha = 0.85f),
                        animationSpec = tween(durationMillis = 200),
                        label = "player_fav_tint"
                    )
                    AnimatedContent(
                        targetState = track.isFavorite,
                        transitionSpec = {
                            (scaleIn(
                                initialScale = 0.55f,
                                animationSpec = KuroMotion.appleSnappySpring()
                            ) + fadeIn(tween(150)))
                                .togetherWith(
                                    scaleOut(
                                        targetScale = 0.55f,
                                        animationSpec = tween(120)
                                    ) + fadeOut(tween(120))
                                )
                        },
                        label = "player_fav_icon_morph"
                    ) { isFav ->
                        Icon(
                            imageVector = if (isFav) Icons.Default.CheckCircle else Icons.Outlined.AddCircleOutline,
                            contentDescription = "Save / Favorite",
                            tint = favTint,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PlayerScrubber(
                viewModel = viewModel,
                track = track,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                        IconButton(
                            onClick = onPlayPauseClick,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                painter = if (isPlaying) painterResource(R.drawable.ic_kuro_pause) else painterResource(R.drawable.ic_kuro_play),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = colors.background,
                                modifier = Modifier.size(34.dp)
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            val hasLyrics = sanitizedLyricsData != null && sanitizedLyricsData.lines.isNotEmpty() &&
                    sanitizedLyricsData.lines.none { it.text.trim().equals("null", ignoreCase = true) }

            if (hasLyrics) {
                LyricsPreviewCard(
                    lyricsData = sanitizedLyricsData!!,
                    viewModel = viewModel,
                    dominantColor = dominantColor,
                    showTranslationInPreview = showTranslationInPreview,
                    onToggleTranslation = { showTranslationInPreview = !showTranslationInPreview },
                    onExpandLyrics = { showFullScreenLyrics = true }
                )
                Spacer(modifier = Modifier.height(36.dp))
            } else {
                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        AnimatedVisibility(
            visible = showFullScreenLyrics,
            enter = fadeIn(tween(200, easing = KuroMotion.EmphasizedDecelerate)) + slideInVertically(
                animationSpec = tween(250, easing = KuroMotion.EmphasizedDecelerate),
                initialOffsetY = { it }
            ),
            exit = fadeOut(tween(170, easing = KuroMotion.EmphasizedDecelerate)) + slideOutVertically(
                animationSpec = tween(210, easing = KuroMotion.EmphasizedDecelerate),
                targetOffsetY = { it }
            )
        ) {
            FullScreenLyricsSheet(
                track = track,
                lyricsData = sanitizedLyricsData,
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
private fun LiveLyricSnippet(
    lyricsData: LyricsData?,
    viewModel: MusicViewModel,
    onClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    val currentPositionMs by viewModel.playbackPosition.collectAsState()

    val activeLineText: String by remember(lyricsData) {
        derivedStateOf {
            if (lyricsData != null && lyricsData.lines.isNotEmpty()) {
                val idx = lyricsData.getCurrentLineIndex(currentPositionMs)
                if (idx in lyricsData.lines.indices) {
                    val line = lyricsData.lines[idx]
                    val trans = line.translation
                    if (!trans.isNullOrBlank()) trans else line.text
                } else ""
            } else ""
        }
    }

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
                .clickable(onClick = onClick)
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
}

@Composable
private fun LyricsPreviewCard(
    lyricsData: LyricsData,
    viewModel: MusicViewModel,
    dominantColor: Color,
    showTranslationInPreview: Boolean,
    onToggleTranslation: () -> Unit,
    onExpandLyrics: () -> Unit
) {
    val colors = KuroAppTheme.colors
    val currentPositionMs by viewModel.playbackPosition.collectAsState()
    val activeIdx by remember(lyricsData) {
        derivedStateOf {
            lyricsData.getCurrentLineIndex(currentPositionMs)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.lerp(colors.card, dominantColor, if (colors.isDark) 0.28f else 0.16f),
                        colors.card
                    )
                )
            )
            .border(1.dp, androidx.compose.ui.graphics.lerp(colors.border, dominantColor, 0.35f), RoundedCornerShape(20.dp))
            .clickable(onClick = onExpandLyrics)
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
                    onClick = onToggleTranslation,
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

            val lines = lyricsData.lines
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

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.textPrimary)
                    .clickable(onClick = onExpandLyrics)
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
}

@Composable
fun PlayerScrubber(
    viewModel: MusicViewModel,
    track: Track,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by viewModel.playbackPosition.collectAsState()
    val bufferedPositionMs by viewModel.bufferedPosition.collectAsState()
    val durationMs by viewModel.duration.collectAsState()

    val effectiveDuration = if (durationMs > 0) durationMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)
    val progress = if (effectiveDuration > 0) {
        (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val bufferedProgress = if (effectiveDuration > 0) {
        (bufferedPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val displayProgress = if (isDragging) dragProgress else progress
    val displayCurrentMs = if (isDragging) (dragProgress * effectiveDuration).toLong() else currentPositionMs

    val currentFormatted = formatMs(displayCurrentMs)
    val totalFormatted = formatMs(effectiveDuration)

    val colors = KuroAppTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.border.copy(alpha = 0.45f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedProgress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.textSecondary.copy(alpha = 0.45f))
                )
            }
            Slider(
                value = displayProgress,
                onValueChange = { newProgress ->
                    isDragging = true
                    dragProgress = newProgress
                },
                onValueChangeFinished = {
                    viewModel.seekTo((dragProgress * effectiveDuration).toLong())
                    isDragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = colors.textPrimary,
                    activeTrackColor = colors.textPrimary,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

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

@Composable
fun SleepTimerDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val colors = KuroAppTheme.colors
    val activeMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val endOfTrack by viewModel.sleepTimerEndOfTrack.collectAsState()
    val presets = listOf(15, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.sleep_timer_title),
                    style = Typography.titleLarge,
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { mins ->
                    val isSelected = activeMinutes == mins && !endOfTrack
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.accent else colors.card)
                            .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setSleepTimer(mins, pauseAtEndOfTrack = false)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.sleep_timer_minutes, mins),
                            style = Typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) colors.background else colors.textPrimary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (endOfTrack) colors.accent else colors.card)
                        .border(1.dp, if (endOfTrack) colors.accent else colors.border, RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.setSleepTimer(null, pauseAtEndOfTrack = true)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_end_track),
                        style = Typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (endOfTrack) colors.background else colors.textPrimary
                    )
                }

                if (activeMinutes != null || endOfTrack) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .border(1.dp, Color(0xFFFF3B30), RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.cancelSleepTimer()
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.sleep_timer_off),
                            style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFF3B30)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = colors.textPrimary)
            }
        },
        containerColor = colors.background
    )
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
