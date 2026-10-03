package nl.statenbijbel.app

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class ReadingColors(
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val verseNumber: Color,
    val marker: Color,
    val selection: Color,
    val divider: Color,
    val dark: Boolean,
)

val LocalReadingColors = staticCompositionLocalOf {
    ReadingColors(
        Color(0xFFFFFDFB), Color(0xFF1B1917), Color(0xFF6B655D), Color(0xFF8C6D3F),
        Color(0xFF9C9187), Color(0xFFB08636), Color(0x22C9A227), Color(0x14000000), false,
    )
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

private val lightColors = ReadingColors(
    paper = Color(0xFFFFFDFB),
    ink = Color(0xFF1B1917),
    muted = Color(0xFF6E6862),
    accent = Color(0xFF8A6431),
    verseNumber = Color(0xFFA79B8A),
    marker = Color(0xFFB07C2E),
    selection = Color(0x1F8A6431),
    divider = Color(0x14000000),
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
    dark = true,
)

@Composable
fun StatenBijbelTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val base = when (Prefs.theme) {
        Theme.LIGHT -> lightColors
        Theme.SEPIA -> sepiaColors
        Theme.DARK -> darkColors
        Theme.NIGHT -> nightColors
        Theme.SYSTEM -> if (systemDark) darkColors else lightColors
    }
    val chosen = Prefs.accent.color(base.dark)
    val k = base.copy(
        accent = chosen,
        marker = chosen,
        selection = chosen.copy(alpha = if (base.dark) 0.26f else 0.15f),
    )
    val scheme = if (k.dark) darkColorScheme(
        primary = k.accent,
        onPrimary = Color(0xFF20180A),
        surface = k.paper,
        onSurface = k.ink,
        background = k.paper,
        onBackground = k.ink,
        surfaceVariant = Color(0xFF23262B),
        onSurfaceVariant = k.muted,
        outline = k.muted,
        secondaryContainer = Color(0xFF2A2E34),
        onSecondaryContainer = k.ink,
    ) else lightColorScheme(
        primary = k.accent,
        onPrimary = Color.White,
        surface = k.paper,
        onSurface = k.ink,
        background = k.paper,
        onBackground = k.ink,
        surfaceVariant = if (Prefs.theme == Theme.SEPIA) Color(0xFFEDE0C8) else Color(0xFFF1EDE6),
        onSurfaceVariant = k.muted,
        outline = k.muted,
        secondaryContainer = if (Prefs.theme == Theme.SEPIA) Color(0xFFE8D8BA) else Color(0xFFEDE7DC),
        onSecondaryContainer = k.ink,
    )
    // The status bar follows the chosen theme, not the system one.
    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(k.dark) {
            val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !k.dark
                isAppearanceLightNavigationBars = !k.dark
            }
        }
    }

    CompositionLocalProvider(LocalReadingColors provides k) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

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

@Composable
fun renderColors(): RenderColors {
    val k = LocalReadingColors.current
    return RenderColors(k.ink, k.muted, k.accent, k.marker)
}
