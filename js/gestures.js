/*
 * Watch inputs -> actions, using the CONFIG.GESTURES table.
 *
 * Supported inputs: bezelClockwise, bezelCounterClockwise (rotating bezel),
 * back (hardware Back key), tap, swipeLeft, swipeRight (touch) and wristUp.
 * To support a new input, detect it here and call trigger("yourInputName").
 */
(function (root) {
  "use strict";

  var SWIPE_MIN_PX = 50;

  // The gesture table for the screen that is currently visible, with the
  // "anywhere" entries as fallback.
  function trigger(input) {
    var table = CONFIG.GESTURES || {};
    var screen = table[App.currentScreen()] || {};
    var action = Object.prototype.hasOwnProperty.call(screen, input)
      ? screen[input]
      : (table.anywhere || {})[input];
    return Actions.run(action);
  }

  function init() {
    // Rotating bezel (Galaxy Watch / Gear S2, S3). The Watch Active's touch
    // bezel sends the same event.
    document.addEventListener("rotarydetent", function (ev) {
      var dir = ev.detail && ev.detail.direction;
      trigger(dir === "CW" ? "bezelClockwise" : "bezelCounterClockwise");
    });

    // Hardware Back key. If nothing handles it, fall back to hiding the app so
    // the reminders keep running.
    root.addEventListener("tizenhwkey", function (ev) {
      if (ev.keyName !== "back") return;
      if (!trigger("back")) Platform.hide();
    });

    // Tap and swipe on the image view.
    var view = document.getElementById("image-view");
    var startX = null;
    var startY = null;
    view.addEventListener("touchstart", function (ev) {
      var t = ev.changedTouches[0];
      startX = t.clientX;
      startY = t.clientY;
    }, { passive: true });
    view.addEventListener("touchend", function (ev) {
      if (startX === null) return;
      var t = ev.changedTouches[0];
      var dx = t.clientX - startX;
      var dy = t.clientY - startY;
      startX = null;
      if (Math.abs(dx) >= SWIPE_MIN_PX && Math.abs(dx) > Math.abs(dy)) {
        trigger(dx < 0 ? "swipeLeft" : "swipeRight");
      } else if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
        trigger("tap");
      }
      ev.preventDefault(); // don't also fire a click
    });
    view.addEventListener("click", function () { trigger("tap"); });

    // Raise wrist.
    Platform.onWristUp(function () { trigger("wristUp"); });
  }

  root.Gestures = { init: init, trigger: trigger };
})(this);
