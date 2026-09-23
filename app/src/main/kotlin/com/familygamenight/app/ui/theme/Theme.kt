package com.familygamenight.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Castle {
    val Night = Color(0xFF140E0A)
    val Stone = Color(0xFF2B241F)
    val StoneLight = Color(0xFF4A4038)
    val Wood = Color(0xFF5A3620)
    val WoodDark = Color(0xFF2E1A0E)
    val Gold = Color(0xFFD4AF37)
    val GoldPale = Color(0xFFF1D98A)
    val Velvet = Color(0xFF7A1622)
    val Felt = Color(0xFF1F5A3A)
    val Parchment = Color(0xFFF3E7C9)
    val Ink = Color(0xFF231A12)
    val CardRed = Color(0xFFB3172B)
    val CardBlack = Color(0xFF1B1B1B)
}

/** Colours players can pick (ARGB), also used for computer and remote players. */
val PlayerPalette = listOf(
    0xFFB23A48, 0xFF3A6EA5, 0xFF4E8C4A, 0xFFD08C2B, 0xFF7B4FA0,
    0xFF2F8F8A, 0xFFC0567E, 0xFF6B5B3E, 0xFF3D4F9F, 0xFF9C3D22,
)

private val scheme = darkColorScheme(
    primary = Castle.Gold,
    onPrimary = Castle.Ink,
    secondary = Castle.Velvet,
    onSecondary = Color.White,
    tertiary = Castle.Felt,
    background = Castle.Night,
    onBackground = Castle.Parchment,
    surface = Color(0xFF231913),
    onSurface = Castle.Parchment,
    surfaceVariant = Color(0xFF33261C),
    onSurfaceVariant = Color(0xFFD9C9A8),
    surfaceContainer = Color(0xFF2A1F17),
    surfaceContainerHigh = Color(0xFF33261C),
    surfaceContainerHighest = Color(0xFF3D2E22),
    outline = Color(0xFF8C7650),
    error = Color(0xFFE5737A),
)

private val base = Typography()
private val serif = FontFamily.Serif

val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = serif, fontWeight = FontWeight.Bold, color = Castle.GoldPale),
    headlineMedium = base.headlineMedium.copy(fontFamily = serif, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = serif, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = serif, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = serif, fontWeight = FontWeight.SemiBold),
)

val TableLabel = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Castle.Parchment)

@Composable
fun FamilyGameNightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
