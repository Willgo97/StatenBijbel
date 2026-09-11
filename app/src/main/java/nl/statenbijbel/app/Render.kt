package nl.statenbijbel.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

const val TAG_KT = "KT"      // kanttekeningmarkering
const val TAG_REF = "REF"    // verwijzing in een kanttekening

class Opmaak(
    val tekst: Color,
    val gedempt: Color,
    val accent: Color,
    val markerKleur: Color,
)

/**
 * Zet tekst + spans om in een AnnotatedString.
 *
 * De Godsnaam wordt als klein kapitaal gezet (HEERE), toegevoegde woorden
 * cursief, en kanttekeningen krijgen een aanklikbaar superscriptnummer.
 */
private fun bouw(
    tekst: String,
    spans: List<Span>,
    o: Opmaak,
    toonMarkers: Boolean,
): AnnotatedString = buildAnnotatedString2 {
    // Opmaakbereiken: overlappingen laten we vallen, de langste wint.
    val bereiken = ArrayList<Span>()
    var grens = 0
    for (s in spans.filter { it.kind != 'n' && it.end > it.start }
        .sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))) {
        if (s.start >= grens) { bereiken.add(s); grens = s.end }
    }
    // Invoegpunten: de nummers van de kanttekeningen.
    val punten = ArrayList<Span>()
    if (toonMarkers) spans.filterTo(punten) { it.kind == 'n' }
    punten.sortBy { it.start }

    val n = tekst.length
    var pos = 0
    var ri = 0
    var pi = 0

    var vorigePunt = -1
    fun emitPunt(p: Span) {
        // Twee kanttekeningnummers pal achter elkaar ("12") uit elkaar zetten.
        if (vorigePunt == p.start) {
            val scheiding = "\u2009·\u2009"
            withStyle(
                SpanStyle(
                    color = o.gedempt,
                    baselineShift = BaselineShift.Superscript,
                    fontSize = 0.70.em,
                )
            ) { append(scheiding) }
        }
        vorigePunt = p.start
        when (p.kind) {
            'n' -> {
                pushAnn(TAG_KT, p.value)
                withStyle(
                    SpanStyle(
                        color = o.markerKleur,
                        baselineShift = BaselineShift.Superscript,
                        fontSize = 0.70.em,
                        fontWeight = FontWeight.Medium,
                    )
                ) { append(p.value) }
                popAnn()
            }
        }
    }

    while (pos < n) {
        while (pi < punten.size && punten[pi].start <= pos) emitPunt(punten[pi++])

        if (ri < bereiken.size && bereiken[ri].start <= pos) {
            val s = bereiken[ri++]
            val stuk = tekst.substring(s.start.coerceIn(0, n), s.end.coerceIn(0, n))
            when (s.kind) {
                'i' -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(stuk) }
                'd' -> kleinKapitaal(stuk)
                'a' -> withStyle(
                    SpanStyle(fontWeight = FontWeight.SemiBold, color = o.accent)
                ) { append(stuk) }
                'r' -> {
                    pushAnn(TAG_REF, s.value)
                    withStyle(
                        SpanStyle(color = o.accent, fontWeight = FontWeight.Medium)
                    ) { append(stuk) }
                    popAnn()
                }
                else -> append(stuk)
            }
            pos = maxOf(pos + 1, s.end)
            continue
        }

        val volgendBereik = if (ri < bereiken.size) bereiken[ri].start else n
        val volgendPunt = if (pi < punten.size) punten[pi].start else n
        val stop = minOf(volgendBereik, volgendPunt, n)
        if (stop > pos) {
            append(tekst.substring(pos, stop))
            pos = stop
        } else {
            append(tekst.substring(pos, pos + 1))
            pos++
        }
    }
    while (pi < punten.size) emitPunt(punten[pi++])
}

/** HEERE: hoofdletter plus klein kapitaal, zoals in de gedrukte Statenvertaling. */
private fun Bouwer.kleinKapitaal(stuk: String) {
    if (stuk.isEmpty()) return
    append(stuk.first().uppercaseChar().toString())
    if (stuk.length > 1) {
        withStyle(SpanStyle(fontSize = 0.82.em, letterSpacing = 0.04.em)) {
            append(stuk.substring(1).uppercase())
        }
    }
}

fun versTekst(v: Verse, o: Opmaak, toonMarkers: Boolean): AnnotatedString =
    bouw(v.text, v.spans, o, toonMarkers)

fun nootTekst(n: Note, o: Opmaak): AnnotatedString = bouw(n.text, n.spans, o, false)

// ---------------------------------------------------------------------------
// Een dunne schil om AnnotatedString.Builder zodat annotaties netjes
// gestapeld kunnen worden.
class Bouwer {
    val b = AnnotatedString.Builder()
    fun append(s: String) = b.append(s)
    fun pushAnn(tag: String, value: String) {
        @Suppress("DEPRECATION")
        b.pushStringAnnotation(tag, value)
    }
    fun popAnn() = b.pop()
    inline fun withStyle(style: SpanStyle, block: Bouwer.() -> Unit) {
        b.pushStyle(style)
        block()
        b.pop()
    }
    fun build(): AnnotatedString = b.toAnnotatedString()
}

inline fun buildAnnotatedString2(block: Bouwer.() -> Unit): AnnotatedString =
    Bouwer().apply(block).build()

fun sp(v: Int): TextUnit = v.sp
