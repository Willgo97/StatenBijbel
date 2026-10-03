package nl.statenbijbel.app.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.statenbijbel.app.AppState
import nl.statenbijbel.app.AppText
import nl.statenbijbel.app.Bible
import nl.statenbijbel.app.Hit
import nl.statenbijbel.app.LocalReadingColors
import nl.statenbijbel.app.Screen
import nl.statenbijbel.app.WORD_PATTERN
import nl.statenbijbel.app.normalize
import nl.statenbijbel.app.normalizeChar
import nl.statenbijbel.app.styledText

@Composable
fun SearchScreen(state: AppState) {
    val palette = LocalReadingColors.current
    var query by remember { mutableStateOf("") }
    var inNotes by remember { mutableStateOf(false) }
    var testament by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchedFor by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query, inNotes, testament) {
        if (query.trim().length < 2) { results = emptyList(); searchedFor = ""; return@LaunchedEffect }
        searching = true
        delay(180)
        results = withContext(Dispatchers.Default) {
            Bible.search(query, inNotes, testament = testament)
        }
        searchedFor = query
        searching = false
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Zoeken", { state.screen = Screen.READER })
        SearchField(
            query, { query = it },
            if (inNotes) "Zoek in de kanttekeningen" else "Zoek in de bijbeltekst",
            Modifier.focusRequester(focus),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip("Bijbeltekst", !inNotes) { inNotes = false }
            Chip("Kanttekeningen", inNotes) { inNotes = true }
            Spacer(Modifier.width(8.dp))
            Chip("Alles", testament == "") { testament = "" }
            Chip("OT", testament == "OT") { testament = "OT" }
            Chip("NT", testament == "NT") { testament = "NT" }
        }
        if (searchedFor.isNotEmpty()) {
            Text(
                if (results.isEmpty()) "Niets gevonden"
                else "${results.size}${if (results.size >= 400) "+" else ""} resultaten",
                style = AppText.caption,
                color = palette.muted,
                modifier = Modifier.padding(start = 18.dp, top = 10.dp),
            )
        }
        if (searching && results.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = palette.accent)
            }
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(results) { hit ->
                val text = remember(hit, searchedFor, palette) {
                    highlightMatches(styledText(hit.text, hit.spans, palette), searchedFor, palette.accent)
                }
                ReferenceRow(
                    Bible.ref(hit.book, hit.chapter, hit.verse),
                    text,
                    onClick = {
                        if (hit.noteNumber > 0)
                            state.showNote(hit.book, hit.chapter, hit.verse, hit.noteNumber)
                        else state.goTo(hit.book, hit.chapter, hit.verse)
                    },
                    detail = if (hit.noteNumber > 0) noteLabel(hit.noteNumber, hit.catchword) else "",
                    maxLines = 4,
                )
            }
        }
    }
}

// Marks whole-word matches ("en" must not light up inside "geworden"); the last word of a query
// still being typed counts as a prefix. Long texts are cut to a window around the first match.
fun highlightMatches(text: AnnotatedString, query: String, accent: Color): AnnotatedString {
    val terms = WORD_PATTERN.findAll(normalize(query)).map { it.value }.filter { it.length > 1 }.toList()
    if (terms.isEmpty()) return text
    val openEnd = query.isNotEmpty() && !query.last().isWhitespace()
    val exact = if (openEnd) terms.dropLast(1).toSet() else terms.toSet()
    val prefix = if (openEnd) terms.last() else null

    val plain = text.text
    val normalized = CharArray(plain.length) { normalizeChar(plain[it]) }.concatToString()
    val highlighted = AnnotatedString.Builder(text)
    var firstMatch = -1
    for (match in WORD_PATTERN.findAll(normalized)) {
        if (match.value in exact || (prefix != null && match.value.startsWith(prefix))) {
            if (firstMatch < 0) firstMatch = match.range.first
            highlighted.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, color = accent),
                match.range.first, match.range.last + 1,
            )
        }
    }
    val full = highlighted.toAnnotatedString()

    var start = 0
    var end = plain.length
    if (firstMatch > 130) {
        start = (firstMatch - 60).coerceAtLeast(0)
        while (start > 0 && plain[start] != ' ') start--
    }
    if (end - start > 320) {
        end = (start + 320).coerceAtMost(plain.length)
        while (end < plain.length && plain[end] != ' ') end++
    }
    if (start == 0 && end == plain.length) return full
    return buildAnnotatedString {
        if (start > 0) append("… ")
        append(full.subSequence(start, end))
        if (end < plain.length) append(" …")
    }
}
