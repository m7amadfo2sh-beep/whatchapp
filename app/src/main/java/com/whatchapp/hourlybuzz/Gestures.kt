package com.whatchapp.hourlybuzz

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.GestureDetector
import android.content.Context
import android.view.InputDevice
import kotlin.math.abs

enum class Screen { MAIN, IMAGE, ANYWHERE }

/** Watch inputs. To support a new one, detect it and call [Gestures.handle]. */
enum class Gesture {
    BEZEL_CLOCKWISE,
    BEZEL_COUNTER_CLOCKWISE,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    TAP,
    BACK,
    /** Google Wear OS wrist gestures (not available on every watch). */
    WRIST_FLICK_OUT,
    WRIST_FLICK_IN,
}

/** Things a gesture or button can do. Each screen implements the ones that make sense there. */
enum class Action {
    TOGGLE,
    SHOW_IMAGE,
    NEXT_IMAGE,
    PREV_IMAGE,
    CLOSE,
}

/** A screen that can carry out actions. Returns true if it handled the action. */
interface ActionHost {
    val screen: Screen
    fun perform(action: Action): Boolean
}

object Gestures {
    private const val SWIPE_MIN_PX = 60

    /** Looks up [gesture] in Config.GESTURES and performs it. Returns true if handled. */
    fun handle(host: ActionHost, gesture: Gesture): Boolean {
        val forScreen = Config.GESTURES[host.screen].orEmpty()
        val action = if (gesture in forScreen) forScreen[gesture]
        else Config.GESTURES[Screen.ANYWHERE]?.get(gesture)
        return action != null && host.perform(action)
    }

    /**
     * Rotating bezel (Galaxy Watch Classic) and touch bezel (Galaxy Watch 7)
     * arrive as rotary scroll events. Returns true if consumed.
     */
    fun onGenericMotion(host: ActionHost, event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_SCROLL ||
            !event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) return false
        val delta = event.getAxisValue(MotionEvent.AXIS_SCROLL)
        if (delta == 0f) return false
        // A bezel click is one full step; a smooth crown sends many small
        // steps, so add them up until they make one.
        if (rotaryTotal != 0f && (rotaryTotal < 0) != (delta < 0)) rotaryTotal = 0f
        rotaryTotal += delta
        if (abs(rotaryTotal) < ROTARY_STEP) return true
        val clockwise = (rotaryTotal < 0) != Config.BEZEL_REVERSED
        rotaryTotal = 0f
        handle(host, if (clockwise) Gesture.BEZEL_CLOCKWISE else Gesture.BEZEL_COUNTER_CLOCKWISE)
        return true
    }

    private const val ROTARY_STEP = 1f
    private var rotaryTotal = 0f

    /** Wear OS wrist gestures arrive as navigation keys. Returns true if consumed. */
    fun onKeyDown(host: ActionHost, keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_NAVIGATE_NEXT -> handle(host, Gesture.WRIST_FLICK_OUT)
        KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> handle(host, Gesture.WRIST_FLICK_IN)
        else -> false
    }

    /** Tap and swipe detection for a touch surface. */
    fun touchDetector(context: Context, host: ActionHost) = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent) = handle(host, Gesture.TAP)

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: return false)
                val dy = e2.y - e1.y
                if (abs(dx) < SWIPE_MIN_PX || abs(dx) < abs(dy)) return false
                return handle(host, if (dx < 0) Gesture.SWIPE_LEFT else Gesture.SWIPE_RIGHT)
            }
        },
    )
}
