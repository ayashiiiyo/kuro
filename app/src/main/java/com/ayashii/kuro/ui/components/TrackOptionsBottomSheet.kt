package com.ayashii.kuro.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.settings.AudioSubQuality
import com.ayashii.kuro.data.settings.SettingsManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsBottomSheet(
    track: Track,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onOpenLyrics: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val colors = KuroAppTheme.colors
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState()
    val lyricsLang by remember { SettingsManager.getInstance().lyricsLanguage }.collectAsState()
    val isInWatchLater = remember(playlists, track.id) {
        viewModel.isTrackInWatchLater(track.id)
    }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    val offlineTracks by viewModel.offlineTracks.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val activeProgress = downloadProgress[track.id]
    val isOffline = remember(offlineTracks, track.id) { offlineTracks.any { it.track.id == track.id } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.thumbnailUrlForDisplay(144),
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = Typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = Typography.bodySmall.copy(fontSize = 13.sp),
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Divider(color = colors.border.copy(alpha = 0.5f), thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            TrackOptionItem(
                icon = Icons.Outlined.Share,
                title = stringResource(R.string.player_share),
                onClick = {
                    onDismiss()
                    try {
                        val shareTemplate = context.getString(R.string.player_share_text, track.title, track.artist, track.url)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, shareTemplate)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.player_share_via)))
                    } catch (_: Exception) {}
                }
            )

            TrackOptionItem(
                icon = Icons.Outlined.Translate,
                title = stringResource(R.string.player_lyrics) + " • " + lyricsLang.displayName.substringBefore(" ("),
                onClick = {
                    onDismiss()
                    onOpenLyrics()
                }
            )

            TrackOptionItem(
                icon = if (track.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                title = if (track.isFavorite) stringResource(R.string.player_liked_saved) else stringResource(R.string.player_add_liked),
                iconTint = if (track.isFavorite) Color(0xFFFF3B30) else colors.textPrimary,
                onClick = {
                    viewModel.toggleFavorite(track)
                    onDismiss()
                }
            )

            TrackOptionItem(
                icon = if (isInWatchLater) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                title = if (isInWatchLater) stringResource(R.string.player_remove_watch_later) else stringResource(R.string.player_save_watch_later),
                iconTint = if (isInWatchLater) colors.accent else colors.textPrimary,
                onClick = {
                    viewModel.toggleWatchLater(track)
                    onDismiss()
                }
            )

            TrackOptionItem(
                icon = if (isOffline) Icons.Default.DownloadDone else Icons.Outlined.Download,
                title = when {
                    activeProgress != null -> "Downloading (${(activeProgress * 100).toInt()}%)"
                    isOffline -> "Hapus dari Offline"
                    else -> "Download Offline"
                },
                iconTint = if (isOffline) colors.accent else colors.textPrimary,
                onClick = {
                    if (isOffline) {
                        viewModel.deleteOfflineTrack(track.id)
                        onDismiss()
                    } else if (activeProgress == null) {
                        showDownloadQualityDialog = true
                    }
                }
            )

            TrackOptionItem(
                icon = Icons.Outlined.AddCircleOutline,
                title = stringResource(R.string.player_add_to_playlist),
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                }
            )

            if (onRemoveFromPlaylist != null) {
                TrackOptionItem(
                    icon = Icons.Default.DeleteOutline,
                    title = stringResource(R.string.lib_remove_from_playlist),
                    iconTint = Color(0xFFFF3B30),
                    onClick = {
                        onRemoveFromPlaylist()
                    }
                )
            }

            TrackOptionItem(
                icon = Icons.Outlined.QueueMusic,
                title = stringResource(R.string.player_add_to_queue),
                onClick = {
                    viewModel.addToQueue(track)
                    onDismiss()
                }
            )

            TrackOptionItem(
                icon = Icons.Outlined.FormatListBulleted,
                title = stringResource(R.string.player_go_to_queue),
                onClick = {
                    onDismiss()
                    onOpenQueue()
                }
            )

            TrackOptionItem(
                icon = Icons.Outlined.Bedtime,
                title = stringResource(R.string.sleep_timer_title),
                onClick = {
                    showSleepTimerDialog = true
                }
            )

            TrackOptionItem(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.player_audio_settings),
                onClick = {
                    onDismiss()
                    onOpenSettings()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDownloadQualityDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadQualityDialog = false },
            title = { Text("Pilih Kualitas Download", style = Typography.titleLarge, color = colors.textPrimary) },
            text = {
                Column {
                    AudioSubQuality.values().forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.downloadTrack(track, sub)
                                    showDownloadQualityDialog = false
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(sub.badge, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                Text(sub.subtitleId, style = Typography.bodySmall, color = colors.textMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadQualityDialog = false }) {
                    Text("Batal", color = colors.textMuted)
                }
            },
            containerColor = colors.surface
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            viewModel = viewModel,
            onDismiss = {
                showSleepTimerDialog = false
                onDismiss()
            }
        )
    }
}

@Composable
private fun TrackOptionItem(
    icon: ImageVector,
    title: String,
    iconTint: Color = KuroAppTheme.colors.textPrimary,
    onClick: () -> Unit
) {
    val colors = KuroAppTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(23.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = Typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary
        )
    }
}
