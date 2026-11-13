package com.ayashii.kuro.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    progress: Float = 0f,
    progressProvider: (() -> Float)? = null,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: (() -> Unit)? = null,
    onFavoriteClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KuroAppTheme.colors
    val haptic = LocalHapticFeedback.current
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mini_swipe_offset"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffsetX < -110f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dragOffsetX = 0f
                            onSkipNextClick()
                        } else if (dragOffsetX > 110f && onSkipPreviousClick != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dragOffsetX = 0f
                            onSkipPreviousClick()
                        } else {
                            dragOffsetX = 0f
                        }
                    },
                    onDragCancel = { dragOffsetX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-180f, 180f)
                        change.consume()
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .graphicsLayer {
                    translationX = animatedOffsetX * 0.35f
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = { fadeIn(tween(210)) togetherWith fadeOut(tween(170)) },
                    label = "mini_artwork"
                ) { animatedTrack ->
                    AsyncImage(
                        model = animatedTrack.thumbnailUrlForDisplay(144),
                        contentDescription = animatedTrack.title,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = { fadeIn(tween(210)) togetherWith fadeOut(tween(170)) },
                    label = "mini_metadata"
                ) { animatedTrack ->
                    Column {
                        Text(
                            text = animatedTrack.title,
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = animatedTrack.artist,
                            style = Typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (onFavoriteClick != null) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFavoriteClick()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) Color(0xFFFF3B30) else colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.textPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                val playScale by animateFloatAsState(
                    targetValue = if (isPlaying) 0.94f else 1f,
                    animationSpec = tween(durationMillis = 140),
                    label = "mini_play_scale"
                )
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = if (isPlaying) painterResource(R.drawable.ic_kuro_pause) else painterResource(R.drawable.ic_kuro_play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = colors.textPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            }
                    )
                }
            }

            IconButton(
                onClick = onSkipNextClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_kuro_fast_forward),
                    contentDescription = "Next",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        val trackLineColor = colors.border
        val activeLineColor = colors.accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .drawBehind {
                    val p = (progressProvider?.invoke() ?: progress).coerceIn(0f, 1f)
                    drawRect(color = trackLineColor, size = size)
                    if (p > 0f) {
                        drawRect(
                            color = activeLineColor,
                            topLeft = Offset.Zero,
                            size = Size(width = size.width * p, height = size.height)
                        )
                    }
                }
        )
    }
}
