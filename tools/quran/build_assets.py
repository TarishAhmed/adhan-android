#!/usr/bin/env python3
"""One-time generator for the bundled Quran assets.

Writes app/src/main/assets/quran/quran.json:
  {"source": ..., "surahs": [{"n", "name", "ar", "meaning", "place", "verses": [arabic, ...]}]}

The Arabic is the Indo-Pak text of alislam.org's Quran reader (api.readquran.app, font 1), in
Ahmadiyya numbering: the Bismillah is verse 1 of every surah except At-Tawbah, so verses[0] is
always verse 1. Each verse keeps its own end ornament as alislam prints it, e.g. ﴿۵﴾, or ﴿ࣖ۷﴾
where the small high ain marks a rukūʿ. Surah names come from quran.com.

quran.com's Indo-Pak text is not used: it encodes some marks as Private Use Area characters
that only render in quran.com's own font, so they show as stray CJK glyphs elsewhere.

alislam's text also uses a few legacy code points that its own font (ManzoorNaskh) turns into the
proper Quranic annotation marks through its `ccmp` substitutions, e.g. U+066A ARABIC PERCENT SIGN
for the rukūʿ mark U+08D6 SMALL HIGH AIN. LEGACY_MARKS applies the same mapping to the text so it
renders correctly in any Quran font (in Scheherazade New, `٪` would otherwise show as a percent sign).

Also writes app/src/main/assets/quran/translit.json ({"source", "surahs": [[verse 1, ...], ...]}),
an English transliteration in the same Ahmadi numbering, joined from quran.com's word-by-word
transliteration (its verse-level one runs words together). The Bismillah verse reuses 1:1.

Usage: python3 tools/quran/build_assets.py                (~680 requests, about 5 minutes)
       python3 tools/quran/build_assets.py --renormalize  (re-apply LEGACY_MARKS to the existing file)
       python3 tools/quran/build_assets.py --translit     (transliteration only, ~140 requests)
"""
import json
import pathlib
import sys
import time
import unicodedata
import urllib.request

READQURAN = "https://api.readquran.app/chapter/{n}:{start}-{end}"
CHAPTERS = "https://api.quran.com/api/v4/chapters?language=en"
OUT = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/assets/quran/quran.json"
TRANSLIT_OUT = OUT.with_name("translit.json")
WORDS = "https://api.quran.com/api/v4/verses/by_chapter/{n}?words=true&word_fields=char_type_name&per_page=50&page={page}"
PAGE = 10  # the API returns at most 10 verses per request
DELAY = 0.3  # seconds between requests, to be gentle on alislam.org


def get_json(url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json", "User-Agent": "adhan-asset-builder"}
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers), timeout=60) as r:
                return json.load(r)
        except Exception as e:  # transient network errors: back off and retry
            if attempt == 3:
                raise
            print(f"  retry {url}: {e}", file=sys.stderr)
            time.sleep(5 * (attempt + 1))


# From ManzoorNaskh-alislam's GSUB lookup 4 (ccmp): legacy code point -> Unicode Quranic mark.
LEGACY_MARKS = str.maketrans({
    "\u066A": "\u08D6",  # ARABIC PERCENT SIGN -> SMALL HIGH AIN (rukūʿ)
    "\u066D": "\u08D7",  # FIVE POINTED STAR -> SMALL HIGH QAF
    "\u0603": "\u08E1",  # SIGN SAFHA -> SMALL HIGH SIGN SAFHA
    "\u06EC": "\u08E2",  # ROUNDED HIGH STOP WITH FILLED CENTRE -> DISPUTED END OF AYAH
    "\u06DF": "\u08DE",  # SMALL HIGH ROUNDED ZERO -> SMALL HIGH WORD QIF
    "\u065D": "\u08DF",  # REVERSED DAMMA -> SMALL HIGH WORD WAQFA
})


def normalize(text):
    out = text.translate(LEGACY_MARKS)
    left = {f"U+{ord(c):04X}" for c in out if ord(c) in (0x066A, 0x066D, 0x0603, 0x06EC, 0x06DF, 0x065D)}
    assert not left, left
    return out


def renormalize():
    data = json.loads(OUT.read_text())
    for s in data["surahs"]:
        s["verses"] = [normalize(v) for v in s["verses"]]
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    print(f"renormalized {OUT}")


def standard(ch):
    """Arabic blocks, Arabic presentation forms (the ﴿﴾ ornaments), and Unicode spaces and
    format characters (e.g. U+202F narrow no-break space). Private-use characters are rejected."""
    o = ord(ch)
    return (0x0600 <= o <= 0x06FF or 0x0750 <= o <= 0x077F or 0x08A0 <= o <= 0x08FF
            or 0xFB50 <= o <= 0xFDFF or 0xFE70 <= o <= 0xFEFF
            or unicodedata.category(ch) in ("Zs", "Cf"))


def main():
    chapters = get_json(CHAPTERS)["chapters"]
    surahs = []
    for c in chapters:
        n = c["id"]
        count = c["verses_count"] + (0 if n in (1, 9) else 1)  # + Bismillah verse
        rows = []
        for start in range(1, count + 1, PAGE):
            rows += get_json(READQURAN.format(n=n, start=start, end=min(start + PAGE - 1, count)), {"f": 1})
            time.sleep(DELAY)
        rows.sort(key=lambda r: r["v"])
        if [r["v"] for r in rows] != list(range(1, count + 1)):
            sys.exit(f"surah {n}: expected verses 1..{count}, got {len(rows)}")
        verses = [normalize(r["ar"].strip()) for r in rows]
        for i, t in enumerate(verses, 1):
            odd = {f"U+{ord(ch):04X}" for ch in t if not standard(ch)}
            if odd:
                sys.exit(f"{n}:{i} has non-standard characters {sorted(odd)}")
        surahs.append({
            "n": n,
            "name": c["name_simple"],
            "ar": c["name_arabic"],
            "meaning": c["translated_name"]["name"],
            "place": c["revelation_place"],
            "verses": verses,
        })
        print(f"{n:3} {c['name_simple']:<20} {count} verses", flush=True)

    total = sum(len(s["verses"]) for s in surahs)
    assert total == 6236 + 112, total  # 6236 standard verses + 112 Bismillahs
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"source": "alislam.org (Indo-Pak script)", "surahs": surahs}, ensure_ascii=False, separators=(",", ":")))
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB, {total} verses)")


def translit():
    surahs = []
    for n in range(1, 115):
        verses, page = [], 1
        while page:
            d = get_json(WORDS.format(n=n, page=page))
            for v in d["verses"]:
                words = [w["transliteration"]["text"] for w in v["words"]
                         if w["char_type_name"] == "word" and w["transliteration"]["text"]]
                verses.append(" ".join(words))
            page = d["pagination"]["next_page"]
            time.sleep(DELAY)
        surahs.append(verses)
        print(f"{n:3} {len(verses)} verses", flush=True)
    bismillah = surahs[0][0]
    surahs = [s if n in (1, 9) else [bismillah] + s for n, s in enumerate(surahs, 1)]
    arabic = json.loads(OUT.read_text())["surahs"]
    for n, (t, a) in enumerate(zip(surahs, arabic), 1):
        assert len(t) == len(a["verses"]), (n, len(t), len(a["verses"]))
    TRANSLIT_OUT.write_text(json.dumps({"source": "quran.com word-by-word transliteration", "surahs": surahs},
                                       ensure_ascii=False, separators=(",", ":")))
    print(f"wrote {TRANSLIT_OUT} ({TRANSLIT_OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    if "--renormalize" in sys.argv:
        renormalize()
    elif "--translit" in sys.argv:
        translit()
    else:
        main()
