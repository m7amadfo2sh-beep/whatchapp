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

# Shown with the 5-minute buzz (when enabled), in this order. These are only
# search keys: each is looked up in the azkar data by its letters (vowel marks
# ignored) and the app gets the source's own fully-vowelled text. A phrase that
# isn't found is left out and reported.
DHIKR = [
    "سبحان الله",
    "الحمد لله",
    "لا إله إلا الله",
    "الله أكبر",
    "أستغفر الله",
    "سبحان الله وبحمده",
    "سبحان الله العظيم",
    "لا حول ولا قوة إلا بالله",
    "اللهم صل على محمد وعلى آل محمد",
    "سبحان الله العظيم وبحمده",
    "لا إله إلا الله وحده لا شريك له له الملك وله الحمد وهو على كل شيء قدير",
    "سبحان الله والحمد لله ولا إله إلا الله والله أكبر",
    "أستغفر الله وأتوب إليه",
    "رب اغفر لي",
    "حسبي الله لا إله إلا هو عليه توكلت وهو رب العرش العظيم",
    "رضيت بالله ربا وبالإسلام دينا وبمحمد صلى الله عليه وسلم نبيا",
    "يا حي يا قيوم برحمتك أستغيث",
    "سبحان الله وبحمده عدد خلقه ورضا نفسه وزنة عرشه ومداد كلماته",
    "لا إله إلا أنت سبحانك إني كنت من الظالمين",
    "اللهم إني أسألك العفو والعافية في الدنيا والآخرة",
    "اللهم أعني على ذكرك وشكرك وحسن عبادتك",
    "يا مقلب القلوب ثبت قلبي على دينك",
    "أعوذ بكلمات الله التامات من شر ما خلق",
    "بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم",
    "لا إله إلا الله وحده لا شريك له",
    "اللهم صل وسلم على نبينا محمد",
]

# Hourly dua cards: 05:00-11:59 morning, 16:00-20:59 evening, 21:00-23:59
# night, other hours general. Category names are matched after Unicode
# normalization, and a name may be the start of a longer category name.
MORNING = ["أذكار الصباح", "أذكار الاستيقاظ من النوم"]
EVENING = ["أذكار المساء"]
NIGHT = ["أذكار النوم", "الدعاء إذا تقلب في الليل", "دعاء الفزع في النوم"]
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
    "الرُّقية الشرعية من القرآن الكريم",
    "أذكار الآذان",
    "دعاء الاستفتاح",
    "دعاء الركوع",
    "دعاء الرفع من الركوع",
    "دعاء الجلسة بين السجدتين",
    "دعاء سجود التلاوة",
    "التشهد",
    "دعاء قنوت الوتر",
    "الذكر عقب السلام من الوتر",
    "دعاء لقاء العدو",
    "دعاء من خاف ظلم السلطان",
    "ما يقول من خاف قوما",
    "دعاء الوسوسة في الصلاة",
    "دعاء طرد الشيطان",
    "الدعاء حينما يقع ما لا يرضاه",
    "دعاء من أصيب بمصيبة",
    "ما يقول من أحس وجعا",
    "دعاء الغضب",
    "دعاء من رأى مبتلى",
    "ما يقال في المجلس",
    "الدعاء لمن صنع إليك معروفا",
    "ما يقول من أتاه أمر يسره أو يكرهه",
    "ما يفعل من أتاه أمر يسره",
    "إفشاء السلام",
    "كيف كان النبي يسبح",
    "دعاء كراهية الطيرة",
    "من أنواع الخير والآداب الجامعة",
]
MAX_DUA_CHARS = 700  # cards auto-scroll; much longer ones are too long for a watch

# Hourly verse cards: (surah, first ayah, last ayah).
# Quranic duas; each must contain «ربنا» or «رب…» (checked below).
VERSE_DUAS = [
    (2, 127, 128), (2, 201, 201), (2, 250, 250), (2, 286, 286), (3, 8, 9),
    (3, 16, 16), (3, 38, 38), (3, 53, 53), (3, 147, 147), (3, 191, 192),
    (3, 193, 194), (5, 83, 83), (7, 23, 23), (7, 47, 47), (7, 89, 89),
    (7, 126, 126), (7, 151, 151), (10, 85, 86), (11, 47, 47), (12, 101, 101),
    (14, 35, 35), (14, 40, 41), (17, 24, 24), (17, 80, 80), (18, 10, 10),
    (20, 25, 28), (20, 114, 114), (21, 89, 89), (23, 29, 29), (23, 97, 98),
    (23, 109, 109), (23, 118, 118), (25, 65, 66), (25, 74, 74), (26, 83, 85),
    (26, 169, 169), (27, 19, 19), (28, 16, 16), (28, 24, 24), (29, 30, 30),
    (37, 100, 100), (38, 35, 35), (40, 7, 9), (44, 12, 12), (46, 15, 15),
    (59, 10, 10), (60, 4, 5), (66, 8, 8), (66, 11, 11), (71, 28, 28),
]
# Other well-known verses and short surahs.
VERSE_OTHER = [
    (1, 1, 7), (2, 255, 255), (2, 285, 285), (3, 26, 27), (3, 190, 190),
    (59, 22, 24), (57, 3, 3), (24, 35, 35), (65, 3, 3), (13, 28, 28),
    (39, 53, 53), (94, 1, 8), (97, 1, 5), (103, 1, 3), (108, 1, 3),
    (110, 1, 3), (112, 1, 4), (113, 1, 5), (114, 1, 6), (2, 152, 152),
    (2, 153, 153), (2, 156, 156), (2, 186, 186), (9, 129, 129), (21, 87, 87),
    (29, 69, 69), (33, 56, 56), (40, 60, 60), (50, 16, 16), (3, 173, 173),
    (2, 45, 45), (16, 97, 97), (29, 45, 45), (33, 41, 42), (35, 15, 15),
    (51, 56, 56), (67, 1, 2), (93, 1, 11), (36, 82, 83), (2, 163, 163),
    (6, 162, 163), (15, 98, 99), (3, 139, 139), (12, 87, 87), (49, 13, 13),
    (4, 110, 110), (2, 186, 186), (18, 46, 46), (41, 34, 34), (11, 6, 6),
]
MAX_VERSE_CHARS = 700

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content.json")
QURAN_OUT = os.path.join(ROOT, "app", "src", "main", "assets", "quran.txt")
SURAHS_OUT = os.path.join(ROOT, "app", "src", "main", "assets", "quran_surahs.txt")
ARABIC_DIGITS = str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩")


def fetch(pkg, workdir):
    name = subprocess.check_output(["npm", "pack", pkg, "--silent"], cwd=workdir, text=True)
    path = os.path.join(workdir, name.strip().splitlines()[-1])
    dest = os.path.join(workdir, pkg.split("@")[0])
    with tarfile.open(path) as tar:
        tar.extractall(dest, filter="data")
    return os.path.join(dest, "package")


MARKS = {c for c in map(chr, range(0x610, 0x620))} | {c for c in map(chr, range(0x64B, 0x660))} | \
    {"\u0670", "\u0640"} | {c for c in map(chr, range(0x6D6, 0x6EE))}
LETTER_MAP = str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي", "ة": "ه"})


def skeleton(text):
    """Letters only (no vowel marks, tatweel or punctuation), with a map back to source positions."""
    text = unicodedata.normalize("NFKC", text)
    out, pos = [], []
    for i, ch in enumerate(text):
        if ch in MARKS:
            continue
        if ch.isalpha():
            out.append(ch.translate(LETTER_MAP))
            pos.append(i)
        elif out and out[-1] != " ":
            out.append(" ")
            pos.append(i)
    return "".join(out), pos, text


def find_by_letters(key, texts):
    """The source's own text (all its vowel marks) for the words in `key`, or None."""
    want = " " + skeleton(key)[0].strip() + " "
    found = []
    for text in texts:
        sk, pos, norm_text = skeleton(text)
        hay = " " + sk + " "
        i = hay.find(want)
        while i >= 0:
            start = pos[i]  # hay has one leading space: hay[i+1] == sk[i]
            end = pos[i + len(want) - 3] + 1
            while end < len(norm_text) and norm_text[end] in MARKS:
                end += 1
            found.append(norm_text[start:end])
            i = hay.find(want, i + 1)
    if not found:
        return None
    # The same words often appear several times; use the most fully vowelled
    # occurrence with the fewest decorative tatweels.
    def score(t):
        return sum(ch in MARKS and ch != "\u0640" for ch in t) - 2 * t.count("\u0640")
    return max(found, key=score)


def resolve_category(name, azkar):
    want = unicodedata.normalize("NFKC", name)
    matches = [k for k in azkar if unicodedata.normalize("NFKC", k).startswith(want)]
    if len(matches) != 1:
        sys.exit(f"Azkar category {name!r} matches {len(matches)} categories")
    return matches[0]


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
    for key in DHIKR:
        found = find_by_letters(key, all_zekr)
        if found is None:
            print(f"  skipped dhikr (not in source): {key}")
        elif found not in dhikr:
            dhikr.append(found)

    def duas(categories):
        cards = []
        for name in categories:
            cat = resolve_category(name, azkar)
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

    def interleave(a, b):
        out = []
        for i in range(max(len(a), len(b))):
            out += a[i:i + 1] + b[i:i + 1]
        return out

    verses = []
    seen = set()
    duas_refs = set(VERSE_DUAS)
    for surah, first, last in interleave(VERSE_OTHER, VERSE_DUAS):
        if (surah, first, last) in seen:
            continue
        seen.add((surah, first, last))
        chapter = quran[surah - 1]
        assert chapter["id"] == surah
        ayat = {v["id"]: v["text"] for v in chapter["verses"]}
        parts = [ayat[n] + (f"\u00a0﴿{ar(n)}﴾" if last > first else "") for n in range(first, last + 1)]
        where = ar(first) if first == last else f"{ar(first)}–{ar(last)}"
        text_letters = skeleton(" ".join(ayat[n] for n in range(first, last + 1)))[0].split()
        if (surah, first, last) in duas_refs and not any(w == "ربنا" or w.startswith("رب") for w in text_letters):
            sys.exit(f"{surah}:{first}-{last} is listed as a dua but contains no ربنا/رب")
        if sum(len(ayat[n]) for n in range(first, last + 1)) > MAX_VERSE_CHARS:
            print(f"  skipped long verse {surah}:{first}-{last}")
            continue
        print(f"  {surah}:{first}-{last}  {' '.join(ayat[first].split()[:4])}")
        verses.append({
            "title": f"سورة {chapter['name']} · {where}",
            "text": " ".join(parts),
            "source": f"{surah}:{first}" + (f"-{last}" if last > first else ""),
        })

    content = {
        "credits": "Quran: quran-json (QuranEnc Uthmani text, CC BY 4.0). "
                   "Adhkar: Hisn al-Muslim via azkar (CC BY-NC-ND 4.0).",
        "dhikr": dhikr,
        "duas": {"morning": duas(MORNING), "evening": duas(EVENING), "night": duas(NIGHT),
                 "general": duas(GENERAL)},
        "verses": verses,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(content, f, ensure_ascii=False, indent=1)

    # The whole Quran for the recitation checker: one ayah per line, "surah|ayah|text",
    # and surah names in quran_surahs.txt ("number|name").
    with open(QURAN_OUT, "w", encoding="utf-8") as f:
        for chapter in quran:
            for v in chapter["verses"]:
                f.write(f"{chapter['id']}|{v['id']}|{v['text']}\n")
    with open(SURAHS_OUT, "w", encoding="utf-8") as f:
        for chapter in quran:
            f.write(f"{chapter['id']}|{chapter['name']}\n")
    print(f"quran: {sum(len(c['verses']) for c in quran)} ayat -> {os.path.relpath(QURAN_OUT, ROOT)}")

    print(f"dhikr: {len(dhikr)}")
    for pool, cards in content["duas"].items():
        print(f"duas.{pool}: {len(cards)}")
    print(f"verses: {len(verses)} (longest {max(len(v['text']) for v in verses)} chars)")
    print(f"wrote {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
