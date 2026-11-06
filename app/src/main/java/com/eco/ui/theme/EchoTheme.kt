package com.eco.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EchoBackground = Color(0xFF101A23)
val EchoSurface = Color(0xFF1B2A35)
val EchoTrack = Color(0xFF293944)
val EchoAccent = Color(0xFF72DED1)
val EchoText = Color(0xFFE7EEF1)
val EchoMuted = Color(0xFFA3B0B8)
val EchoBorder = Color(0xFF40515B)

private val EchoColors = darkColorScheme(
    primary = EchoAccent,
    onPrimary = EchoBackground,
    background = EchoBackground,
    onBackground = EchoText,
    surface = EchoSurface,
    onSurface = EchoText,
    secondary = EchoMuted,
)

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EchoColors, content = content)
}
