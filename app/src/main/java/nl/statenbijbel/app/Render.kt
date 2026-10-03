package nl.statenbijbel.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em

const val TAG_NOTE = "KT"
const val TAG_REF = "REF"

class RenderColors(
    val text: Color,
    val muted: Color,
    val accent: Color,
    val markerColor: Color,
)

private fun render(
    text: String,
    spans: List<Span>,
    o: RenderColors,
    showMarkers: Boolean,
): AnnotatedString = buildAnnotatedString2 {
    // On overlap the longest range wins.
    val ranges = ArrayList<Span>()
    var boundary = 0
    for (s in spans.filter { it.kind != 'n' && it.end > it.start }
        .sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))) {
        if (s.start >= boundary) { ranges.add(s); boundary = s.end }
    }
    val markers = ArrayList<Span>()
    if (showMarkers) spans.filterTo(markers) { it.kind == 'n' }
    markers.sortBy { it.start }

    val n = text.length
    var pos = 0
    var ri = 0
    var mi = 0

    var prevMarker = -1
    fun emitMarker(p: Span) {
        // Otherwise numbers 1 and 2 at the same spot read as "12".
        if (prevMarker == p.start) {
            withStyle(
                SpanStyle(
                    color = o.muted,
                    baselineShift = BaselineShift.Superscript,
                    fontSize = 0.70.em,
                )
            ) { append("\u2009·\u2009") }
        }
        prevMarker = p.start
        pushAnn(TAG_NOTE, p.value)
        withStyle(
            SpanStyle(
                color = o.markerColor,
                baselineShift = BaselineShift.Superscript,
                fontSize = 0.70.em,
                fontWeight = FontWeight.Medium,
            )
        ) { append(p.value) }
        popAnn()
    }

    while (pos < n) {
        while (mi < markers.size && markers[mi].start <= pos) emitMarker(markers[mi++])

        if (ri < ranges.size && ranges[ri].start <= pos) {
            val s = ranges[ri++]
            val piece = text.substring(s.start.coerceIn(0, n), s.end.coerceIn(0, n))
            when (s.kind) {
                'i' -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(piece) }
                'd' -> smallCaps(piece)
                'a' -> withStyle(
                    SpanStyle(fontWeight = FontWeight.SemiBold, color = o.accent)
                ) { append(piece) }
                'r' -> {
                    pushAnn(TAG_REF, s.value)
                    withStyle(
                        SpanStyle(color = o.accent, fontWeight = FontWeight.Medium)
                    ) { append(piece) }
                    popAnn()
                }
                else -> append(piece)
            }
            pos = maxOf(pos + 1, s.end)
            continue
        }

        val nextRange = if (ri < ranges.size) ranges[ri].start else n
        val nextMarker = if (mi < markers.size) markers[mi].start else n
        val stop = minOf(nextRange, nextMarker, n)
        if (stop > pos) {
            append(text.substring(pos, stop))
            pos = stop
        } else {
            append(text.substring(pos, pos + 1))
            pos++
        }
    }
    while (mi < markers.size) emitMarker(markers[mi++])
}

private fun SpanBuilder.smallCaps(piece: String) {
    if (piece.isEmpty()) return
    append(piece.first().uppercaseChar().toString())
    if (piece.length > 1) {
        withStyle(SpanStyle(fontSize = 0.82.em, letterSpacing = 0.04.em)) {
            append(piece.substring(1).uppercase())
        }
    }
}

fun verseText(v: Verse, o: RenderColors, showMarkers: Boolean): AnnotatedString =
    render(v.text, v.spans, o, showMarkers)

fun noteText(n: Note, o: RenderColors): AnnotatedString = render(n.text, n.spans, o, false)

class SpanBuilder {
    val b = AnnotatedString.Builder()
    fun append(s: String) = b.append(s)
    fun pushAnn(tag: String, value: String) {
        @Suppress("DEPRECATION")
        b.pushStringAnnotation(tag, value)
    }
    fun popAnn() = b.pop()
    inline fun withStyle(style: SpanStyle, block: SpanBuilder.() -> Unit) {
        b.pushStyle(style)
        block()
        b.pop()
    }
    fun build(): AnnotatedString = b.toAnnotatedString()
}

inline fun buildAnnotatedString2(block: SpanBuilder.() -> Unit): AnnotatedString =
    SpanBuilder().apply(block).build()
