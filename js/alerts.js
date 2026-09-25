/*
 * Alerts: every reminder goes through Alerts.fire(kind), which runs each
 * handler below with the settings from CONFIG.ALERTS[kind].
 *
 * To add a new kind of alert (e.g. flashing the screen), add a handler:
 *   Alerts.handlers.flash = function (setting, kind) { ... };
 * and a matching `flash:` entry in CONFIG.ALERTS.
 */
(function (root) {
  "use strict";

  var audio = null;

  var handlers = {
    vibrate: function (pattern) {
      if (!pattern || !navigator.vibrate) return;
      navigator.vibrate(pattern);
    },

    sound: function (src, kind, settings) {
      if (!src) return;
      if (!audio) audio = new Audio();
      audio.pause();
      audio.src = src;
      audio.volume = typeof settings.volume === "number" ? settings.volume : 1;
      var played = audio.play();
      if (played && played.catch) {
        played.catch(function (err) {
          console.warn("Sound for '" + kind + "' could not play:", err);
        });
      }
    },

    // `volume` is read by the sound handler; nothing to do on its own.
    volume: function () {}
  };

  function fire(kind) {
    var settings = (CONFIG.ALERTS && CONFIG.ALERTS[kind]) || {};
    Object.keys(settings).forEach(function (name) {
      var handler = handlers[name];
      if (!handler) {
        console.warn("No alert handler named '" + name + "'");
        return;
      }
      try {
        handler(settings[name], kind, settings);
      } catch (err) {
        console.error("Alert handler '" + name + "' failed:", err);
      }
    });
    if (root.onAlertFired) root.onAlertFired(kind);
  }

  root.Alerts = { fire: fire, handlers: handlers };
})(this);
