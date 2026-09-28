# Credits

## Quran text
Uthmani Quran text from **The Noble Qur'an Encyclopedia (QuranEnc)** — https://quranenc.com —
distributed in the [`quran-json`](https://github.com/risan/quran-json) package by Risan Bagja
Pradana, licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Used verbatim.

## Duas, adhkar and dhikr
From **Hisn al-Muslim (حصن المسلم)** by Sa'id bin Ali bin Wahf al-Qahtani, as distributed in
the [`azkar`](https://www.npmjs.com/package/azkar) package, licensed under
[CC BY-NC-ND 4.0](https://creativecommons.org/licenses/by-nc-nd/4.0/). Entries are used
verbatim, without changes, in a non-commercial app.

## Font
**Amiri** by Khaled Hosny — https://www.amirifont.org — licensed under the
[SIL Open Font License 1.1](https://openfontlicense.org). Bundled unmodified
(`app/src/main/res/font/amiri.ttf`, from the Google Fonts build).

## Speech model and engine
- **whisper-base-ar-quran** by Tarteel AI — https://huggingface.co/tarteel-ai/whisper-base-ar-quran —
  Apache License 2.0. Converted to whisper.cpp format and quantized (q5_1) by
  `.github/workflows/model.yml`; bundled in the APK at build time.
- **whisper.cpp** by Georgi Gerganov and contributors — https://github.com/ggerganov/whisper.cpp —
  MIT License (git submodule `app/src/main/cpp/whisper.cpp`, v1.7.6).
- Test recordings (not shipped in the app): Mishary Rashid Alafasy via https://everyayah.com.
