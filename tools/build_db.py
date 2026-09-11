#!/usr/bin/env python3
"""
Bouwt bijbel.db — de complete offline database voor de StatenBijbel-app.

Bron : https://github.com/Isidore-Guild/statenvertaling  (CC0-1.0)
       Statenvertaling 1888, met de kanttekeningen en verwijzingen.

Twee dingen die dit script oplost:

1. Versnummering.  Het OSIS-bestand gebruikt de Engelse (KJV) telling.  De
   Statenvertaling volgt de Hebreeuwse telling: in de Psalmen is het opschrift
   vers 1, en op ~13 plaatsen loopt de telling anders.  De echte SV-nummers
   staan als [03:2]-markeringen in de tekst.  Die worden hier uitgelezen; de
   verzen worden opnieuw ingedeeld en alle 48.000 kruisverwijzingen worden
   meevertaald naar SV-nummering.

2. Opmaak.  In plaats van HTML slaan we platte tekst op plus een compacte
   'spans'-string (type,start,eind,waarde).  De app rendert dat native met
   Compose AnnotatedString — geen WebView, geen HTML-parser.

Spantypes:
    i  cursief (door de vertalers toegevoegd woord)
    d  Godsnaam HEERE (kleinkapitaal)
    a  acrostichon-letter (Ps. 119, Klaagliederen, Spr. 31)
    n  kanttekeningmarkering, waarde = nummer, start==eind (invoegpunt)
    r  verwijzing (alleen in kanttekeningen), waarde = "BOEK.H.V" of ".V-eind"
"""
import html
import os
import re
import sqlite3
import sys
import unicodedata
from collections import defaultdict
from xml.etree import ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NS = "{http://www.bibletechnologies.net/2003/OSIS/namespace}"

# (osisID, canonieke code, Nederlandse naam, afkorting, testament, zoekaliassen)
BOOKS = [
    ("Gen", "GEN", "Genesis", "Gen", "OT", "1 mozes eerste boek van mozes"),
    ("Exod", "EXO", "Exodus", "Ex", "OT", "2 mozes tweede boek van mozes uittocht"),
    ("Lev", "LEV", "Leviticus", "Lev", "OT", "3 mozes derde boek van mozes"),
    ("Num", "NUM", "Numeri", "Num", "OT", "4 mozes vierde boek van mozes"),
    ("Deut", "DEU", "Deuteronomium", "Deut", "OT", "5 mozes vijfde boek van mozes"),
    ("Josh", "JOS", "Jozua", "Joz", "OT", "joshua"),
    ("Judg", "JDG", "Richteren", "Richt", "OT", "rechters judges"),
    ("Ruth", "RUT", "Ruth", "Ruth", "OT", ""),
    ("1Sam", "1SA", "1 Samuël", "1 Sam", "OT", "eerste samuel"),
    ("2Sam", "2SA", "2 Samuël", "2 Sam", "OT", "tweede samuel"),
    ("1Kgs", "1KI", "1 Koningen", "1 Kon", "OT", "eerste koningen"),
    ("2Kgs", "2KI", "2 Koningen", "2 Kon", "OT", "tweede koningen"),
    ("1Chr", "1CH", "1 Kronieken", "1 Kron", "OT", "eerste kronieken"),
    ("2Chr", "2CH", "2 Kronieken", "2 Kron", "OT", "tweede kronieken"),
    ("Ezra", "EZR", "Ezra", "Ezra", "OT", ""),
    ("Neh", "NEH", "Nehemia", "Neh", "OT", ""),
    ("Esth", "EST", "Esther", "Esth", "OT", ""),
    ("Job", "JOB", "Job", "Job", "OT", ""),
    ("Ps", "PSA", "Psalmen", "Ps", "OT", "psalm"),
    ("Prov", "PRO", "Spreuken", "Spr", "OT", "proverbia"),
    ("Eccl", "ECC", "Prediker", "Pred", "OT", "ecclesiastes"),
    ("Song", "SNG", "Hooglied", "Hoogl", "OT", "hooglied van salomo"),
    ("Isa", "ISA", "Jesaja", "Jes", "OT", "isaiah"),
    ("Jer", "JER", "Jeremia", "Jer", "OT", ""),
    ("Lam", "LAM", "Klaagliederen", "Klaagl", "OT", "klaagliederen van jeremia"),
    ("Ezek", "EZK", "Ezechiël", "Ez", "OT", "ezechiel ezekiel"),
    ("Dan", "DAN", "Daniël", "Dan", "OT", "daniel"),
    ("Hos", "HOS", "Hosea", "Hos", "OT", ""),
    ("Joel", "JOL", "Joël", "Joël", "OT", "joel"),
    ("Amos", "AMO", "Amos", "Amos", "OT", ""),
    ("Obad", "OBA", "Obadja", "Obad", "OT", "obadiah"),
    ("Jonah", "JON", "Jona", "Jona", "OT", "jonah"),
    ("Mic", "MIC", "Micha", "Micha", "OT", ""),
    ("Nah", "NAM", "Nahum", "Nah", "OT", ""),
    ("Hab", "HAB", "Habakuk", "Hab", "OT", "habakkuk"),
    ("Zeph", "ZEP", "Zefanja", "Zef", "OT", "sefanja"),
    ("Hag", "HAG", "Haggaï", "Hagg", "OT", "haggai"),
    ("Zech", "ZEC", "Zacharia", "Zach", "OT", "zacharja"),
    ("Mal", "MAL", "Maleachi", "Mal", "OT", ""),
    ("Matt", "MAT", "Mattheüs", "Matth", "NT", "mattheus matteus"),
    ("Mark", "MRK", "Markus", "Mark", "NT", "marcus"),
    ("Luke", "LUK", "Lukas", "Luk", "NT", "lucas"),
    ("John", "JHN", "Johannes", "Joh", "NT", ""),
    ("Acts", "ACT", "Handelingen", "Hand", "NT", "handelingen der apostelen"),
    ("Rom", "ROM", "Romeinen", "Rom", "NT", ""),
    ("1Cor", "1CO", "1 Korinthe", "1 Kor", "NT", "eerste korinthiers corinthe"),
    ("2Cor", "2CO", "2 Korinthe", "2 Kor", "NT", "tweede korinthiers corinthe"),
    ("Gal", "GAL", "Galaten", "Gal", "NT", ""),
    ("Eph", "EPH", "Efeze", "Ef", "NT", "efeziers"),
    ("Phil", "PHP", "Filippenzen", "Filipp", "NT", "philippenzen"),
    ("Col", "COL", "Kolossenzen", "Kol", "NT", "colossenzen"),
    ("1Thess", "1TH", "1 Thessalonicenzen", "1 Thess", "NT", "eerste thessalonicenzen"),
    ("2Thess", "2TH", "2 Thessalonicenzen", "2 Thess", "NT", "tweede thessalonicenzen"),
    ("1Tim", "1TI", "1 Timotheüs", "1 Tim", "NT", "eerste timotheus"),
    ("2Tim", "2TI", "2 Timotheüs", "2 Tim", "NT", "tweede timotheus"),
    ("Titus", "TIT", "Titus", "Tit", "NT", ""),
    ("Phlm", "PHM", "Filemon", "Filem", "NT", "philemon"),
    ("Heb", "HEB", "Hebreeën", "Hebr", "NT", "hebreeen"),
    ("Jas", "JAS", "Jakobus", "Jak", "NT", "jacobus"),
    ("1Pet", "1PE", "1 Petrus", "1 Petr", "NT", "eerste petrus"),
    ("2Pet", "2PE", "2 Petrus", "2 Petr", "NT", "tweede petrus"),
    ("1John", "1JN", "1 Johannes", "1 Joh", "NT", "eerste johannes"),
    ("2John", "2JN", "2 Johannes", "2 Joh", "NT", "tweede johannes"),
    ("3John", "3JN", "3 Johannes", "3 Joh", "NT", "derde johannes"),
    ("Jude", "JUD", "Judas", "Judas", "NT", "jude"),
    ("Rev", "REV", "Openbaring", "Openb", "NT", "openbaring van johannes apocalyps"),
]

BNUM = {b[0]: i + 1 for i, b in enumerate(BOOKS)}          # osisID  -> 1..66
CODE_NUM = {b[1]: i + 1 for i, b in enumerate(BOOKS)}      # 'GEN'   -> 1
ABBR = {i + 1: b[3] for i, b in enumerate(BOOKS)}

# osisRef gebruikt een eigen afkortingsschema, afwijkend van osisID
REF_TO_NUM = {}
for _code, _num in [
    ("Gen", 1), ("Exo", 2), ("Lev", 3), ("Num", 4), ("Deu", 5), ("Jos", 6),
    ("Jdg", 7), ("Rth", 8), ("Rut", 8), ("1Sa", 9), ("2Sa", 10), ("1Ki", 11),
    ("2Ki", 12), ("1Ch", 13), ("2Ch", 14), ("Ezr", 15), ("Neh", 16), ("Est", 17),
    ("Job", 18), ("Psa", 19), ("Pro", 20), ("Ecc", 21), ("Son", 22), ("Isa", 23),
    ("Jer", 24), ("Lam", 25), ("Eze", 26), ("Dan", 27), ("Hos", 28), ("Joe", 29),
    ("Joel", 29), ("Amo", 30), ("Oba", 31), ("Jon", 32), ("Mic", 33), ("Nah", 34),
    ("Hab", 35), ("Zep", 36), ("Hag", 37), ("Zec", 38), ("Zech", 38), ("Mal", 39),
    ("Mat", 40), ("Mar", 41), ("Luk", 42), ("Joh", 43), ("Act", 44), ("Acts", 44),
    ("Rom", 45), ("1Co", 46), ("2Co", 47), ("Gal", 48), ("Eph", 49), ("Phi", 50),
    ("Col", 51), ("1Th", 52), ("2Th", 53), ("1Ti", 54), ("2Ti", 55), ("Tit", 56),
    ("Phm", 57), ("Heb", 58), ("Jam", 59), ("1Pe", 60), ("2Pe", 61), ("1Jo", 62),
    ("2Jo", 63), ("3Jo", 64), ("Jud", 65), ("Rev", 66),
    ("joh", 43), ("ob", 31), ("os", 28),
]:
    REF_TO_NUM[_code] = _num

MARKER = re.compile(r"\[(\d{2,4}):(\d{1,3})\]")
WORD = re.compile(r"[a-z0-9]+")

stats = defaultdict(int)


def norm(s):
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return s.lower()


# --------------------------------------------------------------------------
class Builder:
    """Verzamelt platte tekst plus opmaak-spans (type, start, eind, waarde)."""

    def __init__(self):
        self.buf = []
        self.len = 0
        self.spans = []

    def add(self, s):
        if s:
            self.buf.append(s)
            self.len += len(s)

    def text(self):
        return "".join(self.buf)


def walk(el, b):
    """OSIS-element -> Builder.  Noten worden overgeslagen (apart verwerkt)."""
    if el.text:
        b.add(el.text)
    for child in el:
        tag = child.tag.replace(NS, "")
        start = b.len
        if tag == "note" or tag == "catchWord":
            pass
        elif tag == "w":
            walk(child, b)
        elif tag in ("transChange", "hi"):
            walk(child, b)
            if b.len > start:
                b.spans.append(("i", start, b.len, ""))
        elif tag == "divineName":
            walk(child, b)
            if b.len > start:
                b.spans.append(("d", start, b.len, ""))
        elif tag == "title":
            walk(child, b)
            if b.len > start:
                b.spans.append(("a", start, b.len, ""))
        elif tag == "reference":
            walk(child, b)
            if b.len > start:
                b.spans.append(("r", start, b.len, child.get("osisRef", "")))
        else:
            stats["tag:" + tag] += 1
            walk(child, b)
        if child.tail:
            b.add(child.tail)
    return b


def collapse(raw, spans):
    """Witruimte samentrekken en trimmen; spanposities schuiven mee."""
    out = []
    idx = []
    prev_space = True
    for ch in raw:
        if ch.isspace():
            if prev_space:
                idx.append(len(out))
                continue
            out.append(" ")
            idx.append(len(out) - 1)
            prev_space = True
        else:
            out.append(ch)
            idx.append(len(out) - 1)
            prev_space = False
    idx.append(len(out))
    s = "".join(out)
    lead = len(s) - len(s.lstrip())
    body = s.strip()
    hi = lead + len(body)

    def mp(p):
        q = idx[min(p, len(idx) - 1)]
        return max(0, min(q, hi) - lead)

    return body, [(k, mp(a), mp(b), v) for k, a, b, v in spans]


def segments(full, ochap, overs):
    """Splits een OSIS-vers in SV-verzen: [(sv_c, sv_v, lo, hi), ...]."""
    cuts = list(MARKER.finditer(full))
    if not cuts:
        return [(ochap, overs, 0, len(full))]
    segs = []
    if full[:cuts[0].start()].strip():
        segs.append((ochap, overs, 0, cuts[0].start()))
    for i, m in enumerate(cuts):
        hi = cuts[i + 1].start() if i + 1 < len(cuts) else len(full)
        mc, mv = int(m.group(1)), int(m.group(2))
        # Zetfout in de bron: Ps. 84:7 staat gemarkeerd als [086:7].  Een
        # markering mag hooguit een hoofdstuk van het OSIS-hoofdstuk afwijken
        # (grensverschuiving); verder weg is altijd een typefout.
        if abs(mc - ochap) > 1:
            stats["markering_gecorrigeerd"] += 1
            mc = ochap
        segs.append((mc, mv, m.end(), hi))
    return segs


def parse_osis_ref(raw):
    """'Pro.8.22-Pro.8.23' -> (boeknr, hfd, vers, eindvers) in OSIS-telling."""
    raw = raw.strip()
    first = re.split(r"[-;,]", raw)[0].strip()
    m = re.match(r"^([0-9A-Za-z]+)\.(\d+)(?:\.(\d+))?$", first)
    if not m:
        return None
    num = REF_TO_NUM.get(m.group(1))
    if not num:
        stats["ref_onbekend:" + m.group(1)] += 1
        return None
    end = None
    if "-" in raw:
        m2 = re.match(r"^(?:[0-9A-Za-z]+\.\d+\.)?(\d+)$", raw.split("-", 1)[1].strip())
        if m2:
            end = int(m2.group(1))
    return (num, int(m.group(2)), int(m.group(3)) if m.group(3) else 0, end)


NAAM = {i + 1: b[2] for i, b in enumerate(BOOKS)}


def verwijs_etiket(waarde):
    """'19.90.2.0' -> 'Ps. 90:2'   ('19.90.0.0' = heel hoofdstuk -> 'Ps. 90')."""
    d = waarde.split(".")
    num, c, v, eind = int(d[0]), int(d[1]), int(d[2]), int(d[3])
    afk = ABBR.get(num, "?")
    punt = "" if afk.lower() == NAAM.get(num, "").lower() else "."
    uit = "%s%s\u00a0%d" % (afk.replace(" ", "\u00a0"), punt, c)
    if v > 0:
        uit += ":%d" % v
        if eind > v:
            uit += "-%d" % eind
    return uit


def herschrijf_verwijzingen(text, spans):
    """Vervangt 'Psa 90:2' door 'Ps. 90:2' en schuift alle spans mee."""
    rs = sorted([sp for sp in spans if sp[0] == "r"], key=lambda x: x[1])
    if not rs:
        return text, spans
    stukken = []
    grenzen = []          # (oud_begin, oud_eind, nieuw_begin, nieuw_eind, waarde)
    laatste = 0
    for kind, s0, e0, val in rs:
        if s0 < laatste or e0 > len(text):
            continue
        stukken.append(text[laatste:s0])
        nb = sum(len(x) for x in stukken)
        etiket = verwijs_etiket(val)
        stukken.append(etiket)
        grenzen.append((s0, e0, nb, nb + len(etiket), val))
        laatste = e0
    if not grenzen:
        return text, spans
    stukken.append(text[laatste:])
    nieuwe_tekst = "".join(stukken)

    def mp(p):
        d = 0
        for ob, oe, nb, ne, _ in grenzen:
            if p >= oe:
                d += (ne - nb) - (oe - ob)
            elif p > ob:
                return nb
        return p + d

    uit = [(k, mp(a), mp(b), v) for k, a, b, v in spans if k != "r"]
    uit += [("r", nb, ne, val) for _, _, nb, ne, val in grenzen]
    return nieuwe_tekst, sorted(uit, key=lambda x: (x[1], x[2]))


LEESTEKEN_SPATIE = re.compile(r"[ \u00a0]+(?=[.,;:!?])|(?<=[(\[])[ \u00a0]+|[ \u00a0]+(?=[)\]])")


def net_leestekens(text, spans):
    """De brontranscriptie zet een spatie voor leestekens ("Zie Gen 1:2 .").
    Die halen we weg; de spanposities schuiven mee."""
    weg = set()
    for m in LEESTEKEN_SPATIE.finditer(text):
        weg.update(range(m.start(), m.end()))
    if not weg:
        return text, spans
    uit = []
    kaart = []
    for i, ch in enumerate(text):
        kaart.append(len(uit))
        if i not in weg:
            uit.append(ch)
    kaart.append(len(uit))
    nieuw_text = "".join(uit)

    def mp(p):
        return kaart[min(max(p, 0), len(kaart) - 1)]

    nieuw_spans = []
    for k, a, b, val in spans:
        na, nb = mp(a), mp(b)
        if nb > na or k == "n":
            nieuw_spans.append((k, na, nb, val))
    return nieuw_text, nieuw_spans


# De Hebreeuwse letternamen boven elke strofe van Psalm 119.  In de brontekst
# staan ze daar als gewone tekst; elders (Klaagl., Spr. 31) al als <title>.
ACROSTICHON = re.compile(
    r"^(Aleph|Beth|Gimel|Daleth|He|Vau|Zain|Cheth|Teth|Jod|Caph|Lamed|Mem|Nun|"
    r"Samech|Ain|Pe|Tsade|Koph|Resch|Schin|Thau)\. ")


def merk_acrostichon(bnum, c, text, spans):
    if bnum != 19 or c != 119:
        return spans
    m = ACROSTICHON.match(text)
    if not m or any(k == "a" for k, _, _, _ in spans):
        return spans
    return sorted(spans + [("a", 0, len(m.group(1)), "")], key=lambda x: (x[1], x[2]))


WOORD_IN_TEKST = re.compile(r"[0-9A-Za-z\u00c0-\u024f]+")


def los(s):
    """Ruwe sleutel: zonder accenten en zonder verdubbelde letters.
    Zo valt het trefwoord 'Hamaaloth' samen met de verstekst 'Hammaaloth'."""
    s = norm(s)
    s = re.sub(r"[^a-z0-9]+", "", s)
    return re.sub(r"(.)\1+", r"\1", s)


def zoek_los(text, ntext, needle, cursor):
    """Laatste redmiddel: een woord in het vers met dezelfde ruwe sleutel."""
    doel = los(needle.split()[-1] if needle.split() else needle)
    if len(doel) < 3:
        return -1, 0
    beste = (-1, 0)
    for m in WOORD_IN_TEKST.finditer(ntext):
        if los(m.group(0)) == doel:
            if m.start() >= cursor:
                return m.start(), m.end() - m.start()
            if beste[0] < 0:
                beste = (m.start(), m.end() - m.start())
    return beste


def spans_str(spans):
    return "|".join("%s,%d,%d,%s" % s for s in spans)


def encode(ids):
    """Oplopende id's -> delta-varint blob."""
    out = bytearray()
    prev = 0
    for x in sorted(ids):
        d = x - prev
        prev = x
        while d >= 0x80:
            out.append((d & 0x7F) | 0x80)
            d >>= 7
        out.append(d)
    return bytes(out)


SCHEMA = """
PRAGMA journal_mode=OFF;
PRAGMA synchronous=OFF;
CREATE TABLE books(
    b INTEGER PRIMARY KEY, code TEXT, name TEXT, abbr TEXT, testament TEXT,
    title TEXT, chapters INTEGER, verses INTEGER, notes INTEGER, alt TEXT);
CREATE TABLE chapters(
    b INTEGER, c INTEGER, verses INTEGER, notes INTEGER,
    PRIMARY KEY(b,c)) WITHOUT ROWID;
CREATE TABLE verses(
    vid INTEGER PRIMARY KEY, b INTEGER, c INTEGER, v INTEGER,
    text TEXT, spans TEXT, kjv INTEGER);
CREATE UNIQUE INDEX verses_bcv ON verses(b,c,v);
CREATE TABLE notes(
    nid INTEGER PRIMARY KEY, b INTEGER, c INTEGER, v INTEGER,
    n INTEGER, cw TEXT, text TEXT, spans TEXT);
CREATE INDEX notes_bcv ON notes(b,c,v);
CREATE TABLE xref(
    nid INTEGER, b INTEGER, c INTEGER, v INTEGER,
    tb INTEGER, tc INTEGER, tv INTEGER, tend INTEGER);
CREATE INDEX xref_tgt ON xref(tb,tc,tv);
CREATE INDEX xref_src ON xref(b,c,v);
CREATE TABLE widx_v(term TEXT PRIMARY KEY, df INTEGER, docs BLOB) WITHOUT ROWID;
CREATE TABLE widx_n(term TEXT PRIMARY KEY, df INTEGER, docs BLOB) WITHOUT ROWID;
CREATE TABLE info(k TEXT PRIMARY KEY, v TEXT);
"""


def build(src, dst):
    print("Inlezen %s ..." % src)
    root = ET.parse(src).getroot()
    text_el = root.find(NS + "osisText")
    divs = {d.get("osisID"): d for d in text_el.findall(NS + "div")
            if d.get("type") == "book"}

    # ---- pas 1: OSIS-nummering -> SV-nummering (nodig voor de verwijzingen)
    print("Pas 1: SV-versnummering afleiden ...")
    osis2sv = {}
    for osis_id, code, name, abbr, test, alt in BOOKS:
        bnum = CODE_NUM[code]
        for chap in divs[osis_id].findall(NS + "chapter"):
            oc = int(chap.get("osisID").split(".")[-1])
            for verse in chap.findall(NS + "verse"):
                ov = int(verse.get("osisID").split(".")[-1])
                segs = segments(walk(verse, Builder()).text(), oc, ov)
                osis2sv[(bnum, oc, ov)] = (segs[0][0], segs[0][1])

    # ---- pas 2: tekst, kanttekeningen, verwijzingen, zoekindex
    print("Pas 2: opbouwen ...")
    if os.path.exists(dst):
        os.remove(dst)
    db = sqlite3.connect(dst)
    db.executescript(SCHEMA)

    vid = nid = 0
    verse_rows, note_rows, xref_rows = [], [], []
    book_rows, chap_rows = [], []
    post_v, post_n = defaultdict(list), defaultdict(list)
    shifted = 0

    for osis_id, code, name, abbr, test, alt in BOOKS:
        bnum = CODE_NUM[code]
        div = divs[osis_id]
        btitle = ""
        t = div.find(NS + "title")
        if t is not None and t.get("type") == "main":
            btitle = walk(t, Builder()).text().strip()

        raw = {}       # (c,v) -> [tekstdelen]
        rspans = {}    # (c,v) -> spans (posities t.o.v. samengevoegde ruwe tekst)
        rnotes = defaultdict(list)
        kjvmap = {}
        order = []

        for chap in div.findall(NS + "chapter"):
            oc = int(chap.get("osisID").split(".")[-1])
            for verse in chap.findall(NS + "verse"):
                ov = int(verse.get("osisID").split(".")[-1])
                b = walk(verse, Builder())
                full = b.text()
                segs = segments(full, oc, ov)

                for (sc, sv, lo, hi) in segs:
                    piece = full[lo:hi]
                    if not piece.strip() and (sc, sv) in raw:
                        continue
                    key = (sc, sv)
                    if key not in raw:
                        raw[key] = []
                        rspans[key] = []
                        kjvmap[key] = oc * 1000 + ov
                        order.append(key)
                    base = sum(len(x) for x in raw[key])
                    if base:
                        raw[key].append(" ")
                        base += 1
                    raw[key].append(piece)
                    shift = base - lo
                    for kind, ss, se, val in b.spans:
                        if lo <= ss and se <= hi:
                            rspans[key].append((kind, ss + shift, se + shift, val))

                # kanttekeningen: aan het juiste SV-segment hangen via trefwoord
                seg_keys = []
                for (sc, sv, lo, hi) in segs:
                    if (sc, sv) not in seg_keys:
                        seg_keys.append((sc, sv))
                if len(segs) > 1:
                    shifted += 1
                for note in verse.findall(NS + "note"):
                    cw_el = note.find(NS + "catchWord")
                    cw = (walk(cw_el, Builder()).text().strip().strip(",;:")
                          if cw_el is not None else "")
                    key = seg_keys[0]
                    if cw and len(seg_keys) > 1:
                        first = norm(cw).split()
                        if first:
                            for k in seg_keys:
                                if first[0] in norm("".join(raw.get(k, []))):
                                    key = k
                                    break
                    nb = walk(note, Builder())
                    rnotes[key].append((cw, nb))

        # ---- wegschrijven, hoofdstuk voor hoofdstuk
        bych = defaultdict(list)
        for (c, v) in order:
            bych[c].append(v)
        total_v = total_n = 0
        for c in sorted(bych):
            note_no = 0
            for v in sorted(bych[c]):
                key = (c, v)
                text, spans = collapse("".join(raw[key]), rspans[key])
                text, spans = net_leestekens(text, spans)
                vid += 1
                ntext = norm(text)
                cursor = 0
                markers = []
                for cw, nb in rnotes.get(key, []):
                    note_no += 1
                    nid += 1
                    pos = None
                    if cw:
                        needle = norm(re.sub(r"\s+", " ", cw).strip().strip(",;:. "))
                        if needle:
                            p = ntext.find(needle, cursor)
                            if p < 0:
                                p = ntext.find(needle)
                            if p < 0:
                                parts = needle.split()
                                if parts and len(parts[-1]) > 2:
                                    p = ntext.find(parts[-1], cursor)
                                    if p >= 0:
                                        needle = parts[-1]
                            if p < 0:
                                p, lengte = zoek_los(text, ntext, needle, cursor)
                                if p >= 0:
                                    needle = ntext[p:p + lengte]
                            if p >= 0:
                                pos = p + len(needle)
                                cursor = pos
                                while pos < len(text) and text[pos] in ",.;:!?’'\")]":
                                    pos += 1
                    if pos is None:
                        stats["cw_mis"] += 1
                        pos = len(text)
                    else:
                        stats["cw_raak"] += 1
                    markers.append(("n", pos, pos, str(note_no)))

                    ntxt, nspans = collapse(nb.text(), nb.spans)
                    out_spans = []
                    for kind, s, e, val in nspans:
                        if kind != "r":
                            out_spans.append((kind, s, e, val))
                            continue
                        pr = parse_osis_ref(val)
                        if not pr:
                            continue
                        tnum, tc, tv, tend = pr
                        sv_t = osis2sv.get((tnum, tc, tv if tv else 1))
                        rc, rv = sv_t if sv_t else (tc, tv or 1)
                        if tv == 0:
                            rv = 0          # verwijzing naar een heel hoofdstuk
                        rend = 0
                        if tend:
                            sv_e = osis2sv.get((tnum, tc, tend))
                            rend = sv_e[1] if sv_e else tend
                            if rend <= rv:
                                rend = 0
                        out_spans.append((kind, s, e, "%d.%d.%d.%d" % (tnum, rc, rv, rend)))
                        xref_rows.append((nid, bnum, c, v, tnum, rc, rv, rend))
                    ntxt, out_spans = herschrijf_verwijzingen(ntxt, out_spans)
                    ntxt, out_spans = net_leestekens(ntxt, out_spans)
                    note_rows.append((nid, bnum, c, v, note_no, cw, ntxt,
                                      spans_str(out_spans)))
                    for term in set(WORD.findall(norm(ntxt))):
                        if len(term) > 1:
                            post_n[term].append(nid)

                spans = merk_acrostichon(bnum, c, text, spans)
                spans = sorted(spans + markers, key=lambda x: (x[1], x[2]))
                verse_rows.append((vid, bnum, c, v, text, spans_str(spans), kjvmap[key]))
                for term in set(WORD.findall(ntext)):
                    if len(term) > 1:
                        post_v[term].append(vid)
                total_v += 1
            chap_rows.append((bnum, c, len(bych[c]), note_no))
            total_n += note_no

        book_rows.append((bnum, code, name, abbr, test, btitle,
                          len(bych), total_v, total_n, alt))
        print("  %-4s %-20s %3d hfd %5d vzn %5d kt" %
              (code, name, len(bych), total_v, total_n))

    db.executemany("INSERT INTO books VALUES(?,?,?,?,?,?,?,?,?,?)", book_rows)
    db.executemany("INSERT INTO chapters VALUES(?,?,?,?)", chap_rows)
    db.executemany("INSERT INTO verses VALUES(?,?,?,?,?,?,?)", verse_rows)
    db.executemany("INSERT INTO notes VALUES(?,?,?,?,?,?,?,?)", note_rows)
    db.executemany("INSERT INTO xref VALUES(?,?,?,?,?,?,?,?)", xref_rows)

    print("Zoekindex bouwen ...")
    db.executemany("INSERT INTO widx_v VALUES(?,?,?)",
                   ((t, len(d), encode(d)) for t, d in post_v.items()))
    db.executemany("INSERT INTO widx_n VALUES(?,?,?)",
                   ((t, len(d), encode(d)) for t, d in post_n.items()))
    db.executemany("INSERT INTO info VALUES(?,?)", [
        ("schema", "1"),
        ("vertaling", "Statenvertaling"),
        ("editie", "editie 1888, met kanttekeningen"),
        ("bron", "github.com/Isidore-Guild/statenvertaling (CC0-1.0)"),
        ("verzen", str(vid)), ("kanttekeningen", str(nid)),
    ])
    db.commit()
    db.executescript("VACUUM;")
    db.close()

    print("\nKlaar: %d verzen, %d kanttekeningen, %d verwijzingen"
          % (vid, nid, len(xref_rows)))
    print("Trefwoord geplaatst: %d, aan verseinde: %d"
          % (stats["cw_raak"], stats["cw_mis"]))
    print("Woordindex: %d verstermen, %d kanttermen" % (len(post_v), len(post_n)))
    print("OSIS-verzen die in tweeën gesplitst zijn: %d" % shifted)
    odd = {k: v for k, v in stats.items() if k.startswith(("tag:", "ref_onbekend:"))}
    if odd:
        print("Bijzonderheden:", odd)
    print("Bestandsgrootte: %.1f MB" % (os.path.getsize(dst) / 1e6))


if __name__ == "__main__":
    src = sys.argv[1] if len(sys.argv) > 1 else "STV.xml"
    dst = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "bijbel.db")
    build(src, dst)
