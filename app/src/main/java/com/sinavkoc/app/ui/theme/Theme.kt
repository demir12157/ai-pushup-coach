package com.sinavkoc.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF090D16)
val SurfaceDark = Color(0xFF111827)
val SurfaceElevated = Color(0xFF162033)
val BorderSlate = Color(0xFF1E293B)
val Emerald = Color(0xFF10B981)
val EmeraldDark = Color(0xFF064E3B)
val Sky = Color(0xFF38BDF8)
val Amber = Color(0xFFF59E0B)
val CoralRed = Color(0xFFEF4444)
val Purple = Color(0xFFA855F7)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

private val DarkScheme = darkColorScheme(
    primary = Emerald,
    onPrimary = BgDark,
    secondary = Sky,
    onSecondary = BgDark,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    error = CoralRed
)

@Composable
fun SinavKocTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}
