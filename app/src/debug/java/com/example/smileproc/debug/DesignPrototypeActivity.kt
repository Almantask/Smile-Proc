package com.example.smileproc.debug

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.smileproc.R
import com.example.smileproc.core.EmotionTags
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.ui.AppTab
import com.example.smileproc.ui.components.GlassBottomNavBar
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.GlassPillButton
import com.example.smileproc.ui.components.GlassTagPill
import com.example.smileproc.ui.components.GlassTopBar
import com.example.smileproc.ui.components.rememberReducedMotion
import com.example.smileproc.ui.tags.EmotionTagsCatalog
import com.example.smileproc.ui.tags.EmotionWheelMath
import com.example.smileproc.ui.tags.parseCoreColor
import com.example.smileproc.ui.tags.resolveTagLabel
import com.example.smileproc.ui.theme.DarkSmileProcColors
import com.example.smileproc.ui.theme.LightSmileProcColors
import com.example.smileproc.ui.theme.LocalSmileProcColors
import com.example.smileproc.ui.theme.SmileProcColors
import com.example.smileproc.ui.week.PlotPoint
import com.example.smileproc.ui.week.WeekPlotMath
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class ProtoStyle(val key: String, val label: String) {
    BASELINE("baseline", "Current"),
    AURA("aura", "1 · Living Aura"),
    EXPRESSIVE("expressive", "2 · Expressive Pulse"),
    ORGANIC("organic", "3 · Sunlit & Organic"),
    ;

    companion object {
        fun fromKey(key: String?): ProtoStyle =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: AURA
    }
}

enum class ProtoScreen(val key: String, val label: String) {
    TODAY("today", "Today"),
    WEEK("week", "Week"),
    FOLLOWUP("followup", "Wheel"),
    ;

    companion object {
        fun fromKey(key: String?): ProtoScreen =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: TODAY
    }
}

/**
 * Debug-only visual prototype showcase for comparing design directions on-device.
 * Launched via adb extras (`style`, `screen`, `dark`, `hideSwitcher`) or interactively.
 */
class DesignPrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialStyle = ProtoStyle.fromKey(intent.getStringExtra("style"))
        val initialScreen = ProtoScreen.fromKey(intent.getStringExtra("screen"))
        val initialDark = intent.getBooleanExtra("dark", true)
        val hideSwitcher = intent.getBooleanExtra("hideSwitcher", false)

        setContent {
            PrototypeHost(
                initialStyle = initialStyle,
                initialScreen = initialScreen,
                initialDark = initialDark,
                hideSwitcher = hideSwitcher,
            )
        }
    }
}

@Composable
private fun PrototypeHost(
    initialStyle: ProtoStyle,
    initialScreen: ProtoScreen,
    initialDark: Boolean,
    hideSwitcher: Boolean,
) {
    var style by remember { mutableStateOf(initialStyle) }
    var screen by remember { mutableStateOf(initialScreen) }
    var dark by remember { mutableStateOf(initialDark) }

    val zone = remember { ZoneId.systemDefault() }
    val baseMonday = remember { LocalDate.of(2026, 11, 9) }
    val todayDate = remember { LocalDate.of(2026, 11, 14) }
    val todayEntries = remember(zone, todayDate) { sampleTodayEntries(todayDate, zone) }
    val weekEntries = remember(zone, baseMonday) { sampleWeekEntries(baseMonday, zone) }
    val placesById = remember {
        mapOf(
            "00000000-0000-4000-8000-000000000001" to "Park",
            "00000000-0000-4000-8000-000000000002" to "Home",
        )
    }

    PrototypeTheme(style = style, darkTheme = dark) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as? android.app.Activity)?.window
                if (window != null) {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            PrototypeBackdrop(
                style = style,
                darkTheme = dark,
                latestMood = todayEntries.firstOrNull()?.mood ?: Mood.GOOD,
            ) {
                val currentTab = when (screen) {
                    ProtoScreen.TODAY, ProtoScreen.FOLLOWUP -> AppTab.TODAY
                    ProtoScreen.WEEK -> AppTab.WEEK
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        PrototypeBottomBar(
                            style = style,
                            currentTab = currentTab,
                            hasUnseenCard = true,
                            onSelectTab = { tab ->
                                screen = when (tab) {
                                    AppTab.TODAY -> ProtoScreen.TODAY
                                    AppTab.WEEK -> ProtoScreen.WEEK
                                    AppTab.SETTINGS -> ProtoScreen.FOLLOWUP
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        when (screen) {
                            ProtoScreen.TODAY -> PrototypeTodayScreen(
                                style = style,
                                entries = todayEntries,
                                placesById = placesById,
                            )
                            ProtoScreen.WEEK -> PrototypeWeekScreen(
                                style = style,
                                entries = weekEntries,
                                weekStart = baseMonday,
                            )
                            ProtoScreen.FOLLOWUP -> {
                                PrototypeTodayScreen(
                                    style = style,
                                    entries = todayEntries,
                                    placesById = placesById,
                                )
                                PrototypeFollowUpOverlay(
                                    style = style,
                                    selectedTags = listOf("calm.peaceful", "happy.grateful"),
                                    note = "Afternoon light felt grounding",
                                )
                            }
                        }
                    }
                }
            }

            if (!hideSwitcher) {
                PrototypeSwitcherBar(
                    style = style,
                    screen = screen,
                    dark = dark,
                    onStyleChange = { style = it },
                    onScreenChange = { screen = it },
                    onDarkToggle = { dark = !dark },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 4.dp, start = 8.dp, end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PrototypeSwitcherBar(
    style: ProtoStyle,
    screen: ProtoScreen,
    dark: Boolean,
    onStyleChange: (ProtoStyle) -> Unit,
    onScreenChange: (ProtoScreen) -> Unit,
    onDarkToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProtoStyle.entries.forEach { option ->
                val selected = option == style
                Surface(
                    shape = CircleShape,
                    color = if (selected) Color.White else Color.Transparent,
                    modifier = Modifier.clickable { onStyleChange(option) },
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text("|", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
            ProtoScreen.entries.forEach { s ->
                val selected = s == screen
                Surface(
                    shape = CircleShape,
                    color = if (selected) Color(0xFF9F90FF) else Color.Transparent,
                    modifier = Modifier.clickable { onScreenChange(s) },
                ) {
                    Text(
                        text = s.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
                modifier = Modifier.clickable(onClick = onDarkToggle),
            ) {
                Text(
                    text = if (dark) "Dark" else "Light",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// =============================================================================
// Theme & Palettes
// =============================================================================

private val AuraDarkColors = DarkSmileProcColors.copy(
    canvasBase = Color(0xFF0A0B14),
    glowPrimary = Color(0x888B5CF6),
    glowSecondary = Color(0x54FF8A70),
    glassTop = Color(0xBA242338),
    glassBottom = Color(0xA6151524),
    glassHighlightTop = Color(0xCC312B4D),
    glassHighlightBottom = Color(0xB81D1A33),
    glassBorderTop = Color(0x42FFFFFF),
    glassBorderBottom = Color(0x14FFFFFF),
    navBarFill = Color(0xDF10111D),
    navActiveGlow = Color(0x8A9F85FF),
    navActivePill = Color(0x5C7C69EE),
    navActiveBorder = Color(0x80C3B5FF),
    sheetContainer = Color(0xFF141423),
    brandAccent = Color(0xFFA78BFA),
    brandWordmark = Color(0xFFD9D5EE),
    moodLow = Color(0xFFEF5A52),
    moodLowGlow = Color(0x5EEF5A52),
    moodOkay = Color(0xFFF5C73D),
    moodOkayGlow = Color(0x5EF5C73D),
    moodGood = Color(0xFF34C768),
    moodGoodGlow = Color(0x5E34C768),
)

private val AuraLightColors = LightSmileProcColors.copy(
    canvasBase = Color(0xFFF3F1FC),
    glowPrimary = Color(0xAA9E8CFC),
    glowSecondary = Color(0x78FFAC8B),
    glassTop = Color(0xDBFFFFFF),
    glassBottom = Color(0xC2F9F7FF),
    glassBorderTop = Color(0xFFFFFFFF),
    glassBorderBottom = Color(0x366D5BD6),
    navBarFill = Color(0xE8FFFFFF),
    navActiveGlow = Color(0x526D5AE8),
    navActivePill = Color(0x2E6D5AE8),
    navActiveBorder = Color(0x5C6D5AE8),
    sheetContainer = Color(0xFFFAF8FF),
    brandAccent = Color(0xFF6B56E8),
    brandWordmark = Color(0xFF3A325C),
    moodLow = Color(0xFFEB5750),
    moodLowGlow = Color(0x4EEB5750),
    moodOkay = Color(0xFFF4C133),
    moodOkayGlow = Color(0x4EF4C133),
    moodGood = Color(0xFF2EBA60),
    moodGoodGlow = Color(0x4E2EBA60),
)

private val ExpressiveDarkColors = DarkSmileProcColors.copy(
    canvasBase = Color(0xFF0D0C18),
    glowPrimary = Color(0x7A9333EA),
    glowSecondary = Color(0x4AF43F5E),
    glassTop = Color(0xCC24213A),
    glassBottom = Color(0xBD181629),
    glassBorderTop = Color(0x3BFFFFFF),
    glassBorderBottom = Color(0x18FFFFFF),
    navBarFill = Color(0xE8131221),
    navActiveGlow = Color(0x88A855F7),
    navActivePill = Color(0x599333EA),
    navActiveBorder = Color(0x88D8B4FE),
    sheetContainer = Color(0xFF171528),
    brandAccent = Color(0xFFB57BFF),
    brandWordmark = Color(0xFFEBE4FF),
    moodLow = Color(0xFFFF6B63),
    moodLowGlow = Color(0x5EFF6B63),
    moodOkay = Color(0xFFFFD147),
    moodOkayGlow = Color(0x5EFFD147),
    moodGood = Color(0xFF2ED47A),
    moodGoodGlow = Color(0x5E2ED47A),
)

private val ExpressiveLightColors = LightSmileProcColors.copy(
    canvasBase = Color(0xFFF7F5FF),
    glowPrimary = Color(0x80C4B5FD),
    glowSecondary = Color(0x78FBCFE8),
    glassTop = Color(0xF2FFFFFF),
    glassBottom = Color(0xE0FAF8FF),
    navActiveGlow = Color(0x4A8B5CF6),
    navActivePill = Color(0x2E8B5CF6),
    navActiveBorder = Color(0x5E8B5CF6),
    sheetContainer = Color(0xFFFCFAFF),
    brandAccent = Color(0xFF7C3AED),
    brandWordmark = Color(0xFF2E2352),
    moodLow = Color(0xFFF25C54),
    moodLowGlow = Color(0x4CF25C54),
    moodOkay = Color(0xFFF6C026),
    moodOkayGlow = Color(0x4CF6C026),
    moodGood = Color(0xFF22C56A),
    moodGoodGlow = Color(0x4C22C56A),
)

private val OrganicDarkColors = SmileProcColors(
    isDark = true,
    canvasBase = Color(0xFF151013),
    glowPrimary = Color(0x7A7C3227),
    glowSecondary = Color(0x5E4A254B),
    glassTop = Color(0xCC281F23),
    glassBottom = Color(0xBA1D161A),
    glassHighlightTop = Color(0xD937282C),
    glassHighlightBottom = Color(0xC2241A1D),
    glassBorderTop = Color(0x33FFDFC8),
    glassBorderBottom = Color(0x14FFDFC8),
    glassDivider = Color(0x1CFFDFC8),
    pillFillTop = Color(0xCC2A2024),
    pillFillBottom = Color(0xBA1D161A),
    pillBorder = Color(0x36FFDFC8),
    chipFill = Color(0x24FFDFC8),
    chipBorder = Color(0x2EFFDFC8),
    navBarFill = Color(0xEB171115),
    navBarBorder = Color(0x24FFDFC8),
    navActiveGlow = Color(0x70F2956B),
    navActivePill = Color(0x45E07A5F),
    navActiveBorder = Color(0x78F7B494),
    navActiveContent = Color(0xFFFFF4EC),
    navInactiveContent = Color(0xFF9F8E87),
    sheetContainer = Color(0xFF1E171A),
    dialogContainer = Color(0xFF261D21),
    brandAccent = Color(0xFFF2956B),
    brandWordmark = Color(0xFFE7D6CC),
    moodLow = Color(0xFFE06358),
    moodLowFeature = Color(0xFF2D0D0A),
    moodLowGlow = Color(0x52E06358),
    moodOkay = Color(0xFFEEB63B),
    moodOkayFeature = Color(0xFF2E2105),
    moodOkayGlow = Color(0x52EEB63B),
    moodGood = Color(0xFF47AD6E),
    moodGoodFeature = Color(0xFF0A2914),
    moodGoodGlow = Color(0x5247AD6E),
    wheelSelectionStroke = Color(0xFF8C3B2B),
    successBadgeBg = Color(0x2E47AD6E),
    successBadgeFg = Color(0xFF8BE2AA),
    warningBadgeBg = Color(0x2EE06358),
    warningBadgeFg = Color(0xFFFFA59D),
)

private val OrganicLightColors = SmileProcColors(
    isDark = false,
    canvasBase = Color(0xFFFBF6EE),
    glowPrimary = Color(0x9EFFD1AE),
    glowSecondary = Color(0x80CBEAD6),
    glassTop = Color(0xEBFFFDF9),
    glassBottom = Color(0xDDFBF6EE),
    glassHighlightTop = Color(0xF5FFF5EB),
    glassHighlightBottom = Color(0xE6F9EADC),
    glassBorderTop = Color(0xFFFFFFFF),
    glassBorderBottom = Color(0x38C78B6C),
    glassDivider = Color(0x1F5C4338),
    pillFillTop = Color(0xEEFFFDF9),
    pillFillBottom = Color(0xDCF7EFE5),
    pillBorder = Color(0x38C78B6C),
    chipFill = Color(0x16B85E46),
    chipBorder = Color(0x2BB85E46),
    navBarFill = Color(0xF2FFFDF9),
    navBarBorder = Color(0x1F5C4338),
    navActiveGlow = Color(0x45D4604A),
    navActivePill = Color(0x24D4604A),
    navActiveBorder = Color(0x52D4604A),
    navActiveContent = Color(0xFF9C3E2D),
    navInactiveContent = Color(0xFF78665E),
    sheetContainer = Color(0xFFFFFBF5),
    dialogContainer = Color(0xFFFFFFFF),
    brandAccent = Color(0xFFD4604A),
    brandWordmark = Color(0xFF4A3832),
    moodLow = Color(0xFFDF6257),
    moodLowFeature = Color(0xFF2D0D0A),
    moodLowGlow = Color(0x42DF6257),
    moodOkay = Color(0xFFEBAE33),
    moodOkayFeature = Color(0xFF2E2105),
    moodOkayGlow = Color(0x42EBAE33),
    moodGood = Color(0xFF43A86A),
    moodGoodFeature = Color(0xFF0A2914),
    moodGoodGlow = Color(0x4243A86A),
    wheelSelectionStroke = Color(0xFF8C3B2B),
    successBadgeBg = Color(0x2143A86A),
    successBadgeFg = Color(0xFF1D6E3D),
    warningBadgeBg = Color(0x21C44035),
    warningBadgeFg = Color(0xFF9C2C22),
)

private val OrganicDarkMaterialScheme = darkColorScheme(
    primary = Color(0xFFF2956B),
    onPrimary = Color(0xFF3B1506),
    primaryContainer = Color(0xFF5A2A1E),
    onPrimaryContainer = Color(0xFFFFE5D9),
    secondary = Color(0xFFD6B9AD),
    onSecondary = Color(0xFF2D1E19),
    secondaryContainer = Color(0xFF3B2B28),
    onSecondaryContainer = Color(0xFFF6E3DA),
    tertiary = Color(0xFF8CD3A6),
    onTertiary = Color(0xFF0B2917),
    background = Color(0xFF151013),
    onBackground = Color(0xFFFAF2EA),
    surface = Color(0xFF1E171A),
    onSurface = Color(0xFFFAF2EA),
    surfaceVariant = Color(0xFF2D2226),
    onSurfaceVariant = Color(0xFFC2B1A8),
    outline = Color(0xFF85726B),
    outlineVariant = Color(0xFF443634),
)

private val OrganicLightMaterialScheme = lightColorScheme(
    primary = Color(0xFFC85640),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE3D8),
    onPrimaryContainer = Color(0xFF4A180E),
    secondary = Color(0xFF6B5952),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4E8DF),
    onSecondaryContainer = Color(0xFF291E1B),
    tertiary = Color(0xFF338754),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF6EE),
    onBackground = Color(0xFF241C1A),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF241C1A),
    surfaceVariant = Color(0xFFF3E9DE),
    onSurfaceVariant = Color(0xFF695852),
    outline = Color(0xFF8F7D75),
    outlineVariant = Color(0xFFE3D5C7),
)

private fun prototypeTypography(style: ProtoStyle): Typography {
    val displayFamily = if (style == ProtoStyle.ORGANIC) FontFamily.Serif else FontFamily.SansSerif
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = displayFamily,
            fontSize = if (style == ProtoStyle.ORGANIC) 31.sp else 30.sp,
            lineHeight = 38.sp,
            fontWeight = if (style == ProtoStyle.ORGANIC) FontWeight.Medium else FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = displayFamily,
            fontSize = if (style == ProtoStyle.ORGANIC) 27.sp else 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.3).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = displayFamily,
            fontSize = 23.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = displayFamily,
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
}

@Composable
private fun PrototypeTheme(
    style: ProtoStyle,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val extendedColors = when (style) {
        ProtoStyle.BASELINE -> if (darkTheme) DarkSmileProcColors else LightSmileProcColors
        ProtoStyle.AURA -> if (darkTheme) AuraDarkColors else AuraLightColors
        ProtoStyle.EXPRESSIVE -> if (darkTheme) ExpressiveDarkColors else ExpressiveLightColors
        ProtoStyle.ORGANIC -> if (darkTheme) OrganicDarkColors else OrganicLightColors
    }

    val baseDark = darkColorScheme(
        primary = if (style == ProtoStyle.EXPRESSIVE) Color(0xFFB57BFF) else Color(0xFF9F90FF),
        onPrimary = Color(0xFF150E36),
        primaryContainer = if (style == ProtoStyle.EXPRESSIVE) Color(0xFF422675) else Color(0xFF342A6E),
        onPrimaryContainer = Color(0xFFECE7FF),
        background = extendedColors.canvasBase,
        onBackground = Color(0xFFF3F2F8),
        surface = extendedColors.sheetContainer,
        onSurface = Color(0xFFF3F2F8),
        surfaceVariant = Color(0xFF222134),
        onSurfaceVariant = Color(0xFFABA7C2),
        outline = Color(0xFF6B6884),
        outlineVariant = Color(0xFF302E44),
    )
    val baseLight = lightColorScheme(
        primary = if (style == ProtoStyle.EXPRESSIVE) Color(0xFF7C3AED) else Color(0xFF5B47D6),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE8E3FF),
        onPrimaryContainer = Color(0xFF1D1259),
        background = extendedColors.canvasBase,
        onBackground = Color(0xFF171524),
        surface = extendedColors.sheetContainer,
        onSurface = Color(0xFF171524),
        surfaceVariant = Color(0xFFEBE8F5),
        onSurfaceVariant = Color(0xFF55516B),
        outline = Color(0xFF7B7794),
        outlineVariant = Color(0xFFD8D4E8),
    )

    val colorScheme = when {
        style == ProtoStyle.ORGANIC && darkTheme -> OrganicDarkMaterialScheme
        style == ProtoStyle.ORGANIC && !darkTheme -> OrganicLightMaterialScheme
        darkTheme -> baseDark
        else -> baseLight
    }

    CompositionLocalProvider(LocalSmileProcColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = prototypeTypography(style),
            content = content,
        )
    }
}

// =============================================================================
// Backdrops
// =============================================================================

@Composable
private fun PrototypeBackdrop(
    style: ProtoStyle,
    darkTheme: Boolean,
    latestMood: Mood,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalSmileProcColors.current
    val reducedMotion = rememberReducedMotion()
    val infinite = rememberInfiniteTransition(label = "backdropPulse")
    val breath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (reducedMotion) 1 else 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val phase = if (reducedMotion) 0.5f else breath

    val moodEchoColor = when (latestMood) {
        Mood.LOW -> colors.moodLow
        Mood.OKAY -> colors.moodOkay
        Mood.GOOD -> colors.moodGood
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = colors.canvasBase)

                when (style) {
                    ProtoStyle.BASELINE -> {
                        val topCenter = Offset(size.width * 0.5f, size.height * 0.14f)
                        val topRadius = size.maxDimension * 0.58f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.glowPrimary, Color.Transparent),
                                center = topCenter,
                                radius = topRadius,
                            ),
                            radius = topRadius,
                            center = topCenter,
                        )
                        val lowerCenter = Offset(size.width * 0.25f, size.height * 0.88f)
                        val lowerRadius = size.maxDimension * 0.50f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.glowSecondary, Color.Transparent),
                                center = lowerCenter,
                                radius = lowerRadius,
                            ),
                            radius = lowerRadius,
                            center = lowerCenter,
                        )
                    }

                    ProtoStyle.AURA -> {
                        // Aurora top-left violet wash
                        val c1 = Offset(size.width * (0.28f + 0.08f * phase), size.height * 0.12f)
                        val r1 = size.maxDimension * (0.52f + 0.05f * phase)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.glowPrimary, Color.Transparent),
                                center = c1,
                                radius = r1,
                            ),
                            radius = r1,
                            center = c1,
                        )
                        // Warm coral sunrise orb top-right
                        val c2 = Offset(size.width * (0.86f - 0.06f * phase), size.height * 0.20f)
                        val r2 = size.maxDimension * 0.44f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.glowSecondary, Color.Transparent),
                                center = c2,
                                radius = r2,
                            ),
                            radius = r2,
                            center = c2,
                        )
                        // Mood-echo halo behind today's check-in & latest entry
                        val c3 = Offset(size.width * 0.72f, size.height * 0.54f)
                        val r3 = size.maxDimension * 0.46f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    moodEchoColor.copy(alpha = if (darkTheme) 0.22f else 0.18f),
                                    Color.Transparent,
                                ),
                                center = c3,
                                radius = r3,
                            ),
                            radius = r3,
                            center = c3,
                        )
                        // Lower indigo-teal pool
                        val c4 = Offset(size.width * 0.20f, size.height * 0.88f)
                        val r4 = size.maxDimension * 0.48f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.brandAccent.copy(alpha = if (darkTheme) 0.28f else 0.20f),
                                    Color.Transparent,
                                ),
                                center = c4,
                                radius = r4,
                            ),
                            radius = r4,
                            center = c4,
                        )
                        // Delicate ambient bokeh light motes
                        val motes = listOf(
                            Triple(0.16f, 0.22f, 26f) to colors.brandAccent,
                            Triple(0.82f, 0.14f, 18f) to Color(0xFFFFA07A),
                            Triple(0.88f, 0.43f, 22f) to moodEchoColor,
                            Triple(0.12f, 0.62f, 16f) to Color(0xFFC084FC),
                            Triple(0.76f, 0.76f, 28f) to colors.brandAccent,
                            Triple(0.32f, 0.84f, 15f) to moodEchoColor,
                        )
                        motes.forEachIndexed { idx, (pos, col) ->
                            val shift = ((idx % 2) * 2 - 1) * 10f * phase
                            val center = Offset(size.width * pos.first, size.height * pos.second + shift)
                            val rad = pos.third.dp.toPx()
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(col.copy(alpha = if (darkTheme) 0.22f else 0.20f), Color.Transparent),
                                    center = center,
                                    radius = rad,
                                ),
                                radius = rad,
                                center = center,
                            )
                        }
                    }

                    ProtoStyle.EXPRESSIVE -> {
                        val c1 = Offset(size.width * 0.50f, size.height * 0.16f)
                        val r1 = size.maxDimension * 0.56f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.glowPrimary, Color.Transparent),
                                center = c1,
                                radius = r1,
                            ),
                            radius = r1,
                            center = c1,
                        )
                        val c2 = Offset(size.width * 0.92f, size.height * 0.36f)
                        val r2 = size.maxDimension * 0.44f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.glowSecondary, Color.Transparent),
                                center = c2,
                                radius = r2,
                            ),
                            radius = r2,
                            center = c2,
                        )
                        val c3 = Offset(size.width * 0.12f, size.height * 0.82f)
                        val r3 = size.maxDimension * 0.46f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.moodGood.copy(alpha = if (darkTheme) 0.18f else 0.16f),
                                    Color.Transparent,
                                ),
                                center = c3,
                                radius = r3,
                            ),
                            radius = r3,
                            center = c3,
                        )
                    }

                    ProtoStyle.ORGANIC -> {
                        // Sunbeam / hearth glow upper right
                        val c1 = Offset(size.width * 0.78f, size.height * 0.14f)
                        val r1 = size.maxDimension * 0.62f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.glowPrimary, Color.Transparent),
                                center = c1,
                                radius = r1,
                            ),
                            radius = r1,
                            center = c1,
                        )
                        // Botanical sage / twilight plum lower left
                        val c2 = Offset(size.width * 0.16f, size.height * 0.78f)
                        val r2 = size.maxDimension * 0.58f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.glowSecondary, Color.Transparent),
                                center = c2,
                                radius = r2,
                            ),
                            radius = r2,
                            center = c2,
                        )
                        // Gentle golden warmth behind faces
                        val c3 = Offset(size.width * 0.50f, size.height * 0.30f)
                        val r3 = size.maxDimension * 0.36f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.moodOkay.copy(alpha = if (darkTheme) 0.12f else 0.16f),
                                    Color.Transparent,
                                ),
                                center = c3,
                                radius = r3,
                            ),
                            radius = r3,
                            center = c3,
                        )
                    }
                }
            },
        content = content,
    )
}

// =============================================================================
// Brand Header & Bottom Nav
// =============================================================================

@Composable
private fun PrototypeBrandHeader(style: ProtoStyle) {
    val colors = LocalSmileProcColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val markSize = when (style) {
            ProtoStyle.BASELINE -> 22.dp
            ProtoStyle.AURA, ProtoStyle.ORGANIC -> 26.dp
            ProtoStyle.EXPRESSIVE -> 28.dp
        }
        Box(
            modifier = Modifier
                .size(markSize + 10.dp)
                .drawBehind {
                    if (style != ProtoStyle.BASELINE) {
                        val r = size.minDimension * 0.68f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.brandAccent.copy(alpha = 0.42f), Color.Transparent),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                            center = center,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(markSize)) {
                val w = size.width
                val h = size.height
                if (style == ProtoStyle.EXPRESSIVE) {
                    drawCircle(
                        color = colors.brandAccent.copy(alpha = 0.22f),
                        radius = w * 0.5f,
                        center = center,
                    )
                }
                val eyeRadius = w * if (style == ProtoStyle.EXPRESSIVE) 0.095f else 0.085f
                drawCircle(
                    color = colors.brandAccent,
                    radius = eyeRadius,
                    center = Offset(w * 0.31f, h * 0.35f),
                )
                drawCircle(
                    color = colors.brandAccent,
                    radius = eyeRadius,
                    center = Offset(w * 0.69f, h * 0.35f),
                )
                if (style == ProtoStyle.EXPRESSIVE) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = eyeRadius * 0.36f,
                        center = Offset(w * 0.31f - eyeRadius * 0.25f, h * 0.35f - eyeRadius * 0.25f),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = eyeRadius * 0.36f,
                        center = Offset(w * 0.69f - eyeRadius * 0.25f, h * 0.35f - eyeRadius * 0.25f),
                    )
                }
                val smile = Path().apply {
                    moveTo(w * 0.19f, h * 0.62f)
                    quadraticTo(w * 0.50f, h * 0.92f, w * 0.81f, h * 0.62f)
                }
                drawPath(
                    path = smile,
                    color = colors.brandAccent,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 21.sp,
                fontWeight = if (style == ProtoStyle.ORGANIC) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.2.sp,
            ),
            color = colors.brandWordmark,
        )
    }
}

@Composable
private fun PrototypeBottomBar(
    style: ProtoStyle,
    currentTab: AppTab,
    hasUnseenCard: Boolean,
    onSelectTab: (AppTab) -> Unit,
) {
    // GlassBottomNavBar already reads LocalSmileProcColors, so every prototype palette
    // automatically skins the active glow, pill, divider, and icons.
    GlassBottomNavBar(
        currentTab = currentTab,
        hasUnseenCard = hasUnseenCard,
        onSelectTab = onSelectTab,
        modifier = if (style == ProtoStyle.ORGANIC) Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) else Modifier,
    )
}

// =============================================================================
// Prototype Faces
// =============================================================================

@Composable
private fun PrototypeFaceIcon(
    style: ProtoStyle,
    mood: Mood,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSmileProcColors.current
    val faceColor = when (mood) {
        Mood.LOW -> colors.moodLow
        Mood.OKAY -> colors.moodOkay
        Mood.GOOD -> colors.moodGood
    }
    val featureColor = when (mood) {
        Mood.LOW -> colors.moodLowFeature
        Mood.OKAY -> colors.moodOkayFeature
        Mood.GOOD -> colors.moodGoodFeature
    }

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        when (style) {
            ProtoStyle.BASELINE -> {
                val topHighlight = lerp(faceColor, Color.White, 0.14f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(topHighlight, faceColor),
                        center = Offset(center.x, center.y - radius * 0.28f),
                        radius = radius * 1.25f,
                    ),
                    radius = radius,
                    center = center,
                )
                drawSimpleFeatures(mood, center, radius, featureColor)
            }

            ProtoStyle.AURA -> {
                val topHighlight = lerp(faceColor, Color.White, 0.24f)
                val deepRim = lerp(faceColor, Color.Black, 0.14f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(topHighlight, faceColor, deepRim),
                        center = Offset(center.x - radius * 0.18f, center.y - radius * 0.30f),
                        radius = radius * 1.35f,
                    ),
                    radius = radius,
                    center = center,
                )
                // Soft inner specular rim
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.42f), Color.Transparent),
                    ),
                    radius = radius - 1.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                drawWarmFeatures(mood, center, radius, featureColor, blushColor = Color.White.copy(alpha = 0.22f))
            }

            ProtoStyle.EXPRESSIVE -> {
                val specular = lerp(faceColor, Color.White, 0.36f)
                val bottomRim = lerp(faceColor, Color(0xFF1A0E2E), 0.18f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(specular, faceColor, bottomRim),
                        center = Offset(center.x - radius * 0.24f, center.y - radius * 0.32f),
                        radius = radius * 1.35f,
                    ),
                    radius = radius,
                    center = center,
                )
                // Glossy top crescent highlight
                drawArc(
                    color = Color.White.copy(alpha = 0.38f),
                    startAngle = 215f,
                    sweepAngle = 95f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.78f, center.y - radius * 0.78f),
                    size = Size(radius * 1.56f, radius * 1.56f),
                    style = Stroke(width = (radius * 0.08f).coerceAtLeast(1.5f), cap = StrokeCap.Round),
                )
                val blush = when (mood) {
                    Mood.LOW -> Color(0xFFFFB5B0).copy(alpha = 0.42f)
                    Mood.OKAY -> Color(0xFFFF8F70).copy(alpha = 0.38f)
                    Mood.GOOD -> Color(0xFFFFA8B8).copy(alpha = 0.45f)
                }
                drawExpressiveCharacterFeatures(mood, center, radius, featureColor, blush)
            }

            ProtoStyle.ORGANIC -> {
                val creamHighlight = lerp(faceColor, Color(0xFFFFF3DD), 0.28f)
                val warmShadow = lerp(faceColor, Color(0xFF3E2218), 0.14f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(creamHighlight, faceColor, warmShadow),
                        center = Offset(center.x - radius * 0.16f, center.y - radius * 0.26f),
                        radius = radius * 1.35f,
                    ),
                    radius = radius,
                    center = center,
                )
                drawWarmFeatures(mood, center, radius, featureColor, blushColor = Color(0xFFFFE5D0).copy(alpha = 0.28f))
            }
        }
    }
}

private fun DrawScope.drawSimpleFeatures(
    mood: Mood,
    center: Offset,
    radius: Float,
    featureColor: Color,
) {
    val eyeRadius = (radius * 0.095f).coerceAtLeast(1.4f)
    val eyeOffsetX = radius * 0.34f
    val eyeOffsetY = radius * 0.20f
    drawCircle(featureColor, eyeRadius, Offset(center.x - eyeOffsetX, center.y - eyeOffsetY))
    drawCircle(featureColor, eyeRadius, Offset(center.x + eyeOffsetX, center.y - eyeOffsetY))

    val mouthWidth = radius * 0.72f
    val mouthCenterY = center.y + radius * 0.26f
    val strokeWidth = (radius * 0.11f).coerceAtLeast(2.dp.toPx())
    when (mood) {
        Mood.LOW -> {
            val path = Path().apply {
                arcTo(
                    Rect(center.x - mouthWidth / 2f, mouthCenterY - radius * 0.06f, center.x + mouthWidth / 2f, mouthCenterY + radius * 0.38f),
                    200f, 140f, true,
                )
            }
            drawPath(path, featureColor, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
        Mood.OKAY -> {
            drawLine(
                featureColor,
                Offset(center.x - mouthWidth / 2f, mouthCenterY + radius * 0.04f),
                Offset(center.x + mouthWidth / 2f, mouthCenterY + radius * 0.04f),
                strokeWidth,
                StrokeCap.Round,
            )
        }
        Mood.GOOD -> {
            val path = Path().apply {
                arcTo(
                    Rect(center.x - mouthWidth / 2f, mouthCenterY - radius * 0.34f, center.x + mouthWidth / 2f, mouthCenterY + radius * 0.16f),
                    20f, 140f, true,
                )
            }
            drawPath(path, featureColor, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawWarmFeatures(
    mood: Mood,
    center: Offset,
    radius: Float,
    featureColor: Color,
    blushColor: Color,
) {
    val eyeRadius = (radius * 0.095f).coerceAtLeast(1.5f)
    val eyeOffsetX = radius * 0.33f
    val eyeOffsetY = radius * 0.17f
    val strokeWidth = (radius * 0.105f).coerceAtLeast(1.8.dp.toPx())

    // Soft cheeks on larger icons
    if (radius > 16.dp.toPx()) {
        drawOval(
            color = blushColor,
            topLeft = Offset(center.x - radius * 0.62f, center.y + radius * 0.02f),
            size = Size(radius * 0.26f, radius * 0.15f),
        )
        drawOval(
            color = blushColor,
            topLeft = Offset(center.x + radius * 0.36f, center.y + radius * 0.02f),
            size = Size(radius * 0.26f, radius * 0.15f),
        )
    }

    if (mood == Mood.GOOD) {
        // Happy arched eyes
        val archW = radius * 0.25f
        val archH = radius * 0.18f
        listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
            val eyePath = Path().apply {
                arcTo(
                    Rect(center.x + dx - archW / 2f, center.y - eyeOffsetY - archH / 2f, center.x + dx + archW / 2f, center.y - eyeOffsetY + archH / 2f),
                    200f, 140f, true,
                )
            }
            drawPath(eyePath, featureColor, style = Stroke(strokeWidth * 0.92f, cap = StrokeCap.Round))
        }
    } else {
        drawCircle(featureColor, eyeRadius, Offset(center.x - eyeOffsetX, center.y - eyeOffsetY))
        drawCircle(featureColor, eyeRadius, Offset(center.x + eyeOffsetX, center.y - eyeOffsetY))
        if (radius > 14.dp.toPx()) {
            drawCircle(Color.White.copy(alpha = 0.8f), eyeRadius * 0.34f, Offset(center.x - eyeOffsetX - eyeRadius * 0.25f, center.y - eyeOffsetY - eyeRadius * 0.28f))
            drawCircle(Color.White.copy(alpha = 0.8f), eyeRadius * 0.34f, Offset(center.x + eyeOffsetX - eyeRadius * 0.25f, center.y - eyeOffsetY - eyeRadius * 0.28f))
        }
    }

    val mouthWidth = radius * 0.72f
    val mouthCenterY = center.y + radius * 0.25f
    when (mood) {
        Mood.LOW -> {
            if (radius > 14.dp.toPx()) {
                // Gentle caring brows
                drawLine(
                    featureColor,
                    Offset(center.x - eyeOffsetX * 1.32f, center.y - eyeOffsetY * 1.65f),
                    Offset(center.x - eyeOffsetX * 0.58f, center.y - eyeOffsetY * 2.1f),
                    strokeWidth * 0.72f,
                    StrokeCap.Round,
                )
                drawLine(
                    featureColor,
                    Offset(center.x + eyeOffsetX * 0.58f, center.y - eyeOffsetY * 2.1f),
                    Offset(center.x + eyeOffsetX * 1.32f, center.y - eyeOffsetY * 1.65f),
                    strokeWidth * 0.72f,
                    StrokeCap.Round,
                )
            }
            val path = Path().apply {
                arcTo(
                    Rect(center.x - mouthWidth * 0.44f, mouthCenterY - radius * 0.04f, center.x + mouthWidth * 0.44f, mouthCenterY + radius * 0.32f),
                    205f, 130f, true,
                )
            }
            drawPath(path, featureColor, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
        Mood.OKAY -> {
            // Soft serene gentle curve (warm & calm rather than flat mechanical line)
            val path = Path().apply {
                moveTo(center.x - mouthWidth * 0.44f, mouthCenterY + radius * 0.02f)
                quadraticTo(
                    center.x,
                    mouthCenterY + radius * 0.11f,
                    center.x + mouthWidth * 0.44f,
                    mouthCenterY + radius * 0.02f,
                )
            }
            drawPath(path, featureColor, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
        Mood.GOOD -> {
            val path = Path().apply {
                arcTo(
                    Rect(center.x - mouthWidth * 0.52f, mouthCenterY - radius * 0.34f, center.x + mouthWidth * 0.52f, mouthCenterY + radius * 0.22f),
                    18f, 144f, true,
                )
            }
            drawPath(path, featureColor, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawExpressiveCharacterFeatures(
    mood: Mood,
    center: Offset,
    radius: Float,
    featureColor: Color,
    blushColor: Color,
) {
    val eyeOffsetX = radius * 0.34f
    val eyeOffsetY = radius * 0.14f
    val strokeWidth = (radius * 0.10f).coerceAtLeast(1.8.dp.toPx())

    // Warm rosy cheeks
    if (radius > 14.dp.toPx()) {
        drawOval(
            color = blushColor,
            topLeft = Offset(center.x - radius * 0.68f, center.y + radius * 0.01f),
            size = Size(radius * 0.30f, radius * 0.18f),
        )
        drawOval(
            color = blushColor,
            topLeft = Offset(center.x + radius * 0.38f, center.y + radius * 0.01f),
            size = Size(radius * 0.30f, radius * 0.18f),
        )
    }

    val eyeRadius = (radius * 0.11f).coerceAtLeast(1.6f)
    when (mood) {
        Mood.GOOD -> {
            // Expressive joyful crescent eyes on hero faces, lively dots on tiny ones
            if (radius > 14.dp.toPx()) {
                val archW = radius * 0.28f
                val archH = radius * 0.22f
                listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
                    val eyePath = Path().apply {
                        arcTo(
                            Rect(center.x + dx - archW / 2f, center.y - eyeOffsetY - archH / 2f, center.x + dx + archW / 2f, center.y - eyeOffsetY + archH / 2f),
                            195f, 150f, true,
                        )
                    }
                    drawPath(eyePath, featureColor, style = Stroke(strokeWidth * 1.05f, cap = StrokeCap.Round))
                }
            } else {
                drawCircle(featureColor, eyeRadius, Offset(center.x - eyeOffsetX, center.y - eyeOffsetY))
                drawCircle(featureColor, eyeRadius, Offset(center.x + eyeOffsetX, center.y - eyeOffsetY))
            }
            // Radiant open-hearted smile
            val mouthW = radius * 0.78f
            val mouthY = center.y + radius * 0.20f
            val smilePath = Path().apply {
                arcTo(
                    Rect(center.x - mouthW / 2f, mouthY - radius * 0.32f, center.x + mouthW / 2f, mouthY + radius * 0.28f),
                    15f, 150f, true,
                )
            }
            drawPath(smilePath, featureColor, style = Stroke(strokeWidth * 1.05f, cap = StrokeCap.Round))
        }

        Mood.OKAY -> {
            // Shiny oval eyes with dual catchlights
            listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
                val ex = center.x + dx
                val ey = center.y - eyeOffsetY
                drawCircle(featureColor, eyeRadius, Offset(ex, ey))
                if (radius > 14.dp.toPx()) {
                    drawCircle(Color.White.copy(alpha = 0.9f), eyeRadius * 0.42f, Offset(ex - eyeRadius * 0.25f, ey - eyeRadius * 0.28f))
                }
            }
            // Gentle content curve
            val mouthW = radius * 0.56f
            val mouthY = center.y + radius * 0.25f
            val mouthPath = Path().apply {
                moveTo(center.x - mouthW / 2f, mouthY)
                quadraticTo(center.x, mouthY + radius * 0.14f, center.x + mouthW / 2f, mouthY - radius * 0.02f)
            }
            drawPath(mouthPath, featureColor, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }

        Mood.LOW -> {
            // Caring eyes with catchlights + tender angled brows
            listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
                val ex = center.x + dx
                val ey = center.y - eyeOffsetY * 0.7f
                drawCircle(featureColor, eyeRadius * 0.95f, Offset(ex, ey))
                if (radius > 14.dp.toPx()) {
                    drawCircle(Color.White.copy(alpha = 0.85f), eyeRadius * 0.38f, Offset(ex - eyeRadius * 0.24f, ey - eyeRadius * 0.26f))
                }
            }
            if (radius > 14.dp.toPx()) {
                drawLine(
                    featureColor,
                    Offset(center.x - eyeOffsetX * 1.38f, center.y - eyeOffsetY * 1.75f),
                    Offset(center.x - eyeOffsetX * 0.55f, center.y - eyeOffsetY * 2.25f),
                    strokeWidth * 0.8f,
                    StrokeCap.Round,
                )
                drawLine(
                    featureColor,
                    Offset(center.x + eyeOffsetX * 0.55f, center.y - eyeOffsetY * 2.25f),
                    Offset(center.x + eyeOffsetX * 1.38f, center.y - eyeOffsetY * 1.75f),
                    strokeWidth * 0.8f,
                    StrokeCap.Round,
                )
            }
            val mouthW = radius * 0.58f
            val mouthY = center.y + radius * 0.28f
            val mouthPath = Path().apply {
                arcTo(
                    Rect(center.x - mouthW / 2f, mouthY - radius * 0.06f, center.x + mouthW / 2f, mouthY + radius * 0.30f),
                    205f, 130f, true,
                )
            }
            drawPath(mouthPath, featureColor, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun PrototypeFaceButton(
    style: ProtoStyle,
    mood: Mood,
    isHeroHighlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSmileProcColors.current
    val glowColor = when (mood) {
        Mood.LOW -> colors.moodLowGlow
        Mood.OKAY -> colors.moodOkayGlow
        Mood.GOOD -> colors.moodGoodGlow
    }
    val faceAccent = when (mood) {
        Mood.LOW -> colors.moodLow
        Mood.OKAY -> colors.moodOkay
        Mood.GOOD -> colors.moodGood
    }
    val label = when (mood) {
        Mood.LOW -> stringResource(R.string.face_low)
        Mood.OKAY -> stringResource(R.string.face_okay)
        Mood.GOOD -> stringResource(R.string.face_good)
    }

    val yShift = if (style == ProtoStyle.EXPRESSIVE && isHeroHighlight) (-4).dp else 0.dp
    val faceSize = when (style) {
        ProtoStyle.BASELINE -> 76.dp
        ProtoStyle.AURA, ProtoStyle.ORGANIC -> 80.dp
        ProtoStyle.EXPRESSIVE -> if (isHeroHighlight) 86.dp else 80.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .offset(y = yShift)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(faceSize + 20.dp)
                .drawBehind {
                    when (style) {
                        ProtoStyle.BASELINE -> {
                            val r = size.minDimension * 0.56f
                            drawCircle(
                                brush = Brush.radialGradient(listOf(glowColor, Color.Transparent), center, r),
                                radius = r,
                                center = center,
                            )
                        }
                        ProtoStyle.AURA -> {
                            val outerR = size.minDimension * (if (isHeroHighlight) 0.72f else 0.62f)
                            drawCircle(
                                brush = Brush.radialGradient(listOf(glowColor, Color.Transparent), center, outerR),
                                radius = outerR,
                                center = center,
                            )
                            if (isHeroHighlight) {
                                drawCircle(
                                    color = faceAccent.copy(alpha = 0.42f),
                                    radius = size.minDimension * 0.47f,
                                    center = center,
                                    style = Stroke(width = 1.6.dp.toPx()),
                                )
                                drawCircle(
                                    color = faceAccent.copy(alpha = 0.18f),
                                    radius = size.minDimension * 0.55f,
                                    center = center,
                                    style = Stroke(width = 1.2.dp.toPx()),
                                )
                            }
                        }
                        ProtoStyle.EXPRESSIVE -> {
                            val outerR = size.minDimension * 0.66f
                            drawCircle(
                                brush = Brush.radialGradient(listOf(glowColor, Color.Transparent), center, outerR),
                                radius = outerR,
                                center = center,
                            )
                            if (isHeroHighlight) {
                                // Kinetic expanding pulse waves + sparkle dots
                                drawCircle(
                                    color = faceAccent.copy(alpha = 0.55f),
                                    radius = size.minDimension * 0.48f,
                                    center = center,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                                drawCircle(
                                    color = faceAccent.copy(alpha = 0.25f),
                                    radius = size.minDimension * 0.56f,
                                    center = center,
                                    style = Stroke(width = 1.4.dp.toPx()),
                                )
                                val sparks = listOf(-40f, 35f, 145f, 220f)
                                sparks.forEach { deg ->
                                    val rad = Math.toRadians(deg.toDouble())
                                    val dist = size.minDimension * 0.54f
                                    drawCircle(
                                        color = faceAccent.copy(alpha = 0.85f),
                                        radius = 2.4.dp.toPx(),
                                        center = Offset(
                                            center.x + (dist * cos(rad)).toFloat(),
                                            center.y + (dist * sin(rad)).toFloat(),
                                        ),
                                    )
                                }
                            }
                        }
                        ProtoStyle.ORGANIC -> {
                            // Soft warm pebble halo
                            val haloR = size.minDimension * 0.64f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(glowColor, glowColor.copy(alpha = 0.15f), Color.Transparent),
                                    center = center,
                                    radius = haloR,
                                ),
                                radius = haloR,
                                center = center,
                            )
                            if (isHeroHighlight) {
                                drawCircle(
                                    color = faceAccent.copy(alpha = 0.34f),
                                    radius = size.minDimension * 0.47f,
                                    center = center,
                                    style = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                },
        ) {
            PrototypeFaceIcon(style = style, mood = mood, size = faceSize)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isHeroHighlight && style != ProtoStyle.BASELINE) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (isHeroHighlight && style != ProtoStyle.BASELINE) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

// =============================================================================
// Prototype Today Screen
// =============================================================================

@Composable
private fun PrototypeTodayScreen(
    style: ProtoStyle,
    entries: List<Entry>,
    placesById: Map<String, String>,
) {
    val cardShape = if (style == ProtoStyle.ORGANIC) RoundedCornerShape(28.dp) else RoundedCornerShape(22.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        PrototypeBrandHeader(style = style)

        Text(
            text = stringResource(R.string.today_question),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrototypeFaceButton(style = style, mood = Mood.LOW, isHeroHighlight = false)
            PrototypeFaceButton(style = style, mood = Mood.OKAY, isHeroHighlight = false)
            PrototypeFaceButton(style = style, mood = Mood.GOOD, isHeroHighlight = true)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.today_entries_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            entries.forEach { entry ->
                PrototypeTodayEntryCard(
                    style = style,
                    shape = cardShape,
                    entry = entry,
                    placeName = placesById[entry.id],
                )
            }
        }

        GlassPillButton(
            text = stringResource(R.string.today_all_entries),
            onClick = {},
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrototypeTodayEntryCard(
    style: ProtoStyle,
    shape: Shape,
    entry: Entry,
    placeName: String?,
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    val catalog = remember(context) { EmotionTagsCatalog.get(context) }
    val timeFormatter = remember {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    }

    val moodAccent = when (entry.mood) {
        Mood.LOW -> smileColors.moodLow
        Mood.OKAY -> smileColors.moodOkay
        Mood.GOOD -> smileColors.moodGood
    }

    val fillBrush = remember(smileColors, style, entry.mood) {
        if (style == ProtoStyle.BASELINE) {
            Brush.verticalGradient(listOf(smileColors.glassTop, smileColors.glassBottom))
        } else {
            val leftWash = lerp(smileColors.glassTop, moodAccent, if (smileColors.isDark) 0.14f else 0.08f)
            Brush.horizontalGradient(listOf(leftWash, smileColors.glassBottom))
        }
    }
    val borderBrush = remember(smileColors, style, entry.mood) {
        if (style == ProtoStyle.BASELINE) {
            Brush.verticalGradient(listOf(smileColors.glassBorderTop, smileColors.glassBorderBottom))
        } else {
            Brush.horizontalGradient(
                listOf(moodAccent.copy(alpha = 0.45f), smileColors.glassBorderBottom),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brush = fillBrush, shape = shape)
            .border(width = 1.dp, brush = borderBrush, shape = shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (style == ProtoStyle.EXPRESSIVE) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(moodAccent),
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.drawBehind {
                    if (style != ProtoStyle.BASELINE) {
                        val r = size.minDimension * 0.78f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(moodAccent.copy(alpha = 0.32f), Color.Transparent),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                            center = center,
                        )
                    }
                },
            ) {
                PrototypeFaceIcon(style = style, mood = entry.mood, size = 40.dp)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = timeFormatter.format(entry.createdAt),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (placeName != null) {
                        GlassTagPill(
                            text = placeName,
                            containerColor = smileColors.brandAccent.copy(alpha = 0.16f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            borderColor = smileColors.brandAccent.copy(alpha = 0.35f),
                        )
                    }
                    entry.tags.take(3).forEach { tagId ->
                        val coreColor = resolveTagCoreColor(catalog, tagId)
                        if (style == ProtoStyle.BASELINE || coreColor == null) {
                            GlassTagPill(text = context.resolveTagLabel(tagId))
                        } else {
                            val pillBg = if (smileColors.isDark) {
                                coreColor.copy(alpha = 0.22f)
                            } else {
                                lerp(coreColor, Color.White, 0.55f)
                            }
                            val pillFg = if (smileColors.isDark) {
                                lerp(coreColor, Color.White, 0.55f)
                            } else {
                                Color(0xFF1F1C26)
                            }
                            GlassTagPill(
                                text = context.resolveTagLabel(tagId),
                                containerColor = pillBg,
                                contentColor = pillFg,
                                borderColor = coreColor.copy(alpha = if (smileColors.isDark) 0.55f else 0.70f),
                            )
                        }
                    }
                }
                if (!entry.comment.isNullOrBlank()) {
                    Text(
                        text = entry.comment.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun resolveTagCoreColor(catalog: EmotionTags, tagId: String): Color? {
    val coreId = tagId.substringBefore('.')
    val core = catalog.cores.firstOrNull { it.id == coreId } ?: return null
    return parseCoreColor(core.color)
}

// =============================================================================
// Prototype Week Screen
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrototypeWeekScreen(
    style: ProtoStyle,
    entries: List<Entry>,
    weekStart: LocalDate,
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    val plotPoints = remember(entries) { entries.map(WeekPlotMath::pointForEntry) }
    val dateFormatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val startStr = dateFormatter.format(weekStart)
    val endStr = dateFormatter.format(weekStart.plusDays(7))

    val cardShape = if (style == ProtoStyle.ORGANIC) RoundedCornerShape(28.dp) else RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        GlassTopBar(title = stringResource(R.string.week_title))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.week_period, startStr, endStr),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                WeekNavChip(mirror = true)
                WeekNavChip(mirror = false)
            }

            GlassCard(
                shape = cardShape,
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.week_day_time_patterns),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrototypeDayTimePlot(
                        style = style,
                        points = plotPoints,
                        smileColors = smileColors,
                    )
                }
            }

            GlassCard(
                shape = if (style == ProtoStyle.ORGANIC) RoundedCornerShape(26.dp) else RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.week_most_checkins_between, "8 AM", "9 PM"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.week_legend_prefix),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        listOf(
                            Mood.LOW to stringResource(R.string.week_legend_sad),
                            Mood.OKAY to stringResource(R.string.week_legend_neutral),
                            Mood.GOOD to stringResource(R.string.week_legend_happy),
                        ).forEach { (mood, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PrototypeFaceIcon(style = style, mood = mood, size = 18.dp)
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    val daysTrackedStr = pluralStringResource(R.plurals.week_days_tracked, 7, 7)
                    val checkInsStr = pluralStringResource(R.plurals.week_checkins_count, entries.size, entries.size)
                    Text(
                        text = stringResource(R.string.week_summary_footer, daysTrackedStr, checkInsStr),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekNavChip(mirror: Boolean) {
    val smileColors = LocalSmileProcColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(smileColors.chipFill)
            .border(1.dp, smileColors.chipBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(18.dp)
                .then(if (mirror) Modifier.graphicsLayer { scaleX = -1f } else Modifier),
        )
    }
}

@Composable
private fun PrototypeDayTimePlot(
    style: ProtoStyle,
    points: List<PlotPoint>,
    smileColors: SmileProcColors,
) {
    val yAxisLabels = listOf(
        stringResource(R.string.week_hour_12am),
        stringResource(R.string.week_hour_8pm),
        stringResource(R.string.week_hour_4pm),
        stringResource(R.string.week_hour_12pm),
        stringResource(R.string.week_hour_8am),
        stringResource(R.string.week_hour_4am),
        stringResource(R.string.week_hour_12am),
    )
    val weekdayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
    val gridHeight = 252.dp
    val gridBackgroundColor = smileColors.glassBottom.copy(alpha = if (smileColors.isDark) 0.45f else 0.55f)
    val gridLineColor = smileColors.pillBorder.copy(alpha = 0.6f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .height(gridHeight)
                .widthIn(min = 42.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            yAxisLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight),
            ) {
                val width = size.width
                val height = size.height
                val colWidth = width / WeekPlotMath.GRID_COLS
                val rowHeight = height / WeekPlotMath.GRID_ROWS
                val strokePx = 1.dp.toPx()

                when (style) {
                    ProtoStyle.BASELINE -> {
                        drawRect(gridBackgroundColor, Offset.Zero, Size(width, height))
                        for (row in 0..WeekPlotMath.GRID_ROWS) {
                            val y = row * rowHeight
                            drawLine(gridLineColor, Offset(0f, y), Offset(width, y), strokePx)
                        }
                        for (col in 0..WeekPlotMath.GRID_COLS) {
                            val x = col * colWidth
                            drawLine(gridLineColor, Offset(x, 0f), Offset(x, height), strokePx)
                        }
                    }

                    ProtoStyle.AURA -> {
                        // Translucent aurora backdrop + soft dashed horizontal markers
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    smileColors.brandAccent.copy(alpha = 0.14f),
                                    smileColors.glowSecondary.copy(alpha = 0.12f),
                                    gridBackgroundColor,
                                ),
                            ),
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        )
                        val softLine = smileColors.pillBorder.copy(alpha = 0.28f)
                        for (row in 1 until WeekPlotMath.GRID_ROWS) {
                            val y = row * rowHeight
                            drawLine(
                                color = softLine,
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = strokePx,
                                pathEffect = if (row % 2 == 1) PathEffect.dashPathEffect(floatArrayOf(6f, 8f)) else null,
                            )
                        }
                        for (col in 1 until WeekPlotMath.GRID_COLS) {
                            val x = col * colWidth
                            drawLine(softLine.copy(alpha = 0.22f), Offset(x, 0f), Offset(x, height), strokePx)
                        }
                    }

                    ProtoStyle.EXPRESSIVE -> {
                        // Alternating daypart tonal bands + pulse ribbon connecting points
                        drawRoundRect(
                            color = gridBackgroundColor,
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        )
                        for (band in 0 until 6) {
                            if (band % 2 == 0) {
                                drawRect(
                                    color = smileColors.brandAccent.copy(alpha = if (smileColors.isDark) 0.08f else 0.05f),
                                    topLeft = Offset(0f, band * 2 * rowHeight),
                                    size = Size(width, 2 * rowHeight),
                                )
                            }
                        }
                        for (col in 0 until WeekPlotMath.GRID_COLS) {
                            val pad = 4.dp.toPx()
                            drawRoundRect(
                                color = Color.White.copy(alpha = if (smileColors.isDark) 0.03f else 0.28f),
                                topLeft = Offset(col * colWidth + pad, pad),
                                size = Size(colWidth - pad * 2, height - pad * 2),
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                            )
                        }
                    }

                    ProtoStyle.ORGANIC -> {
                        // Soft horizon bands (night, morning, afternoon, evening) + vertical pill tracks
                        val dayBands = if (smileColors.isDark) {
                            listOf(
                                smileColors.brandAccent.copy(alpha = 0.06f),
                                smileColors.moodOkay.copy(alpha = 0.08f),
                                smileColors.moodGood.copy(alpha = 0.07f),
                                smileColors.glowSecondary.copy(alpha = 0.10f),
                            )
                        } else {
                            listOf(
                                Color(0xFFF0E6DC).copy(alpha = 0.55f),
                                Color(0xFFFFECD2).copy(alpha = 0.65f),
                                Color(0xFFE3F2E8).copy(alpha = 0.65f),
                                Color(0xFFEDE4F2).copy(alpha = 0.55f),
                            )
                        }
                        val bandHeight = height / 4f
                        dayBands.forEachIndexed { idx, bandColor ->
                            drawRect(
                                color = bandColor,
                                topLeft = Offset(0f, idx * bandHeight),
                                size = Size(width, bandHeight),
                            )
                        }
                        val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 9f))
                        for (i in 1..5) {
                            val y = i * (height / 6f)
                            drawLine(
                                color = smileColors.pillBorder.copy(alpha = 0.45f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = strokePx,
                                pathEffect = dash,
                            )
                        }
                        for (col in 0 until WeekPlotMath.GRID_COLS) {
                            val pad = 3.dp.toPx()
                            drawRoundRect(
                                color = Color.White.copy(alpha = if (smileColors.isDark) 0.04f else 0.35f),
                                topLeft = Offset(col * colWidth + pad, 0f),
                                size = Size(colWidth - pad * 2, height),
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                            )
                        }
                    }
                }

                // Point centers
                val smileyRadius = min(colWidth * 0.34f, 15.dp.toPx())
                val coords = points.map { point ->
                    val cx = (point.dayIndex + 0.5f) * colWidth
                    val rawCy = height * (1f - point.yFractionFromBottom)
                    val cy = rawCy.coerceIn(smileyRadius, height - smileyRadius)
                    point to Offset(cx, cy)
                }

                // Connecting mood pulse thread for Expressive & Organic
                if ((style == ProtoStyle.EXPRESSIVE || style == ProtoStyle.ORGANIC) && coords.size >= 2) {
                    val ribbonPath = Path().apply {
                        moveTo(coords.first().second.x, coords.first().second.y)
                        for (i in 1 until coords.size) {
                            val prev = coords[i - 1].second
                            val curr = coords[i].second
                            val midX = (prev.x + curr.x) / 2f
                            cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                        }
                    }
                    drawPath(
                        path = ribbonPath,
                        color = smileColors.brandAccent.copy(alpha = if (style == ProtoStyle.EXPRESSIVE) 0.42f else 0.30f),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }

                // Plotted points
                coords.forEach { (point, ptCenter) ->
                    val moodColor = when (point.mood) {
                        Mood.LOW -> smileColors.moodLow
                        Mood.OKAY -> smileColors.moodOkay
                        Mood.GOOD -> smileColors.moodGood
                    }
                    if (style != ProtoStyle.BASELINE) {
                        val haloRadius = smileyRadius * (if (style == ProtoStyle.AURA) 2.2f else 1.85f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(moodColor.copy(alpha = 0.45f), Color.Transparent),
                                center = ptCenter,
                                radius = haloRadius,
                            ),
                            radius = haloRadius,
                            center = ptCenter,
                        )
                    }
                    drawPlotDotFace(
                        style = style,
                        mood = point.mood,
                        center = ptCenter,
                        radius = smileyRadius,
                        borderColor = smileColors.canvasBase,
                        fillColor = moodColor,
                        featureColor = when (point.mood) {
                            Mood.LOW -> smileColors.moodLowFeature
                            Mood.OKAY -> smileColors.moodOkayFeature
                            Mood.GOOD -> smileColors.moodGoodFeature
                        },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLetters.forEach { letter ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = letter,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawPlotDotFace(
    style: ProtoStyle,
    mood: Mood,
    center: Offset,
    radius: Float,
    borderColor: Color,
    fillColor: Color,
    featureColor: Color,
) {
    drawCircle(color = borderColor, radius = radius, center = center)
    val faceR = radius * 0.90f
    val highlight = lerp(fillColor, Color.White, if (style == ProtoStyle.BASELINE) 0.0f else 0.25f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(highlight, fillColor),
            center = Offset(center.x - faceR * 0.2f, center.y - faceR * 0.25f),
            radius = faceR * 1.25f,
        ),
        radius = faceR,
        center = center,
    )
    val strokeW = (faceR * 0.14f).coerceAtLeast(1.4f)
    val eyeX = faceR * 0.34f
    val eyeY = faceR * 0.16f
    val eyeR = (faceR * 0.11f).coerceAtLeast(1.2f)

    when (mood) {
        Mood.LOW -> {
            drawCircle(featureColor, eyeR, Offset(center.x - eyeX, center.y - eyeY * 0.6f))
            drawCircle(featureColor, eyeR, Offset(center.x + eyeX, center.y - eyeY * 0.6f))
            val mouthW = faceR * 0.68f
            val path = Path().apply {
                arcTo(
                    Rect(center.x - mouthW / 2f, center.y + faceR * 0.20f, center.x + mouthW / 2f, center.y + faceR * 0.58f),
                    200f, 140f, true,
                )
            }
            drawPath(path, featureColor, style = Stroke(strokeW, cap = StrokeCap.Round))
        }
        Mood.OKAY -> {
            drawCircle(featureColor, eyeR, Offset(center.x - eyeX, center.y - eyeY))
            drawCircle(featureColor, eyeR, Offset(center.x + eyeX, center.y - eyeY))
            val halfW = faceR * 0.34f
            drawLine(
                featureColor,
                Offset(center.x - halfW, center.y + faceR * 0.28f),
                Offset(center.x + halfW, center.y + faceR * 0.28f),
                strokeW,
                StrokeCap.Round,
            )
        }
        Mood.GOOD -> {
            val archW = faceR * 0.30f
            val archH = faceR * 0.22f
            listOf(-eyeX, eyeX).forEach { dx ->
                val eyePath = Path().apply {
                    arcTo(
                        Rect(center.x + dx - archW / 2f, center.y - eyeY - archH / 2f, center.x + dx + archW / 2f, center.y - eyeY + archH / 2f),
                        200f, 140f, true,
                    )
                }
                drawPath(eyePath, featureColor, style = Stroke(strokeW * 0.9f, cap = StrokeCap.Round))
            }
            val mouthW = faceR * 0.74f
            val path = Path().apply {
                arcTo(
                    Rect(center.x - mouthW / 2f, center.y + faceR * 0.02f, center.x + mouthW / 2f, center.y + faceR * 0.50f),
                    20f, 140f, true,
                )
            }
            drawPath(path, featureColor, style = Stroke(strokeW, cap = StrokeCap.Round))
        }
    }
}

// =============================================================================
// Prototype Follow-up Sheet + Emotion Wheel
// =============================================================================

@Composable
private fun PrototypeFollowUpOverlay(
    style: ProtoStyle,
    selectedTags: List<String>,
    note: String,
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    val catalog = remember(context) { EmotionTagsCatalog.get(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = smileColors.sheetContainer,
            border = if (style != ProtoStyle.BASELINE) {
                BorderStroke(1.dp, smileColors.glassBorderTop)
            } else {
                null
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(smileColors.pillBorder),
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.today_saved_header),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.today_followup_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Selected tag echo chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectedTags.forEach { tagId ->
                        val label = context.resolveTagLabel(tagId)
                        val coreColor = resolveTagCoreColor(catalog, tagId)
                        val bg = if (style == ProtoStyle.BASELINE || coreColor == null) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else if (smileColors.isDark) {
                            coreColor.copy(alpha = 0.28f)
                        } else {
                            lerp(coreColor, Color.White, 0.48f)
                        }
                        val fg = if (style == ProtoStyle.BASELINE || coreColor == null) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else if (smileColors.isDark) {
                            lerp(coreColor, Color.White, 0.6f)
                        } else {
                            Color(0xFF1F1C26)
                        }
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(label) },
                            shape = CircleShape,
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = bg,
                                selectedLabelColor = fg,
                                selectedTrailingIconColor = fg,
                            ),
                            border = if (style != ProtoStyle.BASELINE && coreColor != null) {
                                InputChipDefaults.inputChipBorder(
                                    enabled = true,
                                    selected = true,
                                    selectedBorderColor = coreColor.copy(alpha = 0.7f),
                                )
                            } else {
                                null
                            },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.heightIn(min = 42.dp),
                        )
                    }
                }

                // Prototype Emotion Wheel
                PrototypeEmotionWheel(
                    style = style,
                    selectedTags = selectedTags,
                    catalog = catalog,
                )

                // Quick core zoom pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text(stringResource(R.string.tags_all_feelings)) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                    catalog.cores.take(4).forEach { core ->
                        val coreCol = parseCoreColor(core.color)
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(context.resolveTagLabel(core.id)) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (style == ProtoStyle.BASELINE) {
                                    smileColors.chipFill
                                } else {
                                    coreCol.copy(alpha = if (smileColors.isDark) 0.16f else 0.25f)
                                },
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = if (style == ProtoStyle.BASELINE) smileColors.chipBorder else coreCol.copy(alpha = 0.45f),
                            ),
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.today_comment_label)) },
                    shape = RoundedCornerShape(if (style == ProtoStyle.ORGANIC) 22.dp else 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = smileColors.pillBorder,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Button(
                    onClick = {},
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}

@Composable
private fun PrototypeEmotionWheel(
    style: ProtoStyle,
    selectedTags: List<String>,
    catalog: EmotionTags,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val smileColors = LocalSmileProcColors.current
    val surfaceColor = smileColors.sheetContainer
    val strokeColor = surfaceColor.copy(alpha = 0.94f)
    val highlightColor = if (style == ProtoStyle.BASELINE) smileColors.wheelSelectionStroke else Color.White

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val diameter = min(maxWidth - 16.dp, 310.dp)
        val diameterPx = with(density) { diameter.toPx() }
        val baseRadiusPx = diameterPx / 2f
        val popReservePx = if (style == ProtoStyle.EXPRESSIVE) with(density) { 6.dp.toPx() } else 0f
        val radiusPx = baseRadiusPx - popReservePx
        val holeRadiusPx = radiusPx * EmotionWheelMath.HOLE_RATIO
        val innerRadiusPx = radiusPx * EmotionWheelMath.INNER_RATIO
        val middleRadiusPx = radiusPx * EmotionWheelMath.MIDDLE_RATIO

        val textPaint = remember(density) {
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
        }

        Canvas(modifier = Modifier.size(diameter)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val coreCount = catalog.cores.size
            if (coreCount == 0) return@Canvas
            val coreSweep = 360f / coreCount
            val dividerPx = with(density) {
                when (style) {
                    ProtoStyle.ORGANIC -> 1.6.dp.toPx()
                    else -> 0.9.dp.toPx()
                }
            }
            val highlightPx = with(density) { 2.6.dp.toPx() }

            // Soft aura behind wheel for Aura/Organic
            if (style == ProtoStyle.AURA || style == ProtoStyle.ORGANIC) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(smileColors.brandAccent.copy(alpha = 0.22f), Color.Transparent),
                        center = center,
                        radius = radiusPx * 1.12f,
                    ),
                    radius = radiusPx * 1.12f,
                    center = center,
                )
            }

            catalog.cores.forEachIndexed { i, core ->
                val startAngle = EmotionWheelMath.START_ANGLE_DEG + (i * coreSweep)
                val rawColor = parseCoreColor(core.color)
                val baseColor = if (style == ProtoStyle.ORGANIC) {
                    lerp(rawColor, Color(0xFFFFE0C2), 0.14f)
                } else {
                    rawColor
                }

                val corePath = Path().apply {
                    arcWedge(center, holeRadiusPx, innerRadiusPx, startAngle, coreSweep)
                }
                drawPath(corePath, baseColor)
                drawPath(corePath, strokeColor, style = Stroke(dividerPx))

                val coreMidAngle = startAngle + coreSweep / 2f
                drawWheelLabel(
                    label = context.resolveTagLabel(core.id),
                    center = center,
                    angleDegrees = coreMidAngle,
                    midRadius = (holeRadiusPx + innerRadiusPx) / 2f,
                    ringThickness = innerRadiusPx - holeRadiusPx,
                    baseTextSizeSp = 8.5f,
                    curved = true,
                    density = density,
                    textPaint = textPaint,
                )

                val groupCount = core.groups.size
                if (groupCount > 0) {
                    val groupSweep = coreSweep / groupCount
                    core.groups.forEachIndexed { g, group ->
                        val gStart = startAngle + (g * groupSweep)
                        val isGroupSel = group.id in selectedTags
                        val middleColor = lerp(baseColor, Color.White, if (g % 2 == 0) 0.14f else 0.04f)
                        val groupPath = Path().apply {
                            arcWedge(center, innerRadiusPx, middleRadiusPx, gStart, groupSweep)
                        }
                        drawPath(groupPath, middleColor)
                        drawPath(groupPath, strokeColor, style = Stroke(dividerPx))
                        if (isGroupSel) {
                            drawPath(groupPath, highlightColor, style = Stroke(highlightPx))
                        }

                        drawWheelLabel(
                            label = context.resolveTagLabel(group.id),
                            center = center,
                            angleDegrees = gStart + groupSweep / 2f,
                            midRadius = (innerRadiusPx + middleRadiusPx) / 2f,
                            ringThickness = middleRadiusPx - innerRadiusPx,
                            baseTextSizeSp = 6.2f,
                            curved = false,
                            density = density,
                            textPaint = textPaint,
                        )

                        val outerCount = group.outer.size
                        if (outerCount > 0) {
                            val outerSweep = groupSweep / outerCount
                            group.outer.forEachIndexed { o, outerId ->
                                val oStart = gStart + (o * outerSweep)
                                val isOuterSel = outerId in selectedTags
                                val outerColor = lerp(baseColor, Color.White, if ((g * outerCount + o) % 2 == 0) 0.28f else 0.18f)
                                val outerR = if (isOuterSel && style == ProtoStyle.EXPRESSIVE) radiusPx + popReservePx else radiusPx
                                val outerPath = Path().apply {
                                    arcWedge(center, middleRadiusPx, outerR, oStart, outerSweep)
                                }
                                drawPath(outerPath, outerColor)
                                drawPath(outerPath, strokeColor, style = Stroke(dividerPx))
                                if (isOuterSel) {
                                    drawPath(outerPath, highlightColor, style = Stroke(highlightPx))
                                }
                                drawWheelLabel(
                                    label = context.resolveTagLabel(outerId),
                                    center = center,
                                    angleDegrees = oStart + outerSweep / 2f,
                                    midRadius = (middleRadiusPx + outerR) / 2f,
                                    ringThickness = outerR - middleRadiusPx,
                                    baseTextSizeSp = 5.6f,
                                    curved = false,
                                    density = density,
                                    textPaint = textPaint,
                                )
                            }
                        }
                    }
                }
            }

            // Center hub
            drawCircle(surfaceColor, holeRadiusPx, center)
            drawCircle(
                color = if (style == ProtoStyle.BASELINE) strokeColor else smileColors.brandAccent.copy(alpha = 0.55f),
                radius = holeRadiusPx,
                center = center,
                style = Stroke(width = if (style == ProtoStyle.BASELINE) dividerPx else 1.5.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawWheelLabel(
    label: String,
    center: Offset,
    angleDegrees: Float,
    midRadius: Float,
    ringThickness: Float,
    baseTextSizeSp: Float,
    curved: Boolean,
    density: androidx.compose.ui.unit.Density,
    textPaint: Paint,
) {
    drawContext.canvas.nativeCanvas.apply {
        save()
        val normAngle = (angleDegrees % 360f + 360f) % 360f
        textPaint.color = Color(0xFF1C1B1F).toArgb()
        if (curved) {
            val isBottom = normAngle in 0f..180f
            rotate(if (isBottom) normAngle - 90f else normAngle + 90f, center.x, center.y)
            textPaint.textSize = with(density) { baseTextSizeSp.sp.toPx() }
            val textY = if (isBottom) center.y + midRadius + textPaint.textSize * 0.35f else center.y - midRadius + textPaint.textSize * 0.35f
            drawText(label, center.x, textY, textPaint)
        } else {
            val isLeft = normAngle in 90f..270f
            rotate(if (isLeft) normAngle + 180f else normAngle, center.x, center.y)
            val maxW = (ringThickness - with(density) { 6.dp.toPx() }).coerceAtLeast(16f)
            var sizePx = with(density) { baseTextSizeSp.sp.toPx() }
            textPaint.textSize = sizePx
            val measured = textPaint.measureText(label)
            if (measured > maxW && measured > 0f) {
                sizePx = (sizePx * (maxW / measured)).coerceAtLeast(with(density) { 4.5.sp.toPx() })
                textPaint.textSize = sizePx
            }
            val textX = if (isLeft) center.x - midRadius else center.x + midRadius
            drawText(label, textX, center.y + sizePx * 0.34f, textPaint)
        }
        restore()
    }
}

private fun Path.arcWedge(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startAngleDegrees: Float,
    sweepAngleDegrees: Float,
) {
    reset()
    val endRad = Math.toRadians((startAngleDegrees + sweepAngleDegrees).toDouble())
    arcTo(
        rect = Rect(center.x - outerRadius, center.y - outerRadius, center.x + outerRadius, center.y + outerRadius),
        startAngleDegrees = startAngleDegrees,
        sweepAngleDegrees = sweepAngleDegrees,
        forceMoveTo = true,
    )
    lineTo(
        (center.x + innerRadius * cos(endRad)).toFloat(),
        (center.y + innerRadius * sin(endRad)).toFloat(),
    )
    arcTo(
        rect = Rect(center.x - innerRadius, center.y - innerRadius, center.x + innerRadius, center.y + innerRadius),
        startAngleDegrees = startAngleDegrees + sweepAngleDegrees,
        sweepAngleDegrees = -sweepAngleDegrees,
        forceMoveTo = false,
    )
    close()
}

// =============================================================================
// Sample Data
// =============================================================================

private fun sampleTodayEntries(today: LocalDate, zone: ZoneId): List<Entry> {
    fun at(hour: Int, minute: Int): Instant =
        ZonedDateTime.of(today, LocalTime.of(hour, minute), zone).toInstant()

    return listOf(
        Entry(
            id = "00000000-0000-4000-8000-000000000001",
            createdAt = at(13, 46),
            updatedAt = at(13, 46),
            timeZoneID = zone.id,
            mood = Mood.GOOD,
            tags = listOf("calm.peaceful", "happy.grateful"),
            comment = "Great walk in the afternoon sun",
            location = null,
            source = EntrySource.APP,
        ),
        Entry(
            id = "00000000-0000-4000-8000-000000000002",
            createdAt = at(9, 15),
            updatedAt = at(9, 15),
            timeZoneID = zone.id,
            mood = Mood.OKAY,
            tags = listOf("calm.content"),
            comment = null,
            location = null,
            source = EntrySource.NOTIFICATION,
        ),
    )
}

private fun sampleWeekEntries(monday: LocalDate, zone: ZoneId): List<Entry> {
    val specs = listOf(
        Triple(0L, 8 to 30, Mood.OKAY),
        Triple(0L, 14 to 20, Mood.LOW),
        Triple(0L, 19 to 40, Mood.OKAY),
        Triple(1L, 9 to 0, Mood.GOOD),
        Triple(1L, 16 to 10, Mood.GOOD),
        Triple(2L, 10 to 15, Mood.OKAY),
        Triple(2L, 15 to 0, Mood.LOW),
        Triple(2L, 20 to 30, Mood.GOOD),
        Triple(3L, 8 to 45, Mood.GOOD),
        Triple(3L, 13 to 30, Mood.GOOD),
        Triple(4L, 9 to 20, Mood.OKAY),
        Triple(4L, 17 to 45, Mood.GOOD),
        Triple(5L, 11 to 30, Mood.GOOD),
        Triple(5L, 19 to 15, Mood.GOOD),
        Triple(6L, 13 to 45, Mood.GOOD),
    )
    return specs.mapIndexed { idx, (dayOffset, hm, mood) ->
        val instant = ZonedDateTime.of(
            monday.plusDays(dayOffset),
            LocalTime.of(hm.first, hm.second),
            zone,
        ).toInstant()
        Entry(
            id = String.format(java.util.Locale.US, "00000000-0000-4000-8000-0000000001%02x", idx),
            createdAt = instant,
            updatedAt = instant,
            timeZoneID = zone.id,
            mood = mood,
            tags = emptyList(),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )
    }
}
