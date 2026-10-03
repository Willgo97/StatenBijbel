package nl.statenbijbel.app

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatSize
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
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

val SIDE_MARGIN = 10.dp
val VERSE_GUTTER = 22.dp

@Composable
fun readingStyle(): TextStyle = TextStyle(
    fontFamily = if (Prefs.serif) FontFamily.Serif else FontFamily.SansSerif,
    fontSize = Prefs.textSize.sp,
    lineHeight = (Prefs.textSize * Prefs.lineHeight / 100f).sp,
    color = LocalReadingColors.current.ink,
)

private fun annotationAt(
    lr: TextLayoutResult, text: AnnotatedString, pos: Offset, tag: String,
): String? {
    val off = lr.getOffsetForPosition(pos)
    for (o in intArrayOf(off, off - 1)) {
        if (o < 0 || o >= text.length) continue
        val ann = text.getStringAnnotations(tag, o, o).firstOrNull() ?: continue
        val box = lr.getBoundingBox(o)
        if (pos.x >= box.left - 10f && pos.x <= box.right + 10f &&
            pos.y >= box.top - 8f && pos.y <= box.bottom + 8f
        ) return ann.item
    }
    return null
}

@Composable
fun Reader(st: AppState) {
    val pager = rememberPagerState(
        initialPage = Index.index(st.book, st.chapter)
    ) { Index.count }
    var showDisplayBar by remember { mutableStateOf(false) }

    LaunchedEffect(st.book, st.chapter) {
        val target = Index.index(st.book, st.chapter)
        if (pager.currentPage != target) pager.scrollToPage(target)
    }
    LaunchedEffect(pager.settledPage) {
        val b = Index.bookAt(pager.settledPage)
        val c = Index.chapterAt(pager.settledPage)
        if (b != st.book || c != st.chapter) {
            st.book = b; st.chapter = c; st.selectedVerse = 0
            Prefs.savePosition(b, c, 0)
            Prefs.addToHistory(b, c)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(st, onDisplayBar = { showDisplayBar = !showDisplayBar })
        AnimatedVisibility(showDisplayBar) { DisplayBar() }
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
                st, Index.bookAt(page), Index.chapterAt(page),
                active = page == pager.currentPage,
            )
        }
    }

    st.citationsFor?.let { (b, c, v) ->
        CitationsSheet(st, b, c, v) { st.citationsFor = null }
    }
}

@Composable
private fun TopBar(st: AppState, onDisplayBar: () -> Unit) {
    val k = LocalReadingColors.current
    val book = Bible.book(st.book)
    Surface(color = k.paper, tonalElevation = 0.dp) {
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
                        .clickable { st.screen = Screen.BOOKS }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val heading = remember(st.book, st.chapter) {
                        Bible.chapterTitle(st.book, st.chapter)
                            ?.lineSequence()?.firstOrNull()?.trim()
                    }
                    Text(
                        heading ?: "${book.name} ${st.chapter}",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                        color = k.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        Icons.Default.ExpandMore, null,
                        tint = k.muted,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(19.dp),
                    )
                }
                IconButton(onDisplayBar) {
                    Icon(Icons.Default.FormatSize, "Weergave", tint = k.muted)
                }
                IconButton({ st.screen = Screen.SEARCH }) {
                    Icon(Icons.Default.Search, "Zoeken", tint = k.muted)
                }
                IconButton({ st.screen = Screen.BOOKMARKS }) {
                    Icon(Icons.Default.Bookmark, "Bladwijzers", tint = k.muted)
                }
                IconButton({ st.screen = Screen.SETTINGS }) {
                    Icon(Icons.Default.Settings, "Instellingen", tint = k.muted)
                }
            }
            HorizontalDivider(color = k.divider)
        }
    }
}

@Composable
private fun ChapterPage(st: AppState, b: Int, c: Int, active: Boolean) {
    val k = LocalReadingColors.current
    val verses = remember(b, c) { Bible.verses(b, c) }
    val notes = remember(b, c) { Bible.notes(b, c) }
    val book = remember(b) { Bible.book(b) }
    val style = readingStyle()
    val colors = renderColors()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The tapped verse stays in place when a block above it collapses.
    fun toggleNotes(verse: Int, n: Int) {
        val i = verses.indexOfFirst { it.v == verse } + 1 // +1 for the header
        val before = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }?.offset
        st.toggleNotes(b, c, verse, n)
        if (before == null) return
        scope.launch {
            withFrameNanos { }
            listState.scrollToItem(i)
            val after = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }?.offset
                ?: return@launch
            listState.scrollBy((after - before).toFloat())
        }
    }

    LaunchedEffect(active, st.scrollToVerse, b, c) {
        if (active && st.scrollToVerse > 0) {
            val i = verses.indexOfFirst { it.v == st.scrollToVerse }
            if (i >= 0) listState.scrollToItem(i + 1)
            st.scrollToVerse = 0
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SIDE_MARGIN, end = SIDE_MARGIN, top = 10.dp, bottom = 120.dp
        ),
    ) {
        item(key = "kop") {
            val title = remember(b, c) { Bible.chapterTitle(b, c) }
            val lines = title?.split("\n").orEmpty()
            Column(Modifier.padding(bottom = 14.dp)) {
                val overline = when {
                    !book.isBible -> book.name
                    c == 1 && book.title.isNotBlank() -> book.title
                    else -> ""
                }
                if (overline.isNotBlank()) {
                    Text(
                        overline,
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        color = k.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    )
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        lines.firstOrNull()?.trim()?.ifBlank { null } ?: "${book.name} $c",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 25.sp,
                        color = k.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                    )
                    val bookmarked = Prefs.isBookmarked(b, c, 0)
                    IconButton(
                        { Prefs.toggleBookmark(b, c, 0) },
                        Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            if (bookmarked) "Bladwijzer weghalen" else "Bladwijzer zetten",
                            tint = if (bookmarked) k.accent else k.muted,
                        )
                    }
                }
                if (lines.size > 1 && lines[1].isNotBlank()) {
                    Text(
                        lines[1].trim(),
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        color = k.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
        items(verses, key = { it.vid }) { v ->
            VerseLine(st, v, notes[v.v].orEmpty(), style, colors, ::toggleNotes)
        }
        item(key = "voet") {
            ChapterFooter(st, b, c)
        }
    }
}

@Composable
private fun VerseLine(
    st: AppState, v: Verse, notes: List<Note>, style: TextStyle, colors: RenderColors,
    toggleNotes: (verse: Int, n: Int) -> Unit,
) {
    val k = LocalReadingColors.current
    val text = remember(v.vid, Prefs.showNoteMarkers, k.dark) {
        verseText(v, colors, Prefs.showNoteMarkers)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val selected = st.selectedVerse == v.v
    val notesOpen = st.notesOpen(v.b, v.c, v.v)
    val bgColor = if (selected) k.selection else Color.Transparent

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .padding(vertical = if (Prefs.compactVerses) 1.dp else 4.dp),
        ) {
            Text(
                v.v.toString(),
                modifier = Modifier
                    .width(VERSE_GUTTER)
                    .padding(top = 3.dp, end = 5.dp),
                textAlign = TextAlign.End,
                fontSize = (Prefs.textSize * 0.62f).sp,
                color = if (notesOpen) k.accent else k.verseNumber,
                fontFamily = FontFamily.SansSerif,
            )
            Text(
                text = text,
                style = style,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(text, notes.size) {
                        detectTapGestures { pos ->
                            st.selectedVerse = 0
                            val lr = layout
                            val tappedNote = lr?.let { annotationAt(it, text, pos, TAG_NOTE) }
                            when {
                                tappedNote != null -> toggleNotes(v.v, tappedNote.toIntOrNull() ?: 0)
                                notes.isNotEmpty() -> toggleNotes(v.v, 0)
                                Bible.citations(v.b, v.c, v.v).isNotEmpty() ->
                                    st.citationsFor = Triple(v.b, v.c, v.v)
                            }
                        }
                    },
                onTextLayout = { layout = it },
            )
        }
        if (notesOpen) NotesBlock(st, v.b, v.c, v.v)
    }
}

@Composable
private fun ChapterFooter(st: AppState, b: Int, c: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (Index.index(b, c) > 0) {
            TextButton({
                val i = Index.index(b, c) - 1
                st.goTo(Index.bookAt(i), Index.chapterAt(i))
            }) {
                Icon(Icons.Default.ArrowBack, null, Modifier.size(17.dp))
                Text("  vorige", fontSize = 14.sp)
            }
        } else Spacer(Modifier.width(1.dp))
        if (Index.index(b, c) < Index.count - 1) {
            TextButton({
                val i = Index.index(b, c) + 1
                st.goTo(Index.bookAt(i), Index.chapterAt(i))
            }) {
                Text("volgende  ", fontSize = 14.sp)
                Icon(Icons.Default.ArrowForward, null, Modifier.size(17.dp))
            }
        } else Spacer(Modifier.width(1.dp))
    }
}
