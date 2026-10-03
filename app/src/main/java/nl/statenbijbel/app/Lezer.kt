package nl.statenbijbel.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

val ZIJMARGE = 10.dp
val VERSGOOT = 22.dp

@Composable
fun leesStijl(): TextStyle = TextStyle(
    fontFamily = if (Prefs.schreef) FontFamily.Serif else FontFamily.SansSerif,
    fontSize = Prefs.tekstGrootte.sp,
    lineHeight = (Prefs.tekstGrootte * Prefs.regelHoogte / 100f).sp,
    color = LocalLeeskleuren.current.inkt,
)

private fun annotatieOp(
    lr: TextLayoutResult, tekst: AnnotatedString, pos: Offset, tag: String,
): String? {
    val off = lr.getOffsetForPosition(pos)
    for (o in intArrayOf(off, off - 1)) {
        if (o < 0 || o >= tekst.length) continue
        val ann = tekst.getStringAnnotations(tag, o, o).firstOrNull() ?: continue
        val box = lr.getBoundingBox(o)
        if (pos.x >= box.left - 10f && pos.x <= box.right + 10f &&
            pos.y >= box.top - 8f && pos.y <= box.bottom + 8f
        ) return ann.item
    }
    return null
}

@Composable
fun Lezer(st: AppState) {
    val pager = rememberPagerState(
        initialPage = Index.index(st.boek, st.hoofdstuk)
    ) { Index.aantal }
    var toonLeesbalk by remember { mutableStateOf(false) }

    LaunchedEffect(st.boek, st.hoofdstuk) {
        val doel = Index.index(st.boek, st.hoofdstuk)
        if (pager.currentPage != doel) pager.scrollToPage(doel)
    }
    LaunchedEffect(pager.settledPage) {
        val b = Index.boekVan(pager.settledPage)
        val c = Index.hoofdstukVan(pager.settledPage)
        if (b != st.boek || c != st.hoofdstuk) {
            st.boek = b; st.hoofdstuk = c; st.gekozenVers = 0
            Prefs.onthoudPlek(b, c, 0)
            Prefs.voegGeschiedenisToe(b, c)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Bovenbalk(st, onLeesbalk = { toonLeesbalk = !toonLeesbalk })
        AnimatedVisibility(toonLeesbalk) { Leesbalk() }
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            beyondViewportPageCount = if (Prefs.veegNavigatie) 1 else 0,
            userScrollEnabled = Prefs.veegNavigatie,
            key = { it },
        ) { page ->
            HoofdstukPagina(
                st, Index.boekVan(page), Index.hoofdstukVan(page),
                actief = page == pager.currentPage,
            )
        }
    }

    st.verwijzingenVoor?.let { (b, c, v) ->
        VerwijzingenBlad(st, b, c, v) { st.verwijzingenVoor = null }
    }
}

@Composable
private fun Bovenbalk(st: AppState, onLeesbalk: () -> Unit) {
    val k = LocalLeeskleuren.current
    val boek = Bijbel.book(st.boek)
    Surface(color = k.papier, tonalElevation = 0.dp) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { st.scherm = Scherm.KIEZEN }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val opschrift = remember(st.boek, st.hoofdstuk) {
                        Bijbel.hoofdstukTitel(st.boek, st.hoofdstuk)
                            ?.lineSequence()?.firstOrNull()?.trim()
                    }
                    Text(
                        opschrift ?: "${boek.name} ${st.hoofdstuk}",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                        color = k.inkt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        Icons.Default.ExpandMore, null,
                        tint = k.gedempt,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(19.dp),
                    )
                }
                IconButton(onLeesbalk) {
                    Icon(Icons.Default.FormatSize, "Weergave", tint = k.gedempt)
                }
                IconButton({ st.scherm = Scherm.ZOEKEN }) {
                    Icon(Icons.Default.Search, "Zoeken", tint = k.gedempt)
                }
                IconButton({ st.scherm = Scherm.BLADWIJZERS }) {
                    Icon(Icons.Default.Bookmark, "Bladwijzers", tint = k.gedempt)
                }
                IconButton({ st.scherm = Scherm.INSTELLINGEN }) {
                    Icon(Icons.Default.Settings, "Instellingen", tint = k.gedempt)
                }
            }
            HorizontalDivider(color = k.scheiding)
        }
    }
}

@Composable
private fun HoofdstukPagina(st: AppState, b: Int, c: Int, actief: Boolean) {
    val k = LocalLeeskleuren.current
    val verzen = remember(b, c) { Bijbel.verses(b, c) }
    val noten = remember(b, c) { Bijbel.notes(b, c) }
    val boek = remember(b) { Bijbel.book(b) }
    val stijl = leesStijl()
    val opmaak = leesOpmaak()
    val lijst = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Het aangetikte vers blijft op zijn plek als erboven een blok dichtklapt.
    fun wisselKant(vers: Int, n: Int) {
        val i = verzen.indexOfFirst { it.v == vers } + 1 // +1 voor de kop
        val voor = lijst.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }?.offset
        st.wisselKant(b, c, vers, n)
        if (voor == null) return
        scope.launch {
            withFrameNanos { }
            lijst.scrollToItem(i)
            val na = lijst.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }?.offset
                ?: return@launch
            lijst.scrollBy((na - voor).toFloat())
        }
    }

    LaunchedEffect(actief, st.springNaarVers, b, c) {
        if (actief && st.springNaarVers > 0) {
            val i = verzen.indexOfFirst { it.v == st.springNaarVers }
            if (i >= 0) lijst.scrollToItem(i + 1)
            st.springNaarVers = 0
        }
    }

    LazyColumn(
        state = lijst,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ZIJMARGE, end = ZIJMARGE, top = 10.dp, bottom = 120.dp
        ),
    ) {
        item(key = "kop") {
            val titel = remember(b, c) { Bijbel.hoofdstukTitel(b, c) }
            val regels = titel?.split("\n").orEmpty()
            Column(Modifier.padding(bottom = 14.dp)) {
                val boven = when {
                    !boek.isBijbel -> boek.name
                    c == 1 && boek.title.isNotBlank() -> boek.title
                    else -> ""
                }
                if (boven.isNotBlank()) {
                    Text(
                        boven,
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        color = k.gedempt,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    )
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        regels.firstOrNull()?.trim()?.ifBlank { null } ?: "${boek.name} $c",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 25.sp,
                        color = k.inkt,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                    )
                    val isBlad = Prefs.isBladwijzer(b, c, 0)
                    IconButton(
                        { Prefs.wisselBladwijzer(b, c, 0) },
                        Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            if (isBlad) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            if (isBlad) "Bladwijzer weghalen" else "Bladwijzer zetten",
                            tint = if (isBlad) k.accent else k.gedempt,
                        )
                    }
                }
                if (regels.size > 1 && regels[1].isNotBlank()) {
                    Text(
                        regels[1].trim(),
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        color = k.gedempt,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
        items(verzen, key = { it.vid }) { v ->
            VersRegel(st, v, noten[v.v].orEmpty(), stijl, opmaak, ::wisselKant)
        }
        item(key = "voet") {
            Voetregel(st, b, c)
        }
    }
}

@Composable
private fun VersRegel(
    st: AppState, v: Verse, noten: List<Note>, stijl: TextStyle, opmaak: Opmaak,
    wisselKant: (vers: Int, n: Int) -> Unit,
) {
    val k = LocalLeeskleuren.current
    val tekst = remember(v.vid, Prefs.toonKantMarkers, k.donker) {
        versTekst(v, opmaak, Prefs.toonKantMarkers)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val gekozen = st.gekozenVers == v.v
    val kantOpen = st.kantOpen(v.b, v.c, v.v)
    val achtergrond = if (gekozen) k.selectie else Color.Transparent

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(achtergrond)
                .padding(vertical = if (Prefs.doorlopend) 1.dp else 4.dp),
        ) {
            Text(
                v.v.toString(),
                modifier = Modifier
                    .width(VERSGOOT)
                    .padding(top = 3.dp, end = 5.dp),
                textAlign = TextAlign.End,
                fontSize = (Prefs.tekstGrootte * 0.62f).sp,
                color = if (kantOpen) k.accent else k.versnummer,
                fontFamily = FontFamily.SansSerif,
            )
            Text(
                text = tekst,
                style = stijl,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(tekst, noten.size) {
                        detectTapGestures { pos ->
                            st.gekozenVers = 0
                            val lr = layout
                            val kt = lr?.let { annotatieOp(it, tekst, pos, TAG_KT) }
                            when {
                                kt != null -> wisselKant(v.v, kt.toIntOrNull() ?: 0)
                                noten.isNotEmpty() -> wisselKant(v.v, 0)
                                Bijbel.citations(v.b, v.c, v.v).isNotEmpty() ->
                                    st.verwijzingenVoor = Triple(v.b, v.c, v.v)
                            }
                        }
                    },
                onTextLayout = { layout = it },
            )
        }
        if (kantOpen) KanttekeningBlok(st, v.b, v.c, v.v)
    }
}

@Composable
private fun Voetregel(st: AppState, b: Int, c: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (Index.index(b, c) > 0) {
            TextButton({
                val i = Index.index(b, c) - 1
                st.ga(Index.boekVan(i), Index.hoofdstukVan(i))
            }) {
                Icon(Icons.Default.ArrowBack, null, Modifier.size(17.dp))
                Text("  vorige", fontSize = 14.sp)
            }
        } else Spacer(Modifier.width(1.dp))
        if (Index.index(b, c) < Index.aantal - 1) {
            TextButton({
                val i = Index.index(b, c) + 1
                st.ga(Index.boekVan(i), Index.hoofdstukVan(i))
            }) {
                Text("volgende  ", fontSize = 14.sp)
                Icon(Icons.Default.ArrowForward, null, Modifier.size(17.dp))
            }
        } else Spacer(Modifier.width(1.dp))
    }
}
