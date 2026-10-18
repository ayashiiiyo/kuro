package com.ayashii.kuro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KuroColorScheme = darkColorScheme(
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

@Composable
fun KuroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KuroColorScheme,
        typography = Typography,
        content = content
    )
}
