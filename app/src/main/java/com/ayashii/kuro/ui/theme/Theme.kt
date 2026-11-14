package com.ayashii.kuro.ui.theme

import android.graphics.RenderEffect as FrameworkRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.ayashii.kuro.data.settings.SettingsManager
import com.ayashii.kuro.data.settings.ThemeMode
import kotlin.math.abs

object KuroMotion {
    val EmphasizedDecelerate = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.32f, 0.0f, 0.67f, 0.0f)
    val StandardEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
    val AppleDecelerate = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

    val is64BitDevice: Boolean = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

    fun <T> appleFluidSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.84f,
        stiffness = 400f
    )

    fun <T> appleSnappySpring(): SpringSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 520f
    )

    fun <T> appleInteractiveSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.90f,
        stiffness = 680f
    )

    fun Modifier.pagerMotionBlurTransition(
        pageOffsetProvider: () -> Float,
        maxBlurPx: Float = 16f
    ): Modifier = this.graphicsLayer {
        val rawOffset = pageOffsetProvider().coerceIn(-1f, 1f)
        val absOffset = abs(rawOffset)
        val motionCurve = (4f * absOffset * (1f - absOffset)).coerceIn(0f, 1f)
        alpha = (1f - (absOffset * 0.28f)).coerceIn(0.72f, 1f)
        translationX = -rawOffset * size.width * 0.06f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && is64BitDevice) {
            val blurX = motionCurve * maxBlurPx
            if (blurX > 0.6f) {
                renderEffect = FrameworkRenderEffect.createBlurEffect(
                    blurX,
                    (blurX * 0.18f).coerceAtLeast(0.5f),
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            } else {
                renderEffect = null
            }
        }
    }
}

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

    val baseContext = LocalContext.current
    val settingsManager = remember {
        try {
            SettingsManager.getInstance()
        } catch (_: Throwable) {
            SettingsManager.init(baseContext)
        }
    }
    val currentLanguage by settingsManager.language.collectAsState()
    val localizedContext = remember(baseContext, currentLanguage) {
        settingsManager.createLocalizedContext(baseContext, currentLanguage)
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val kuroColors = if (isDark) DarkKuroColors else LightKuroColors

    CompositionLocalProvider(
        LocalKuroColors provides kuroColors,
        LocalContext provides localizedContext
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
