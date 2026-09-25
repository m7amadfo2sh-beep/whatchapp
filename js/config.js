/*
 * Hourly Buzz - settings.
 *
 * This is the only file you normally need to edit. Rebuild and reinstall the
 * app after changing it.
 */
var CONFIG = {
  // Images shown one per hour, in this order, wrapping back to the first.
  // Any https:// URL works; square images around 360x360 look best.
  IMAGE_URLS: [
    "https://picsum.photos/id/10/360/360",
    "https://picsum.photos/id/28/360/360",
    "https://picsum.photos/id/43/360/360",
    "https://picsum.photos/id/57/360/360"
  ],

  // How often the watch vibrates, in minutes (aligned to the clock: :00, :05, :10...).
  VIBRATE_EVERY_MINUTES: 5,

  // How often an image is shown, in minutes (60 = on the hour).
  IMAGE_EVERY_MINUTES: 60,

  // Start downloading the next image this many minutes before it is due.
  PRELOAD_MINUTES: 3,

  // Keep the screen on for this long when an image appears (0 = watch default).
  IMAGE_SCREEN_ON_SECONDS: 30,

  // Close the image automatically after this many seconds (0 = stay until dismissed).
  IMAGE_AUTO_CLOSE_SECONDS: 0,

  // After an image appears, raising your wrist re-shows it for this many minutes.
  WRIST_UP_WINDOW_MINUTES: 10,

  // What happens for each kind of alert. Every entry is handled by a matching
  // handler in js/alerts.js, so new alert types can be added there.
  //   vibrate: pattern in ms [buzz, pause, buzz, ...] or null for none
  //   sound:   path to a bundled file (e.g. "sounds/chime.wav"), a URL, or null
  //   volume:  0.0 - 1.0
  ALERTS: {
    tick:  { vibrate: [200, 100, 200], sound: null, volume: 1.0 },
    image: { vibrate: [500],           sound: null, volume: 1.0 }
  },

  // Watch inputs -> actions (see js/actions.js for the list of actions).
  // Set an entry to null to disable it. Screens: "main", "image", "anywhere".
  GESTURES: {
    main: {
      bezelClockwise: null,
      bezelCounterClockwise: null,
      back: "hideApp"
    },
    image: {
      bezelClockwise: "nextImage",
      bezelCounterClockwise: "prevImage",
      swipeLeft: "nextImage",
      swipeRight: "prevImage",
      tap: "closeImage",
      back: "closeImage"
    },
    anywhere: {
      wristUp: "reshowImage"
    }
  },

  // Open index.html?test=1 in a browser to run with these short intervals (seconds).
  TEST_MODE_SECONDS: { vibrate: 10, image: 30, preload: 5 }
};
