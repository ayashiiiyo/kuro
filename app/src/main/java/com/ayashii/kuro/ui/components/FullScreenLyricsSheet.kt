package com.ayashii.kuro.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayashii.kuro.R
import com.ayashii.kuro.data.lyrics.LrcParser
import com.ayashii.kuro.data.lyrics.LyricsData
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.settings.SettingsManager
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.KuroMotion
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.ui.theme.rememberDynamicDominantColor
import com.ayashii.kuro.viewmodel.MusicViewModel

@Composable
fun FullScreenLyricsSheet(
    track: Track,
    lyricsData: LyricsData?,
    viewModel: MusicViewModel,
    isPlaying: Boolean,
    isLoadingTrack: Boolean,
    onPlayPauseClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colors = KuroAppTheme.colors
    val dominantColor = rememberDynamicDominantColor(track.thumbnailUrlForDisplay(768))
    val baseBg = colors.background
    val lyricsGradient = remember(dominantColor, baseBg, colors.isDark) {
        val topWeight = if (colors.isDark) 0.45f else 0.24f
        val midWeight = if (colors.isDark) 0.20f else 0.10f
        val topTint = androidx.compose.ui.graphics.lerp(baseBg, dominantColor, topWeight)
        val midTint = androidx.compose.ui.graphics.lerp(baseBg, dominantColor, midWeight)
        Brush.verticalGradient(
            0.0f to topTint,
            0.35f to midTint,
            0.70f to baseBg,
            1.0f to baseBg
        )
    }

    val sanitizedLyrics = remember(lyricsData) {
        lyricsData?.let { data ->
            val clean = LrcParser.sanitizeLines(data.lines)
            data.copy(lines = clean)
        }
    }

    val settingsManager = remember { SettingsManager.getInstance() }
    val currentLyricsLanguage by settingsManager.lyricsLanguage.collectAsState()
    var showTranslation by remember { mutableStateOf(true) }
    var showLangPickerDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var userScrolledRecently by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var lastDragDeltaY by remember { mutableFloatStateOf(0f) }
    var isDraggingSheet by remember { mutableStateOf(false) }
    var isDismissingSheet by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        dragOffsetY = 0f
        isDraggingSheet = false
        isDismissingSheet = false
    }

    val settledDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lyrics_drag_offset"
    )
    val animatedDragY = if (isDraggingSheet || isDismissingSheet) dragOffsetY else settledDragY

    BackHandler {
        onDismiss()
    }

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            userScrolledRecently = true
            kotlinx.coroutines.delay(3500)
            userScrolledRecently = false
        }
    }

    var activeIndex by remember(sanitizedLyrics) {
        mutableIntStateOf(sanitizedLyrics?.getCurrentLineIndex(viewModel.getPositionMs()) ?: -1)
    }

    LaunchedEffect(sanitizedLyrics, viewModel) {
        viewModel.playbackPosition.collect { posMs ->
            val nextIdx = sanitizedLyrics?.getCurrentLineIndex(posMs) ?: -1
            if (nextIdx != activeIndex) {
                activeIndex = nextIdx
            }
        }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !userScrolledRecently && !listState.isScrollInProgress) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val lyricsNestedScroll = remember(listState) {
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
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0f && isAtTop) {
                    isDraggingSheet = true
                    lastDragDeltaY = available.y
                    dragOffsetY = (dragOffsetY + available.y).coerceAtLeast(0f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffsetY > 0f) {
                    val shouldDismiss = dragOffsetY > 100f || available.y > 600f || (dragOffsetY > 34f && lastDragDeltaY > 12f)
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
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val currentY = animatedDragY.coerceAtLeast(0f)
                val dismissProgress = (currentY / 540f).coerceIn(0f, 1f)
                translationY = currentY
                alpha = 1f - (dismissProgress * 0.16f)
            }
            .background(baseBg)
            .background(lyricsGradient)
            .nestedScroll(lyricsNestedScroll)
            .pointerInput(isAtTop) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDraggingSheet = true
                        lastDragDeltaY = 0f
                    },
                    onDragEnd = {
                        val shouldDismiss = dragOffsetY > 100f || (dragOffsetY > 34f && lastDragDeltaY > 12f)
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
                        if (isAtTop && (dragAmount > 0 || dragOffsetY > 0f)) {
                            lastDragDeltaY = dragAmount
                            dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                            change.consume()
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .pointerInput(Unit) {
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
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse Lyrics",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = track.title,
                            style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            style = Typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    IconButton(
                        onClick = {
                            try {
                                val shareText = if (sanitizedLyrics != null && sanitizedLyrics.lines.isNotEmpty()) {
                                    val snippet = sanitizedLyrics.lines.take(8).joinToString("\n") { it.text }
                                    "${track.title} - ${track.artist}\n\n$snippet\n\n${track.url}"
                                } else {
                                    "${track.title} - ${track.artist}: ${track.url}"
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(intent, "Bagikan Lirik"))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (sanitizedLyrics == null || sanitizedLyrics.lines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.player_lyrics_unavailable),
                            style = Typography.titleMedium,
                            color = colors.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        itemsIndexed(
                            items = sanitizedLyrics.lines,
                            key = { idx, item -> "${idx}_${item.timeMs}" }
                        ) { index, line ->
                            val isActive = index == activeIndex
                            val distance = kotlin.math.abs(index - activeIndex)
                            val targetAlpha = when {
                                isActive -> 1.0f
                                distance == 1 -> 0.54f
                                distance == 2 -> 0.34f
                                else -> 0.20f
                            }
                            val lineAlpha by animateFloatAsState(
                                targetValue = targetAlpha,
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                label = "lyrics_line_alpha"
                            )
                            val lineOffsetX by animateFloatAsState(
                                targetValue = if (isActive) 6f else 0f,
                                animationSpec = KuroMotion.appleFluidSpring(),
                                label = "lyrics_line_offset_x"
                            )

                            val textColor by animateColorAsState(
                                targetValue = if (isActive) colors.textPrimary else colors.textSecondary,
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                label = "lyrics_color"
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = lineAlpha
                                        translationX = lineOffsetX
                                    }
                                    .clickable {
                                        viewModel.seekTo(line.timeMs)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = line.text,
                                    fontSize = 23.sp,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = textColor,
                                    lineHeight = 31.sp
                                )

                                if (showTranslation && !line.translation.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = line.translation!!,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isActive) colors.textSecondary else colors.textSecondary.copy(alpha = 0.45f),
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.card)
                                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                                        .clickable {
                                            try {
                                                val shareText = "${track.title} - ${track.artist}\n${track.url}"
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Bagikan"))
                                            } catch (_: Exception) {}
                                        }
                                        .padding(horizontal = 16.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = "Share",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.player_share),
                                        style = Typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (showTranslation) colors.textPrimary else colors.card)
                                        .border(1.dp, if (showTranslation) colors.textPrimary else colors.border, RoundedCornerShape(20.dp))
                                        .clickable { showTranslation = !showTranslation }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Translate,
                                        contentDescription = "Translation",
                                        tint = if (showTranslation) colors.background else colors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${stringResource(R.string.player_translate_lyrics)} · ${currentLyricsLanguage.deeplCode.uppercase()}",
                                        style = Typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                        color = if (showTranslation) colors.background else colors.textPrimary
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.card)
                                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                                        .clickable { showLangPickerDialog = true }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentLyricsLanguage.displayName.substringBefore(" ("),
                                        style = Typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                }
            }

            LyricsBottomControls(
                track = track,
                viewModel = viewModel,
                isPlaying = isPlaying,
                isLoadingTrack = isLoadingTrack,
                onPlayPauseClick = onPlayPauseClick
            )
        }
    }

    if (showLangPickerDialog) {
        LyricsLanguagePickerDialog(
            currentLyricsLanguage = currentLyricsLanguage,
            onSelectLanguage = { selected ->
                settingsManager.setLyricsLanguage(selected)
                showTranslation = true
                viewModel.retranslateActiveLyrics(sanitizedLyrics, selected)
                showLangPickerDialog = false
            },
            onDismiss = { showLangPickerDialog = false }
        )
    }
}

@Composable
private fun LyricsBottomControls(
    track: Track,
    viewModel: MusicViewModel,
    isPlaying: Boolean,
    isLoadingTrack: Boolean,
    onPlayPauseClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    val currentPositionMs by viewModel.playbackPosition.collectAsState()
    val durationMs by viewModel.duration.collectAsState()

    val effectiveDuration = if (durationMs > 0) durationMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)
    val progress = (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val displayProgress = if (isDragging) dragProgress else progress
    val displayCurrentMs = if (isDragging) (dragProgress * effectiveDuration).toLong() else currentPositionMs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
    ) {
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
            Text(
                text = formatMs(displayCurrentMs),
                style = Typography.bodySmall,
                color = colors.textMuted
            )
            Text(
                text = formatMs(effectiveDuration),
                style = Typography.bodySmall,
                color = colors.textMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.textPrimary),
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingTrack) {
                    CircularProgressIndicator(
                        color = colors.background,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            painter = if (isPlaying) painterResource(R.drawable.ic_kuro_pause) else painterResource(R.drawable.ic_kuro_play),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = colors.background,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
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
