# Hourly Buzz — Galaxy Watch 7 (Wear OS)

A Wear OS app for the Samsung Galaxy Watch 7 (and any Wear OS 3+ watch: Galaxy Watch 4 and
newer, Pixel Watch, …). It:

- **vibrates every 5 minutes** (on the clock: :00, :05, :10 …) and posts a **dhikr** as a quiet
  notification: سبحان الله, الحمد لله, لا إله إلا الله, الله أكبر, أستغفر الله … It does **not**
  turn the screen on; you see it when you look at the watch. Tap it to show it full screen.
- **every hour** (on the hour) **turns the screen on** and shows a full‑screen **dua or Quran
  verse** in Arabic:
  - odd hours: a Quran verse (36 well‑known verses and Quranic duas: آية الكرسي, الإخلاص,
    المعوذتين, الفاتحة, …)
  - even hours: أذكار الصباح (05:00–11:59), أذكار المساء (16:00–20:59), other hours general
    duas and adhkar from Hisn al‑Muslim (146 in total)

  Each collection goes in order and starts again at the end, so everything is shown before
  anything repeats.

Everything is built into the app, so it works offline and nothing is downloaded on the watch.

## Where the text comes from

None of the Arabic text was typed by hand. `tools/build_content.py` copies it word for word
from published sources into `app/src/main/assets/content.json`:

- **Quran:** the Uthmani text from [QuranEnc](https://quranenc.com) (The Noble Qur'an
  Encyclopedia), via the [`quran-json`](https://github.com/risan/quran-json) package (CC BY 4.0).
- **Duas, adhkar and dhikr:** Hisn al‑Muslim (حصن المسلم), via the
  [`azkar`](https://www.npmjs.com/package/azkar) package (CC BY‑NC‑ND 4.0: personal,
  non‑commercial use, text unchanged). Each 5‑minute dhikr phrase is checked to appear exactly
  in this collection.

The Arabic font is [Amiri](https://www.amirifont.org) (SIL Open Font License 1.1). See
`CREDITS.md`.

To change which verses or dua categories appear, edit the lists at the top of
`tools/build_content.py` and run `python3 tools/build_content.py` (needs Python 3 and npm).

## Settings

Edit **`app/src/main/java/com/whatchapp/hourlybuzz/Config.kt`**:

```kotlin
const val VIBRATE_EVERY_MINUTES = 5
const val CARD_EVERY_MINUTES = 60
const val SHOW_DHIKR = true          // false = buzz only, no dhikr notification
const val DHIKR_SHOW_SECONDS = 8     // how long a tapped dhikr stays full screen
val MORNING_HOURS = 5..11
val EVENING_HOURS = 16..20
```

Set `TEST_MODE = true` to buzz every 20 seconds and show a card every minute while you try it
out.

## Using it on the watch

| Where | Input | What it does |
|---|---|---|
| Main screen | Tap **ON/OFF** | Start/stop the reminders |
| Main screen | **Show dua / verse**, or turn the bezel clockwise | Open the latest card |
| Main screen | **Back** button / swipe right | Close the app; the reminders keep running |
| Dua / verse | Turn the **bezel** | Scroll a long text; at the end, go to the next / previous card |
| Dua / verse | **Swipe** left / right | Next / previous card |
| Dua / verse | **Tap** or **Back** | Close |
| Dhikr notification | **Tap** | Show the dhikr full screen (closes by itself) |

The Galaxy Watch 7's touch bezel (swipe around the edge of the screen) and the Classic's rotating
bezel both work. If the cards move the wrong way when you turn it, set
`BEZEL_REVERSED = true`.

When you first open the app, allow **notifications**. The hourly card is shown through a
full-screen notification. If a yellow banner appears on the main screen, tap it and allow the
setting it opens.

## Sounds, gestures and other extensions

The code is split so new features slot in without rewiring anything:

- **Sounds**: each alert kind in `Config.ALERTS` can play a sound. A chime is included:
  ```kotlin
  AlertKind.CARD to AlertSettings(vibrate = longArrayOf(500), sound = Sound.Raw(R.raw.chime)),
  ```
  Put more sound files in `app/src/main/res/raw/`, or use `Sound.Url("https://…")`.
- **Do Not Disturb**: by default buzzes and sounds behave like an alarm, so they come through in
  Do Not Disturb, Bedtime and silent mode. Set `RESPECT_DO_NOT_DISTURB = true` to have them obey
  those modes.
- **New alert types**: write an `AlertHandler` in `Alerts.kt` and add it to `Alerts.handlers`.
- **Remapping gestures**: change `Config.GESTURES`. Each input (`Gesture`) maps to an `Action`
  (`TOGGLE`, `SHOW_CARD`, `NEXT_CARD`, `PREV_CARD`, `SCROLL_OR_NEXT`, `SCROLL_OR_PREV`, `CLOSE`).
- **New gestures**: add a value to `Gesture` in `Gestures.kt`, detect it, and call
  `Gestures.handle(host, Gesture.YOUR_GESTURE)`. Wear OS wrist flicks (`WRIST_FLICK_OUT/IN`) are
  already wired up for watches that support them.
- **New actions**: add a value to `Action` and handle it in `perform()` in `MainActivity`,
  `CardActivity` and `DhikrActivity`.

About Samsung's own gestures: Samsung doesn't let apps use its "double pinch" and "knock knock"
gestures, and One UI doesn't send Google's wrist-flick gestures to apps. Raising your wrist
turns the screen on by itself, so a card that is still open is visible again.

## Getting the app onto your watch

### 1. Get the APK

Every push to GitHub builds the app automatically and publishes it on the repo's
**Releases** page (latest release → **HourlyBuzz.apk**). It is also attached to each run in the
**Actions** tab (*Build watch app* → latest
run → *Artifacts* → **HourlyBuzz-apk**). Download and unzip it to get `HourlyBuzz.apk`.

Or build it yourself with Android Studio: open this folder, then run *Build → Build APK(s)*.

### 2. Turn on debugging on the watch

1. *Settings → About watch → Software information → tap "Software version" 5 times* to enable
   Developer options.
2. *Settings → Developer options*: turn on **ADB debugging** and **Wireless debugging**.
3. Put the watch on the same Wi‑Fi as your computer. Open *Wireless debugging → Pair new device*
   and note the IP, port and pairing code.

### 3. Install

With [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)
(`adb`) on your computer:

```bash
adb pair <watch-ip>:<pairing-port>      # enter the pairing code
adb connect <watch-ip>:<port>           # the port shown on the Wireless debugging screen
adb install -r HourlyBuzz.apk
```

No computer? Phone apps such as *Bugjaeger* or *Wear Installer 2* can install APKs over the same
wireless debugging connection.

> The APKs built on GitHub are signed with a temporary key that changes with each build. To
> install a newer build over an older one, uninstall the old one first
> (`adb uninstall com.whatchapp.hourlybuzz`).

## Things to know

- **The alarm icon**: the app uses Android's "alarm clock" alarms. They are the only kind
  allowed to fire every 5 minutes while the watch sleeps. As a result, the watch may show the
  next buzz as an upcoming alarm.
- **Battery**: the watch sleeps between buzzes and the screen only turns on once an hour, so
  battery use is modest.
- **Survives restarts**: the schedule restarts after the watch reboots or the app is updated.
  **Force stop** cancels it until you open the app again.

## Project layout

```
app/src/main/java/com/whatchapp/hourlybuzz/
  Config.kt         Your settings (timing, dhikr, alerts, gestures)
  Content.kt        Loads the dhikr/duas/verses; picks which collection each hour uses
  Schedule.kt       Clock maths (unit-tested)
  Scheduler.kt      Sets the exact alarm for the next buzz
  AlarmReceiver.kt  Runs at each buzz time: buzz + dhikr, or a dua/verse on the hour
  BootReceiver.kt   Restarts the schedule after reboot / update / clock change
  Alerts.kt         Alert handlers: vibrate, sound
  Cards.kt          Puts the dhikr and the cards on screen (full-screen notification)
  Gestures.kt       Bezel, swipe, tap, Back, wrist flick → actions
  MainActivity.kt   Main screen
  CardActivity.kt   Full-screen dua / verse
  DhikrActivity.kt  The dhikr shown with each buzz
app/src/main/assets/content.json Arabic text (generated by tools/build_content.py)
app/src/main/res/font/amiri.ttf  Arabic font
app/src/main/res/raw/chime.wav   Sample sound
app/src/test/                    Unit tests (./gradlew testDebugUnitTest)
.github/workflows/build.yml      Builds the APK on GitHub
```
