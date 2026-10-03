package nl.statenbijbel.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import nl.statenbijbel.app.AppState
import nl.statenbijbel.app.AppText
import nl.statenbijbel.app.Bible
import nl.statenbijbel.app.Bookmark
import nl.statenbijbel.app.LocalReadingColors
import nl.statenbijbel.app.Prefs
import nl.statenbijbel.app.Screen
import nl.statenbijbel.app.VisitedChapter
import nl.statenbijbel.app.verseText

@Composable
fun BookmarksScreen(state: AppState) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Bewaard", { state.screen = Screen.READER })
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
            if (tab == 0) {
                if (Prefs.bookmarks.isEmpty()) item { EmptyMessage("Nog geen bladwijzers.") }
                items(Prefs.bookmarks.toList()) { bookmark ->
                    BookmarkRow(bookmark) { state.goTo(bookmark.book, bookmark.chapter, bookmark.verse) }
                }
            } else {
                if (Prefs.history.isEmpty()) item { EmptyMessage("Nog geen geschiedenis.") }
                items(Prefs.history.toList()) { visit ->
                    HistoryRow(visit) { state.goTo(visit.book, visit.chapter) }
                }
            }
        }
    }
}

// A chapter bookmark (verse 0) shows verse 1 as a preview.
@Composable
private fun BookmarkRow(bookmark: Bookmark, onClick: () -> Unit) {
    val palette = LocalReadingColors.current
    val text = remember(bookmark, palette) {
        Bible.verse(bookmark.book, bookmark.chapter, maxOf(bookmark.verse, 1))
            ?.let { verseText(it, palette, showMarkers = false) } ?: AnnotatedString("")
    }
    ReferenceRow(Bible.ref(bookmark.book, bookmark.chapter, bookmark.verse), text, onClick)
}

@Composable
private fun HistoryRow(visit: VisitedChapter, onClick: () -> Unit) {
    val palette = LocalReadingColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.History, null, tint = palette.muted, modifier = Modifier.size(17.dp))
        Text(
            "  ${Bible.book(visit.book).name} ${visit.chapter}",
            style = AppText.listItem,
            color = palette.ink,
        )
    }
    HorizontalDivider(color = palette.divider)
}
