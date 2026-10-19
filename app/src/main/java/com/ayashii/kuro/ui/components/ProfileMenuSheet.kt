package com.ayashii.kuro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ayashii.kuro.data.auth.UserAccount
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuSheet(
    account: UserAccount?,
    viewModel: MusicViewModel,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = KuroAppTheme.colors
    var showStatsDialog by remember { mutableStateOf(false) }
    var showRecentsDialog by remember { mutableStateOf(false) }

    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val onRepeatTracks by viewModel.onRepeatTracks.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Profile & Account Card (Spotify-Style Upper Section)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (account == null) {
                                onLoginClick()
                                onDismiss()
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (account?.avatarUrl != null && account.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = account.avatarUrl,
                            contentDescription = account.name,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, colors.accent, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(colors.border),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account?.name ?: "Kuro Music Lover",
                            style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (account != null) (account.email.ifBlank { "Akun Google Terhubung" }) else "Ketuk untuk Login Google",
                            style = Typography.bodyMedium,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Action Row (Add Account / Switch / Logout)
                if (account != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLogoutClick()
                                onDismiss()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colors.border),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Logout,
                                contentDescription = "Logout",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Ganti / Keluar Akun",
                            style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLoginClick()
                                onDismiss()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = colors.background,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Hubungkan Akun Google",
                            style = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Navigation List (Spotify-Style)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Listening Stats (Taste Profile)
                DrawerMenuItem(
                    icon = Icons.Outlined.BarChart,
                    title = "Statistik Mendengarkan",
                    subtitle = "Analisis selera & artis favoritmu",
                    onClick = { showStatsDialog = true }
                )

                // 2. Recents
                DrawerMenuItem(
                    icon = Icons.Outlined.History,
                    title = "Terakhir Diputar",
                    subtitle = "${recentlyPlayed.size} lagu baru saja diputar",
                    onClick = { showRecentsDialog = true }
                )

                // 3. Settings & Privacy
                DrawerMenuItem(
                    icon = Icons.Outlined.Settings,
                    title = "Pengaturan & Privasi",
                    subtitle = "Kualitas audio, tema, cache & preferensi",
                    onClick = {
                        onDismiss()
                        onOpenSettingsClick()
                    }
                )
            }
        }
    }

    // Listening Stats Dialog
    if (showStatsDialog) {
        val totalPlays = remember(showStatsDialog) { viewModel.tasteManager.getTotalPlays() }
        val uniqueArtists = remember(showStatsDialog) { viewModel.tasteManager.getUniqueArtistsCount() }
        val topArtists = remember(showStatsDialog) { viewModel.tasteManager.getTopArtists(5) }

        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.BarChart,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Statistik Mendengarkan",
                        style = Typography.titleLarge,
                        color = colors.textPrimary
                    )
                }
            },
            text = {
                ListeningStatsDiagram(
                    totalPlays = totalPlays,
                    uniqueArtists = uniqueArtists,
                    topArtists = topArtists
                )
            },
            confirmButton = {
                TextButton(onClick = { showStatsDialog = false }) {
                    Text("Tutup", color = colors.textPrimary)
                }
            },
            containerColor = colors.background
        )
    }

    // Recents Dialog
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
                        text = "Terakhir Diputar",
                        style = Typography.titleLarge,
                        color = colors.textPrimary
                    )
                }
            },
            text = {
                if (recentlyPlayed.isEmpty()) {
                    Text(
                        text = "Belum ada riwayat lagu yang diputar.",
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
                                        viewModel.playTrack(track)
                                        showRecentsDialog = false
                                        onDismiss()
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = track.highResThumbnailUrl,
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
                    Text("Tutup", color = colors.textPrimary)
                }
            },
            containerColor = colors.background
        )
    }
}

@Composable
private fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = KuroAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.card),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = Typography.bodySmall,
                color = colors.textMuted
            )
        }
    }
}

@Composable
fun ListeningStatsDiagram(
    totalPlays: Int,
    uniqueArtists: Int,
    topArtists: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    val colors = KuroAppTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Summary Metric Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tile 1: Total Putar
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Total Putar",
                    style = Typography.bodySmall,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$totalPlays kali",
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
            }

            // Tile 2: Artis Didengar
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Artis Didengar",
                    style = Typography.bodySmall,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$uniqueArtists artis",
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (topArtists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum cukup data. Putar lebih banyak lagu untuk melihat diagram!",
                    style = Typography.bodyMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val maxCount = topArtists.maxOf { it.second }.coerceAtLeast(1)

            // Visual Column Bar Chart
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DIAGRAM FREKUENSI ARTIS",
                        style = Typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = colors.textMuted
                    )
                    Text(
                        text = "Top ${topArtists.size}",
                        style = Typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Vertical Columns
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    topArtists.forEachIndexed { index, (_, count) ->
                        val barHeightFraction = (count.toFloat() / maxCount.toFloat()).coerceIn(0.12f, 1f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .weight(1f)
                                .height(130.dp)
                        ) {
                            Text(
                                text = "${count}x",
                                style = Typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Bar Track Container
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height(86.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(colors.border),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(barHeightFraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(colors.accent)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "#${index + 1}",
                                style = Typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Detailed Horizontal Progress Distribution
            Text(
                text = "Peringkat Artis Teratas",
                style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            topArtists.forEachIndexed { index, (artist, count) ->
                val pct = if (totalPlays > 0) (count * 100 / totalPlays) else 0
                val progressFraction = (count.toFloat() / maxCount.toFloat()).coerceIn(0.05f, 1f)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${index + 1}  $artist",
                            style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${count}x ($pct%)",
                            style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.textMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.border)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.accent)
                        )
                    }
                }
            }
        }
    }
}
