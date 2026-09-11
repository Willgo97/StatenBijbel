#!/usr/bin/env python3
"""
Haalt de 'achterin het kerkboek'-stukken op: de berijmde psalmen van 1773,
de gezangen, de Drie Formulieren van Enigheid, de oecumenische belijdenissen,
de liturgische formulieren en de christelijke gebeden.

Bron: bijbel-statenvertaling.com (GBS Bijbel Online) — dezelfde editie als het
gedrukte kerkboek. De teksten zelf zijn publiek domein (1773 en ouder).

De pagina's worden op schijf bewaard, zodat opnieuw ontleden geen nieuw
netwerkverkeer kost.  Gebruik:

    python3 tools/haal_extras.py ophalen   # eenmalig, ~280 pagina's
    python3 tools/haal_extras.py ontleden  # maakt extras.json
"""
import gzip
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
from html import unescape

BASIS = "https://bijbel-statenvertaling.com"
UA = ("Mozilla/5.0 (X11; Linux x86_64) StatenBijbel-offline/1.0 "
      "(persoonlijk gebruik; publiek-domeinteksten)")
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cache-extras")
PAUZE = 0.6

GEZANGEN = [
    "de-tien-geboden-des-heeren",
    "de-eerste-berijming-van-de-twaalf-artikelen-des-geloofs",
    "de-tweede-berijming-van-de-twaalf-artikelen-des-geloofs",
    "het-gebed-des-heeren",
    "de-lofzang-van-maria",
    "de-lofzang-van-zacharias",
    "de-lofzang-van-simeon",
    "bedezang-voor-de-predicatie",
    "morgenzang",
    "avondzang",
    "bedezang-voor-het-eten",
    "dankzang-na-het-eten",
    "een-eigen-geschrift-van-david",
]

LEERREGELS = [
    "voorrede",
    "het-eerste-hoofdstuk-der-leer",
    "het-tweede-hoofdstuk-der-leer",
    "het-derde-en-vierde-hoofdstuk-der-leer",
    "het-vijfde-hoofdstuk-der-leer",
    "besluit",
]

BELIJDENISSEN = [
    "apostolische-geloofsbelijdenis",
    "geloofsbelijdenis-van-nicea",
    "geloofsbelijdenis-van-athanasius",
]

FORMULIEREN = [
    "formulier-om-den-heiligen-doop-aan-de-kinderen-te-bedienen",
    "formulier-om-den-heiligen-doop-aan-de-volwassenen-te-bedienen",
    "formulier-om-het-heilig-avondmaal-te-houden",
    "formulier-om-den-huwelijken-staat-voor-de-gemeente-van-christus-te-bevestigen",
    "formulier-om-de-dienaars-des-woords-te-bevestigen",
    "formulier-om-ouderlingen-en-diakenen-te-bevestigen",
    "formulier-van-den-ban-of-de-afsnijding",
    "formulier-van-wederopneming",
]

GEBEDEN = [
    "het-morgengebed",
    "het-avondgebed",
    "gebed-voor-het-eten",
    "gebed-na-het-eten",
    "een-gebed-voor-de-leer-van-den-catechismus",
    "een-gebed-na-de-leer-van-den-catechismus",
    "een-kort-formulier-des-gebeds-na-de-predicatie",
    "een-algemene-belijdenis-der-zonden",
    "een-openlijke-belijdenis-der-zonden",
    "een-gebed-voor-allen-nood-der-christenheid",
    "een-gebed-voor-de-vergadering-der-diakenen",
    "gebed-voor-de-handeling-der-kerkelijke-bijeenkomsten",
    "gebed-na-de-handeling-der-kerkelijke-bijeenkomsten",
    "gebed-voor-kranke-en-aangevochten-mensen",
]


def paden():
    """Alle op te halen pagina's: (groep, sleutel, pad)."""
    uit = []
    for n in range(1, 151):
        uit.append(("psalm", str(n), f"/1773/psalm/{n}/"))
    for i, s in enumerate(GEZANGEN, 1):
        uit.append(("gezang", str(i), f"/1773/gezang/{s}/"))
    for n in range(1, 53):
        uit.append(("catechismus", str(n), f"/catechismus/zondag/{n}/"))
    for n in range(1, 38):
        uit.append(("ngb", str(n), f"/nederlandse-geloofsbelijdenis/artikel/{n}/"))
    for i, s in enumerate(LEERREGELS, 1):
        uit.append(("leerregels", str(i), f"/dordtse-leerregels/{s}/"))
    for i, s in enumerate(BELIJDENISSEN, 1):
        uit.append(("belijdenis", str(i), f"/belijdenis/{s}/"))
    for i, s in enumerate(FORMULIEREN, 1):
        uit.append(("formulier", str(i), f"/liturgische-formulieren/{s}/"))
    for i, s in enumerate(GEBEDEN, 1):
        uit.append(("gebed", str(i), f"/christelijke-gebeden/{s}/"))
    return uit


def bestand(groep, sleutel):
    return os.path.join(CACHE, f"{groep}-{sleutel}.html.gz")


def ophalen():
    os.makedirs(CACHE, exist_ok=True)
    lijst = paden()
    nieuw = 0
    for i, (groep, sleutel, pad) in enumerate(lijst, 1):
        doel = bestand(groep, sleutel)
        if os.path.exists(doel) and os.path.getsize(doel) > 5000:
            continue
        req = urllib.request.Request(
            BASIS + pad,
            headers={"User-Agent": UA, "Accept-Encoding": "gzip",
                     "Accept-Language": "nl"},
        )
        for poging in range(3):
            try:
                with urllib.request.urlopen(req, timeout=40) as r:
                    rauw = r.read()
                    if r.headers.get("Content-Encoding") == "gzip":
                        rauw = gzip.decompress(rauw)
                break
            except (urllib.error.URLError, OSError) as e:
                if poging == 2:
                    print(f"  ! mislukt {pad}: {e}")
                    rauw = None
                else:
                    time.sleep(2 * (poging + 1))
        if not rauw:
            continue
        with gzip.open(doel, "wb") as f:
            f.write(rauw)
        nieuw += 1
        if nieuw % 25 == 0:
            print(f"  {i}/{len(lijst)} ({groep})")
        time.sleep(PAUZE)
    print(f"Klaar: {nieuw} nieuwe pagina's, {len(lijst)} totaal.")


# ------------------------------------------------------------------ ontleden
SCHOON = re.compile(r"<(script|style)\b.*?</\1>", re.S | re.I)


def lees(groep, sleutel):
    p = bestand(groep, sleutel)
    if not os.path.exists(p):
        return None
    with gzip.open(p, "rb") as f:
        return f.read().decode("utf-8", "replace")


def inhoudsblok(s):
    """Het deel van de pagina met de eigenlijke tekst."""
    m = re.search(r'<div class="[^"]*publication-content[^"]*"[^>]*>', s)
    if not m:
        return ""
    start = m.end()
    eind = s.find('<div class="col', start)
    staart = s.find("</section>", start)
    if staart != -1 and (eind == -1 or staart < eind):
        eind = staart
    return s[start:eind if eind != -1 else len(s)]


def titel_van(s):
    m = re.search(r'<h1 class="[^"]*chapter-title[^"]*">(.*?)</h1>', s, re.S)
    if not m:
        m = re.search(r'<h1 class="publication-title">(.*?)</h1>', s, re.S)
    if not m:
        return ""
    ruw = re.sub(r"</span>\s*<span", "</span>\n<span", m.group(1))
    return plat(ruw)


def plat(h):
    """Opmaak weg.  Alleen een echte <br> telt als regelovergang; de
    witruimte waarmee de bron-HTML is ingesprongen niet."""
    h = re.sub(r"<br\s*/?>", "\x02", h)
    h = re.sub(r"<[^>]+>", "", h)
    h = unescape(h)
    h = re.sub(r"[ \t\r\n]+", " ", h)
    h = h.replace("\x02", "\n")
    h = re.sub(r" *\n *", "\n", h)
    return h.strip()


def verwijzingen_van(s):
    """De bewijsteksten: letter -> [(label, boek-slug, hoofdstuk, vers)]."""
    uit = {}
    for m in re.finditer(
        r"<span class='reference-number'>([a-z]+)</span>(.*?)(?=<span class='reference-number'>|$)",
        s, re.S,
    ):
        letter = m.group(1)
        plaatsen = []
        for a in re.finditer(
            r"<a href='/statenvertaling/([^/]+)/(\d+)/#(\d+)'[^>]*>(.*?)</a>", m.group(2), re.S
        ):
            plaatsen.append({
                "boek": a.group(1), "h": int(a.group(2)),
                "v": int(a.group(3)), "label": plat(a.group(4)),
            })
        if plaatsen:
            uit.setdefault(letter, []).extend(plaatsen)
    return uit


def blokken_van(inhoud):
    """De genummerde tekstblokken (verzen, vragen, artikelen)."""
    posities = [(m.start(), int(m.group(1)))
                for m in re.finditer(r'<div class="[^"]*\bverse verse-(\d+)\b[^"]*"', inhoud)]
    uit = []
    for i, (pos, nr) in enumerate(posities):
        eind = posities[i + 1][0] if i + 1 < len(posities) else len(inhoud)
        stuk = inhoud[pos:eind]
        stuk = re.sub(r'<span class="verse-number">.*?</span>', "", stuk, count=1, flags=re.S)
        uit.append((nr, stuk))
    return uit


def ontleed_pagina(groep, sleutel):
    s = lees(groep, sleutel)
    if not s:
        return None
    s = SCHOON.sub("", s)
    inhoud = inhoudsblok(s)
    titel = titel_van(s)
    verw = verwijzingen_van(s)
    blokken = blokken_van(inhoud)
    if not blokken:
        # doorlopende tekst: alinea's uit <p>, of anders uit <div class="text">
        alineas = [plat(m.group(1)) for m in
                   re.finditer(r"<p[^>]*>(.*?)</p>", inhoud, re.S)]
        if not any(len(a) > 1 for a in alineas):
            ruw = "\n\n".join(
                m.group(1) for m in
                re.finditer(r'<div class="text[^"]*"[^>]*>(.*?)</div>', inhoud, re.S)
            )
            if not ruw.strip():
                ruw = inhoud
            alineas = [a.strip() for a in re.split(r"\n\s*\n", plat(ruw))]
        alineas = [a for a in alineas if len(a) > 1]
        blokken = [(i, a) for i, a in enumerate(alineas, 1)]
        rijen = [{"n": n, "tekst": t} for n, t in blokken]
    else:
        rijen = []
        for n, ruw in blokken:
            # De verwijsletters eerst vervangen door een merkteken, zodat hun
            # plaats in de tekst bewaard blijft na het strippen van de opmaak.
            gemerkt = re.sub(r'<span class="verwijzing">\s*([a-z]+)\s*</span>',
                             lambda m: "\x01" + m.group(1) + "\x01", ruw)
            tekst = plat(gemerkt)
            # Eerst de witruimte opschonen, pas daarna de plaatsen uitlezen —
            # anders verschuiven de markeringen.
            tekst = re.sub(r"[ \t]+(\x01[a-z]+\x01)", r"\1", tekst)
            tekst = re.sub(r"[ \t]+([,.;:!?])", r"\1", tekst)
            tekst = re.sub(r"[ \t]{2,}", " ", tekst)
            merken = []
            while True:
                m = re.search(r"\x01([a-z]+)\x01", tekst)
                if not m:
                    break
                merken.append({"p": m.start(), "letter": m.group(1)})
                tekst = tekst[:m.start()] + tekst[m.end():]
            rij = {"n": n, "tekst": tekst}
            if merken:
                rij["merken"] = merken
            rijen.append(rij)
    return {"titel": titel, "rijen": rijen, "verwijzingen": verw}


def ontleden():
    uit = {}
    stuk = 0
    for groep, sleutel, pad in paden():
        p = ontleed_pagina(groep, sleutel)
        if not p or not p["rijen"]:
            print(f"  ! leeg: {groep}/{sleutel} ({pad})")
            continue
        uit.setdefault(groep, {})[sleutel] = p
        stuk += len(p["rijen"])
    doel = os.path.join(os.path.dirname(CACHE), "extras.json")
    with open(doel, "w", encoding="utf-8") as f:
        json.dump(uit, f, ensure_ascii=False)
    print(f"\nOntleed: {sum(len(v) for v in uit.values())} pagina's, {stuk} blokken")
    for g, v in uit.items():
        blok = sum(len(p["rijen"]) for p in v.values())
        verw = sum(len(p["verwijzingen"]) for p in v.values())
        print(f"  {g:14s} {len(v):4d} pagina's  {blok:5d} blokken  {verw:5d} verwijsletters")
    print(f"-> {doel}")


if __name__ == "__main__":
    wat = sys.argv[1] if len(sys.argv) > 1 else "ophalen"
    if wat == "ophalen":
        ophalen()
    elif wat == "ontleden":
        ontleden()
    else:
        print(__doc__)
