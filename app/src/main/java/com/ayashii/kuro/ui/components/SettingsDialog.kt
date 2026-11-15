package com.ayashii.kuro.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ayashii.kuro.BuildConfig
import com.ayashii.kuro.R
import com.ayashii.kuro.data.settings.AppLanguage
import com.ayashii.kuro.data.settings.AudioQuality
import com.ayashii.kuro.data.settings.AudioSubQuality
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
    val viewContext = LocalView.current.context
    val colors = KuroAppTheme.colors
    val scope = rememberCoroutineScope()
    val settingsManager = remember {
        runCatching { SettingsManager.getInstance() }.getOrElse { SettingsManager.init(context) }
    }

    val currentTheme by settingsManager.themeMode.collectAsState()
    val currentLanguage by settingsManager.language.collectAsState()
    val currentLyricsLanguage by settingsManager.lyricsLanguage.collectAsState()
    val currentQuality by settingsManager.audioQuality.collectAsState()
    val currentSubQuality by settingsManager.audioSubQuality.collectAsState()
    val autoTranslate by settingsManager.autoTranslate.collectAsState()
    val downloadTreeUri by settingsManager.downloadTreeUri.collectAsState()
    val downloadSubQuality by settingsManager.downloadSubQuality.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            settingsManager.setDownloadTreeUri(uri.toString())
            Toast.makeText(context, context.getString(R.string.settings_offline_folder_saved), Toast.LENGTH_SHORT).show()
        }
    }

    val calculatingLabel = stringResource(R.string.common_calculating)
    var cacheSize by remember { mutableStateOf(calculatingLabel) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var foundUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    var showAudioQualityPicker by remember { mutableStateOf(false) }
    var showDownloadQualityPicker by remember { mutableStateOf(false) }
    var showAppLangPicker by remember { mutableStateOf(false) }
    var showLyricsLangPicker by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        cacheSize = settingsManager.getFormattedCacheSizeAsync()
    }

    val isIndonesianLocale = remember(currentLanguage) {
        val loc = settingsManager.resolveEffectiveLocale(currentLanguage).language.lowercase()
        loc == "in" || loc == "id"
    }

    val currentLangLabel = when (currentLanguage) {
        AppLanguage.SYSTEM -> stringResource(R.string.lang_system)
        AppLanguage.INDONESIAN -> stringResource(R.string.lang_id)
        AppLanguage.ENGLISH -> stringResource(R.string.lang_en)
        else -> currentLanguage.displayName
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.border.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 28.dp)
                ) {
                    SettingsSectionHeader(text = stringResource(R.string.settings_section_audio_offline))
                    SettingsGroupCard {
                        SettingsActionRow(
                            icon = Icons.Outlined.GraphicEq,
                            title = stringResource(R.string.settings_audio_quality),
                            subtitle = "${stringResource(currentQuality.titleRes)} • ${currentSubQuality.badge}",
                            onClick = { showAudioQualityPicker = true }
                        )
                        SettingsRowDivider()
                        SettingsActionRow(
                            icon = Icons.Outlined.Download,
                            title = stringResource(R.string.settings_download_quality),
                            subtitle = downloadSubQuality.badge,
                            onClick = { showDownloadQualityPicker = true }
                        )
                        SettingsRowDivider()
                        SettingsActionRow(
                            icon = Icons.Outlined.FolderOpen,
                            title = stringResource(R.string.settings_offline_folder),
                            subtitle = downloadTreeUri?.let {
                                runCatching { Uri.parse(it).lastPathSegment }.getOrNull() ?: it
                            } ?: stringResource(R.string.settings_offline_folder_default),
                            onClick = { folderPickerLauncher.launch(null) },
                            trailing = if (downloadTreeUri != null) {
                                {
                                    Text(
                                        text = stringResource(R.string.settings_action_reset),
                                        style = Typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { settingsManager.setDownloadTreeUri(null) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else null
                        )
                        SettingsRowDivider()
                        SettingsActionRow(
                            icon = Icons.Outlined.DeleteOutline,
                            title = stringResource(R.string.settings_cache),
                            subtitle = stringResource(R.string.settings_cache_size, cacheSize),
                            onClick = {
                                scope.launch {
                                    val success = settingsManager.clearCacheAsync()
                                    if (success) {
                                        cacheSize = settingsManager.getFormattedCacheSizeAsync()
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.settings_cache_cleared),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            trailing = {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(colors.surface)
                                        .border(1.dp, colors.border, RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_cache_clear),
                                        style = Typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsSectionHeader(text = stringResource(R.string.settings_section_appearance_lang))
                    SettingsGroupCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIconBadge(icon = Icons.Outlined.Palette)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.settings_theme),
                                    style = Typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.surface)
                                    .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(50))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val themeItems = listOf(
                                    ThemeMode.SYSTEM to stringResource(R.string.settings_theme_short_system),
                                    ThemeMode.DARK to stringResource(R.string.settings_theme_short_dark),
                                    ThemeMode.LIGHT to stringResource(R.string.settings_theme_short_light)
                                )
                                themeItems.forEach { (mode, label) ->
                                    val selected = currentTheme == mode
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(if (selected) colors.accent else Color.Transparent)
                                            .clickable { settingsManager.setThemeMode(mode) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = Typography.labelSmall,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) colors.background else colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        SettingsRowDivider()

                        SettingsActionRow(
                            icon = Icons.Outlined.Language,
                            title = stringResource(R.string.settings_language),
                            subtitle = currentLangLabel,
                            onClick = { showAppLangPicker = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsSectionHeader(text = stringResource(R.string.settings_section_lyrics))
                    SettingsGroupCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIconBadge(icon = Icons.Outlined.Translate)
                                Spacer(modifier = Modifier.width(12.dp))
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
                                        color = colors.textMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = autoTranslate,
                                onCheckedChange = { settingsManager.setAutoTranslate(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.background,
                                    checkedTrackColor = colors.accent,
                                    uncheckedThumbColor = colors.textSecondary,
                                    uncheckedTrackColor = colors.surface
                                )
                            )
                        }

                        AnimatedVisibility(visible = autoTranslate) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SettingsRowDivider()
                                SettingsActionRow(
                                    icon = Icons.Outlined.Language,
                                    title = stringResource(R.string.settings_lyrics_target_lang),
                                    subtitle = "${currentLyricsLanguage.displayName} (${currentLyricsLanguage.deeplCode.uppercase()})",
                                    onClick = { showLyricsLangPicker = true }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsSectionHeader(text = stringResource(R.string.settings_section_about))
                    SettingsGroupCard {
                        SettingsActionRow(
                            icon = Icons.Outlined.Info,
                            title = stringResource(R.string.settings_update),
                            subtitle = updateStatusMessage ?: "v${BuildConfig.VERSION_NAME}",
                            onClick = {
                                if (!isCheckingUpdate) {
                                    scope.launch {
                                        isCheckingUpdate = true
                                        updateStatusMessage = context.getString(R.string.settings_update_checking)
                                        val update = UpdateManager.getInstance(context).checkForUpdate(force = true)
                                        isCheckingUpdate = false
                                        if (update != null) {
                                            foundUpdateInfo = update
                                            updateStatusMessage = context.getString(
                                                R.string.settings_update_found,
                                                update.versionName
                                            )
                                        } else {
                                            updateStatusMessage = context.getString(R.string.settings_update_latest)
                                        }
                                    }
                                }
                            },
                            trailing = {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(colors.surface)
                                        .border(1.dp, colors.border, RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (isCheckingUpdate) "…" else stringResource(R.string.settings_update_check),
                                        style = Typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Kuro v${BuildConfig.VERSION_NAME} • Media3 & Jetpack Compose",
                            style = Typography.labelSmall,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_about_desc),
                            style = Typography.labelSmall,
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }
    }

    if (showAudioQualityPicker) {
        AlertDialog(
            onDismissRequest = { showAudioQualityPicker = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.settings_audio_quality),
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioQuality.values().forEach { quality ->
                        val isSelected = currentQuality == quality
                        val subOptions = remember(quality) { AudioSubQuality.optionsFor(quality) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) colors.card else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accent else colors.border,
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                    Text(
                                        text = stringResource(quality.descRes),
                                        style = Typography.bodySmall,
                                        color = colors.textMuted
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.common_selected),
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isSelected && subOptions.size > 1) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                                    subOptions.forEach { sub ->
                                        val isSubSelected = currentSubQuality == sub
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSubSelected) colors.surface else Color.Transparent)
                                                .border(
                                                    1.dp,
                                                    if (isSubSelected) colors.accent.copy(alpha = 0.85f) else colors.border.copy(alpha = 0.45f),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable { settingsManager.setAudioSubQuality(sub) }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = sub.badge,
                                                    style = Typography.bodySmall,
                                                    fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = colors.textPrimary
                                                )
                                                Text(
                                                    text = if (isIndonesianLocale) sub.subtitleId else sub.subtitleEn,
                                                    style = Typography.labelSmall,
                                                    color = colors.textMuted
                                                )
                                            }
                                            if (isSubSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = colors.accent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioQualityPicker = false }) {
                    Text(stringResource(R.string.action_close), color = colors.textPrimary)
                }
            }
        )
    }

    if (showDownloadQualityPicker) {
        AlertDialog(
            onDismissRequest = { showDownloadQualityPicker = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.settings_download_quality),
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
                    items(AudioSubQuality.values().toList()) { sub ->
                        val selected = sub == downloadSubQuality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) colors.card else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (selected) colors.accent else colors.border,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    settingsManager.setDownloadSubQuality(sub)
                                    showDownloadQualityPicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sub.badge,
                                    style = Typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (isIndonesianLocale) sub.subtitleId else sub.subtitleEn,
                                    style = Typography.labelSmall,
                                    color = colors.textMuted
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadQualityPicker = false }) {
                    Text(stringResource(R.string.action_close), color = colors.textPrimary)
                }
            }
        )
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
                                    var ctx: Context? = viewContext
                                    while (ctx is ContextWrapper && ctx !is Activity) {
                                        ctx = ctx.baseContext
                                    }
                                    (ctx as? Activity)?.recreate()
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
private fun SettingsSectionHeader(text: String) {
    val colors = KuroAppTheme.colors
    Text(
        text = text,
        style = Typography.labelSmall,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
        color = colors.textMuted,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = KuroAppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
        content = content
    )
}

@Composable
private fun SettingsRowDivider() {
    val colors = KuroAppTheme.colors
    HorizontalDivider(
        color = colors.border.copy(alpha = 0.5f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 62.dp)
    )
}

@Composable
private fun SettingsIconBadge(icon: ImageVector) {
    val colors = KuroAppTheme.colors
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = KuroAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            SettingsIconBadge(icon = icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Text(
                    text = subtitle,
                    style = Typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
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
