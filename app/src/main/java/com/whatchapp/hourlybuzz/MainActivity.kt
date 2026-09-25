package com.whatchapp.hourlybuzz

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity(), ActionHost {
    override val screen = Screen.MAIN

    private lateinit var toggle: Button
    private lateinit var nextBuzz: TextView
    private lateinit var nextCard: TextView
    private lateinit var warning: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateUi()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        insetForRoundScreen(findViewById(R.id.content))

        toggle = findViewById(R.id.toggle)
        nextBuzz = findViewById(R.id.next_buzz)
        nextCard = findViewById(R.id.next_card)
        warning = findViewById(R.id.warning)
        toggle.setOnClickListener { perform(Action.TOGGLE) }
        findViewById<View>(R.id.show_card).setOnClickListener { perform(Action.SHOW_CARD) }
        warning.setOnClickListener { fixPermission() }
        findViewById<View>(R.id.test_badge).visibility =
            if (Config.TEST_MODE) View.VISIBLE else View.GONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (State(this).enabled) Scheduler.start(this)
    }

    override fun onResume() {
        super.onResume()
        Cards.visibleScreens++
        if (State(this).enabled) Scheduler.start(this) // e.g. after granting a permission
        handler.post(ticker)
    }

    override fun onPause() {
        Cards.visibleScreens--
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun perform(action: Action): Boolean {
        when (action) {
            Action.TOGGLE -> {
                Scheduler.setEnabled(this, !State(this).enabled)
                updateUi()
            }
            Action.SHOW_CARD, Action.NEXT_CARD, Action.PREV_CARD,
            Action.SCROLL_OR_NEXT, Action.SCROLL_OR_PREV -> Cards.open(this)
            Action.CLOSE -> finish()
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

    private fun updateUi() {
        val enabled = State(this).enabled
        toggle.setText(if (enabled) R.string.on else R.string.off)
        toggle.setBackgroundResource(if (enabled) R.drawable.toggle_on else R.drawable.toggle_off)
        val now = System.currentTimeMillis()
        if (enabled) {
            nextBuzz.text = Schedule.formatCountdown(Schedule.nextAligned(now, Schedule.tickMs) - now)
            val style = if (Config.TEST_MODE) DateFormat.MEDIUM else DateFormat.SHORT
            nextCard.text = DateFormat.getTimeInstance(style)
                .format(Date(Schedule.nextAligned(now, Schedule.cardMs)))
        } else {
            nextBuzz.text = "–"
            nextCard.text = "–"
        }
        val problem = permissionProblem()
        warning.visibility = if (problem == null) View.GONE else View.VISIBLE
        if (problem != null) warning.setText(problem)
    }

    // ---- permissions ---------------------------------------------------------

    private fun hasNotificationPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun canUseFullScreen() =
        Build.VERSION.SDK_INT < 34 ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun permissionProblem(): Int? = when {
        !Scheduler.canSchedule(this) -> R.string.need_alarms
        !hasNotificationPermission() -> R.string.need_notifications
        !canUseFullScreen() -> R.string.need_full_screen
        else -> null
    }

    private fun fixPermission() {
        val appUri = Uri.parse("package:$packageName")
        val intent = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Scheduler.canSchedule(this) ->
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri)
            !hasNotificationPermission() ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            Build.VERSION.SDK_INT >= 34 && !canUseFullScreen() ->
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, appUri)
            else -> return
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri))
        }
    }
}

/** Keeps content inside the circle on round watch screens. */
fun Activity.insetForRoundScreen(view: View) {
    if (!resources.configuration.isScreenRound) return
    val inset = (resources.displayMetrics.widthPixels * 0.146f).toInt()
    view.setPadding(inset, inset, inset, inset)
}
