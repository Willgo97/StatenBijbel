package nl.statenbijbel.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Scherm { LEZEN, KIEZEN, ZOEKEN, BLADWIJZERS, INSTELLINGEN }

/** Alle hoofdstukken van de bijbel achter elkaar, voor het doorbladeren. */
object Index {
    lateinit var paren: List<Pair<Int, Int>>
        private set
    private lateinit var begin: IntArray

    fun bouw() {
        val l = ArrayList<Pair<Int, Int>>(1200)
        val b = IntArray(Bijbel.books.size + 2)
        Bijbel.books.forEach { boek ->
            b[boek.b] = l.size
            for (c in 1..boek.chapters) l.add(boek.b to c)
        }
        paren = l
        begin = b
    }

    fun index(b: Int, c: Int): Int = (begin.getOrNull(b) ?: 0) + (c - 1).coerceAtLeast(0)
    fun boekVan(i: Int) = paren[i.coerceIn(0, paren.size - 1)].first
    fun hoofdstukVan(i: Int) = paren[i.coerceIn(0, paren.size - 1)].second
    val aantal get() = paren.size
}

class AppState {
    var scherm by mutableStateOf(Scherm.LEZEN)
    var boek by mutableIntStateOf(1)
    var hoofdstuk by mutableIntStateOf(1)
    var springNaarVers by mutableIntStateOf(0)
    var gekozenVers by mutableIntStateOf(0)

    /** Vers waarvan de kanttekeningen tussen de tekst openstaan. */
    var kantB by mutableIntStateOf(0)
    var kantC by mutableIntStateOf(0)
    var kantV by mutableIntStateOf(0)
    var kantN by mutableIntStateOf(0)

    var verwijzingenVoor by mutableStateOf<Triple<Int, Int, Int>?>(null)
    var kiesBoek by mutableIntStateOf(0)

    fun kantOpen(b: Int, c: Int, v: Int) = kantB == b && kantC == c && kantV == v

    fun wisselKant(b: Int, c: Int, v: Int, n: Int = 0) {
        if (kantOpen(b, c, v) && (n == 0 || n == kantN)) {
            kantB = 0; kantC = 0; kantV = 0; kantN = 0
        } else {
            kantB = b; kantC = c; kantV = v; kantN = n
        }
    }

    fun toonKanttekening(b: Int, c: Int, v: Int, n: Int) {
        ga(b, c, v)
        kantB = b; kantC = c; kantV = v; kantN = n
    }

    fun ga(b: Int, c: Int, v: Int = 0) {
        boek = b
        hoofdstuk = c
        springNaarVers = v
        gekozenVers = v
        scherm = Scherm.LEZEN
        Prefs.onthoudPlek(b, c, v)
        Prefs.voegGeschiedenisToe(b, c)
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
    var klaar by remember { mutableStateOf(false) }
    var fout by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                Prefs.load(ctx)
                Bijbel.open(ctx)
                Index.bouw()
            } catch (e: Throwable) {
                fout = e.message ?: e.toString()
            }
        }
        klaar = true
    }

    if (!klaar || fout != null) {
        Surface(color = androidx.compose.ui.graphics.Color(0xFFFBF7F0)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "StatenBijbel",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF2F4858),
                    )
                    Text(
                        fout ?: "de tekst wordt klaargezet…",
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF7A7168),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (fout == null) {
                        CircularProgressIndicator(
                            Modifier.padding(top = 22.dp),
                            color = androidx.compose.ui.graphics.Color(0xFF8A6431),
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
                // Ook een plek in het kerkboek moet hersteld kunnen worden.
                boek = if (Bijbel.bookOrNull(Prefs.boek) != null) Prefs.boek else 1
                hoofdstuk = Prefs.hoofdstuk
                springNaarVers = Prefs.vers
            }
        }
        LaunchedEffect(Prefs.schermAan) {
            if (Prefs.schermAan)
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        Surface(
            Modifier.fillMaxSize(),
            color = LocalLeeskleuren.current.papier,
        ) {
            when (st.scherm) {
                Scherm.LEZEN -> Lezer(st)
                Scherm.KIEZEN -> KiesScherm(st)
                Scherm.ZOEKEN -> ZoekScherm(st)
                Scherm.BLADWIJZERS -> BladwijzerScherm(st)
                Scherm.INSTELLINGEN -> InstellingenScherm(st)
            }
        }

        if (st.scherm != Scherm.LEZEN) {
            BackHandler { st.scherm = Scherm.LEZEN }
        }
    }
}
