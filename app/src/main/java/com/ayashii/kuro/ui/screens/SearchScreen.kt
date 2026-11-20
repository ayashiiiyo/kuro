package com.ayashii.kuro.ui.screens

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.PureWhite
import com.ayashii.kuro.ui.theme.rememberDynamicDominantColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import com.ayashii.kuro.ui.components.PlaylistDetailSheet
import com.ayashii.kuro.ui.components.ProfileMenuSheet
import com.ayashii.kuro.ui.components.QueueBottomSheet
import com.ayashii.kuro.ui.components.SettingsDialog
import com.ayashii.kuro.ui.components.TrackOptionsBottomSheet
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.KuroMotion
import com.ayashii.kuro.ui.theme.KuroMotion.cascadeSection
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

private const val DEFAULT_DISCOVER_HEADER = "Discover something new"

data class SearchCategoryItem(
    val titleRes: Int,
    val searchQuery: String,
    val stripeColor: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    pageOffsetProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var selectedFilterChip by rememberSaveable { mutableStateOf("All") }
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
    val activeBrowseDetails by viewModel.activeBrowseDetails.collectAsState()
    val isLoadingBrowseDetails by viewModel.isLoadingBrowseDetails.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val searchRecents by viewModel.searchRecents.collectAsState()
    val currentAccount by viewModel.authState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by remember(favorites) { derivedStateOf { favorites.mapTo(HashSet()) { it.id } } }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val colors = KuroAppTheme.colors
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) {
                searchQuery = spoken
                isSearchActive = true
                focusManager.clearFocus()
                keyboardController?.hide()
                viewModel.searchWithFilter(spoken, selectedFilterChip)
            }
        }
    }
    val launchVoiceSearch: () -> Unit = {
        runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak song, artist, or lyrics")
            }
            voiceLauncher.launch(intent)
        }.onFailure {
            isSearchActive = true
        }
    }
    val focusedTextColor = colors.textPrimary
    val unfocusedTextColor = colors.textPrimary

    val exitSearchMode: () -> Unit = {
        searchQuery = ""
        selectedFilterChip = "All"
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
            SearchCategoryItem(R.string.search_cat_music, "Pop Hits", Color(0xFFE4E4E7)),
            SearchCategoryItem(R.string.search_cat_discover, "New Releases", Color(0xFFA1A1AA)),
            SearchCategoryItem(R.string.search_cat_charts, "Top Global Charts", Color(0xFFD4D4D8)),
            SearchCategoryItem(R.string.search_cat_indie, "Indie & Alternative", Color(0xFF71717A)),
            SearchCategoryItem(R.string.search_cat_kpop, "J-Pop & K-Pop", Color(0xFFB4B4BC)),
            SearchCategoryItem(R.string.search_cat_made_for_you, "Chill & Lo-Fi", Color(0xFF8E8E96)),
            SearchCategoryItem(R.string.search_cat_live, "Live Performances", Color(0xFFC4C4CC)),
            SearchCategoryItem(R.string.search_cat_podcasts, "Acoustic & Unplugged", Color(0xFF63636B))
        )
    }

    val filterChips = remember {
        listOf("All", "Songs", "Videos", "Albums", "Artists", "Playlists")
    }

    BackHandler(enabled = !isPlayerExpanded && searchQuery.isNotEmpty()) {
        exitSearchMode()
    }

    BackHandler(enabled = !isPlayerExpanded && isSearchActive && searchQuery.isEmpty()) {
        exitSearchMode()
    }

    val isFieldExpanded = isSearchActive || searchQuery.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                label = "search_header_transition",
                modifier = Modifier.cascadeSection(stage = 0, pageOffsetProvider = pageOffsetProvider)
            ) { activeHeader ->
                if (!activeHeader) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(CircleShape)
                                    .background(if (colors.isDark) PureWhite.copy(alpha = 0.09f) else colors.card)
                                    .border(1.dp, colors.border.copy(alpha = 0.5f), CircleShape)
                                    .clickable { isSearchActive = true }
                                    .padding(start = 16.dp, end = 6.dp),
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
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = launchVoiceSearch,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice Search",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            AccountTopButton(
                                account = currentAccount,
                                onClick = { showProfileMenu = true }
                            )
                        }
                    }
                } else {
                    LaunchedEffect(Unit) {
                        if (searchQuery.isEmpty()) {
                            focusRequester.requestFocus()
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
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
                                    .height(46.dp)
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
                                            selectedFilterChip = "All"
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
                                                viewModel.searchWithFilter(searchQuery, selectedFilterChip)
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
                                            selectedFilterChip = "All"
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
                                } else {
                                    IconButton(
                                        onClick = launchVoiceSearch,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Voice Search",
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (searchQuery.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(filterChips, key = { it }) { chip ->
                                    val isSelected = selectedFilterChip == chip
                                    val chipBg = when {
                                        isSelected -> colors.accent
                                        colors.isDark -> PureWhite.copy(alpha = 0.10f)
                                        else -> colors.card
                                    }
                                    val chipBorder = when {
                                        isSelected -> colors.accent
                                        colors.isDark -> PureWhite.copy(alpha = 0.14f)
                                        else -> colors.border
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(chipBg)
                                            .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedFilterChip = chip
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                viewModel.searchWithFilter(searchQuery, chip)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = chip,
                                            style = Typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) colors.background else colors.textPrimary
                                        )
                                    }
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
                                                viewModel.searchWithFilter(suggestion, selectedFilterChip)
                                            }
                                            .padding(horizontal = 18.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(19.dp)
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
                                    Spacer(modifier = Modifier.height(8.dp))
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
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                            }

                            if (searchResults.isNotEmpty()) {
                                val showTopCard = selectedFilterChip == "All" && suggestions.isEmpty()
                                val topResult = if (showTopCard) searchResults.firstOrNull() else null
                                val listResults = if (topResult != null) searchResults.drop(1) else searchResults

                                if (topResult != null) {
                                    item(key = "top_result_${topResult.id}") {
                                        YtmTopResultCard(
                                            track = topResult,
                                            isPlaying = currentTrack?.id == topResult.id,
                                            isSaved = topResult.id in favoriteIds,
                                            onPlayClick = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                viewModel.playTrackFromSearch(topResult)
                                            },
                                            onRadioOrSaveClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                if (topResult.isCollection || topResult.isArtistProfile) {
                                                    viewModel.openBrowseItemDetails(topResult)
                                                } else {
                                                    viewModel.playTrack(topResult, searchResults.filter { !it.isCollection && !it.isArtistProfile })
                                                }
                                            },
                                            onMoreClick = { trackForOptions = topResult }
                                        )
                                    }
                                }

                                items(listResults, key = { it.id }, contentType = { "search_track" }) { track ->
                                    val isCurrent = currentTrack?.id == track.id
                                    val thumbShape = if (track.isArtistProfile) CircleShape else RoundedCornerShape(6.dp)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                    viewModel.playTrackFromSearch(track)
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
                                                .size(52.dp)
                                                .clip(thumbShape)
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
                                            val subtitleLabel = when {
                                                track.isArtistProfile -> "Artist • ${track.artist}"
                                                track.isCollection -> track.artist
                                                track.durationSeconds > 0 -> "Song • ${track.artist} • ${track.formattedDuration}"
                                                else -> stringResource(R.string.search_song_subtitle, track.artist)
                                            }
                                            Text(
                                                text = subtitleLabel,
                                                style = Typography.bodyMedium.copy(
                                                    fontSize = 13.sp,
                                                    lineHeight = 18.sp
                                                ),
                                                color = colors.textMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

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
                            modifier = Modifier
                                .fillMaxSize()
                                .cascadeSection(stage = 1, pageOffsetProvider = pageOffsetProvider),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 156.dp)
                        ) {
                            if (searchRecents.isNotEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.search_recents_title),
                                        style = Typography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary,
                                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)
                                    )
                                }

                                items(searchRecents, key = { "recent_${it.id}" }, contentType = { "recent_track" }) { track ->
                                    val isSaved = track.id in favoriteIds

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                    viewModel.playTrackFromSearch(track)
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
                                                .clip(RoundedCornerShape(6.dp))
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
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(bottom = 156.dp)
                        ) {
                            /* YouTube Music 2026 Unified Search + Explore Quick Grid */
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                val exploreCards = listOf(
                                    Triple(Icons.Outlined.NewReleases, "New releases", "New Releases 2026"),
                                    Triple(Icons.Outlined.TrendingUp, "Charts", "Top Global Charts 2026"),
                                    Triple(Icons.Outlined.Mood, "Moods & genres", "Moods and Genres Mix"),
                                    Triple(Icons.Default.Shuffle, "Quick shuffle", "")
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .cascadeSection(stage = 1, pageOffsetProvider = pageOffsetProvider)
                                ) {
                                    exploreCards.chunked(2).forEach { pair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            pair.forEach { (icon, label, queryTarget) ->
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(if (colors.isDark) PureWhite.copy(alpha = 0.08f) else colors.card)
                                                        .border(1.dp, colors.border.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            if (queryTarget.isBlank()) {
                                                                val pool = (favorites + recentlyPlayed + trendingTracks).distinctBy { it.id }
                                                                if (pool.isNotEmpty()) {
                                                                    val shuffled = pool.shuffled()
                                                                    viewModel.playTrack(shuffled.first(), shuffled)
                                                                } else {
                                                                    isSearchActive = true
                                                                }
                                                            } else {
                                                                searchQuery = queryTarget
                                                                isSearchActive = true
                                                                focusManager.clearFocus()
                                                                keyboardController?.hide()
                                                                viewModel.search(queryTarget)
                                                            }
                                                        }
                                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = label,
                                                        tint = colors.textPrimary,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = label,
                                                        style = Typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                                        color = colors.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (searchRecents.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        text = "Recent searches",
                                        style = Typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                items(searchRecents.take(4), key = { "landing_recent_${it.id}" }) { rTrack ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                searchQuery = rTrack.title
                                                isSearchActive = true
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                viewModel.search(rTrack.title)
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.History,
                                            contentDescription = null,
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = rTrack.title,
                                            style = Typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.NorthWest,
                                            contentDescription = "Fill query",
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = "Moods & genres",
                                    style = Typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            /* Authentic YouTube Music Moods & Genres left-stripe tonal cards */
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .cascadeSection(stage = 1, pageOffsetProvider = pageOffsetProvider),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    categories.chunked(2).forEach { rowCategories ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            rowCategories.forEach { category ->
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(54.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(colors.card)
                                                        .border(1.dp, colors.border.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            searchQuery = category.searchQuery
                                                            isSearchActive = true
                                                            focusManager.clearFocus()
                                                            keyboardController?.hide()
                                                            viewModel.search(category.searchQuery)
                                                        },
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(6.dp)
                                                            .height(54.dp)
                                                            .background(category.stripeColor)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = stringResource(category.titleRes).replace("\n", " "),
                                                        style = Typography.titleSmall.copy(
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = colors.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(end = 10.dp)
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

                            val discoverTracks = trendingTracks.take(8)
                            if (discoverTracks.isNotEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .padding(top = 24.dp, bottom = 12.dp)
                                            .cascadeSection(stage = 2, pageOffsetProvider = pageOffsetProvider)
                                    ) {
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
                                                YtmMusicCard(
                                                    track = track,
                                                    isPlaying = currentTrack?.id == track.id,
                                                    onClick = { viewModel.playTrackFromSearch(track) },
                                                    onPlayBadgeClick = { viewModel.playHomeItem(track, discoverTracks) },
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
                    }
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

@Composable
private fun YtmTopResultCard(
    track: Track,
    isPlaying: Boolean,
    isSaved: Boolean,
    onPlayClick: () -> Unit,
    onRadioOrSaveClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    val dominant = rememberDynamicDominantColor(track.thumbnailUrlForDisplay(256), colors.card)
    val cardGradient = remember(dominant, colors.card, colors.isDark) {
        val tint = lerp(colors.card, dominant, if (colors.isDark) 0.28f else 0.14f)
        Brush.verticalGradient(listOf(tint, colors.card))
    }
    val thumbShape = if (track.isArtistProfile) CircleShape else RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardGradient)
            .border(1.dp, colors.border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable(onClick = onPlayClick)
            .padding(14.dp)
    ) {
        Text(
            text = "TOP RESULT",
            style = Typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 0.9.sp,
                fontWeight = FontWeight.Bold
            ),
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.thumbnailUrlForDisplay(256),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(thumbShape)
                    .background(colors.surface)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = Typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (isPlaying) colors.accent else colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                val typeLabel = when {
                    track.isArtistProfile -> "Artist • ${track.artist}"
                    track.isCollection -> track.artist
                    track.durationSeconds > 0 -> "Song • ${track.artist} • ${track.formattedDuration}"
                    else -> "Song • ${track.artist}"
                }
                Text(
                    text = typeLabel,
                    style = Typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(colors.accent)
                    .clickable(onClick = onPlayClick),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = colors.background,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (track.isCollection || track.isArtistProfile) "Open" else "Play",
                    style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.background
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (colors.isDark) PureWhite.copy(alpha = 0.12f) else colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(19.dp))
                    .clickable(onClick = onRadioOrSaveClick),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Radio,
                    contentDescription = "Radio",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (track.isCollection || track.isArtistProfile) "Browse" else "Radio",
                    style = Typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
            }
        }
    }
}
