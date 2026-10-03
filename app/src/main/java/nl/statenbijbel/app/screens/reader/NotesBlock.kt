package nl.statenbijbel.app.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import nl.statenbijbel.app.AppState
import nl.statenbijbel.app.AppText
import nl.statenbijbel.app.Bible
import nl.statenbijbel.app.LocalReadingColors
import nl.statenbijbel.app.NOTE_SCALE
import nl.statenbijbel.app.Note
import nl.statenbijbel.app.ReadingColors
import nl.statenbijbel.app.Ref
import nl.statenbijbel.app.TAG_REF
import nl.statenbijbel.app.noteText
import nl.statenbijbel.app.readingStyle

@Composable
fun NotesBlock(state: AppState, book: Int, chapter: Int, verse: Int) {
    val palette = LocalReadingColors.current
    val notes = remember(book, chapter) { Bible.notes(book, chapter) }[verse].orEmpty()
    if (notes.isEmpty()) return
    val noteStyle = readingStyle(NOTE_SCALE)
    var preview by remember(book, chapter, verse) { mutableStateOf<Ref?>(null) }
    var previewNote by remember(book, chapter, verse) { mutableIntStateOf(0) }
    val citationCount = remember(book, chapter, verse) {
        Bible.citations(book, chapter, verse).size
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = VERSE_GUTTER, top = 4.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.notesBackground),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
            notes.forEachIndexed { i, note ->
                val highlighted = state.highlightedNote == note.number
                val text = remember(note.id, palette) { noteText(note, palette) }
                var layout by remember(note.id) { mutableStateOf<TextLayoutResult?>(null) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (highlighted) palette.accent.copy(alpha = if (palette.dark) 0.22f else 0.15f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                ) {
                    Text(
                        buildNoteHeader(note, palette),
                        style = noteStyle,
                        modifier = if (i == 0) Modifier.padding(end = 18.dp) else Modifier,
                    )
                    Text(
                        text = text,
                        style = noteStyle,
                        modifier = Modifier.pointerInput(text) {
                            detectTapGestures { position ->
                                val currentLayout = layout ?: return@detectTapGestures
                                val offset = currentLayout.getOffsetForPosition(position)
                                val annotation = text.getStringAnnotations(TAG_REF, offset, offset)
                                    .firstOrNull()
                                if (annotation != null) {
                                    val ref = Ref.parse(annotation.item)
                                    if (previewNote == note.number && preview?.let {
                                            it.book == ref?.book && it.chapter == ref.chapter &&
                                                it.verse == ref.verse
                                        } == true) {
                                        preview = null
                                    } else {
                                        preview = ref
                                        previewNote = note.number
                                    }
                                }
                            }
                        },
                        onTextLayout = { layout = it },
                    )
                    if (previewNote == note.number) {
                        preview?.let { VersePreview(state, it) }
                    }
                }
            }
            if (citationCount > 0) {
                Row(
                    Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { state.citationsFor = Triple(book, chapter, verse) }
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Link, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                    Text(
                        if (citationCount == 1) "  1 verwijzing hierheen"
                        else "  $citationCount verwijzingen hierheen",
                        style = AppText.small, color = palette.accent,
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { state.toggleNotes(book, chapter, verse, state.highlightedNote) }
                .padding(3.dp),
        ) {
            Icon(
                Icons.Default.Close, "Sluiten", tint = palette.muted,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

private fun buildNoteHeader(note: Note, palette: ReadingColors): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = palette.accent, fontWeight = FontWeight.Bold)) {
            append("${note.number}  ")
        }
        if (note.catchword.isNotBlank()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(note.catchword) }
        }
    }
