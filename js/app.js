/*
 * Hourly Buzz - main logic: state, timers and the two screens.
 */
(function (root) {
  "use strict";

  var STORAGE_KEY = "hourlyBuzz.state";
  var MISSED_IMAGE_GRACE_MS = 2 * 60 * 1000;

  var testMode = /[?&]test=1\b/.test(root.location.search);
  var T = CONFIG.TEST_MODE_SECONDS;
  var tickMs = testMode ? T.vibrate * 1000 : CONFIG.VIBRATE_EVERY_MINUTES * 60 * 1000;
  var imageMs = testMode ? T.image * 1000 : CONFIG.IMAGE_EVERY_MINUTES * 60 * 1000;
  var preloadMs = testMode ? T.preload * 1000 : CONFIG.PRELOAD_MINUTES * 60 * 1000;
  var urls = CONFIG.IMAGE_URLS || [];
  var missedGraceMs = Math.min(MISSED_IMAGE_GRACE_MS, imageMs / 4);

  var state = loadState();
  var timers = {};
  var nextTickAt = 0;
  var nextImageAt = 0;
  var viewIndex = 0;
  var el = {};

  // ---- persistence -------------------------------------------------------

  function loadState() {
    var saved = {};
    try {
      saved = JSON.parse(root.localStorage.getItem(STORAGE_KEY)) || {};
    } catch (err) { /* first run or storage unavailable */ }
    return {
      enabled: saved.enabled !== false,
      nextIndex: saved.nextIndex || 0,
      lastShownIndex: saved.lastShownIndex || 0,
      lastImageAt: saved.lastImageAt || 0
    };
  }

  function saveState() {
    try {
      root.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (err) { /* ignore */ }
  }

  // ---- timers ------------------------------------------------------------

  function clearTimer(name) {
    clearTimeout(timers[name]);
    timers[name] = null;
  }

  function scheduleTick(from) {
    clearTimer("tick");
    nextTickAt = Schedule.nextAligned(from || Date.now(), tickMs);
    timers.tick = setTimeout(onTick, Math.max(0, nextTickAt - Date.now()));
  }

  function onTick() {
    var due = nextTickAt;
    // On an image boundary the image alert replaces the regular buzz.
    if (Schedule.prevAligned(due, imageMs) !== due) Alerts.fire("tick");
    scheduleTick(Math.max(Date.now(), due));
    updateUi();
  }

  function scheduleImage(from) {
    clearTimer("image");
    clearTimer("preload");
    nextImageAt = Schedule.nextAligned(from || Date.now(), imageMs);
    timers.image = setTimeout(onImage, Math.max(0, nextImageAt - Date.now()));
    var preloadIn = nextImageAt - preloadMs - Date.now();
    timers.preload = setTimeout(preloadNext, Math.max(0, preloadIn));
    if (!testMode) Platform.setBackupAlarm(nextImageAt);
  }

  function onImage() {
    var due = nextImageAt;
    showScheduledImage();
    scheduleImage(Math.max(Date.now(), due));
  }

  function preloadNext() {
    if (!urls.length) return;
    var img = new Image();
    img.src = urls[Schedule.wrapIndex(state.nextIndex, urls.length)];
  }

  function start() {
    Platform.keepCpuAwake(true);
    scheduleTick();
    scheduleImage();
    startUiTimer();
  }

  function stop() {
    clearTimer("tick");
    clearTimer("image");
    clearTimer("preload");
    Platform.keepCpuAwake(false);
    Platform.setBackupAlarm(null);
  }

  // Timers can be delayed while the watch sleeps; when the app becomes
  // visible (or is relaunched by the backup alarm) catch up.
  function resync() {
    if (!state.enabled) return;
    catchUpMissedImage();
    scheduleTick();
    scheduleImage();
  }

  // Show an image whose time passed while the app was asleep or closed (not
  // on the very first run, so a fresh install doesn't pop up an image).
  function catchUpMissedImage() {
    if (state.lastImageAt &&
        Schedule.missedImage(Date.now(), imageMs, state.lastImageAt, missedGraceMs)) {
      showScheduledImage();
    }
  }

  // ---- images ------------------------------------------------------------

  // Called on schedule: show the next image in the rotation and advance it.
  function showScheduledImage() {
    if (!urls.length) {
      Alerts.fire("image");
      return;
    }
    var index = Schedule.wrapIndex(state.nextIndex, urls.length);
    state.lastShownIndex = index;
    state.nextIndex = Schedule.wrapIndex(index + 1, urls.length);
    state.lastImageAt = Date.now();
    saveState();

    Alerts.fire("image");
    Platform.wakeScreen(CONFIG.IMAGE_SCREEN_ON_SECONDS);
    if (document.hidden) Platform.bringToFront();
    openImage(index);
  }

  // Preview the upcoming image without changing the rotation.
  function previewNextImage() {
    if (urls.length) openImage(state.nextIndex);
  }

  function reshowRecentImage() {
    var windowMs = CONFIG.WRIST_UP_WINDOW_MINUTES * 60 * 1000;
    if (!urls.length || !state.lastImageAt || Date.now() - state.lastImageAt > windowMs) return;
    if (isImageOpen() && viewIndex === state.lastShownIndex) return;
    Platform.wakeScreen(CONFIG.IMAGE_SCREEN_ON_SECONDS);
    if (document.hidden) Platform.bringToFront();
    openImage(state.lastShownIndex);
  }

  function openImage(index) {
    viewIndex = Schedule.wrapIndex(index, urls.length);
    el.imageMsg.textContent = "Loading…";
    el.imageMsg.hidden = false;
    el.image.hidden = true;
    el.image.src = urls[viewIndex];
    el.imageCaption.textContent = (viewIndex + 1) + " / " + urls.length;
    el.main.hidden = true;
    el.imageView.hidden = false;

    clearTimer("autoClose");
    if (CONFIG.IMAGE_AUTO_CLOSE_SECONDS > 0) {
      timers.autoClose = setTimeout(closeImage, CONFIG.IMAGE_AUTO_CLOSE_SECONDS * 1000);
    }
  }

  function browse(delta) {
    if (!urls.length) return;
    if (!isImageOpen()) {
      openImage(state.lastShownIndex);
      return;
    }
    openImage(viewIndex + delta);
  }

  function closeImage() {
    clearTimer("autoClose");
    el.imageView.hidden = true;
    el.main.hidden = false;
    el.image.removeAttribute("src");
    updateUi();
  }

  function isImageOpen() {
    return !el.imageView.hidden;
  }

  // ---- UI ----------------------------------------------------------------

  function pad(n) {
    return (n < 10 ? "0" : "") + n;
  }

  function updateUi() {
    el.toggle.textContent = state.enabled ? "ON" : "OFF";
    el.toggle.className = "toggle " + (state.enabled ? "on" : "off");
    if (state.enabled) {
      el.nextBuzz.textContent = Schedule.formatCountdown(nextTickAt - Date.now());
      var d = new Date(nextImageAt);
      el.nextImage.textContent = pad(d.getHours()) + ":" + pad(d.getMinutes()) +
        (testMode ? ":" + pad(d.getSeconds()) : "");
    } else {
      el.nextBuzz.textContent = "–";
      el.nextImage.textContent = "–";
    }
  }

  function startUiTimer() {
    clearInterval(timers.ui);
    timers.ui = null;
    if (!document.hidden) {
      updateUi();
      timers.ui = setInterval(updateUi, 1000);
    }
  }

  function setEnabled(on) {
    state.enabled = on;
    saveState();
    if (on) start();
    else stop();
    updateUi();
  }

  function currentScreen() {
    return isImageOpen() ? "image" : "main";
  }

  function init() {
    el.main = document.getElementById("main");
    el.toggle = document.getElementById("toggle");
    el.nextBuzz = document.getElementById("next-buzz");
    el.nextImage = document.getElementById("next-image");
    el.preview = document.getElementById("preview");
    el.imageView = document.getElementById("image-view");
    el.image = document.getElementById("image");
    el.imageMsg = document.getElementById("image-msg");
    el.imageCaption = document.getElementById("image-caption");
    if (testMode) document.getElementById("test-badge").hidden = false;

    el.image.addEventListener("load", function () {
      el.image.hidden = false;
      el.imageMsg.hidden = true;
    });
    el.image.addEventListener("error", function () {
      if (!el.image.getAttribute("src")) return;
      el.imageMsg.textContent = "Couldn't load image. Check the watch's connection.";
      el.imageMsg.hidden = false;
    });

    el.toggle.addEventListener("click", function () { Actions.run("toggle"); });
    el.preview.addEventListener("click", function () { Actions.run("showImage"); });

    document.addEventListener("visibilitychange", function () {
      if (!document.hidden) resync();
      startUiTimer();
    });

    Gestures.init();

    if (state.enabled) {
      catchUpMissedImage();
      start();
    } else {
      startUiTimer();
    }
  }

  root.App = {
    state: state,
    setEnabled: setEnabled,
    showScheduledImage: showScheduledImage,
    previewNextImage: previewNextImage,
    reshowRecentImage: reshowRecentImage,
    browse: browse,
    closeImage: closeImage,
    currentScreen: currentScreen
  };

  root.addEventListener("load", init);
})(this);
