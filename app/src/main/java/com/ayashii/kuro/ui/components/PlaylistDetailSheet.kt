package com.ayashii.kuro.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.PlaylistBrowseDetails
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.PureWhite
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.ui.theme.rememberDynamicDominantColor
import com.ayashii.kuro.viewmodel.MusicViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailSheet(
    details: PlaylistBrowseDetails,
    isLoading: Boolean,
    currentTrackId: String?,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onTrackMoreClick: (Track) -> Unit
) {
    val colors = KuroAppTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isSavedToLibrary by remember(details.title) { mutableStateOf(false) }
    var isBatchDownloading by remember(details.title) { mutableStateOf(false) }

    BackHandler {
        onDismiss()
    }

    val heroThumb = details.thumbnailUrl.ifBlank {
        details.tracks.firstOrNull()?.thumbnailUrlForDisplay(512).orEmpty()
    }
    val dominant = rememberDynamicDominantColor(heroThumb, colors.card)
    val topGlow = remember(dominant, colors.isDark, colors.background) {
        androidx.compose.ui.graphics.lerp(
            colors.background,
            dominant,
            if (colors.isDark) 0.34f else 0.16f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .background(
                Brush.verticalGradient(
                    0.0f to topGlow,
                    0.38f to colors.background,
                    1.0f to colors.background
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = details.title,
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = {
                        val shareId = details.playlistId ?: details.browseId?.removePrefix("VL")
                        val shareUrl = if (!shareId.isNullOrBlank()) {
                            "https://music.youtube.com/playlist?list=$shareId"
                        } else {
                            details.tracks.firstOrNull()?.url.orEmpty()
                        }
                        if (shareUrl.isNotBlank()) {
                            runCatching {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, "${details.title} - $shareUrl")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, details.title))
                            }
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                item(key = "playlist_detail_hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(196.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.card)
                                .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (heroThumb.isNotBlank()) {
                                AsyncImage(
                                    model = heroThumb,
                                    contentDescription = details.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = details.title,
                            style = Typography.headlineMedium.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val metaText = buildString {
                            if (details.subtitle.isNotBlank()) {
                                append(details.subtitle)
                            }
                            if (details.tracks.isNotEmpty()) {
                                if (isNotEmpty()) append(" • ")
                                append("${details.tracks.size} songs")
                            }
                        }
                        if (metaText.isNotBlank()) {
                            Text(
                                text = metaText,
                                style = Typography.bodySmall.copy(fontSize = 13.sp),
                                color = colors.textMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (details.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = details.description,
                                style = Typography.bodySmall.copy(fontSize = 12.sp),
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.card)
                                        .border(1.dp, colors.border, CircleShape)
                                        .clickable(enabled = details.tracks.isNotEmpty()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            isBatchDownloading = true
                                            details.tracks.take(25).forEach { t ->
                                                viewModel.downloadTrack(t)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isBatchDownloading) Icons.Default.Check else Icons.Outlined.Download,
                                        contentDescription = "Download Mix",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (isSavedToLibrary) colors.accent else colors.card)
                                        .border(1.dp, colors.border, CircleShape)
                                        .clickable(enabled = details.tracks.isNotEmpty() && !isSavedToLibrary) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.saveBrowseDetailsAsPlaylist(details)
                                            isSavedToLibrary = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSavedToLibrary) Icons.Default.Check else Icons.Outlined.LibraryAdd,
                                        contentDescription = "Save to Library",
                                        tint = if (isSavedToLibrary) colors.background else colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.card)
                                        .border(1.dp, colors.border, CircleShape)
                                        .clickable(enabled = details.tracks.isNotEmpty()) {
                                            if (details.tracks.isNotEmpty()) {
                                                val shuffled = details.tracks.shuffled()
                                                viewModel.playTrack(shuffled.first(), shuffled)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(23.dp))
                                    .background(colors.accent)
                                    .clickable(enabled = details.tracks.isNotEmpty()) {
                                        if (details.tracks.isNotEmpty()) {
                                            viewModel.playTrack(details.tracks.first(), details.tracks)
                                        }
                                    }
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = colors.background,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Play",
                                    style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.background
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                if (isLoading && details.tracks.isEmpty()) {
                    item(key = "playlist_detail_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = colors.textPrimary,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else if (details.tracks.isEmpty()) {
                    item(key = "playlist_detail_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tracks found in this mix.",
                                style = Typography.bodyMedium,
                                color = colors.textMuted
                            )
                        }
                    }
                } else {
                    itemsIndexed(details.tracks, key = { idx, t -> "${idx}_${t.id}" }) { _, track ->
                        val isPlaying = currentTrackId == track.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (track.isCollection || track.isArtistProfile) {
                                            viewModel.openBrowseItemDetails(track)
                                        } else {
                                            val playable = details.tracks.filter { !it.isCollection && !it.isArtistProfile }
                                            viewModel.playTrack(track, playable.ifEmpty { listOf(track) })
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTrackMoreClick(track)
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.card),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = track.thumbnailUrlForDisplay(160),
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(PureBlack.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_kuro_play),
                                            contentDescription = "Playing",
                                            tint = PureWhite,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = Typography.titleSmall.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (isPlaying) colors.accent else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val sub = if (track.durationSeconds > 0) {
                                    "${track.artist} • ${track.formattedDuration}"
                                } else {
                                    track.artist
                                }
                                Text(
                                    text = sub,
                                    style = Typography.bodySmall.copy(fontSize = 12.sp),
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onTrackMoreClick(track) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
