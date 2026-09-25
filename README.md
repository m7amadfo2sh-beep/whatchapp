# Hourly Buzz — Samsung Galaxy Watch (Tizen)

A watch app for Tizen-based Samsung watches (Galaxy Watch, Watch 3, Watch Active / Active 2,
Gear S2/S3, Gear Sport). It:

- **vibrates every 5 minutes** (on the clock: :00, :05, :10 …), also with the screen off
- **shows a picture every hour** (on the hour) from your list of image URLs, in order and
  wrapping around. The screen turns on and the app comes to the front.

> Galaxy Watch 4 and newer run Wear OS, not Tizen, so they can't run this app.

## Setting your images and timing

Edit **`js/config.js`**. It's the only file you normally need to change.

```js
IMAGE_URLS: [
  "https://example.com/photo1.jpg",
  "https://example.com/photo2.png"
],
VIBRATE_EVERY_MINUTES: 5,
IMAGE_EVERY_MINUTES: 60,
```

Images load over the watch's connection (via the paired phone or Wi‑Fi). The next image starts
downloading 3 minutes early (`PRELOAD_MINUTES`). Square images of about 360×360 look best.

## Using it on the watch

| Where | Input | What it does |
|---|---|---|
| Main screen | Tap **ON/OFF** | Start/stop the reminders |
| Main screen | **Preview picture** | Show the next picture without using it up |
| Main screen | **Back** key | Hide the app; reminders keep running |
| Picture | Turn **bezel** clockwise / counter‑clockwise | Next / previous picture |
| Picture | **Swipe** left / right | Next / previous picture |
| Picture | **Tap** or **Back** | Close the picture |
| Anywhere | **Raise wrist** (within 10 min of a picture) | Show that picture again |

## Sounds, gestures and other extensions

The code is split so new features slot in without rewiring anything:

- **Sounds**: each alert kind in `CONFIG.ALERTS` can play a sound. A chime is included:
  ```js
  ALERTS: {
    tick:  { vibrate: [200, 100, 200], sound: null, volume: 1.0 },
    image: { vibrate: [500], sound: "sounds/chime.wav", volume: 0.8 }
  }
  ```
  Add more files to `sounds/`, or use a URL. The watch's sound mode (mute/vibrate) still applies.
- **New alert types**: add a handler to `Alerts.handlers` in `js/alerts.js`
  (e.g. `flash: function (setting, kind) { … }`), then a `flash:` key in `CONFIG.ALERTS`.
- **Remapping gestures**: change `CONFIG.GESTURES`. Every input maps to an action name from
  `js/actions.js` (`nextImage`, `prevImage`, `showImage`, `closeImage`, `reshowImage`, `toggle`,
  `hideApp`, `exitApp`). For example, set `main.bezelClockwise: "showImage"` so turning the bezel
  on the main screen opens the picture.
- **New gestures**: detect the input in `js/gestures.js` and call `trigger("myGesture")`, then map
  `myGesture` in the config.
- **New actions**: add a function to `js/actions.js`.

## Project layout

```
config.xml        Tizen app manifest (id, privileges, background mode)
index.html        Main screen + picture view
css/style.css     Round 360×360 layout
js/config.js      Your settings (images, timing, alerts, gestures)
js/schedule.js    Clock maths (unit-tested)
js/alerts.js      Alert handlers: vibrate, sound
js/actions.js     Named actions used by gestures and buttons
js/gestures.js    Bezel, Back key, tap/swipe, wrist-raise → actions
js/platform.js    Tizen API wrapper (no-ops in a desktop browser)
js/app.js         Timers, state, screens
sounds/chime.wav  Sample sound
test/             Unit tests (Node)
```

## Building and installing on the watch

You need **Tizen Studio** (with the *Wearable* profile and the *Samsung Certificate Extension*
from the Package Manager).

1. **Import**: *File → Import → Tizen → Tizen Project*, then pick this folder. Or create a
   *Wearable → Web Application → Basic UI* project and copy these files over it.
2. **Certificate**: *Tools → Certificate Manager → + → Samsung*, then sign in with a Samsung
   account. When asked, add your watch's DUID. The watch must be connected (step 3) so the
   manager can read it. Real watches only accept apps signed with a Samsung certificate.
3. **Connect the watch** (same Wi‑Fi as your computer):
   - Watch: *Settings → About watch → Software → tap Software version 5×* to enable developer
     mode.
   - *Settings → Developer options → Debugging ON*, *Settings → Connections → Wi‑Fi* on, and
     note the watch's IP address.
   - Computer: `sdb connect <watch-ip>`, then accept the prompt on the watch.
4. **Run**: right-click the project → *Run As → Tizen Wearable Application*. Or build a `.wgt`
   (*Build Signed Package*) and install it with `sdb install HourlyBuzz.wgt`.

The first time you raise your wrist, the watch asks for health-sensor permission. That
permission is only used for the wrist-raise gesture.

## Trying it in a browser

```bash
npx http-server .     # then open http://localhost:8080/index.html?test=1
```

`?test=1` uses short intervals (buzz every 10 s, picture every 30 s; see
`CONFIG.TEST_MODE_SECONDS`). Watch-only features (vibration, bezel, screen wake) are skipped.

Run the unit tests with `npm test` (Node 18+).

## Things to know

- **Battery**: to buzz on time with the screen off, the app keeps the CPU awake while it is ON.
  This drains the battery faster than a normal watch face. Turn it OFF when you don't need it.
- **Staying alive**: Tizen can still close background apps, for example under memory pressure.
  As a backup, the app sets a system alarm for each hourly picture. The alarm relaunches the app,
  which then picks up its schedule again. Buzzes between that point and the relaunch can be
  missed.
- **Power saving / Theatre mode** may block vibration or the screen turning on.
