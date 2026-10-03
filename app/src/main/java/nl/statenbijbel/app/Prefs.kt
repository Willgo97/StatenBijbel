package nl.statenbijbel.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Thema(val label: String) {
    SYSTEEM("Systeem"), LICHT("Licht"), SEPIA("Sepia"), DONKER("Donker"), NACHT("Nacht")
}

class Bladwijzer(val b: Int, val c: Int, val v: Int, val tijd: Long) {
    val sleutel get() = "$b.$c.$v"
}

object Prefs {
    private lateinit var sp: SharedPreferences

    var thema by mutableStateOf(Thema.SYSTEEM)
        private set
    var accent by mutableStateOf(Accent.GOUD)
        private set
    var tekstGrootte by mutableIntStateOf(19)
        private set
    var regelHoogte by mutableIntStateOf(150) // procent
    var schreef by mutableStateOf(true)
        private set
    var toonKantMarkers by mutableStateOf(true)
        private set
    var doorlopend by mutableStateOf(false)
        private set
    var schermAan by mutableStateOf(false)
        private set
    var veegNavigatie by mutableStateOf(false)
        private set

    var boek by mutableIntStateOf(1)
    var hoofdstuk by mutableIntStateOf(1)
    var vers by mutableIntStateOf(0)

    val bladwijzers = mutableStateListOf<Bladwijzer>()
    val geschiedenis = mutableStateListOf<String>()

    fun load(ctx: Context) {
        sp = ctx.getSharedPreferences("statenbijbel", Context.MODE_PRIVATE)
        thema = runCatching { Thema.valueOf(sp.getString("thema", "SYSTEEM")!!) }
            .getOrDefault(Thema.SYSTEEM)
        accent = runCatching { Accent.valueOf(sp.getString("accent", "GOUD")!!) }
            .getOrDefault(Accent.GOUD)
        tekstGrootte = sp.getInt("grootte", 19)
        regelHoogte = sp.getInt("regel", 150)
        schreef = sp.getBoolean("schreef", true)
        toonKantMarkers = sp.getBoolean("markers", true)
        doorlopend = sp.getBoolean("doorlopend", false)
        schermAan = sp.getBoolean("schermaan", false)
        veegNavigatie = sp.getBoolean("vegen", false)
        boek = sp.getInt("boek", 1)
        hoofdstuk = sp.getInt("hoofdstuk", 1)
        vers = sp.getInt("vers", 0)

        bladwijzers.clear()
        sp.getString("bladwijzers", "")!!.split(';').forEach { rij ->
            val d = rij.split(',')
            if (d.size == 4) {
                bladwijzers.add(
                    Bladwijzer(d[0].toInt(), d[1].toInt(), d[2].toInt(), d[3].toLong())
                )
            }
        }
        if (sp.contains("markeringen")) edit { remove("markeringen") }
        geschiedenis.clear()
        sp.getString("geschiedenis", "")!!.split(';').filter { it.isNotBlank() }
            .forEach { geschiedenis.add(it) }
    }

    private fun edit(f: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(f).apply()
    }

    fun zetThema(t: Thema) { thema = t; edit { putString("thema", t.name) } }
    fun zetAccent(a: Accent) { accent = a; edit { putString("accent", a.name) } }
    fun zetGrootte(v: Int) {
        tekstGrootte = v.coerceIn(13, 34); edit { putInt("grootte", tekstGrootte) }
    }
    fun zetRegel(v: Int) {
        regelHoogte = v.coerceIn(110, 220); edit { putInt("regel", regelHoogte) }
    }
    fun zetSchreef(v: Boolean) { schreef = v; edit { putBoolean("schreef", v) } }
    fun zetMarkers(v: Boolean) { toonKantMarkers = v; edit { putBoolean("markers", v) } }
    fun zetDoorlopend(v: Boolean) { doorlopend = v; edit { putBoolean("doorlopend", v) } }
    fun zetSchermAan(v: Boolean) { schermAan = v; edit { putBoolean("schermaan", v) } }
    fun zetVegen(v: Boolean) { veegNavigatie = v; edit { putBoolean("vegen", v) } }

    fun onthoudPlek(b: Int, c: Int, v: Int) {
        boek = b; hoofdstuk = c; vers = v
        edit { putInt("boek", b); putInt("hoofdstuk", c); putInt("vers", v) }
    }

    fun voegGeschiedenisToe(b: Int, c: Int) {
        val sleutel = "$b.$c"
        geschiedenis.remove(sleutel)
        geschiedenis.add(0, sleutel)
        while (geschiedenis.size > 40) geschiedenis.removeAt(geschiedenis.size - 1)
        edit { putString("geschiedenis", geschiedenis.joinToString(";")) }
    }

    fun isBladwijzer(b: Int, c: Int, v: Int) =
        bladwijzers.any { it.b == b && it.c == c && it.v == v }

    fun wisselBladwijzer(b: Int, c: Int, v: Int) {
        val bestaand = bladwijzers.indexOfFirst { it.b == b && it.c == c && it.v == v }
        if (bestaand >= 0) bladwijzers.removeAt(bestaand)
        else bladwijzers.add(0, Bladwijzer(b, c, v, System.currentTimeMillis()))
        bewaarBladwijzers()
    }

    private fun bewaarBladwijzers() = edit {
        putString("bladwijzers",
            bladwijzers.joinToString(";") { "${it.b},${it.c},${it.v},${it.tijd}" })
    }
}
