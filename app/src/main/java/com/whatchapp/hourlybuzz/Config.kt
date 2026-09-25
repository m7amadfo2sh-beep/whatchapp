package com.whatchapp.hourlybuzz

/**
 * Hourly Buzz settings.
 *
 * This is the only file you normally need to edit. Rebuild and reinstall the
 * app after changing it. The dhikr, duas and verses themselves come from
 * app/src/main/assets/content.json (see tools/build_content.py).
 */
object Config {

    /** How often the watch vibrates and shows a dhikr, in minutes (on the clock: :00, :05...). */
    const val VIBRATE_EVERY_MINUTES = 5

    /** How often a dua or Quran verse card is shown, in minutes (60 = on the hour). */
    const val CARD_EVERY_MINUTES = 60

    /** Show a dhikr on screen with each buzz. false = buzz only (saves battery). */
    const val SHOW_DHIKR = true

    /** How long the dhikr stays on screen before closing itself. */
    const val DHIKR_SHOW_SECONDS = 8

    /** Keep the screen on for this long when a dua/verse card appears (0 = watch default). */
    const val CARD_SCREEN_ON_SECONDS = 30

    /** Close the card automatically after this many seconds (0 = stay until dismissed). */
    const val CARD_AUTO_CLOSE_SECONDS = 0

    /** Hours (0-23) that show أذكار الصباح / أذكار المساء instead of general duas. */
    val MORNING_HOURS = 5..11
    val EVENING_HOURS = 16..20

    /**
     * What happens for each kind of alert. Each field is handled by an
     * [AlertHandler] in Alerts.kt, so new alert types can be added there.
     *  - vibrate: pattern in ms [buzz, pause, buzz, ...] or null for none
     *  - sound:   Sound.Raw(R.raw.chime), Sound.Url("https://..."), or null
     *  - volume:  0.0 - 1.0
     */
    val ALERTS = mapOf(
        AlertKind.TICK to AlertSettings(vibrate = longArrayOf(200, 100, 200), sound = null),
        AlertKind.CARD to AlertSettings(vibrate = longArrayOf(500), sound = null),
    )

    /**
     * false: buzz/sound like an alarm, even in Do Not Disturb, Bedtime or
     * silent mode. true: behave like a notification and obey those modes.
     */
    const val RESPECT_DO_NOT_DISTURB = false

    /**
     * Watch inputs -> actions. Remove an entry (or set it to null) to disable it.
     * [Screen.ANYWHERE] entries apply when the current screen has no entry.
     */
    val GESTURES: Map<Screen, Map<Gesture, Action?>> = mapOf(
        Screen.MAIN to mapOf(
            Gesture.BEZEL_CLOCKWISE to Action.SHOW_CARD,
            Gesture.BACK to Action.CLOSE,
        ),
        Screen.CARD to mapOf(
            Gesture.BEZEL_CLOCKWISE to Action.SCROLL_OR_NEXT,
            Gesture.BEZEL_COUNTER_CLOCKWISE to Action.SCROLL_OR_PREV,
            Gesture.SWIPE_LEFT to Action.NEXT_CARD,
            Gesture.SWIPE_RIGHT to Action.PREV_CARD,
            Gesture.TAP to Action.CLOSE,
            Gesture.BACK to Action.CLOSE,
        ),
        Screen.DHIKR to mapOf(
            Gesture.TAP to Action.CLOSE,
            Gesture.BACK to Action.CLOSE,
        ),
        Screen.ANYWHERE to mapOf(
            Gesture.WRIST_FLICK_OUT to Action.NEXT_CARD,
            Gesture.WRIST_FLICK_IN to Action.PREV_CARD,
        ),
    )

    /** Flip this if turning the bezel moves the "wrong" way. */
    const val BEZEL_REVERSED = false

    /**
     * Test mode: buzz every [TEST_VIBRATE_SECONDS] and show a card every
     * [TEST_CARD_SECONDS] so you can check everything quickly. Turn off for real use.
     */
    const val TEST_MODE = false
    const val TEST_VIBRATE_SECONDS = 20
    const val TEST_CARD_SECONDS = 60
}
