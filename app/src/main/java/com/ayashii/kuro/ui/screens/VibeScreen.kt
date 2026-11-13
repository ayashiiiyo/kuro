package com.ayashii.kuro.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.components.AddToPlaylistDialog
import com.ayashii.kuro.ui.components.ListeningStatsDiagram
import com.ayashii.kuro.ui.components.QueueBottomSheet
import com.ayashii.kuro.ui.components.SettingsDialog
import com.ayashii.kuro.ui.components.TrackItem
import com.ayashii.kuro.ui.components.TrackOptionsBottomSheet
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.ui.theme.rememberDynamicDominantColor
import com.ayashii.kuro.viewmodel.MusicViewModel

@Composable
fun VibeScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val colors = KuroAppTheme.colors
    val onRepeatTracks by viewModel.onRepeatTracks.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val genreTracks by viewModel.genreTracks.collectAsState()

    val favoriteIds by remember(favorites) {
        derivedStateOf { favorites.mapTo(HashSet()) { it.id } }
    }

    var selectedMoodIndex by rememberSaveable { mutableStateOf(0) }
    var trackForOptions by remember { mutableStateOf<Track?>(null) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }

    val moodLabels = listOf(
        stringResource(R.string.vibe_mood_all) to "",
        stringResource(R.string.vibe_mood_repeat) to "repeat",
        stringResource(R.string.vibe_mood_chill) to "Lo-Fi Chill Night",
        stringResource(R.string.vibe_mood_focus) to "Deep Focus Instrumental",
        stringResource(R.string.vibe_mood_energy) to "Upbeat Energy Hits"
    )

    val seedArtwork = currentTrack?.thumbnailUrlForDisplay(512)
        ?: onRepeatTracks.firstOrNull()?.thumbnailUrlForDisplay(512)
        ?: favorites.firstOrNull()?.thumbnailUrlForDisplay(512)
    val dominantColor = rememberDynamicDominantColor(seedArtwork)

    val candidatePool = remember(selectedMoodIndex, onRepeatTracks, recentlyPlayed, favorites, trendingTracks, genreTracks) {
        val base = when (selectedMoodIndex) {
            1 -> onRepeatTracks.ifEmpty { favorites }.ifEmpty { recentlyPlayed }
            2, 3, 4 -> genreTracks.ifEmpty { (onRepeatTracks + recentlyPlayed + trendingTracks).distinctBy { it.id } }
            else -> (onRepeatTracks + favorites + recentlyPlayed + trendingTracks).distinctBy { it.id }
        }
        viewModel.tasteManager.rankRadioTracks(base).ifEmpty { trendingTracks }
    }

    val totalPlays = remember(recentlyPlayed, onRepeatTracks) { viewModel.tasteManager.getTotalPlays() }
    val uniqueArtists = remember(recentlyPlayed, onRepeatTracks) { viewModel.tasteManager.getUniqueArtistsCount() }
    val topArtists = remember(recentlyPlayed, onRepeatTracks) { viewModel.tasteManager.getTopArtists(5) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(top = 18.dp, bottom = 140.dp)
    ) {
        item(key = "vibe_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.vibe_title),
                        style = Typography.headlineLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.vibe_subtitle),
                    style = Typography.bodyMedium,
                    color = colors.textMuted
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        item(key = "vibe_mood_chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(moodLabels.indices.toList()) { idx ->
                    val (label, query) = moodLabels[idx]
                    val isSelected = selectedMoodIndex == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) colors.accent else colors.card)
                            .border(
                                1.dp,
                                if (isSelected) colors.accent else colors.border,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedMoodIndex = idx
                                if (query.isNotEmpty() && query != "repeat") {
                                    viewModel.loadGenreTracks(query)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = Typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) colors.background else colors.textPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item(key = "vibe_hero_card") {
            val heroGradient = remember(dominantColor, colors.card, colors.isDark) {
                Brush.linearGradient(
                    listOf(
                        androidx.compose.ui.graphics.lerp(colors.card, dominantColor, if (colors.isDark) 0.36f else 0.18f),
                        colors.card
                    )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(heroGradient)
                    .border(
                        1.dp,
                        androidx.compose.ui.graphics.lerp(colors.border, dominantColor, 0.4f),
                        RoundedCornerShape(22.dp)
                    )
                    .clickable {
                        if (candidatePool.isNotEmpty()) {
                            viewModel.playTrack(candidatePool.first(), candidatePool)
                        }
                    }
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.background.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GraphicEq,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.vibe_engine_badge),
                                style = Typography.labelSmall.copy(fontSize = 10.sp),
                                color = colors.textPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                                .clickable {
                                    if (candidatePool.isNotEmpty()) {
                                        val shuffled = candidatePool.shuffled()
                                        viewModel.playTrack(shuffled.first(), shuffled)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle Vibe",
                                tint = colors.background,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.vibe_start_mix),
                        style = Typography.headlineMedium.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.vibe_start_mix_sub),
                        style = Typography.bodySmall,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.accent)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = colors.background,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.vibe_tracks_ready, candidatePool.size),
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.background
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        item(key = "vibe_stats_card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                ListeningStatsDiagram(
                    totalPlays = totalPlays,
                    uniqueArtists = uniqueArtists,
                    topArtists = topArtists
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (candidatePool.isNotEmpty()) {
            item(key = "vibe_tracks_header") {
                Text(
                    text = stringResource(R.string.vibe_heavy_rotation),
                    style = Typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            items(candidatePool.take(15), key = { "vibe_track_${it.id}" }) { track ->
                val isFav = track.id in favoriteIds
                TrackItem(
                    track = track.copy(isFavorite = isFav),
                    isPlaying = currentTrack?.id == track.id,
                    isLoading = isLoadingTrack && currentTrack?.id == track.id,
                    onClick = { viewModel.playTrack(track, candidatePool) },
                    onFavoriteClick = { viewModel.toggleFavorite(track) },
                    onMoreClick = { trackForOptions = track }
                )
            }
        }
    }

    if (trackForOptions != null) {
        TrackOptionsBottomSheet(
            track = trackForOptions!!,
            viewModel = viewModel,
            onDismiss = { trackForOptions = null },
            onOpenLyrics = {
                trackForOptions = null
                viewModel.expandPlayer(true)
            },
            onOpenQueue = {
                trackForOptions = null
                showQueueSheet = true
            },
            onOpenSettings = {
                trackForOptions = null
                showSettingsDialog = true
            },
            onAddToPlaylist = {
                val t = trackForOptions
                trackForOptions = null
                trackForPlaylist = t
            }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            viewModel = viewModel,
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (trackForPlaylist != null) {
        AddToPlaylistDialog(
            track = trackForPlaylist!!,
            viewModel = viewModel,
            onDismiss = { trackForPlaylist = null }
        )
    }
}
