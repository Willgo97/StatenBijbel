package nl.statenbijbel.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

data class ReadingColors(
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val verseNumber: Color,
    val marker: Color,
    val selection: Color,
    val divider: Color,
    val surface: Color,
    val notesBackground: Color,
    val dark: Boolean,
)

val LocalReadingColors = staticCompositionLocalOf {
    lightColors
}

enum class Accent(val key: String, val label: String, val light: Color, val dark: Color) {
    GOLD("GOUD", "Goud", Color(0xFF8A6431), Color(0xFFD9B566)),
    ROSE("ROZEROOD", "Rozerood", Color(0xFFB0355C), Color(0xFFEE8CA9)),
    BORDEAUX("BORDEAUX", "Bordeaux", Color(0xFF9A3430), Color(0xFFE58C7E)),
    COPPER("KOPER", "Koper", Color(0xFF9E5522), Color(0xFFE8A46E)),
    BLUE("BLAUW", "Blauw", Color(0xFF2C5B8C), Color(0xFF88B8E6)),
    GREEN("GROEN", "Groen", Color(0xFF36684A), Color(0xFF89C8A2)),
    PURPLE("PAARS", "Paars", Color(0xFF67488B), Color(0xFFBB9EDE)),
    INK("INKT", "Inkt", Color(0xFF4B463F), Color(0xFFB7B2AA)),
    ;

    fun color(darkTheme: Boolean): Color = if (darkTheme) dark else light

    companion object {
        fun fromKey(key: String?): Accent? = entries.firstOrNull { it.key == key }
    }
}

internal val lightColors = ReadingColors(
    paper = Color(0xFFFFFDFB),
    ink = Color(0xFF1B1917),
    muted = Color(0xFF6E6862),
    accent = Color(0xFF8A6431),
    verseNumber = Color(0xFFA79B8A),
    marker = Color(0xFFB07C2E),
    selection = Color(0x1F8A6431),
    divider = Color(0x14000000),
    surface = Color(0xFFF2EEE7),
    notesBackground = Color(0x0F8A6431),
    dark = false,
)

private val sepiaColors = ReadingColors(
    paper = Color(0xFFF7EEDD),
    ink = Color(0xFF33291D),
    muted = Color(0xFF6F6252),
    accent = Color(0xFF8A5A22),
    verseNumber = Color(0xFFAE9E84),
    marker = Color(0xFF9C6A22),
    selection = Color(0x22A0701F),
    divider = Color(0x18503C20),
    surface = Color(0xFFEDE0C8),
    notesBackground = Color(0x148A5A22),
    dark = false,
)

private val darkColors = ReadingColors(
    paper = Color(0xFF15171A),
    ink = Color(0xFFDEDAD3),
    muted = Color(0xFF938D85),
    accent = Color(0xFFD9B566),
    verseNumber = Color(0xFF6E6862),
    marker = Color(0xFFD2A950),
    selection = Color(0x33C9A227),
    divider = Color(0x1AFFFFFF),
    surface = Color(0xFF20242A),
    notesBackground = Color(0x14FFFFFF),
    dark = true,
)

private val nightColors = ReadingColors(
    paper = Color(0xFF000000),
    ink = Color(0xFFB4AFA8),
    muted = Color(0xFF7A756E),
    accent = Color(0xFFBE9A43),
    verseNumber = Color(0xFF5A554F),
    marker = Color(0xFFB08F3E),
    selection = Color(0x33C9A227),
    divider = Color(0x14FFFFFF),
    surface = Color(0xFF17191C),
    notesBackground = Color(0x10FFFFFF),
    dark = true,
)

@Composable
fun StatenBijbelTheme(content: @Composable () -> Unit) {
    val palette = readingColors(Prefs.theme, Prefs.accent, isSystemInDarkTheme())
    val scheme = colorScheme(palette, sepia = Prefs.theme == Theme.SEPIA)
    // The status bar follows the chosen theme, not the system one.
    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(palette.dark) {
            val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !palette.dark
                isAppearanceLightNavigationBars = !palette.dark
            }
        }
    }

    CompositionLocalProvider(LocalReadingColors provides palette) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

// Kept out of the composable: with all its Color longs inlined there, R8 produces code that
// Android 11's verifier rejects.
private fun readingColors(theme: Theme, accent: Accent, systemDark: Boolean): ReadingColors {
    val base = when (theme) {
        Theme.LIGHT -> lightColors
        Theme.SEPIA -> sepiaColors
        Theme.DARK -> darkColors
        Theme.NIGHT -> nightColors
        Theme.SYSTEM -> if (systemDark) darkColors else lightColors
    }
    val chosen = accent.color(base.dark)
    return base.copy(
        accent = chosen,
        marker = chosen,
        selection = chosen.copy(alpha = if (base.dark) 0.26f else 0.15f),
    )
}

private fun colorScheme(palette: ReadingColors, sepia: Boolean): ColorScheme =
    if (palette.dark) darkColorScheme(
        primary = palette.accent,
        onPrimary = Color(0xFF20180A),
        surface = palette.paper,
        onSurface = palette.ink,
        background = palette.paper,
        onBackground = palette.ink,
        surfaceVariant = palette.surface,
        onSurfaceVariant = palette.muted,
        outline = palette.muted,
        secondaryContainer = Color(0xFF2A2E34),
        onSecondaryContainer = palette.ink,
    ) else lightColorScheme(
        primary = palette.accent,
        onPrimary = Color.White,
        surface = palette.paper,
        onSurface = palette.ink,
        background = palette.paper,
        onBackground = palette.ink,
        surfaceVariant = palette.surface,
        onSurfaceVariant = palette.muted,
        outline = palette.muted,
        secondaryContainer = if (sepia) Color(0xFFE8D8BA) else Color(0xFFEDE7DC),
        onSecondaryContainer = palette.ink,
    )

private val typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp
    ),
)

// Scripture and notes follow the reading settings; [scale] is relative to the chosen size.
@Composable
fun readingStyle(scale: Float = 1f): TextStyle {
    val size = Prefs.textSize * scale
    return TextStyle(
        fontFamily = if (Prefs.serif) FontFamily.Serif else FontFamily.SansSerif,
        fontSize = size.sp,
        lineHeight = (size * Prefs.lineHeight / 100f).sp,
        color = LocalReadingColors.current.ink,
    )
}

const val NOTE_SCALE = 0.85f
const val SNIPPET_SCALE = 0.75f

object AppText {
    val title = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
    val reference = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val listItem = TextStyle(fontFamily = FontFamily.Serif, fontSize = 16.sp)
    val body = TextStyle(fontSize = 15.sp)
    val small = TextStyle(fontSize = 13.sp)
    val caption = TextStyle(fontSize = 12.sp)
    val sectionLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
}
