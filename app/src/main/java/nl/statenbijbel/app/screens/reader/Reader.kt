package nl.statenbijbel.app.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.statenbijbel.app.AppState
import nl.statenbijbel.app.AppText
import nl.statenbijbel.app.Bible
import nl.statenbijbel.app.Bookmark
import nl.statenbijbel.app.Index
import nl.statenbijbel.app.LocalReadingColors
import nl.statenbijbel.app.Note
import nl.statenbijbel.app.Prefs
import nl.statenbijbel.app.Screen
import nl.statenbijbel.app.TAG_NOTE
import nl.statenbijbel.app.Verse
import nl.statenbijbel.app.readingStyle
import nl.statenbijbel.app.verseText

val SIDE_MARGIN = 10.dp

val VERSE_GUTTER = 22.dp

private fun annotationAt(
    layout: TextLayoutResult, text: AnnotatedString, position: Offset, tag: String,
): String? {
    val tapped = layout.getOffsetForPosition(position)
    for (offset in intArrayOf(tapped, tapped - 1)) {
        if (offset < 0 || offset >= text.length) continue
        val annotation = text.getStringAnnotations(tag, offset, offset).firstOrNull() ?: continue
        val box = layout.getBoundingBox(offset)
        if (position.x >= box.left - 10f && position.x <= box.right + 10f &&
            position.y >= box.top - 8f && position.y <= box.bottom + 8f
        ) return annotation.item
    }
    return null
}

@Composable
fun Reader(state: AppState) {
    val pager = rememberPagerState(
        initialPage = Index.index(state.book, state.chapter)
    ) { Index.count }

    LaunchedEffect(state.book, state.chapter) {
        val target = Index.index(state.book, state.chapter)
        if (pager.currentPage != target) pager.scrollToPage(target)
    }
    LaunchedEffect(pager.settledPage) {
        val book = Index.bookAt(pager.settledPage)
        val chapter = Index.chapterAt(pager.settledPage)
        if (book != state.book || chapter != state.chapter) {
            state.book = book; state.chapter = chapter; state.selectedVerse = 0
            Prefs.savePosition(book, chapter, 0)
            Prefs.addToHistory(book, chapter)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(state)
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            beyondViewportPageCount = if (Prefs.swipeNavigation) 1 else 0,
            userScrollEnabled = Prefs.swipeNavigation,
            key = { it },
        ) { page ->
            ChapterPage(
                state, Index.bookAt(page), Index.chapterAt(page),
                active = page == pager.currentPage,
            )
        }
    }

    state.citationsFor?.let { (book, chapter, verse) ->
        CitationsSheet(state, book, chapter, verse) { state.citationsFor = null }
    }
}

@Composable
private fun TopBar(state: AppState) {
    val palette = LocalReadingColors.current
    Surface(color = palette.paper, tonalElevation = 0.dp) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { state.screen = Screen.BOOKS }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Bible.chapterHeading(state.book, state.chapter),
                        style = AppText.title,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        Icons.Default.ExpandMore, null,
                        tint = palette.muted,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(19.dp),
                    )
                }
                IconButton({ state.screen = Screen.SEARCH }) {
                    Icon(Icons.Default.Search, "Zoeken", tint = palette.muted)
                }
                IconButton({ state.screen = Screen.BOOKMARKS }) {
                    Icon(Icons.Default.Bookmark, "Bladwijzers", tint = palette.muted)
                }
                IconButton({ state.screen = Screen.SETTINGS }) {
                    Icon(Icons.Default.Settings, "Instellingen", tint = palette.muted)
                }
            }
            HorizontalDivider(color = palette.divider)
        }
    }
}

@Composable
private fun ChapterPage(state: AppState, book: Int, chapter: Int, active: Boolean) {
    val palette = LocalReadingColors.current
    val verses = remember(book, chapter) { Bible.verses(book, chapter) }
    val notes = remember(book, chapter) { Bible.notes(book, chapter) }
    val style = readingStyle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The tapped verse stays in place when a block above it collapses.
    fun toggleNotes(verse: Int, noteNumber: Int) {
        val itemIndex = verses.indexOfFirst { it.number == verse } + 1 // +1 for the header
        val before = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == itemIndex }?.offset
        state.toggleNotes(book, chapter, verse, noteNumber)
        if (before == null) return
        scope.launch {
            withFrameNanos { }
            listState.scrollToItem(itemIndex)
            val after = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == itemIndex }?.offset
                ?: return@launch
            listState.scrollBy((after - before).toFloat())
        }
    }

    LaunchedEffect(active, state.scrollToVerse, book, chapter) {
        if (active && state.scrollToVerse > 0) {
            val verseIndex = verses.indexOfFirst { it.number == state.scrollToVerse }
            if (verseIndex >= 0) listState.scrollToItem(verseIndex + 1)
            state.scrollToVerse = 0
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SIDE_MARGIN, end = SIDE_MARGIN, top = 10.dp, bottom = 120.dp
        ),
    ) {
        item(key = "kop") { ChapterHeader(book, chapter) }
        items(verses, key = { it.id }) { verse ->
            VerseLine(state, verse, notes[verse.number].orEmpty(), style, ::toggleNotes)
        }
        item(key = "voet") {
            ChapterFooter(state, book, chapter)
        }
    }
}

@Composable
private fun ChapterHeader(book: Int, chapter: Int) {
    val palette = LocalReadingColors.current
    val bookInfo = Bible.book(book)
    val overline = when {
        !bookInfo.isBible -> bookInfo.name
        chapter == 1 -> bookInfo.title
        else -> ""
    }
    val subtitle = remember(book, chapter) { Bible.chapterTitle(book, chapter)?.subtitle.orEmpty() }
    val besideTitle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 14.sp, textAlign = TextAlign.Center)

    Column(Modifier.padding(bottom = 14.dp)) {
        if (overline.isNotBlank()) {
            Text(
                overline, style = besideTitle, color = palette.muted,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                Bible.chapterHeading(book, chapter),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
                color = palette.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
            )
            val bookmarked = Prefs.isBookmarked(book, chapter, 0)
            IconButton({ Prefs.toggleBookmark(book, chapter, 0) }, Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    if (bookmarked) "Bladwijzer weghalen" else "Bladwijzer zetten",
                    tint = if (bookmarked) palette.accent else palette.muted,
                )
            }
        }
        if (subtitle.isNotBlank()) {
            Text(
                subtitle, style = besideTitle, color = palette.muted,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun VerseLine(
    state: AppState, verse: Verse, notes: List<Note>, style: TextStyle,
    toggleNotes: (verse: Int, noteNumber: Int) -> Unit,
) {
    val palette = LocalReadingColors.current
    val text = remember(verse.id, Prefs.showNoteMarkers, palette) {
        verseText(verse, palette, Prefs.showNoteMarkers)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val selected = state.selectedVerse == verse.number
    val notesOpen = state.notesOpen(verse.book, verse.chapter, verse.number)
    val bgColor = if (selected) palette.selection else Color.Transparent

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .padding(vertical = if (Prefs.compactVerses) 1.dp else 4.dp),
        ) {
            Text(
                verse.number.toString(),
                modifier = Modifier
                    .width(VERSE_GUTTER)
                    .padding(top = 3.dp, end = 5.dp),
                textAlign = TextAlign.End,
                fontSize = (Prefs.textSize * 0.62f).sp,
                color = if (notesOpen) palette.accent else palette.verseNumber,
                fontFamily = FontFamily.SansSerif,
            )
            Text(
                text = text,
                style = style,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(text, notes.size) {
                        detectTapGestures { position ->
                            state.selectedVerse = 0
                            val currentLayout = layout
                            val tappedNote = currentLayout?.let {
                                annotationAt(it, text, position, TAG_NOTE)
                            }
                            when {
                                tappedNote != null ->
                                    toggleNotes(verse.number, tappedNote.toIntOrNull() ?: 0)
                                notes.isNotEmpty() -> toggleNotes(verse.number, 0)
                                Bible.citations(verse.book, verse.chapter, verse.number)
                                    .isNotEmpty() ->
                                    state.citationsFor =
                                        Triple(verse.book, verse.chapter, verse.number)
                            }
                        }
                    },
                onTextLayout = { layout = it },
            )
        }
        if (notesOpen) NotesBlock(state, verse.book, verse.chapter, verse.number)
    }
}

@Composable
private fun ChapterFooter(state: AppState, book: Int, chapter: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (Index.index(book, chapter) > 0) {
            TextButton({
                val previousPage = Index.index(book, chapter) - 1
                state.goTo(Index.bookAt(previousPage), Index.chapterAt(previousPage))
            }) {
                Icon(Icons.Default.ArrowBack, null, Modifier.size(17.dp))
                Text("  vorige")
            }
        } else Spacer(Modifier.width(1.dp))
        if (Index.index(book, chapter) < Index.count - 1) {
            TextButton({
                val nextPage = Index.index(book, chapter) + 1
                state.goTo(Index.bookAt(nextPage), Index.chapterAt(nextPage))
            }) {
                Text("volgende  ")
                Icon(Icons.Default.ArrowForward, null, Modifier.size(17.dp))
            }
        } else Spacer(Modifier.width(1.dp))
    }
}
