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

class Bookmark(val b: Int, val c: Int, val v: Int, val time: Long) {
    val key get() = "$b.$c.$v"
}

object Prefs {
    private lateinit var sp: SharedPreferences

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
    val history = mutableStateListOf<String>()

    fun load(ctx: Context) {
        sp = ctx.getSharedPreferences("statenbijbel", Context.MODE_PRIVATE)
        theme = Theme.fromKey(sp.getString("thema", Theme.SYSTEM.key)) ?: Theme.SYSTEM
        accent = Accent.fromKey(sp.getString("accent", Accent.GOLD.key)) ?: Accent.GOLD
        textSize = sp.getInt("grootte", 19)
        lineHeight = sp.getInt("regel", 150)
        serif = sp.getBoolean("schreef", true)
        showNoteMarkers = sp.getBoolean("markers", true)
        compactVerses = sp.getBoolean("doorlopend", false)
        keepScreenOn = sp.getBoolean("schermaan", false)
        swipeNavigation = sp.getBoolean("vegen", false)
        book = sp.getInt("boek", 1)
        chapter = sp.getInt("hoofdstuk", 1)
        verse = sp.getInt("vers", 0)

        bookmarks.clear()
        sp.getString("bladwijzers", "")!!.split(';').forEach { row ->
            val d = row.split(',')
            if (d.size == 4) {
                bookmarks.add(
                    Bookmark(d[0].toInt(), d[1].toInt(), d[2].toInt(), d[3].toLong())
                )
            }
        }
        if (sp.contains("markeringen")) edit { remove("markeringen") }
        history.clear()
        sp.getString("geschiedenis", "")!!.split(';').filter { it.isNotBlank() }
            .forEach { history.add(it) }
    }

    private fun edit(f: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(f).apply()
    }

    fun saveTheme(t: Theme) { theme = t; edit { putString("thema", t.key) } }
    fun saveAccent(a: Accent) { accent = a; edit { putString("accent", a.key) } }
    fun saveTextSize(v: Int) {
        textSize = v.coerceIn(13, 34); edit { putInt("grootte", textSize) }
    }
    fun saveLineHeight(v: Int) {
        lineHeight = v.coerceIn(110, 220); edit { putInt("regel", lineHeight) }
    }
    fun saveSerif(v: Boolean) { serif = v; edit { putBoolean("schreef", v) } }
    fun saveNoteMarkers(v: Boolean) { showNoteMarkers = v; edit { putBoolean("markers", v) } }
    fun saveCompactVerses(v: Boolean) { compactVerses = v; edit { putBoolean("doorlopend", v) } }
    fun saveKeepScreenOn(v: Boolean) { keepScreenOn = v; edit { putBoolean("schermaan", v) } }
    fun saveSwipeNavigation(v: Boolean) { swipeNavigation = v; edit { putBoolean("vegen", v) } }

    fun savePosition(b: Int, c: Int, v: Int) {
        book = b; chapter = c; verse = v
        edit { putInt("boek", b); putInt("hoofdstuk", c); putInt("vers", v) }
    }

    fun addToHistory(b: Int, c: Int) {
        val key = "$b.$c"
        history.remove(key)
        history.add(0, key)
        while (history.size > 40) history.removeAt(history.size - 1)
        edit { putString("geschiedenis", history.joinToString(";")) }
    }

    fun isBookmarked(b: Int, c: Int, v: Int) =
        bookmarks.any { it.b == b && it.c == c && it.v == v }

    fun toggleBookmark(b: Int, c: Int, v: Int) {
        val existing = bookmarks.indexOfFirst { it.b == b && it.c == c && it.v == v }
        if (existing >= 0) bookmarks.removeAt(existing)
        else bookmarks.add(0, Bookmark(b, c, v, System.currentTimeMillis()))
        saveBookmarks()
    }

    private fun saveBookmarks() = edit {
        putString("bladwijzers",
            bookmarks.joinToString(";") { "${it.b},${it.c},${it.v},${it.time}" })
    }
}
