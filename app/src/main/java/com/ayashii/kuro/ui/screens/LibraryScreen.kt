package com.ayashii.kuro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.data.playlist.Playlist
import com.ayashii.kuro.ui.components.AddToPlaylistDialog
import com.ayashii.kuro.ui.components.QueueBottomSheet
import com.ayashii.kuro.ui.components.SettingsDialog
import com.ayashii.kuro.ui.components.TrackItem
import com.ayashii.kuro.ui.components.TrackOptionsBottomSheet
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.KuroMotion
import com.ayashii.kuro.ui.theme.KuroMotion.pagerMotionBlurTransition
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val watchLaterTracks by viewModel.watchLaterTracks.collectAsState()
    val offlineTracks by viewModel.offlineTracks.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentAccount by viewModel.authState.collectAsState()
    val favoriteIds by remember(favorites) { derivedStateOf { favorites.mapTo(HashSet()) { it.id } } }

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var sortAlphabetical by rememberSaveable { mutableStateOf(false) }
    val sortedPlaylists = remember(playlists, sortAlphabetical) {
        if (sortAlphabetical) playlists.sortedBy { it.name.lowercase() } else playlists
    }
    val libPagerState = rememberPagerState(initialPage = selectedTab, pageCount = { 5 })
    val chipListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(libPagerState.currentPage) {
        selectedTab = libPagerState.currentPage
        chipListState.animateScrollToItem(libPagerState.currentPage)
    }

    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var trackForOptions by remember { mutableStateOf<Track?>(null) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var newPlaylistName by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = !isPlayerExpanded && selectedPlaylistId == null && selectedTab != 0) {
        selectedTab = 0
        coroutineScope.launch {
            libPagerState.animateScrollToPage(0, animationSpec = KuroMotion.appleFluidSpring())
        }
    }

    BackHandler(enabled = !isPlayerExpanded && selectedPlaylistId != null) {
        selectedPlaylistId = null
    }

    val colors = KuroAppTheme.colors
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 16.dp)
    ) {
        AnimatedContent(
            targetState = selectedPlaylist,
            contentKey = { it?.id },
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(260, easing = KuroMotion.EmphasizedDecelerate)) { it / 5 } +
                        fadeIn(tween(220, easing = KuroMotion.EmphasizedDecelerate)))
                        .togetherWith(
                            slideOutHorizontally(tween(200, easing = KuroMotion.EmphasizedAccelerate)) { -it / 6 } +
                                fadeOut(tween(160, easing = KuroMotion.EmphasizedAccelerate))
                        )
                } else {
                    (slideInHorizontally(tween(260, easing = KuroMotion.EmphasizedDecelerate)) { -it / 5 } +
                        fadeIn(tween(220, easing = KuroMotion.EmphasizedDecelerate)))
                        .togetherWith(
                            slideOutHorizontally(tween(200, easing = KuroMotion.EmphasizedAccelerate)) { it / 6 } +
                                fadeOut(tween(160, easing = KuroMotion.EmphasizedAccelerate))
                        )
                }
            },
            label = "library_detail_transition",
            modifier = Modifier.fillMaxSize()
        ) { activePlaylist ->
            if (activePlaylist != null) {
                PlaylistDetailView(
                    playlist = activePlaylist,
                    viewModel = viewModel,
                    currentTrack = currentTrack,
                    isLoadingTrack = isLoadingTrack,
                    favoriteIds = favoriteIds,
                    onBack = { selectedPlaylistId = null },
                    onMoreClick = { trackForOptions = it }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.lib_playlist_title),
                                    style = Typography.headlineLarge,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (selectedTab) {
                                        0 -> stringResource(R.string.lib_all_count, playlists.size, favorites.size)
                                        1 -> stringResource(R.string.lib_fav_count, favorites.size)
                                        2 -> stringResource(R.string.lib_playlist_count, playlists.size)
                                        3 -> stringResource(R.string.lib_watch_later_count, watchLaterTracks.size)
                                        else -> stringResource(R.string.lib_offline_count, offlineTracks.size)
                                    },
                                    style = Typography.bodyMedium,
                                    color = colors.textMuted
                                )
                            }

                            if ((selectedTab == 0 || selectedTab == 1 || selectedTab == 2) && currentAccount != null) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.card)
                                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                        .clickable {
                                            when (selectedTab) {
                                                0 -> {
                                                    viewModel.syncLikedSongs()
                                                    viewModel.syncUserPlaylists()
                                                }
                                                1 -> viewModel.syncLikedSongs()
                                                else -> viewModel.syncUserPlaylists()
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync YT Music",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.lib_sync_yt),
                                        style = Typography.labelSmall,
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val tabLabels = listOf(
                            stringResource(R.string.lib_tab_all),
                            stringResource(R.string.lib_tab_favorites),
                            stringResource(R.string.lib_tab_my_playlists, playlists.size),
                            "${stringResource(R.string.lib_tab_watch_later)} (${watchLaterTracks.size})",
                            "${stringResource(R.string.lib_tab_offline)} (${offlineTracks.size})"
                        )

                        LazyRow(
                            state = chipListState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tabLabels.size) { index ->
                                val isSelected = selectedTab == index
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (isSelected) colors.accent else colors.card)
                                        .border(
                                            1.dp,
                                            if (isSelected) colors.accent else colors.border,
                                            RoundedCornerShape(18.dp)
                                        )
                                        .clickable {
                                            selectedTab = index
                                            coroutineScope.launch {
                                                libPagerState.animateScrollToPage(index, animationSpec = KuroMotion.appleFluidSpring())
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tabLabels[index],
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

                        Spacer(modifier = Modifier.height(14.dp))

                        HorizontalPager(
                            state = libPagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { tab ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pagerMotionBlurTransition(
                                        pageOffsetProvider = {
                                            (libPagerState.currentPage - tab) + libPagerState.currentPageOffsetFraction
                                        }
                                    )
                            ) {
                                when (tab) {
                                    0 -> {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            item(key = "section_pinned") {
                                                Text(
                                                    text = stringResource(R.string.lib_section_pinned),
                                                    style = Typography.labelSmall.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        letterSpacing = 0.8.sp
                                                    ),
                                                    color = colors.textMuted,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                                )
                                            }
                                            item(key = "pinned_favorites") {
                                                PinnedLibraryCard(
                                                    title = stringResource(R.string.lib_tab_favorites),
                                                    subtitle = "${stringResource(R.string.lib_auto_playlist)} • ${stringResource(R.string.lib_songs_count, favorites.size)}",
                                                    icon = Icons.Outlined.FavoriteBorder,
                                                    isPinned = true,
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            libPagerState.animateScrollToPage(1, animationSpec = KuroMotion.appleFluidSpring())
                                                        }
                                                    }
                                                )
                                            }
                                            item(key = "pinned_watch_later") {
                                                PinnedLibraryCard(
                                                    title = stringResource(R.string.lib_tab_watch_later),
                                                    subtitle = stringResource(R.string.lib_watch_later_count, watchLaterTracks.size),
                                                    icon = Icons.Outlined.BookmarkBorder,
                                                    isPinned = false,
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            libPagerState.animateScrollToPage(3, animationSpec = KuroMotion.appleFluidSpring())
                                                        }
                                                    }
                                                )
                                            }
                                            item(key = "pinned_offline") {
                                                PinnedLibraryCard(
                                                    title = stringResource(R.string.lib_tab_offline),
                                                    subtitle = stringResource(R.string.lib_offline_count, offlineTracks.size),
                                                    icon = Icons.Outlined.Download,
                                                    isPinned = false,
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            libPagerState.animateScrollToPage(4, animationSpec = KuroMotion.appleFluidSpring())
                                                        }
                                                    }
                                                )
                                            }

                                            item(key = "section_playlists") {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.lib_section_playlists),
                                                        style = Typography.labelSmall.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            letterSpacing = 0.8.sp
                                                        ),
                                                        color = colors.textMuted
                                                    )
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(colors.card)
                                                            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                                            .clickable { sortAlphabetical = !sortAlphabetical }
                                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = stringResource(
                                                                if (sortAlphabetical) R.string.lib_sort_az else R.string.lib_sort_recent
                                                            ),
                                                            style = Typography.labelSmall,
                                                            color = colors.textPrimary,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowDown,
                                                            contentDescription = null,
                                                            tint = colors.textMuted,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            items(sortedPlaylists, key = { "all_pl_${it.id}" }) { playlist ->
                                                LibraryPlaylistCard(
                                                    playlist = playlist,
                                                    onClick = { selectedPlaylistId = playlist.id },
                                                    onDeleteClick = { playlistToDelete = playlist }
                                                )
                                            }

                                            item {
                                                Spacer(modifier = Modifier.height(150.dp))
                                            }
                                        }
                                    }
                                    1 -> {
                                        if (favorites.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.QueueMusic,
                                                        contentDescription = null,
                                                        tint = colors.textSecondary,
                                                        modifier = Modifier.size(56.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Text(
                                                        text = stringResource(R.string.lib_fav_empty),
                                                        style = Typography.titleMedium,
                                                        color = colors.textPrimary
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.lib_fav_empty_sub),
                                                        style = Typography.bodyMedium,
                                                        color = colors.textMuted
                                                    )
                                                }
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(favorites, key = { it.id }, contentType = { "track" }) { track ->
                                                    TrackItem(
                                                        track = track.copy(isFavorite = true),
                                                        isPlaying = currentTrack?.id == track.id,
                                                        isLoading = isLoadingTrack && currentTrack?.id == track.id,
                                                        onClick = { viewModel.playTrack(track, favorites) },
                                                        onFavoriteClick = { viewModel.toggleFavorite(track) },
                                                        onMoreClick = { trackForOptions = track },
                                                        onSwipeAddToQueue = { viewModel.addToQueue(track) }
                                                    )
                                                }
                                                item {
                                                    Spacer(modifier = Modifier.height(150.dp))
                                                }
                                            }
                                        }
                                    }
                                    2 -> {
                                        if (sortedPlaylists.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.QueueMusic,
                                                        contentDescription = null,
                                                        tint = colors.textSecondary,
                                                        modifier = Modifier.size(52.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text(
                                                        text = stringResource(R.string.lib_no_playlists),
                                                        style = Typography.titleMedium,
                                                        color = colors.textPrimary
                                                    )
                                                }
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(sortedPlaylists, key = { it.id }) { playlist ->
                                                    LibraryPlaylistCard(
                                                        playlist = playlist,
                                                        onClick = { selectedPlaylistId = playlist.id },
                                                        onDeleteClick = { playlistToDelete = playlist }
                                                    )
                                                }
                                                item {
                                                    Spacer(modifier = Modifier.height(150.dp))
                                                }
                                            }
                                        }
                                    }
                                    3 -> {
                                        if (watchLaterTracks.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.lib_watch_later_empty),
                                                    style = Typography.bodyMedium,
                                                    color = colors.textMuted
                                                )
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(watchLaterTracks, key = { "wl_${it.id}" }, contentType = { "track" }) { track ->
                                                    val isFav = track.id in favoriteIds
                                                    TrackItem(
                                                        track = track.copy(isFavorite = isFav),
                                                        isPlaying = currentTrack?.id == track.id,
                                                        isLoading = isLoadingTrack && currentTrack?.id == track.id,
                                                        onClick = { viewModel.playWatchLater(startTrack = track) },
                                                        onFavoriteClick = { viewModel.toggleFavorite(track) },
                                                        onMoreClick = { trackForOptions = track },
                                                        onSwipeAddToQueue = { viewModel.addToQueue(track) }
                                                    )
                                                }
                                                item {
                                                    Spacer(modifier = Modifier.height(150.dp))
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        if (offlineTracks.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.lib_offline_empty),
                                                    style = Typography.bodyMedium,
                                                    color = colors.textMuted
                                                )
                                            }
                                        } else {
                                            val offlineQueue = remember(offlineTracks) { offlineTracks.map { it.track } }
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(offlineTracks, key = { "offline_${it.track.id}" }) { item ->
                                                    val isFav = item.track.id in favoriteIds
                                                    TrackItem(
                                                        track = item.track.copy(isFavorite = isFav),
                                                        isPlaying = currentTrack?.id == item.track.id,
                                                        isLoading = isLoadingTrack && currentTrack?.id == item.track.id,
                                                        onClick = { viewModel.playTrack(item.track, offlineQueue) },
                                                        onFavoriteClick = { viewModel.toggleFavorite(item.track) },
                                                        onMoreClick = { trackForOptions = item.track },
                                                        onSwipeAddToQueue = { viewModel.addToQueue(item.track) }
                                                    )
                                                }
                                                item {
                                                    Spacer(modifier = Modifier.height(150.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 104.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.accent)
                            .clickable { showCreateDialog = true }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.lib_create_playlist),
                            tint = colors.background,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.lib_new_fab),
                            style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.background,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.lib_enter_name),
                    style = Typography.titleLarge,
                    color = colors.textPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text(stringResource(R.string.lib_playlist_name_hint), color = colors.textMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.card,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.textPrimary,
                        focusedIndicatorColor = colors.accent,
                        unfocusedIndicatorColor = colors.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            val created = viewModel.createPlaylist(newPlaylistName.trim())
                            selectedPlaylistId = created.id
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.background
                    )
                ) {
                    Text(stringResource(R.string.lib_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.lib_cancel), color = colors.textMuted)
                }
            },
            containerColor = colors.background
        )
    }

    if (playlistToDelete != null) {
        val targetPlaylist = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.lib_delete_title),
                    style = Typography.titleLarge,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.lib_delete_confirm, targetPlaylist.name, targetPlaylist.tracks.size),
                    style = Typography.bodyMedium,
                    color = colors.textMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(targetPlaylist.id)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFFF3B30),
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text(stringResource(R.string.lib_delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(R.string.lib_cancel), color = colors.textPrimary)
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
            },
            onRemoveFromPlaylist = if (selectedPlaylist != null) {
                {
                    viewModel.removeTrackFromPlaylist(selectedPlaylist.id, targetTrack.id)
                    trackForOptions = null
                }
            } else null
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

@Composable
fun PlaylistCollageIcon(playlist: Playlist, size: Int = 50) {
    val colors = KuroAppTheme.colors
    val tracks = playlist.tracks

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.border),
        contentAlignment = Alignment.Center
    ) {
        if (tracks.size >= 4) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    AsyncImage(
                        model = tracks[0].thumbnailUrlForDisplay(192),
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    AsyncImage(
                        model = tracks[1].thumbnailUrlForDisplay(192),
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    AsyncImage(
                        model = tracks[2].thumbnailUrlForDisplay(192),
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    AsyncImage(
                        model = tracks[3].thumbnailUrlForDisplay(192),
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        } else if (tracks.isNotEmpty()) {
            AsyncImage(
                model = tracks[0].thumbnailUrlForDisplay(512),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.QueueMusic,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size((size * 0.5).dp)
            )
        }
    }
}

@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    viewModel: MusicViewModel,
    currentTrack: Track?,
    isLoadingTrack: Boolean,
    favoriteIds: Set<String> = emptySet(),
    onBack: () -> Unit,
    onMoreClick: (Track) -> Unit
) {
    val colors = KuroAppTheme.colors
    val haptic = LocalHapticFeedback.current
    var dragBackX by remember { mutableFloatStateOf(0f) }
    var isDraggingBack by remember { mutableStateOf(false) }

    val settledDragBackX by animateFloatAsState(
        targetValue = dragBackX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playlist_detail_back_drag"
    )
    val activeDragX = if (isDraggingBack) dragBackX else settledDragBackX

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val curX = activeDragX.coerceAtLeast(0f)
                translationX = curX
                alpha = 1f - ((curX / size.width.coerceAtLeast(1f)) * 0.25f).coerceIn(0f, 0.25f)
            }
            .pointerInput(playlist.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDraggingBack = true
                        dragBackX = 0f
                    },
                    onDragEnd = {
                        val shouldDismiss = dragBackX > (size.width * 0.28f).coerceAtLeast(100f)
                        isDraggingBack = false
                        if (shouldDismiss) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dragBackX = 0f
                            onBack()
                        } else {
                            dragBackX = 0f
                        }
                    },
                    onDragCancel = {
                        isDraggingBack = false
                        dragBackX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragAmount > 0f || dragBackX > 0f) {
                            dragBackX = (dragBackX + dragAmount).coerceIn(0f, size.width.toFloat().coerceAtLeast(360f))
                            change.consume()
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = playlist.name,
                style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlaylistCollageIcon(playlist = playlist, size = 180)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = playlist.name,
                        style = Typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.lib_songs_count, playlist.tracks.size),
                        style = Typography.bodyMedium,
                        color = colors.textMuted
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (playlist.tracks.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.playPlaylist(playlist, shuffle = false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.background
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.lib_play_all), fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.playPlaylist(playlist, shuffle = true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.card,
                                    contentColor = colors.textPrimary
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.lib_shuffle), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (playlist.tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.lib_playlist_empty),
                            style = Typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                items(playlist.tracks, key = { it.id }, contentType = { "track" }) { track ->
                    val isFav = track.id in favoriteIds
                    TrackItem(
                        track = track.copy(isFavorite = isFav),
                        isPlaying = currentTrack?.id == track.id,
                        isLoading = isLoadingTrack && currentTrack?.id == track.id,
                        onClick = { viewModel.playPlaylist(playlist, startTrack = track) },
                        onFavoriteClick = { viewModel.toggleFavorite(track) },
                        onMoreClick = { onMoreClick(track) },
                        onSwipeAddToQueue = { viewModel.addToQueue(track) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(130.dp))
            }
        }
    }
}

@Composable
private fun PinnedLibraryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isPinned: Boolean,
    onClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background.copy(alpha = 0.55f))
                .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = subtitle,
                    style = Typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LibraryPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistCollageIcon(playlist = playlist, size = 48)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.lib_songs_count, playlist.tracks.size),
                style = Typography.bodySmall,
                color = colors.textMuted
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.lib_delete),
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lib_delete), color = colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = colors.textMuted
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}
