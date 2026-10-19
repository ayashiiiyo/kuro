package com.ayashii.kuro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ayashii.kuro.R

val OutfitFontFamily = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold)
)

val FredokaFontFamily = FontFamily(
    Font(R.font.fredoka_bold, FontWeight.Bold)
)

val UbuntuMonoFontFamily = FontFamily.Monospace

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FredokaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
        color = Color.Unspecified
    ),
    headlineMedium = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp,
        color = Color.Unspecified
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = Color.Unspecified
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Color.Unspecified
    ),
    titleSmall = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = Color.Unspecified
    ),
    bodyLarge = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = Color.Unspecified
    ),
    bodyMedium = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = Color.Unspecified
    ),
    bodySmall = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = Color.Unspecified
    ),
    labelLarge = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = Color.Unspecified
    ),
    labelMedium = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = Color.Unspecified
    ),
    labelSmall = TextStyle(
        fontFamily = UbuntuMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
        color = Color.Unspecified
    )
)
