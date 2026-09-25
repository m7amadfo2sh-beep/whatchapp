/*
 * Pure scheduling helpers. No Tizen or DOM dependencies, so they can be
 * unit-tested with Node (see test/schedule.test.js).
 */
(function (root) {
  "use strict";

  // Start of the local day containing `now` (ms since epoch).
  function localMidnight(now) {
    var d = new Date(now);
    return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  }

  // Next time strictly after `now` that is a whole multiple of `periodMs`
  // counted from local midnight. With a 5 minute period that is :00, :05, ...;
  // with 60 minutes it is the top of the next hour.
  function nextAligned(now, periodMs) {
    var start = localMidnight(now);
    var steps = Math.floor((now - start) / periodMs) + 1;
    return start + steps * periodMs;
  }

  // Most recent aligned time at or before `now`.
  function prevAligned(now, periodMs) {
    var start = localMidnight(now);
    return start + Math.floor((now - start) / periodMs) * periodMs;
  }

  // Index wrapped into [0, length), also for negative numbers.
  function wrapIndex(index, length) {
    if (!length) return 0;
    return ((index % length) + length) % length;
  }

  // True when an image boundary was missed (e.g. the app was killed and
  // relaunched by the backup alarm) and should still be shown.
  function missedImage(now, periodMs, lastImageAt, graceMs) {
    var due = prevAligned(now, periodMs);
    return lastImageAt < due && now - due <= graceMs;
  }

  // Milliseconds as "m:ss" (or "h:mm:ss" for an hour or more).
  function formatCountdown(ms) {
    var total = Math.max(0, Math.ceil(ms / 1000));
    var h = Math.floor(total / 3600);
    var m = Math.floor((total % 3600) / 60);
    var s = total % 60;
    var ss = (s < 10 ? "0" : "") + s;
    if (h > 0) return h + ":" + (m < 10 ? "0" : "") + m + ":" + ss;
    return m + ":" + ss;
  }

  var Schedule = {
    localMidnight: localMidnight,
    nextAligned: nextAligned,
    prevAligned: prevAligned,
    wrapIndex: wrapIndex,
    missedImage: missedImage,
    formatCountdown: formatCountdown
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = Schedule;
  } else {
    root.Schedule = Schedule;
  }
})(this);
