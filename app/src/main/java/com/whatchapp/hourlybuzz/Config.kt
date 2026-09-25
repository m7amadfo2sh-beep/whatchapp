package com.whatchapp.hourlybuzz

/**
 * Hourly Buzz settings.
 *
 * This is the only file you normally need to edit. Rebuild and reinstall the
 * app after changing it.
 */
object Config {

    /** Pictures shown one per hour, in this order, wrapping back to the first. */
    val IMAGE_URLS = listOf(
        "https://picsum.photos/id/10/480/480",
        "https://picsum.photos/id/28/480/480",
        "https://picsum.photos/id/43/480/480",
        "https://picsum.photos/id/57/480/480",
    )

    /** How often the watch vibrates, in minutes (aligned to the clock: :00, :05, :10...). */
    const val VIBRATE_EVERY_MINUTES = 5

    /** How often a picture is shown, in minutes (60 = on the hour). */
    const val IMAGE_EVERY_MINUTES = 60

    /** Start downloading the next picture this many minutes before it is due. */
    const val PRELOAD_MINUTES = 5

    /** Keep the screen on for this long when a picture appears (0 = watch default). */
    const val IMAGE_SCREEN_ON_SECONDS = 30

    /** Close the picture automatically after this many seconds (0 = stay until dismissed). */
    const val IMAGE_AUTO_CLOSE_SECONDS = 0

    /**
     * What happens for each kind of alert. Each field is handled by an
     * [AlertHandler] in Alerts.kt, so new alert types can be added there.
     *  - vibrate: pattern in ms [buzz, pause, buzz, ...] or null for none
     *  - sound:   Sound.Raw(R.raw.chime), Sound.Url("https://..."), or null
     *  - volume:  0.0 - 1.0
     */
    val ALERTS = mapOf(
        AlertKind.TICK to AlertSettings(vibrate = longArrayOf(200, 100, 200), sound = null),
        AlertKind.IMAGE to AlertSettings(vibrate = longArrayOf(500), sound = null),
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
            Gesture.BEZEL_CLOCKWISE to Action.SHOW_IMAGE,
            Gesture.BACK to Action.CLOSE,
        ),
        Screen.IMAGE to mapOf(
            Gesture.BEZEL_CLOCKWISE to Action.NEXT_IMAGE,
            Gesture.BEZEL_COUNTER_CLOCKWISE to Action.PREV_IMAGE,
            Gesture.SWIPE_LEFT to Action.NEXT_IMAGE,
            Gesture.SWIPE_RIGHT to Action.PREV_IMAGE,
            Gesture.TAP to Action.CLOSE,
            Gesture.BACK to Action.CLOSE,
        ),
        Screen.ANYWHERE to mapOf(
            Gesture.WRIST_FLICK_OUT to Action.NEXT_IMAGE,
            Gesture.WRIST_FLICK_IN to Action.PREV_IMAGE,
        ),
    )

    /** Flip this if turning the bezel moves through pictures the "wrong" way. */
    const val BEZEL_REVERSED = false

    /**
     * Test mode: buzz every [TEST_VIBRATE_SECONDS] and show a picture every
     * [TEST_IMAGE_SECONDS] so you can check everything quickly. Turn off for real use.
     */
    const val TEST_MODE = false
    const val TEST_VIBRATE_SECONDS = 20
    const val TEST_IMAGE_SECONDS = 60
}
