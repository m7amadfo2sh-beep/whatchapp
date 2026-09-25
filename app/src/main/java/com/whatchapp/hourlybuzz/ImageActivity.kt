package com.whatchapp.hourlybuzz

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import java.util.concurrent.Executors

/** Full-screen picture. Bezel/swipe browses, tap or Back closes. */
class ImageActivity : Activity(), ActionHost {
    override val screen = Screen.IMAGE

    private lateinit var image: ImageView
    private lateinit var message: TextView
    private lateinit var caption: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private var index = 0
    private var loadToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image)
        image = findViewById(R.id.image)
        message = findViewById(R.id.message)
        caption = findViewById(R.id.caption)

        val detector = Gestures.touchDetector(this, this)
        findViewById<View>(R.id.root).setOnTouchListener { v, e ->
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

    override fun onResume() {
        super.onResume()
        Pictures.visibleScreens++
    }

    override fun onPause() {
        Pictures.visibleScreens--
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        loader.shutdownNow()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_SCHEDULED, false)) {
            Pictures.cancelNotification(this)
            keepScreenOn()
        }
        handler.removeCallbacks(autoClose)
        if (Config.IMAGE_AUTO_CLOSE_SECONDS > 0) {
            handler.postDelayed(autoClose, Config.IMAGE_AUTO_CLOSE_SECONDS * 1000L)
        }
        show(intent.getIntExtra(EXTRA_INDEX, 0))
    }

    private val autoClose = Runnable { finish() }

    private fun keepScreenOn() {
        if (Config.IMAGE_SCREEN_ON_SECONDS <= 0) return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.postDelayed({
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }, Config.IMAGE_SCREEN_ON_SECONDS * 1000L)
    }

    private fun show(newIndex: Int) {
        val count = Pictures.count
        if (count == 0) {
            finish()
            return
        }
        index = Schedule.wrapIndex(newIndex, count)
        caption.text = getString(R.string.picture_of, index + 1, count)
        message.setText(R.string.loading)
        message.visibility = View.VISIBLE
        image.setImageDrawable(null)

        val token = ++loadToken
        val url = Config.IMAGE_URLS[index]
        val size = resources.displayMetrics.widthPixels
        loader.execute {
            val bitmap = ImageCache.load(applicationContext, url, size)
            handler.post {
                if (token != loadToken || isDestroyed) return@post
                if (bitmap != null) {
                    image.setImageBitmap(bitmap)
                    message.visibility = View.GONE
                } else {
                    message.setText(R.string.load_failed)
                }
            }
        }
    }

    override fun perform(action: Action): Boolean {
        when (action) {
            Action.NEXT_IMAGE -> show(index + 1)
            Action.PREV_IMAGE -> show(index - 1)
            Action.CLOSE -> finish()
            Action.SHOW_IMAGE -> Unit
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
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_SCHEDULED = "scheduled"

        fun intent(context: Context, index: Int, scheduled: Boolean): Intent =
            Intent(context, ImageActivity::class.java)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_SCHEDULED, scheduled)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
