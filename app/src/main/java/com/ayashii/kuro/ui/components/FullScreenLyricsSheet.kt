package com.ayashii.kuro.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayashii.kuro.R
import com.ayashii.kuro.data.lyrics.LyricsData
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography
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
    val colors = KuroAppTheme.colors
    val currentPositionMs by viewModel.playbackPosition.collectAsState()
    val durationMs by viewModel.duration.collectAsState()

    var showTranslation by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val activeIndex by remember(lyricsData, currentPositionMs) {
        derivedStateOf {
            lyricsData?.getCurrentLineIndex(currentPositionMs) ?: -1
        }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Header: Down arrow, Song Title & Artist, More
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
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
                            val shareText = if (lyricsData != null && lyricsData.lines.isNotEmpty()) {
                                val snippet = lyricsData.lines.take(8).joinToString("\n") { it.text }
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

            // Synced Karaoke Lyrics List
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (lyricsData == null || lyricsData.lines.isEmpty()) {
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
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        itemsIndexed(lyricsData.lines) { index, line ->
                            val isActive = index == activeIndex
                            val animatedFontSize by animateFloatAsState(
                                targetValue = if (isActive) 26f else 20f,
                                animationSpec = tween(200),
                                label = "lyrics_font_size"
                            )

                            val textColor by animateColorAsState(
                                targetValue = if (isActive) colors.textPrimary else colors.textSecondary.copy(alpha = 0.35f),
                                animationSpec = tween(220),
                                label = "lyrics_color"
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.seekTo(line.startTimeMs)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = line.text,
                                    fontSize = animatedFontSize.sp,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = textColor,
                                    lineHeight = (animatedFontSize * 1.35f).sp
                                )

                                if (showTranslation && !line.translation.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = line.translation!!,
                                        fontSize = if (isActive) 15.sp else 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isActive) colors.textSecondary else colors.textSecondary.copy(alpha = 0.35f),
                                        lineHeight = if (isActive) 20.sp else 17.sp
                                    )
                                }
                            }
                        }

                        // Bottom Actions: Share pill & Language translation pill (Like in image 3)
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Share Pill
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

                                // Translation Pill
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (showTranslation) colors.textPrimary else colors.card)
                                        .border(1.dp, if (showTranslation) colors.textPrimary else colors.border, RoundedCornerShape(20.dp))
                                        .clickable { showTranslation = !showTranslation }
                                        .padding(horizontal = 16.dp, vertical = 9.dp),
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
                                        text = stringResource(R.string.player_indonesian),
                                        style = Typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                        color = if (showTranslation) colors.background else colors.textPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                }
            }

            // Bottom Player Scrubber + Play/Pause Button (Image 3)
            val effectiveDuration = if (durationMs > 0) durationMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)
            val progress = (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
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
                    Text(
                        text = formatMs(currentPositionMs),
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
                            val playScale by animateFloatAsState(
                                targetValue = if (isPlaying) 0.94f else 1f,
                                animationSpec = tween(durationMillis = 140),
                                label = "lyrics_play_scale"
                            )
                            IconButton(
                                onClick = onPlayPauseClick,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    painter = if (isPlaying) painterResource(R.drawable.ic_kuro_pause) else painterResource(R.drawable.ic_kuro_play),
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = colors.background,
                                    modifier = Modifier.size(30.dp).graphicsLayer { scaleX = playScale; scaleY = playScale }
                                )
                            }
                        }
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
