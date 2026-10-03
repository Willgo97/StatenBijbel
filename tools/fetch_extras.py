#!/usr/bin/env python3
"""
Church book (1773 metrical psalms, confessions, forms, prayers) from
bijbel-statenvertaling.com -> extras.json. Pages are kept in cache-extras/.

    python3 tools/fetch_extras.py fetch   # once, ~280 pages
    python3 tools/fetch_extras.py parse   # writes extras.json
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

BASE_URL = "https://bijbel-statenvertaling.com"
UA = ("Mozilla/5.0 (X11; Linux x86_64) StatenBijbel-offline/1.0 "
      "(persoonlijk gebruik; publiek-domeinteksten)")
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cache-extras")
PAUSE = 0.6

HYMNS = [
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

CANONS = [
    "voorrede",
    "het-eerste-hoofdstuk-der-leer",
    "het-tweede-hoofdstuk-der-leer",
    "het-derde-en-vierde-hoofdstuk-der-leer",
    "het-vijfde-hoofdstuk-der-leer",
    "besluit",
]

CREEDS = [
    "apostolische-geloofsbelijdenis",
    "geloofsbelijdenis-van-nicea",
    "geloofsbelijdenis-van-athanasius",
]

FORMS = [
    "formulier-om-den-heiligen-doop-aan-de-kinderen-te-bedienen",
    "formulier-om-den-heiligen-doop-aan-de-volwassenen-te-bedienen",
    "formulier-om-het-heilig-avondmaal-te-houden",
    "formulier-om-den-huwelijken-staat-voor-de-gemeente-van-christus-te-bevestigen",
    "formulier-om-de-dienaars-des-woords-te-bevestigen",
    "formulier-om-ouderlingen-en-diakenen-te-bevestigen",
    "formulier-van-den-ban-of-de-afsnijding",
    "formulier-van-wederopneming",
]

PRAYERS = [
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


def pages():
    """All pages to fetch: (group, key, path)."""
    out = []
    for n in range(1, 151):
        out.append(("psalm", str(n), f"/1773/psalm/{n}/"))
    for i, s in enumerate(HYMNS, 1):
        out.append(("gezang", str(i), f"/1773/gezang/{s}/"))
    for n in range(1, 53):
        out.append(("catechismus", str(n), f"/catechismus/zondag/{n}/"))
    for n in range(1, 38):
        out.append(("ngb", str(n), f"/nederlandse-geloofsbelijdenis/artikel/{n}/"))
    for i, s in enumerate(CANONS, 1):
        out.append(("leerregels", str(i), f"/dordtse-leerregels/{s}/"))
    for i, s in enumerate(CREEDS, 1):
        out.append(("belijdenis", str(i), f"/belijdenis/{s}/"))
    for i, s in enumerate(FORMS, 1):
        out.append(("formulier", str(i), f"/liturgische-formulieren/{s}/"))
    for i, s in enumerate(PRAYERS, 1):
        out.append(("gebed", str(i), f"/christelijke-gebeden/{s}/"))
    return out


def cache_file(group, key):
    return os.path.join(CACHE, f"{group}-{key}.html.gz")


def fetch():
    os.makedirs(CACHE, exist_ok=True)
    todo = pages()
    new = 0
    for i, (group, key, path) in enumerate(todo, 1):
        dest = cache_file(group, key)
        if os.path.exists(dest) and os.path.getsize(dest) > 5000:
            continue
        req = urllib.request.Request(
            BASE_URL + path,
            headers={"User-Agent": UA, "Accept-Encoding": "gzip",
                     "Accept-Language": "nl"},
        )
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=40) as r:
                    raw = r.read()
                    if r.headers.get("Content-Encoding") == "gzip":
                        raw = gzip.decompress(raw)
                break
            except (urllib.error.URLError, OSError) as e:
                if attempt == 2:
                    print(f"  ! failed {path}: {e}")
                    raw = None
                else:
                    time.sleep(2 * (attempt + 1))
        if not raw:
            continue
        with gzip.open(dest, "wb") as f:
            f.write(raw)
        new += 1
        if new % 25 == 0:
            print(f"  {i}/{len(todo)} ({group})")
        time.sleep(PAUSE)
    print(f"Done: {new} new pages, {len(todo)} total.")


SCRIPT_STYLE = re.compile(r"<(script|style)\b.*?</\1>", re.S | re.I)


def read_cached(group, key):
    p = cache_file(group, key)
    if not os.path.exists(p):
        return None
    with gzip.open(p, "rb") as f:
        return f.read().decode("utf-8", "replace")


def content_block(s):
    """The part of the page holding the actual text."""
    m = re.search(r'<div class="[^"]*publication-content[^"]*"[^>]*>', s)
    if not m:
        return ""
    start = m.end()
    end = s.find('<div class="col', start)
    tail = s.find("</section>", start)
    if tail != -1 and (end == -1 or tail < end):
        end = tail
    return s[start:end if end != -1 else len(s)]


def title_of(s):
    m = re.search(r'<h1 class="[^"]*chapter-title[^"]*">(.*?)</h1>', s, re.S)
    if not m:
        m = re.search(r'<h1 class="publication-title">(.*?)</h1>', s, re.S)
    if not m:
        return ""
    raw = re.sub(r"</span>\s*<span", "</span>\n<span", m.group(1))
    return flatten(raw)


def flatten(h):
    """Only <br> is a line break, the HTML indentation is not."""
    h = re.sub(r"<br\s*/?>", "\x02", h)
    h = re.sub(r"<[^>]+>", "", h)
    h = unescape(h)
    h = re.sub(r"[ \t\r\n]+", " ", h)
    h = h.replace("\x02", "\n")
    h = re.sub(r" *\n *", "\n", h)
    return h.strip()


def refs_of(s):
    """The proof texts: letter -> [(label, book slug, chapter, verse)]."""
    out = {}
    for m in re.finditer(
        r"<span class='reference-number'>([a-z]+)</span>(.*?)(?=<span class='reference-number'>|$)",
        s, re.S,
    ):
        letter = m.group(1)
        places = []
        for a in re.finditer(
            r"<a href='/statenvertaling/([^/]+)/(\d+)/#(\d+)'[^>]*>(.*?)</a>", m.group(2), re.S
        ):
            places.append({
                "boek": a.group(1), "h": int(a.group(2)),
                "v": int(a.group(3)), "label": flatten(a.group(4)),
            })
        if places:
            out.setdefault(letter, []).extend(places)
    return out


def blocks_of(content):
    """The numbered text blocks (verses, questions, articles)."""
    positions = [(m.start(), int(m.group(1)))
                 for m in re.finditer(r'<div class="[^"]*\bverse verse-(\d+)\b[^"]*"', content)]
    out = []
    for i, (pos, nr) in enumerate(positions):
        end = positions[i + 1][0] if i + 1 < len(positions) else len(content)
        piece = content[pos:end]
        piece = re.sub(r'<span class="verse-number">.*?</span>', "", piece, count=1, flags=re.S)
        out.append((nr, piece))
    return out


def parse_page(group, key):
    s = read_cached(group, key)
    if not s:
        return None
    s = SCRIPT_STYLE.sub("", s)
    content = content_block(s)
    title = title_of(s)
    refs = refs_of(s)
    blocks = blocks_of(content)
    if not blocks:
        # running text: paragraphs from <p>, or else from <div class="text">
        paragraphs = [flatten(m.group(1)) for m in
                      re.finditer(r"<p[^>]*>(.*?)</p>", content, re.S)]
        if not any(len(a) > 1 for a in paragraphs):
            raw = "\n\n".join(
                m.group(1) for m in
                re.finditer(r'<div class="text[^"]*"[^>]*>(.*?)</div>', content, re.S)
            )
            if not raw.strip():
                raw = content
            paragraphs = [a.strip() for a in re.split(r"\n\s*\n", flatten(raw))]
        paragraphs = [a for a in paragraphs if len(a) > 1]
        blocks = [(i, a) for i, a in enumerate(paragraphs, 1)]
        rows = [{"n": n, "tekst": t} for n, t in blocks]
    else:
        rows = []
        for n, raw in blocks:
            # The letters restart at a for each question.
            cut = raw.find('<div class="verse-references"')
            own_refs = refs_of(raw[cut:]) if cut != -1 else {}
            if cut != -1:
                raw = raw[:cut]
            # Sentinel, so the position of the letter survives the stripping.
            marked = re.sub(r'<span class="verwijzing">\s*([a-z]+)\s*</span>',
                            lambda m: "\x01" + m.group(1) + "\x01", raw)
            text = flatten(marked)
            # Clean up whitespace first, otherwise the marks shift.
            text = re.sub(r"[ \t]+(\x01[a-z]+\x01)", r"\1", text)
            text = re.sub(r"[ \t]+([,.;:!?])", r"\1", text)
            text = re.sub(r"[ \t]{2,}", " ", text)
            marks = []
            while True:
                m = re.search(r"\x01([a-z]+)\x01", text)
                if not m:
                    break
                marks.append({"p": m.start(), "letter": m.group(1)})
                text = text[:m.start()] + text[m.end():]
            row = {"n": n, "tekst": text}
            if marks:
                row["merken"] = marks
            if own_refs:
                row["verwijzingen"] = own_refs
            rows.append(row)
    return {"titel": title, "rijen": rows, "verwijzingen": refs}


def parse_all():
    out = {}
    blocks_total = 0
    for group, key, path in pages():
        p = parse_page(group, key)
        if not p or not p["rijen"]:
            print(f"  ! empty: {group}/{key} ({path})")
            continue
        out.setdefault(group, {})[key] = p
        blocks_total += len(p["rijen"])
    dest = os.path.join(os.path.dirname(CACHE), "extras.json")
    with open(dest, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False)
    print(f"\nParsed: {sum(len(v) for v in out.values())} pages, {blocks_total} blocks")
    for g, v in out.items():
        blk = sum(len(p["rijen"]) for p in v.values())
        refs = sum(len(p["verwijzingen"]) for p in v.values())
        print(f"  {g:14s} {len(v):4d} pages  {blk:5d} blocks  {refs:5d} ref letters")
    print(f"-> {dest}")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "fetch"
    if cmd == "fetch":
        fetch()
    elif cmd == "parse":
        parse_all()
    else:
        print(__doc__)
