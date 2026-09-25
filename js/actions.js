/*
 * Named actions. Gestures (CONFIG.GESTURES), buttons and the scheduler all
 * call these by name, so remapping an input is a config change and adding a
 * new behaviour is one function here.
 */
(function (root) {
  "use strict";

  var Actions = {
    toggle: function () { App.setEnabled(!App.state.enabled); },
    showImage: function () { App.previewNextImage(); },
    nextImage: function () { App.browse(1); },
    prevImage: function () { App.browse(-1); },
    closeImage: function () { App.closeImage(); },
    reshowImage: function () { App.reshowRecentImage(); },
    hideApp: function () { Platform.hide(); },
    exitApp: function () { Platform.exit(); }
  };

  Actions.run = function (name) {
    if (!name) return false;
    var action = Actions[name];
    if (typeof action !== "function" || name === "run") {
      console.warn("Unknown action '" + name + "'");
      return false;
    }
    action();
    return true;
  };

  root.Actions = Actions;
})(this);
