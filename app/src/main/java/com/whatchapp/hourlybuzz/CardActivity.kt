package com.whatchapp.hourlybuzz

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView

/** A full-screen dua or Quran verse. Bezel scrolls/browses, swipe browses, tap or Back closes. */
class CardActivity : Activity(), ActionHost {
    override val screen = Screen.CARD

    private lateinit var scroll: ScrollView
    private lateinit var title: TextView
    private lateinit var text: TextView
    private lateinit var count: TextView
    private lateinit var source: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { finish() }
    private var pool = Pool.GENERAL
    private var index = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card)
        scroll = findViewById(R.id.scroll)
        title = findViewById(R.id.title)
        text = findViewById(R.id.text)
        count = findViewById(R.id.count)
        source = findViewById(R.id.source)
        insetForRoundScreen(findViewById(R.id.content))
        val arabic = resources.getFont(R.font.amiri)
        listOf(title, text, count, source).forEach { it.typeface = arabic }

        val detector = Gestures.touchDetector(this, this)
        scroll.setOnTouchListener { _, e ->
            detector.onTouchEvent(e)
            false // let the ScrollView scroll too
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
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

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_SCHEDULED, false)) {
            Cards.cancel(this, Cards.NOTIFICATION_CARD)
            keepScreenOn()
        }
        handler.removeCallbacks(autoClose)
        if (Config.CARD_AUTO_CLOSE_SECONDS > 0) {
            handler.postDelayed(autoClose, Config.CARD_AUTO_CLOSE_SECONDS * 1000L)
        }
        pool = Pool.entries.find { it.name == intent.getStringExtra(EXTRA_POOL) } ?: Pool.GENERAL
        show(intent.getIntExtra(EXTRA_INDEX, 0))
    }

    private fun keepScreenOn() {
        if (Config.CARD_SCREEN_ON_SECONDS <= 0) return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.postDelayed({
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }, Config.CARD_SCREEN_ON_SECONDS * 1000L)
    }

    private fun show(newIndex: Int) {
        val cards = Content.cards(this, pool)
        if (cards.isEmpty()) {
            finish()
            return
        }
        index = Schedule.wrapIndex(newIndex, cards.size)
        val card = cards[index]
        title.text = card.title
        text.text = card.text
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeFor(card))
        count.visibility = if (card.count > 1) View.VISIBLE else View.GONE
        count.text = "×" + arabicDigits(card.count)
        source.visibility = if (card.source != null) View.VISIBLE else View.GONE
        source.text = card.source
        scroll.scrollTo(0, 0)
    }

    private fun textSizeFor(card: Card): Float {
        val base = if (card.isVerse) 24f else 21f
        return when {
            card.text.length > 300 -> base - 4
            card.text.length > 160 -> base - 2
            else -> base
        }
    }

    override fun perform(action: Action): Boolean {
        val step = scroll.height / 4
        when (action) {
            Action.NEXT_CARD -> show(index + 1)
            Action.PREV_CARD -> show(index - 1)
            Action.SCROLL_OR_NEXT ->
                if (scroll.canScrollVertically(1)) scroll.smoothScrollBy(0, step) else show(index + 1)
            Action.SCROLL_OR_PREV ->
                if (scroll.canScrollVertically(-1)) scroll.smoothScrollBy(0, -step) else show(index - 1)
            Action.CLOSE -> finish()
            Action.SHOW_CARD -> Unit
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
        private const val EXTRA_POOL = "pool"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_SCHEDULED = "scheduled"

        fun intent(context: Context, pool: Pool, index: Int, scheduled: Boolean): Intent =
            Intent(context, CardActivity::class.java)
                .putExtra(EXTRA_POOL, pool.name)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_SCHEDULED, scheduled)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** 12 -> "١٢" */
fun arabicDigits(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")
