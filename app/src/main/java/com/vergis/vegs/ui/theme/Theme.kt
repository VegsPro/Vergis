package com.vergis.vegs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF0B1220)
val SurfaceGlass = Color(0xCC121A2B)
val BorderLight = Color(0x33FFFFFF)
val Accent = Color(0xFF38BDF8)
val AccentDark = Color(0xFF0EA5E9)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val Success = Color(0xFF22C55E)
val Warning = Color(0xFFF59E0B)
val Error = Color(0xFFEF4444)

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    secondary = AccentDark,
    background = DarkBg,
    surface = SurfaceGlass,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Error
)

@Composable
fun VergisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
