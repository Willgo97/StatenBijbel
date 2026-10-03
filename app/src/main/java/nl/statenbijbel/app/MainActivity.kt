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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Screen { READER, BOOKS, SEARCH, BOOKMARKS, SETTINGS }

object Index {
    lateinit var pairs: List<Pair<Int, Int>>
        private set
    private lateinit var start: IntArray

    fun build() {
        val l = ArrayList<Pair<Int, Int>>(1500)
        // By book number, not by order: the church book starts at 101.
        val b = IntArray((Bible.books.maxOfOrNull { it.b } ?: 0) + 2)
        Bible.books.forEach { book ->
            b[book.b] = l.size
            for (c in 1..book.chapters) l.add(book.b to c)
        }
        pairs = l
        start = b
    }

    fun index(b: Int, c: Int): Int = (start.getOrNull(b) ?: 0) + (c - 1).coerceAtLeast(0)
    fun bookAt(i: Int) = pairs[i.coerceIn(0, pairs.size - 1)].first
    fun chapterAt(i: Int) = pairs[i.coerceIn(0, pairs.size - 1)].second
    val count get() = pairs.size
}

class AppState {
    var screen by mutableStateOf(Screen.READER)
    var book by mutableIntStateOf(1)
    var chapter by mutableIntStateOf(1)
    var scrollToVerse by mutableIntStateOf(0)
    var selectedVerse by mutableIntStateOf(0)

    var noteB by mutableIntStateOf(0)
    var noteC by mutableIntStateOf(0)
    var noteV by mutableIntStateOf(0)
    var noteN by mutableIntStateOf(0)

    var citationsFor by mutableStateOf<Triple<Int, Int, Int>?>(null)
    var pickedBook by mutableIntStateOf(0)

    fun notesOpen(b: Int, c: Int, v: Int) = noteB == b && noteC == c && noteV == v

    fun toggleNotes(b: Int, c: Int, v: Int, n: Int = 0) {
        if (notesOpen(b, c, v) && (n == 0 || n == noteN)) {
            noteB = 0; noteC = 0; noteV = 0; noteN = 0
        } else {
            noteB = b; noteC = c; noteV = v; noteN = n
        }
    }

    fun showNote(b: Int, c: Int, v: Int, n: Int) {
        goTo(b, c, v)
        noteB = b; noteC = c; noteV = v; noteN = n
    }

    fun goTo(b: Int, c: Int, v: Int = 0) {
        book = b
        chapter = c
        scrollToVerse = v
        selectedVerse = v
        screen = Screen.READER
        Prefs.savePosition(b, c, v)
        Prefs.addToHistory(b, c)
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
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                Prefs.load(ctx)
                Bible.open(ctx)
                Index.build()
            } catch (e: Throwable) {
                error = e.message ?: e.toString()
            }
        }
        ready = true
    }

    if (!ready || error != null) {
        Surface(color = Color(0xFFFBF7F0)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "StatenBijbel",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        color = Color(0xFF2F4858),
                    )
                    Text(
                        error ?: "de tekst wordt klaargezet…",
                        fontSize = 13.sp,
                        color = Color(0xFF7A7168),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (error == null) {
                        CircularProgressIndicator(
                            Modifier.padding(top = 22.dp),
                            color = Color(0xFF8A6431),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
        return
    }

    StatenBijbelTheme {
        val st = remember {
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
            when (st.screen) {
                Screen.READER -> Reader(st)
                Screen.BOOKS -> BooksScreen(st)
                Screen.SEARCH -> SearchScreen(st)
                Screen.BOOKMARKS -> BookmarksScreen(st)
                Screen.SETTINGS -> SettingsScreen(st)
            }
        }

        if (st.screen != Screen.READER) {
            BackHandler { st.screen = Screen.READER }
        }
    }
}
