package nl.statenbijbel.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean

class Span(val kind: Char, val start: Int, val end: Int, val value: String)

class Book(
    val b: Int, val code: String, val name: String, val abbr: String,
    val testament: String, val title: String, val chapters: Int,
    val verses: Int, val notes: Int, val alt: String, val category: String,
) {
    val searchText: String = normalize("$name $abbr $alt $code")

    val isBible: Boolean get() = category == "bijbel"

    val unit: String
        get() = when {
            category == "bijbel" || category == "psalm" -> "vers"
            code == "HCA" -> "vraag"
            code == "DLR" -> "artikel"
            else -> "gedeelte"
        }

    val noteName: String get() = if (isBible) "kanttekening" else "bewijsplaats"
    val noteNamePlural: String get() = if (isBible) "kanttekeningen" else "bewijsplaatsen"
    val noteShort: String get() = if (isBible) "kantt." else "bewijspl."
}

class Verse(
    val vid: Int, val b: Int, val c: Int, val v: Int,
    val text: String, val spans: List<Span>,
)

class Note(
    val nid: Int, val b: Int, val c: Int, val v: Int, val n: Int,
    val cw: String, val text: String, val spans: List<Span>,
)

class Ref(val b: Int, val c: Int, val v: Int, val end: Int) {
    companion object {
        fun parse(s: String): Ref? {
            val p = s.split('.')
            if (p.size < 3) return null
            return try {
                Ref(p[0].toInt(), p[1].toInt(), p[2].toInt(),
                    if (p.size > 3) p[3].toInt() else 0)
            } catch (e: NumberFormatException) { null }
        }
    }
}

class Hit(
    val b: Int, val c: Int, val v: Int, val text: String,
    val noteNo: Int = 0, val catchWord: String = "",
)

class Citation(val b: Int, val c: Int, val v: Int, val n: Int, val cw: String, val text: String)

fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase()

private val MARKS = Regex("\\p{Mn}+")
private val WORDS = Regex("[a-z0-9]+")

private fun parseSpans(s: String?): List<Span> {
    if (s.isNullOrEmpty()) return emptyList()
    val out = ArrayList<Span>(8)
    for (part in s.split('|')) {
        if (part.isEmpty()) continue
        val a = part.indexOf(',')
        val b = part.indexOf(',', a + 1)
        val c = part.indexOf(',', b + 1)
        if (a < 0 || b < 0 || c < 0) continue
        out.add(
            Span(
                part[0],
                part.substring(a + 1, b).toInt(),
                part.substring(b + 1, c).toInt(),
                part.substring(c + 1),
            )
        )
    }
    return out
}

object Bible {
    const val DB_ASSET = "bijbel.db"
    const val DB_VERSION = 4

    private lateinit var db: SQLiteDatabase
    private val ready = AtomicBoolean(false)

    lateinit var books: List<Book>
        private set
    private lateinit var byNum: Map<Int, Book>

    // Re-extract on every app update, otherwise an old text would linger.
    fun open(ctx: Context) {
        if (ready.get()) return
        val lastUpdate = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        val sp = ctx.getSharedPreferences("statenbijbel", Context.MODE_PRIVATE)
        val prevStamp = sp.getLong("db_stempel", -1L)
        val target = File(ctx.filesDir, "bijbel-v$DB_VERSION.db")
        if (!target.exists() || target.length() < 1_000_000 || prevStamp != lastUpdate) {
            ctx.filesDir.listFiles { f -> f.name.startsWith("bijbel-v") }
                ?.forEach { it.delete() }
            val tmp = File(ctx.filesDir, "bijbel.tmp")
            ctx.assets.open(DB_ASSET).use { input ->
                tmp.outputStream().use { out -> input.copyTo(out, 1 shl 16) }
            }
            tmp.renameTo(target)
            sp.edit().putLong("db_stempel", lastUpdate).apply()
        }
        db = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
        books = db.rawQuery(
            "SELECT b,code,name,abbr,testament,title,chapters,verses,notes,alt," +
                "soort FROM books ORDER BY b", null
        ).use { c ->
            val l = ArrayList<Book>(66)
            while (c.moveToNext()) {
                l.add(
                    Book(
                        c.getInt(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getString(5) ?: "", c.getInt(6),
                        c.getInt(7), c.getInt(8), c.getString(9) ?: "",
                        c.getString(10) ?: "bijbel"
                    )
                )
            }
            l
        }
        byNum = books.associateBy { it.b }
        ready.set(true)
    }

    fun book(b: Int): Book = byNum[b] ?: books[0]
    fun bookOrNull(b: Int): Book? = byNum[b]

    fun ref(b: Int, c: Int, v: Int, end: Int = 0): String {
        val bk = byNum[b] ?: return ""
        // "Joh. 3:16", but "Ruth 1:1", "HC 1:1" and "Ps. ber. 23:1".
        val dot = if (!bk.abbr.endsWith(".") && !bk.abbr.equals(bk.name, true) &&
            bk.name.startsWith(bk.abbr, ignoreCase = true)
        ) "." else ""
        return buildString {
            append(bk.abbr); append(dot); append(' '); append(c)
            if (v > 0) { append(':'); append(v) }
            if (end > v) { append('-'); append(end) }
        }
    }

    private val chapterCache = object : LinkedHashMap<Long, List<Verse>>(16, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Long, List<Verse>>) = size > 12
    }

    fun verses(b: Int, c: Int): List<Verse> {
        val key = b * 1000L + c
        synchronized(chapterCache) { chapterCache[key] }?.let { return it }
        val out = db.rawQuery(
            "SELECT vid,b,c,v,text,spans FROM verses WHERE b=? AND c=? ORDER BY v",
            arrayOf(b.toString(), c.toString())
        ).use { cur ->
            val l = ArrayList<Verse>(40)
            while (cur.moveToNext()) {
                l.add(
                    Verse(
                        cur.getInt(0), cur.getInt(1), cur.getInt(2), cur.getInt(3),
                        cur.getString(4), parseSpans(cur.getString(5))
                    )
                )
            }
            l
        }
        synchronized(chapterCache) { chapterCache[key] = out }
        return out
    }

    fun verse(b: Int, c: Int, v: Int): Verse? =
        verses(b, c).firstOrNull { it.v == v }

    fun verseRange(b: Int, c: Int, from: Int, to: Int): List<Verse> =
        verses(b, c).filter { it.v in from..maxOf(from, to) }

    fun notes(b: Int, c: Int): Map<Int, List<Note>> {
        val out = HashMap<Int, MutableList<Note>>()
        db.rawQuery(
            "SELECT nid,b,c,v,n,cw,text,spans FROM notes WHERE b=? AND c=? ORDER BY n",
            arrayOf(b.toString(), c.toString())
        ).use { cur ->
            while (cur.moveToNext()) {
                val n = Note(
                    cur.getInt(0), cur.getInt(1), cur.getInt(2), cur.getInt(3),
                    cur.getInt(4), cur.getString(5) ?: "", cur.getString(6),
                    parseSpans(cur.getString(7))
                )
                out.getOrPut(n.v) { ArrayList() }.add(n)
            }
        }
        return out
    }

    private val titleCache = HashMap<Int, Map<Int, String>>()

    fun chapterTitles(b: Int): Map<Int, String> = synchronized(titleCache) {
        titleCache.getOrPut(b) {
            val m = HashMap<Int, String>()
            db.rawQuery(
                "SELECT c,titel FROM chapters WHERE b=? AND titel<>''",
                arrayOf(b.toString())
            ).use { while (it.moveToNext()) m[it.getInt(0)] = it.getString(1) }
            m
        }
    }

    fun chapterTitle(b: Int, c: Int): String? = chapterTitles(b)[c]

    fun citations(b: Int, c: Int, v: Int): List<Citation> =
        db.rawQuery(
            "SELECT n.b,n.c,n.v,n.n,n.cw,n.text FROM xref x JOIN notes n ON n.nid=x.nid " +
                "WHERE x.tb=? AND x.tc=? AND (x.tv=? OR (x.tv<=? AND x.tend>=?)) " +
                "ORDER BY n.b,n.c,n.v LIMIT 300",
            arrayOf(b.toString(), c.toString(), v.toString(), v.toString(), v.toString())
        ).use { cur ->
            val l = ArrayList<Citation>()
            val seen = HashSet<Long>()
            while (cur.moveToNext()) {
                val key = cur.getInt(0) * 1_000_000L + cur.getInt(1) * 1000L + cur.getInt(2)
                if (!seen.add(key * 1000 + cur.getInt(3))) continue
                l.add(
                    Citation(
                        cur.getInt(0), cur.getInt(1), cur.getInt(2), cur.getInt(3),
                        cur.getString(4) ?: "", cur.getString(5)
                    )
                )
            }
            l
        }

    private fun postings(table: String, term: String, prefix: Boolean): IntArray? {
        val sql: String
        val args: Array<String>
        if (prefix) {
            sql = "SELECT docs FROM $table WHERE term>=? AND term<? ORDER BY df DESC LIMIT 60"
            args = arrayOf(term, term + '￿')
        } else {
            sql = "SELECT docs FROM $table WHERE term=?"
            args = arrayOf(term)
        }
        var acc: IntArray? = null
        db.rawQuery(sql, args).use { cur ->
            while (cur.moveToNext()) {
                val ids = decode(cur.getBlob(0))
                acc = if (acc == null) ids else union(acc!!, ids)
            }
        }
        return acc
    }

    private fun decode(blob: ByteArray): IntArray {
        val out = IntArray(blob.size)
        var n = 0
        var i = 0
        var prev = 0
        while (i < blob.size) {
            var shift = 0
            var value = 0
            while (true) {
                val byte = blob[i++].toInt() and 0xFF
                value = value or ((byte and 0x7F) shl shift)
                if (byte < 0x80) break
                shift += 7
            }
            prev += value
            out[n++] = prev
        }
        return out.copyOf(n)
    }

    private fun union(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(a.size + b.size)
        var i = 0; var j = 0; var n = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] < b[j] -> out[n++] = a[i++]
                a[i] > b[j] -> out[n++] = b[j++]
                else -> { out[n++] = a[i++]; j++ }
            }
        }
        while (i < a.size) out[n++] = a[i++]
        while (j < b.size) out[n++] = b[j++]
        return out.copyOf(n)
    }

    private fun intersect(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(minOf(a.size, b.size))
        var i = 0; var j = 0; var n = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] < b[j] -> i++
                a[i] > b[j] -> j++
                else -> { out[n++] = a[i]; i++; j++ }
            }
        }
        return out.copyOf(n)
    }

    // The last word counts as a prefix, so results keep up with typing.
    fun search(
        query: String, inNotes: Boolean, limit: Int = 400,
        bookFilter: Int = 0, testament: String = "",
    ): List<Hit> {
        val q = normalize(query).trim()
        if (q.length < 2) return emptyList()
        val terms = WORDS.findAll(q).map { it.value }.toList()
        if (terms.isEmpty()) return emptyList()
        val openEnd = !q.last().isWhitespace()
        val table = if (inNotes) "widx_n" else "widx_v"

        var ids: IntArray? = null
        terms.forEachIndexed { i, t ->
            val prefix = openEnd && i == terms.lastIndex && t.length >= 2
            val p = postings(table, t, prefix) ?: return emptyList()
            ids = if (ids == null) p else intersect(ids!!, p)
            if (ids!!.isEmpty()) return emptyList()
        }
        val all = ids ?: return emptyList()

        val phrase = terms.joinToString(" ")
        val wantPhrase = terms.size > 1

        val hits = ArrayList<Hit>(minOf(all.size, limit))
        val extra = ArrayList<Hit>()
        val chunk = 900
        var index = 0
        while (index < all.size && hits.size < limit) {
            val slice = all.copyOfRange(index, minOf(index + chunk, all.size))
            index += chunk
            val inClause = slice.joinToString(",")
            val sql = if (inNotes)
                "SELECT b,c,v,text,n,cw FROM notes WHERE nid IN ($inClause) ORDER BY nid"
            else
                "SELECT b,c,v,text FROM verses WHERE vid IN ($inClause) ORDER BY vid"
            db.rawQuery(sql, null).use { cur ->
                while (cur.moveToNext()) {
                    val b = cur.getInt(0)
                    if (bookFilter != 0 && b != bookFilter) continue
                    if (testament.isNotEmpty() && book(b).testament != testament) continue
                    val text = cur.getString(3)
                    val hit = if (inNotes)
                        Hit(b, cur.getInt(1), cur.getInt(2), text, cur.getInt(4), cur.getString(5) ?: "")
                    else Hit(b, cur.getInt(1), cur.getInt(2), text)
                    if (wantPhrase && !normalize(text).contains(phrase)) {
                        if (extra.size < limit) extra.add(hit)
                    } else if (hits.size < limit) hits.add(hit)
                }
            }
        }
        if (hits.size < limit) hits.addAll(extra.take(limit - hits.size))
        return hits
    }

    private fun compact(s: String) = normalize(s).replace(NON_ALNUM, "")

    fun bookPart(input: String): String {
        val s = normalize(input).trim()
        val lastLetter = s.indexOfLast { it in 'a'..'z' }
        return if (lastLetter < 0) "" else s.substring(0, lastLetter + 1).trim()
    }

    fun bookCandidates(input: String): List<Book> {
        val q = compact(input)
        if (q.isEmpty()) return emptyList()
        fun mainNames(bk: Book) = listOf(compact(bk.name), compact(bk.abbr), compact(bk.code))
        books.firstOrNull { bk -> mainNames(bk).any { it == q } }?.let { return listOf(it) }
        val prefixMatches = books.filter { bk -> mainNames(bk).any { it.startsWith(q) } }
        if (prefixMatches.isNotEmpty()) return prefixMatches
        if (q.length >= 2) {
            val viaAlt = books.filter { bk ->
                bk.alt.split(' ').any { w -> w.length >= 2 && compact(w).startsWith(q) }
            }
            if (viaAlt.isNotEmpty()) return viaAlt
        }
        return books.filter { bk -> mainNames(bk).any { it.length >= 3 && q.startsWith(it) } }
    }

    fun parseReference(input: String): Triple<Int, Int, Int>? {
        val s = normalize(input).trim()
        if (s.isEmpty()) return null
        val lastLetter = s.indexOfLast { it in 'a'..'z' }
        if (lastLetter < 0) return null
        val name = s.substring(0, lastLetter + 1)
        val rest = s.substring(lastLetter + 1)
        val numbers = Regex("\\d+").findAll(rest).map { it.value.toInt() }.toList()
        val bk = bookCandidates(name).minByOrNull { it.name.length } ?: return null
        val c = (numbers.getOrNull(0) ?: 1).coerceIn(1, bk.chapters)
        val v = numbers.getOrNull(1) ?: 0
        return Triple(bk.b, c, v)
    }
}

private val NON_ALNUM = Regex("[^a-z0-9]")
