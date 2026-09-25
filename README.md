# Hourly Buzz — Galaxy Watch 7 (Wear OS)

A Wear OS app for the Samsung Galaxy Watch 7 (and any Wear OS 3+ watch: Galaxy Watch 4 and
newer, Pixel Watch, …). It:

- **vibrates every 5 minutes** (on the clock: :00, :05, :10 …), also when the screen is off
- **shows a picture every hour** (on the hour) from your list of image URLs, in order and
  wrapping around. The screen turns on to show it.

## Setting your pictures and timing

Edit **`app/src/main/java/com/whatchapp/hourlybuzz/Config.kt`**. It's the only file you normally
need to change.

```kotlin
val IMAGE_URLS = listOf(
    "https://example.com/photo1.jpg",
    "https://example.com/photo2.png",
)
const val VIBRATE_EVERY_MINUTES = 5
const val IMAGE_EVERY_MINUTES = 60
```

Pictures download over the watch's connection (via the phone, Wi‑Fi or LTE). The next picture
is downloaded 5 minutes early (`PRELOAD_MINUTES`) so it appears instantly. Square pictures of
about 480×480 look best.

Set `TEST_MODE = true` to buzz every 20 seconds and show a picture every minute while you try it
out.

## Using it on the watch

| Where | Input | What it does |
|---|---|---|
| Main screen | Tap **ON/OFF** | Start/stop the reminders |
| Main screen | **Show picture**, or turn the bezel clockwise | Open the latest picture |
| Main screen | **Back** button / swipe right | Close the app; the reminders keep running |
| Picture | Turn the **bezel** clockwise / counter‑clockwise | Next / previous picture |
| Picture | **Swipe** left / right | Next / previous picture |
| Picture | **Tap** or **Back** | Close the picture |

The Galaxy Watch 7's touch bezel (swipe around the edge of the screen) and the Classic's rotating
bezel both work. If the pictures move the wrong way when you turn it, set
`BEZEL_REVERSED = true`.

When you first open the app, allow **notifications**. The hourly picture is delivered through a
full-screen notification. If a yellow banner appears on the main screen, tap it and allow the
setting it opens.

## Sounds, gestures and other extensions

The code is split so new features slot in without rewiring anything:

- **Sounds**: each alert kind in `Config.ALERTS` can play a sound. A chime is included:
  ```kotlin
  AlertKind.IMAGE to AlertSettings(vibrate = longArrayOf(500), sound = Sound.Raw(R.raw.chime)),
  ```
  Put more sound files in `app/src/main/res/raw/`, or use `Sound.Url("https://…")`.
- **Do Not Disturb**: by default buzzes and sounds behave like an alarm, so they come through in
  Do Not Disturb, Bedtime and silent mode. Set `RESPECT_DO_NOT_DISTURB = true` to have them obey
  those modes.
- **New alert types**: write an `AlertHandler` in `Alerts.kt` and add it to `Alerts.handlers`.
- **Remapping gestures**: change `Config.GESTURES`. Each input (`Gesture`) maps to an `Action`
  (`TOGGLE`, `SHOW_IMAGE`, `NEXT_IMAGE`, `PREV_IMAGE`, `CLOSE`).
- **New gestures**: add a value to `Gesture` in `Gestures.kt`, detect it, and call
  `Gestures.handle(host, Gesture.YOUR_GESTURE)`. Wear OS wrist flicks (`WRIST_FLICK_OUT/IN`) are
  already wired up for watches that support them.
- **New actions**: add a value to `Action` and handle it in `perform()` in `MainActivity` and
  `ImageActivity`.

About Samsung's own gestures: Samsung doesn't let apps use its "double pinch" and "knock knock"
gestures, and One UI doesn't send Google's wrist-flick gestures to apps. Raising your wrist
turns the screen on by itself, so a picture that is still open is visible again.

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
- **Battery**: the watch sleeps between buzzes, so battery use is modest. Downloading pictures
  and turning the screen on every hour costs a little more.
- **Survives restarts**: the schedule restarts after the watch reboots or the app is updated.
  **Force stop** cancels it until you open the app again.

## Project layout

```
app/src/main/java/com/whatchapp/hourlybuzz/
  Config.kt         Your settings (pictures, timing, alerts, gestures)
  Schedule.kt       Clock maths (unit-tested)
  Scheduler.kt      Sets the exact alarm for the next buzz
  AlarmReceiver.kt  Runs at each buzz time: buzz, or show the picture on the hour
  BootReceiver.kt   Restarts the schedule after reboot / update / clock change
  Alerts.kt         Alert handlers: vibrate, sound
  Pictures.kt       Picture rotation + full-screen notification
  ImageCache.kt     Downloads and caches pictures
  Gestures.kt       Bezel, swipe, tap, Back, wrist flick → actions
  MainActivity.kt   Main screen
  ImageActivity.kt  Full-screen picture
app/src/main/res/raw/chime.wav   Sample sound
app/src/test/                    Unit tests (./gradlew testDebugUnitTest)
.github/workflows/build.yml      Builds the APK on GitHub
```
