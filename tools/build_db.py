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

BOOK_NUMBER_BY_OSIS_ID = {book[0]: i + 1 for i, book in enumerate(BOOKS)}   # osisID  -> 1..66
BOOK_NUMBER_BY_CODE = {book[1]: i + 1 for i, book in enumerate(BOOKS)}      # 'GEN'   -> 1
ABBREVIATION_BY_NUMBER = {i + 1: book[3] for i, book in enumerate(BOOKS)}

# osisRef uses its own abbreviation scheme, different from osisID
BOOK_NUMBER_BY_REF_CODE = {}
for _ref_code, _book_number in [
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
    BOOK_NUMBER_BY_REF_CODE[_ref_code] = _book_number

MARKER = re.compile(r"\[(\d{2,4}):(\d{1,3})\]")
WORD = re.compile(r"[a-z0-9]+")

stats = defaultdict(int)


def normalize(text):
    text = unicodedata.normalize("NFD", text)
    text = "".join(char for char in text if unicodedata.category(char) != "Mn")
    return text.lower()


class Builder:

    def __init__(self):
        self.parts = []
        self.length = 0
        self.spans = []

    def add(self, text):
        if text:
            self.parts.append(text)
            self.length += len(text)

    def text(self):
        return "".join(self.parts)


def walk(element, builder):
    """OSIS element -> Builder.  Notes are skipped (handled separately)."""
    if element.text:
        builder.add(element.text)
    for child in element:
        tag = child.tag.replace(NS, "")
        start = builder.length
        if tag == "note" or tag == "catchWord":
            pass
        elif tag == "w":
            walk(child, builder)
        elif tag in ("transChange", "hi"):
            walk(child, builder)
            if builder.length > start:
                builder.spans.append(("i", start, builder.length, ""))
        elif tag == "divineName":
            walk(child, builder)
            if builder.length > start:
                builder.spans.append(("d", start, builder.length, ""))
        elif tag == "title":
            walk(child, builder)
            if builder.length > start:
                builder.spans.append(("a", start, builder.length, ""))
        elif tag == "reference":
            walk(child, builder)
            if builder.length > start:
                builder.spans.append(("r", start, builder.length, child.get("osisRef", "")))
        else:
            stats["tag:" + tag] += 1
            walk(child, builder)
        if child.tail:
            builder.add(child.tail)
    return builder


def collapse(raw, spans):
    """Collapse and trim whitespace; span positions shift along."""
    chars = []
    index_map = []
    prev_space = True
    for char in raw:
        if char.isspace():
            if prev_space:
                index_map.append(len(chars))
                continue
            chars.append(" ")
            index_map.append(len(chars) - 1)
            prev_space = True
        else:
            chars.append(char)
            index_map.append(len(chars) - 1)
            prev_space = False
    index_map.append(len(chars))
    collapsed = "".join(chars)
    leading = len(collapsed) - len(collapsed.lstrip())
    body = collapsed.strip()
    body_end = leading + len(body)

    def map_position(position):
        mapped = index_map[min(position, len(index_map) - 1)]
        return max(0, min(mapped, body_end) - leading)

    return body, [(kind, map_position(start), map_position(end), value)
                  for kind, start, end, value in spans]


def segments(full_text, osis_chapter, osis_verse):
    """Split an OSIS verse into SV verses: [(sv_c, sv_v, lo, hi), ...]."""
    markers = list(MARKER.finditer(full_text))
    if not markers:
        return [(osis_chapter, osis_verse, 0, len(full_text))]
    result = []
    if full_text[:markers[0].start()].strip():
        result.append((osis_chapter, osis_verse, 0, markers[0].start()))
    for i, marker in enumerate(markers):
        segment_end = markers[i + 1].start() if i + 1 < len(markers) else len(full_text)
        marker_chapter, marker_verse = int(marker.group(1)), int(marker.group(2))
        # More than one chapter off is a typesetting error (Ps. 84:7 appears as [086:7]).
        if abs(marker_chapter - osis_chapter) > 1:
            stats["marker_corrected"] += 1
            marker_chapter = osis_chapter
        result.append((marker_chapter, marker_verse, marker.end(), segment_end))
    return result


def parse_osis_ref(raw):
    """'Pro.8.22-Pro.8.23' -> (book, chapter, verse, end verse) in OSIS numbering."""
    raw = raw.strip()
    first_ref = re.split(r"[-;,]", raw)[0].strip()
    match = re.match(r"^([0-9A-Za-z]+)\.(\d+)(?:\.(\d+))?$", first_ref)
    if not match:
        return None
    book_number = BOOK_NUMBER_BY_REF_CODE.get(match.group(1))
    if not book_number:
        stats["ref_unknown:" + match.group(1)] += 1
        return None
    end_verse = None
    if "-" in raw:
        end_match = re.match(r"^(?:[0-9A-Za-z]+\.\d+\.)?(\d+)$", raw.split("-", 1)[1].strip())
        if end_match:
            end_verse = int(end_match.group(1))
    return (book_number, int(match.group(2)),
            int(match.group(3)) if match.group(3) else 0, end_verse)


NAME_BY_NUMBER = {i + 1: book[2] for i, book in enumerate(BOOKS)}


def ref_label(value):
    """'19.90.2.0' -> 'Ps. 90:2'   ('19.90.0.0' = whole chapter -> 'Ps. 90')."""
    parts = value.split(".")
    book_number, chapter, verse, end_verse = (
        int(parts[0]), int(parts[1]), int(parts[2]), int(parts[3]))
    abbreviation = ABBREVIATION_BY_NUMBER.get(book_number, "?")
    dot = "" if abbreviation.lower() == NAME_BY_NUMBER.get(book_number, "").lower() else "."
    label = "%s%s\u00a0%d" % (abbreviation.replace(" ", "\u00a0"), dot, chapter)
    if verse > 0:
        label += ":%d" % verse
        if end_verse > verse:
            label += "-%d" % end_verse
    return label


def rewrite_refs(text, spans):
    """Replaces 'Psa 90:2' with 'Ps. 90:2' and shifts all spans along."""
    ref_spans = sorted([span for span in spans if span[0] == "r"], key=lambda span: span[1])
    if not ref_spans:
        return text, spans
    pieces = []
    bounds = []          # (old_start, old_end, new_start, new_end, value)
    copied_up_to = 0
    for kind, old_start, old_end, value in ref_spans:
        if old_start < copied_up_to or old_end > len(text):
            continue
        pieces.append(text[copied_up_to:old_start])
        new_start = sum(len(piece) for piece in pieces)
        label = ref_label(value)
        pieces.append(label)
        bounds.append((old_start, old_end, new_start, new_start + len(label), value))
        copied_up_to = old_end
    if not bounds:
        return text, spans
    pieces.append(text[copied_up_to:])
    new_text = "".join(pieces)

    def map_position(position):
        shift = 0
        for old_start, old_end, new_start, new_end, _ in bounds:
            if position >= old_end:
                shift += (new_end - new_start) - (old_end - old_start)
            elif position > old_start:
                return new_start
        return position + shift

    result = [(kind, map_position(start), map_position(end), value)
              for kind, start, end, value in spans if kind != "r"]
    result += [("r", new_start, new_end, value) for _, _, new_start, new_end, value in bounds]
    return new_text, sorted(result, key=lambda span: (span[1], span[2]))


PUNCT_SPACE = re.compile(r"[ \u00a0]+(?=[.,;:!?])|(?<=[(\[])[ \u00a0]+|[ \u00a0]+(?=[)\]])")


def tidy_punctuation(text, spans):
    """'Zie Gen 1:2 .' -> 'Zie Gen 1:2.'; spans shift along."""
    dropped = set()
    for match in PUNCT_SPACE.finditer(text):
        dropped.update(range(match.start(), match.end()))
    if not dropped:
        return text, spans
    chars = []
    index_map = []
    for i, char in enumerate(text):
        index_map.append(len(chars))
        if i not in dropped:
            chars.append(char)
    index_map.append(len(chars))
    new_text = "".join(chars)

    def map_position(position):
        return index_map[min(max(position, 0), len(index_map) - 1)]

    new_spans = []
    for kind, start, end, value in spans:
        new_start, new_end = map_position(start), map_position(end)
        if new_end > new_start or kind == "n":
            new_spans.append((kind, new_start, new_end, value))
    return new_text, new_spans


# In Ps. 119 the letter names are plain text, elsewhere they are <title>.
ACROSTIC = re.compile(
    r"^(Aleph|Beth|Gimel|Daleth|He|Vau|Zain|Cheth|Teth|Jod|Caph|Lamed|Mem|Nun|"
    r"Samech|Ain|Pe|Tsade|Koph|Resch|Schin|Thau)\. ")


def mark_acrostic(book_number, chapter, text, spans):
    if book_number != 19 or chapter != 119:
        return spans
    match = ACROSTIC.match(text)
    if not match or any(kind == "a" for kind, _, _, _ in spans):
        return spans
    return sorted(spans + [("a", 0, len(match.group(1)), "")],
                  key=lambda span: (span[1], span[2]))


WORD_IN_TEXT = re.compile(r"[0-9A-Za-z\u00c0-\u024f]+")


def loose(text):
    """Without accents and double letters: 'Hamaaloth' == 'Hammaaloth'."""
    text = normalize(text)
    text = re.sub(r"[^a-z0-9]+", "", text)
    return re.sub(r"(.)\1+", r"\1", text)


def find_loose(text, normalized_text, needle, cursor):
    target = loose(needle.split()[-1] if needle.split() else needle)
    if len(target) < 3:
        return -1, 0
    best = (-1, 0)
    for match in WORD_IN_TEXT.finditer(normalized_text):
        if loose(match.group(0)) == target:
            if match.start() >= cursor:
                return match.start(), match.end() - match.start()
            if best[0] < 0:
                best = (match.start(), match.end() - match.start())
    return best


def spans_str(spans):
    return "|".join("%s,%d,%d,%s" % span for span in spans)


def encode(ids):
    """Ascending ids -> delta-varint blob."""
    out = bytearray()
    prev = 0
    for doc_id in sorted(ids):
        delta = doc_id - prev
        prev = doc_id
        while delta >= 0x80:
            out.append((delta & 0x7F) | 0x80)
            delta >>= 7
        out.append(delta)
    return bytes(out)


SCHEMA = """
PRAGMA journal_mode=OFF;
PRAGMA synchronous=OFF;
CREATE TABLE books(
    number INTEGER PRIMARY KEY, code TEXT, name TEXT, abbreviation TEXT,
    testament TEXT, title TEXT, chapter_count INTEGER, aliases TEXT);
CREATE TABLE chapters(
    book INTEGER, chapter INTEGER, title TEXT,
    PRIMARY KEY(book,chapter)) WITHOUT ROWID;
CREATE TABLE verses(
    id INTEGER PRIMARY KEY, book INTEGER, chapter INTEGER, verse INTEGER,
    text TEXT, spans TEXT);
CREATE UNIQUE INDEX verses_by_reference ON verses(book,chapter,verse);
CREATE TABLE notes(
    id INTEGER PRIMARY KEY, book INTEGER, chapter INTEGER, verse INTEGER,
    number INTEGER, catchword TEXT, text TEXT, spans TEXT);
CREATE INDEX notes_by_reference ON notes(book,chapter,verse);
CREATE TABLE xref(
    note_id INTEGER, target_book INTEGER, target_chapter INTEGER,
    target_verse INTEGER, target_end_verse INTEGER);
CREATE INDEX xref_by_target ON xref(target_book,target_chapter,target_verse);
CREATE TABLE word_index_verses(
    term TEXT PRIMARY KEY, doc_count INTEGER, docs BLOB) WITHOUT ROWID;
CREATE TABLE word_index_notes(
    term TEXT PRIMARY KEY, doc_count INTEGER, docs BLOB) WITHOUT ROWID;
"""


def build(source_path, db_path):
    print("Reading %s ..." % source_path)
    xml_root = ET.parse(source_path).getroot()
    osis_text = xml_root.find(NS + "osisText")
    book_divs = {div.get("osisID"): div for div in osis_text.findall(NS + "div")
                 if div.get("type") == "book"}

    # ---- pass 1: OSIS numbering -> SV numbering (needed for the references)
    print("Pass 1: deriving SV verse numbering ...")
    osis_to_sv = {}
    for osis_id, code, name, abbreviation, testament, aliases in BOOKS:
        book_number = BOOK_NUMBER_BY_CODE[code]
        for chapter_el in book_divs[osis_id].findall(NS + "chapter"):
            osis_chapter = int(chapter_el.get("osisID").split(".")[-1])
            for verse_el in chapter_el.findall(NS + "verse"):
                osis_verse = int(verse_el.get("osisID").split(".")[-1])
                verse_segments = segments(walk(verse_el, Builder()).text(),
                                          osis_chapter, osis_verse)
                osis_to_sv[(book_number, osis_chapter, osis_verse)] = (
                    verse_segments[0][0], verse_segments[0][1])

    # ---- pass 2: text, notes, references, search index
    print("Pass 2: building ...")
    if os.path.exists(db_path):
        os.remove(db_path)
    db = sqlite3.connect(db_path)
    db.executescript(SCHEMA)

    verse_id = note_id = 0
    verse_rows, note_rows, xref_rows = [], [], []
    book_rows, chapter_rows = [], []
    verse_postings, note_postings = defaultdict(list), defaultdict(list)
    split_verses = 0

    for osis_id, code, name, abbreviation, testament, aliases in BOOKS:
        book_number = BOOK_NUMBER_BY_CODE[code]
        book_div = book_divs[osis_id]
        book_title = ""
        title_el = book_div.find(NS + "title")
        if title_el is not None and title_el.get("type") == "main":
            book_title = walk(title_el, Builder()).text().strip()

        raw_parts = {}     # (c,v) -> [text parts]
        raw_spans = {}     # (c,v) -> spans (positions relative to the joined raw text)
        raw_notes = defaultdict(list)
        verse_order = []

        for chapter_el in book_div.findall(NS + "chapter"):
            osis_chapter = int(chapter_el.get("osisID").split(".")[-1])
            for verse_el in chapter_el.findall(NS + "verse"):
                osis_verse = int(verse_el.get("osisID").split(".")[-1])
                builder = walk(verse_el, Builder())
                full_text = builder.text()
                verse_segments = segments(full_text, osis_chapter, osis_verse)

                for (sv_chapter, sv_verse, segment_start, segment_end) in verse_segments:
                    piece = full_text[segment_start:segment_end]
                    if not piece.strip() and (sv_chapter, sv_verse) in raw_parts:
                        continue
                    key = (sv_chapter, sv_verse)
                    if key not in raw_parts:
                        raw_parts[key] = []
                        raw_spans[key] = []
                        verse_order.append(key)
                    offset = sum(len(part) for part in raw_parts[key])
                    if offset:
                        raw_parts[key].append(" ")
                        offset += 1
                    raw_parts[key].append(piece)
                    shift = offset - segment_start
                    for kind, span_start, span_end, value in builder.spans:
                        if segment_start <= span_start and span_end <= segment_end:
                            raw_spans[key].append(
                                (kind, span_start + shift, span_end + shift, value))

                # notes: attach to the right SV segment via the catchword
                segment_keys = []
                for (sv_chapter, sv_verse, segment_start, segment_end) in verse_segments:
                    if (sv_chapter, sv_verse) not in segment_keys:
                        segment_keys.append((sv_chapter, sv_verse))
                if len(verse_segments) > 1:
                    split_verses += 1
                for note_el in verse_el.findall(NS + "note"):
                    catchword_el = note_el.find(NS + "catchWord")
                    catchword = (walk(catchword_el, Builder()).text().strip().strip(",;:")
                                 if catchword_el is not None else "")
                    key = segment_keys[0]
                    if catchword and len(segment_keys) > 1:
                        catchword_words = normalize(catchword).split()
                        if catchword_words:
                            for segment_key in segment_keys:
                                segment_text = "".join(raw_parts.get(segment_key, []))
                                if catchword_words[0] in normalize(segment_text):
                                    key = segment_key
                                    break
                    note_builder = walk(note_el, Builder())
                    raw_notes[key].append((catchword, note_builder))

        # ---- write out, chapter by chapter
        verses_by_chapter = defaultdict(list)
        for (chapter, verse) in verse_order:
            verses_by_chapter[chapter].append(verse)
        verse_total = note_total = 0
        for chapter in sorted(verses_by_chapter):
            note_number = 0
            for verse in sorted(verses_by_chapter[chapter]):
                key = (chapter, verse)
                text, spans = collapse("".join(raw_parts[key]), raw_spans[key])
                text, spans = tidy_punctuation(text, spans)
                verse_id += 1
                normalized_text = normalize(text)
                cursor = 0
                note_markers = []
                for catchword, note_builder in raw_notes.get(key, []):
                    note_number += 1
                    note_id += 1
                    marker_pos = None
                    if catchword:
                        needle = normalize(re.sub(r"\s+", " ", catchword).strip().strip(",;:. "))
                        if needle:
                            found = normalized_text.find(needle, cursor)
                            if found < 0:
                                found = normalized_text.find(needle)
                            if found < 0:
                                needle_words = needle.split()
                                if needle_words and len(needle_words[-1]) > 2:
                                    found = normalized_text.find(needle_words[-1], cursor)
                                    if found >= 0:
                                        needle = needle_words[-1]
                            if found < 0:
                                found, match_length = find_loose(
                                    text, normalized_text, needle, cursor)
                                if found >= 0:
                                    needle = normalized_text[found:found + match_length]
                            if found >= 0:
                                marker_pos = found + len(needle)
                                cursor = marker_pos
                                while (marker_pos < len(text)
                                       and text[marker_pos] in ",.;:!?’'\")]"):
                                    marker_pos += 1
                    if marker_pos is None:
                        stats["catchword_missed"] += 1
                        marker_pos = len(text)
                    else:
                        stats["catchword_placed"] += 1
                    note_markers.append(("n", marker_pos, marker_pos, str(note_number)))

                    note_text, note_spans = collapse(note_builder.text(), note_builder.spans)
                    out_spans = []
                    for kind, start, end, value in note_spans:
                        if kind != "r":
                            out_spans.append((kind, start, end, value))
                            continue
                        parsed_ref = parse_osis_ref(value)
                        if not parsed_ref:
                            continue
                        target_book, osis_chapter, osis_verse, osis_end_verse = parsed_ref
                        sv_target = osis_to_sv.get(
                            (target_book, osis_chapter, osis_verse if osis_verse else 1))
                        target_chapter, target_verse = (
                            sv_target if sv_target else (osis_chapter, osis_verse or 1))
                        if osis_verse == 0:
                            target_verse = 0          # reference to a whole chapter
                        target_end_verse = 0
                        if osis_end_verse:
                            sv_end = osis_to_sv.get((target_book, osis_chapter, osis_end_verse))
                            target_end_verse = sv_end[1] if sv_end else osis_end_verse
                            if target_end_verse <= target_verse:
                                target_end_verse = 0
                        out_spans.append((kind, start, end, "%d.%d.%d.%d" % (
                            target_book, target_chapter, target_verse, target_end_verse)))
                        xref_rows.append((note_id, target_book, target_chapter,
                                          target_verse, target_end_verse))
                    note_text, out_spans = rewrite_refs(note_text, out_spans)
                    note_text, out_spans = tidy_punctuation(note_text, out_spans)
                    note_rows.append((note_id, book_number, chapter, verse, note_number,
                                      catchword, note_text, spans_str(out_spans)))
                    for term in set(WORD.findall(normalize(note_text))):
                        if len(term) > 1:
                            note_postings[term].append(note_id)

                spans = mark_acrostic(book_number, chapter, text, spans)
                spans = sorted(spans + note_markers, key=lambda span: (span[1], span[2]))
                verse_rows.append((verse_id, book_number, chapter, verse, text,
                                   spans_str(spans)))
                for term in set(WORD.findall(normalized_text)):
                    if len(term) > 1:
                        verse_postings[term].append(verse_id)
                verse_total += 1
            note_total += note_number

        book_rows.append((book_number, code, name, abbreviation, testament, book_title,
                          len(verses_by_chapter), aliases))
        print("  %-4s %-20s %3d ch %5d vs %5d notes" %
              (code, name, len(verses_by_chapter), verse_total, note_total))

    # ---- church book: metrical psalms, confessions, forms and prayers
    extras = extras_in_db.load(os.path.join(ROOT, "extras.json"))
    if extras:
        print("\nAdding church book ...")
        book_by_slug = {extras_in_db.slug(book[2]): BOOK_NUMBER_BY_CODE[book[1]]
                        for book in BOOKS}
        verse_id, note_id = extras_in_db.add_rows(
            extras, book_by_slug, spans_str, normalize, WORD,
            book_rows, chapter_rows, verse_rows, note_rows, xref_rows,
            verse_postings, note_postings, verse_id, note_id)
    else:
        print("\n(extras.json missing — Bible text only)")

    db.executemany("INSERT INTO books VALUES(?,?,?,?,?,?,?,?)", book_rows)
    db.executemany("INSERT INTO chapters VALUES(?,?,?)", chapter_rows)
    db.executemany("INSERT INTO verses VALUES(?,?,?,?,?,?)", verse_rows)
    db.executemany("INSERT INTO notes VALUES(?,?,?,?,?,?,?,?)", note_rows)
    db.executemany("INSERT INTO xref VALUES(?,?,?,?,?)", xref_rows)

    print("Building search index ...")
    db.executemany("INSERT INTO word_index_verses VALUES(?,?,?)",
                   ((term, len(doc_ids), encode(doc_ids))
                    for term, doc_ids in verse_postings.items()))
    db.executemany("INSERT INTO word_index_notes VALUES(?,?,?)",
                   ((term, len(doc_ids), encode(doc_ids))
                    for term, doc_ids in note_postings.items()))
    db.commit()
    db.executescript("VACUUM;")
    db.close()

    print("\nDone: %d verses, %d notes, %d references"
          % (verse_id, note_id, len(xref_rows)))
    print("Catchword placed: %d, at verse end: %d"
          % (stats["catchword_placed"], stats["catchword_missed"]))
    print("Word index: %d verse terms, %d note terms"
          % (len(verse_postings), len(note_postings)))
    print("OSIS verses split in two: %d" % split_verses)
    anomalies = {key: count for key, count in stats.items()
                 if key.startswith(("tag:", "ref_unknown:"))}
    if anomalies:
        print("Anomalies:", anomalies)
    print("File size: %.1f MB" % (os.path.getsize(db_path) / 1e6))


if __name__ == "__main__":
    source_path = sys.argv[1] if len(sys.argv) > 1 else "STV.xml"
    db_path = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "bijbel.db")
    build(source_path, db_path)
