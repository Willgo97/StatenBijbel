package nl.statenbijbel.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Snelle knoppenbalk voor lettergrootte, thema en weergave. */
@Composable
fun Leesbalk() {
    val k = LocalLeeskleuren.current
    Surface(color = k.papier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aa", fontSize = 13.sp, color = k.gedempt, modifier = Modifier.width(26.dp))
                Slider(
                    value = Prefs.tekstGrootte.toFloat(),
                    onValueChange = { Prefs.zetGrootte(it.toInt()) },
                    valueRange = 13f..34f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${Prefs.tekstGrootte}",
                    fontSize = 12.sp, color = k.gedempt,
                    modifier = Modifier.width(26.dp), textAlign = TextAlign.End,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Thema.entries.forEach { t ->
                    Chip(t.label, Prefs.thema == t) { Prefs.zetThema(t) }
                }
                Chip(if (Prefs.schreef) "Schreef" else "Schreefloos", false) {
                    Prefs.zetSchreef(!Prefs.schreef)
                }
                Chip("Kantt. ${if (Prefs.toonKantMarkers) "aan" else "uit"}",
                    Prefs.toonKantMarkers) { Prefs.zetMarkers(!Prefs.toonKantMarkers) }
            }
            HorizontalDivider(color = k.scheiding)
        }
    }
}

@Composable
fun Chip(label: String, actief: Boolean, onClick: () -> Unit) {
    val k = LocalLeeskleuren.current
    Surface(
        color = if (actief) k.accent.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .border(
                1.dp,
                if (actief) k.accent.copy(alpha = 0.5f) else k.scheiding,
                RoundedCornerShape(50),
            ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 13.sp,
            color = if (actief) k.accent else k.gedempt,
            fontWeight = if (actief) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * De kanttekeningen van één vers, ingevouwen tussen de verzen zelf —
 * zoals in een uitgave met kanttekeningen in de kolom naast de tekst.
 */
@Composable
fun KanttekeningBlok(st: AppState, b: Int, c: Int, v: Int) {
    val k = LocalLeeskleuren.current
    val noten = remember(b, c) { Bijbel.notes(b, c) }[v].orEmpty()
    if (noten.isEmpty()) return
    val opmaak = leesOpmaak()
    var kijk by remember(b, c, v) { mutableStateOf<Ref?>(null) }
    var kijkBij by remember(b, c, v) { mutableIntStateOf(0) }
    val aantalVerw = remember(b, c, v) { Bijbel.citations(b, c, v).size }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = VERSGOOT, top = 4.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (k.donker) Color(0x14FFFFFF) else Color(0x0F8A6431)),
    ) {
    Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
        noten.forEachIndexed { i, noot ->
            val uitgelicht = st.kantN == noot.n
            val tekst = remember(noot.nid, k.donker) { nootTekst(noot, opmaak) }
            var layout by remember(noot.nid) { mutableStateOf<TextLayoutResult?>(null) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (uitgelicht) k.accent.copy(alpha = if (k.donker) 0.22f else 0.15f)
                        else Color.Transparent
                    )
                    .padding(horizontal = 7.dp, vertical = 5.dp),
            ) {
                Text(
                    buildKopregel(noot, k),
                    fontFamily = if (Prefs.schreef) FontFamily.Serif else FontFamily.SansSerif,
                    fontSize = (Prefs.tekstGrootte - 3).sp,
                    lineHeight = ((Prefs.tekstGrootte - 3) * 1.35f).sp,
                    color = k.inkt,
                    // Ruimte voor het sluitkruisje rechtsboven.
                    modifier = if (i == 0) Modifier.padding(end = 18.dp) else Modifier,
                )
                Text(
                    text = tekst,
                    style = TextStyle(
                        fontFamily = if (Prefs.schreef) FontFamily.Serif
                        else FontFamily.SansSerif,
                        fontSize = (Prefs.tekstGrootte - 3).sp,
                        lineHeight = ((Prefs.tekstGrootte - 3) * Prefs.regelHoogte / 100f).sp,
                        color = k.inkt,
                    ),
                    modifier = Modifier.pointerInput(tekst) {
                        detectTapGestures { pos ->
                            val lr = layout ?: return@detectTapGestures
                            val off = lr.getOffsetForPosition(pos)
                            val ann = tekst.getStringAnnotations(TAG_REF, off, off)
                                .firstOrNull()
                            if (ann != null) {
                                val r = Ref.parse(ann.item)
                                if (kijkBij == noot.n && kijk?.let {
                                        it.b == r?.b && it.c == r.c && it.v == r.v
                                    } == true) {
                                    kijk = null
                                } else {
                                    kijk = r
                                    kijkBij = noot.n
                                }
                            }
                        }
                    },
                    onTextLayout = { layout = it },
                )
                if (kijkBij == noot.n) {
                    kijk?.let { r -> VersKijker(st, r) { kijk = null } }
                }
            }
        }
        if (aantalVerw > 0) {
            Row(
                Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { st.verwijzingenVoor = Triple(b, c, v) }
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Link, null, tint = k.accent, modifier = Modifier.size(16.dp))
                Text(
                    if (aantalVerw == 1) "  1 verwijzing hierheen"
                    else "  $aantalVerw verwijzingen hierheen",
                    fontSize = 13.sp, color = k.accent,
                )
            }
        }
    }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { st.wisselKant(b, c, v, st.kantN) }
                .padding(3.dp),
        ) {
            Icon(
                Icons.Default.Close, "Sluiten", tint = k.gedempt,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** Het nummer en het trefwoord als vetgedrukte aanhef van een kanttekening. */
private fun buildKopregel(noot: Note, k: Leeskleuren): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = k.accent, fontWeight = FontWeight.Bold)) {
            append("${noot.n}  ")
        }
        if (noot.cw.isNotBlank()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(noot.cw) }
        }
    }

/** Klein kaartje met de tekst van een aangehaalde plaats. */
@Composable
fun VersKijker(st: AppState, r: Ref, onSluit: () -> Unit) {
    val k = LocalLeeskleuren.current
    val verzen = remember(r.b, r.c, r.v, r.end) {
        if (Bijbel.bookOrNull(r.b) == null) emptyList()
        else Bijbel.verseRange(r.b, r.c, r.v, if (r.end > r.v) r.end else r.v)
    }
    Surface(
        color = if (k.donker) Color(0xFF20242A) else Color(0xFFF3EFE7),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Bijbel.ref(r.b, r.c, r.v, r.end),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = k.accent,
                    modifier = Modifier.weight(1f),
                )
                TextButton({ st.ga(r.b, r.c, r.v) }) {
                    Text("Ga erheen", fontSize = 13.sp)
                }
            }
            if (verzen.isEmpty()) {
                Text("Deze plaats is niet gevonden.", fontSize = 13.sp, color = k.gedempt)
            } else {
                verzen.take(8).forEach { vs ->
                    Text(
                        "${vs.v}  ${vs.text}",
                        fontFamily = if (Prefs.schreef) FontFamily.Serif
                        else FontFamily.SansSerif,
                        fontSize = (Prefs.tekstGrootte - 3).sp,
                        lineHeight = ((Prefs.tekstGrootte - 3) * 1.4f).sp,
                        color = k.inkt,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/** Alle kanttekeningen elders die naar dit vers verwijzen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerwijzingenBlad(st: AppState, b: Int, c: Int, v: Int, onSluit: () -> Unit) {
    val k = LocalLeeskleuren.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lijst = remember(b, c, v) { Bijbel.citations(b, c, v) }

    ModalBottomSheet(
        onDismissRequest = onSluit,
        sheetState = sheetState,
        containerColor = k.papier,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .heightIn(max = 620.dp),
        ) {
            Text(
                "Verwijzingen naar ${Bijbel.ref(b, c, v)}",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = k.inkt,
            )
            Text(
                "${lijst.size} kanttekening(en) elders halen deze plaats aan",
                fontSize = 12.sp, color = k.gedempt,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(lijst) { cit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                st.toonKanttekening(cit.b, cit.c, cit.v, cit.n)
                                onSluit()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Bijbel.ref(cit.b, cit.c, cit.v),
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = k.accent,
                            )
                            Text(
                                "  kantt. ${cit.n}" +
                                    (if (cit.cw.isNotBlank()) " · ${cit.cw}" else ""),
                                fontSize = 12.sp, color = k.gedempt, maxLines = 1,
                            )
                        }
                        Text(
                            cit.text,
                            fontSize = 13.sp,
                            color = k.inkt,
                            maxLines = 3,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    HorizontalDivider(color = k.scheiding)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
