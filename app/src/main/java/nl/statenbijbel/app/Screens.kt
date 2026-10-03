package nl.statenbijbel.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, extra: @Composable () -> Unit = {}) {
    val k = LocalReadingColors.current
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onBack) {
                Icon(Icons.Default.ArrowBack, "Terug", tint = k.muted)
            }
            Text(
                title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                color = k.ink,
                modifier = Modifier.weight(1f),
            )
            extra()
        }
        HorizontalDivider(color = k.divider)
    }
}

@Composable
fun BooksScreen(st: AppState) {
    val k = LocalReadingColors.current
    var query by remember { mutableStateOf("") }

    if (st.pickedBook != 0) {
        ChapterPicker(st, st.pickedBook)
        return
    }

    val match = remember(query) { if (query.isBlank()) null else Bible.parseReference(query) }
    val books = remember(query) {
        val part = Bible.bookPart(query)
        if (part.isEmpty()) Bible.books
        else Bible.bookCandidates(part).ifEmpty {
            Bible.books.filter { it.searchText.contains(normalize(part)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Boeken", { st.screen = Screen.READER })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Boek zoeken, of \"joh 3:16\"", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = k.muted) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        if (match != null && Bible.bookPart(query).length >= 2) {
            val (b, c, v) = match
            Surface(
                color = k.accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { st.goTo(b, c, v) },
            ) {
                Text(
                    "Ga naar ${Bible.ref(b, c, v)}",
                    modifier = Modifier.padding(14.dp),
                    color = k.accent,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            listOf(
                "OT" to "Oude Testament",
                "NT" to "Nieuwe Testament",
                "EX" to "Kerkboek",
            ).forEach { (code, label) ->
                val part = books.filter { it.testament == code }
                if (part.isNotEmpty()) {
                    item(key = "kop$code") {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = k.muted,
                            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    items(part, key = { it.b }) { book ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { st.pickedBook = book.b }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                book.name,
                                fontFamily = FontFamily.Serif,
                                fontSize = 16.sp,
                                color = k.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (book.isBible) "${book.chapters} hfdst."
                                else "${book.chapters} ${if (book.chapters == 1) "deel" else "delen"}",
                                fontSize = 12.sp, color = k.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterPicker(st: AppState, b: Int) {
    val k = LocalReadingColors.current
    val book = Bible.book(b)
    val titles = remember(b) { Bible.chapterTitles(b) }

    if (titles.isNotEmpty() && book.chapters <= 20) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(book.name, { st.pickedBook = 0 })
            LazyColumn(
                Modifier
                    .weight(1f)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items((1..book.chapters).toList()) { c ->
                    val t = titles[c].orEmpty().split("\n")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { st.pickedBook = 0; st.goTo(b, c) }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                    ) {
                        Text(
                            t.firstOrNull().orEmpty().ifBlank { "$c" },
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            color = k.ink,
                        )
                        if (t.size > 1 && t[1].isNotBlank()) {
                            Text(
                                t[1], fontSize = 12.sp, color = k.muted,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = k.divider)
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(book.name, { st.pickedBook = 0 })
        LazyVerticalGrid(
            columns = GridCells.Adaptive(56.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((1..book.chapters).toList()) { c ->
                val current = st.book == b && st.chapter == c
                Surface(
                    color = if (current) k.accent.copy(alpha = 0.18f)
                    else if (k.dark) Color(0xFF20242A) else Color(0xFFF2EEE7),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { st.pickedBook = 0; st.goTo(b, c) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "$c",
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            color = if (current) k.accent else k.ink,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(st: AppState) {
    val k = LocalReadingColors.current
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
        ScreenHeader("Zoeken", { st.screen = Screen.READER })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(
                    if (inNotes) "Zoek in de kanttekeningen" else "Zoek in de bijbeltekst",
                    fontSize = 14.sp,
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = k.muted) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton({ query = "" }) {
                        Icon(Icons.Default.Close, "Wissen", tint = k.muted)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .focusRequester(focus),
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
                fontSize = 12.sp,
                color = k.muted,
                modifier = Modifier.padding(start = 18.dp, top = 10.dp),
            )
        }
        if (searching && results.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = k.accent)
            }
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(results) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (hit.noteNo > 0)
                                st.showNote(hit.b, hit.c, hit.v, hit.noteNo)
                            else st.goTo(hit.b, hit.c, hit.v)
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Bible.ref(hit.b, hit.c, hit.v),
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = k.accent,
                        )
                        if (hit.noteNo > 0) {
                            Text(
                                "  kantt. ${hit.noteNo}" +
                                    (if (hit.catchWord.isNotBlank()) " · ${hit.catchWord}" else ""),
                                fontSize = 12.sp, color = k.muted, maxLines = 1,
                            )
                        }
                    }
                    Text(
                        highlightMatches(hit.text, searchedFor, k.accent),
                        fontFamily = if (Prefs.serif) FontFamily.Serif else FontFamily.SansSerif,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = k.ink,
                        maxLines = 4,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                HorizontalDivider(color = k.divider)
            }
        }
    }
}

fun highlightMatches(text: String, query: String, accent: Color): AnnotatedString {
    val terms = Regex("[a-z0-9]+").findAll(normalize(query)).map { it.value }
        .filter { it.length > 1 }.toList()
    if (terms.isEmpty()) return AnnotatedString(text)
    val openEnd = query.isNotEmpty() && !query.last().isWhitespace()
    val exact = if (openEnd) terms.dropLast(1).toSet() else terms.toSet()
    val prefix = if (openEnd) terms.last() else null

    // Normalize per character so positions stay aligned with text.
    val normalized = CharArray(text.length) { normalizeChar(text[it]) }.concatToString()
    val flags = BooleanArray(text.length)
    var first = -1
    // Whole words: "en" must not light up inside "geworden".
    for (m in Regex("[a-z0-9]+").findAll(normalized)) {
        val w = m.value
        if (w in exact || (prefix != null && w.startsWith(prefix))) {
            if (first < 0) first = m.range.first
            for (j in m.range) flags[j] = true
        }
    }

    var start = 0
    var end = text.length
    if (first > 130) {
        start = (first - 60).coerceAtLeast(0)
        while (start > 0 && text[start] != ' ') start--
    }
    if (end - start > 320) {
        end = (start + 320).coerceAtMost(text.length)
        while (end < text.length && text[end] != ' ') end++
    }
    return buildAnnotatedString {
        if (start > 0) append("… ")
        var i = start
        while (i < end) {
            val marked = flags[i]
            var j = i
            while (j < end && flags[j] == marked) j++
            if (marked) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) {
                    append(text.substring(i, j))
                }
            } else append(text.substring(i, j))
            i = j
        }
        if (end < text.length) append(" …")
    }
}

private fun normalizeChar(c: Char): Char {
    if (c.code < 128) return c.lowercaseChar()
    val decomposed = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD)
    val base = decomposed.firstOrNull {
        Character.getType(it) != Character.NON_SPACING_MARK.toInt()
    } ?: c
    return base.lowercaseChar()
}

@Composable
fun BookmarksScreen(st: AppState) {
    val k = LocalReadingColors.current
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Bewaard", { st.screen = Screen.READER })
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip("Bladwijzers", tab == 0) { tab = 0 }
            Chip("Geschiedenis", tab == 1) { tab = 1 }
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            when (tab) {
                0 -> {
                    if (Prefs.bookmarks.isEmpty()) item { EmptyMessage("Nog geen bladwijzers.") }
                    items(Prefs.bookmarks.toList()) { bm ->
                        BookmarkRow(bm.b, bm.c, bm.v, k) { st.goTo(bm.b, bm.c, bm.v) }
                    }
                }
                else -> {
                    if (Prefs.history.isEmpty()) item { EmptyMessage("Nog geen geschiedenis.") }
                    items(Prefs.history.toList()) { key ->
                        val d = key.split('.')
                        val b = d[0].toInt(); val c = d[1].toInt()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { st.goTo(b, c) }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.History, null, tint = k.muted,
                                modifier = Modifier.size(17.dp),
                            )
                            Text(
                                "  ${Bible.book(b).name} $c",
                                fontFamily = FontFamily.Serif, fontSize = 15.sp, color = k.ink,
                            )
                        }
                        HorizontalDivider(color = k.divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    val k = LocalReadingColors.current
    Text(
        text,
        fontSize = 14.sp, color = k.muted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(30.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BookmarkRow(
    b: Int, c: Int, v: Int, k: ReadingColors, onClick: () -> Unit,
) {
    val verse = remember(b, c, v) { Bible.verse(b, c, maxOf(v, 1)) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            Bible.ref(b, c, v),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = k.accent,
        )
        Text(
            verse?.text ?: "",
            fontFamily = if (Prefs.serif) FontFamily.Serif else FontFamily.SansSerif,
            fontSize = 14.sp, lineHeight = 20.sp, color = k.ink, maxLines = 3,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    HorizontalDivider(color = k.divider)
}

@Composable
fun SettingsScreen(st: AppState) {
    val k = LocalReadingColors.current
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        ScreenHeader("Instellingen", { st.screen = Screen.READER })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Weergave")
            SettingRow("Thema") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Theme.entries.forEach { t ->
                        Chip(t.label, Prefs.theme == t) { Prefs.saveTheme(t) }
                    }
                }
            }
            AccentPicker()
            SliderRow("Tekstgrootte", Prefs.textSize, 13, 34) { Prefs.saveTextSize(it) }
            SliderRow("Regelafstand", Prefs.lineHeight, 110, 220) { Prefs.saveLineHeight(it) }
            SwitchRow("Schreefletter (serif)", Prefs.serif) { Prefs.saveSerif(it) }

            SectionHeader("Kanttekeningen")
            SwitchRow("Toon nummers in de tekst", Prefs.showNoteMarkers) { Prefs.saveNoteMarkers(it) }
            SwitchRow("Compacte versregels", Prefs.compactVerses) { Prefs.saveCompactVerses(it) }

            SectionHeader("Bladeren")
            SwitchRow("Vegen om van hoofdstuk te wisselen", Prefs.swipeNavigation) {
                Prefs.saveSwipeNavigation(it)
            }

            SectionHeader("Overig")
            SwitchRow("Scherm aan laten tijdens lezen", Prefs.keepScreenOn) { Prefs.saveKeepScreenOn(it) }

            SectionHeader("Over")
            Text(
                "Statenvertaling met de kanttekeningen van de Statenvertalers " +
                    "(editie 1888). 31.171 verzen, 59.385 kanttekeningen en 48.466 " +
                    "onderlinge verwijzingen — volledig offline op dit toestel.\n\n" +
                    "Teksteditie: github.com/Isidore-Guild/statenvertaling (CC0).",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = k.muted,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun AccentPicker() {
    val k = LocalReadingColors.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Accentkleur", fontSize = 15.sp, color = k.ink, modifier = Modifier.weight(1f))
            Text(Prefs.accent.label, fontSize = 13.sp, color = k.muted)
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Accent.entries.forEach { a ->
                val chosen = Prefs.accent == a
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(a.color(k.dark))
                        .border(
                            if (chosen) 3.dp else 1.dp,
                            if (chosen) k.ink else k.divider,
                            CircleShape,
                        )
                        .clickable { Prefs.saveAccent(a) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) {
                        Icon(
                            Icons.Default.Check, a.label,
                            tint = k.paper,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val k = LocalReadingColors.current
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = k.accent,
        modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    val k = LocalReadingColors.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(label, fontSize = 15.sp, color = k.ink, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onToggle: (Boolean) -> Unit) {
    val k = LocalReadingColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!value) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = k.ink, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onToggle)
    }
}

@Composable
private fun SliderRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val k = LocalReadingColors.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
        Row {
            Text(label, fontSize = 15.sp, color = k.ink, modifier = Modifier.weight(1f))
            Text("$value", fontSize = 13.sp, color = k.muted)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}
