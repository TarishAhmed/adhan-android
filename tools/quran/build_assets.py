#!/usr/bin/env python3
"""One-time generator for the bundled Quran assets.

Writes app/src/main/assets/quran/quran.json:
  {"source": ..., "surahs": [{"n", "name", "ar", "meaning", "place", "verses": [arabic, ...]}]}

The Arabic is the Indo-Pak text of alislam.org's Quran reader (api.readquran.app, font 1), in
Ahmadiyya numbering: the Bismillah is verse 1 of every surah except At-Tawbah, so verses[0] is
always verse 1. Each verse keeps its own end ornament as alislam prints it, e.g. ﴿۵﴾ or ﴿٪۷﴾
(٪ marks a rukūʿ). Surah names come from quran.com.

quran.com's Indo-Pak text is not used: it encodes some marks as Private Use Area characters
that only render in quran.com's own font, so they show as stray CJK glyphs elsewhere.

Usage: python3 tools/quran/build_assets.py   (~680 requests, about 5 minutes)
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
        verses = [r["ar"].strip() for r in rows]
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


if __name__ == "__main__":
    main()
