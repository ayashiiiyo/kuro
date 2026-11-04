package com.ayashii.kuro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.ayashii.kuro.R
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.ayashii.kuro.ui.components.ProfileMenuSheet
import com.ayashii.kuro.ui.components.TrackOptionsBottomSheet
import com.ayashii.kuro.ui.components.TrackItem
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
    var trackForPlaylist by remember { mutableStateOf<com.ayashii.kuro.data.model.Track?>(null) }
    var trackForOptions by remember { mutableStateOf<com.ayashii.kuro.data.model.Track?>(null) }
    var showProfileMenu by remember { mutableStateOf(false) }

    val searchResults by viewModel.searchResults.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val currentAccount by viewModel.authState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by remember(favorites) { androidx.compose.runtime.derivedStateOf { favorites.mapTo(HashSet()) { it.id } } }
    val focusManager = LocalFocusManager.current

    val colors = com.ayashii.kuro.ui.theme.KuroAppTheme.colors

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 16.dp)
    ) {
        // Top Header: Avatar, Search, Camera (Ref Image 2)
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

        Spacer(modifier = Modifier.height(10.dp))

        // White Search Bar (Ref Image 2)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            TextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.loadSuggestions(it)
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_what_do_you_want_to_listen_to),
                        color = Color(0xFF535353),
                        style = Typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon",
                        tint = Color(0xFF121212),
                        modifier = Modifier.size(22.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            focusManager.clearFocus()
                            viewModel.search("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFF121212),
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
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color(0xFF121212),
                    unfocusedTextColor = Color(0xFF121212),
                    cursorColor = Color(0xFF121212)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (suggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.search_suggestions),
                        style = Typography.labelMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(suggestions) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                searchQuery = suggestion
                                focusManager.clearFocus()
                                viewModel.search(suggestion)
                            }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = suggestion,
                                style = Typography.bodyLarge,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.NorthWest,
                            contentDescription = "Pilih",
                            tint = colors.textMuted.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        } else if (isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = colors.textPrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else if (searchResults.isEmpty() && searchQuery.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.search_explore_categories),
                        style = Typography.titleLarge,
                        color = colors.textPrimary
                    )
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
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
                                    focusManager.clearFocus()
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

                            // Decorative angled album cover mockup in bottom right (Ref Image 2)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 10.dp, y = 6.dp)
                                    .graphicsLayer {
                                        rotationZ = 24f
                                    }
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.22f))
                            )
                        }
                    }

                    val discoverTracks = trendingTracks.take(6)
                    if (discoverTracks.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            Column(modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)) {
                                Text(
                                    text = "Discover something new",
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
                                                .clickable { viewModel.playTrack(track, discoverTracks) }
                                        ) {
                                            AsyncImage(
                                                model = track.thumbnailUrlForDisplay(512),
                                                contentDescription = track.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(136.dp)
                                                    .clip(RoundedCornerShape(8.dp))
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

                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        } else if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.border, CircleShape),
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        text = stringResource(R.string.search_results),
                        style = Typography.labelMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp)
                    )
                }
                items(searchResults, key = { it.id }, contentType = { "track" }) { track ->
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
                    Spacer(modifier = Modifier.height(100.dp))
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
        com.ayashii.kuro.ui.components.AddToPlaylistDialog(
            track = trackForPlaylist!!,
            viewModel = viewModel,
            onDismiss = { trackForPlaylist = null }
        )
    }
}
