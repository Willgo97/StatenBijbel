package nl.statenbijbel.app

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

const val TAG_NOTE = "KT"
const val TAG_REF = "REF"

fun verseText(verse: Verse, palette: ReadingColors, showMarkers: Boolean): AnnotatedString =
    styledText(verse.text, verse.spans, palette, showMarkers)

fun noteText(note: Note, palette: ReadingColors): AnnotatedString =
    styledText(note.text, note.spans, palette)

fun styledText(
    text: String,
    spans: List<Span>,
    palette: ReadingColors,
    showMarkers: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    // On overlap the longest range wins.
    val ranges = ArrayList<Span>()
    var boundary = 0
    for (span in spans.filter { it.kind != 'n' && it.end > it.start }
        .sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))) {
        if (span.start >= boundary) { ranges.add(span); boundary = span.end }
    }
    val markers = if (showMarkers) spans.filter { it.kind == 'n' }.sortedBy { it.start } else emptyList()
    val markerStyle = SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.70.em)

    var previousMarker = -1
    fun appendMarker(marker: Span) {
        // Otherwise numbers 1 and 2 at the same spot read as "12".
        if (previousMarker == marker.start) {
            withStyle(markerStyle.copy(color = palette.muted)) { append(" · ") }
        }
        previousMarker = marker.start
        withAnnotation(TAG_NOTE, marker.value) {
            withStyle(markerStyle.copy(color = palette.marker, fontWeight = FontWeight.Medium)) {
                append(marker.value)
            }
        }
    }

    val length = text.length
    var position = 0
    var rangeIndex = 0
    var markerIndex = 0
    while (position < length) {
        while (markerIndex < markers.size && markers[markerIndex].start <= position) {
            appendMarker(markers[markerIndex++])
        }

        if (rangeIndex < ranges.size && ranges[rangeIndex].start <= position) {
            val range = ranges[rangeIndex++]
            val piece = text.substring(range.start.coerceIn(0, length), range.end.coerceIn(0, length))
            when (range.kind) {
                'i' -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(piece) }
                'd' -> appendSmallCaps(piece)
                'a' -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = palette.accent)) {
                    append(piece)
                }
                'r' -> withAnnotation(TAG_REF, range.value) {
                    withStyle(SpanStyle(color = palette.accent, fontWeight = FontWeight.Medium)) {
                        append(piece)
                    }
                }
                else -> append(piece)
            }
            position = maxOf(position + 1, range.end)
            continue
        }

        val nextRange = if (rangeIndex < ranges.size) ranges[rangeIndex].start else length
        val nextMarker = if (markerIndex < markers.size) markers[markerIndex].start else length
        val stop = maxOf(minOf(nextRange, nextMarker, length), position + 1)
        append(text.substring(position, stop))
        position = stop
    }
    while (markerIndex < markers.size) appendMarker(markers[markerIndex++])
}

private fun AnnotatedString.Builder.appendSmallCaps(piece: String) {
    if (piece.isEmpty()) return
    append(piece.first().uppercaseChar())
    if (piece.length > 1) {
        withStyle(SpanStyle(fontSize = 0.82.em, letterSpacing = 0.04.em)) {
            append(piece.substring(1).uppercase())
        }
    }
}
