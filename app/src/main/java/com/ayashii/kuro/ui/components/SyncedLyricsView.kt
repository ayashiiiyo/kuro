package com.ayashii.kuro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import com.ayashii.kuro.data.lyrics.LyricsData
import com.ayashii.kuro.ui.theme.DarkBorder
import com.ayashii.kuro.ui.theme.DarkCard
import com.ayashii.kuro.ui.theme.MutedText
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.PureWhite
import com.ayashii.kuro.ui.theme.SubtleGray
import com.ayashii.kuro.ui.theme.Typography

@Composable
fun SyncedLyricsView(
    lyricsData: LyricsData?,
    isLoading: Boolean,
    playbackPositionFlow: StateFlow<Long>,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by playbackPositionFlow.collectAsState()
    var showTranslation by remember { mutableStateOf(true) }
    var syncOffsetMs by remember { mutableStateOf(1200L) }
    val listState = rememberLazyListState()

    // Derived active index dynamically reacts to currentPositionMs with lead offset & intro guard!
    val activeIndex by remember(lyricsData, syncOffsetMs) {
        derivedStateOf {
            lyricsData?.getCurrentLineIndex(currentPositionMs, syncOffsetMs) ?: -1
        }
    }

    // Auto-scroll to center current lyric smoothly (ONLY when vocal has actually started!)
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val targetScroll = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Lyrics Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lyricsData?.isSynced == true) "KARAOKE SYNC" else "LYRICS",
                    style = Typography.labelSmall,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = SubtleGray
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sync Lead Offset Adjustment Pill (Only for Synced Lyrics!)
                    if (lyricsData?.isSynced == true) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(PureBlack)
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    syncOffsetMs = when (syncOffsetMs) {
                                        1200L -> 1800L
                                        1800L -> 0L
                                        0L -> 600L
                                        else -> 1200L
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (syncOffsetMs > 0) "⚡ +${syncOffsetMs / 1000f}s" else "⚡ 0s",
                                style = Typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = PureWhite
                            )
                        }
                    }

                    // Translation Toggle Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (showTranslation) PureWhite else PureBlack)
                            .border(1.dp, if (showTranslation) PureWhite else DarkBorder, RoundedCornerShape(12.dp))
                            .clickable { showTranslation = !showTranslation }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = "Translate",
                            tint = if (showTranslation) PureBlack else PureWhite,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showTranslation) "ID: ON" else "ID: OFF",
                            style = Typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showTranslation) PureBlack else PureWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = PureWhite,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Loading lyrics...",
                            style = Typography.bodyMedium,
                            color = MutedText
                        )
                    }
                }
            } else if (lyricsData == null || lyricsData.lines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No synchronized lyrics available",
                        style = Typography.bodyMedium,
                        color = MutedText
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = lyricsData.lines,
                        key = { index, line -> "${line.timeMs}_$index" }
                    ) { index, line ->
                        val isActive = index == activeIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) PureWhite else SubtleGray.copy(alpha = 0.45f),
                            animationSpec = tween(durationMillis = 200),
                            label = "textColor"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (line.timeMs >= 0) {
                                        onSeekTo(line.timeMs)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = line.text,
                                style = Typography.headlineMedium,
                                fontSize = if (isActive) 20.sp else 16.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = textColor,
                                lineHeight = if (isActive) 28.sp else 22.sp
                            )

                            if (showTranslation && line.translation != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = line.translation!!,
                                    style = Typography.bodyMedium,
                                    fontSize = if (isActive) 14.sp else 12.sp,
                                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isActive) PureWhite.copy(alpha = 0.85f) else SubtleGray.copy(alpha = 0.35f),
                                    lineHeight = if (isActive) 20.sp else 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
