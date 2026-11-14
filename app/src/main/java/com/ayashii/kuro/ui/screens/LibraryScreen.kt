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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentAccount by viewModel.authState.collectAsState()
    val favoriteIds by remember(favorites) { derivedStateOf { favorites.mapTo(HashSet()) { it.id } } }

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val libPagerState = rememberPagerState(initialPage = selectedTab, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(libPagerState.currentPage) {
        selectedTab = libPagerState.currentPage
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
                                text = if (selectedTab == 0) stringResource(R.string.lib_fav_count, favorites.size) else stringResource(R.string.lib_playlist_count, playlists.size),
                                style = Typography.bodyMedium,
                                color = colors.textMuted
                            )
                        }

                        if (selectedTab == 0 && currentAccount != null) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.card)
                                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                    .clickable { viewModel.syncLikedSongs() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync Liked Songs",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.lib_sync_yt),
                                    style = Typography.labelSmall,
                                    color = colors.textPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selectedTab == 0) colors.accent else colors.card)
                                .border(1.dp, if (selectedTab == 0) colors.accent else colors.border, RoundedCornerShape(20.dp))
                                .clickable {
                                    selectedTab = 0
                                    coroutineScope.launch {
                                        libPagerState.animateScrollToPage(0, animationSpec = KuroMotion.appleFluidSpring())
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.lib_tab_favorites),
                                style = Typography.labelMedium.copy(fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium),
                                color = if (selectedTab == 0) colors.background else colors.textPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selectedTab == 1) colors.accent else colors.card)
                                .border(1.dp, if (selectedTab == 1) colors.accent else colors.border, RoundedCornerShape(20.dp))
                                .clickable {
                                    selectedTab = 1
                                    coroutineScope.launch {
                                        libPagerState.animateScrollToPage(1, animationSpec = KuroMotion.appleFluidSpring())
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.lib_tab_my_playlists, playlists.size),
                                style = Typography.labelMedium.copy(fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium),
                                color = if (selectedTab == 1) colors.background else colors.textPrimary
                            )
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
                            if (tab == 0) {
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
                                            Spacer(modifier = Modifier.height(130.dp))
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.card)
                                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                                .clickable { showCreateDialog = true }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.accent),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "New Playlist",
                                                    tint = colors.background,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.lib_create_playlist),
                                                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = colors.textPrimary
                                                )
                                                Text(
                                                    text = stringResource(R.string.lib_create_playlist_sub),
                                                    style = Typography.bodySmall,
                                                    color = colors.textMuted
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }

                                    if (playlists.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 40.dp),
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
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.lib_no_playlists_sub),
                                                        style = Typography.bodyMedium,
                                                        color = colors.textMuted
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        items(playlists, key = { it.id }) { playlist ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.card)
                                                    .clickable { selectedPlaylistId = playlist.id }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                PlaylistCollageIcon(playlist = playlist, size = 52)
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = playlist.name,
                                                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                                                IconButton(onClick = { playlistToDelete = playlist }) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = stringResource(R.string.lib_delete),
                                                        tint = colors.textMuted,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    item {
                                        Spacer(modifier = Modifier.height(130.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
                val t = trackForOptions
                trackForOptions = null
                trackForPlaylist = t
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
