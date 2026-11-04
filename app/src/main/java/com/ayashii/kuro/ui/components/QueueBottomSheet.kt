package com.ayashii.kuro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = KuroAppTheme.colors
    val queueTracks by viewModel.queueState.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentIndex = viewModel.getCurrentQueueIndex()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.player_queue),
                    style = Typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (queueTracks.isEmpty() && currentTrack == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.player_queue_empty),
                        style = Typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    currentTrack?.let { nowPlaying ->
                        item {
                            Text(
                                text = stringResource(R.string.player_now_playing),
                                style = Typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            QueueItemRow(
                                track = nowPlaying,
                                isCurrent = true,
                                onClick = {}
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "BERIKUTNYA DALAM ANTREAN",
                                style = Typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    val upcomingTracks = if (currentIndex >= 0 && currentIndex + 1 < queueTracks.size) {
                        queueTracks.subList(currentIndex + 1, queueTracks.size)
                    } else if (currentIndex < 0) {
                        queueTracks.filter { it.id != currentTrack?.id }
                    } else {
                        emptyList()
                    }

                    if (upcomingTracks.isEmpty()) {
                        item {
                            Text(
                                text = "Lagu berikutnya akan dipilih secara otomatis (Radio Mix).",
                                style = Typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        itemsIndexed(upcomingTracks) { _, track ->
                            QueueItemRow(
                                track = track,
                                isCurrent = false,
                                onClick = {
                                    viewModel.playTrack(track)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val colors = KuroAppTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) colors.card else colors.background)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrlForDisplay(120),
            contentDescription = track.title,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = Typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = Typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isCurrent) {
            Icon(
                imageVector = Icons.Outlined.Equalizer,
                contentDescription = "Now Playing",
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp).padding(end = 4.dp)
            )
        }
    }
}
