package com.ayashii.kuro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
        val totalPlays = viewModel.tasteManager.getTotalPlays()
        val topArtists = viewModel.tasteManager.getTopArtists(5)

        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = {
                Text(
                    text = "📊 Statistik Selera Musik",
                    style = Typography.titleLarge,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Total lagu didengarkan: $totalPlays kali",
                        style = Typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Artis Paling Sering Kamu Dengar:",
                        style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (topArtists.isEmpty()) {
                        Text(
                            text = "Belum cukup data. Putar lagu favoritmu sekarang!",
                            style = Typography.bodyMedium,
                            color = colors.textMuted
                        )
                    } else {
                        topArtists.forEachIndexed { idx, (artist, count) ->
                            Text(
                                text = "#${idx + 1} $artist (${count}x)",
                                style = Typography.bodyMedium,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
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
                Text(
                    text = "🕒 Terakhir Diputar",
                    style = Typography.titleLarge,
                    color = colors.textPrimary
                )
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
                                    model = track.thumbnailUrl,
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
