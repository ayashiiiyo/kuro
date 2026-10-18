package com.ayashii.kuro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun SearchScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val focusManager = LocalFocusManager.current

    val browseCategories = listOf(
        "Top Global Hits",
        "Lo-Fi Chill Beats",
        "Anime & J-Pop",
        "Hip-Hop Classics",
        "Acoustic & Folk",
        "Deep Focus & Study",
        "Rock & Alternative",
        "Electronic & Dance"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(top = 16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Search",
                style = Typography.headlineLarge,
                color = PureWhite
            )

            Spacer(modifier = Modifier.height(14.dp))

            TextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.loadSuggestions(it)
                },
                placeholder = {
                    Text(
                        text = "Songs, artists, genres…",
                        color = MutedText,
                        style = Typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon",
                        tint = MutedText
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.search("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MutedText
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    viewModel.search(searchQuery)
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    cursorColor = PureWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Autocomplete suggestions popup / list
        AnimatedVisibility(
            visible = suggestions.isNotEmpty(),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            ) {
                items(suggestions) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                searchQuery = suggestion
                                focusManager.clearFocus()
                                viewModel.search(suggestion)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                tint = SubtleGray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = suggestion,
                                style = Typography.bodyLarge,
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.NorthWest,
                            contentDescription = "Select Suggestion",
                            tint = SubtleGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (suggestions.isEmpty()) {
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PureWhite)
                }
            } else if (searchResults.isEmpty() && searchQuery.isBlank()) {
                // Browse Categories Grid (Apple Music / Spotify Style)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Browse Categories",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(browseCategories) { category ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.8f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkCard)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        searchQuery = category
                                        focusManager.clearFocus()
                                        viewModel.search(category)
                                    }
                                    .padding(14.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Text(
                                    text = category,
                                    style = Typography.titleMedium,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PureWhite
                                )
                            }
                        }
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No tracks found", color = MutedText, style = Typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchResults, key = { it.id }, contentType = { "track" }) { track ->
                        TrackItem(
                            track = track,
                            isPlaying = currentTrack?.id == track.id,
                            isLoading = isLoadingTrack && currentTrack?.id == track.id,
                            onClick = { viewModel.playTrack(track, searchResults) },
                            onFavoriteClick = { viewModel.toggleFavorite(track) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}
