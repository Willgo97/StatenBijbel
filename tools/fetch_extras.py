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
USER_AGENT = ("Mozilla/5.0 (X11; Linux x86_64) StatenBijbel-offline/1.0 "
              "(persoonlijk gebruik; publiek-domeinteksten)")
CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cache-extras")
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
    for number in range(1, 151):
        out.append(("psalm", str(number), f"/1773/psalm/{number}/"))
    for i, slug in enumerate(HYMNS, 1):
        out.append(("gezang", str(i), f"/1773/gezang/{slug}/"))
    for number in range(1, 53):
        out.append(("catechismus", str(number), f"/catechismus/zondag/{number}/"))
    for number in range(1, 38):
        out.append(("ngb", str(number), f"/nederlandse-geloofsbelijdenis/artikel/{number}/"))
    for i, slug in enumerate(CANONS, 1):
        out.append(("leerregels", str(i), f"/dordtse-leerregels/{slug}/"))
    for i, slug in enumerate(CREEDS, 1):
        out.append(("belijdenis", str(i), f"/belijdenis/{slug}/"))
    for i, slug in enumerate(FORMS, 1):
        out.append(("formulier", str(i), f"/liturgische-formulieren/{slug}/"))
    for i, slug in enumerate(PRAYERS, 1):
        out.append(("gebed", str(i), f"/christelijke-gebeden/{slug}/"))
    return out


def cache_file(group, key):
    return os.path.join(CACHE_DIR, f"{group}-{key}.html.gz")


def fetch():
    os.makedirs(CACHE_DIR, exist_ok=True)
    all_pages = pages()
    new_count = 0
    for i, (group, key, path) in enumerate(all_pages, 1):
        dest = cache_file(group, key)
        if os.path.exists(dest) and os.path.getsize(dest) > 5000:
            continue
        request = urllib.request.Request(
            BASE_URL + path,
            headers={"User-Agent": USER_AGENT, "Accept-Encoding": "gzip",
                     "Accept-Language": "nl"},
        )
        for attempt in range(3):
            try:
                with urllib.request.urlopen(request, timeout=40) as response:
                    raw = response.read()
                    if response.headers.get("Content-Encoding") == "gzip":
                        raw = gzip.decompress(raw)
                break
            except (urllib.error.URLError, OSError) as error:
                if attempt == 2:
                    print(f"  ! failed {path}: {error}")
                    raw = None
                else:
                    time.sleep(2 * (attempt + 1))
        if not raw:
            continue
        with gzip.open(dest, "wb") as file:
            file.write(raw)
        new_count += 1
        if new_count % 25 == 0:
            print(f"  {i}/{len(all_pages)} ({group})")
        time.sleep(PAUSE)
    print(f"Done: {new_count} new pages, {len(all_pages)} total.")


SCRIPT_STYLE = re.compile(r"<(script|style)\b.*?</\1>", re.S | re.I)


def read_cached(group, key):
    path = cache_file(group, key)
    if not os.path.exists(path):
        return None
    with gzip.open(path, "rb") as file:
        return file.read().decode("utf-8", "replace")


def content_block(html):
    """The part of the page holding the actual text."""
    match = re.search(r'<div class="[^"]*publication-content[^"]*"[^>]*>', html)
    if not match:
        return ""
    start = match.end()
    end = html.find('<div class="col', start)
    section_end = html.find("</section>", start)
    if section_end != -1 and (end == -1 or section_end < end):
        end = section_end
    return html[start:end if end != -1 else len(html)]


def title_of(html):
    match = re.search(r'<h1 class="[^"]*chapter-title[^"]*">(.*?)</h1>', html, re.S)
    if not match:
        match = re.search(r'<h1 class="publication-title">(.*?)</h1>', html, re.S)
    if not match:
        return ""
    raw = re.sub(r"</span>\s*<span", "</span>\n<span", match.group(1))
    return flatten(raw)


def flatten(fragment):
    """Only <br> is a line break, the HTML indentation is not."""
    fragment = re.sub(r"<br\s*/?>", "\x02", fragment)
    fragment = re.sub(r"<[^>]+>", "", fragment)
    fragment = unescape(fragment)
    fragment = re.sub(r"[ \t\r\n]+", " ", fragment)
    fragment = fragment.replace("\x02", "\n")
    fragment = re.sub(r" *\n *", "\n", fragment)
    return fragment.strip()


def refs_of(html):
    """The proof texts: letter -> [(label, book slug, chapter, verse)]."""
    out = {}
    for match in re.finditer(
        r"<span class='reference-number'>([a-z]+)</span>(.*?)(?=<span class='reference-number'>|$)",
        html, re.S,
    ):
        letter = match.group(1)
        places = []
        for link in re.finditer(
            r"<a href='/statenvertaling/([^/]+)/(\d+)/#(\d+)'[^>]*>(.*?)</a>", match.group(2), re.S
        ):
            places.append({
                "boek": link.group(1), "h": int(link.group(2)),
                "v": int(link.group(3)), "label": flatten(link.group(4)),
            })
        if places:
            out.setdefault(letter, []).extend(places)
    return out


def blocks_of(content):
    """The numbered text blocks (verses, questions, articles)."""
    positions = [(match.start(), int(match.group(1)))
                 for match in re.finditer(r'<div class="[^"]*\bverse verse-(\d+)\b[^"]*"', content)]
    out = []
    for i, (position, number) in enumerate(positions):
        end = positions[i + 1][0] if i + 1 < len(positions) else len(content)
        piece = content[position:end]
        piece = re.sub(r'<span class="verse-number">.*?</span>', "", piece, count=1, flags=re.S)
        out.append((number, piece))
    return out


def parse_page(group, key):
    html = read_cached(group, key)
    if not html:
        return None
    html = SCRIPT_STYLE.sub("", html)
    content = content_block(html)
    title = title_of(html)
    refs = refs_of(html)
    blocks = blocks_of(content)
    if not blocks:
        # running text: paragraphs from <p>, or else from <div class="text">
        paragraphs = [flatten(match.group(1)) for match in
                      re.finditer(r"<p[^>]*>(.*?)</p>", content, re.S)]
        if not any(len(paragraph) > 1 for paragraph in paragraphs):
            raw = "\n\n".join(
                match.group(1) for match in
                re.finditer(r'<div class="text[^"]*"[^>]*>(.*?)</div>', content, re.S)
            )
            if not raw.strip():
                raw = content
            paragraphs = [paragraph.strip() for paragraph in re.split(r"\n\s*\n", flatten(raw))]
        paragraphs = [paragraph for paragraph in paragraphs if len(paragraph) > 1]
        blocks = [(i, paragraph) for i, paragraph in enumerate(paragraphs, 1)]
        rows = [{"n": number, "tekst": text} for number, text in blocks]
    else:
        rows = []
        for number, raw in blocks:
            # The letters restart at a for each question.
            cut = raw.find('<div class="verse-references"')
            own_refs = refs_of(raw[cut:]) if cut != -1 else {}
            if cut != -1:
                raw = raw[:cut]
            # Sentinel, so the position of the letter survives the stripping.
            marked = re.sub(r'<span class="verwijzing">\s*([a-z]+)\s*</span>',
                            lambda match: "\x01" + match.group(1) + "\x01", raw)
            text = flatten(marked)
            # Clean up whitespace first, otherwise the marks shift.
            text = re.sub(r"[ \t]+(\x01[a-z]+\x01)", r"\1", text)
            text = re.sub(r"[ \t]+([,.;:!?])", r"\1", text)
            text = re.sub(r"[ \t]{2,}", " ", text)
            marks = []
            while True:
                match = re.search(r"\x01([a-z]+)\x01", text)
                if not match:
                    break
                marks.append({"p": match.start(), "letter": match.group(1)})
                text = text[:match.start()] + text[match.end():]
            row = {"n": number, "tekst": text}
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
        page = parse_page(group, key)
        if not page or not page["rijen"]:
            print(f"  ! empty: {group}/{key} ({path})")
            continue
        out.setdefault(group, {})[key] = page
        blocks_total += len(page["rijen"])
    dest = os.path.join(os.path.dirname(CACHE_DIR), "extras.json")
    with open(dest, "w", encoding="utf-8") as file:
        json.dump(out, file, ensure_ascii=False)
    print(f"\nParsed: {sum(len(group_pages) for group_pages in out.values())} pages, "
          f"{blocks_total} blocks")
    for group, group_pages in out.items():
        block_count = sum(len(page["rijen"]) for page in group_pages.values())
        ref_letters = sum(len(page["verwijzingen"]) for page in group_pages.values())
        print(f"  {group:14s} {len(group_pages):4d} pages  {block_count:5d} blocks  "
              f"{ref_letters:5d} ref letters")
    print(f"-> {dest}")


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else "fetch"
    if command == "fetch":
        fetch()
    elif command == "parse":
        parse_all()
    else:
        print(__doc__)
