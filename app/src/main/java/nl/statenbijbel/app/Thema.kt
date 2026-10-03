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

data class Leeskleuren(
    val papier: Color,
    val inkt: Color,
    val gedempt: Color,
    val accent: Color,
    val versnummer: Color,
    val marker: Color,
    val selectie: Color,
    val scheiding: Color,
    val donker: Boolean,
)

val LocalLeeskleuren = staticCompositionLocalOf {
    Leeskleuren(
        Color(0xFFFFFDFB), Color(0xFF1B1917), Color(0xFF6B655D), Color(0xFF8C6D3F),
        Color(0xFF9C9187), Color(0xFFB08636), Color(0x22C9A227), Color(0x14000000), false,
    )
}

enum class Accent(val label: String, val licht: Color, val donker: Color) {
    GOUD("Goud", Color(0xFF8A6431), Color(0xFFD9B566)),
    ROZEROOD("Rozerood", Color(0xFFB0355C), Color(0xFFEE8CA9)),
    BORDEAUX("Bordeaux", Color(0xFF9A3430), Color(0xFFE58C7E)),
    KOPER("Koper", Color(0xFF9E5522), Color(0xFFE8A46E)),
    BLAUW("Blauw", Color(0xFF2C5B8C), Color(0xFF88B8E6)),
    GROEN("Groen", Color(0xFF36684A), Color(0xFF89C8A2)),
    PAARS("Paars", Color(0xFF67488B), Color(0xFFBB9EDE)),
    INKT("Inkt", Color(0xFF4B463F), Color(0xFFB7B2AA)),
    ;

    fun kleur(donkerThema: Boolean): Color = if (donkerThema) donker else licht
}

private val licht = Leeskleuren(
    papier = Color(0xFFFFFDFB),
    inkt = Color(0xFF1B1917),
    gedempt = Color(0xFF6E6862),
    accent = Color(0xFF8A6431),
    versnummer = Color(0xFFA79B8A),
    marker = Color(0xFFB07C2E),
    selectie = Color(0x1F8A6431),
    scheiding = Color(0x14000000),
    donker = false,
)

private val sepia = Leeskleuren(
    papier = Color(0xFFF7EEDD),
    inkt = Color(0xFF33291D),
    gedempt = Color(0xFF6F6252),
    accent = Color(0xFF8A5A22),
    versnummer = Color(0xFFAE9E84),
    marker = Color(0xFF9C6A22),
    selectie = Color(0x22A0701F),
    scheiding = Color(0x18503C20),
    donker = false,
)

private val donker = Leeskleuren(
    papier = Color(0xFF15171A),
    inkt = Color(0xFFDEDAD3),
    gedempt = Color(0xFF938D85),
    accent = Color(0xFFD9B566),
    versnummer = Color(0xFF6E6862),
    marker = Color(0xFFD2A950),
    selectie = Color(0x33C9A227),
    scheiding = Color(0x1AFFFFFF),
    donker = true,
)

private val nacht = Leeskleuren(
    papier = Color(0xFF000000),
    inkt = Color(0xFFB4AFA8),
    gedempt = Color(0xFF7A756E),
    accent = Color(0xFFBE9A43),
    versnummer = Color(0xFF5A554F),
    marker = Color(0xFFB08F3E),
    selectie = Color(0x33C9A227),
    scheiding = Color(0x14FFFFFF),
    donker = true,
)

@Composable
fun StatenBijbelTheme(content: @Composable () -> Unit) {
    val systeemDonker = isSystemInDarkTheme()
    val basis = when (Prefs.thema) {
        Thema.LICHT -> licht
        Thema.SEPIA -> sepia
        Thema.DONKER -> donker
        Thema.NACHT -> nacht
        Thema.SYSTEEM -> if (systeemDonker) donker else licht
    }
    val gekozen = Prefs.accent.kleur(basis.donker)
    val k = basis.copy(
        accent = gekozen,
        marker = gekozen,
        selectie = gekozen.copy(alpha = if (basis.donker) 0.26f else 0.15f),
    )
    val schema = if (k.donker) darkColorScheme(
        primary = k.accent,
        onPrimary = Color(0xFF20180A),
        surface = k.papier,
        onSurface = k.inkt,
        background = k.papier,
        onBackground = k.inkt,
        surfaceVariant = Color(0xFF23262B),
        onSurfaceVariant = k.gedempt,
        outline = k.gedempt,
        secondaryContainer = Color(0xFF2A2E34),
        onSecondaryContainer = k.inkt,
    ) else lightColorScheme(
        primary = k.accent,
        onPrimary = Color.White,
        surface = k.papier,
        onSurface = k.inkt,
        background = k.papier,
        onBackground = k.inkt,
        surfaceVariant = if (Prefs.thema == Thema.SEPIA) Color(0xFFEDE0C8) else Color(0xFFF1EDE6),
        onSurfaceVariant = k.gedempt,
        outline = k.gedempt,
        secondaryContainer = if (Prefs.thema == Thema.SEPIA) Color(0xFFE8D8BA) else Color(0xFFEDE7DC),
        onSecondaryContainer = k.inkt,
    )
    // Statusbalk volgt het gekozen thema, niet dat van het systeem.
    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(k.donker) {
            val venster = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(venster, view).apply {
                isAppearanceLightStatusBars = !k.donker
                isAppearanceLightNavigationBars = !k.donker
            }
        }
    }

    CompositionLocalProvider(LocalLeeskleuren provides k) {
        MaterialTheme(colorScheme = schema, typography = typografie, content = content)
    }
}

private val typografie = Typography(
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
fun leesOpmaak(): Opmaak {
    val k = LocalLeeskleuren.current
    return Opmaak(k.inkt, k.gedempt, k.accent, k.marker)
}
