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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography

@Composable
fun SyncedLyricsView(
    lyricsData: LyricsData?,
    isLoading: Boolean,
    playbackPositionFlow: StateFlow<Long>,
    onSeekTo: (Long) -> Unit,
    isExpanded: Boolean = false,
    onExpandToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by playbackPositionFlow.collectAsState()
    var showTranslation by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // Derived active index dynamically reacts to currentPositionMs
    val activeIndex by remember(lyricsData) {
        derivedStateOf {
            lyricsData?.getCurrentLineIndex(currentPositionMs) ?: -1
        }
    }

    // Auto-scroll to center current lyric smoothly
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val targetScroll = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    val colors = KuroAppTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Lyrics Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = onExpandToggle != null) { onExpandToggle?.invoke() }
                ) {
                    Text(
                        text = "Lyrics",
                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.border)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (lyricsData?.isSynced == true) "KARAOKE SYNC" else "LYRICS",
                            style = Typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = colors.textSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bilingual Translation Toggle Pill: "文A ID: ON" / "ID: OFF" (from Design Spec)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (showTranslation) colors.textPrimary else colors.background)
                            .border(1.dp, if (showTranslation) colors.textPrimary else colors.border, RoundedCornerShape(12.dp))
                            .clickable { showTranslation = !showTranslation }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = "Translate",
                            tint = if (showTranslation) colors.background else colors.textPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showTranslation) "文A ID: ON" else "文A ID: OFF",
                            style = Typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (showTranslation) colors.background else colors.textPrimary
                        )
                    }

                    // Expand / Collapse Chevron Button
                    if (onExpandToggle != null) {
                        IconButton(
                            onClick = onExpandToggle,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
                            color = colors.textPrimary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Loading lyrics...",
                            style = Typography.bodyMedium,
                            color = colors.textMuted
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
                        color = colors.textMuted
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = lyricsData.lines,
                        key = { index, line -> "${line.timeMs}_$index" }
                    ) { index, line ->
                        val isActive = index == activeIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) colors.textPrimary else colors.textSecondary.copy(alpha = 0.45f),
                            animationSpec = tween(durationMillis = 200),
                            label = "textColor"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
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
                            // Active Accent Indicator Bar on the left (Design Spec)
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(colors.accent)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.text,
                                    style = Typography.headlineMedium,
                                    fontSize = if (isActive) 19.sp else 15.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor,
                                    lineHeight = if (isActive) 26.sp else 22.sp
                                )

                                if (showTranslation && line.translation != null) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = line.translation!!,
                                        style = Typography.bodyMedium,
                                        fontSize = if (isActive) 13.sp else 12.sp,
                                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isActive) colors.textPrimary.copy(alpha = 0.85f) else colors.textSecondary.copy(alpha = 0.35f),
                                        lineHeight = if (isActive) 19.sp else 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
