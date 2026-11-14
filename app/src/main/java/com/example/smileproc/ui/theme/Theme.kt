package com.example.smileproc.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.smileproc.core.Mood

@Immutable
data class SmileProcColors(
    val isDark: Boolean,
    val canvasBase: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassHighlightTop: Color,
    val glassHighlightBottom: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val glassDivider: Color,
    val pillFillTop: Color,
    val pillFillBottom: Color,
    val pillBorder: Color,
    val chipFill: Color,
    val chipBorder: Color,
    val navBarFill: Color,
    val navBarBorder: Color,
    val navActiveGlow: Color,
    val navActivePill: Color,
    val navActiveBorder: Color,
    val navActiveContent: Color,
    val navInactiveContent: Color,
    val sheetContainer: Color,
    val dialogContainer: Color,
    val brandAccent: Color,
    val brandWordmark: Color,
    val moodLow: Color,
    val moodLowFeature: Color,
    val moodLowGlow: Color,
    val moodOkay: Color,
    val moodOkayFeature: Color,
    val moodOkayGlow: Color,
    val moodGood: Color,
    val moodGoodFeature: Color,
    val moodGoodGlow: Color,
    val wheelSelectionStroke: Color,
    val successBadgeBg: Color,
    val successBadgeFg: Color,
    val warningBadgeBg: Color,
    val warningBadgeFg: Color,
)

fun SmileProcColors.moodAccent(mood: Mood): Color = when (mood) {
    Mood.LOW -> moodLow
    Mood.OKAY -> moodOkay
    Mood.GOOD -> moodGood
}

fun SmileProcColors.moodGlow(mood: Mood): Color = when (mood) {
    Mood.LOW -> moodLowGlow
    Mood.OKAY -> moodOkayGlow
    Mood.GOOD -> moodGoodGlow
}

val DarkSmileProcColors = SmileProcColors(
    isDark = true,
    canvasBase = Color(0xFF0A0B14),
    glowPrimary = Color(0x888B5CF6),
    glowSecondary = Color(0x54FF8A70),
    glassTop = Color(0xBA242338),
    glassBottom = Color(0xA6151524),
    glassHighlightTop = Color(0xCC312B4D),
    glassHighlightBottom = Color(0xB81D1A33),
    glassBorderTop = Color(0x42FFFFFF),
    glassBorderBottom = Color(0x14FFFFFF),
    glassDivider = Color(0x1AFFFFFF),
    pillFillTop = Color(0xBA201F30),
    pillFillBottom = Color(0xAA161522),
    pillBorder = Color(0x2BFFFFFF),
    chipFill = Color(0x26FFFFFF),
    chipBorder = Color(0x22FFFFFF),
    navBarFill = Color(0xDF10111D),
    navBarBorder = Color(0x20FFFFFF),
    navActiveGlow = Color(0x8A9F85FF),
    navActivePill = Color(0x5C7C69EE),
    navActiveBorder = Color(0x80C3B5FF),
    navActiveContent = Color(0xFFF6F4FF),
    navInactiveContent = Color(0xFF8F8CA6),
    sheetContainer = Color(0xFF141423),
    dialogContainer = Color(0xFF1D1C2D),
    brandAccent = Color(0xFFA78BFA),
    brandWordmark = Color(0xFFD9D5EE),
    moodLow = Color(0xFFEF5A52),
    moodLowFeature = Color(0xFF280B09),
    moodLowGlow = Color(0x5EEF5A52),
    moodOkay = Color(0xFFF5C73D),
    moodOkayFeature = Color(0xFF2D2207),
    moodOkayGlow = Color(0x5EF5C73D),
    moodGood = Color(0xFF34C768),
    moodGoodFeature = Color(0xFF0A2912),
    moodGoodGlow = Color(0x5E34C768),
    wheelSelectionStroke = Color(0xFFFFFFFF),
    successBadgeBg = Color(0x2E34C768),
    successBadgeFg = Color(0xFF7BE397),
    warningBadgeBg = Color(0x2EFF7A70),
    warningBadgeFg = Color(0xFFFF9890),
)

val LightSmileProcColors = SmileProcColors(
    isDark = false,
    canvasBase = Color(0xFFF3F1FC),
    glowPrimary = Color(0xAA9E8CFC),
    glowSecondary = Color(0x78FFAC8B),
    glassTop = Color(0xDBFFFFFF),
    glassBottom = Color(0xC2F9F7FF),
    glassHighlightTop = Color(0xF5F4F0FF),
    glassHighlightBottom = Color(0xE3ECE6FE),
    glassBorderTop = Color(0xFFFFFFFF),
    glassBorderBottom = Color(0x366D5BD6),
    glassDivider = Color(0x1C3D346E),
    pillFillTop = Color(0xEBFFFFFF),
    pillFillBottom = Color(0xD6F6F4FC),
    pillBorder = Color(0x305C4BD6),
    chipFill = Color(0x165C4BD6),
    chipBorder = Color(0x225C4BD6),
    navBarFill = Color(0xE8FFFFFF),
    navBarBorder = Color(0x1E3D346E),
    navActiveGlow = Color(0x526D5AE8),
    navActivePill = Color(0x2E6D5AE8),
    navActiveBorder = Color(0x5C6D5AE8),
    navActiveContent = Color(0xFF4736BD),
    navInactiveContent = Color(0xFF66627D),
    sheetContainer = Color(0xFFFAF8FF),
    dialogContainer = Color(0xFFFFFFFF),
    brandAccent = Color(0xFF6B56E8),
    brandWordmark = Color(0xFF3A325C),
    moodLow = Color(0xFFEB5750),
    moodLowFeature = Color(0xFF280B09),
    moodLowGlow = Color(0x4EEB5750),
    moodOkay = Color(0xFFF4C133),
    moodOkayFeature = Color(0xFF2D2207),
    moodOkayGlow = Color(0x4EF4C133),
    moodGood = Color(0xFF2EBA60),
    moodGoodFeature = Color(0xFF0A2912),
    moodGoodGlow = Color(0x4E2EBA60),
    wheelSelectionStroke = Color(0xFF2F1F96),
    successBadgeBg = Color(0x212EBA60),
    successBadgeFg = Color(0xFF176B30),
    warningBadgeBg = Color(0x21C42D25),
    warningBadgeFg = Color(0xFFA82019),
)

val LocalSmileProcColors = staticCompositionLocalOf { LightSmileProcColors }

private val SmileProcDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF150E36),
    primaryContainer = Color(0xFF362A72),
    onPrimaryContainer = Color(0xFFECE7FF),
    secondary = Color(0xFFB8AFDF),
    onSecondary = Color(0xFF1F1B36),
    secondaryContainer = Color(0xFF292541),
    onSecondaryContainer = Color(0xFFE6E0FA),
    tertiary = Color(0xFF8CD3B2),
    onTertiary = Color(0xFF0C2A1C),
    tertiaryContainer = Color(0xFF1B3C2E),
    onTertiaryContainer = Color(0xFFD0F4E2),
    background = Color(0xFF0A0B14),
    onBackground = Color(0xFFF3F2F8),
    surface = Color(0xFF141423),
    onSurface = Color(0xFFF3F2F8),
    surfaceVariant = Color(0xFF222134),
    onSurfaceVariant = Color(0xFFAFAAC7),
    surfaceContainerLowest = Color(0xFF0A0A12),
    surfaceContainerLow = Color(0xFF141423),
    surfaceContainer = Color(0xFF1A192A),
    surfaceContainerHigh = Color(0xFF222134),
    surfaceContainerHighest = Color(0xFF2C2B42),
    outline = Color(0xFF6B6884),
    outlineVariant = Color(0xFF302E44),
    error = Color(0xFFFF7D73),
    onError = Color(0xFF3D0806),
    errorContainer = Color(0xFF5C1916),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val SmileProcLightColorScheme = lightColorScheme(
    primary = Color(0xFF6B56E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E3FF),
    onPrimaryContainer = Color(0xFF1D1259),
    secondary = Color(0xFF5C5878),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E6F5),
    onSecondaryContainer = Color(0xFF1B182E),
    tertiary = Color(0xFF247A53),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3F2E2),
    onTertiaryContainer = Color(0xFF0B2E1D),
    background = Color(0xFFF3F1FC),
    onBackground = Color(0xFF171524),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF171524),
    surfaceVariant = Color(0xFFEBE8F5),
    onSurfaceVariant = Color(0xFF55516B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F4FA),
    surfaceContainer = Color(0xFFEFEDF7),
    surfaceContainerHigh = Color(0xFFE9E6F4),
    surfaceContainerHighest = Color(0xFFE2DFEE),
    outline = Color(0xFF7B7794),
    outlineVariant = Color(0xFFD8D4E8),
    error = Color(0xFFC42D25),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF410805),
)

private val SmileProcTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private val SmileProcShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun SmileProcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extendedColors = if (darkTheme) DarkSmileProcColors else LightSmileProcColors
    val colorScheme = if (darkTheme) SmileProcDarkColorScheme else SmileProcLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSmileProcColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SmileProcTypography,
            shapes = SmileProcShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
