package com.ayashii.kuro.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ayashii.kuro.R
import com.ayashii.kuro.data.settings.AppLanguage
import com.ayashii.kuro.data.settings.AudioQuality
import com.ayashii.kuro.data.settings.LyricsLanguage
import com.ayashii.kuro.data.settings.SettingsManager
import com.ayashii.kuro.data.settings.ThemeMode
import com.ayashii.kuro.data.update.UpdateInfo
import com.ayashii.kuro.data.update.UpdateManager
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
    val currentLanguage by settingsManager.language.collectAsState()
    val currentLyricsLanguage by settingsManager.lyricsLanguage.collectAsState()
    val currentQuality by settingsManager.audioQuality.collectAsState()
    val autoTranslate by settingsManager.autoTranslate.collectAsState()

    val calculatingLabel = stringResource(R.string.common_calculating)
    var cacheSize by remember { mutableStateOf(calculatingLabel) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var foundUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showAppLangPicker by remember { mutableStateOf(false) }
    var showLyricsLangPicker by remember { mutableStateOf(false) }
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
                .padding(vertical = 24.dp)
                .heightIn(max = 640.dp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
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
                            contentDescription = stringResource(R.string.action_close),
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_audio_quality),
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
                                text = stringResource(quality.titleRes),
                                style = Typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(quality.descRes),
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
                                    contentDescription = stringResource(R.string.common_selected),
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val themeOptions = listOf(
                    Triple(
                        ThemeMode.SYSTEM,
                        stringResource(R.string.settings_theme_system),
                        stringResource(R.string.settings_theme_system_desc)
                    ),
                    Triple(
                        ThemeMode.DARK,
                        stringResource(R.string.settings_theme_dark),
                        stringResource(R.string.settings_theme_dark_desc)
                    ),
                    Triple(
                        ThemeMode.LIGHT,
                        stringResource(R.string.settings_theme_light),
                        stringResource(R.string.settings_theme_light_desc)
                    )
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
                                    contentDescription = stringResource(R.string.common_selected),
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_language),
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_language_desc),
                            style = Typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val currentLangLabel = when (currentLanguage) {
                    AppLanguage.SYSTEM -> stringResource(R.string.lang_system)
                    AppLanguage.INDONESIAN -> stringResource(R.string.lang_id)
                    AppLanguage.ENGLISH -> stringResource(R.string.lang_en)
                    else -> currentLanguage.displayName
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .border(1.dp, colors.accent, RoundedCornerShape(12.dp))
                        .clickable { showAppLangPicker = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentLangLabel,
                            style = Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_choose_language),
                            style = Typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = colors.border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

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
                                text = stringResource(R.string.settings_lyrics_trans),
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.settings_lyrics_trans_desc),
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

                AnimatedVisibility(visible = autoTranslate) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.card)
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                .clickable { showLyricsLangPicker = true }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_lyrics_target_lang),
                                    style = Typography.bodySmall,
                                    color = colors.textMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${currentLyricsLanguage.displayName} (${currentLyricsLanguage.deeplCode.uppercase()})",
                                    style = Typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = colors.border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

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
                                text = stringResource(R.string.settings_cache),
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.settings_cache_size, cacheSize),
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
                                        Toast.makeText(context, context.getString(R.string.settings_cache_cleared), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_cache_clear),
                            style = Typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_update),
                            style = Typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = updateStatusMessage ?: "v${com.ayashii.kuro.BuildConfig.VERSION_NAME}",
                            style = Typography.bodySmall,
                            color = if (updateStatusMessage != null) colors.accent else colors.textMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.card)
                            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                            .clickable(enabled = !isCheckingUpdate) {
                                scope.launch {
                                    isCheckingUpdate = true
                                    updateStatusMessage = context.getString(R.string.settings_update_checking)
                                    val update = UpdateManager.getInstance(context).checkForUpdate(force = true)
                                    isCheckingUpdate = false
                                    if (update != null) {
                                        foundUpdateInfo = update
                                        updateStatusMessage = context.getString(R.string.settings_update_found, update.versionName)
                                    } else {
                                        updateStatusMessage = context.getString(R.string.settings_update_latest)
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (isCheckingUpdate) "…" else stringResource(R.string.settings_update_check),
                            style = Typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

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
                            text = "Kuro v${com.ayashii.kuro.BuildConfig.VERSION_NAME} • Media3 & Jetpack Compose",
                            style = Typography.labelSmall,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_about_desc),
                        style = Typography.labelSmall,
                        fontSize = 10.sp,
                        color = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showAppLangPicker) {
        AlertDialog(
            onDismissRequest = { showAppLangPicker = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_choose_language),
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(AppLanguage.values().toList()) { lang ->
                        val isSelected = currentLanguage == lang
                        val label = when (lang) {
                            AppLanguage.SYSTEM -> stringResource(R.string.lang_system)
                            AppLanguage.INDONESIAN -> stringResource(R.string.lang_id)
                            AppLanguage.ENGLISH -> stringResource(R.string.lang_en)
                            else -> lang.displayName
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.card else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accent else colors.border,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    settingsManager.setLanguage(lang)
                                    showAppLangPicker = false
                                    var ctx: android.content.Context? = context
                                    while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) {
                                        ctx = ctx.baseContext
                                    }
                                    (ctx as? android.app.Activity)?.recreate()
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = Typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = colors.textPrimary
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.common_selected),
                                        tint = colors.background,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppLangPicker = false }) {
                    Text(stringResource(R.string.action_close), color = colors.textPrimary)
                }
            },
            containerColor = colors.surface
        )
    }

    if (showLyricsLangPicker) {
        LyricsLanguagePickerDialog(
            currentLyricsLanguage = currentLyricsLanguage,
            onSelectLanguage = { selected ->
                settingsManager.setLyricsLanguage(selected)
                showLyricsLangPicker = false
            },
            onDismiss = { showLyricsLangPicker = false }
        )
    }

    if (foundUpdateInfo != null) {
        UpdateDialog(
            updateInfo = foundUpdateInfo!!,
            onDownloadClick = {
                UpdateManager.getInstance(context).startDownloadAndInstall(foundUpdateInfo!!)
                foundUpdateInfo = null
            },
            onDismiss = {
                foundUpdateInfo = null
            }
        )
    }
}

@Composable
fun LyricsLanguagePickerDialog(
    currentLyricsLanguage: LyricsLanguage,
    onSelectLanguage: (LyricsLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = KuroAppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.settings_lyrics_target_lang),
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "DeepL Multi-Language Translator",
                    style = Typography.bodySmall,
                    color = colors.textMuted
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(LyricsLanguage.values().toList()) { lang ->
                    val isSelected = currentLyricsLanguage == lang
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.card else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) colors.accent else colors.border,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectLanguage(lang) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lang.displayName,
                                style = Typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "DeepL · ${lang.deeplCode.uppercase()}",
                                style = Typography.labelSmall,
                                color = colors.textMuted
                            )
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.common_selected),
                                    tint = colors.background,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = colors.textPrimary)
            }
        },
        containerColor = colors.surface
    )
}
