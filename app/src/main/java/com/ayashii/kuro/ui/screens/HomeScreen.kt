package com.ayashii.kuro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.TrendingUp
import com.ayashii.kuro.data.model.PlaylistBrowseDetails
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ayashii.kuro.R
import com.ayashii.kuro.data.auth.UserAccount
import com.ayashii.kuro.data.model.HomeSection
import com.ayashii.kuro.data.model.HomeSectionType
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.components.AddToPlaylistDialog
import com.ayashii.kuro.ui.components.LoginWebViewDialog
import com.ayashii.kuro.ui.components.PlaylistDetailSheet
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
import com.ayashii.kuro.ui.theme.rememberDynamicDominantColor
import com.ayashii.kuro.viewmodel.MusicViewModel

/**
 * Prevents inner horizontal shelves (Quick Picks, Mix cards, Carousels) from leaking
 * leftover horizontal drag/fling deltas to the outer MainActivity HorizontalPager.
 */
@Composable
private fun rememberHorizontalShelfNestedScroll(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = available.copy(y = 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                available.copy(y = 0f)
        }
    }
}

private fun formatMixBadgeLabel(artist: String): String {
    val clean = artist.trim().ifBlank { "Kuro" }
    val primary = clean.split(",", "&", " feat.", " ft.", " x ", " X ", "•")
        .firstOrNull()?.trim().orEmpty().ifBlank { clean }
    return if (primary.length <= 16) {
        "$primary Mix"
    } else {
        val cutAtSpace = primary.take(15).substringBeforeLast(' ', "")
        val base = if (cutAtSpace.length >= 4) cutAtSpace else primary.take(14).trim()
        "$base Mix"
    }
}

private fun resolveEffectiveLayout(section: HomeSection, index: Int): HomeSectionType {
    if (section.layoutType != HomeSectionType.SQUARE_CAROUSEL && section.layoutType != HomeSectionType.SPOTLIGHT_HERO) {
        return section.layoutType
    }
    val t = section.title.lowercase()
    return when {
        "speed dial" in t -> HomeSectionType.SPEED_DIAL
        !section.headerAvatarUrl.isNullOrBlank() || "similar to" in section.subtitle.lowercase() -> HomeSectionType.SIMILAR_TO
        "quick picks" in t || "covers" in t || "trending songs" in t || "long listens" in t || "listen again" in t -> HomeSectionType.COMPACT_COLUMNS
        "forgotten" in t || "live" in t || "performances" in t || "videos" in t -> HomeSectionType.WIDE_CARDS
        index == 0 && section.tracks.size >= 4 && section.tracks.none { it.isCollection } -> HomeSectionType.COMPACT_COLUMNS
        else -> HomeSectionType.SQUARE_CAROUSEL
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onOpenSearchTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val homeSections by viewModel.homeSections.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val genreTracks by viewModel.genreTracks.collectAsState()
    val genreSections by viewModel.genreSections.collectAsState()
    val isLoadingGenre by viewModel.isLoadingGenre.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val isRefreshingHome by viewModel.isRefreshingHome.collectAsState()
    val activeBrowseDetails by viewModel.activeBrowseDetails.collectAsState()
    val isLoadingBrowseDetails by viewModel.isLoadingBrowseDetails.collectAsState()
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
        "Energize" to stringResource(R.string.home_genre_energize),
        "Relax" to stringResource(R.string.home_genre_relax),
        "Workout" to stringResource(R.string.home_genre_workout),
        "Focus" to stringResource(R.string.home_genre_focus),
        "Feel good" to stringResource(R.string.home_genre_feel_good),
        "Sad" to stringResource(R.string.home_genre_sad),
        "Romance" to stringResource(R.string.home_genre_romance)
    )
    val genreRowState = rememberLazyListState()
    val mainListState = rememberLazyListState()
    val genreListState = rememberLazyListState()
    val shelfNestedScroll = rememberHorizontalShelfNestedScroll()

    BackHandler(enabled = !isPlayerExpanded && selectedGenre != "Semua") {
        selectedGenre = "Semua"
        viewModel.loadTrending()
    }

    val colors = KuroAppTheme.colors

    val ambientImageUrl = remember(currentTrack?.id, homeSections, trendingTracks, selectedGenre) {
        currentTrack?.thumbnailUrlForDisplay(192)
            ?: homeSections.firstOrNull()?.tracks?.firstOrNull()?.thumbnailUrlForDisplay(192)
            ?: trendingTracks.firstOrNull()?.thumbnailUrlForDisplay(192)
    }
    val dominantAmbient = rememberDynamicDominantColor(
        imageUrl = ambientImageUrl,
        defaultColor = colors.card
    )
    val topAmbientTint = remember(dominantAmbient, selectedGenre, colors.isDark, colors.card) {
        val moodBoost = when (selectedGenre) {
            "Feel good" -> Color(0xFF4C3A1E)
            "Relax" -> Color(0xFF1D353A)
            "Sad" -> Color(0xFF1E263B)
            "Energize" -> Color(0xFF3F1F2B)
            "Romance" -> Color(0xFF3A1F33)
            "Focus" -> Color(0xFF202D2A)
            "Workout" -> Color(0xFF3B241E)
            else -> dominantAmbient
        }
        val blended = if (selectedGenre == "Semua") dominantAmbient else lerp(dominantAmbient, moodBoost, 0.55f)
        blended.copy(alpha = if (colors.isDark) 0.36f else 0.14f)
    }

    val onSmartItemTap: (Track, List<Track>) -> Unit = { item, sectionTracks ->
        if (item.isCollection || item.isArtistProfile) {
            viewModel.openBrowseItemDetails(item)
        } else {
            viewModel.playHomeItem(item, sectionTracks)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to topAmbientTint,
                        0.24f to colors.background,
                        1.0f to colors.background
                    )
                )
                .padding(top = 8.dp)
        ) {
            HomeTopBar(
                currentAccount = currentAccount,
                onRecentsClick = { showRecentsDialog = true },
                onSearchClick = onOpenSearchTab,
                onProfileClick = { showProfileMenu = true }
            )

            val visibleMoodChips = remember(selectedGenre, genreChips) {
                if (selectedGenre == "Semua") {
                    genreChips.filter { it.first != "Semua" }
                } else {
                    genreChips
                }
            }

            LazyRow(
                state = genreRowState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(shelfNestedScroll)
            ) {
                items(visibleMoodChips, key = { it.first }) { (genreKey, genreLabel) ->
                    val isResetChip = genreKey == "Semua"
                    val isSelected = !isResetChip && selectedGenre == genreKey
                    val chipBg = when {
                        isSelected -> colors.accent
                        colors.isDark -> PureWhite.copy(alpha = 0.10f)
                        else -> colors.card.copy(alpha = 0.9f)
                    }
                    val chipBorder = when {
                        isSelected -> colors.accent
                        colors.isDark -> PureWhite.copy(alpha = 0.14f)
                        else -> colors.border.copy(alpha = 0.6f)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                if (isResetChip || isSelected) {
                                    if (selectedGenre != "Semua") {
                                        selectedGenre = "Semua"
                                        if (homeSections.isEmpty() && trendingTracks.isEmpty()) {
                                            viewModel.loadTrending()
                                        }
                                    }
                                } else {
                                    selectedGenre = genreKey
                                    viewModel.loadGenreTracks(genreKey)
                                }
                            }
                            .padding(horizontal = if (isResetChip) 10.dp else 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isResetChip) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = genreLabel,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
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
                    .clipToBounds()
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
                        val activeGenreLabel = when (activeGenre) {
                            "FEmusic_new_releases" -> "New releases"
                            "FEmusic_charts" -> "Charts"
                            "FEmusic_moods_and_genres" -> "Moods & genres"
                            else -> genreChips.firstOrNull { it.first == activeGenre }?.second ?: activeGenre
                        }
                        if (isLoadingGenre && genreSections.isEmpty() && genreTracks.isEmpty()) {
                            HomeMonochromeSkeleton()
                        } else if (genreSections.isNotEmpty()) {
                            HomeShelvesColumn(
                                sections = genreSections,
                                listState = genreListState,
                                currentTrackId = currentTrack?.id,
                                shelfNestedScroll = shelfNestedScroll,
                                onItemTap = onSmartItemTap,
                                onPlayImmediately = { item, tracks -> viewModel.playHomeItem(item, tracks) },
                                onMoreClick = { trackForOptions = it },
                                onSelectExplore = { browseKey ->
                                    selectedGenre = browseKey
                                    viewModel.loadGenreTracks(browseKey)
                                }
                            )
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
                                        onClick = { onSmartItemTap(track, genreTracks) },
                                        onFavoriteClick = { viewModel.toggleFavorite(track) },
                                        onMoreClick = { trackForOptions = track },
                                        onSwipeAddToQueue = { viewModel.addToQueue(track) }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(130.dp)) }
                            }
                        }
                    } else if (isLoadingTrending && homeSections.isEmpty() && trendingTracks.isEmpty()) {
                        HomeMonochromeSkeleton()
                    } else if (homeSections.isNotEmpty() || trendingTracks.isNotEmpty()) {
                        val effectiveSections = remember(homeSections, trendingTracks) {
                            if (homeSections.isNotEmpty()) {
                                homeSections
                            } else {
                                listOf(
                                    HomeSection(
                                        title = "Quick picks",
                                        subtitle = "START RADIO FROM A SONG",
                                        tracks = trendingTracks,
                                        layoutType = HomeSectionType.COMPACT_COLUMNS
                                    )
                                )
                            }
                        }

                        HomeShelvesColumn(
                            sections = effectiveSections,
                            listState = mainListState,
                            currentTrackId = currentTrack?.id,
                            shelfNestedScroll = shelfNestedScroll,
                            onItemTap = onSmartItemTap,
                            onPlayImmediately = { item, tracks -> viewModel.playHomeItem(item, tracks) },
                            onMoreClick = { trackForOptions = it },
                            onSelectExplore = { browseKey ->
                                selectedGenre = browseKey
                                viewModel.loadGenreTracks(browseKey)
                            }
                        )
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

                if (pullRefreshState.progress > 0f || pullRefreshState.isRefreshing) {
                    PullToRefreshContainer(
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = colors.card,
                        contentColor = colors.textPrimary
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = activeBrowseDetails != null,
            enter = slideInVertically(
                animationSpec = tween(260, easing = KuroMotion.EmphasizedDecelerate),
                initialOffsetY = { it }
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                animationSpec = tween(220, easing = KuroMotion.EmphasizedAccelerate),
                targetOffsetY = { it }
            ) + fadeOut(tween(160))
        ) {
            activeBrowseDetails?.let { details ->
                PlaylistDetailSheet(
                    details = details,
                    isLoading = isLoadingBrowseDetails,
                    currentTrackId = currentTrack?.id,
                    viewModel = viewModel,
                    onDismiss = { viewModel.closeBrowseItemDetails() },
                    onTrackMoreClick = { trackForOptions = it }
                )
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
                trackForPlaylist = targetTrack
                trackForOptions = null
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeShelvesColumn(
    sections: List<HomeSection>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    currentTrackId: String?,
    shelfNestedScroll: NestedScrollConnection,
    onItemTap: (Track, List<Track>) -> Unit,
    onPlayImmediately: (Track, List<Track>) -> Unit,
    onMoreClick: (Track) -> Unit,
    onSelectExplore: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp)
    ) {
        sections.forEachIndexed { idx, section ->
            if (section.tracks.isEmpty()) return@forEachIndexed
            val layoutType = resolveEffectiveLayout(section, idx)
            val strapline = section.subtitle.ifBlank {
                if (layoutType == HomeSectionType.COMPACT_COLUMNS && idx == 0) "START RADIO FROM A SONG" else ""
            }

            item(key = "sec_header_${idx}_${section.title}") {
                val playableSongs = section.tracks.filter { !it.isCollection && !it.isArtistProfile }
                if (layoutType == HomeSectionType.SIMILAR_TO) {
                    SimilarToSectionHeader(
                        title = section.title,
                        subtitle = section.subtitle.ifBlank { "SIMILAR TO" },
                        avatarUrl = section.headerAvatarUrl ?: section.tracks.firstOrNull()?.thumbnailUrlForDisplay(160),
                        onClick = {
                            section.tracks.firstOrNull()?.let { first ->
                                onItemTap(first, section.tracks)
                            }
                        }
                    )
                } else if (layoutType == HomeSectionType.SPEED_DIAL) {
                    SpeedDialSectionHeader(
                        title = section.title,
                        strapline = section.subtitle.ifBlank { "FOR YOU" },
                        avatarUrl = section.headerAvatarUrl ?: section.tracks.firstOrNull()?.thumbnailUrlForDisplay(160),
                        onClick = {
                            if (playableSongs.isNotEmpty()) {
                                onPlayImmediately(playableSongs.first(), playableSongs)
                            } else {
                                section.tracks.firstOrNull()?.let { first ->
                                    onItemTap(first, section.tracks)
                                }
                            }
                        }
                    )
                } else {
                    val isSongShelf = layoutType == HomeSectionType.COMPACT_COLUMNS || playableSongs.size == section.tracks.size
                    val actionLabel = if (isSongShelf && playableSongs.isNotEmpty()) {
                        stringResource(R.string.home_play_all)
                    } else if (section.tracks.isNotEmpty()) {
                        "More"
                    } else null
                    HomeSectionHeader(
                        title = section.title,
                        strapline = strapline,
                        actionText = actionLabel,
                        onActionClick = {
                            if (isSongShelf && playableSongs.isNotEmpty()) {
                                onPlayImmediately(playableSongs.first(), playableSongs)
                            } else {
                                section.tracks.firstOrNull()?.let { first ->
                                    onItemTap(first, section.tracks)
                                }
                            }
                        }
                    )
                }
            }

            when (layoutType) {
                HomeSectionType.COMPACT_COLUMNS -> {
                    val cols = section.tracks.chunked(4)
                    item(key = "sec_compact_${idx}_${section.title}") {
                        val rowState = rememberLazyListState()
                        val snapFling = rememberSnapFlingBehavior(lazyListState = rowState)
                        LazyRow(
                            state = rowState,
                            flingBehavior = snapFling,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .nestedScroll(shelfNestedScroll)
                        ) {
                            items(cols.size, key = { c -> "col_${idx}_$c" }) { colIdx ->
                                val colTracks = cols[colIdx]
                                Column(modifier = Modifier.width(300.dp)) {
                                    colTracks.forEach { track ->
                                        HomeCompactTrackRow(
                                            track = track,
                                            isPlaying = currentTrackId == track.id,
                                            onClick = { onItemTap(track, section.tracks) },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onMoreClick(track)
                                            },
                                            onMoreClick = { onMoreClick(track) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HomeSectionType.SPEED_DIAL -> {
                    item(key = "sec_speed_${idx}_${section.title}") {
                        val pages = section.tracks.chunked(9)
                        val rowState = rememberLazyListState()
                        val snapFling = rememberSnapFlingBehavior(lazyListState = rowState)
                        val activePageIdx by remember {
                            derivedStateOf { rowState.firstVisibleItemIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)) }
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LazyRow(
                                state = rowState,
                                flingBehavior = snapFling,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .nestedScroll(shelfNestedScroll)
                            ) {
                                items(pages.size, key = { p -> "sd_${idx}_$p" }) { pIdx ->
                                    SpeedDialGridPage(
                                        tracks = pages[pIdx],
                                        currentTrackId = currentTrackId,
                                        isFirstPage = pIdx == 0,
                                        onTrackClick = { t -> onItemTap(t, section.tracks) },
                                        onLongClick = { t ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onMoreClick(t)
                                        }
                                    )
                                }
                            }
                            if (pages.size > 1) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val colors = KuroAppTheme.colors
                                    pages.indices.forEach { dotIdx ->
                                        val isCurrentDot = dotIdx == activePageIdx
                                        val dotWidth by animateDpAsState(
                                            targetValue = if (isCurrentDot) 16.dp else 6.dp,
                                            animationSpec = tween(200),
                                            label = "sd_dot_w_$dotIdx"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 3.dp)
                                                .width(dotWidth)
                                                .height(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isCurrentDot) colors.textPrimary else colors.textMuted.copy(alpha = 0.35f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HomeSectionType.WIDE_CARDS -> {
                    item(key = "sec_wide_${idx}_${section.title}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .nestedScroll(shelfNestedScroll)
                        ) {
                            items(section.tracks, key = { "wide_${idx}_${it.id}" }) { track ->
                                WideVideoMusicCard(
                                    track = track,
                                    isPlaying = currentTrackId == track.id,
                                    onClick = { onItemTap(track, section.tracks) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMoreClick(track)
                                    }
                                )
                            }
                        }
                    }
                }

                else -> {
                    item(key = "sec_carousel_${idx}_${section.title}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .nestedScroll(shelfNestedScroll)
                        ) {
                            items(section.tracks, key = { "sq_${idx}_${it.id}" }) { track ->
                                YtmMusicCard(
                                    track = track,
                                    isPlaying = currentTrackId == track.id,
                                    onClick = { onItemTap(track, section.tracks) },
                                    onPlayBadgeClick = { onPlayImmediately(track, section.tracks) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMoreClick(track)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "home_explore_tiles") {
            HomeExploreQuickRow(
                onSelectGenre = onSelectExplore
            )
        }

        item {
            Spacer(modifier = Modifier.height(130.dp))
        }
    }
}

@Composable
private fun HomeMonochromeSkeleton() {
    val colors = KuroAppTheme.colors
    val pulseTransition = rememberInfiniteTransition(label = "home_skeleton_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    val baseColor = colors.card
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(148.dp)
                .height(22.dp)
                .drawBehind {
                    drawRoundRect(
                        color = baseColor.copy(alpha = pulseAlpha),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                }
        )
        repeat(4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = baseColor.copy(alpha = pulseAlpha),
                                cornerRadius = CornerRadius(6.dp.toPx())
                            )
                        }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(14.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = baseColor.copy(alpha = pulseAlpha),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                            }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(11.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = baseColor.copy(alpha = pulseAlpha * 0.8f),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                            }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(156.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = baseColor.copy(alpha = pulseAlpha * 0.85f),
                                cornerRadius = CornerRadius(12.dp.toPx())
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun SpeedDialSectionHeader(
    title: String,
    strapline: String,
    avatarUrl: String?,
    onClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, colors.border.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (strapline.isNotBlank()) {
                Text(
                    text = strapline.uppercase(),
                    style = Typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = Typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Open Speed Dial",
                    tint = colors.textMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialGridPage(
    tracks: List<Track>,
    currentTrackId: String?,
    isFirstPage: Boolean = false,
    onTrackClick: (Track) -> Unit,
    onLongClick: (Track) -> Unit
) {
    val colors = KuroAppTheme.colors
    val rows = tracks.chunked(3)
    Column(
        modifier = Modifier.width(328.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEachIndexed { rIdx, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEachIndexed { cIdx, track ->
                    val isPlaying = currentTrackId == track.id
                    val isHighlighted = isPlaying || (isFirstPage && rIdx == 0 && cIdx == 0 && currentTrackId == null)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(106.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.card)
                            .border(
                                if (isHighlighted) 1.6.dp else 1.dp,
                                if (isHighlighted) colors.accent else colors.border.copy(alpha = 0.35f),
                                RoundedCornerShape(10.dp)
                            )
                            .combinedClickable(
                                onClick = { onTrackClick(track) },
                                onLongClick = { onLongClick(track) }
                            )
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrlForDisplay(256),
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.35f to Color.Transparent,
                                        1.0f to PureBlack.copy(alpha = 0.86f)
                                    )
                                )
                        )
                        Text(
                            text = track.title,
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 7.dp, vertical = 6.dp)
                        )
                    }
                }
                repeat((3 - rowItems.size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SimilarToSectionHeader(
    title: String,
    subtitle: String,
    avatarUrl: String?,
    onClick: () -> Unit = {}
) {
    val colors = KuroAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, colors.border.copy(alpha = 0.45f), CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subtitle.uppercase(),
                style = Typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = Typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WideVideoMusicCard(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    Column(
        modifier = Modifier
            .width(232.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.card)
                .border(1.dp, colors.border.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
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
                        .background(PureBlack.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_kuro_play),
                        contentDescription = "Playing",
                        tint = PureWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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

@Composable
private fun HomeExploreQuickRow(
    onSelectGenre: (String) -> Unit
) {
    val colors = KuroAppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp)
    ) {
        Text(
            text = "Explore",
            style = Typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val tiles = listOf(
                Triple(Icons.Outlined.NewReleases, "New releases", "FEmusic_new_releases"),
                Triple(Icons.Outlined.TrendingUp, "Charts", "FEmusic_charts"),
                Triple(Icons.Outlined.Mood, "Moods & genres", "FEmusic_moods_and_genres")
            )
            tiles.forEach { (icon, label, targetBrowseId) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { onSelectGenre(targetBrowseId) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = label,
                        style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    currentAccount: UserAccount?,
    onRecentsClick: () -> Unit,
    onSearchClick: () -> Unit = {},
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
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "History",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
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
            .size(34.dp)
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
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
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
    onPlayBadgeClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = KuroAppTheme.colors
    val isArtist = track.isArtistProfile
    val shape = if (isArtist) CircleShape else RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .width(156.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(shape)
                .background(colors.card)
                .border(1.dp, colors.border.copy(alpha = 0.35f), shape)
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
            } else if (track.isCollection && onPlayBadgeClick != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1.0f to PureBlack.copy(alpha = 0.55f)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(PureBlack.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_notification),
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PureBlack.copy(alpha = 0.78f))
                        .border(0.8.dp, PureWhite.copy(alpha = 0.25f), CircleShape)
                        .clickable { onPlayBadgeClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Mix",
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = Typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist.ifBlank { formatMixBadgeLabel(track.title) },
            style = Typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            fontWeight = FontWeight.Normal,
            color = colors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start
        )
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    strapline: String = "",
    actionText: String? = null,
    onActionClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (strapline.isNotBlank()) {
                Text(
                    text = strapline.uppercase(),
                    style = Typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = title,
                style = Typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!actionText.isNullOrBlank()) {
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
                .background(colors.card)
                .border(1.dp, colors.border.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
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
