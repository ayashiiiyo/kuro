package com.ayashii.kuro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.ayashii.kuro.ui.theme.KuroMotion
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.PureWhite
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

private fun formatMixBadgeLabel(artist: String): String {
    val clean = artist.trim().ifBlank { "Kuro" }
    val primary = clean.split(",", "&", " feat.", " ft.", " x ", " X ").firstOrNull()?.trim().orEmpty().ifBlank { clean }
    return if (primary.length <= 16) {
        "$primary Mix"
    } else {
        val cutAtSpace = primary.take(15).substringBeforeLast(' ', "")
        val base = if (cutAtSpace.length >= 4) cutAtSpace else primary.take(14).trim()
        "$base Mix"
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val homeSections by viewModel.homeSections.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val genreTracks by viewModel.genreTracks.collectAsState()
    val isLoadingGenre by viewModel.isLoadingGenre.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val isRefreshingHome by viewModel.isRefreshingHome.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()
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

    val haptic = LocalHapticFeedback.current
    var selectedGenre by rememberSaveable { mutableStateOf("Semua") }
    val genreChips = listOf(
        "Semua" to stringResource(R.string.home_genre_all),
        "Feel good" to stringResource(R.string.home_genre_feel_good),
        "Relax" to stringResource(R.string.home_genre_relax),
        "Sad" to stringResource(R.string.home_genre_sad),
        "Energize" to stringResource(R.string.home_genre_energize),
        "Romance" to stringResource(R.string.home_genre_romance),
        "Focus" to stringResource(R.string.home_genre_focus),
        "Workout" to stringResource(R.string.home_genre_workout)
    )
    val genreRowState = rememberLazyListState()
    val mainListState = rememberLazyListState()
    val genreListState = rememberLazyListState()

    BackHandler(enabled = !isPlayerExpanded && selectedGenre != "Semua") {
        selectedGenre = "Semua"
        viewModel.loadTrending()
    }

    val colors = KuroAppTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to colors.card.copy(alpha = 0.65f),
                    0.18f to colors.background,
                    1.0f to colors.background
                )
            )
            .padding(top = 12.dp)
    ) {
        AnimatedVisibility(
            visible = selectedGenre != "Semua",
            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
        ) {
            HomeTopBar(
                currentAccount = currentAccount,
                onRecentsClick = { showRecentsDialog = true },
                onProfileClick = { showProfileMenu = true }
            )
        }

        LazyRow(
            state = genreRowState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(genreChips, key = { it.first }) { (genreKey, genreLabel) ->
                val isSelected = selectedGenre == genreKey
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.accent else colors.card.copy(alpha = 0.85f))
                        .border(
                            1.dp,
                            if (isSelected) colors.accent else colors.border.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (genreKey == "Semua") {
                                if (selectedGenre != "Semua") {
                                    selectedGenre = "Semua"
                                    if (homeSections.isEmpty() && trendingTracks.isEmpty()) {
                                        viewModel.loadTrending()
                                    }
                                }
                            } else if (isSelected) {
                                selectedGenre = "Semua"
                                if (homeSections.isEmpty() && trendingTracks.isEmpty()) {
                                    viewModel.loadTrending()
                                }
                            } else {
                                selectedGenre = genreKey
                                viewModel.loadGenreTracks(genreKey)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = genreLabel,
                        style = Typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) colors.background else colors.textPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        LaunchedEffect(pullRefreshState.isRefreshing) {
            if (pullRefreshState.isRefreshing) {
                if (selectedGenre == "Semua") {
                    viewModel.refreshHome()
                } else {
                    viewModel.loadGenreTracks(selectedGenre)
                }
            }
        }
        LaunchedEffect(isRefreshingHome, isLoadingGenre) {
            if (!isRefreshingHome && !isLoadingGenre && pullRefreshState.isRefreshing) {
                pullRefreshState.endRefresh()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
        AnimatedContent(
            targetState = selectedGenre,
            transitionSpec = {
                (fadeIn(tween(240, easing = KuroMotion.EmphasizedDecelerate)) +
                    slideInVertically(animationSpec = tween(240, easing = KuroMotion.EmphasizedDecelerate)) { it / 20 })
                    .togetherWith(fadeOut(tween(160, easing = KuroMotion.EmphasizedAccelerate)))
            },
            label = "home_genre_transition",
            modifier = Modifier.fillMaxSize()
        ) { activeGenre ->
            if (activeGenre != "Semua") {
                val activeGenreLabel = genreChips.firstOrNull { it.first == activeGenre }?.second ?: activeGenre
                if (isLoadingGenre && genreTracks.isEmpty()) {
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
                            text = stringResource(R.string.home_no_songs_genre, activeGenreLabel),
                            style = Typography.bodyMedium,
                            color = colors.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        state = genreListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp)
                    ) {
                        items(genreTracks, key = { it.id }, contentType = { "track" }) { track ->
                            val isFav = track.id in favoriteIds
                            TrackItem(
                                track = track.copy(isFavorite = isFav),
                                isPlaying = currentTrack?.id == track.id,
                                isLoading = isLoadingTrack && currentTrack?.id == track.id,
                                onClick = { viewModel.playTrack(track, genreTracks) },
                                onFavoriteClick = { viewModel.toggleFavorite(track) },
                                onMoreClick = { trackForOptions = track },
                                onSwipeAddToQueue = { viewModel.addToQueue(track) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(130.dp))
                        }
                    }
                }
            } else if (isLoadingTrending && homeSections.isEmpty() && trendingTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.textPrimary)
                }
            } else if (homeSections.isNotEmpty() || trendingTracks.isNotEmpty()) {
                LazyColumn(
                    state = mainListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp)
                ) {
                    item(key = "home_top_header") {
                        HomeTopBar(
                            currentAccount = currentAccount,
                            onRecentsClick = { showRecentsDialog = true },
                            onProfileClick = { showProfileMenu = true }
                        )
                    }
                    val homeTracks = trendingTracks.ifEmpty { homeSections.flatMap { it.tracks } }.distinctBy { it.id }
                    val quickPickTracks = (homeSections.firstOrNull()?.tracks ?: homeTracks).distinctBy { it.id }.take(16)
                    val quickPickCols = quickPickTracks.chunked(4)

                    if (quickPickCols.isNotEmpty()) {
                        item(key = "quick_picks_header") {
                            HomeSectionHeader(
                                title = homeSections.firstOrNull()?.title ?: stringResource(R.string.home_quick_picks_title),
                                actionText = stringResource(R.string.home_play_all),
                                onActionClick = {
                                    quickPickTracks.firstOrNull()?.let { viewModel.playTrack(it, quickPickTracks) }
                                }
                            )
                        }
                        item(key = "quick_picks_pager") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(quickPickCols.size) { colIdx ->
                                    val colTracks = quickPickCols[colIdx]
                                    Column(modifier = Modifier.width(308.dp)) {
                                        colTracks.forEach { track ->
                                            HomeCompactTrackRow(
                                                track = track,
                                                isPlaying = currentTrack?.id == track.id,
                                                onClick = { viewModel.playTrack(track, quickPickTracks) },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    trackForOptions = track
                                                },
                                                onMoreClick = { trackForOptions = track }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val usedIds = quickPickTracks.mapTo(HashSet()) { it.id }
                    val discoverTrack = homeTracks.firstOrNull { it.id !in usedIds } ?: homeTracks.firstOrNull()
                    if (discoverTrack != null) {
                        usedIds.add(discoverTrack.id)
                        item(key = "daily_discover_header") {
                            HomeSectionHeader(
                                title = stringResource(R.string.home_daily_discover),
                                actionText = stringResource(R.string.home_play_all),
                                onActionClick = { viewModel.playTrack(discoverTrack, homeTracks) }
                            )
                        }
                        item(key = "daily_discover_card") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(310.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.card)
                                    .combinedClickable(
                                        onClick = { viewModel.playTrack(discoverTrack, homeTracks) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            trackForOptions = discoverTrack
                                        }
                                    )
                            ) {
                                AsyncImage(
                                    model = discoverTrack.highResThumbnailUrl,
                                    contentDescription = discoverTrack.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                0.0f to Color.Black.copy(alpha = 0.72f),
                                                0.35f to Color.Transparent,
                                                0.68f to Color.Transparent,
                                                1.0f to Color.Black.copy(alpha = 0.82f)
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 16.dp, top = 16.dp, end = 52.dp)
                                ) {
                                    Text(
                                        text = discoverTrack.title,
                                        style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = discoverTrack.artist,
                                        style = Typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.82f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { trackForOptions = discoverTrack },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = formatMixBadgeLabel(discoverTrack.artist),
                                    style = Typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.88f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                )
                            }
                        }
                    }

                    val remainingSections = homeSections.drop(1)
                    remainingSections.forEachIndexed { idx, section ->
                        if (section.tracks.isNotEmpty()) {
                            item(key = "sec_header_${idx}_${section.title}") {
                                HomeSectionHeader(
                                    title = section.title,
                                    actionText = stringResource(R.string.home_play_all),
                                    onActionClick = {
                                        section.tracks.firstOrNull()?.let { viewModel.playTrack(it, section.tracks) }
                                    }
                                )
                            }
                            if (idx % 2 == 1 && section.tracks.size >= 4) {
                                val secCols = section.tracks.take(12).chunked(4)
                                item(key = "sec_cols_${idx}_${section.title}") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(secCols.size) { cIdx ->
                                            val cTracks = secCols[cIdx]
                                            Column(modifier = Modifier.width(308.dp)) {
                                                cTracks.forEach { track ->
                                                    HomeCompactTrackRow(
                                                        track = track,
                                                        isPlaying = currentTrack?.id == track.id,
                                                        onClick = { viewModel.playTrack(track, section.tracks) },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            trackForOptions = track
                                                        },
                                                        onMoreClick = { trackForOptions = track }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                item(key = "sec_carousel_${idx}_${section.title}") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(section.tracks, key = { "sec_${idx}_${it.id}" }) { track ->
                                            YtmMusicCard(
                                                track = track,
                                                isPlaying = currentTrack?.id == track.id,
                                                onClick = { viewModel.playTrack(track, section.tracks) },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    trackForOptions = track
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(130.dp))
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.home_empty),
                        style = Typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
            }
        }
        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = colors.card,
            contentColor = colors.textPrimary
        )
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
                                        viewModel.playTrack(track, recentlyPlayed)
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
        val targetTrack = trackForOptions!!
        TrackOptionsBottomSheet(
            track = targetTrack.copy(isFavorite = targetTrack.id in favoriteIds),
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
private fun HomeTopBar(
    currentAccount: UserAccount?,
    onRecentsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_notification),
                contentDescription = "Kuro",
                tint = colors.textPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Kuro",
                style = Typography.headlineMedium.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.textPrimary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onRecentsClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Activity",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            AccountTopButton(
                account = currentAccount,
                onClick = onProfileClick
            )
        }
    }
}

@Composable
fun AccountTopButton(
    account: UserAccount?,
    onClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    var avatarLoadFailed by remember(account?.avatarUrl) { mutableStateOf(false) }
    val showPhoto = account != null && account.avatarUrl.isNotBlank() && !avatarLoadFailed
    val initial = account?.name
        ?.takeIf { it.isNotBlank() && it != "YouTube User" }
        ?.trim()
        ?.take(1)
        ?.uppercase()
        ?: account?.email?.trim()?.take(1)?.uppercase()
        ?: "K"

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (!showPhoto) colors.accent else colors.card)
            .border(1.dp, colors.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (showPhoto) {
            AsyncImage(
                model = account!!.avatarUrl,
                contentDescription = "Account Profile",
                contentScale = ContentScale.Crop,
                onError = { avatarLoadFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
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

@Composable
private fun HomeSectionHeader(
    title: String,
    actionText: String,
    onActionClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = Typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .clickable(onClick = onActionClick)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = actionText,
                style = Typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeCompactTrackRow(
    track: Track,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
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
                style = Typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = if (isPlaying) colors.accent else colors.textPrimary,
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
            onClick = onMoreClick,
            modifier = Modifier.size(32.dp)
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
