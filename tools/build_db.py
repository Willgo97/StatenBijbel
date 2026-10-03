#!/usr/bin/env python3
"""
OSIS file of the Statenvertaling (github.com/Isidore-Guild/statenvertaling)
-> bijbel.db.

The source numbers verses like the KJV; the real SV numbers appear as [03:2] markers
in the text. Verses and references are renumbered from those.

Span types (type,start,end,value):
    i  italic (word added by the translators)
    d  divine name HEERE (small caps)
    a  acrostic letter (Ps. 119, Lamentations, Prov. 31)
    n  note marker, value = number, start==end (insertion point)
    r  reference (only in notes), value = "BOOK.C.V" or ".V-end"
"""
import html
import os
import re
import sqlite3
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import unicodedata
from collections import defaultdict
from xml.etree import ElementTree as ET

import extras_in_db

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NS = "{http://www.bibletechnologies.net/2003/OSIS/namespace}"

# (osisID, canonical code, Dutch name, abbreviation, testament, search aliases)
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

# osisRef uses its own abbreviation scheme, different from osisID
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


class Builder:

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
    """OSIS element -> Builder.  Notes are skipped (handled separately)."""
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
    """Collapse and trim whitespace; span positions shift along."""
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
    """Split an OSIS verse into SV verses: [(sv_c, sv_v, lo, hi), ...]."""
    cuts = list(MARKER.finditer(full))
    if not cuts:
        return [(ochap, overs, 0, len(full))]
    segs = []
    if full[:cuts[0].start()].strip():
        segs.append((ochap, overs, 0, cuts[0].start()))
    for i, m in enumerate(cuts):
        hi = cuts[i + 1].start() if i + 1 < len(cuts) else len(full)
        mc, mv = int(m.group(1)), int(m.group(2))
        # More than one chapter off is a typesetting error (Ps. 84:7 appears as [086:7]).
        if abs(mc - ochap) > 1:
            stats["marker_corrected"] += 1
            mc = ochap
        segs.append((mc, mv, m.end(), hi))
    return segs


def parse_osis_ref(raw):
    """'Pro.8.22-Pro.8.23' -> (book, chapter, verse, end verse) in OSIS numbering."""
    raw = raw.strip()
    first = re.split(r"[-;,]", raw)[0].strip()
    m = re.match(r"^([0-9A-Za-z]+)\.(\d+)(?:\.(\d+))?$", first)
    if not m:
        return None
    num = REF_TO_NUM.get(m.group(1))
    if not num:
        stats["ref_unknown:" + m.group(1)] += 1
        return None
    end = None
    if "-" in raw:
        m2 = re.match(r"^(?:[0-9A-Za-z]+\.\d+\.)?(\d+)$", raw.split("-", 1)[1].strip())
        if m2:
            end = int(m2.group(1))
    return (num, int(m.group(2)), int(m.group(3)) if m.group(3) else 0, end)


NAME = {i + 1: b[2] for i, b in enumerate(BOOKS)}


def ref_label(value):
    """'19.90.2.0' -> 'Ps. 90:2'   ('19.90.0.0' = whole chapter -> 'Ps. 90')."""
    d = value.split(".")
    num, c, v, end = int(d[0]), int(d[1]), int(d[2]), int(d[3])
    abbr = ABBR.get(num, "?")
    dot = "" if abbr.lower() == NAME.get(num, "").lower() else "."
    out = "%s%s\u00a0%d" % (abbr.replace(" ", "\u00a0"), dot, c)
    if v > 0:
        out += ":%d" % v
        if end > v:
            out += "-%d" % end
    return out


def rewrite_refs(text, spans):
    """Replaces 'Psa 90:2' with 'Ps. 90:2' and shifts all spans along."""
    rs = sorted([sp for sp in spans if sp[0] == "r"], key=lambda x: x[1])
    if not rs:
        return text, spans
    pieces = []
    bounds = []          # (old_start, old_end, new_start, new_end, value)
    last = 0
    for kind, s0, e0, val in rs:
        if s0 < last or e0 > len(text):
            continue
        pieces.append(text[last:s0])
        nb = sum(len(x) for x in pieces)
        label = ref_label(val)
        pieces.append(label)
        bounds.append((s0, e0, nb, nb + len(label), val))
        last = e0
    if not bounds:
        return text, spans
    pieces.append(text[last:])
    new_text = "".join(pieces)

    def mp(p):
        d = 0
        for ob, oe, nb, ne, _ in bounds:
            if p >= oe:
                d += (ne - nb) - (oe - ob)
            elif p > ob:
                return nb
        return p + d

    out = [(k, mp(a), mp(b), v) for k, a, b, v in spans if k != "r"]
    out += [("r", nb, ne, val) for _, _, nb, ne, val in bounds]
    return new_text, sorted(out, key=lambda x: (x[1], x[2]))


PUNCT_SPACE = re.compile(r"[ \u00a0]+(?=[.,;:!?])|(?<=[(\[])[ \u00a0]+|[ \u00a0]+(?=[)\]])")


def tidy_punctuation(text, spans):
    """'Zie Gen 1:2 .' -> 'Zie Gen 1:2.'; spans shift along."""
    drop = set()
    for m in PUNCT_SPACE.finditer(text):
        drop.update(range(m.start(), m.end()))
    if not drop:
        return text, spans
    out = []
    index_map = []
    for i, ch in enumerate(text):
        index_map.append(len(out))
        if i not in drop:
            out.append(ch)
    index_map.append(len(out))
    new_text = "".join(out)

    def mp(p):
        return index_map[min(max(p, 0), len(index_map) - 1)]

    new_spans = []
    for k, a, b, val in spans:
        na, nb = mp(a), mp(b)
        if nb > na or k == "n":
            new_spans.append((k, na, nb, val))
    return new_text, new_spans


# In Ps. 119 the letter names are plain text, elsewhere they are <title>.
ACROSTIC = re.compile(
    r"^(Aleph|Beth|Gimel|Daleth|He|Vau|Zain|Cheth|Teth|Jod|Caph|Lamed|Mem|Nun|"
    r"Samech|Ain|Pe|Tsade|Koph|Resch|Schin|Thau)\. ")


def mark_acrostic(bnum, c, text, spans):
    if bnum != 19 or c != 119:
        return spans
    m = ACROSTIC.match(text)
    if not m or any(k == "a" for k, _, _, _ in spans):
        return spans
    return sorted(spans + [("a", 0, len(m.group(1)), "")], key=lambda x: (x[1], x[2]))


WORD_IN_TEXT = re.compile(r"[0-9A-Za-z\u00c0-\u024f]+")


def loose(s):
    """Without accents and double letters: 'Hamaaloth' == 'Hammaaloth'."""
    s = norm(s)
    s = re.sub(r"[^a-z0-9]+", "", s)
    return re.sub(r"(.)\1+", r"\1", s)


def find_loose(text, ntext, needle, cursor):
    target = loose(needle.split()[-1] if needle.split() else needle)
    if len(target) < 3:
        return -1, 0
    best = (-1, 0)
    for m in WORD_IN_TEXT.finditer(ntext):
        if loose(m.group(0)) == target:
            if m.start() >= cursor:
                return m.start(), m.end() - m.start()
            if best[0] < 0:
                best = (m.start(), m.end() - m.start())
    return best


def spans_str(spans):
    return "|".join("%s,%d,%d,%s" % s for s in spans)


def encode(ids):
    """Ascending ids -> delta-varint blob."""
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
    title TEXT, chapters INTEGER, verses INTEGER, notes INTEGER, alt TEXT,
    soort TEXT);
CREATE TABLE chapters(
    b INTEGER, c INTEGER, verses INTEGER, notes INTEGER, titel TEXT,
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
    print("Reading %s ..." % src)
    root = ET.parse(src).getroot()
    text_el = root.find(NS + "osisText")
    divs = {d.get("osisID"): d for d in text_el.findall(NS + "div")
            if d.get("type") == "book"}

    # ---- pass 1: OSIS numbering -> SV numbering (needed for the references)
    print("Pass 1: deriving SV verse numbering ...")
    osis2sv = {}
    for osis_id, code, name, abbr, test, alt in BOOKS:
        bnum = CODE_NUM[code]
        for chap in divs[osis_id].findall(NS + "chapter"):
            oc = int(chap.get("osisID").split(".")[-1])
            for verse in chap.findall(NS + "verse"):
                ov = int(verse.get("osisID").split(".")[-1])
                segs = segments(walk(verse, Builder()).text(), oc, ov)
                osis2sv[(bnum, oc, ov)] = (segs[0][0], segs[0][1])

    # ---- pass 2: text, notes, references, search index
    print("Pass 2: building ...")
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

        raw = {}       # (c,v) -> [text parts]
        rspans = {}    # (c,v) -> spans (positions relative to the joined raw text)
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

                # notes: attach to the right SV segment via the catchword
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

        # ---- write out, chapter by chapter
        bych = defaultdict(list)
        for (c, v) in order:
            bych[c].append(v)
        total_v = total_n = 0
        for c in sorted(bych):
            note_no = 0
            for v in sorted(bych[c]):
                key = (c, v)
                text, spans = collapse("".join(raw[key]), rspans[key])
                text, spans = tidy_punctuation(text, spans)
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
                                p, length = find_loose(text, ntext, needle, cursor)
                                if p >= 0:
                                    needle = ntext[p:p + length]
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
                            rv = 0          # reference to a whole chapter
                        rend = 0
                        if tend:
                            sv_e = osis2sv.get((tnum, tc, tend))
                            rend = sv_e[1] if sv_e else tend
                            if rend <= rv:
                                rend = 0
                        out_spans.append((kind, s, e, "%d.%d.%d.%d" % (tnum, rc, rv, rend)))
                        xref_rows.append((nid, bnum, c, v, tnum, rc, rv, rend))
                    ntxt, out_spans = rewrite_refs(ntxt, out_spans)
                    ntxt, out_spans = tidy_punctuation(ntxt, out_spans)
                    note_rows.append((nid, bnum, c, v, note_no, cw, ntxt,
                                      spans_str(out_spans)))
                    for term in set(WORD.findall(norm(ntxt))):
                        if len(term) > 1:
                            post_n[term].append(nid)

                spans = mark_acrostic(bnum, c, text, spans)
                spans = sorted(spans + markers, key=lambda x: (x[1], x[2]))
                verse_rows.append((vid, bnum, c, v, text, spans_str(spans), kjvmap[key]))
                for term in set(WORD.findall(ntext)):
                    if len(term) > 1:
                        post_v[term].append(vid)
                total_v += 1
            chap_rows.append((bnum, c, len(bych[c]), note_no, ""))
            total_n += note_no

        book_rows.append((bnum, code, name, abbr, test, btitle,
                          len(bych), total_v, total_n, alt, "bijbel"))
        print("  %-4s %-20s %3d ch %5d vs %5d notes" %
              (code, name, len(bych), total_v, total_n))

    # ---- church book: metrical psalms, confessions, forms and prayers
    extras = extras_in_db.load(os.path.join(ROOT, "extras.json"))
    if extras:
        print("\nAdding church book ...")
        book_by_slug = {extras_in_db.slug(b[2]): CODE_NUM[b[1]] for b in BOOKS}
        vid, nid = extras_in_db.add_rows(
            extras, book_by_slug, spans_str, norm, WORD,
            book_rows, chap_rows, verse_rows, note_rows, xref_rows,
            post_v, post_n, vid, nid)
    else:
        print("\n(extras.json missing — Bible text only)")

    db.executemany("INSERT INTO books VALUES(?,?,?,?,?,?,?,?,?,?,?)", book_rows)
    db.executemany("INSERT INTO chapters VALUES(?,?,?,?,?)", chap_rows)
    db.executemany("INSERT INTO verses VALUES(?,?,?,?,?,?,?)", verse_rows)
    db.executemany("INSERT INTO notes VALUES(?,?,?,?,?,?,?,?)", note_rows)
    db.executemany("INSERT INTO xref VALUES(?,?,?,?,?,?,?,?)", xref_rows)

    print("Building search index ...")
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

    print("\nDone: %d verses, %d notes, %d references"
          % (vid, nid, len(xref_rows)))
    print("Catchword placed: %d, at verse end: %d"
          % (stats["cw_raak"], stats["cw_mis"]))
    print("Word index: %d verse terms, %d note terms" % (len(post_v), len(post_n)))
    print("OSIS verses split in two: %d" % shifted)
    odd = {k: v for k, v in stats.items() if k.startswith(("tag:", "ref_unknown:"))}
    if odd:
        print("Anomalies:", odd)
    print("File size: %.1f MB" % (os.path.getsize(dst) / 1e6))


if __name__ == "__main__":
    src = sys.argv[1] if len(sys.argv) > 1 else "STV.xml"
    dst = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "bijbel.db")
    build(src, dst)
