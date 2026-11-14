package com.ayashii.kuro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ayashii.kuro.R
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.components.AddToPlaylistDialog
import com.ayashii.kuro.ui.components.LoginWebViewDialog
import com.ayashii.kuro.ui.components.ProfileMenuSheet
import com.ayashii.kuro.ui.components.QueueBottomSheet
import com.ayashii.kuro.ui.components.SettingsDialog
import com.ayashii.kuro.ui.components.TrackOptionsBottomSheet
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.KuroMotion
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

private const val DEFAULT_DISCOVER_HEADER = "Discover something new"

data class SearchCategoryItem(
    val titleRes: Int,
    val searchQuery: String,
    val color: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var trackForOptions by remember { mutableStateOf<Track?>(null) }
    var showProfileMenu by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var showLoginWebView by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var swipeBackOffsetX by remember { mutableFloatStateOf(0f) }

    val searchResults by viewModel.searchResults.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val currentAccount by viewModel.authState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by remember(favorites) { derivedStateOf { favorites.mapTo(HashSet()) { it.id } } }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val colors = KuroAppTheme.colors
    val focusedTextColor = colors.textPrimary
    val unfocusedTextColor = colors.textPrimary

    val exitSearchMode: () -> Unit = {
        searchQuery = ""
        isSearchActive = false
        viewModel.search("")
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val animatedSwipeBackX by animateFloatAsState(
        targetValue = swipeBackOffsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "search_swipe_back_x"
    )

    val categories = remember {
        listOf(
            SearchCategoryItem(R.string.search_cat_music, "Pop Music", Color(0xFFE13300)),
            SearchCategoryItem(R.string.search_cat_podcasts, "Podcast", Color(0xFF006450)),
            SearchCategoryItem(R.string.search_cat_live, "Live Concert", Color(0xFF8400E7)),
            SearchCategoryItem(R.string.search_cat_kpop, "K-Pop Hits", Color(0xFF1E3264)),
            SearchCategoryItem(R.string.search_cat_made_for_you, "Top Trending Hits", Color(0xFF8D67AB)),
            SearchCategoryItem(R.string.search_cat_indie, "Indie Alternative", Color(0xFFBA5D07)),
            SearchCategoryItem(R.string.search_cat_charts, "Top 50", Color(0xFF8C1932)),
            SearchCategoryItem(R.string.search_cat_discover, "New Music", Color(0xFF503750))
        )
    }

    BackHandler(enabled = !isPlayerExpanded && searchQuery.isNotEmpty()) {
        exitSearchMode()
    }

    BackHandler(enabled = !isPlayerExpanded && isSearchActive && searchQuery.isEmpty()) {
        exitSearchMode()
    }

    val isFieldExpanded = isSearchActive || searchQuery.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AnimatedContent(
            targetState = isFieldExpanded,
            transitionSpec = {
                (fadeIn(tween(220, easing = KuroMotion.EmphasizedDecelerate)) +
                    slideInVertically(animationSpec = tween(220, easing = KuroMotion.EmphasizedDecelerate)) { -it / 6 })
                    .togetherWith(
                        fadeOut(tween(150, easing = KuroMotion.EmphasizedAccelerate)) +
                            slideOutVertically(animationSpec = tween(150, easing = KuroMotion.EmphasizedAccelerate)) { -it / 8 }
                    )
            },
            label = "search_header_transition"
        ) { activeHeader ->
            if (!activeHeader) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccountTopButton(
                            account = currentAccount,
                            onClick = { showProfileMenu = true }
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.search_title),
                            style = Typography.headlineLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                val pool = (favorites + recentlyPlayed + trendingTracks).distinctBy { it.id }
                                if (pool.isNotEmpty()) {
                                    val shuffled = pool.shuffled()
                                    viewModel.playTrack(shuffled.first(), shuffled)
                                } else {
                                    isSearchActive = true
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.card)
                                .border(1.dp, colors.border, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Quick Shuffle",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(CircleShape)
                                .background(colors.card)
                                .border(1.dp, colors.border, CircleShape)
                                .clickable { isSearchActive = true }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.search_what_do_you_want_to_listen_to),
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = Typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            } else {
                LaunchedEffect(Unit) {
                    if (searchQuery.isEmpty()) {
                        focusRequester.requestFocus()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { exitSearchMode() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.border, CircleShape)
                            .padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_what_do_you_want_to_listen_to),
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = Typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    viewModel.loadSuggestions(it)
                                },
                                singleLine = true,
                                textStyle = Typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (searchQuery.isNotEmpty()) focusedTextColor else unfocusedTextColor
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    if (searchQuery.isNotBlank()) {
                                        viewModel.search(searchQuery)
                                    }
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    viewModel.search("")
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val bodyMode = when {
            searchQuery.isNotEmpty() -> 2
            isSearchActive -> 1
            else -> 0
        }

        AnimatedContent(
            targetState = bodyMode,
            transitionSpec = {
                (fadeIn(tween(220, easing = KuroMotion.EmphasizedDecelerate)) +
                    slideInVertically(animationSpec = tween(220, easing = KuroMotion.EmphasizedDecelerate)) { it / 18 })
                    .togetherWith(
                        fadeOut(tween(150, easing = KuroMotion.EmphasizedAccelerate))
                    )
            },
            label = "search_body_transition",
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .graphicsLayer {
                    translationX = if (bodyMode != 0) animatedSwipeBackX.coerceAtLeast(0f) else 0f
                }
                .then(
                    if (bodyMode != 0) {
                        Modifier.pointerInput(bodyMode) {
                            detectHorizontalDragGestures(
                                onDragStart = { swipeBackOffsetX = 0f },
                                onDragEnd = {
                                    if (swipeBackOffsetX > 95f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        swipeBackOffsetX = 0f
                                        exitSearchMode()
                                    } else {
                                        swipeBackOffsetX = 0f
                                    }
                                },
                                onDragCancel = { swipeBackOffsetX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (dragAmount > 0f || swipeBackOffsetX > 0f) {
                                        swipeBackOffsetX = (swipeBackOffsetX + dragAmount).coerceIn(0f, size.width.toFloat().coerceAtLeast(360f))
                                        change.consume()
                                    }
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) { mode ->
            when (mode) {
                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 156.dp)
                    ) {
                        if (suggestions.isNotEmpty()) {
                            items(suggestions) { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = suggestion
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            viewModel.search(suggestion)
                                        }
                                        .padding(horizontal = 18.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = suggestion,
                                        style = Typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.NorthWest,
                                        contentDescription = "Use suggestion",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        if (isSearching) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = colors.accent,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }

                        if (searchResults.isNotEmpty()) {
                            items(searchResults, key = { it.id }, contentType = { "search_track" }) { track ->
                                val isCurrent = currentTrack?.id == track.id
                                val isSaved = track.id in favoriteIds

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                viewModel.playTrack(track, searchResults)
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                trackForOptions = track
                                            }
                                        )
                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = track.thumbnailUrlForDisplay(160),
                                        contentDescription = track.title,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.card),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = Typography.titleMedium.copy(
                                                fontSize = 15.sp,
                                                lineHeight = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrent) colors.accent else colors.textPrimary
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = stringResource(R.string.search_song_subtitle, track.artist),
                                            style = Typography.bodyMedium.copy(
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            ),
                                            color = colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

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

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (isSaved) {
                                                viewModel.toggleFavorite(track)
                                            } else {
                                                trackForPlaylist = track
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        val saveTint by animateColorAsState(
                                            targetValue = if (isSaved) colors.accent else colors.textMuted,
                                            animationSpec = tween(180),
                                            label = "search_save_tint"
                                        )
                                        AnimatedContent(
                                            targetState = isSaved,
                                            transitionSpec = {
                                                (scaleIn(initialScale = 0.6f, animationSpec = KuroMotion.appleSnappySpring()) + fadeIn(tween(140)))
                                                    .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(110)) + fadeOut(tween(110)))
                                            },
                                            label = "search_save_icon"
                                        ) { saved ->
                                            Icon(
                                                imageVector = if (saved) Icons.Default.CheckCircle else Icons.Outlined.AddCircleOutline,
                                                contentDescription = "Save track",
                                                tint = saveTint,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (!isSearching && suggestions.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 64.dp, start = 32.dp, end = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(colors.card),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.SearchOff,
                                                contentDescription = null,
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = stringResource(R.string.search_not_found),
                                            style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.search_not_found_sub),
                                            style = Typography.bodyMedium,
                                            color = colors.textMuted,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 156.dp)
                    ) {
                        if (recentlyPlayed.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.search_recents_title),
                                    style = Typography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)
                                )
                            }

                            items(recentlyPlayed, key = { "recent_${it.id}" }, contentType = { "recent_track" }) { track ->
                                val isSaved = track.id in favoriteIds

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                viewModel.playTrack(track, recentlyPlayed)
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                trackForOptions = track
                                            }
                                        )
                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = track.thumbnailUrlForDisplay(160),
                                        contentDescription = track.title,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.card),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = Typography.titleMedium.copy(
                                                fontSize = 15.sp,
                                                lineHeight = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = stringResource(R.string.search_song_subtitle, track.artist),
                                            style = Typography.bodyMedium.copy(
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            ),
                                            color = colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (isSaved) {
                                                viewModel.toggleFavorite(track)
                                            } else {
                                                trackForPlaylist = track
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        val saveTint by animateColorAsState(
                                            targetValue = if (isSaved) colors.accent else colors.textMuted,
                                            animationSpec = tween(180),
                                            label = "recent_save_tint"
                                        )
                                        AnimatedContent(
                                            targetState = isSaved,
                                            transitionSpec = {
                                                (scaleIn(initialScale = 0.6f, animationSpec = KuroMotion.appleSnappySpring()) + fadeIn(tween(140)))
                                                    .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(110)) + fadeOut(tween(110)))
                                            },
                                            label = "recent_save_icon"
                                        ) { saved ->
                                            Icon(
                                                imageVector = if (saved) Icons.Default.CheckCircle else Icons.Outlined.AddCircleOutline,
                                                contentDescription = "Save track",
                                                tint = saveTint,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { viewModel.removeFromRecents(track.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove from recents",
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(20.dp))
                                            .clickable { viewModel.clearRecentSearches() }
                                            .padding(horizontal = 24.dp, vertical = 9.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.search_clear_recents),
                                            style = Typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_empty_prompt),
                                        style = Typography.bodyMedium,
                                        color = colors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        contentPadding = PaddingValues(bottom = 156.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.search_explore_categories),
                                style = Typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                color = colors.textPrimary,
                                modifier = Modifier.padding(bottom = 14.dp)
                            )
                        }

                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                categories.chunked(2).forEach { rowCategories ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowCategories.forEach { category ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(98.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(category.color)
                                                    .clickable {
                                                        searchQuery = category.searchQuery
                                                        isSearchActive = true
                                                        focusManager.clearFocus()
                                                        keyboardController?.hide()
                                                        viewModel.search(category.searchQuery)
                                                    }
                                            ) {
                                                Text(
                                                    text = stringResource(category.titleRes).replace("\n", " "),
                                                    style = Typography.titleMedium.copy(
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        lineHeight = 20.sp
                                                    ),
                                                    color = Color.White,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .padding(12.dp)
                                                        .align(Alignment.TopStart)
                                                        .fillMaxWidth(0.72f)
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .offset(x = 10.dp, y = 6.dp)
                                                        .graphicsLayer { rotationZ = 24f }
                                                        .size(60.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color.White.copy(alpha = 0.22f))
                                                )
                                            }
                                        }
                                        if (rowCategories.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        val discoverTracks = trendingTracks.take(6)
                        if (discoverTracks.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(top = 22.dp, bottom = 12.dp)) {
                                    Text(
                                        text = stringResource(R.string.search_discover_new).ifBlank { DEFAULT_DISCOVER_HEADER },
                                        style = Typography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(discoverTracks, key = { "discover_${it.id}" }) { track ->
                                            Column(
                                                modifier = Modifier
                                                    .width(136.dp)
                                                    .combinedClickable(
                                                        onClick = { viewModel.playTrack(track, discoverTracks) },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            trackForOptions = track
                                                        }
                                                    )
                                            ) {
                                                AsyncImage(
                                                    model = track.thumbnailUrlForDisplay(512),
                                                    contentDescription = track.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(136.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(colors.card)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = track.title,
                                                    style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                                    color = colors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = track.artist,
                                                    style = Typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = colors.textMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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

    if (showProfileMenu) {
        ProfileMenuSheet(
            account = currentAccount,
            viewModel = viewModel,
            onLoginClick = {
                showProfileMenu = false
                showLoginWebView = true
            },
            onLogoutClick = {
                showProfileMenu = false
                viewModel.logout()
            },
            onOpenSettingsClick = {
                showProfileMenu = false
                showSettingsDialog = true
            },
            onDismiss = { showProfileMenu = false }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            viewModel = viewModel,
            onDismiss = { showQueueSheet = false }
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

    if (trackForPlaylist != null) {
        AddToPlaylistDialog(
            track = trackForPlaylist!!,
            viewModel = viewModel,
            onDismiss = { trackForPlaylist = null }
        )
    }
}
