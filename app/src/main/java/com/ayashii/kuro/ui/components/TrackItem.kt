package com.ayashii.kuro.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.KuroMotion
import com.ayashii.kuro.ui.theme.Typography
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackItem(
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    onSwipeAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = KuroAppTheme.colors
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val imageModel = remember(track.id, track.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(track.thumbnailUrlForDisplay(160))
            .crossfade(false)
            .build()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) colors.card else Color.Transparent)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (onMoreClick != null) {
                        onMoreClick()
                    } else if (onSwipeAddToQueue != null) {
                        onSwipeAddToQueue()
                    } else {
                        onFavoriteClick()
                    }
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = track.title,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.card),
                contentScale = ContentScale.Crop
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else if (isPlaying) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerAnimation()
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = track.artist,
                style = Typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = track.formattedDuration,
            style = Typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(end = 4.dp)
        )

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = androidx.compose.material.ripple.rememberRipple(bounded = false, radius = 20.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFavoriteClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val favTint by animateColorAsState(
                targetValue = if (track.isFavorite) Color(0xFFFF3B30) else colors.textMuted,
                animationSpec = tween(durationMillis = 160),
                label = "track_fav_tint"
            )
            AnimatedContent(
                targetState = track.isFavorite,
                transitionSpec = {
                    (scaleIn(initialScale = 0.6f, animationSpec = KuroMotion.appleSnappySpring()) + fadeIn(tween(140)))
                        .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(110)) + fadeOut(tween(110)))
                },
                label = "track_fav_icon"
            ) { isFav ->
                Icon(
                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = favTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (onMoreClick != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = androidx.compose.material.ripple.rememberRipple(bounded = false, radius = 18.dp),
                        onClick = onMoreClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EqualizerAnimation() {
    val colors = KuroAppTheme.colors
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isForeground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val phase = remember { Animatable(0f) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            isForeground = event.targetState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isForeground) {
        if (isForeground) {
            while (true) {
                phase.animateTo(1f, animationSpec = tween(durationMillis = 900))
                phase.snapTo(0f)
            }
        } else {
            phase.snapTo(0f)
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(18.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .graphicsLayer {
                    scaleY = 0.2f + phase.value * 0.7f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .background(colors.accent, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .graphicsLayer {
                    scaleY = 0.9f - phase.value * 0.6f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .background(colors.accent, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .graphicsLayer {
                    scaleY = 0.4f + abs(sin(phase.value * PI)).toFloat() * 0.6f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .background(colors.accent, RoundedCornerShape(1.dp))
        )
    }
}
