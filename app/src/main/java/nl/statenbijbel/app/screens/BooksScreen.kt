package nl.statenbijbel.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.statenbijbel.app.AppState
import nl.statenbijbel.app.AppText
import nl.statenbijbel.app.Bible
import nl.statenbijbel.app.LocalReadingColors
import nl.statenbijbel.app.Screen
import nl.statenbijbel.app.normalize

@Composable
fun BooksScreen(state: AppState) {
    val palette = LocalReadingColors.current
    var query by remember { mutableStateOf("") }

    if (state.pickedBook != 0) {
        ChapterPicker(state, state.pickedBook)
        return
    }

    val match = remember(query) { if (query.isBlank()) null else Bible.parseReference(query) }
    val books = remember(query) {
        val bookQuery = Bible.bookPart(query)
        if (bookQuery.isEmpty()) Bible.books
        else Bible.bookCandidates(bookQuery).ifEmpty {
            Bible.books.filter { it.searchText.contains(normalize(bookQuery)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Boeken", { state.screen = Screen.READER })
        SearchField(query, { query = it }, "Boek zoeken, of \"joh 3:16\"")
        if (match != null && Bible.bookPart(query).length >= 2) {
            val (book, chapter, verse) = match
            Surface(
                color = palette.accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { state.goTo(book, chapter, verse) },
            ) {
                Text(
                    "Ga naar ${Bible.ref(book, chapter, verse)}",
                    modifier = Modifier.padding(14.dp),
                    color = palette.accent,
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
                val booksInGroup = books.filter { it.testament == code }
                if (booksInGroup.isNotEmpty()) {
                    item(key = "kop$code") {
                        Text(
                            label,
                            style = AppText.sectionLabel,
                            color = palette.muted,
                            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    items(booksInGroup, key = { it.number }) { book ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { state.pickedBook = book.number }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                book.name,
                                style = AppText.listItem,
                                color = palette.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (book.isBible) "${book.chapterCount} hfdst."
                                else "${book.chapterCount} ${if (book.chapterCount == 1) "deel" else "delen"}",
                                style = AppText.caption, color = palette.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterPicker(state: AppState, book: Int) {
    val palette = LocalReadingColors.current
    val bookInfo = Bible.book(book)
    val titles = remember(book) { Bible.chapterTitles(book) }

    if (titles.isNotEmpty() && bookInfo.chapterCount <= 20) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(bookInfo.name, { state.pickedBook = 0 })
            LazyColumn(
                Modifier
                    .weight(1f)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items((1..bookInfo.chapterCount).toList()) { chapter ->
                    val title = titles[chapter]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { state.pickedBook = 0; state.goTo(book, chapter) }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                    ) {
                        Text(
                            title?.title?.ifBlank { null } ?: "$chapter",
                            style = AppText.listItem,
                            color = palette.ink,
                        )
                        title?.subtitle?.ifBlank { null }?.let { subtitle ->
                            Text(
                                subtitle, style = AppText.caption, color = palette.muted,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = palette.divider)
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(bookInfo.name, { state.pickedBook = 0 })
        LazyVerticalGrid(
            columns = GridCells.Adaptive(56.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((1..bookInfo.chapterCount).toList()) { chapter ->
                val current = state.book == book && state.chapter == chapter
                Surface(
                    color = if (current) palette.accent.copy(alpha = 0.18f)
                    else palette.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { state.pickedBook = 0; state.goTo(book, chapter) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "$chapter",
                            style = AppText.listItem,
                            color = if (current) palette.accent else palette.ink,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
