package com.ayashii.kuro.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ayashii.kuro.R
import com.ayashii.kuro.data.auth.UserAccount
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.model.HomeSection
import com.ayashii.kuro.ui.components.AddToPlaylistDialog
import com.ayashii.kuro.ui.components.LoginWebViewDialog
import com.ayashii.kuro.ui.components.ProfileMenuSheet
import com.ayashii.kuro.ui.components.TrackItem
import com.ayashii.kuro.ui.theme.DarkBorder
import com.ayashii.kuro.ui.theme.DarkCard
import com.ayashii.kuro.ui.theme.MutedText
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.PureWhite
import com.ayashii.kuro.ui.theme.SubtleGray
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val homeSections by viewModel.homeSections.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentAccount by viewModel.authState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    var showProfileMenu by remember { mutableStateOf(false) }
    var showLoginWebView by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }

    var selectedGenre by remember { mutableStateOf("Semua") }
    val genres = listOf("Semua", "Pop", "Lo-Fi", "Rock", "Acoustic", "Anime", "Hip-Hop", "R&B", "Top Global")

    val colors = com.ayashii.kuro.ui.theme.KuroAppTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 16.dp)
    ) {
        // Spotify-Style Top Header: Avatar on the LEFT, and Filter Chips right beside it!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountTopButton(
                account = currentAccount,
                onClick = { showProfileMenu = true }
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Scrollable Spotify-Style Filter Chips (Pills)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(genres) { genre ->
                    val isSelected = selectedGenre == genre
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) colors.textPrimary else colors.card)
                            .border(1.dp, if (isSelected) colors.textPrimary else colors.border, RoundedCornerShape(20.dp))
                            .clickable {
                                selectedGenre = genre
                                if (genre == "Semua") {
                                    viewModel.loadTrending()
                                } else {
                                    viewModel.search("$genre music hits")
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = genre,
                            style = Typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                            fontSize = 13.sp,
                            color = if (isSelected) colors.background else colors.textPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedGenre != "Semua") {
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.textPrimary)
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak ada lagu ditemukan untuk $selectedGenre",
                        style = Typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(searchResults, key = { it.id }, contentType = { "track" }) { track ->
                        val isFav = favorites.any { it.id == track.id }
                        TrackItem(
                            track = track.copy(isFavorite = isFav),
                            isPlaying = currentTrack?.id == track.id,
                            isLoading = isLoadingTrack && currentTrack?.id == track.id,
                            onClick = { viewModel.playTrackFromSearch(track) },
                            onFavoriteClick = { viewModel.toggleFavorite(track) },
                            onMoreClick = { trackForPlaylist = track }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        } else if (isLoadingTrending) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.textPrimary)
            }
        } else if (homeSections.isNotEmpty()) {
            // YouTube Music Style Shelves (Carousel Rows!)
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                homeSections.forEachIndexed { sIndex, section ->
                    item(key = "header_${sIndex}_${section.title}") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = section.title,
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = colors.textPrimary
                            )
                            if (section.subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = section.subtitle,
                                    style = Typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                        }
                    }

                    item(key = "shelf_${sIndex}_${section.title}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(section.tracks, key = { tIndex, track -> "shelf_${sIndex}_${track.id}_$tIndex" }) { _, track ->
                                YtmMusicCard(
                                    track = track,
                                    isPlaying = currentTrack?.id == track.id,
                                    onClick = { viewModel.playTrack(track, section.tracks) },
                                    onLongClick = { trackForPlaylist = track }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(22.dp))
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(trendingTracks, key = { it.id }, contentType = { "track" }) { track ->
                    val isFav = favorites.any { it.id == track.id }
                    TrackItem(
                        track = track.copy(isFavorite = isFav),
                        isPlaying = currentTrack?.id == track.id,
                        isLoading = isLoadingTrack && currentTrack?.id == track.id,
                        onClick = { viewModel.playTrack(track, trendingTracks) },
                        onFavoriteClick = { viewModel.toggleFavorite(track) },
                        onMoreClick = { trackForPlaylist = track }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    if (showProfileMenu) {
        ProfileMenuSheet(
            account = currentAccount,
            viewModel = viewModel,
            onLoginClick = { showLoginWebView = true },
            onLogoutClick = { viewModel.logout() },
            onOpenSettingsClick = { showSettingsDialog = true },
            onDismiss = { showProfileMenu = false }
        )
    }

    if (showLoginWebView) {
        LoginWebViewDialog(
            onDismiss = { showLoginWebView = false },
            onLoginSuccess = { showProfileMenu = true }
        )
    }

    if (showSettingsDialog) {
        com.ayashii.kuro.ui.components.SettingsDialog(
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

@Composable
fun AccountTopButton(
    account: UserAccount?,
    onClick: () -> Unit
) {
    val colors = com.ayashii.kuro.ui.theme.KuroAppTheme.colors

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, if (account != null) colors.textPrimary else colors.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (account != null && account.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = account.avatarUrl,
                contentDescription = "Account Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else if (account != null) {
            Text(
                text = account.name.take(1).uppercase(),
                style = Typography.titleMedium,
                color = colors.textPrimary
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = "Login YouTube",
                tint = colors.textSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YtmMusicCard(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = com.ayashii.kuro.ui.theme.KuroAppTheme.colors

    Column(
        modifier = modifier
            .width(144.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.card)
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
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
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (track.durationSeconds > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PureBlack.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = track.formattedDuration,
                        style = Typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = PureWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist,
            style = Typography.bodySmall,
            fontWeight = FontWeight.Normal,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
