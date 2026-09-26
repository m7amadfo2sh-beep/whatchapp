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

    /**
     * Post a dhikr with each 5-minute buzz as a quiet notification (it does not
     * turn the screen on). false = buzz only, nothing shown.
     */
    const val SHOW_DHIKR = false

    /** When you tap the dhikr notification, how long it stays full screen. */
    const val DHIKR_SHOW_SECONDS = 8

    /**
     * Each hour's card is shown this many times, [CARD_REPEAT_MINUTES] apart
     * (3 and 3 = at :00, :03 and :06). Each time: buzz, screen on, scroll.
     */
    const val CARD_SHOW_TIMES = 3
    const val CARD_REPEAT_MINUTES = 3

    /** Keep the screen on for this long when a dua/verse card appears (0 = watch default). */
    const val CARD_SCREEN_ON_SECONDS = 60

    /** Close the card automatically after this many seconds (0 = stay until dismissed). */
    const val CARD_AUTO_CLOSE_SECONDS = 60

    /**
     * Scroll long cards automatically: wait [AUTO_SCROLL_PAUSE_SECONDS] at the
     * top, then scroll slowly so the end is reached before the card closes.
     * Touching the screen or turning the bezel stops it.
     */
    const val AUTO_SCROLL = true
    const val AUTO_SCROLL_PAUSE_SECONDS = 3

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
    const val TEST_REPEAT_SECONDS = 15
}
