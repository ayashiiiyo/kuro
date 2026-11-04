package com.ayashii.kuro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
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
import com.ayashii.kuro.ui.components.ProfileMenuSheet
import com.ayashii.kuro.ui.components.TrackOptionsBottomSheet
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

data class SearchCategoryItem(
    val title: String,
    val searchQuery: String,
    val color: Color
)

@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var trackForOptions by remember { mutableStateOf<Track?>(null) }
    var showProfileMenu by remember { mutableStateOf(false) }

    val searchResults by viewModel.searchResults.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val currentAccount by viewModel.authState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by remember(favorites) { derivedStateOf { favorites.mapTo(HashSet()) { it.id } } }

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val colors = KuroAppTheme.colors

    val categories = remember {
        listOf(
            SearchCategoryItem("Music", "Pop Music", Color(0xFFE13300)),
            SearchCategoryItem("Podcasts", "Podcast", Color(0xFF006450)),
            SearchCategoryItem("Live Events", "Live Concert", Color(0xFF8400E7)),
            SearchCategoryItem("K-Pop ON!\n(온) Hub", "K-Pop Hits", Color(0xFF1E3264)),
            SearchCategoryItem("Made For\nYou", "Top Trending Hits", Color(0xFF8D67AB)),
            SearchCategoryItem("Indie & Alt", "Indie Alternative", Color(0xFFBA5D07)),
            SearchCategoryItem("Charts", "Top 50", Color(0xFF8C1932)),
            SearchCategoryItem("Discover", "New Music", Color(0xFF503750))
        )
    }

    BackHandler(enabled = isSearchActive || searchQuery.isNotEmpty()) {
        isSearchActive = false
        searchQuery = ""
        focusManager.clearFocus()
        viewModel.search("")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // TOP APP BAR
        if (!isSearchActive && searchQuery.isEmpty()) {
            // Default Browse Mode Header (Avatar + Title "Search" + Camera)
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
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "Search Camera",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Clickable search pill for Browse mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242424))
                        .clickable { isSearchActive = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF8E8E8E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.search_what_do_you_want_to_listen_to),
                        color = Color(0xFF8E8E8E),
                        style = Typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal)
                    )
                }
            }
        } else {
            // Active Search Mode Header (Back Arrow + Dark Pill Search Bar matching Ref Image 2 & 3)
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        isSearchActive = false
                        searchQuery = ""
                        focusManager.clearFocus()
                        viewModel.search("")
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                TextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.loadSuggestions(it)
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_what_do_you_want_to_listen_to),
                            color = Color(0xFF8E8E8E),
                            style = Typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal)
                        )
                    },
                    trailingIcon = {
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
                                    tint = Color(0xFFA7A7A7),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        if (searchQuery.isNotBlank()) {
                            viewModel.search(searchQuery)
                        }
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF242424),
                        unfocusedContainerColor = Color(0xFF242424),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = colors.accent
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(focusRequester)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // MAIN CONTENT AREA
        if (searchQuery.isNotEmpty()) {
            // SEARCH QUERY TYPED (Ref Image 2: Suggestions & Results)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // 1. Suggestions rows (Ref Image 2)
                if (suggestions.isNotEmpty()) {
                    items(suggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchQuery = suggestion
                                    focusManager.clearFocus()
                                    viewModel.search(suggestion)
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFFB3B3B3),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = suggestion,
                                style = Typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.NorthWest,
                                contentDescription = "Use suggestion",
                                tint = Color(0xFF727272),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // 2. Loading indicator
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

                // 3. Search results (Ref Image 2: Album art, Title, Song • Artist, 3-dots, (+))
                if (searchResults.isNotEmpty()) {
                    items(searchResults, key = { it.id }) { track ->
                        val isCurrent = currentTrack?.id == track.id
                        val isSaved = track.id in favoriteIds

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusManager.clearFocus()
                                    viewModel.playTrackFromSearch(track)
                                }
                                .padding(horizontal = 18.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrlForDisplay(160),
                                contentDescription = track.title,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF282828)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = Typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) colors.accent else Color.White
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Song • ${track.artist}",
                                    style = Typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = Color(0xFFA7A7A7),
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
                                    tint = Color(0xFFA7A7A7),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    if (isSaved) {
                                        viewModel.toggleFavorite(track)
                                    } else {
                                        trackForPlaylist = track
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Default.CheckCircle else Icons.Outlined.AddCircleOutline,
                                    contentDescription = "Save track",
                                    tint = if (isSaved) colors.accent else Color(0xFFA7A7A7),
                                    modifier = Modifier.size(22.dp)
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
                                        .background(Color(0xFF242424)),
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
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.search_not_found_sub),
                                    style = Typography.bodyMedium,
                                    color = Color(0xFFA7A7A7),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        } else if (isSearchActive) {
            // ACTIVE SEARCH BUT QUERY EMPTY (Ref Image 3: Recents + Clear recent searches)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (recentlyPlayed.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recents",
                            style = Typography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)
                        )
                    }

                    items(recentlyPlayed, key = { "recent_${it.id}" }) { track ->
                        val isSaved = track.id in favoriteIds

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusManager.clearFocus()
                                    viewModel.playTrack(track, recentlyPlayed)
                                }
                                .padding(horizontal = 18.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrlForDisplay(160),
                                contentDescription = track.title,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF282828)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = Typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Song • ${track.artist}",
                                    style = Typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = Color(0xFFA7A7A7),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (isSaved) {
                                        viewModel.toggleFavorite(track)
                                    } else {
                                        trackForPlaylist = track
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Default.CheckCircle else Icons.Outlined.AddCircleOutline,
                                    contentDescription = "Save track",
                                    tint = if (isSaved) colors.accent else Color(0xFFB3B3B3),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = { viewModel.removeFromRecents(track.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove from recents",
                                    tint = Color(0xFFB3B3B3),
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
                                    .border(BorderStroke(1.dp, Color(0xFF666666)), RoundedCornerShape(20.dp))
                                    .clickable { viewModel.clearRecentSearches() }
                                    .padding(horizontal = 24.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "Clear recent searches",
                                    style = Typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                    color = Color.White
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
                                text = "Search for artists, songs, or podcasts",
                                style = Typography.bodyMedium,
                                color = Color(0xFFA7A7A7)
                            )
                        }
                    }
                }
            }
        } else {
            // BROWSE MODE (Categories grid + Discover something new)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.search_explore_categories),
                        style = Typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }

                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                    ) {
                        items(categories) { category ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(category.color)
                                    .clickable {
                                        searchQuery = category.searchQuery
                                        isSearchActive = true
                                        viewModel.search(category.searchQuery)
                                    }
                            ) {
                                Text(
                                    text = category.title,
                                    style = Typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 20.sp
                                    ),
                                    color = Color.White,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .align(Alignment.TopStart)
                                        .fillMaxWidth(0.65f)
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 10.dp, y = 6.dp)
                                        .graphicsLayer { rotationZ = 24f }
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.22f))
                                )
                            }
                        }
                    }
                }

                val discoverTracks = trendingTracks.take(6)
                if (discoverTracks.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 22.dp, bottom = 12.dp)) {
                            Text(
                                text = "Discover something new",
                                style = Typography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                color = Color.White
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
                                            .clickable { viewModel.playTrack(track, discoverTracks) }
                                    ) {
                                        AsyncImage(
                                            model = track.thumbnailUrlForDisplay(512),
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(136.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF282828))
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = track.title,
                                            style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            style = Typography.bodySmall.copy(fontSize = 11.sp),
                                            color = Color(0xFFA7A7A7),
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

    if (trackForOptions != null) {
        TrackOptionsBottomSheet(
            track = trackForOptions!!,
            viewModel = viewModel,
            onDismiss = { trackForOptions = null },
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
            onDismiss = { showProfileMenu = false }
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
