#!/usr/bin/env python3
"""
extras.json -> rijen in bijbel.db; aangeroepen vanuit build_db.py.
Bewijsteksten worden kanttekeningen (letter als trefwoord) en gaan ook in xref.
"""
import json
import os
import re
import unicodedata

# (boeknr, code, naam, afkorting, groep in extras.json, soort, zoektermen)
EXTRA_BOEKEN = [
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


def net_titel(t):
    """De eerste regel is de kop, de rest het onderschrift."""
    regels = [r.strip() for r in (t or "").split("\n") if r.strip()]
    if not regels:
        return "", ""
    kop = regels[0]
    onder = " ".join(regels[1:])
    return kop, onder


def laad(pad):
    if not os.path.exists(pad):
        return None
    with open(pad, encoding="utf-8") as f:
        return json.load(f)


def voeg_toe(extras, boeknr_van_slug, spans_str, norm, WORD,
             book_rows, chap_rows, verse_rows, note_rows, xref_rows,
             post_v, post_n, vid, nid):
    """Vult de rijen aan; geeft de nieuwe vid- en nid-tellers terug."""
    for bnum, code, naam, afk, groep, soort, zoek in EXTRA_BOEKEN:
        pagina = extras.get(groep)
        if not pagina:
            continue
        sleutels = sorted(pagina, key=lambda x: int(x))
        totaal_v = totaal_n = 0
        for c_index, sleutel in enumerate(sleutels, 1):
            p = pagina[sleutel]
            c = int(sleutel)
            kop, onder = net_titel(p["titel"])
            note_no = 0
            for rij in p["rijen"]:
                v = rij["n"]
                tekst = rij["tekst"]
                spans = []

                eigen = rij.get("verwijzingen") or p.get("verwijzingen") or {}
                for merk in rij.get("merken", []):
                    plaatsen = eigen.get(merk["letter"]) or []
                    if not plaatsen:
                        continue
                    note_no += 1
                    nid += 1
                    spans.append(("n", merk["p"], merk["p"], str(note_no)))
                    stukken = []
                    nspans = []
                    for pl in plaatsen:
                        tb = boeknr_van_slug.get(pl["boek"])
                        label = pl["label"]
                        if stukken:
                            stukken.append("; ")
                        begin = sum(len(x) for x in stukken)
                        stukken.append(label)
                        if tb:
                            nspans.append(("r", begin, begin + len(label),
                                           "%d.%d.%d.0" % (tb, pl["h"], pl["v"])))
                            xref_rows.append((nid, bnum, c, v, tb, pl["h"], pl["v"], 0))
                    ntekst = "".join(stukken)
                    note_rows.append((nid, bnum, c, v, note_no, merk["letter"],
                                      ntekst, spans_str(nspans)))
                    for term in set(WORD.findall(norm(ntekst))):
                        if len(term) > 1:
                            post_n[term].append(nid)

                vid += 1
                spans.sort(key=lambda x: (x[1], x[2]))
                verse_rows.append((vid, bnum, c, v, tekst, spans_str(spans), 0))
                for term in set(WORD.findall(norm(tekst))):
                    if len(term) > 1:
                        post_v[term].append(vid)
                totaal_v += 1
            titel = kop if kop else ""
            if onder:
                titel = (titel + "\n" + onder) if titel else onder
            chap_rows.append((bnum, c, len(p["rijen"]), note_no, titel))
            totaal_n += note_no
        book_rows.append((bnum, code, naam, afk, "EX", "", len(sleutels),
                          totaal_v, totaal_n, zoek, soort))
        print("  %-4s %-30s %3d dl %5d blk %5d verw."
              % (code, naam, len(sleutels), totaal_v, totaal_n))
    return vid, nid
