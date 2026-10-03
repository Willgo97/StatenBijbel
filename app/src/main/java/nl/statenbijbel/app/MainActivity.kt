package nl.statenbijbel.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.statenbijbel.app.screens.BookmarksScreen
import nl.statenbijbel.app.screens.BooksScreen
import nl.statenbijbel.app.screens.SearchScreen
import nl.statenbijbel.app.screens.SettingsScreen
import nl.statenbijbel.app.screens.reader.Reader

enum class Screen { READER, BOOKS, SEARCH, BOOKMARKS, SETTINGS }

object Index {
    lateinit var pairs: List<Pair<Int, Int>>
        private set
    private lateinit var firstPageOfBook: IntArray

    fun build() {
        val chapterPairs = ArrayList<Pair<Int, Int>>(1500)
        // By book number, not by order: the church book starts at 101.
        val firstPages = IntArray((Bible.books.maxOfOrNull { it.number } ?: 0) + 2)
        Bible.books.forEach { book ->
            firstPages[book.number] = chapterPairs.size
            for (chapter in 1..book.chapterCount) chapterPairs.add(book.number to chapter)
        }
        pairs = chapterPairs
        firstPageOfBook = firstPages
    }

    fun index(book: Int, chapter: Int): Int =
        (firstPageOfBook.getOrNull(book) ?: 0) + (chapter - 1).coerceAtLeast(0)
    fun bookAt(page: Int) = pairs[page.coerceIn(0, pairs.size - 1)].first
    fun chapterAt(page: Int) = pairs[page.coerceIn(0, pairs.size - 1)].second
    val count get() = pairs.size
}

class AppState {
    var screen by mutableStateOf(Screen.READER)
    var book by mutableIntStateOf(1)
    var chapter by mutableIntStateOf(1)
    var scrollToVerse by mutableIntStateOf(0)
    var selectedVerse by mutableIntStateOf(0)

    // The verse whose notes are open, and the note that is highlighted (0 = none).
    var notesBook by mutableIntStateOf(0)
    var notesChapter by mutableIntStateOf(0)
    var notesVerse by mutableIntStateOf(0)
    var highlightedNote by mutableIntStateOf(0)

    var citationsFor by mutableStateOf<Triple<Int, Int, Int>?>(null)
    var pickedBook by mutableIntStateOf(0)

    fun notesOpen(book: Int, chapter: Int, verse: Int) =
        notesBook == book && notesChapter == chapter && notesVerse == verse

    fun toggleNotes(book: Int, chapter: Int, verse: Int, noteNumber: Int = 0) {
        if (notesOpen(book, chapter, verse) && (noteNumber == 0 || noteNumber == highlightedNote)) {
            notesBook = 0; notesChapter = 0; notesVerse = 0; highlightedNote = 0
        } else {
            notesBook = book; notesChapter = chapter; notesVerse = verse
            highlightedNote = noteNumber
        }
    }

    fun showNote(book: Int, chapter: Int, verse: Int, noteNumber: Int) {
        goTo(book, chapter, verse)
        notesBook = book; notesChapter = chapter; notesVerse = verse
        highlightedNote = noteNumber
    }

    fun goTo(book: Int, chapter: Int, verse: Int = 0) {
        this.book = book
        this.chapter = chapter
        scrollToVerse = verse
        selectedVerse = verse
        screen = Screen.READER
        Prefs.savePosition(book, chapter, verse)
        Prefs.addToHistory(book, chapter)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(this) }
    }
}

@Composable
fun App(activity: ComponentActivity) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                Prefs.load(context)
                Bible.open(context)
                Index.build()
            } catch (e: Throwable) {
                error = e.message ?: e.toString()
            }
        }
        ready = true
    }

    if (!ready || error != null) {
        Surface(color = lightColors.paper) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "StatenBijbel",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        color = lightColors.ink,
                    )
                    Text(
                        error ?: "de tekst wordt klaargezet…",
                        fontSize = 13.sp,
                        color = lightColors.muted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (error == null) {
                        CircularProgressIndicator(
                            Modifier.padding(top = 22.dp),
                            color = lightColors.accent,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
        return
    }

    StatenBijbelTheme {
        val state = remember {
            AppState().apply {
                book = if (Bible.bookOrNull(Prefs.book) != null) Prefs.book else 1
                chapter = Prefs.chapter
                scrollToVerse = Prefs.verse
            }
        }
        LaunchedEffect(Prefs.keepScreenOn) {
            if (Prefs.keepScreenOn)
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        Surface(
            Modifier.fillMaxSize(),
            color = LocalReadingColors.current.paper,
        ) {
            when (state.screen) {
                Screen.READER -> Reader(state)
                Screen.BOOKS -> BooksScreen(state)
                Screen.SEARCH -> SearchScreen(state)
                Screen.BOOKMARKS -> BookmarksScreen(state)
                Screen.SETTINGS -> SettingsScreen(state)
            }
        }

        if (state.screen != Screen.READER) {
            BackHandler { state.screen = Screen.READER }
        }
    }
}
