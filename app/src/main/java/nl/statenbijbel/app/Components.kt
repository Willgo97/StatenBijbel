package nl.statenbijbel.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DisplayBar() {
    val k = LocalReadingColors.current
    Surface(color = k.paper) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aa", fontSize = 13.sp, color = k.muted, modifier = Modifier.width(26.dp))
                Slider(
                    value = Prefs.textSize.toFloat(),
                    onValueChange = { Prefs.saveTextSize(it.toInt()) },
                    valueRange = 13f..34f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${Prefs.textSize}",
                    fontSize = 12.sp, color = k.muted,
                    modifier = Modifier.width(26.dp), textAlign = TextAlign.End,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Theme.entries.forEach { t ->
                    Chip(t.label, Prefs.theme == t) { Prefs.saveTheme(t) }
                }
                Chip(if (Prefs.serif) "Schreef" else "Schreefloos", false) {
                    Prefs.saveSerif(!Prefs.serif)
                }
                Chip("Kantt. ${if (Prefs.showNoteMarkers) "aan" else "uit"}",
                    Prefs.showNoteMarkers) { Prefs.saveNoteMarkers(!Prefs.showNoteMarkers) }
            }
            HorizontalDivider(color = k.divider)
        }
    }
}

@Composable
fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    val k = LocalReadingColors.current
    Surface(
        color = if (active) k.accent.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .border(
                1.dp,
                if (active) k.accent.copy(alpha = 0.5f) else k.divider,
                RoundedCornerShape(50),
            ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 13.sp,
            color = if (active) k.accent else k.muted,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
fun NotesBlock(st: AppState, b: Int, c: Int, v: Int) {
    val k = LocalReadingColors.current
    val notes = remember(b, c) { Bible.notes(b, c) }[v].orEmpty()
    if (notes.isEmpty()) return
    val colors = renderColors()
    var preview by remember(b, c, v) { mutableStateOf<Ref?>(null) }
    var previewNote by remember(b, c, v) { mutableIntStateOf(0) }
    val citationCount = remember(b, c, v) { Bible.citations(b, c, v).size }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = VERSE_GUTTER, top = 4.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (k.dark) Color(0x14FFFFFF) else Color(0x0F8A6431)),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
            notes.forEachIndexed { i, note ->
                val highlighted = st.noteN == note.n
                val text = remember(note.nid, k.dark) { noteText(note, colors) }
                var layout by remember(note.nid) { mutableStateOf<TextLayoutResult?>(null) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (highlighted) k.accent.copy(alpha = if (k.dark) 0.22f else 0.15f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                ) {
                    Text(
                        buildNoteHeader(note, k),
                        fontFamily = if (Prefs.serif) FontFamily.Serif else FontFamily.SansSerif,
                        fontSize = (Prefs.textSize - 3).sp,
                        lineHeight = ((Prefs.textSize - 3) * 1.35f).sp,
                        color = k.ink,
                        modifier = if (i == 0) Modifier.padding(end = 18.dp) else Modifier,
                    )
                    Text(
                        text = text,
                        style = TextStyle(
                            fontFamily = if (Prefs.serif) FontFamily.Serif
                            else FontFamily.SansSerif,
                            fontSize = (Prefs.textSize - 3).sp,
                            lineHeight = ((Prefs.textSize - 3) * Prefs.lineHeight / 100f).sp,
                            color = k.ink,
                        ),
                        modifier = Modifier.pointerInput(text) {
                            detectTapGestures { pos ->
                                val lr = layout ?: return@detectTapGestures
                                val off = lr.getOffsetForPosition(pos)
                                val ann = text.getStringAnnotations(TAG_REF, off, off)
                                    .firstOrNull()
                                if (ann != null) {
                                    val r = Ref.parse(ann.item)
                                    if (previewNote == note.n && preview?.let {
                                            it.b == r?.b && it.c == r.c && it.v == r.v
                                        } == true) {
                                        preview = null
                                    } else {
                                        preview = r
                                        previewNote = note.n
                                    }
                                }
                            }
                        },
                        onTextLayout = { layout = it },
                    )
                    if (previewNote == note.n) {
                        preview?.let { VersePreview(st, it) }
                    }
                }
            }
            if (citationCount > 0) {
                Row(
                    Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { st.citationsFor = Triple(b, c, v) }
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Link, null, tint = k.accent, modifier = Modifier.size(16.dp))
                    Text(
                        if (citationCount == 1) "  1 verwijzing hierheen"
                        else "  $citationCount verwijzingen hierheen",
                        fontSize = 13.sp, color = k.accent,
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { st.toggleNotes(b, c, v, st.noteN) }
                .padding(3.dp),
        ) {
            Icon(
                Icons.Default.Close, "Sluiten", tint = k.muted,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

private fun buildNoteHeader(note: Note, k: ReadingColors): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = k.accent, fontWeight = FontWeight.Bold)) {
            append("${note.n}  ")
        }
        if (note.cw.isNotBlank()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(note.cw) }
        }
    }

@Composable
fun VersePreview(st: AppState, r: Ref) {
    val k = LocalReadingColors.current
    val verses = remember(r.b, r.c, r.v, r.end) {
        if (Bible.bookOrNull(r.b) == null) emptyList()
        else Bible.verseRange(r.b, r.c, r.v, if (r.end > r.v) r.end else r.v)
    }
    Surface(
        color = if (k.dark) Color(0xFF20242A) else Color(0xFFF3EFE7),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Bible.ref(r.b, r.c, r.v, r.end),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = k.accent,
                    modifier = Modifier.weight(1f),
                )
                TextButton({ st.goTo(r.b, r.c, r.v) }) {
                    Text("Ga erheen", fontSize = 13.sp)
                }
            }
            if (verses.isEmpty()) {
                Text("Deze plaats is niet gevonden.", fontSize = 13.sp, color = k.muted)
            } else {
                verses.take(8).forEach { vs ->
                    Text(
                        "${vs.v}  ${vs.text}",
                        fontFamily = if (Prefs.serif) FontFamily.Serif
                        else FontFamily.SansSerif,
                        fontSize = (Prefs.textSize - 3).sp,
                        lineHeight = ((Prefs.textSize - 3) * 1.4f).sp,
                        color = k.ink,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitationsSheet(st: AppState, b: Int, c: Int, v: Int, onDismiss: () -> Unit) {
    val k = LocalReadingColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val citations = remember(b, c, v) { Bible.citations(b, c, v) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = k.paper,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .heightIn(max = 620.dp),
        ) {
            Text(
                "Verwijzingen naar ${Bible.ref(b, c, v)}",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = k.ink,
            )
            Text(
                "${citations.size} kanttekening(en) elders halen deze plaats aan",
                fontSize = 12.sp, color = k.muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(citations) { cit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                st.showNote(cit.b, cit.c, cit.v, cit.n)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Bible.ref(cit.b, cit.c, cit.v),
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = k.accent,
                            )
                            Text(
                                "  kantt. ${cit.n}" +
                                    (if (cit.cw.isNotBlank()) " · ${cit.cw}" else ""),
                                fontSize = 12.sp, color = k.muted, maxLines = 1,
                            )
                        }
                        Text(
                            cit.text,
                            fontSize = 13.sp,
                            color = k.ink,
                            maxLines = 3,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    HorizontalDivider(color = k.divider)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
