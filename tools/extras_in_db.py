#!/usr/bin/env python3
"""
extras.json -> rows in bijbel.db; called from build_db.py.
Proof texts become notes (letter as catchword) and also go into xref.
"""
import json
import os
import re
import unicodedata

# (book number, code, name, abbreviation, group in extras.json, search aliases)
EXTRA_BOOKS = [
    (101, "PSB", "Psalmen (berijmd)", "Ps. ber.", "psalm",
     "psalmberijming berijmde psalmen 1773 zingen"),
    (102, "GEZ", "Gezangen", "Gez.", "gezang",
     "lofzang morgenzang avondzang tien geboden"),
    (103, "HCA", "Heidelbergse Catechismus", "HC", "catechismus",
     "catechismus zondag heidelberger onderwijzing"),
    (104, "NGB", "Nederlandse Geloofsbelijdenis", "NGB", "ngb",
     "geloofsbelijdenis artikelen confessie"),
    (105, "DLR", "Dordtse Leerregels", "DL", "leerregels",
     "leerregels dordt dordrecht vijf artikelen"),
    (106, "BEL", "Geloofsbelijdenissen", "Belijdenis", "belijdenis",
     "apostolische nicea athanasius twaalf artikelen"),
    (107, "FOR", "Liturgische formulieren", "Formulier", "formulier",
     "doop avondmaal huwelijk ban bevestiging"),
    (108, "GEB", "Christelijke gebeden", "Gebed", "gebed",
     "morgengebed avondgebed gebeden"),
]


def slug(text):
    text = unicodedata.normalize("NFD", text)
    text = "".join(char for char in text if unicodedata.category(char) != "Mn").lower()
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-")


def split_title(title):
    """The first line is the heading, the rest the subtitle."""
    lines = [line.strip() for line in (title or "").split("\n") if line.strip()]
    if not lines:
        return "", ""
    heading = lines[0]
    subtitle = " ".join(lines[1:])
    return heading, subtitle


def load(path):
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as file:
        return json.load(file)


def add_rows(extras, book_by_slug, spans_str, normalize, word_pattern,
             book_rows, chapter_rows, verse_rows, note_rows, xref_rows,
             verse_postings, note_postings, verse_id, note_id):
    """Appends the rows; returns the new verse_id and note_id counters."""
    for book_number, code, name, abbreviation, group, aliases in EXTRA_BOOKS:
        pages = extras.get(group)
        if not pages:
            continue
        page_keys = sorted(pages, key=lambda page_key: int(page_key))
        verse_total = note_total = 0
        for page_key in page_keys:
            page = pages[page_key]
            chapter = int(page_key)
            heading, subtitle = split_title(page["titel"])
            note_number = 0
            for row in page["rijen"]:
                verse = row["n"]
                text = row["tekst"]
                spans = []

                own_refs = row.get("verwijzingen") or page.get("verwijzingen") or {}
                for mark in row.get("merken", []):
                    places = own_refs.get(mark["letter"]) or []
                    if not places:
                        continue
                    note_number += 1
                    note_id += 1
                    spans.append(("n", mark["p"], mark["p"], str(note_number)))
                    pieces = []
                    note_spans = []
                    for place in places:
                        target_book = book_by_slug.get(place["boek"])
                        label = place["label"]
                        if pieces:
                            pieces.append("; ")
                        start = sum(len(piece) for piece in pieces)
                        pieces.append(label)
                        if target_book:
                            note_spans.append(("r", start, start + len(label), "%d.%d.%d.0" % (
                                target_book, place["h"], place["v"])))
                            xref_rows.append((note_id, target_book, place["h"], place["v"], 0))
                    note_text = "".join(pieces)
                    note_rows.append((note_id, book_number, chapter, verse, note_number,
                                      mark["letter"], note_text, spans_str(note_spans)))
                    for term in set(word_pattern.findall(normalize(note_text))):
                        if len(term) > 1:
                            note_postings[term].append(note_id)

                verse_id += 1
                spans.sort(key=lambda span: (span[1], span[2]))
                verse_rows.append((verse_id, book_number, chapter, verse, text,
                                   spans_str(spans)))
                for term in set(word_pattern.findall(normalize(text))):
                    if len(term) > 1:
                        verse_postings[term].append(verse_id)
                verse_total += 1
            title = heading if heading else ""
            if subtitle:
                title = (title + "\n" + subtitle) if title else subtitle
            if title:
                chapter_rows.append((book_number, chapter, title))
            note_total += note_number
        book_rows.append((book_number, code, name, abbreviation, "EX", "", len(page_keys),
                          aliases))
        print("  %-4s %-30s %3d pt %5d blk %5d refs"
              % (code, name, len(page_keys), verse_total, note_total))
    return verse_id, note_id
