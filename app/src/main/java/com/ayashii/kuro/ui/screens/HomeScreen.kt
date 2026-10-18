package com.ayashii.kuro.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayashii.kuro.ui.components.TrackItem
import com.ayashii.kuro.ui.theme.DarkBorder
import com.ayashii.kuro.ui.theme.DarkCard
import com.ayashii.kuro.ui.theme.MutedText
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.PureWhite
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val isLoading by viewModel.isLoadingTrending.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()

    var selectedGenre by remember { mutableStateOf("Trending") }
    val genres = listOf("Trending", "Top Global", "Pop", "Lo-Fi", "Hip-Hop", "R&B", "Rock", "Acoustic", "Electronic")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(top = 16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Kuro",
                style = Typography.headlineLarge,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Discover & Stream without limits",
                style = Typography.bodyMedium,
                color = MutedText
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Genre Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(genres) { genre ->
                val isSelected = selectedGenre == genre
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) PureWhite else DarkCard)
                        .border(1.dp, if (isSelected) PureWhite else DarkBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            selectedGenre = genre
                            if (genre == "Trending") {
                                viewModel.loadTrending()
                            } else {
                                viewModel.search("$genre music hits")
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = genre,
                        style = Typography.labelSmall,
                        fontSize = 12.sp,
                        color = if (isSelected) PureBlack else PureWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PureWhite)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(trendingTracks, key = { it.id }, contentType = { "track" }) { track ->
                    TrackItem(
                        track = track,
                        isPlaying = currentTrack?.id == track.id,
                        isLoading = isLoadingTrack && currentTrack?.id == track.id,
                        onClick = { viewModel.playTrack(track, trendingTracks) },
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
