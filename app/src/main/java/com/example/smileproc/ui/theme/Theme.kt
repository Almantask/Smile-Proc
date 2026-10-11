package com.example.smileproc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF241C16)
private val InkSoft = Color(0xFF5C5148)
private val Cream = Color(0xFFFBF6EE)
private val Paper = Color(0xFFFFFCF8)
private val Gold = Color(0xFF8A5A22)
private val GoldSoft = Color(0xFFF3E2C0)

private val Night = Color(0xFF141210)
private val NightPaper = Color(0xFF221E1A)
private val NightInk = Color(0xFFF6F0E8)
private val NightInkSoft = Color(0xFFCFC3B6)
private val NightGold = Color(0xFFE6C27A)

private val LightColors = lightColorScheme(
    primary = Gold,
    onPrimary = Color.White,
    primaryContainer = GoldSoft,
    onPrimaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    onSurfaceVariant = InkSoft,
    surfaceVariant = Color(0xFFF3EBDF),
    outline = Color(0xFFD9CBB8),
)

private val DarkColors = darkColorScheme(
    primary = NightGold,
    onPrimary = Color(0xFF2A2116),
    primaryContainer = Color(0xFF4A3820),
    onPrimaryContainer = NightGold,
    background = Night,
    onBackground = NightInk,
    surface = NightPaper,
    onSurface = NightInk,
    onSurfaceVariant = NightInkSoft,
    surfaceVariant = Color(0xFF2C2722),
    outline = Color(0xFF4A433C),
)

@Composable
fun SmileProcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = SmileProcTypography,
        content = content,
    )
}
