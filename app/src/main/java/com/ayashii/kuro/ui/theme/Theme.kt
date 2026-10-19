package com.ayashii.kuro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ayashii.kuro.data.settings.ThemeMode

@Immutable
data class KuroColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val iconPrimary: Color,
    val isDark: Boolean,
    val accent: Color = textPrimary
)

val DarkKuroColors = KuroColors(
    background = PureBlack,
    surface = DarkSurface,
    card = DarkCard,
    border = DarkBorder,
    textPrimary = PureWhite,
    textSecondary = SubtleGray,
    textMuted = MutedText,
    iconPrimary = PureWhite,
    isDark = true,
    accent = PureWhite
)

val LightKuroColors = KuroColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFAFAFA),
    card = Color(0xFFF4F4F5),
    border = Color(0xFFE4E4E7),
    textPrimary = Color(0xFF09090B),
    textSecondary = Color(0xFF71717A),
    textMuted = Color(0xFFA1A1AA),
    iconPrimary = Color(0xFF09090B),
    isDark = false,
    accent = Color(0xFF09090B)
)

val LocalKuroColors = staticCompositionLocalOf { DarkKuroColors }

object KuroAppTheme {
    val colors: KuroColors
        @Composable
        get() = LocalKuroColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    secondary = AccentWhite,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = PureWhite,
    surface = DarkSurface,
    onSurface = PureWhite,
    surfaceVariant = DarkCard,
    onSurfaceVariant = MutedText,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = PureBlack,
    onPrimary = PureWhite,
    secondary = Color(0xFF27272A),
    onSecondary = PureWhite,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF09090B),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF09090B),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF71717A),
    outline = Color(0xFFE4E4E7)
)

@Composable
fun KuroTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val kuroColors = if (isDark) DarkKuroColors else LightKuroColors

    CompositionLocalProvider(LocalKuroColors provides kuroColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
