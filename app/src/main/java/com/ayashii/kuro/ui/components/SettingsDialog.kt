package com.ayashii.kuro.ui.components

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ayashii.kuro.data.settings.AudioQuality
import com.ayashii.kuro.data.settings.SettingsManager
import com.ayashii.kuro.data.settings.ThemeMode
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = KuroAppTheme.colors
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance() }
    val currentTheme by settingsManager.themeMode.collectAsState()
    val currentQuality by settingsManager.audioQuality.collectAsState()
    val autoTranslate by settingsManager.autoTranslate.collectAsState()

    var cacheSize by remember { mutableStateOf("Menghitung...") }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        cacheSize = settingsManager.getFormattedCacheSizeAsync()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pengaturan",
                        style = Typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 1. Audio Quality Section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kualitas Audio Streaming",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                AudioQuality.values().forEach { quality ->
                    val isSelected = currentQuality == quality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.card else colors.surface)
                            .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(12.dp))
                            .clickable { settingsManager.setAudioQuality(quality) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quality.title,
                                style = Typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = quality.description,
                                style = Typography.bodySmall,
                                color = colors.textMuted
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Dipilih",
                                    tint = colors.background,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = colors.border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 2. Theme Selection
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tema Tampilan",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val themeOptions = listOf(
                    Triple(ThemeMode.SYSTEM, "Mengikuti Sistem", "Sesuai tema mode HP kamu"),
                    Triple(ThemeMode.DARK, "Dark Mode", "Hitam OLED hemat baterai"),
                    Triple(ThemeMode.LIGHT, "Light Mode", "Putih monokrom bersih")
                )

                themeOptions.forEach { (mode, title, desc) ->
                    val isSelected = currentTheme == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.card else colors.surface)
                            .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(12.dp))
                            .clickable { settingsManager.setThemeMode(mode) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = Typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                style = Typography.bodySmall,
                                color = colors.textMuted
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Dipilih",
                                    tint = colors.background,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = colors.border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 3. Lyrics Translation Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Terjemahan Indonesia Lirik",
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Tampilkan arti lirik bahasa Indonesia",
                                style = Typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }

                    Switch(
                        checked = autoTranslate,
                        onCheckedChange = { settingsManager.setAutoTranslate(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.background,
                            checkedTrackColor = colors.accent,
                            uncheckedThumbColor = colors.textSecondary,
                            uncheckedTrackColor = colors.card
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = colors.border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 4. Storage & Cache
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Penyimpanan & Cache",
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Ukuran Cache: $cacheSize",
                                style = Typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.card)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch {
                                    val success = settingsManager.clearCacheAsync()
                                    if (success) {
                                        cacheSize = settingsManager.getFormattedCacheSizeAsync()
                                        Toast.makeText(context, "Cache berhasil dibersihkan!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Bersihkan",
                            style = Typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 5. About / Version Footer
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Kuro v3.3.0 • Media3 & Jetpack Compose",
                            style = Typography.labelSmall,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Open source, zero ads & ultra lightweight",
                        style = Typography.labelSmall,
                        fontSize = 10.sp,
                        color = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
