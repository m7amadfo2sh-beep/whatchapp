#!/usr/bin/env python3
"""Builds app/src/main/assets/content.json from verified sources.

Nothing here is typed from memory: every Arabic text comes verbatim from
  - quran-json (npm, CC BY 4.0): Uthmani Quran text from QuranEnc
  - azkar (npm, CC BY-NC-ND 4.0): Hisn al-Muslim adhkar and duas
and each 5-minute dhikr phrase must appear word-for-word in the azkar data.

Usage: python3 tools/build_content.py      (needs npm; downloads both packages)
Edit the lists below to change what appears, then rebuild the app.
"""
import json
import os
import subprocess
import sys
import tarfile
import tempfile
import unicodedata

QURAN_PKG = "quran-json@3.1.2"
AZKAR_PKG = "azkar@1.0.2"

# Shown with the 5-minute buzz, in this order. Each must be an exact substring
# of an entry in the azkar data (checked below).
DHIKR = [
    "سُبْحَانَ اللَّهِ",
    "الْحَمْدُ لِلَّهِ",
    "لاَ إِلَهَ إِلاَّ اللَّهُ",
    "اللَّهُ أَكْبَرُ",
    "أَسْتَغْفِرُ اللَّهَ",
    "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
    "سُبْحانَ اللَّهِ الْعَظِيمِ",
    "لاَ حَوْلَ وَلاَ قُوَّةَ إِلاَّ بِاللَّهِ",
    "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ، وَعَلَى آلِ مُحَمَّدٍ",
    "سُبْحَانَ اللَّهِ الْعَظِيمِ وَبِحَمْدِهِ",
    "لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ، وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
]

# Hourly dua cards: 05:00-11:59 morning, 16:00-20:59 evening, other hours general.
MORNING = ["أذكار الصباح"]
EVENING = ["أذكار المساء"]
GENERAL = [
    "التسبيح، التحميد، التهليل، التكبير",
    "الاستغفار و التوبة",
    "دعاء الهم والحزن",
    "دعاء الكرب",
    "الدعاء بعد التشهد الأخير قبل السلام",
    "الأذكار بعد السلام من الصلاة",
    "دعاء السجود",
    "دعاء من أصابه وسوسة في الإيمان",
    "دعاء قضاء الدين",
    "دعاء من استصعب عليه أمر",
    "ما يقول ويفعل من أذنب ذنبا",
    "دعاء الخوف من الشرك",
    "فضل الصلاة على النبي صلى الله عليه و سلم",
    "الصلاة على النبي بعد التشهد",
    "دعاء التعجب والأمر السار",
    "كفارة اﻟﻤﺠلس",
    "الرُّقية الشرعية من السنة النبوية",
]
MAX_DUA_CHARS = 420  # longer entries don't fit a watch screen well

# Hourly verse cards: (surah, first ayah, last ayah).
VERSES = [
    (1, 1, 7), (2, 255, 255), (2, 286, 286), (2, 201, 201), (2, 186, 186),
    (2, 152, 152), (2, 153, 153), (2, 156, 156), (2, 250, 250), (3, 8, 8),
    (3, 147, 147), (3, 173, 173), (7, 23, 23), (9, 129, 129), (13, 28, 28),
    (14, 40, 41), (17, 24, 24), (18, 10, 10), (20, 25, 28), (20, 114, 114),
    (21, 87, 87), (23, 118, 118), (25, 74, 74), (28, 24, 24), (29, 69, 69),
    (33, 56, 56), (39, 53, 53), (40, 60, 60), (50, 16, 16), (59, 10, 10),
    (65, 3, 3), (71, 28, 28), (94, 5, 6), (112, 1, 4), (113, 1, 5), (114, 1, 6),
]

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content.json")
ARABIC_DIGITS = str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩")


def fetch(pkg, workdir):
    name = subprocess.check_output(["npm", "pack", pkg, "--silent"], cwd=workdir, text=True)
    path = os.path.join(workdir, name.strip().splitlines()[-1])
    dest = os.path.join(workdir, pkg.split("@")[0])
    with tarfile.open(path) as tar:
        tar.extractall(dest, filter="data")
    return os.path.join(dest, "package")


def find_verbatim(phrase, texts):
    """Returns the source's own characters for `phrase`.

    Arabic marks (e.g. shadda + fatha) can be stored in either order and still
    be the same text, so the match is by canonical Unicode equivalence, but the
    returned string is copied exactly from the source.
    """
    want = unicodedata.normalize("NFC", phrase)
    n = len(phrase)
    for text in texts:
        for i, ch in enumerate(text):
            if ch != phrase[0]:
                continue
            for j in range(i + n - 3, i + n + 4):
                if 0 < j <= len(text) and unicodedata.normalize("NFC", text[i:j]) == want:
                    return text[i:j]
    return None


def ar(n):
    return str(n).translate(ARABIC_DIGITS)


def main():
    with tempfile.TemporaryDirectory() as tmp:
        quran = json.load(open(os.path.join(fetch(QURAN_PKG, tmp), "dist", "quran.json"), encoding="utf-8"))
        azkar = json.load(open(os.path.join(fetch(AZKAR_PKG, tmp), "azkars.json"), encoding="utf-8"))

    all_zekr = [z["zekr"] for items in azkar.values() for z in items]
    dhikr = []
    for phrase in DHIKR:
        found = find_verbatim(phrase, all_zekr)
        if found is None:
            sys.exit(f"Dhikr phrase not found in the azkar data: {phrase}")
        dhikr.append(found)

    def duas(categories):
        cards = []
        for cat in categories:
            if cat not in azkar:
                sys.exit(f"Unknown azkar category: {cat}")
            for z in azkar[cat]:
                text = z["zekr"].strip()
                if len(text) > MAX_DUA_CHARS:
                    continue
                card = {"title": cat, "text": text}
                count = str(z.get("count") or "").strip()
                if count.isdigit() and int(count) > 1:
                    card["count"] = int(count)
                if z.get("reference"):
                    card["source"] = z["reference"].strip()
                cards.append(card)
        return cards

    verses = []
    for surah, first, last in VERSES:
        chapter = quran[surah - 1]
        assert chapter["id"] == surah
        ayat = {v["id"]: v["text"] for v in chapter["verses"]}
        parts = [ayat[n] + (f"\u00a0﴿{ar(n)}﴾" if last > first else "") for n in range(first, last + 1)]
        where = ar(first) if first == last else f"{ar(first)}–{ar(last)}"
        verses.append({
            "title": f"سورة {chapter['name']} · {where}",
            "text": " ".join(parts),
            "source": f"{surah}:{first}" + (f"-{last}" if last > first else ""),
        })

    content = {
        "credits": "Quran: quran-json (QuranEnc Uthmani text, CC BY 4.0). "
                   "Adhkar: Hisn al-Muslim via azkar (CC BY-NC-ND 4.0).",
        "dhikr": dhikr,
        "duas": {"morning": duas(MORNING), "evening": duas(EVENING), "general": duas(GENERAL)},
        "verses": verses,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(content, f, ensure_ascii=False, indent=1)

    print(f"dhikr: {len(dhikr)}")
    for pool, cards in content["duas"].items():
        print(f"duas.{pool}: {len(cards)}")
    print(f"verses: {len(verses)} (longest {max(len(v['text']) for v in verses)} chars)")
    print(f"wrote {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
