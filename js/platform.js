/*
 * Thin wrapper around the Tizen device APIs. Every call is guarded so the app
 * also runs in a normal desktop browser (where these become no-ops).
 */
(function (root) {
  "use strict";

  var hasTizen = typeof root.tizen !== "undefined";
  var screenTimer = null;

  function attempt(what, fn) {
    if (!hasTizen) return undefined;
    try {
      return fn();
    } catch (err) {
      console.warn("Tizen: " + what + " failed:", err);
      return undefined;
    }
  }

  function currentApp() {
    return tizen.application.getCurrentApplication();
  }

  var Platform = {
    isTizen: hasTizen,

    // Keep the CPU running while the screen is off so timers fire on time.
    keepCpuAwake: function (on) {
      attempt("CPU lock", function () {
        if (on) tizen.power.request("CPU", "CPU_AWAKE");
        else tizen.power.release("CPU");
      });
    },

    // Turn the screen on and keep it on for `seconds` (0 = watch default).
    wakeScreen: function (seconds) {
      attempt("screen on", function () {
        tizen.power.turnScreenOn();
        if (seconds > 0) {
          tizen.power.request("SCREEN", "SCREEN_NORMAL");
          clearTimeout(screenTimer);
          screenTimer = setTimeout(function () {
            attempt("screen release", function () { tizen.power.release("SCREEN"); });
          }, seconds * 1000);
        }
      });
    },

    // Bring this app to the front (used when it is running in the background).
    bringToFront: function () {
      attempt("launch self", function () {
        tizen.application.launch(currentApp().appInfo.id);
      });
    },

    // Send the app to the background without stopping it.
    hide: function () {
      attempt("hide", function () { currentApp().hide(); });
    },

    exit: function () {
      attempt("exit", function () { currentApp().exit(); });
    },

    // Backup: ask the system to (re)launch the app at `time` in case Tizen
    // closed it. Replaces any alarm set previously.
    setBackupAlarm: function (time) {
      attempt("alarm", function () {
        tizen.alarm.removeAll();
        if (time) {
          tizen.alarm.add(new tizen.AlarmAbsolute(new Date(time)), currentApp().appInfo.id);
        }
      });
    },

    // Calls `callback` when the wrist is raised (Tizen 4.0+). Returns true if
    // the watch supports it.
    onWristUp: function (callback) {
      if (!hasTizen || !tizen.humanactivitymonitor ||
          !tizen.humanactivitymonitor.addGestureRecognitionListener) {
        return false;
      }
      function listen() {
        try {
          if (!tizen.humanactivitymonitor.isGestureSupported("GESTURE_WRIST_UP")) return false;
          tizen.humanactivitymonitor.addGestureRecognitionListener(
            "GESTURE_WRIST_UP",
            function () { callback(); },
            function (err) { console.warn("Wrist gesture error:", err); },
            true // also while the screen is off
          );
          return true;
        } catch (err) {
          console.warn("Wrist gesture unavailable:", err);
          return false;
        }
      }
      // Tizen 4.0+ asks the user for the health-info permission at runtime.
      if (tizen.ppm && tizen.ppm.requestPermission) {
        var privilege = "http://tizen.org/privilege/healthinfo";
        try {
          if (tizen.ppm.checkPermission(privilege) === "PPM_ALLOW") return listen();
          tizen.ppm.requestPermission(privilege, function (result) {
            if (result === "PPM_ALLOW_FOREVER" || result === "PPM_ALLOW_ONCE") listen();
          }, function (err) { console.warn("Permission request failed:", err); });
          return true;
        } catch (err) {
          console.warn("Permission check failed:", err);
        }
      }
      return listen();
    }
  };

  root.Platform = Platform;
})(this);
