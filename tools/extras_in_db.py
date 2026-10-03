#!/usr/bin/env python3
"""
extras.json -> rows in bijbel.db; called from build_db.py.
Proof texts become notes (letter as catchword) and also go into xref.
"""
import json
import os
import re
import unicodedata

# (book number, code, name, abbreviation, group in extras.json, category, search aliases)
EXTRA_BOOKS = [
    (101, "PSB", "Psalmen (berijmd)", "Ps. ber.", "psalm", "psalm",
     "psalmberijming berijmde psalmen 1773 zingen"),
    (102, "GEZ", "Gezangen", "Gez.", "gezang", "psalm",
     "lofzang morgenzang avondzang tien geboden"),
    (103, "HCA", "Heidelbergse Catechismus", "HC", "catechismus", "belijdenis",
     "catechismus zondag heidelberger onderwijzing"),
    (104, "NGB", "Nederlandse Geloofsbelijdenis", "NGB", "ngb", "belijdenis",
     "geloofsbelijdenis artikelen confessie"),
    (105, "DLR", "Dordtse Leerregels", "DL", "leerregels", "belijdenis",
     "leerregels dordt dordrecht vijf artikelen"),
    (106, "BEL", "Geloofsbelijdenissen", "Belijdenis", "belijdenis", "belijdenis",
     "apostolische nicea athanasius twaalf artikelen"),
    (107, "FOR", "Liturgische formulieren", "Formulier", "formulier", "formulier",
     "doop avondmaal huwelijk ban bevestiging"),
    (108, "GEB", "Christelijke gebeden", "Gebed", "gebed", "formulier",
     "morgengebed avondgebed gebeden"),
]


def slug(s):
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn").lower()
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def split_title(t):
    """The first line is the heading, the rest the subtitle."""
    lines = [r.strip() for r in (t or "").split("\n") if r.strip()]
    if not lines:
        return "", ""
    heading = lines[0]
    subtitle = " ".join(lines[1:])
    return heading, subtitle


def load(path):
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def add_rows(extras, book_by_slug, spans_str, norm, WORD,
             book_rows, chap_rows, verse_rows, note_rows, xref_rows,
             post_v, post_n, vid, nid):
    """Appends the rows; returns the new vid and nid counters."""
    for bnum, code, name, abbr, group, category, aliases in EXTRA_BOOKS:
        pages = extras.get(group)
        if not pages:
            continue
        keys = sorted(pages, key=lambda x: int(x))
        total_v = total_n = 0
        for c_index, key in enumerate(keys, 1):
            p = pages[key]
            c = int(key)
            heading, subtitle = split_title(p["titel"])
            note_no = 0
            for row in p["rijen"]:
                v = row["n"]
                text = row["tekst"]
                spans = []

                own_refs = row.get("verwijzingen") or p.get("verwijzingen") or {}
                for mark in row.get("merken", []):
                    places = own_refs.get(mark["letter"]) or []
                    if not places:
                        continue
                    note_no += 1
                    nid += 1
                    spans.append(("n", mark["p"], mark["p"], str(note_no)))
                    pieces = []
                    nspans = []
                    for pl in places:
                        tb = book_by_slug.get(pl["boek"])
                        label = pl["label"]
                        if pieces:
                            pieces.append("; ")
                        start = sum(len(x) for x in pieces)
                        pieces.append(label)
                        if tb:
                            nspans.append(("r", start, start + len(label),
                                           "%d.%d.%d.0" % (tb, pl["h"], pl["v"])))
                            xref_rows.append((nid, bnum, c, v, tb, pl["h"], pl["v"], 0))
                    ntext = "".join(pieces)
                    note_rows.append((nid, bnum, c, v, note_no, mark["letter"],
                                      ntext, spans_str(nspans)))
                    for term in set(WORD.findall(norm(ntext))):
                        if len(term) > 1:
                            post_n[term].append(nid)

                vid += 1
                spans.sort(key=lambda x: (x[1], x[2]))
                verse_rows.append((vid, bnum, c, v, text, spans_str(spans), 0))
                for term in set(WORD.findall(norm(text))):
                    if len(term) > 1:
                        post_v[term].append(vid)
                total_v += 1
            title = heading if heading else ""
            if subtitle:
                title = (title + "\n" + subtitle) if title else subtitle
            chap_rows.append((bnum, c, len(p["rijen"]), note_no, title))
            total_n += note_no
        book_rows.append((bnum, code, name, abbr, "EX", "", len(keys),
                          total_v, total_n, aliases, category))
        print("  %-4s %-30s %3d pt %5d blk %5d refs"
              % (code, name, len(keys), total_v, total_n))
    return vid, nid
