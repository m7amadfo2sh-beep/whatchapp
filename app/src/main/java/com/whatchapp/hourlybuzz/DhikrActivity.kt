package com.whatchapp.hourlybuzz

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.TextView

/** The dhikr shown with each 5-minute buzz. Closes itself after a few seconds. */
class DhikrActivity : Activity(), ActionHost {
    override val screen = Screen.DHIKR

    private lateinit var text: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dhikr)
        insetForRoundScreen(findViewById(R.id.root))
        text = findViewById(R.id.text)
        text.typeface = resources.getFont(R.font.amiri)
        val detector = Gestures.touchDetector(this, this)
        findViewById<android.view.View>(R.id.root).setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) v.performClick()
            detector.onTouchEvent(e)
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Cards.cancel(this, Cards.NOTIFICATION_DHIKR)
        text.text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, Config.DHIKR_SHOW_SECONDS * 1000L)
    }

    override fun onResume() {
        super.onResume()
        Cards.visibleScreens++
    }

    override fun onPause() {
        Cards.visibleScreens--
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun perform(action: Action): Boolean {
        when (action) {
            Action.CLOSE -> finish()
            Action.SHOW_CARD, Action.NEXT_CARD, Action.PREV_CARD,
            Action.SCROLL_OR_NEXT, Action.SCROLL_OR_PREV -> {
                Cards.open(this)
                finish()
            }
            Action.TOGGLE -> Scheduler.setEnabled(this, !State(this).enabled)
        }
        return true
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean =
        Gestures.onGenericMotion(this, ev) || super.dispatchGenericMotionEvent(ev)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        Gestures.onKeyDown(this, keyCode) || super.onKeyDown(keyCode, event)

    @Deprecated("Still called on Wear OS without predictive back")
    override fun onBackPressed() {
        if (!Gestures.handle(this, Gesture.BACK)) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    companion object {
        private const val EXTRA_TEXT = "text"

        fun intent(context: Context, text: String): Intent =
            Intent(context, DhikrActivity::class.java)
                .putExtra(EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
