#!/usr/bin/env python3
"""One-time generator for the bundled Quran assets.

Writes app/src/main/assets/quran/quran.json:
  {"source": ..., "surahs": [{"n", "name", "ar", "meaning", "place", "verses": [arabic, ...]}]}

Verses use Ahmadi numbering: the Bismillah is verse 1 of every surah except At-Tawbah (which has
none) and Al-Fatiha (where it is already verse 1). So verses[0] is always verse 1. The Arabic is
quran.com's Indo-Pak text; translations are fetched by the app from alislam.org, which numbers
verses the same way.

Usage: python3 tools/quran/build_assets.py   (two requests)
"""
import json
import pathlib
import urllib.request

VERSES = "https://api.quran.com/api/v4/quran/verses/indopak"
CHAPTERS = "https://api.quran.com/api/v4/chapters?language=en"
OUT = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/assets/quran/quran.json"
DIRECTION_MARKS = dict.fromkeys(map(ord, "‎‏‪‫‬"))


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "adhan-asset-builder"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)


def main():
    chapters = get_json(CHAPTERS)["chapters"]
    by_surah = {}
    for v in get_json(VERSES)["verses"]:
        s, n = map(int, v["verse_key"].split(":"))
        by_surah.setdefault(s, []).append((n, v["text_indopak"].translate(DIRECTION_MARKS).strip()))

    bismillah = dict(by_surah[1])[1]
    surahs = []
    for c in chapters:
        n = c["id"]
        rows = sorted(by_surah[n])
        assert [k for k, _ in rows] == list(range(1, c["verses_count"] + 1)), n
        verses = [t for _, t in rows]
        if n not in (1, 9):
            verses.insert(0, bismillah)
        surahs.append({
            "n": n,
            "name": c["name_simple"],
            "ar": c["name_arabic"],
            "meaning": c["translated_name"]["name"],
            "place": c["revelation_place"],
            "verses": verses,
        })

    total = sum(len(s["verses"]) for s in surahs)
    assert total == 6236 + 112, total  # 6236 standard verses + 112 Bismillahs
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"source": "quran.com (Indo-Pak script)", "surahs": surahs}, ensure_ascii=False, separators=(",", ":")))
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB, {total} verses)")


if __name__ == "__main__":
    main()
