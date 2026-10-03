package nl.statenbijbel.app.screens.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import nl.statenbijbel.app.AppState
import nl.statenbijbel.app.AppText
import nl.statenbijbel.app.Bible
import nl.statenbijbel.app.LocalReadingColors
import nl.statenbijbel.app.NOTE_SCALE
import nl.statenbijbel.app.Ref
import nl.statenbijbel.app.readingStyle
import nl.statenbijbel.app.screens.ReferenceRow
import nl.statenbijbel.app.screens.noteLabel
import nl.statenbijbel.app.styledText
import nl.statenbijbel.app.verseText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitationsSheet(
    state: AppState, book: Int, chapter: Int, verse: Int, onDismiss: () -> Unit,
) {
    val palette = LocalReadingColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val citations = remember(book, chapter, verse) { Bible.citations(book, chapter, verse) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.paper,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .heightIn(max = 620.dp),
        ) {
            Text(
                "Verwijzingen naar ${Bible.ref(book, chapter, verse)}",
                style = AppText.title,
                color = palette.ink,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(citations) { citation ->
                    val text = remember(citation, palette) {
                        styledText(citation.text, citation.spans, palette)
                    }
                    ReferenceRow(
                        Bible.ref(citation.book, citation.chapter, citation.verse),
                        text,
                        onClick = {
                            state.showNote(
                                citation.book, citation.chapter, citation.verse, citation.noteNumber,
                            )
                            onDismiss()
                        },
                        detail = noteLabel(citation.noteNumber, citation.catchword),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun VersePreview(state: AppState, ref: Ref) {
    val palette = LocalReadingColors.current
    val verses = remember(ref.book, ref.chapter, ref.verse, ref.endVerse) {
        if (Bible.bookOrNull(ref.book) == null) emptyList()
        else Bible.verseRange(ref.book, ref.chapter, ref.verse, maxOf(ref.verse, ref.endVerse))
    }
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Bible.ref(ref.book, ref.chapter, ref.verse, ref.endVerse),
                    style = AppText.reference,
                    color = palette.accent,
                    modifier = Modifier.weight(1f),
                )
                TextButton({ state.goTo(ref.book, ref.chapter, ref.verse) }) {
                    Text("Ga erheen", style = AppText.small)
                }
            }
            if (verses.isEmpty()) {
                Text("Deze plaats is niet gevonden.", style = AppText.small, color = palette.muted)
            }
            verses.take(8).forEach { previewVerse ->
                val text = remember(previewVerse.id, palette) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = palette.verseNumber, fontSize = 0.75.em)) {
                            append("${previewVerse.number}  ")
                        }
                        append(verseText(previewVerse, palette, showMarkers = false))
                    }
                }
                Text(text, style = readingStyle(NOTE_SCALE), modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
