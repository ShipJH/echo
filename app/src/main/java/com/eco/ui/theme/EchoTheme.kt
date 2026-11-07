package com.eco.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val EchoBackground = Color(0xFF101A23)
val EchoSurface = Color(0xFF1B2A35)
val EchoTrack = Color(0xFF293944)
val EchoAccent = Color(0xFF72DED1)
val EchoText = Color(0xFFE7EEF1)
val EchoMuted = Color(0xFFA3B0B8)
val EchoBorder = Color(0xFF40515B)

/** Shared type scale for vehicle screens; individual screens may emphasize key values. */
object EchoType {
    val headline = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 38.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold)
    val title = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 24.sp, lineHeight = 34.sp)
    val supporting = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 28.sp)
    val label = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
}

private val EchoTypography = Typography(
    displayLarge = EchoType.headline, displayMedium = EchoType.headline, displaySmall = EchoType.headline,
    headlineLarge = EchoType.headline, headlineMedium = EchoType.headline, headlineSmall = EchoType.title,
    titleLarge = EchoType.title, titleMedium = EchoType.title, titleSmall = EchoType.body,
    bodyLarge = EchoType.body, bodyMedium = EchoType.body, bodySmall = EchoType.supporting,
    labelLarge = EchoType.label, labelMedium = EchoType.label, labelSmall = EchoType.supporting,
)

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
    MaterialTheme(colorScheme = EchoColors, typography = EchoTypography, content = content)
}
