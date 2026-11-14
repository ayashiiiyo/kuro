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

    private val cachedBlurSteps: Array<androidx.compose.ui.graphics.RenderEffect?> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scale = if (is64BitDevice) 1.0f else 0.75f
            arrayOf(
                null,
                FrameworkRenderEffect.createBlurEffect(3.2f * scale, 0.7f * scale, Shader.TileMode.CLAMP).asComposeRenderEffect(),
                FrameworkRenderEffect.createBlurEffect(6.5f * scale, 1.2f * scale, Shader.TileMode.CLAMP).asComposeRenderEffect(),
                FrameworkRenderEffect.createBlurEffect(10.5f * scale, 1.8f * scale, Shader.TileMode.CLAMP).asComposeRenderEffect()
            )
        } else {
            emptyArray()
        }
    }

    fun <T> appleFluidSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.90f,
        stiffness = 620f
    )

    fun <T> appleSnappySpring(): SpringSpec<T> = spring(
        dampingRatio = 0.92f,
        stiffness = 740f
    )

    fun <T> appleInteractiveSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.94f,
        stiffness = 860f
    )

    fun Modifier.pagerMotionBlurTransition(
        pageOffsetProvider: () -> Float,
        maxBlurPx: Float = 16f
    ): Modifier = this.graphicsLayer {
        val rawOffset = pageOffsetProvider().coerceIn(-1f, 1f)
        val absOffset = abs(rawOffset)
        val motionCurve = (4f * absOffset * (1f - absOffset)).coerceIn(0f, 1f)
        alpha = (1f - (absOffset * 0.22f)).coerceIn(0.78f, 1f)
        translationX = -rawOffset * size.width * 0.07f
        scaleX = 1f + (motionCurve * 0.026f)
        scaleY = 1f - (motionCurve * 0.013f)
        if (cachedBlurSteps.isNotEmpty() && maxBlurPx > 0f) {
            val step = (motionCurve * 3.4f).toInt().coerceIn(0, 3)
            renderEffect = cachedBlurSteps[step]
        }
    }

    fun Modifier.cascadeSection(
        stage: Int,
        pageOffsetProvider: () -> Float
    ): Modifier = this.graphicsLayer {
        val rawOffset = pageOffsetProvider().coerceIn(-1f, 1f)
        val absOffset = abs(rawOffset)
        if (absOffset > 0.001f) {
            val staggerMultiplier = 1f + (stage * 0.38f)
            val stagedProgress = (absOffset * staggerMultiplier).coerceIn(0f, 1f)
            alpha = (1f - (stagedProgress * 0.65f)).coerceIn(0.35f, 1f)
            translationX = rawOffset * (18f + stage * 16f) * density
            translationY = stagedProgress * (8f + stage * 7f) * density
            val s = 1f - (stagedProgress * 0.03f)
            scaleX = s
            scaleY = s
        } else {
            alpha = 1f
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
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
