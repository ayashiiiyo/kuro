package com.ayashii.kuro.ui.motion

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/**
 * KuroFlow Engine — Pure Kotlin/Compose GPU-composited motion system
 * inspired by ColorOS 17 Living Flow Design (Fluid Motion & Flexible Feedback).
 *
 * All transitions operate strictly inside [graphicsLayer] (translation, scale, alpha)
 * without full-screen RenderEffect blurs or layout reflows to maintain 60–120fps.
 */
object KuroFlowEngine {

    val EmphasizedDecelerate = CubicBezierEasing(0.20f, 0.96f, 0.30f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.32f, 0.0f, 0.67f, 0.0f)
    val StandardEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

    val isHighTierDevice: Boolean = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

    /**
     * Smooth fluid spring for page navigation, floating pills, and layout shifts.
     */
    fun <T> fluidSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.84f,
        stiffness = 420f
    )

    /**
     * Elastic rebound spring for ColorOS 17-style Flexible Feedback on cards & buttons.
     */
    fun <T> elasticSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.76f,
        stiffness = 460f
    )

    /**
     * Snappy spring for quick micro-interactions (icons, badges, tab alphas).
     */
    fun <T> snappySpring(): SpringSpec<T> = spring(
        dampingRatio = 0.88f,
        stiffness = 560f
    )

    /**
     * Sheet expansion spring for MiniPlayer -> FullPlayer fluid transition.
     */
    fun <T> sheetSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = 390f
    )

    /**
     * Lightweight, blur-free GPU-only pager transition.
     * Replaces heavy RenderEffect full-screen blur with crisp parallax + subtle depth scale.
     */
    fun Modifier.kuroFlowPagerTransition(
        pageOffsetProvider: () -> Float
    ): Modifier = this.graphicsLayer {
        val rawOffset = pageOffsetProvider().coerceIn(-1f, 1f)
        val absOffset = abs(rawOffset)
        if (absOffset > 0.0005f) {
            val scaleFactor = if (isHighTierDevice) 0.028f else 0.018f
            val parallaxFactor = if (isHighTierDevice) 0.045f else 0.03f
            val s = 1f - (absOffset * scaleFactor)
            scaleX = s
            scaleY = s
            alpha = (1f - (absOffset * 0.18f)).coerceIn(0.82f, 1f)
            translationX = -rawOffset * size.width * parallaxFactor
        } else {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            translationX = 0f
        }
    }

    /**
     * Staged parallax entrance for inner screen sections (GPU-only).
     */
    fun Modifier.kuroCascadeSection(
        stage: Int,
        pageOffsetProvider: () -> Float
    ): Modifier = this.graphicsLayer {
        val rawOffset = pageOffsetProvider().coerceIn(-1f, 1f)
        val absOffset = abs(rawOffset)
        if (absOffset > 0.001f) {
            val staggerMultiplier = 1f + (stage * 0.28f)
            val stagedProgress = (absOffset * staggerMultiplier).coerceIn(0f, 1f)
            alpha = (1f - (stagedProgress * 0.45f)).coerceIn(0.55f, 1f)
            translationX = rawOffset * (12f + stage * 10f) * density
            val s = 1f - (stagedProgress * 0.02f)
            scaleX = s
            scaleY = s
        } else {
            alpha = 1f
            translationX = 0f
            scaleX = 1f
            scaleY = 1f
        }
    }

    /**
     * Flexible Feedback press compression & spring rebound on song cards and interactive controls.
     */
    fun Modifier.kuroElasticPress(
        interactionSource: InteractionSource,
        pressedScale: Float = 0.96f
    ): Modifier = composed {
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) pressedScale else 1f,
            animationSpec = elasticSpring(),
            label = "kuro_elastic_press"
        )
        this.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
}
