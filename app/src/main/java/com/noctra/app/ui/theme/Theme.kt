package com.noctra.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NC {
    val Ink = Color(0xFF12102A)
    val InkMid = Color(0xFF211C4F)
    val InkCard = Color(0xFF1F1B47)
    val Violet = Color(0xFF7C5CF2)
    val VioletSoft = Color(0xFFB9A7FF)
    val VioletTint = Color(0xFFE6E0FF)
    val Canvas = Color(0xFFF3F1FF)
    val Slate = Color(0xFF1B1840)
    val Muted = Color(0xFF7A76A3)
    val Green = Color(0xFF34D399)
    val Pink = Color(0xFFF472B6)
    val Amber = Color(0xFFFBBF24)
    val Blue = Color(0xFF60A5FA)
    val Danger = Color(0xFFE5484D)
}

private val LightScheme = lightColorScheme(
    primary = NC.Violet,
    onPrimary = Color.White,
    primaryContainer = NC.VioletTint,
    onPrimaryContainer = NC.Slate,
    background = NC.Canvas,
    onBackground = NC.Slate,
    surface = Color.White,
    onSurface = NC.Slate,
    surfaceVariant = Color(0xFFEFECFF),
    onSurfaceVariant = NC.Muted,
    outline = Color(0xFFD7D1F5),
    error = NC.Danger
)

private val DarkScheme = darkColorScheme(
    primary = NC.VioletSoft,
    onPrimary = NC.Ink,
    primaryContainer = Color(0xFF3A2F7A),
    onPrimaryContainer = Color.White,
    background = NC.Ink,
    onBackground = Color.White,
    surface = NC.InkCard,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2559),
    onSurfaceVariant = Color(0xFFB4AFDB),
    outline = Color(0xFF3B3670),
    error = Color(0xFFFF8A8F)
)

private val NcTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

private val NcShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NoctraTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = NcTypography,
        shapes = NcShapes,
        content = content
    )
}
