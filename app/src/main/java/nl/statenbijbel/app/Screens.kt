package nl.statenbijbel.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
private fun SchermKop(titel: String, onTerug: () -> Unit, extra: @Composable () -> Unit = {}) {
    val k = LocalLeeskleuren.current
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onTerug) {
                Icon(Icons.Default.ArrowBack, "Terug", tint = k.gedempt)
            }
            Text(
                titel,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                color = k.inkt,
                modifier = Modifier.weight(1f),
            )
            extra()
        }
        HorizontalDivider(color = k.scheiding)
    }
}

@Composable
fun KiesScherm(st: AppState) {
    val k = LocalLeeskleuren.current
    var vraag by remember { mutableStateOf("") }

    if (st.kiesBoek != 0) {
        HoofdstukKiezer(st, st.kiesBoek)
        return
    }

    val treffer = remember(vraag) { if (vraag.isBlank()) null else Bijbel.parseReference(vraag) }
    val boeken = remember(vraag) {
        val deel = Bijbel.boekDeel(vraag)
        if (deel.isEmpty()) Bijbel.books
        else Bijbel.boekKandidaten(deel).ifEmpty {
            Bijbel.books.filter { it.zoekterm.contains(normaliseer(deel)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SchermKop("Boeken", { st.scherm = Scherm.LEZEN })
        OutlinedTextField(
            value = vraag,
            onValueChange = { vraag = it },
            placeholder = { Text("Boek zoeken, of \"joh 3:16\"", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = k.gedempt) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        if (treffer != null && Bijbel.boekDeel(vraag).length >= 2) {
            val (b, c, v) = treffer
            Surface(
                color = k.accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { st.ga(b, c, v) },
            ) {
                Text(
                    "Ga naar ${Bijbel.ref(b, c, v)}",
                    modifier = Modifier.padding(14.dp),
                    color = k.accent,
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
            ).forEach { (code, naam) ->
                val deel = boeken.filter { it.testament == code }
                if (deel.isNotEmpty()) {
                    item(key = "kop$code") {
                        Text(
                            naam,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = k.gedempt,
                            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    items(deel, key = { it.b }) { boek ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { st.kiesBoek = boek.b }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                boek.name,
                                fontFamily = FontFamily.Serif,
                                fontSize = 16.sp,
                                color = k.inkt,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (boek.isBijbel) "${boek.chapters} hfdst."
                                else "${boek.chapters} ${if (boek.chapters == 1) "deel" else "delen"}",
                                fontSize = 12.sp, color = k.gedempt,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HoofdstukKiezer(st: AppState, b: Int) {
    val k = LocalLeeskleuren.current
    val boek = Bijbel.book(b)
    val titels = remember(b) { Bijbel.hoofdstukTitels(b) }

    if (titels.isNotEmpty() && boek.chapters <= 20) {
        Column(Modifier.fillMaxSize()) {
            SchermKop(boek.name, { st.kiesBoek = 0 })
            LazyColumn(
                Modifier
                    .weight(1f)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items((1..boek.chapters).toList()) { c ->
                    val t = titels[c].orEmpty().split("\n")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { st.kiesBoek = 0; st.ga(b, c) }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                    ) {
                        Text(
                            t.firstOrNull().orEmpty().ifBlank { "$c" },
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            color = k.inkt,
                        )
                        if (t.size > 1 && t[1].isNotBlank()) {
                            Text(
                                t[1], fontSize = 12.sp, color = k.gedempt,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = k.scheiding)
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        SchermKop(boek.name, { st.kiesBoek = 0 })
        LazyVerticalGrid(
            columns = GridCells.Adaptive(56.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((1..boek.chapters).toList()) { c ->
                val huidig = st.boek == b && st.hoofdstuk == c
                Surface(
                    color = if (huidig) k.accent.copy(alpha = 0.18f)
                    else if (k.donker) Color(0xFF20242A) else Color(0xFFF2EEE7),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { st.kiesBoek = 0; st.ga(b, c) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "$c",
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            color = if (huidig) k.accent else k.inkt,
                            fontWeight = if (huidig) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoekScherm(st: AppState) {
    val k = LocalLeeskleuren.current
    var vraag by remember { mutableStateOf("") }
    var inKant by remember { mutableStateOf(false) }
    var bereik by remember { mutableStateOf("") }
    var resultaten by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var bezig by remember { mutableStateOf(false) }
    var gezocht by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(vraag, inKant, bereik) {
        if (vraag.trim().length < 2) { resultaten = emptyList(); gezocht = ""; return@LaunchedEffect }
        bezig = true
        delay(180)
        resultaten = withContext(Dispatchers.Default) {
            Bijbel.search(vraag, inKant, testament = bereik)
        }
        gezocht = vraag
        bezig = false
    }

    Column(Modifier.fillMaxSize()) {
        SchermKop("Zoeken", { st.scherm = Scherm.LEZEN })
        OutlinedTextField(
            value = vraag,
            onValueChange = { vraag = it },
            placeholder = {
                Text(
                    if (inKant) "Zoek in de kanttekeningen" else "Zoek in de bijbeltekst",
                    fontSize = 14.sp,
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = k.gedempt) },
            trailingIcon = {
                if (vraag.isNotEmpty()) {
                    IconButton({ vraag = "" }) {
                        Icon(Icons.Default.Close, "Wissen", tint = k.gedempt)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .focusRequester(focus),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip("Bijbeltekst", !inKant) { inKant = false }
            Chip("Kanttekeningen", inKant) { inKant = true }
            Spacer(Modifier.width(8.dp))
            Chip("Alles", bereik == "") { bereik = "" }
            Chip("OT", bereik == "OT") { bereik = "OT" }
            Chip("NT", bereik == "NT") { bereik = "NT" }
        }
        if (gezocht.isNotEmpty()) {
            Text(
                if (resultaten.isEmpty()) "Niets gevonden"
                else "${resultaten.size}${if (resultaten.size >= 400) "+" else ""} resultaten",
                fontSize = 12.sp,
                color = k.gedempt,
                modifier = Modifier.padding(start = 18.dp, top = 10.dp),
            )
        }
        if (bezig && resultaten.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = k.accent)
            }
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(resultaten) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (hit.noteNo > 0)
                                st.toonKanttekening(hit.b, hit.c, hit.v, hit.noteNo)
                            else st.ga(hit.b, hit.c, hit.v)
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Bijbel.ref(hit.b, hit.c, hit.v),
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = k.accent,
                        )
                        if (hit.noteNo > 0) {
                            Text(
                                "  kantt. ${hit.noteNo}" +
                                    (if (hit.catchWord.isNotBlank()) " · ${hit.catchWord}" else ""),
                                fontSize = 12.sp, color = k.gedempt, maxLines = 1,
                            )
                        }
                    }
                    Text(
                        markeerTreffers(hit.text, gezocht, k.accent),
                        fontFamily = if (Prefs.schreef) FontFamily.Serif else FontFamily.SansSerif,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = k.inkt,
                        maxLines = 4,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                HorizontalDivider(color = k.scheiding)
            }
        }
    }
}

fun markeerTreffers(tekst: String, vraag: String, accent: Color): AnnotatedString {
    val alle = Regex("[a-z0-9]+").findAll(normaliseer(vraag)).map { it.value }
        .filter { it.length > 1 }.toList()
    if (alle.isEmpty()) return AnnotatedString(tekst)
    val openEind = vraag.isNotEmpty() && !vraag.last().isWhitespace()
    val exact = if (openEind) alle.dropLast(1).toSet() else alle.toSet()
    val begin = if (openEind) alle.last() else null

    // Per teken normaliseren, zodat posities gelijk blijven aan die in tekst.
    val genorm = CharArray(tekst.length) { normLetter(tekst[it]) }.concatToString()
    val vlaggen = BooleanArray(tekst.length)
    var eerste = -1
    // Hele woorden: "en" mag niet oplichten in "geworden".
    for (m in Regex("[a-z0-9]+").findAll(genorm)) {
        val w = m.value
        if (w in exact || (begin != null && w.startsWith(begin))) {
            if (eerste < 0) eerste = m.range.first
            for (j in m.range) vlaggen[j] = true
        }
    }

    var start = 0
    var eind = tekst.length
    if (eerste > 130) {
        start = (eerste - 60).coerceAtLeast(0)
        while (start > 0 && tekst[start] != ' ') start--
    }
    if (eind - start > 320) {
        eind = (start + 320).coerceAtMost(tekst.length)
        while (eind < tekst.length && tekst[eind] != ' ') eind++
    }
    return buildAnnotatedString {
        if (start > 0) append("… ")
        var i = start
        while (i < eind) {
            val aan = vlaggen[i]
            var j = i
            while (j < eind && vlaggen[j] == aan) j++
            if (aan) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) {
                    append(tekst.substring(i, j))
                }
            } else append(tekst.substring(i, j))
            i = j
        }
        if (eind < tekst.length) append(" …")
    }
}

private fun normLetter(c: Char): Char {
    if (c.code < 128) return c.lowercaseChar()
    val ontleed = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD)
    val basis = ontleed.firstOrNull {
        Character.getType(it) != Character.NON_SPACING_MARK.toInt()
    } ?: c
    return basis.lowercaseChar()
}

@Composable
fun BladwijzerScherm(st: AppState) {
    val k = LocalLeeskleuren.current
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SchermKop("Bewaard", { st.scherm = Scherm.LEZEN })
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
            when (tab) {
                0 -> {
                    if (Prefs.bladwijzers.isEmpty()) item { Leeg("Nog geen bladwijzers.") }
                    items(Prefs.bladwijzers.toList()) { bw ->
                        VersRij(bw.b, bw.c, bw.v, k) { st.ga(bw.b, bw.c, bw.v) }
                    }
                }
                else -> {
                    if (Prefs.geschiedenis.isEmpty()) item { Leeg("Nog geen geschiedenis.") }
                    items(Prefs.geschiedenis.toList()) { sleutel ->
                        val d = sleutel.split('.')
                        val b = d[0].toInt(); val c = d[1].toInt()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { st.ga(b, c) }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.History, null, tint = k.gedempt,
                                modifier = Modifier.size(17.dp),
                            )
                            Text(
                                "  ${Bijbel.book(b).name} $c",
                                fontFamily = FontFamily.Serif, fontSize = 15.sp, color = k.inkt,
                            )
                        }
                        HorizontalDivider(color = k.scheiding)
                    }
                }
            }
        }
    }
}

@Composable
private fun Leeg(tekst: String) {
    val k = LocalLeeskleuren.current
    Text(
        tekst,
        fontSize = 14.sp, color = k.gedempt,
        modifier = Modifier
            .fillMaxWidth()
            .padding(30.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun VersRij(
    b: Int, c: Int, v: Int, k: Leeskleuren, onClick: () -> Unit,
) {
    val vers = remember(b, c, v) { Bijbel.verse(b, c, maxOf(v, 1)) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            Bijbel.ref(b, c, v),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = k.accent,
        )
        Text(
            vers?.text ?: "",
            fontFamily = if (Prefs.schreef) FontFamily.Serif else FontFamily.SansSerif,
            fontSize = 14.sp, lineHeight = 20.sp, color = k.inkt, maxLines = 3,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    HorizontalDivider(color = k.scheiding)
}

@Composable
fun InstellingenScherm(st: AppState) {
    val k = LocalLeeskleuren.current
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        SchermKop("Instellingen", { st.scherm = Scherm.LEZEN })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Kopje("Weergave")
            Regel("Thema") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Thema.entries.forEach { t ->
                        Chip(t.label, Prefs.thema == t) { Prefs.zetThema(t) }
                    }
                }
            }
            AccentKiezer()
            Schuif("Tekstgrootte", Prefs.tekstGrootte, 13, 34) { Prefs.zetGrootte(it) }
            Schuif("Regelafstand", Prefs.regelHoogte, 110, 220) { Prefs.zetRegel(it) }
            Knop("Schreefletter (serif)", Prefs.schreef) { Prefs.zetSchreef(it) }

            Kopje("Kanttekeningen")
            Knop("Toon nummers in de tekst", Prefs.toonKantMarkers) { Prefs.zetMarkers(it) }
            Knop("Compacte versregels", Prefs.doorlopend) { Prefs.zetDoorlopend(it) }

            Kopje("Bladeren")
            Knop("Vegen om van hoofdstuk te wisselen", Prefs.veegNavigatie) {
                Prefs.zetVegen(it)
            }

            Kopje("Overig")
            Knop("Scherm aan laten tijdens lezen", Prefs.schermAan) { Prefs.zetSchermAan(it) }

            Kopje("Over")
            Text(
                "Statenvertaling met de kanttekeningen van de Statenvertalers " +
                    "(editie 1888). 31.171 verzen, 59.385 kanttekeningen en 48.466 " +
                    "onderlinge verwijzingen — volledig offline op dit toestel.\n\n" +
                    "Teksteditie: github.com/Isidore-Guild/statenvertaling (CC0).",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = k.gedempt,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun AccentKiezer() {
    val k = LocalLeeskleuren.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Accentkleur", fontSize = 15.sp, color = k.inkt, modifier = Modifier.weight(1f))
            Text(Prefs.accent.label, fontSize = 13.sp, color = k.gedempt)
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Accent.entries.forEach { a ->
                val gekozen = Prefs.accent == a
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(a.kleur(k.donker))
                        .border(
                            if (gekozen) 3.dp else 1.dp,
                            if (gekozen) k.inkt else k.scheiding,
                            CircleShape,
                        )
                        .clickable { Prefs.zetAccent(a) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (gekozen) {
                        Icon(
                            Icons.Default.Check, a.label,
                            tint = k.papier,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Kopje(tekst: String) {
    val k = LocalLeeskleuren.current
    Text(
        tekst,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = k.accent,
        modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Regel(label: String, inhoud: @Composable () -> Unit) {
    val k = LocalLeeskleuren.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(label, fontSize = 15.sp, color = k.inkt, modifier = Modifier.padding(bottom = 6.dp))
        inhoud()
    }
}

@Composable
private fun Knop(label: String, waarde: Boolean, onWissel: (Boolean) -> Unit) {
    val k = LocalLeeskleuren.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onWissel(!waarde) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = k.inkt, modifier = Modifier.weight(1f))
        Switch(checked = waarde, onCheckedChange = onWissel)
    }
}

@Composable
private fun Schuif(label: String, waarde: Int, min: Int, max: Int, onZet: (Int) -> Unit) {
    val k = LocalLeeskleuren.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
        Row {
            Text(label, fontSize = 15.sp, color = k.inkt, modifier = Modifier.weight(1f))
            Text("$waarde", fontSize = 13.sp, color = k.gedempt)
        }
        Slider(
            value = waarde.toFloat(),
            onValueChange = { onZet(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}
