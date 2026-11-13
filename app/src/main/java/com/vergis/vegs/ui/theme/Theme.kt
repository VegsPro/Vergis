package com.vergis.vegs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Deep blue glass palette (Zentra / blue quiz refs)
val BgDeep = Color(0xFF050B18)
val BgMid = Color(0xFF0A1628)
val BgCard = Color(0xCC0D1B2A)
val BgCardSolid = Color(0xFF0F1C2E)
val GlassStroke = Color(0x33A5C8FF)
val GlassHighlight = Color(0x22FFFFFF)

val BluePrimary = Color(0xFF3B82F6)
val BlueBright = Color(0xFF60A5FA)
val BlueGlow = Color(0xFF2563EB)
val BlueSoft = Color(0xFF93C5FD)
val CyanAccent = Color(0xFF38BDF8)

val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val Success = Color(0xFF34D399)
val Warning = Color(0xFFFBBF24)
val Error = Color(0xFFF87171)

val GradientBg = Brush.verticalGradient(
    colors = listOf(Color(0xFF07101F), Color(0xFF0B1730), Color(0xFF0A1220))
)

val GradientBlue = Brush.horizontalGradient(
    colors = listOf(Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF60A5FA))
)

val GradientBlueVertical = Brush.verticalGradient(
    colors = listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6))
)

val GlowRadial = Brush.radialGradient(
    colors = listOf(Color(0x443B82F6), Color(0x00050B18)),
    center = Offset(0.5f, 0.2f),
    radius = 900f
)

private val DarkScheme = darkColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    secondary = CyanAccent,
    background = BgDeep,
    surface = BgCardSolid,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Error
)

@Composable
fun VergisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}
