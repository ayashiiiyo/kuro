package com.ayashii.kuro.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import com.ayashii.kuro.ui.theme.KuroMotion
import com.ayashii.kuro.ui.theme.Typography
import kotlin.math.abs

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
    onSwipeDown: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KuroAppTheme.colors
    val haptic = LocalHapticFeedback.current
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var lockedAxis by remember { mutableIntStateOf(0) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mini_swipe_offset_x"
    )

    val animatedOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mini_swipe_offset_y"
    )

    val miniArtScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.90f,
        animationSpec = KuroMotion.appleFluidSpring(),
        label = "mini_art_breathing"
    )
    val solidContainerColor = if (colors.isDark) Color(0xFF0A0A0A) else Color(0xFFFFFFFF)
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = (animatedOffsetY * 0.35f).coerceIn(-28f, 20f)
            }
            .shadow(elevation = 12.dp, shape = shape, clip = false)
            .clip(shape)
            .background(solidContainerColor)
            .border(1.dp, colors.border, shape)
            .clickable { onClick() }
            .pointerInput(track.id, isPlaying) {
                detectDragGestures(
                    onDragStart = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                        lockedAxis = 0
                    },
                    onDragEnd = {
                        if (lockedAxis == 2) {
                            if (dragOffsetY < -44f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                lockedAxis = 0
                                onClick()
                            } else if (dragOffsetY > 48f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                lockedAxis = 0
                                if (onSwipeDown != null) {
                                    onSwipeDown()
                                } else if (isPlaying) {
                                    onPlayPauseClick()
                                }
                            } else {
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                lockedAxis = 0
                            }
                        } else {
                            if (dragOffsetX < -95f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                lockedAxis = 0
                                onSkipNextClick()
                            } else if (dragOffsetX > 95f && onSkipPreviousClick != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                lockedAxis = 0
                                onSkipPreviousClick()
                            } else {
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                lockedAxis = 0
                            }
                        }
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                        lockedAxis = 0
                    },
                    onDrag = { change, dragAmount ->
                        if (lockedAxis == 0) {
                            val nextX = dragOffsetX + dragAmount.x
                            val nextY = dragOffsetY + dragAmount.y
                            if (abs(nextX) > 10f || abs(nextY) > 10f) {
                                lockedAxis = if (abs(nextY) > abs(nextX) * 1.15f) 2 else 1
                            }
                        }
                        if (lockedAxis == 2) {
                            dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-140f, 120f)
                            change.consume()
                        } else {
                            dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-180f, 180f)
                            change.consume()
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .graphicsLayer {
                    translationX = animatedOffsetX * 0.35f
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        (fadeIn(tween(240, easing = KuroMotion.EmphasizedDecelerate)) +
                            scaleIn(initialScale = 0.92f, animationSpec = tween(240, easing = KuroMotion.EmphasizedDecelerate)))
                            .togetherWith(fadeOut(tween(180, easing = KuroMotion.EmphasizedAccelerate)))
                    },
                    label = "mini_artwork"
                ) { animatedTrack ->
                    AsyncImage(
                        model = animatedTrack.thumbnailUrlForDisplay(160),
                        contentDescription = animatedTrack.title,
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = miniArtScale
                                scaleY = miniArtScale
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.card),
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
                    transitionSpec = {
                        fadeIn(tween(220, easing = KuroMotion.EmphasizedDecelerate))
                            .togetherWith(fadeOut(tween(160, easing = KuroMotion.EmphasizedAccelerate)))
                    },
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
                    animationSpec = tween(durationMillis = 160, easing = KuroMotion.StandardEasing),
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
                .height(2.5.dp)
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
