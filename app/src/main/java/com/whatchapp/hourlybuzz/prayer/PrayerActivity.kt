package com.whatchapp.hourlybuzz.prayer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.whatchapp.hourlybuzz.Action
import com.whatchapp.hourlybuzz.ActionHost
import com.whatchapp.hourlybuzz.Alerts
import com.whatchapp.hourlybuzz.Gesture
import com.whatchapp.hourlybuzz.Gestures
import com.whatchapp.hourlybuzz.R
import com.whatchapp.hourlybuzz.Screen
import com.whatchapp.hourlybuzz.State
import com.whatchapp.hourlybuzz.arabicDigits
import com.whatchapp.hourlybuzz.insetForRoundScreen

/** Pick the prayer, then show the live rak'ah count while PrayerService runs. */
class PrayerActivity : Activity(), ActionHost {
    override val screen = Screen.PRAYER

    private lateinit var picker: View
    private lateinit var live: View
    private lateinit var liveName: TextView
    private lateinit var liveRakah: TextView
    private lateinit var livePosture: TextView
    private lateinit var otherCount: TextView
    private lateinit var otherStart: Button
    private var other = 2
    private var pending: Pair<Int, String>? = null
    private var askedToCalibrate = false
    private var startRequestedAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateLive()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prayer)
        insetForRoundScreen(findViewById(R.id.picker_list))
        insetForRoundScreen(findViewById(R.id.live))
        picker = findViewById(R.id.picker)
        live = findViewById(R.id.live)
        liveName = findViewById(R.id.live_name)
        liveRakah = findViewById(R.id.live_rakah)
        livePosture = findViewById(R.id.live_posture)
        otherCount = findViewById(R.id.other_count)
        otherStart = findViewById(R.id.other_start)

        val buttons = findViewById<LinearLayout>(R.id.prayer_buttons)
        for ((name, rakahs) in PRAYERS) {
            val label = getString(name)
            buttons.addView(Button(this).apply {
                text = "$label  ${arabicDigits(rakahs)}"
                textSize = 15f
                setOnClickListener { begin(rakahs, label) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        findViewById<View>(R.id.other_minus).setOnClickListener { setOther(other - 1) }
        findViewById<View>(R.id.other_plus).setOnClickListener { setOther(other + 1) }
        otherStart.setOnClickListener { begin(other, getString(R.string.prayer_other)) }
        setOther(other)
        findViewById<View>(R.id.recalibrate).setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        findViewById<View>(R.id.live_end).setOnClickListener {
            PrayerService.stop(this)
            showPicker()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!askedToCalibrate && Calibration.decode(State(this).calibration) == null) {
            // First use: calibrate before praying (once; the buttons ask again).
            askedToCalibrate = true
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        if (PrayerService.engine != null) showLive() else showPicker()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun setOther(n: Int) {
        other = n.coerceIn(1, 10)
        otherCount.text = arabicDigits(other)
        otherStart.text = "${getString(R.string.prayer_start)} (${getString(R.string.prayer_other)})"
    }

    private fun begin(rakahs: Int, name: String) {
        if (Calibration.decode(State(this).calibration) == null) {
            startActivity(Intent(this, CalibrationActivity::class.java))
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // The mic helps (takbir detection) but isn't required: start either way.
            pending = rakahs to name
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        startPrayer(rakahs, name)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val p = pending ?: return
        pending = null
        if (requestCode == REQUEST_MIC) startPrayer(p.first, p.second)
    }

    private fun startPrayer(rakahs: Int, name: String) {
        PrayerService.start(this, rakahs, name)
        startRequestedAt = android.os.SystemClock.elapsedRealtime()
        showLive()
    }

    private fun showPicker() {
        handler.removeCallbacks(ticker)
        live.visibility = View.GONE
        picker.visibility = View.VISIBLE
    }

    private fun showLive() {
        picker.visibility = View.GONE
        live.visibility = View.VISIBLE
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun updateLive() {
        val engine = PrayerService.engine
        if (engine == null) {
            // Give the service a moment to start; after that, it has ended.
            if (android.os.SystemClock.elapsedRealtime() - startRequestedAt > 3000) showPicker()
            return
        }
        liveName.text = PrayerService.prayerName
        val shown = engine.counter.rakah.coerceAtMost(engine.total)
        liveRakah.text = "${arabicDigits(shown)} / ${arabicDigits(engine.total)}"
        livePosture.setText(
            when (engine.counter.stable) {
                Posture.QIYAM -> R.string.posture_qiyam
                Posture.RUKU -> R.string.posture_ruku
                Posture.LOW -> R.string.posture_low
                else -> R.string.posture_moving
            }
        )
    }

    override fun perform(action: Action): Boolean {
        val engine = PrayerService.engine
        when (action) {
            Action.RAKAH_PLUS, Action.RAKAH_MINUS -> {
                if (engine == null || live.visibility != View.VISIBLE) return false
                engine.counter.adjust(if (action == Action.RAKAH_PLUS) 1 else -1)
                Alerts.vibratePattern(this, longArrayOf(40))
                updateLive()
            }
            Action.CLOSE -> finish() // prayer mode keeps running in the background
            else -> return false
        }
        return true
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        // The bezel corrects the count on the live screen and scrolls the list otherwise.
        if (live.visibility == View.VISIBLE && Gestures.onGenericMotion(this, ev)) return true
        return super.dispatchGenericMotionEvent(ev)
    }

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
        private const val REQUEST_MIC = 7

        val PRAYERS = listOf(
            R.string.prayer_fajr to 2,
            R.string.prayer_dhuhr to 4,
            R.string.prayer_asr to 4,
            R.string.prayer_maghrib to 3,
            R.string.prayer_isha to 4,
            R.string.prayer_jumuah to 2,
            R.string.prayer_witr to 1,
            R.string.prayer_witr to 3,
            R.string.prayer_shaf to 2,
            R.string.prayer_sunnah to 2,
            R.string.prayer_sunnah to 4,
            R.string.prayer_duha to 2,
            R.string.prayer_qiyam to 2,
            R.string.prayer_taraweeh to 2,
            R.string.prayer_eid to 2,
            R.string.prayer_istisqa to 2,
            R.string.prayer_tahiyya to 2,
            R.string.prayer_istikhara to 2,
        )
    }
}
