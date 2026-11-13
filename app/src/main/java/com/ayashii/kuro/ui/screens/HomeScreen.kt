package com.ayashii.kuro.ui.screens

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ayashii.kuro.R
import com.ayashii.kuro.data.auth.UserAccount
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.components.AddToPlaylistDialog
import com.ayashii.kuro.ui.components.LoginWebViewDialog
import com.ayashii.kuro.ui.components.ProfileMenuSheet
import com.ayashii.kuro.ui.components.QueueBottomSheet
import com.ayashii.kuro.ui.components.SettingsDialog
import com.ayashii.kuro.ui.components.TrackItem
import com.ayashii.kuro.ui.components.TrackOptionsBottomSheet
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.PureWhite
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val homeSections by viewModel.homeSections.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val genreTracks by viewModel.genreTracks.collectAsState()
    val isLoadingGenre by viewModel.isLoadingGenre.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentAccount by viewModel.authState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val favoriteIds by remember(favorites) { derivedStateOf { favorites.mapTo(HashSet()) { it.id } } }

    var showProfileMenu by rememberSaveable { mutableStateOf(false) }
    var showLoginWebView by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showRecentsDialog by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var trackForOptions by remember { mutableStateOf<Track?>(null) }

    var selectedGenre by rememberSaveable { mutableStateOf("Semua") }
    val genres = listOf("Semua", "Pop", "Lo-Fi", "Rock", "Acoustic", "Anime", "Hip-Hop", "R&B", "Top Global")

    BackHandler(enabled = selectedGenre != "Semua") {
        selectedGenre = "Semua"
        viewModel.loadTrending()
    }

    val colors = KuroAppTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccountTopButton(
                    account = currentAccount,
                    onClick = { showProfileMenu = true }
                )
                Spacer(modifier = Modifier.width(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(genres) { genre ->
                        val isSelected = selectedGenre == genre
                        val chipBg = if (isSelected) colors.accent else colors.card
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(chipBg)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accent else colors.border,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    selectedGenre = genre
                                    if (genre == "Semua") {
                                        viewModel.loadTrending()
                                    } else {
                                        viewModel.loadGenreTracks(genre)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (genre == "Semua") "All" else genre,
                                style = Typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                fontSize = 13.sp,
                                color = if (isSelected) colors.background else colors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedGenre != "Semua") {
            if (isLoadingGenre || isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.textPrimary)
                }
            } else if (genreTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.home_no_songs_genre, selectedGenre),
                        style = Typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(genreTracks, key = { it.id }, contentType = { "track" }) { track ->
                        val isFav = track.id in favoriteIds
                        TrackItem(
                            track = track.copy(isFavorite = isFav),
                            isPlaying = currentTrack?.id == track.id,
                            isLoading = isLoadingTrack && currentTrack?.id == track.id,
                            onClick = { viewModel.playTrackFromSearch(track) },
                            onFavoriteClick = { viewModel.toggleFavorite(track) },
                            onMoreClick = { trackForOptions = track }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(130.dp))
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
        } else if (homeSections.isNotEmpty() || trendingTracks.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                val homeTracks = trendingTracks.ifEmpty { homeSections.flatMap { it.tracks } }

                val startListeningTracks = (recentlyPlayed.ifEmpty { homeTracks }).distinctBy { it.id }.take(3)
                if (startListeningTracks.isNotEmpty()) {
                    item(key = "start_listening_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_start_listening_sub),
                                style = Typography.bodySmall.copy(fontSize = 12.sp),
                                color = colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.home_start_listening),
                                style = Typography.headlineMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                        }
                    }

                    items(startListeningTracks, key = { "start_listen_${it.id}" }) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playTrack(track, startListeningTracks) }
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrlForDisplay(192),
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.card)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
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
                            IconButton(
                                onClick = { trackForOptions = track },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                val mixTracks = homeTracks.drop(3).take(8)
                if (mixTracks.isNotEmpty()) {
                    item(key = "to_get_you_started_header") {
                        Text(
                            text = stringResource(R.string.home_to_get_you_started),
                            style = Typography.headlineMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
                        )
                    }
                    item(key = "to_get_you_started_carousel") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(mixTracks, key = { "mix_${it.id}" }) { track ->
                                Column(
                                    modifier = Modifier
                                        .width(152.dp)
                                        .clickable { viewModel.playTrack(track, mixTracks) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(152.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.card)
                                    ) {
                                        AsyncImage(
                                            model = track.thumbnailUrlForDisplay(512),
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(PureBlack.copy(alpha = 0.78f))
                                                .border(1.dp, PureWhite.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "${track.artist.take(12)} Mix",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PureWhite,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${track.title} • ${track.artist}",
                                        style = Typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                        color = colors.textMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                val quickPicks = (recentlyPlayed.ifEmpty { homeTracks }).distinctBy { it.id }.take(4)
                if (quickPicks.isNotEmpty()) {
                    item(key = "quick_picks_title") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.home_play_again), style = Typography.titleLarge, color = colors.textPrimary)
                            Text(
                                text = stringResource(R.string.home_view_all),
                                style = Typography.bodySmall,
                                color = colors.textMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showRecentsDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                    item(key = "quick_picks_grid") {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickPicks.chunked(2).forEach { rowTracks ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowTracks.forEach { track ->
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(62.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.card)
                                                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                                .clickable { viewModel.playTrack(track, quickPicks) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = track.thumbnailUrlForDisplay(192),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(62.dp)
                                            )
                                            Text(
                                                text = track.title,
                                                style = Typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 9.dp)
                                            )
                                        }
                                    }
                                    if (rowTracks.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                }

                val featuredTrack = homeSections.firstNotNullOfOrNull { it.tracks.firstOrNull() } ?: homeTracks.firstOrNull()
                if (featuredTrack != null) {
                    item(key = "featured_title") {
                        Text(
                            text = stringResource(R.string.home_picks_for_you),
                            style = Typography.titleLarge,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                        )
                    }
                    item(key = "featured_card") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .height(142.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.playTrack(featuredTrack, homeSections.firstOrNull { it.tracks.contains(featuredTrack) }?.tracks ?: homeTracks) }
                        ) {
                            AsyncImage(
                                model = featuredTrack.thumbnailUrlForDisplay(640),
                                contentDescription = featuredTrack.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.horizontalGradient(listOf(PureBlack.copy(alpha = 0.82f), PureBlack.copy(alpha = 0.08f))))
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(15.dp)
                                    .fillMaxWidth(0.72f)
                            ) {
                                Text(stringResource(R.string.home_mix_personal), style = Typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.2.sp), color = PureWhite.copy(alpha = 0.72f))
                                Text(featuredTrack.title, style = Typography.titleLarge, color = PureWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(featuredTrack.artist, style = Typography.bodySmall, color = PureWhite.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(15.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(PureWhite),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_kuro_play),
                                    contentDescription = "Putar ${featuredTrack.title}",
                                    tint = PureBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                }

                homeSections.forEachIndexed { sIndex, section ->
                    item(key = "header_${sIndex}_${section.title}") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
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
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
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
                    Spacer(modifier = Modifier.height(130.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(trendingTracks, key = { it.id }, contentType = { "track" }) { track ->
                    val isFav = track.id in favoriteIds
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
                    Spacer(modifier = Modifier.height(130.dp))
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
        SettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showRecentsDialog) {
        AlertDialog(
            onDismissRequest = { showRecentsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.home_recently_played),
                        style = Typography.titleLarge,
                        color = colors.textPrimary
                    )
                }
            },
            text = {
                if (recentlyPlayed.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_no_history),
                        style = Typography.bodyMedium,
                        color = colors.textMuted
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentlyPlayed, key = { it.id }) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.card)
                                    .clickable {
                                        viewModel.playTrack(track)
                                        showRecentsDialog = false
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = track.thumbnailUrlForDisplay(192),
                                    contentDescription = track.title,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        style = Typography.bodySmall,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecentsDialog = false }) {
                    Text(stringResource(R.string.action_close), color = colors.textPrimary)
                }
            },
            containerColor = colors.background
        )
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
    val colors = KuroAppTheme.colors

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (account?.avatarUrl.isNullOrBlank()) colors.accent else colors.card)
            .border(1.dp, colors.border, CircleShape)
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
        } else {
            val initial = account?.name?.take(1)?.uppercase() ?: "K"
            Text(
                text = initial,
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = colors.background
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
    val colors = KuroAppTheme.colors

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
                model = track.thumbnailUrlForDisplay(512),
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
