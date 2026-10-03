package nl.statenbijbel.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean

class Span(val kind: Char, val start: Int, val end: Int, val value: String)

class Book(
    val number: Int, val code: String, val name: String, val abbreviation: String,
    val testament: String, val title: String, val chapterCount: Int,
    val aliases: String,
) {
    val searchText: String = normalize("$name $abbreviation $aliases $code")

    // The church book (metrical psalms, confessions, forms, prayers) has testament "EX".
    val isBible: Boolean get() = testament != "EX"
}

class Verse(
    val id: Int, val book: Int, val chapter: Int, val number: Int,
    val text: String, val spans: List<Span>,
)

class Note(
    val id: Int, val book: Int, val chapter: Int, val verse: Int, val number: Int,
    val catchword: String, val text: String, val spans: List<Span>,
)

class Ref(val book: Int, val chapter: Int, val verse: Int, val endVerse: Int) {
    companion object {
        fun parse(value: String): Ref? {
            val parts = value.split('.')
            if (parts.size < 3) return null
            return try {
                Ref(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(),
                    if (parts.size > 3) parts[3].toInt() else 0)
            } catch (e: NumberFormatException) { null }
        }
    }
}

// noteNumber is 0 for a hit in the Bible text itself.
class Hit(
    val book: Int, val chapter: Int, val verse: Int, val text: String, val spans: List<Span>,
    val noteNumber: Int = 0, val catchword: String = "",
)

class Citation(
    val book: Int, val chapter: Int, val verse: Int, val noteNumber: Int,
    val catchword: String, val text: String, val spans: List<Span>,
)

class ChapterTitle(val title: String, val subtitle: String)

fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase()

// Like normalize, but one character at a time, so positions stay aligned with the original.
fun normalizeChar(character: Char): Char {
    if (character.code < 128) return character.lowercaseChar()
    val base = Normalizer.normalize(character.toString(), Normalizer.Form.NFD)
        .firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    return (base ?: character).lowercaseChar()
}

val WORD_PATTERN = Regex("[a-z0-9]+")
private val MARKS = Regex("\\p{Mn}+")

// "kind,start,end,value|kind,start,end,value|..."
private fun parseSpans(encoded: String?): List<Span> {
    if (encoded.isNullOrEmpty()) return emptyList()
    val spans = ArrayList<Span>(8)
    for (part in encoded.split('|')) {
        if (part.isEmpty()) continue
        val firstComma = part.indexOf(',')
        val secondComma = part.indexOf(',', firstComma + 1)
        val thirdComma = part.indexOf(',', secondComma + 1)
        if (firstComma < 0 || secondComma < 0 || thirdComma < 0) continue
        spans.add(
            Span(
                part[0],
                part.substring(firstComma + 1, secondComma).toInt(),
                part.substring(secondComma + 1, thirdComma).toInt(),
                part.substring(thirdComma + 1),
            )
        )
    }
    return spans
}

object Bible {
    const val DB_ASSET = "bijbel.db"
    const val DB_VERSION = 4

    private lateinit var db: SQLiteDatabase
    private val ready = AtomicBoolean(false)

    lateinit var books: List<Book>
        private set
    private lateinit var booksByNumber: Map<Int, Book>

    // Re-extract on every app update, otherwise an old text would linger.
    fun open(context: Context) {
        if (ready.get()) return
        val lastUpdate = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        val prefs = context.getSharedPreferences("statenbijbel", Context.MODE_PRIVATE)
        val prevStamp = prefs.getLong("db_stempel", -1L)
        val target = File(context.filesDir, "bijbel-v$DB_VERSION.db")
        if (!target.exists() || target.length() < 1_000_000 || prevStamp != lastUpdate) {
            context.filesDir.listFiles { file -> file.name.startsWith("bijbel-v") }
                ?.forEach { it.delete() }
            val tempFile = File(context.filesDir, "bijbel.tmp")
            context.assets.open(DB_ASSET).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            }
            tempFile.renameTo(target)
            prefs.edit().putLong("db_stempel", lastUpdate).apply()
        }
        db = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
        books = db.rawQuery(
            "SELECT number,code,name,abbreviation,testament,title,chapter_count,aliases " +
                "FROM books ORDER BY number", null
        ).use { cursor ->
            val list = ArrayList<Book>(66)
            while (cursor.moveToNext()) {
                list.add(
                    Book(
                        cursor.getInt(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5) ?: "",
                        cursor.getInt(6), cursor.getString(7) ?: "",
                    )
                )
            }
            list
        }
        booksByNumber = books.associateBy { it.number }
        ready.set(true)
    }

    fun book(number: Int): Book = booksByNumber[number] ?: books[0]
    fun bookOrNull(number: Int): Book? = booksByNumber[number]

    fun ref(book: Int, chapter: Int, verse: Int, endVerse: Int = 0): String {
        val bookInfo = booksByNumber[book] ?: return ""
        // "Joh. 3:16", but "Ruth 1:1", "HC 1:1" and "Ps. ber. 23:1".
        val abbreviation = bookInfo.abbreviation
        val dot = if (!abbreviation.endsWith(".") && !abbreviation.equals(bookInfo.name, true) &&
            bookInfo.name.startsWith(abbreviation, ignoreCase = true)
        ) "." else ""
        return buildString {
            append(abbreviation); append(dot); append(' '); append(chapter)
            if (verse > 0) { append(':'); append(verse) }
            if (endVerse > verse) { append('-'); append(endVerse) }
        }
    }

    private val chapterCache = object : LinkedHashMap<Long, List<Verse>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<Verse>>) =
            size > 12
    }

    fun verses(book: Int, chapter: Int): List<Verse> {
        val key = book * 1000L + chapter
        synchronized(chapterCache) { chapterCache[key] }?.let { return it }
        val chapterVerses = db.rawQuery(
            "SELECT id,book,chapter,verse,text,spans FROM verses " +
                "WHERE book=? AND chapter=? ORDER BY verse",
            arrayOf(book.toString(), chapter.toString())
        ).use { cursor ->
            val list = ArrayList<Verse>(40)
            while (cursor.moveToNext()) {
                list.add(
                    Verse(
                        cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3),
                        cursor.getString(4), parseSpans(cursor.getString(5))
                    )
                )
            }
            list
        }
        synchronized(chapterCache) { chapterCache[key] = chapterVerses }
        return chapterVerses
    }

    fun verse(book: Int, chapter: Int, verse: Int): Verse? =
        verses(book, chapter).firstOrNull { it.number == verse }

    fun verseRange(book: Int, chapter: Int, from: Int, to: Int): List<Verse> =
        verses(book, chapter).filter { it.number in from..maxOf(from, to) }

    fun notes(book: Int, chapter: Int): Map<Int, List<Note>> {
        val notesByVerse = HashMap<Int, MutableList<Note>>()
        db.rawQuery(
            "SELECT id,book,chapter,verse,number,catchword,text,spans FROM notes " +
                "WHERE book=? AND chapter=? ORDER BY number",
            arrayOf(book.toString(), chapter.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val note = Note(
                    cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3),
                    cursor.getInt(4), cursor.getString(5) ?: "", cursor.getString(6),
                    parseSpans(cursor.getString(7))
                )
                notesByVerse.getOrPut(note.verse) { ArrayList() }.add(note)
            }
        }
        return notesByVerse
    }

    private val titleCache = HashMap<Int, Map<Int, ChapterTitle>>()

    // Stored as "title\nsubtitle".
    fun chapterTitles(book: Int): Map<Int, ChapterTitle> = synchronized(titleCache) {
        titleCache.getOrPut(book) {
            val titles = HashMap<Int, ChapterTitle>()
            db.rawQuery(
                "SELECT chapter,title FROM chapters WHERE book=? AND title<>''",
                arrayOf(book.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val lines = cursor.getString(1).split("\n")
                    titles[cursor.getInt(0)] =
                        ChapterTitle(lines[0].trim(), lines.getOrElse(1) { "" }.trim())
                }
            }
            titles
        }
    }

    fun chapterTitle(book: Int, chapter: Int): ChapterTitle? = chapterTitles(book)[chapter]

    // The chapter's own title if it has one, otherwise "Genesis 1".
    fun chapterHeading(book: Int, chapter: Int): String =
        chapterTitle(book, chapter)?.title?.ifBlank { null } ?: "${book(book).name} $chapter"

    fun citations(book: Int, chapter: Int, verse: Int): List<Citation> =
        db.rawQuery(
            "SELECT notes.book,notes.chapter,notes.verse,notes.number,notes.catchword,notes.text," +
                "notes.spans " +
                "FROM xref JOIN notes ON notes.id=xref.note_id " +
                "WHERE xref.target_book=? AND xref.target_chapter=? AND " +
                "(xref.target_verse=? OR (xref.target_verse<=? AND xref.target_end_verse>=?)) " +
                "ORDER BY notes.book,notes.chapter,notes.verse LIMIT 300",
            arrayOf(
                book.toString(), chapter.toString(),
                verse.toString(), verse.toString(), verse.toString(),
            )
        ).use { cursor ->
            val list = ArrayList<Citation>()
            val seen = HashSet<Long>()
            while (cursor.moveToNext()) {
                val verseKey =
                    cursor.getInt(0) * 1_000_000L + cursor.getInt(1) * 1000L + cursor.getInt(2)
                if (!seen.add(verseKey * 1000 + cursor.getInt(3))) continue
                list.add(
                    Citation(
                        cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3),
                        cursor.getString(4) ?: "", cursor.getString(5),
                        parseSpans(cursor.getString(6)),
                    )
                )
            }
            list
        }

    private fun postings(table: String, term: String, prefix: Boolean): IntArray? {
        val sql: String
        val args: Array<String>
        if (prefix) {
            sql = "SELECT docs FROM $table WHERE term>=? AND term<? " +
                "ORDER BY doc_count DESC LIMIT 60"
            args = arrayOf(term, term + '￿')
        } else {
            sql = "SELECT docs FROM $table WHERE term=?"
            args = arrayOf(term)
        }
        var merged: IntArray? = null
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val ids = decode(cursor.getBlob(0))
                merged = if (merged == null) ids else union(merged!!, ids)
            }
        }
        return merged
    }

    // Delta-encoded varints -> ascending ids.
    private fun decode(blob: ByteArray): IntArray {
        val ids = IntArray(blob.size)
        var count = 0
        var i = 0
        var prev = 0
        while (i < blob.size) {
            var shift = 0
            var delta = 0
            while (true) {
                val byte = blob[i++].toInt() and 0xFF
                delta = delta or ((byte and 0x7F) shl shift)
                if (byte < 0x80) break
                shift += 7
            }
            prev += delta
            ids[count++] = prev
        }
        return ids.copyOf(count)
    }

    private fun union(left: IntArray, right: IntArray): IntArray {
        val result = IntArray(left.size + right.size)
        var i = 0; var j = 0; var count = 0
        while (i < left.size && j < right.size) {
            when {
                left[i] < right[j] -> result[count++] = left[i++]
                left[i] > right[j] -> result[count++] = right[j++]
                else -> { result[count++] = left[i++]; j++ }
            }
        }
        while (i < left.size) result[count++] = left[i++]
        while (j < right.size) result[count++] = right[j++]
        return result.copyOf(count)
    }

    private fun intersect(left: IntArray, right: IntArray): IntArray {
        val result = IntArray(minOf(left.size, right.size))
        var i = 0; var j = 0; var count = 0
        while (i < left.size && j < right.size) {
            when {
                left[i] < right[j] -> i++
                left[i] > right[j] -> j++
                else -> { result[count++] = left[i]; i++; j++ }
            }
        }
        return result.copyOf(count)
    }

    // The last word counts as a prefix, so results keep up with typing.
    fun search(
        query: String, inNotes: Boolean, limit: Int = 400,
        bookFilter: Int = 0, testament: String = "",
    ): List<Hit> {
        val normalizedQuery = normalize(query).trim()
        if (normalizedQuery.length < 2) return emptyList()
        val terms = WORD_PATTERN.findAll(normalizedQuery).map { it.value }.toList()
        if (terms.isEmpty()) return emptyList()
        val openEnd = !normalizedQuery.last().isWhitespace()
        val table = if (inNotes) "word_index_notes" else "word_index_verses"

        var ids: IntArray? = null
        terms.forEachIndexed { i, term ->
            val prefix = openEnd && i == terms.lastIndex && term.length >= 2
            val termIds = postings(table, term, prefix) ?: return emptyList()
            ids = if (ids == null) termIds else intersect(ids!!, termIds)
            if (ids!!.isEmpty()) return emptyList()
        }
        val allIds = ids ?: return emptyList()

        val phrase = terms.joinToString(" ")
        val wantPhrase = terms.size > 1

        val hits = ArrayList<Hit>(minOf(allIds.size, limit))
        val extra = ArrayList<Hit>()
        val chunkSize = 900
        var chunkStart = 0
        while (chunkStart < allIds.size && hits.size < limit) {
            val slice = allIds.copyOfRange(chunkStart, minOf(chunkStart + chunkSize, allIds.size))
            chunkStart += chunkSize
            val inClause = slice.joinToString(",")
            val sql = if (inNotes)
                "SELECT book,chapter,verse,text,spans,number,catchword FROM notes " +
                    "WHERE id IN ($inClause) ORDER BY id"
            else
                "SELECT book,chapter,verse,text,spans FROM verses WHERE id IN ($inClause) ORDER BY id"
            db.rawQuery(sql, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val bookNumber = cursor.getInt(0)
                    if (bookFilter != 0 && bookNumber != bookFilter) continue
                    if (testament.isNotEmpty() && book(bookNumber).testament != testament) continue
                    val text = cursor.getString(3)
                    val spans = parseSpans(cursor.getString(4))
                    val hit = if (inNotes)
                        Hit(
                            bookNumber, cursor.getInt(1), cursor.getInt(2), text, spans,
                            cursor.getInt(5), cursor.getString(6) ?: "",
                        )
                    else Hit(bookNumber, cursor.getInt(1), cursor.getInt(2), text, spans)
                    if (wantPhrase && !normalize(text).contains(phrase)) {
                        if (extra.size < limit) extra.add(hit)
                    } else if (hits.size < limit) hits.add(hit)
                }
            }
        }
        if (hits.size < limit) hits.addAll(extra.take(limit - hits.size))
        return hits
    }

    private fun compact(text: String) = normalize(text).replace(NON_ALNUM, "")

    fun bookPart(input: String): String {
        val normalized = normalize(input).trim()
        val lastLetter = normalized.indexOfLast { it in 'a'..'z' }
        return if (lastLetter < 0) "" else normalized.substring(0, lastLetter + 1).trim()
    }

    fun bookCandidates(input: String): List<Book> {
        val wanted = compact(input)
        if (wanted.isEmpty()) return emptyList()
        fun mainNames(book: Book) =
            listOf(compact(book.name), compact(book.abbreviation), compact(book.code))
        books.firstOrNull { book -> mainNames(book).any { it == wanted } }?.let { return listOf(it) }
        val prefixMatches = books.filter { book -> mainNames(book).any { it.startsWith(wanted) } }
        if (prefixMatches.isNotEmpty()) return prefixMatches
        if (wanted.length >= 2) {
            val viaAliases = books.filter { book ->
                book.aliases.split(' ').any { alias ->
                    alias.length >= 2 && compact(alias).startsWith(wanted)
                }
            }
            if (viaAliases.isNotEmpty()) return viaAliases
        }
        return books.filter { book ->
            mainNames(book).any { it.length >= 3 && wanted.startsWith(it) }
        }
    }

    fun parseReference(input: String): Triple<Int, Int, Int>? {
        val normalized = normalize(input).trim()
        if (normalized.isEmpty()) return null
        val lastLetter = normalized.indexOfLast { it in 'a'..'z' }
        if (lastLetter < 0) return null
        val name = normalized.substring(0, lastLetter + 1)
        val rest = normalized.substring(lastLetter + 1)
        val numbers = Regex("\\d+").findAll(rest).map { it.value.toInt() }.toList()
        val bookInfo = bookCandidates(name).minByOrNull { it.name.length } ?: return null
        val chapter = (numbers.getOrNull(0) ?: 1).coerceIn(1, bookInfo.chapterCount)
        val verse = numbers.getOrNull(1) ?: 0
        return Triple(bookInfo.number, chapter, verse)
    }
}

private val NON_ALNUM = Regex("[^a-z0-9]")
