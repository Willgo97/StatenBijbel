package nl.statenbijbel.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Theme(val key: String, val label: String) {
    SYSTEM("SYSTEEM", "Systeem"),
    LIGHT("LICHT", "Licht"),
    SEPIA("SEPIA", "Sepia"),
    DARK("DONKER", "Donker"),
    NIGHT("NACHT", "Nacht"),
    ;

    companion object {
        fun fromKey(key: String?): Theme? = entries.firstOrNull { it.key == key }
    }
}

class Bookmark(val book: Int, val chapter: Int, val verse: Int, val time: Long)

data class VisitedChapter(val book: Int, val chapter: Int)

object Prefs {
    private lateinit var prefs: SharedPreferences

    var theme by mutableStateOf(Theme.SYSTEM)
        private set
    var accent by mutableStateOf(Accent.GOLD)
        private set
    var textSize by mutableIntStateOf(19)
        private set
    var lineHeight by mutableIntStateOf(150) // percent
    var serif by mutableStateOf(true)
        private set
    var showNoteMarkers by mutableStateOf(true)
        private set
    var compactVerses by mutableStateOf(false)
        private set
    var keepScreenOn by mutableStateOf(false)
        private set
    var swipeNavigation by mutableStateOf(false)
        private set

    var book by mutableIntStateOf(1)
    var chapter by mutableIntStateOf(1)
    var verse by mutableIntStateOf(0)

    val bookmarks = mutableStateListOf<Bookmark>()
    val history = mutableStateListOf<VisitedChapter>()

    fun load(context: Context) {
        prefs = context.getSharedPreferences("statenbijbel", Context.MODE_PRIVATE)
        theme = Theme.fromKey(prefs.getString("thema", Theme.SYSTEM.key)) ?: Theme.SYSTEM
        accent = Accent.fromKey(prefs.getString("accent", Accent.GOLD.key)) ?: Accent.GOLD
        textSize = prefs.getInt("grootte", 19)
        lineHeight = prefs.getInt("regel", 150)
        serif = prefs.getBoolean("schreef", true)
        showNoteMarkers = prefs.getBoolean("markers", true)
        compactVerses = prefs.getBoolean("doorlopend", false)
        keepScreenOn = prefs.getBoolean("schermaan", false)
        swipeNavigation = prefs.getBoolean("vegen", false)
        book = prefs.getInt("boek", 1)
        chapter = prefs.getInt("hoofdstuk", 1)
        verse = prefs.getInt("vers", 0)

        bookmarks.clear()
        // "book,chapter,verse,time;..."
        prefs.getString("bladwijzers", "")!!.split(';').forEach { row ->
            val fields = row.split(',')
            if (fields.size == 4) {
                bookmarks.add(
                    Bookmark(
                        fields[0].toInt(), fields[1].toInt(), fields[2].toInt(),
                        fields[3].toLong(),
                    )
                )
            }
        }
        if (prefs.contains("markeringen")) edit { remove("markeringen") }
        history.clear()
        // "book.chapter;..."
        prefs.getString("geschiedenis", "")!!.split(';').forEach { entry ->
            val fields = entry.split('.')
            if (fields.size == 2) history.add(VisitedChapter(fields[0].toInt(), fields[1].toInt()))
        }
    }

    private fun edit(changes: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(changes).apply()
    }

    fun saveTheme(newTheme: Theme) { theme = newTheme; edit { putString("thema", newTheme.key) } }
    fun saveAccent(newAccent: Accent) {
        accent = newAccent; edit { putString("accent", newAccent.key) }
    }
    fun saveTextSize(size: Int) {
        textSize = size.coerceIn(13, 34); edit { putInt("grootte", textSize) }
    }
    fun saveLineHeight(percent: Int) {
        lineHeight = percent.coerceIn(110, 220); edit { putInt("regel", lineHeight) }
    }
    fun saveSerif(enabled: Boolean) { serif = enabled; edit { putBoolean("schreef", enabled) } }
    fun saveNoteMarkers(enabled: Boolean) {
        showNoteMarkers = enabled; edit { putBoolean("markers", enabled) }
    }
    fun saveCompactVerses(enabled: Boolean) {
        compactVerses = enabled; edit { putBoolean("doorlopend", enabled) }
    }
    fun saveKeepScreenOn(enabled: Boolean) {
        keepScreenOn = enabled; edit { putBoolean("schermaan", enabled) }
    }
    fun saveSwipeNavigation(enabled: Boolean) {
        swipeNavigation = enabled; edit { putBoolean("vegen", enabled) }
    }

    fun savePosition(book: Int, chapter: Int, verse: Int) {
        this.book = book; this.chapter = chapter; this.verse = verse
        edit { putInt("boek", book); putInt("hoofdstuk", chapter); putInt("vers", verse) }
    }

    fun addToHistory(book: Int, chapter: Int) {
        val visit = VisitedChapter(book, chapter)
        history.remove(visit)
        history.add(0, visit)
        while (history.size > 40) history.removeAt(history.size - 1)
        edit { putString("geschiedenis", history.joinToString(";") { "${it.book}.${it.chapter}" }) }
    }

    fun isBookmarked(book: Int, chapter: Int, verse: Int) =
        bookmarks.any { it.book == book && it.chapter == chapter && it.verse == verse }

    fun toggleBookmark(book: Int, chapter: Int, verse: Int) {
        val existing = bookmarks.indexOfFirst {
            it.book == book && it.chapter == chapter && it.verse == verse
        }
        if (existing >= 0) bookmarks.removeAt(existing)
        else bookmarks.add(0, Bookmark(book, chapter, verse, System.currentTimeMillis()))
        saveBookmarks()
    }

    private fun saveBookmarks() = edit {
        putString("bladwijzers",
            bookmarks.joinToString(";") { "${it.book},${it.chapter},${it.verse},${it.time}" })
    }
}
