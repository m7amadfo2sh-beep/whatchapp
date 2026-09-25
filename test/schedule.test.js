"use strict";

const test = require("node:test");
const assert = require("node:assert");
const S = require("../js/schedule.js");

const MIN = 60 * 1000;
const at = (h, m, s = 0) => new Date(2026, 0, 15, h, m, s).getTime();

test("nextAligned: 5 minute ticks land on the clock", () => {
  assert.strictEqual(S.nextAligned(at(10, 2), 5 * MIN), at(10, 5));
  assert.strictEqual(S.nextAligned(at(10, 59, 59), 5 * MIN), at(11, 0));
});

test("nextAligned: exactly on a boundary returns the following one", () => {
  assert.strictEqual(S.nextAligned(at(10, 5), 5 * MIN), at(10, 10));
  assert.strictEqual(S.nextAligned(at(10, 0), 60 * MIN), at(11, 0));
});

test("nextAligned: hourly wraps past midnight", () => {
  assert.strictEqual(S.nextAligned(at(23, 30), 60 * MIN), new Date(2026, 0, 16, 0, 0).getTime());
});

test("prevAligned", () => {
  assert.strictEqual(S.prevAligned(at(10, 7), 5 * MIN), at(10, 5));
  assert.strictEqual(S.prevAligned(at(10, 0), 60 * MIN), at(10, 0));
});

test("wrapIndex handles overflow and negatives", () => {
  assert.strictEqual(S.wrapIndex(4, 4), 0);
  assert.strictEqual(S.wrapIndex(-1, 4), 3);
  assert.strictEqual(S.wrapIndex(5, 0), 0);
});

test("missedImage: only within the grace period after an unshown boundary", () => {
  const grace = 2 * MIN;
  assert.strictEqual(S.missedImage(at(10, 1), 60 * MIN, at(9, 0), grace), true);
  assert.strictEqual(S.missedImage(at(10, 1), 60 * MIN, at(10, 0), grace), false);
  assert.strictEqual(S.missedImage(at(10, 5), 60 * MIN, at(9, 0), grace), false);
});

test("formatCountdown", () => {
  assert.strictEqual(S.formatCountdown(4 * MIN + 5000), "4:05");
  assert.strictEqual(S.formatCountdown(61 * MIN), "1:01:00");
  assert.strictEqual(S.formatCountdown(-5), "0:00");
});
