package com.ayashii.kuro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ayashii.kuro.ui.components.TrackItem
import com.ayashii.kuro.ui.theme.MutedText
import com.ayashii.kuro.ui.theme.PureBlack
import com.ayashii.kuro.ui.theme.PureWhite
import com.ayashii.kuro.ui.theme.SubtleGray
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val favorites by viewModel.favorites.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(top = 16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Library",
                style = Typography.headlineLarge,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${favorites.size} Liked Songs",
                style = Typography.bodyMedium,
                color = MutedText
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.LibraryMusic,
                        contentDescription = null,
                        tint = SubtleGray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your library is empty",
                        style = Typography.titleMedium,
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the heart icon on any song to save it here",
                        style = Typography.bodyMedium,
                        color = MutedText
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(favorites, key = { it.id }, contentType = { "track" }) { track ->
                    TrackItem(
                        track = track,
                        isPlaying = currentTrack?.id == track.id,
                        isLoading = isLoadingTrack && currentTrack?.id == track.id,
                        onClick = { viewModel.playTrack(track, favorites) },
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
